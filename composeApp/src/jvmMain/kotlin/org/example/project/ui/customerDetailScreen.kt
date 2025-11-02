package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.firebase.FirestoreCustomer
import org.example.project.firebase.FirestoreTransaction // Added import
import org.example.project.firebase.DoubleValue // Added import
import org.example.project.firebase.FirestoreUpiId // Added import
import org.example.project.viewmodels.AuthViewModel
import org.example.project.viewmodels.CustomerViewModel
import org.example.project.viewmodels.CustomerUiState // Added import
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerViewModel: CustomerViewModel,
    authViewModel: AuthViewModel,
    customer: FirestoreCustomer,
    onBack: () -> Unit
) {
    val customerState = customerViewModel.uiState
    var showEditDialog by remember { mutableStateOf(false) }

    // Local state to hold current customer data
    var currentCustomer by remember { mutableStateOf(customer) }

    LaunchedEffect(customer.name) {
        customerViewModel.loadTransactions()
    }

    // Update current customer when the viewmodel state for selectedCustomer changes
    // This ensures our local `currentCustomer` updates after a successful API edit
    LaunchedEffect(customerState.selectedCustomer) {
        customerState.selectedCustomer?.let {
            if (it.name == currentCustomer.name) { // Only update if it's the same customer
                currentCustomer = it
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentCustomer.fields.Name.stringValue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Details Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Customer Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showEditDialog = true }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Customer")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        CustomerDetailRow("Account No", currentCustomer.fields.Accno.stringValue)
                        CustomerDetailRow("Name", currentCustomer.fields.Name.stringValue)
                        CustomerDetailRow("Phone No", currentCustomer.fields.PhoneNo.stringValue)
                        CustomerDetailRow("Vehicle No", currentCustomer.fields.VehicleNo.stringValue)
                        CustomerDetailRow(
                            "Opening Date",
                            formatTimestamp(currentCustomer.fields.OpeningDate.timestampValue)
                        )
                        CustomerDetailRow(
                            "Closing Date",
                            formatTimestamp(currentCustomer.fields.ClosingDate.timestampValue)
                        )
                        CustomerDetailRow("Total Amount", "₹${currentCustomer.fields.Amount.doubleValue}")
                        CustomerDetailRow(
                            "Amount Paid",
                            "₹${currentCustomer.fields.amountPaid.doubleValue}"
                        )
                        CustomerDetailRow(
                            "Installment Amount",
                            "₹${currentCustomer.fields.installmentAmount.doubleValue}"
                        )

                        val remainingAmount =
                            customerViewModel.getRemainingAmount(currentCustomer)
                        CustomerDetailRow(
                            "Remaining Amount",
                            "₹%.2f".format(remainingAmount),
                            isHighlight = remainingAmount > 0
                        )

                        // --- UPI IDs Section ---
                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            text = "UPI IDs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        customerState.customerUpiIds?.let { upiIds ->
                            if (upiIds.isEmpty()) {
                                Text(
                                    "No UPI IDs added.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    upiIds.forEach { upiIdDoc ->
                                        Text(
                                            upiIdDoc.fields.upiId.stringValue,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        } ?: run {
                            // Show shimmer or placeholder while loading
                            Text(
                                "Loading UPI IDs...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { customerViewModel.openAddUpiIdDialog() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add New UPI ID")
                        }
                        // --- End of UPI IDs Section ---
                    }
                }
            }

            // Transactions Section
            item {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (customerState.isLoading && customerState.transactions == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!customerState.transactions?.documents.isNullOrEmpty()) {
                items(customerState.transactions!!.documents!!) { transaction ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Payment: ₹${transaction.fields.amount.doubleValue}",
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            supportingContent = {
                                Text("Date: ${formatTimestamp(transaction.fields.date.timestampValue)}")
                                Text("Balance: ₹%.2f".format(transaction.fields.Balance.doubleValue))
                                Text("Fine: ₹${transaction.fields.Fine.doubleValue}")
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    customerViewModel.openEditTransactionDialog(transaction)
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Fine")
                                }
                            }
                        )
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No transactions found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // Edit Customer Dialog
    if (showEditDialog) {
        EditCustomerDialog(
            customer = currentCustomer,
            onDismiss = { showEditDialog = false },
            onSave = { updatedCustomerFields ->
                val customerId = currentCustomer.name.substringAfterLast('/')

                // Call the API to update
                customerViewModel.updateCustomer(
                    customerId = customerId,
                    updatedFields = updatedCustomerFields,
                    fieldsToUpdate = listOf("Name", "PhoneNo", "VehicleNo", "Amount", "installmentAmount")
                )
                showEditDialog = false
            }
        )
    }

    // Add UPI ID Dialog
    if (customerState.isAddUpiIdDialogOpen) {
        AddUpiIdDialog(
            uiState = customerState,
            onDismiss = { customerViewModel.closeAddUpiIdDialog() },
            onSave = { customerViewModel.addUpiIdForCustomer() },
            onUpiIdChange = { customerViewModel.onNewUpiIdChange(it) }
        )
    }

    // Edit Transaction Dialog
    if (customerState.isEditTransactionDialogOpen) {
        EditTransactionDialog(
            uiState = customerState,
            onDismiss = { customerViewModel.closeEditTransactionDialog() },
            onSave = { customerViewModel.updateTransactionFine() },
            onFineChange = { customerViewModel.onEditTransactionFineChange(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerDialog(
    customer: FirestoreCustomer,
    onDismiss: () -> Unit,
    onSave: (org.example.project.firebase.CustomerFields) -> Unit
) {
    var name by remember { mutableStateOf(customer.fields.Name.stringValue) }
    var phoneNo by remember { mutableStateOf(customer.fields.PhoneNo.stringValue) }
    var vehicleNo by remember { mutableStateOf(customer.fields.VehicleNo.stringValue) }
    var amount by remember { mutableStateOf(customer.fields.Amount.doubleValue.toString()) }
    var installmentAmount by remember { mutableStateOf(customer.fields.installmentAmount.doubleValue.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Customer Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNo,
                    onValueChange = { phoneNo = it; errorMessage = null },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = vehicleNo,
                    onValueChange = { vehicleNo = it; errorMessage = null },
                    label = { Text("Vehicle Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; errorMessage = null },
                    label = { Text("Total Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("₹") }
                )

                OutlinedTextField(
                    value = installmentAmount,
                    onValueChange = { installmentAmount = it; errorMessage = null },
                    label = { Text("Installment Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("₹") }
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Note: Account Number and Opening Date cannot be changed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Name cannot be empty"
                        return@Button
                    }

                    val amountValue = amount.toDoubleOrNull()
                    val installmentValue = installmentAmount.toDoubleOrNull()

                    if (amountValue == null || amountValue <= 0) {
                        errorMessage = "Please enter a valid total amount"
                        return@Button
                    }

                    if (installmentValue == null || installmentValue <= 0) {
                        errorMessage = "Please enter a valid installment amount"
                        return@Button
                    }

                    val updatedFields = org.example.project.firebase.CustomerFields(
                        Accno = customer.fields.Accno,
                        Name = org.example.project.firebase.StringValue(name),
                        PhoneNo = org.example.project.firebase.StringValue(phoneNo),
                        VehicleNo = org.example.project.firebase.StringValue(vehicleNo),
                        OpeningDate = customer.fields.OpeningDate,
                        ClosingDate = customer.fields.ClosingDate,
                        installmentAmount = org.example.project.firebase.DoubleValue(installmentValue),
                        Amount = org.example.project.firebase.DoubleValue(amountValue),
                        amountPaid = customer.fields.amountPaid
                    )
                    onSave(updatedFields)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUpiIdDialog(
    uiState: CustomerUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpiIdChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New UPI ID") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isAddingUpiId) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    OutlinedTextField(
                        value = uiState.newUpiId,
                        onValueChange = onUpiIdChange,
                        label = { Text("UPI ID (e.g., user@okhdfcbank)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.addUpiIdError != null
                    )
                    if (uiState.addUpiIdError != null) {
                        Text(
                            text = uiState.addUpiIdError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !uiState.isAddingUpiId
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !uiState.isAddingUpiId
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    uiState: CustomerUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onFineChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction Fine") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isUpdatingTransaction) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(
                        "Only the fine amount can be edited. To change the payment amount, please delete and recreate the transaction.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = uiState.editTransactionFine,
                        onValueChange = onFineChange,
                        label = { Text("Fine Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("₹") },
                        isError = uiState.editTransactionError != null
                    )
                    if (uiState.editTransactionError != null) {
                        Text(
                            text = uiState.editTransactionError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !uiState.isUpdatingTransaction
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !uiState.isUpdatingTransaction
            ) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun CustomerDetailRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.ifBlank { "N/A" }, // Show N/A if value is blank
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlight && value.contains("₹") && !value.contains("₹0.00")) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

fun formatTimestamp(timestamp: String): String {
    if (timestamp.isBlank()) return "N/A"
    return try {
        val instant = Instant.parse(timestamp)
        val date = Date.from(instant)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(date)
    } catch (e: Exception) {
        timestamp // Return original string if parsing fails
    }
}
