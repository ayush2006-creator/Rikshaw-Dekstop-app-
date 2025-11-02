package org.example.project.firebase

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import java.time.Instant
import java.security.KeyFactory

import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

import java.security.Signature

// Add this data class to parse the response from creating a document
@Serializable
data class FirestoreDocumentResponse(
    val name: String, // e.g., "projects/your-project/databases/(default)/documents/users/someUser/customer/NEW_CUSTOMER_ID"
    val fields: CustomerFields // Or a more generic map if needed
)

// This wrapper is used when parsing the result of a :runQuery request
@Serializable
data class QueryResponseDocument(
    val document: FirestoreUpiId,
    val readTime: String
)

@Serializable
data class ServiceAccountKey(
    val type: String,
    val project_id: String,
    val private_key_id: String,
    val private_key: String,
    val client_email: String,
    val client_id: String,
    val auth_uri: String,
    val token_uri: String,
    val auth_provider_x509_cert_url: String,
    val client_x509_cert_url: String,
    val universe_domain: String? = null // Add this optional field
)

@Serializable
data class AccessTokenResponse(
    val access_token: String,
    val expires_in: Int,
    val token_type: String
)

// --- REMOVED CONFLICTING DATA CLASSES ---
// The definitions for FirestoreUpiId and UpiIdFields
// have been moved to DataModels.kt
// ---

class FirestoreService {
    private val projectId = "rikshaw-925ef"
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    // Cache for access token
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    // Service account configuration
    private lateinit var serviceAccountKey: ServiceAccountKey

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        expectSuccess = false
    }

    // Initialize with service account JSON content
    fun initializeServiceAccount(serviceAccountJson: String) {
        val json = Json {
            ignoreUnknownKeys = true // Add this to handle any future unknown keys
            isLenient = true
        }
        serviceAccountKey = json.decodeFromString<ServiceAccountKey>(serviceAccountJson)
    }

    // Generate JWT for service account authentication
    private fun createJWT(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val now = System.currentTimeMillis() / 1000
        val exp = now + 3600 // 1 hour expiry

        val payload = """{
            "iss":"${serviceAccountKey.client_email}",
            "scope":"https://www.googleapis.com/auth/datastore",
            "aud":"${serviceAccountKey.token_uri}",
            "iat":$now,
            "exp":$exp
        }"""

        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        val signingInput = "$encodedHeader.$encodedPayload"

        // Sign with private key
        val privateKeyContent = serviceAccountKey.private_key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")

        val keyBytes = Base64.getDecoder().decode(privateKeyContent)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(spec)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray())
        val signatureBytes = signature.sign()
        val encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)

        return "$signingInput.$encodedSignature"
    }

    // Get access token using service account
    private suspend fun getAccessToken(): String {
        // Return cached token if still valid
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken!!
        }

        val jwt = createJWT()

        val response = httpClient.post(serviceAccountKey.token_uri) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
            )
        }

        if (response.status.isSuccess()) {
            val tokenResponse = response.body<AccessTokenResponse>()
            cachedToken = tokenResponse.access_token
            tokenExpiry = System.currentTimeMillis() + (tokenResponse.expires_in * 1000) - 300000 // Refresh 5 minutes early
            return tokenResponse.access_token
        } else {
            throw Exception("Failed to get access token: ${response.status} - ${response.bodyAsText()}")
        }
    }

    suspend fun getCustomers(userId: String): Result<CustomerListResponse> {
        return try {
            val accessToken = getAccessToken()
            ensureUserDocumentExists(userId, accessToken)

            val url = "$baseUrl/users/$userId/customer"
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $accessToken")
            }
            println(response.toString())

            handleFirestoreResponse<CustomerListResponse>(response)
        } catch (e: Exception) {
            println(e.toString())
            Result.failure(e)
        }
    }



    @Serializable
    data class CommitRequest(
        val writes: List<Write>
    )

    @Serializable
    data class Write(
        val update: Document,
        val currentDocument: Precondition
    )

    @Serializable
    data class Document(
        val name: String,
        val fields: Map<String, JsonElement> // Changed from Map<String, Any>
    )

    // REMOVED the incorrect FieldValue data class
    // @Serializable
    // data class FieldValue(
    //     val stringValue: String
    // )

    @Serializable
    data class Precondition(
        val exists: Boolean
    )



    suspend fun addUpiId(userId: String, upiId: String, customerAccNo: String): Result<Unit> {
        if (upiId.isBlank() || upiId.contains("/")) {
            return Result.failure(IllegalArgumentException("UPI ID cannot be blank or contain '/' characters."))
        }

        return try {
            val accessToken = getAccessToken()
            val commitUrl = "$baseUrl:commit"
            val newUpiDocumentId = UUID.randomUUID().toString()

            // Full Firestore resource paths
            val userUpiDocPath =
                "projects/$projectId/databases/(default)/documents/users/$userId/upiIds/$newUpiDocumentId"
            val uniqueUpiDocPath =
                "projects/$projectId/databases/(default)/documents/users/$userId/uniqueUpiIds/$upiId"

            // --- FIX: Updated fields to match DataModels.kt schema ---
            val userUpiWrite = Write(
                update = Document(
                    name = userUpiDocPath,
                    fields = mapOf(
                        "upiId" to buildJsonObject { put("stringValue", upiId) },
                        "customerId" to buildJsonObject { put("stringValue", customerAccNo) },
                        "isActive" to buildJsonObject { put("booleanValue", true) },
                        "createdAt" to buildJsonObject { put("timestampValue", Instant.now().toString()) }
                    )
                ),
                currentDocument = Precondition(exists = false)
            )

            val uniqueUpiWrite = Write(
                update = Document(
                    name = uniqueUpiDocPath,
                    fields = mapOf(
                        "customerId" to buildJsonObject { put("stringValue", customerAccNo) }
                    )
                ),
                currentDocument = Precondition(exists = false)
            )

            // --- REMOVED CONFLICTING DECLARATION ---
            // val uniqueUpiWrite = Write(
            //     update = Document(
            //         name = uniqueUpiDocPath,
            //         fields = mapOf(
            //             "customerId" to FieldValue(stringValue = customerAccNo)
            //         )
            //     ),
            //     currentDocument = Precondition(exists = false)
            // )

            val requestBody = CommitRequest(writes = listOf(userUpiWrite, uniqueUpiWrite))

            val response = httpClient.post(commitUrl) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.value in 200..299) {
                println("✅ Successfully committed new UPI ID: $upiId")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()

                return when {
                    response.status.value == 409 || errorBody.contains("FAILED_PRECONDITION") -> {
                        Result.failure(Exception("UPI ID already exists. Please choose another one."))
                    }
                    else -> {
                        Result.failure(Exception("Failed to add UPI ID. Reason: ${response.status} - $errorBody"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }




    suspend fun addCustomer(userId: String, customerFields: CustomerFields, upiId: String): Result<Unit> {
        println("Attempting to add customer for userId: $userId")
        return try {
            val accessToken = getAccessToken()
            ensureUserDocumentExists(userId, accessToken)

            // --- Step 1: Extract the desired customerId from customerFields ---
            val customerId = customerFields.Accno.stringValue
            if (customerId.isBlank()) {
                return Result.failure(Exception("Account number (customerId) cannot be blank"))
            }
            println("Using customerId from Accno field: $customerId")

            // --- Step 2: Create the customer with specific document ID ---
            val customerUrl = "$baseUrl/users/$userId/customer/$customerId" // Add customerId to URL
            println("POSTing to customer URL: $customerUrl")
            val customerResponse = httpClient.patch(customerUrl) { // Use PATCH instead of POST for specific document ID
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(mapOf("fields" to customerFields))
            }

            // --- Log the response ---
            val customerResponseBody = customerResponse.bodyAsText()
            println("Customer creation response status: ${customerResponse.status}")
            println("Customer creation response body: $customerResponseBody")

            // --- Step 3: Check if customer creation was successful ---
            if (!customerResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to add customer: ${customerResponse.status} - $customerResponseBody"))
            }

            // --- Step 4: Call addUpiId with the customerId (which is the Accno) ---
            println("Attempting to add UPI ID for customerId: $customerId")
            val upiResult = addUpiId(
                userId = userId,
                upiId = upiId,
                customerAccNo = customerFields.Accno.stringValue // This will be the same as customerId
            )

            // --- Step 5: Return the result ---
            if (upiResult.isSuccess) {
                println("Successfully added customer and UPI ID.")
            } else {
                println("ERROR: Failed to add UPI ID. Reason: ${upiResult.exceptionOrNull()?.message}")
            }
            return upiResult

        } catch (e: Exception) {
            println("FATAL ERROR in addCustomer function: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun ensureUserDocumentExists(userId: String, accessToken: String) {
        val userDocUrl = "$baseUrl/users/$userId"

        val getUserResponse = httpClient.get(userDocUrl) {
            header("Authorization", "Bearer $accessToken")
        }

        if (getUserResponse.status == HttpStatusCode.NotFound) {
            // Create new user document
            val createUrl = "$baseUrl/users?documentId=$userId"
            val userData = mapOf(
                "fields" to mapOf(
                    "createdAt" to mapOf("timestampValue" to Instant.now().toString()),
                    "lastActive" to mapOf("timestampValue" to Instant.now().toString()),
                    "userId" to mapOf("stringValue" to userId)
                )
            )

            val createResponse = httpClient.post(createUrl) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(userData)
            }

            if (!createResponse.status.isSuccess()) {
                throw Exception("Failed to create user document: ${createResponse.status} - ${createResponse.bodyAsText()}")
            }
        } else if (getUserResponse.status.isSuccess()) {
            // Update last active time
            val updateData = mapOf(
                "fields" to mapOf(
                    "lastActive" to mapOf("timestampValue" to Instant.now().toString())
                )
            )

            httpClient.patch("$baseUrl/users/$userId?updateMask.fieldPaths=lastActive") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(updateData)
            }
        } else {
            throw Exception("Error checking user document: ${getUserResponse.status} - ${getUserResponse.bodyAsText()}")
        }
    }

    suspend fun getTransactions(userId: String, customerId: String): Result<TransactionListResponse> {
        return try {
            val accessToken = getAccessToken()
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions"
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $accessToken")
            }

            handleFirestoreResponse<TransactionListResponse>(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Add transaction method
    suspend fun addTransaction(userId: String, customerId: String, transactionFields: TransactionFields): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions"
            val response = httpClient.post(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(mapOf("fields" to transactionFields))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add transaction: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Update customer method
    suspend fun updateCustomer(userId: String, customerId: String, customerFields: CustomerFields, updateMask: List<String>): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            val maskParams = updateMask.joinToString("&") { field ->
                "updateMask.fieldPaths=$field"
            }
            val url = "$baseUrl/users/$userId/customer/$customerId?$maskParams"


            val response = httpClient.patch(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(mapOf("fields" to customerFields))
            }
            println(response)

            if (response.status.isSuccess()) {
                println("customer updated successfully")
                Result.success(Unit)

            } else {
                Result.failure(Exception("Failed to update customer: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Delete customer method
    suspend fun deleteCustomer(userId: String, customerId: String): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            val url = "$baseUrl/users/$userId/customer/$customerId"

            val response = httpClient.delete(url) {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete customer: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // Add this method to your FirestoreService class



    // Enhanced method with more transaction details and fine calculation
    suspend fun addTransactionByAccountNumber(userId: String, accountNo: String, amount: Double, fine: Double): Result<Unit> {
        return try {
            // 1. Find the customer by their account number.
            val customersResult = getCustomers(userId)
            if (customersResult.isFailure) {
                return Result.failure(customersResult.exceptionOrNull() ?: Exception("Failed to fetch customers."))
            }

            val customer = customersResult.getOrNull()?.documents?.find {
                it.fields.Accno.stringValue == accountNo
            } ?: return Result.failure(Exception("Customer with account number $accountNo not found."))

            // 2. Prepare the new transaction details.
            val customerId = customer.name.substringAfterLast("/")
            val currentBalance = customer.fields.Amount.doubleValue - customer.fields.amountPaid.doubleValue
            val newBalance = currentBalance - amount

            val transactionFields = TransactionFields(
                amount = DoubleValue(amount),
                Balance = DoubleValue(newBalance),
                date = TimestampValue(Instant.now().toString()),
                Fine = DoubleValue(fine)
            )

            // 3. Add the transaction document to Firestore.
            val addTransactionResult = addTransaction(userId, customerId, transactionFields)

            // 4. Check the result and proceed to update the customer.
            if (addTransactionResult.isSuccess) {
                println("Transaction added successfully with amount: $amount and fine: $fine")
                // THIS IS THE CRITICAL FIX:
                // We now return the result of the customer update operation.
                return updateCustomerAmountPaid(userId, customerId, customer, amount)
            } else {
                // If adding the transaction failed, return that specific failure result.
                println("Failed to add transaction, customer will not be updated.")
                return addTransactionResult
            }

        } catch (e: Exception) {
            // Catch any other exceptions during the process.
            Result.failure(e)
        }
    }

    private suspend fun updateCustomerAmountPaid(
        userId: String,
        customerId: String,
        customer: FirestoreCustomer,
        paymentAmount: Double
    ): Result<Unit> {
        return try {
            val currentAmountPaid = customer.fields.amountPaid.doubleValue
            val newAmountPaid = currentAmountPaid + paymentAmount

            val updatedFields = customer.fields.copy(
                amountPaid = DoubleValue(newAmountPaid)
            )

            updateCustomer(userId, customerId, updatedFields, listOf("amountPaid"))
        } catch (e: Exception) {
            Result.failure<Unit>(e)
        }
    }

    // Enhanced method with more transaction details and fine calculation
    suspend fun addDetailedTransactionByAccountNumber(
        userId: String,
        accountNo: String,
        amount: Double,
        fine: Double = 0.0
    ): Result<Unit> {
        return try {
            val accessToken = getAccessToken()

            // First, find the customer by account number
            val customersResult = getCustomers(userId)
            if (customersResult.isFailure) {
                return Result.failure(Exception("Failed to fetch customers: ${customersResult.exceptionOrNull()?.message}"))
            }

            val customers = customersResult.getOrNull()
            val customer = customers?.documents?.find {
                it.fields.Accno.stringValue == accountNo
            }

            if (customer == null) {
                return Result.failure(Exception("Customer with account number $accountNo not found"))
            }
            else{}

            // Extract customer ID from the document name
            val customerId = customer.name.substringAfterLast("/")

            // Calculate new balance after payment
            val currentBalance = customer.fields.Amount.doubleValue - customer.fields.amountPaid.doubleValue
            val newBalance = currentBalance - amount

            // Create transaction fields using your structure
            val transactionFields = TransactionFields(
                amount = DoubleValue(amount),
                Balance = DoubleValue(newBalance),
                date = TimestampValue(Instant.now().toString()),
                Fine = DoubleValue(fine)
            )

            // Make API call using the existing addTransaction method
            val addResult = addTransaction(userId, customerId, transactionFields)

        } catch (e: Exception) {
            Result.failure<Unit>(e)
        } as Result<Unit>
    }

    // Method to get all transactions for a customer by account number
    suspend fun getTransactionsByAccountNumber(userId: String, accountNo: String): Result<TransactionListResponse> {
        return try {
            // First, find the customer by account number
            val customersResult = getCustomers(userId)
            if (customersResult.isFailure) {
                return Result.failure(Exception("Failed to fetch customers: ${customersResult.exceptionOrNull()?.message}"))
            }

            val customers = customersResult.getOrNull()
            val customer = customers?.documents?.find {
                it.fields.Accno.stringValue == accountNo
            }

            if (customer == null) {
                return Result.failure(Exception("Customer with account number $accountNo not found"))
            }

            // Extract customer ID from the document name
            val customerId = customer.name.substringAfterLast("/")

            // Get transactions for this customer
            getTransactions(userId, customerId)

        } catch (e: Exception) {
            Result.failure<TransactionListResponse>(e)
        }
    }

    // Utility method to validate account number exists
    suspend fun validateAccountNumber(userId: String, accountNo: String): Result<FirestoreCustomer> {
        return try {
            val customersResult = getCustomers(userId)
            if (customersResult.isFailure) {
                return Result.failure(Exception("Failed to fetch customers: ${customersResult.exceptionOrNull()?.message}"))
            }

            val customers = customersResult.getOrNull()
            val customer = customers?.documents?.find {
                it.fields.Accno.stringValue == accountNo
            }

            if (customer != null) {
                Result.success(customer)
            } else {
                Result.failure(Exception("Customer with account number $accountNo not found"))
            }

        } catch (e: Exception) {
            Result.failure<FirestoreCustomer>(e)
        }
    }

    // --- New method to get UPI IDs ---
    suspend fun getUpiIdsForCustomer(userId: String, customerAccNo: String): Result<List<FirestoreUpiId>> {
        return try {
            val accessToken = getAccessToken()
           val url = "$baseUrl/users/$userId:runQuery"
            // We need to construct a JSON body for the query
            // Updated to query 'customerId' and 'isActive' based on new DataModels.kt
            val queryBody = """
                {
                  "structuredQuery": {
                    "from": [{"collectionId": "upiIds"}],
                    "where": {
                      "compositeFilter": {
                        "op": "AND",
                        "filters": [
                          {
                            "fieldFilter": {
                              "field": {"fieldPath": "customerId"},
                              "op": "EQUAL",
                              "value": {"stringValue": "$customerAccNo"}
                            }
                          },
                          {
                            "fieldFilter": {
                              "field": {"fieldPath": "isActive"},
                              "op": "EQUAL",
                              "value": {"booleanValue": true}
                            }
                          }
                        ]
                      }
                    }
                  }
                }
            """.trimIndent()

            val response = httpClient.post(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(queryBody)
            }

            if (response.status.isSuccess()) {
                val json = Json { ignoreUnknownKeys = true }
                // runQuery returns a JSON array of documents, potentially empty
                val responseBody = response.bodyAsText()
                if (responseBody == "[]") {
                    return Result.success(emptyList()) // No matching documents
                }

                // Each item in the array has a "document" field
                val queryResponse = json.decodeFromString<List<QueryResponseDocument>>(responseBody)
                val documents = queryResponse.map { it.document }
                Result.success(documents)
            } else {
                Result.failure(Exception("Firestore error: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- NEW METHOD: Delete a transaction ---
    suspend fun deleteTransaction(userId: String, customerId: String, transactionId: String): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            // Construct the full path to the specific transaction document
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions/$transactionId"

            val response = httpClient.delete(url) {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                println("Transaction deleted successfully: $transactionId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete transaction: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Add this method to your FirestoreService class



    // Enhanced method with more transaction details and fine calculation
    // --- New method to update a transaction ---
    suspend fun updateTransaction(userId: String, customerId: String, transactionId: String, transactionFields: TransactionFields, updateMask: List<String>): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            val maskParams = updateMask.joinToString("&") { field ->
                "updateMask.fieldPaths=$field"
            }
            // Construct the full path to the specific transaction document
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions/$transactionId?$maskParams"


            val response = httpClient.patch(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(mapOf("fields" to transactionFields))
            }

            if (response.status.isSuccess()) {
                println("Transaction updated successfully")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update transaction: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T> handleFirestoreResponse(response: HttpResponse): Result<T> {
        return if (response.status.isSuccess()) {
            val json = Json { ignoreUnknownKeys = true }
            val result = json.decodeFromString<T>(response.bodyAsText())
            Result.success(result)
        } else {
            Result.failure(Exception("Firestore error: ${response.status} - ${response.bodyAsText()}"))
        }
    }
}


