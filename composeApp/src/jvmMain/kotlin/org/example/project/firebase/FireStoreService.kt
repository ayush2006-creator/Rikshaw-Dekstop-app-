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
            // println(response.toString())

            handleFirestoreResponse<CustomerListResponse>(response)
        } catch (e: Exception) {
            // println(e.toString())
            Result.failure(e)
        }
    }


    // --- FIXED: Renamed Document to FirestoreDoc to avoid serialization conflict ---
    @Serializable
    data class FirestoreDoc(
        val name: String,
        val fields: Map<String, JsonElement>
    )

    @Serializable
    data class Write(
        val update: FirestoreDoc? = null,
        val currentDocument: Precondition? = null,
        val updateMask: FieldMask? = null // For updating specific fields
    )

    @Serializable
    data class FieldMask(
        val fieldPaths: List<String>
    )

    @Serializable
    data class CommitRequest(
        val writes: List<Write>
    )

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

            val userUpiWrite = Write(
                update = FirestoreDoc(
                    name = userUpiDocPath,
                    fields = buildJsonObject {
                        put("upiId", buildJsonObject { put("stringValue", upiId) })
                        put("customerId", buildJsonObject { put("stringValue", customerAccNo) }) // Changed from accNo
                        put("isActive", buildJsonObject { put("booleanValue", true) })
                        put("createdAt", buildJsonObject { put("timestampValue", Instant.now().toString()) })
                    }
                ),
                currentDocument = Precondition(exists = false)
            )

            val uniqueUpiWrite = Write(
                update = FirestoreDoc(
                    name = uniqueUpiDocPath,
                    fields = buildJsonObject {
                        put("customerId", buildJsonObject { put("stringValue", customerAccNo) })
                    }
                ),
                currentDocument = Precondition(exists = false)
            )

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
            if (upiId.isNotBlank()) {
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
            } else {
                println("No UPI ID provided, skipping UPI add.")
                return Result.success(Unit)
            }

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
            // Fetch all transactions without orderBy (to avoid index requirement)
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions"
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                val json = Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }

                val responseBody = response.bodyAsText()
                println("📄 Transactions Response: $responseBody")

                // Parse the response
                val result = json.decodeFromString<TransactionListResponse>(responseBody)

                // Sort transactions by date in memory (descending - newest first)
                val sortedDocuments = result.documents?.sortedByDescending { transaction ->
                    try {
                        transaction.fields.date.timestampValue
                    } catch (e: Exception) {
                        "" // If date parsing fails, put it at the end
                    }
                }

                Result.success(TransactionListResponse(documents = sortedDocuments))
            } else {
                val errorBody = response.bodyAsText()
                println("❌ Error fetching transactions: ${response.status} - $errorBody")
                Result.failure(Exception("Firestore error: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            println("❌ Exception in getTransactions: ${e.message}")
            e.printStackTrace()
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
            // println(response)

            if (response.status.isSuccess()) {
                // println("customer updated successfully")
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

    // --- UPI ID and Transaction Management ---

    @Serializable
    data class StructuredQuery(
        val from: List<CollectionSelector>,
        val where: Filter,
        val limit: Int = 1
    )

    @Serializable
    data class CollectionSelector(
        val collectionId: String
    )

    @Serializable
    data class Filter(
        val compositeFilter: CompositeFilter
    )

    @Serializable
    data class CompositeFilter(
        val op: String, // "AND"
        val filters: List<FieldFilterWrapper>
    )

    @Serializable
    data class FieldFilterWrapper(
        val fieldFilter: FieldFilter
    )

    @Serializable
    data class FieldFilter(
        val field: Path,
        val op: String, // "EQUAL"
        val value: Value
    )

    @Serializable
    data class Path(
        val fieldPath: String
    )

    @Serializable
    data class Value(
        val stringValue: String? = null,
        val booleanValue: Boolean? = null
    )

    // Get UPI IDs for a specific customer
    suspend fun getUpiIdsForCustomer(userId: String, customerAccNo: String): Result<List<FirestoreUpiId>> {
        return try {
            val accessToken = getAccessToken()
            // This is the URL for running a query
            val url = "$baseUrl/users/$userId:runQuery"

            // Construct the structured query
            val query = mapOf(
                "structuredQuery" to StructuredQuery(
                    from = listOf(CollectionSelector(collectionId = "upiIds")),
                    where = Filter(
                        compositeFilter = CompositeFilter(
                            op = "AND",
                            filters = listOf(
                                FieldFilterWrapper(FieldFilter(
                                    field = Path("customerId"),
                                    op = "EQUAL",
                                    value = Value(stringValue = customerAccNo)
                                )),
                                FieldFilterWrapper(FieldFilter(
                                    field = Path("isActive"),
                                    op = "EQUAL",
                                    value = Value(booleanValue = true)
                                ))
                            )
                        )
                    ),
                    limit = 20 // Get up to 20 UPI IDs for this customer
                )
            )

            val response = httpClient.post(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(query)
            }

            if (response.status.isSuccess()) {
                val json = Json { ignoreUnknownKeys = true }
                // The response is an array of objects, each containing a 'document'
                val queryResults = json.decodeFromString<List<QueryResponseDocument>>(response.bodyAsText())
                val upiIds = queryResults.map { it.document }
                Result.success(upiIds)
            } else {
                Result.failure(Exception("Failed to get UPI IDs: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Update a specific transaction (e.g., to add a fine)
    suspend fun updateTransaction(
        userId: String,
        customerId: String,
        transactionId: String,
        updatedFields: TransactionFields,
        updateMask: List<String>
    ): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            val maskParams = updateMask.joinToString("&") { field ->
                "updateMask.fieldPaths=$field"
            }
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions/$transactionId?$maskParams"

            val response = httpClient.patch(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(mapOf("fields" to updatedFields))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update transaction: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Delete a specific transaction
    suspend fun deleteTransaction(userId: String, customerId: String, transactionId: String): Result<Unit> {
        return try {
            val accessToken = getAccessToken()
            val url = "$baseUrl/users/$userId/customer/$customerId/transactions/$transactionId"

            val response = httpClient.delete(url) {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete transaction: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // --- Bank Statement Processing ---

    // 1. Check if a bank transaction reference has already been processed
    suspend fun isBankRefProcessed(userId: String, bankTransactionRef: String): Result<Boolean> {
        return try {
            val accessToken = getAccessToken()
            // Document ID is the ref itself for a quick lookup
            val url = "$baseUrl/users/$userId/processedBankTransactions/$bankTransactionRef"

            val response = httpClient.get(url) {
                header("Authorization", "Bearer $accessToken")
            }

            when (response.status) {
                HttpStatusCode.OK -> Result.success(true) // Document exists, it's a duplicate
                HttpStatusCode.NotFound -> Result.success(false) // Document doesn't exist, not a duplicate
                else -> Result.failure(Exception("Error checking bank ref: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. Find a customer's account number by their UPI ID
    // Replace your existing findCustomerByUpiId function with this:

    suspend fun findCustomerByUpiId(userId: String, upiId: String): Result<String?> {
        return try {
            val accessToken = getAccessToken()
            val url = "$baseUrl/users/$userId/uniqueUpiIds/$upiId"

            println("🔍 Firebase GET request to: $url")

            val response = httpClient.get(url) {
                header("Authorization", "Bearer $accessToken")
            }

            println("📡 Response status: ${response.status}")

            if (response.status == HttpStatusCode.OK) {
                val responseBody = response.bodyAsText()
                println("📄 Response body: $responseBody")

                val json = Json { ignoreUnknownKeys = true }

                // Define the structure to match Firestore's response
                @Serializable
                data class FirestoreStringValue(val stringValue: String)

                @Serializable
                data class UpiLookupFields(val customerId: FirestoreStringValue)

                @Serializable
                data class UpiLookupDoc(val name: String, val fields: UpiLookupFields)

                val upiDoc = json.decodeFromString<UpiLookupDoc>(responseBody)
                val customerIdValue = upiDoc.fields.customerId.stringValue

                println("✅ Successfully extracted customerId: '$customerIdValue'")
                Result.success(customerIdValue)

            } else if (response.status == HttpStatusCode.NotFound) {
                println("⚠️ Document not found in Firebase for UPI: $upiId")
                Result.success(null)
            } else {
                val errorBody = response.bodyAsText()
                println("❌ Error response: $errorBody")
                Result.failure(Exception("Error finding UPI ID: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            println("❌ Exception in findCustomerByUpiId for '$upiId': ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 3. Add transaction and log bank ref in a single atomic batch
    suspend fun addTransactionByAccountNumber(
        userId: String,
        accountNo: String,
        amount: Double,
        fine: Double,
        bankTransactionRef: String // New param
    ): Result<Unit> {
        return try {
            // 1. Find the customer by their account number to get current data
            val customersResult = getCustomers(userId)
            if (customersResult.isFailure) {
                return Result.failure(customersResult.exceptionOrNull() ?: Exception("Failed to fetch customers."))
            }

            val customer = customersResult.getOrNull()?.documents?.find {
                it.fields.Accno.stringValue == accountNo
            } ?: return Result.failure(Exception("Customer with account number $accountNo not found."))

            val customerId = customer.name.substringAfterLast("/") // This is the Document ID (which is the accNo)
            val customerDocPath = "projects/$projectId/databases/(default)/documents/users/$userId/customer/$customerId"

            // 2. Prepare transaction details
            val newTransactionId = UUID.randomUUID().toString()
            val transactionDocPath = "$customerDocPath/transactions/$newTransactionId"

            val currentAmountPaid = customer.fields.amountPaid.doubleValue
            val currentBalance = customer.fields.Amount.doubleValue - currentAmountPaid
            val newBalance = currentBalance - amount
            val newAmountPaid = currentAmountPaid + amount

            // 3. Prepare the atomic commit
            val accessToken = getAccessToken()
            val commitUrl = "$baseUrl:commit"

            val writes = mutableListOf<Write>()

            // --- Write 1: Create the new transaction ---
            val transactionFields = buildJsonObject {
                put("amount", buildJsonObject { put("doubleValue", amount) })
                put("Balance", buildJsonObject { put("doubleValue", newBalance) })
                put("date", buildJsonObject { put("timestampValue", Instant.now().toString()) })
                put("Fine", buildJsonObject { put("doubleValue", fine) })
                put("transactionType", buildJsonObject { put("stringValue", "payment") })
                put("description", buildJsonObject { put("stringValue", "") })
                put("installmentsCovered", buildJsonObject { put("integerValue", 1) })
                if (bankTransactionRef.isNotBlank()) {
                    put("bankTransactionRef", buildJsonObject { put("stringValue", bankTransactionRef) })
                }
            }
            writes.add(
                Write(
                    update = FirestoreDoc(
                        name = transactionDocPath,
                        fields = transactionFields
                    )
                )
            )

            // --- Write 2: Log the bank transaction ref (if provided) ---
            if (bankTransactionRef.isNotBlank()) {
                val bankRefDocPath = "projects/$projectId/databases/(default)/documents/users/$userId/processedBankTransactions/$bankTransactionRef"
                writes.add(
                    Write(
                        update = FirestoreDoc(
                            name = bankRefDocPath,
                            fields = buildJsonObject {
                                put("processedAt", buildJsonObject { put("timestampValue", Instant.now().toString()) })
                                put("customerId", buildJsonObject { put("stringValue", customerId) })
                                put("amount", buildJsonObject { put("doubleValue", amount) })
                            }
                        ),
                        // This precondition ensures it's not a duplicate
                        currentDocument = Precondition(exists = false)
                    )
                )
            }

            // --- Write 3: Update the customer's amountPaid ---
            writes.add(
                Write(
                    update = FirestoreDoc(
                        name = customerDocPath,
                        fields = buildJsonObject {
                            put("amountPaid", buildJsonObject { put("doubleValue", newAmountPaid) })
                        }
                    ),
                    // FIXED: Added the required updateMask
                    updateMask = FieldMask(fieldPaths = listOf("amountPaid")),
                    // This precondition ensures the customer document exists
                    currentDocument = Precondition(exists = true)
                )
            )

            // 4. Send the batch write request
            val requestBody = CommitRequest(writes = writes)
            val response = httpClient.post(commitUrl) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            // 5. Handle response
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                if (errorBody.contains("FAILED_PRECONDITION")) {
                    Result.failure(Exception("Transaction failed: This is a duplicate bank transaction."))
                } else {
                    Result.failure(Exception("Transaction commit failed: ${response.status} - $errorBody"))
                }
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

