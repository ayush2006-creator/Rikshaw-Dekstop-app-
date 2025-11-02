package org.example.project.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.firebase.*

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CustomerUiState(
    val customers: CustomerListResponse? = null,
    val selectedCustomer: FirestoreCustomer? = null,
    val transactions: TransactionListResponse? = null,
    val upiIds: List<FirestoreUpiId>? = null, // For UPI IDs
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Add Customer Dialog
    val isAddCustomerDialogOpen: Boolean = false,
    val newCustomerAccNo: String = "",
    val newCustomerName: String = "",
    val newCustomerPhoneNo: String = "",
    val newCustomerVehicleNo: String = "",
    val newCustomerInstallmentAmount: String = "",
    val newCustomerAmount: String = "",
    val newCustomerClosingDate: String = "",
    val newCustomerUpiId: String = "",
    val isAddingCustomer: Boolean = false,
    val addCustomerError: String? = null,

    // Add UPI ID Dialog
    val isAddUpiIdDialogOpen: Boolean = false,
    val newUpiId: String = "",
    val addUpiIdError: String? = null,
    val isAddingUpiId: Boolean = false,

    // Edit Transaction Dialog
    val isEditTransactionDialogOpen: Boolean = false,
    val transactionToEdit: FirestoreTransaction? = null,
    val editTransactionFine: String = "",
    val editTransactionError: String? = null,
    val isEditingTransaction: Boolean = false,

    // Delete Transaction Dialog
    val isDeleteTransactionDialogOpen: Boolean = false,
    val transactionToDelete: FirestoreTransaction? = null,
    val isDeletingTransaction: Boolean = false
)


// Data class to hold information about customers with pending payments
data class PendingCustomerInfo(
    val customer: FirestoreCustomer,
    val nextDueDate: LocalDate,
    val daysOverdue: Long,
    val partialPaymentLeft: Double,
    val totalAmountDue: Double
)
class CustomerViewModel(
    private val firestoreService: FirestoreService,
    private val currentUserId: String // Pass user ID directly instead of using AuthViewModel
) : ViewModel() {

    var uiState by mutableStateOf(CustomerUiState())
        private set

    // Initialize service account when ViewModel is created
    init {
        // You'll need to load your service account JSON here
        // This can be from assets, resources, or environment variables
        initializeServiceAccount()
    }

    private fun initializeServiceAccount() {
        val localProperties = java.util.Properties()
        val propertiesFile = java.io.File(System.getProperty("user.dir"), "local.properties").let { file ->
            if (file.exists()) file else java.io.File(System.getProperty("user.dir")).parentFile.resolve("local.properties")
        }

        if (!propertiesFile.exists()) {
            throw IllegalStateException("local.properties not found at: ${propertiesFile.absolutePath}")
        }

        propertiesFile.inputStream().use { localProperties.load(it) }

        val serviceAccountJson = localProperties.getProperty("FIREBASE_SERVICE_ACCOUNT_JSON")
            ?: throw IllegalStateException("FIREBASE_SERVICE_ACCOUNT_JSON not found in local.properties")

        firestoreService.initializeServiceAccount(serviceAccountJson)
    }

    // Functions to update the new state fields
    fun onNewCustomerAccNoChange(accNo: String) {
        uiState = uiState.copy(newCustomerAccNo = accNo, addCustomerError = null)
    }

    fun onNewCustomerNameChange(name: String) {
        uiState = uiState.copy(newCustomerName = name, addCustomerError = null)
    }

    fun onNewCustomerPhoneNoChange(phone: String) {
        uiState = uiState.copy(newCustomerPhoneNo = phone, addCustomerError = null)
    }

    fun onNewCustomerVehicleNoChange(vehicleNo: String) {
        uiState = uiState.copy(newCustomerVehicleNo = vehicleNo, addCustomerError = null)
    }
    fun onNewCustomerUpiIdChange(upiId: String) {
        uiState = uiState.copy(newCustomerUpiId = upiId, addCustomerError = null)
    }
    fun onNewCustomerInstallmentChange(amount: String) {
        uiState = uiState.copy(newCustomerInstallmentAmount = amount, addCustomerError = null)
    }

    fun onNewCustomerAmountChange(amount: String) {
        uiState = uiState.copy(newCustomerAmount = amount, addCustomerError = null)
    }

    fun onNewCustomerClosingDateChange(date: String) {
        uiState = uiState.copy(newCustomerClosingDate = date, addCustomerError = null)
    }

    fun openAddCustomerDialog() {
        uiState = uiState.copy(isAddCustomerDialogOpen = true, addCustomerError = null)
    }

    fun closeAddCustomerDialog() {
        uiState = uiState.copy(
            isAddCustomerDialogOpen = false,
            isAddingCustomer = false,
            addCustomerError = null,
            newCustomerAccNo = "",
            newCustomerName = "",
            newCustomerPhoneNo = "",
            newCustomerVehicleNo = "",
            newCustomerInstallmentAmount = "",
            newCustomerAmount = "",
            newCustomerClosingDate = "",
            newCustomerUpiId = ""
        )

    }

    fun selectCustomer(customer: FirestoreCustomer) {
        uiState = uiState.copy(
            selectedCustomer = customer,
            transactions = null,
            upiIds = null,
            errorMessage = null
        )
        // Load both transactions and UPI IDs
        loadTransactions()
        loadUpiIds()
    }

    fun clearSelectedCustomer() {
        uiState = uiState.copy(selectedCustomer = null, transactions = null, upiIds = null)

    }

    fun loadCustomers() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val result = firestoreService.getCustomers(currentUserId)

            uiState = result.fold(
                onSuccess = { customerResponse ->
                    uiState.copy(isLoading = false, customers = customerResponse, errorMessage = null)
                },
                onFailure = { error ->
                    val errorMessage = when {
                        error.message?.contains("401") == true -> error.message
                        error.message?.contains("403") == true -> "Permission denied. Check Firestore security rules."
                        else -> "Failed to load customers: ${error.message}"
                    }
                    uiState.copy(isLoading = false, errorMessage = errorMessage)
                }
            )
        }
    }

    fun addCustomer() {
        if (uiState.newCustomerName.isBlank() || uiState.newCustomerAccNo.isBlank()) {
            uiState = uiState.copy(addCustomerError = "Please fill in all required fields")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isAddingCustomer = true, addCustomerError = null)

            try {
                val customerFields = CustomerFields(
                    Accno = StringValue(uiState.newCustomerAccNo),
                    Name = StringValue(uiState.newCustomerName),
                    PhoneNo = StringValue(uiState.newCustomerPhoneNo),
                    VehicleNo = StringValue(uiState.newCustomerVehicleNo),
                    OpeningDate = TimestampValue(Instant.now().toString()),
                    ClosingDate = TimestampValue(
                        if (uiState.newCustomerClosingDate.isNotBlank()) {
                            Instant.parse(uiState.newCustomerClosingDate + "T00:00:00.00Z").toString()
                        } else {
                            ""
                        }
                    ),
                    installmentAmount = DoubleValue(uiState.newCustomerInstallmentAmount.toDoubleOrNull() ?: 0.0),
                    Amount = DoubleValue(uiState.newCustomerAmount.toDoubleOrNull() ?: 0.0),
                    amountPaid = DoubleValue(0.0),
                )
                if (uiState.customers?.documents?.any { it.fields.Accno.stringValue == uiState.newCustomerAccNo } == true) {
                    println("account found ")
                    uiState = uiState.copy(
                        isAddingCustomer = false,
                        addCustomerError = "Customer with this account number already exists"
                    )
                    return@launch
                }
                val result = firestoreService.addCustomer(currentUserId, customerFields, uiState.newCustomerUpiId)

                result.fold(
                    onSuccess = {
                        println("Customer added successfully!")
                        closeAddCustomerDialog()
                        loadCustomers() // Refresh the list
                    },
                    onFailure = { error ->
                        val errorMessage = when {
                            error.message?.contains("401") == true -> "Authentication failed. Please check service account configuration."
                            error.message?.contains("403") == true -> "Permission denied. Check Firestore security rules."
                            else -> "Failed to add customer: ${error.message}"
                        }
                        uiState = uiState.copy(
                            isAddingCustomer = false,
                            addCustomerError = errorMessage
                        )
                    }
                )
            } catch (e: Exception) {
                println("Exception in addCustomer: ${e.message}")
                uiState = uiState.copy(
                    isAddingCustomer = false,
                    addCustomerError = "Error: ${e.message}"
                )
            }
        }
    }

    fun loadTransactions() {
        viewModelScope.launch {
            val customerId = uiState.selectedCustomer?.name?.substringAfterLast('/') ?: run {
                uiState = uiState.copy(errorMessage = "No customer selected.")
                return@launch
            }

            uiState = uiState.copy(isLoading = true)
            val result = firestoreService.getTransactions(currentUserId, customerId)
            uiState = result.fold(
                onSuccess = { uiState.copy(isLoading = false, transactions = it) },
                onFailure = {
                    val errorMessage = when {
                        it.message?.contains("401") == true -> "Authentication failed. Please check service account configuration."
                        it.message?.contains("403") == true -> "Permission denied. Check Firestore security rules."
                        else -> "Failed to load transactions: ${it.message}"
                    }
                    uiState.copy(isLoading = false, errorMessage = errorMessage)
                }
            )
        }
    }

    fun loadUpiIds() {
        viewModelScope.launch {
            val customerAccNo = uiState.selectedCustomer?.fields?.Accno?.stringValue ?: run {
                uiState = uiState.copy(errorMessage = "No customer selected.")
                return@launch
            }

            uiState = uiState.copy(isLoading = true)
            val result = firestoreService.getUpiIdsForCustomer(currentUserId, customerAccNo)
            uiState = result.fold(
                onSuccess = {
                    uiState.copy(isLoading = false, upiIds = it)
                },
                onFailure = {
                    val errorMessage = "Failed to load UPI IDs: ${it.message}"
                    uiState.copy(isLoading = false, errorMessage = errorMessage)
                }
            )
        }
    }

    // Additional methods for full CRUD operations
    fun updateCustomer(customerId: String, updatedFields: CustomerFields, fieldsToUpdate: List<String>) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val result = firestoreService.updateCustomer(currentUserId, customerId, updatedFields, fieldsToUpdate)

            result.fold(
                onSuccess = {
                    println("Customer updated successfully!")
                    // Update the selected customer in the UI state
                    uiState = uiState.copy(
                        isLoading = false,
                        selectedCustomer = uiState.selectedCustomer?.copy(fields = updatedFields)
                    )
                    loadCustomers() // Refresh the main list
                },
                onFailure = { error ->
                    val errorMessage = "Failed to update customer: ${error.message}"
                    uiState = uiState.copy(isLoading = false, errorMessage = errorMessage)
                }
            )
        }
    }

    fun deleteCustomer(customerId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val result = firestoreService.deleteCustomer(currentUserId, customerId)

            result.fold(
                onSuccess = {
                    println("Customer deleted successfully!")
                    loadCustomers() // Refresh the list
                    clearSelectedCustomer() // Clear selection if the deleted customer was selected
                    uiState = uiState.copy(isLoading = false)
                },
                onFailure = { error ->
                    val errorMessage = "Failed to delete customer: ${error.message}"
                    uiState = uiState.copy(isLoading = false, errorMessage = errorMessage)
                }
            )
        }
    }

    // --- Add UPI ID Dialog Functions ---
    fun openAddUpiIdDialog() {
        uiState = uiState.copy(isAddUpiIdDialogOpen = true, newUpiId = "", addUpiIdError = null)
    }

    fun closeAddUpiIdDialog() {
        uiState = uiState.copy(isAddUpiIdDialogOpen = false, isAddingUpiId = false)
    }

    fun onNewUpiIdChange(upiId: String) {
        uiState = uiState.copy(newUpiId = upiId, addUpiIdError = null)
    }

    fun confirmAddUpiId() {
        val upiId = uiState.newUpiId
        val customerAccNo = uiState.selectedCustomer?.fields?.Accno?.stringValue

        if (upiId.isBlank()) {
            uiState = uiState.copy(addUpiIdError = "UPI ID cannot be empty.")
            return
        }
        if (customerAccNo == null) {
            uiState = uiState.copy(addUpiIdError = "No customer selected.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isAddingUpiId = true, addUpiIdError = null)
            val result = firestoreService.addUpiId(currentUserId, upiId, customerAccNo)
            result.fold(
                onSuccess = {
                    closeAddUpiIdDialog()
                    loadUpiIds() // Refresh the list
                },
                onFailure = {
                    uiState = uiState.copy(isAddingUpiId = false, addUpiIdError = it.message)
                }
            )
        }
    }

    // --- Edit Transaction Dialog Functions ---
    fun openEditTransactionDialog(transaction: FirestoreTransaction) {
        uiState = uiState.copy(
            isEditTransactionDialogOpen = true,
            transactionToEdit = transaction,
            editTransactionFine = transaction.fields.Fine.doubleValue.toString(),
            editTransactionError = null
        )
    }

    fun closeEditTransactionDialog() {
        uiState = uiState.copy(
            isEditTransactionDialogOpen = false,
            transactionToEdit = null,
            isEditingTransaction = false
        )
    }

    fun onEditTransactionFineChange(fine: String) {
        uiState = uiState.copy(editTransactionFine = fine, editTransactionError = null)
    }

    fun confirmEditTransaction() {
        val transaction = uiState.transactionToEdit ?: return
        val customerId = uiState.selectedCustomer?.name?.substringAfterLast('/') ?: return
        val transactionId = transaction.name.substringAfterLast('/')
        val newFine = uiState.editTransactionFine.toDoubleOrNull()

        if (newFine == null) {
            uiState = uiState.copy(editTransactionError = "Please enter a valid number for the fine.")
            return
        }

        val updatedFields = transaction.fields.copy(Fine = DoubleValue(newFine))
        viewModelScope.launch {
            uiState = uiState.copy(isEditingTransaction = true, editTransactionError = null)
            val result = firestoreService.updateTransaction(
                currentUserId,
                customerId,
                transactionId,
                updatedFields,
                listOf("Fine")
            )
            result.fold(
                onSuccess = {
                    closeEditTransactionDialog()
                    loadTransactions() // Refresh the list
                },
                onFailure = {
                    uiState = uiState.copy(isEditingTransaction = false, editTransactionError = it.message)
                }
            )
        }
    }

    // --- Delete Transaction Dialog Functions ---
    fun openDeleteTransactionDialog(transaction: FirestoreTransaction) {
        uiState = uiState.copy(isDeleteTransactionDialogOpen = true, transactionToDelete = transaction)
    }

    fun closeDeleteTransactionDialog() {
        uiState = uiState.copy(isDeleteTransactionDialogOpen = false, transactionToDelete = null, isDeletingTransaction = false)
    }

    fun confirmDeleteTransaction() {
        val transaction = uiState.transactionToDelete ?: return
        val customer = uiState.selectedCustomer ?: return

        val customerId = customer.name.substringAfterLast('/')
        val transactionId = transaction.name.substringAfterLast('/')
        val amountToSubtract = transaction.fields.amount.doubleValue
        val currentAmountPaid = customer.fields.amountPaid.doubleValue
        val newAmountPaid = (currentAmountPaid - amountToSubtract).coerceAtLeast(0.0)

        val updatedCustomerFields = customer.fields.copy(amountPaid = DoubleValue(newAmountPaid))

        viewModelScope.launch {
            uiState = uiState.copy(isDeletingTransaction = true, errorMessage = null)

            // 1. Update the customer's amountPaid first
            val updateResult = firestoreService.updateCustomer(
                currentUserId,
                customerId,
                updatedCustomerFields,
                listOf("amountPaid")
            )

            if (updateResult.isSuccess) {
                // 2. If customer update succeeds, delete the transaction
                val deleteResult = firestoreService.deleteTransaction(
                    currentUserId,
                    customerId,
                    transactionId
                )

                if (deleteResult.isSuccess) {
                    // 3. Both succeeded, refresh UI
                    closeDeleteTransactionDialog() // This will also set isDeletingTransaction = false
                    loadTransactions()
                    // Optimistically update the customer in the UI
                    uiState = uiState.copy(selectedCustomer = customer.copy(fields = updatedCustomerFields))
                } else {
                    // Transaction delete failed.
                    uiState = uiState.copy(
                        isDeletingTransaction = false,
                        errorMessage = "Error: Failed to delete transaction. ${deleteResult.exceptionOrNull()?.message}"
                    )
                }
            } else {
                // Customer update failed. Don't delete transaction.
                uiState = uiState.copy(
                    isDeletingTransaction = false,
                    errorMessage = "Failed to update customer. Transaction was not deleted. ${updateResult.exceptionOrNull()?.message}"
                )
            }
        }
    }


    fun addDetailedTransaction(
        accountNo: String,
        amount: Double,
        fine: Double = 0.0
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            try {
                // The FirestoreService will handle both transaction creation AND customer update
                val result = firestoreService.addTransactionByAccountNumber(
                    currentUserId, accountNo, amount, fine
                )

                if (result.isSuccess) {

                    // No need to call updateCustomerPaymentStatus here - it's handled in the service
                    // Just refresh the customers list to show updated amounts
                    loadCustomers()
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = null
                    )
                    println("Transaction added successfully with amount: $amount and fine: $fine")
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    // Method to get remaining amount to be paid for a customer
    fun getRemainingAmount(customer: FirestoreCustomer): Double {
        val totalAmount = customer.fields.Amount.doubleValue
        val amountPaid = customer.fields.amountPaid.doubleValue
        return maxOf(0.0, totalAmount - amountPaid)
    }

    // Method to get payment progress percentage
    fun getPaymentProgress(customer: FirestoreCustomer): Float {
        val totalAmount = customer.fields.Amount.doubleValue
        val amountPaid = customer.fields.amountPaid.doubleValue

        return if (totalAmount > 0) {
            (amountPaid / totalAmount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // Method to check if customer has completed payment
    fun isPaymentComplete(customer: FirestoreCustomer): Boolean {
        return getRemainingAmount(customer) <= 0.0
    }
    fun getPendingInstallmentCustomers(): List<PendingCustomerInfo> {
        val allCustomers = uiState.customers?.documents ?: return emptyList()
        val today = LocalDate.now(ZoneId.systemDefault())

        val pendingCustomers = allCustomers.mapNotNull { customer ->
            try {
                val amountPaid = customer.fields.amountPaid.doubleValue
                val installmentAmount = customer.fields.installmentAmount.doubleValue
                val openingDateString = customer.fields.OpeningDate.timestampValue

                if (installmentAmount <= 0 || openingDateString.isBlank()) {
                    return@mapNotNull null
                }

                val openingDate = Instant.parse(openingDateString)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                // --- Calculation for how many installments have been fully paid ---
                val paidInstallmentsCount = kotlin.math.floor(amountPaid / installmentAmount).toLong()

                // --- CORRECTED: Calculate the next due date directly ---
                // If 5 installments are paid, the 6th day (index 5) is the next due date.
                val nextInstallmentDueDate = openingDate.plusDays(paidInstallmentsCount)

                // Filter out customers who are not yet overdue
                if (nextInstallmentDueDate.isAfter(today)) {
                    return@mapNotNull null // This customer is up-to-date
                }

                // --- CORRECTED: Calculation for Amount Overdue ---
                // 1. Calculate the total number of installments that were due by today.
                val daysSinceOpening = ChronoUnit.DAYS.between(openingDate, today)
                val totalInstallmentsDueCount = if (daysSinceOpening < 0) 0 else daysSinceOpening + 1

                // 2. Calculate the total cumulative amount that should have been paid by today.
                val cumulativeAmountDue = totalInstallmentsDueCount * installmentAmount

                // 3. The actual amount overdue is the difference.
                val amountOverdue = cumulativeAmountDue - amountPaid

                // Ensure amountOverdue is not negative due to floating point inaccuracies or overpayment.
                val finalAmountOverdue = if (amountOverdue < 0) 0.0 else amountOverdue

                // --- Other Calculations ---
                val daysOverdue = ChronoUnit.DAYS.between(nextInstallmentDueDate, today)

                // Calculate remaining partial payment for the *current* installment being paid
                val amountForPaidInstallments = paidInstallmentsCount * installmentAmount
                val partialPaymentMade = amountPaid - amountForPaidInstallments
                val partialPaymentLeft = if (partialPaymentMade > 0) installmentAmount - partialPaymentMade else installmentAmount

                PendingCustomerInfo(
                    customer = customer,
                    nextDueDate = nextInstallmentDueDate,
                    daysOverdue = daysOverdue,
                    partialPaymentLeft = "%.2f".format(partialPaymentLeft).toDouble(),
                    totalAmountDue = "%.2f".format(finalAmountOverdue).toDouble() // Use the new correct value
                )

            } catch (e: Exception) {
                println("Error processing customer ${customer.fields.Accno.stringValue}: ${e.message}")
                null
            }
        }

        return pendingCustomers.sortedByDescending { it.daysOverdue }
    }


}

