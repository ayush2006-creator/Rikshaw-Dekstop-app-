package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.firebase.FirestoreCustomer
import org.example.project.firebase.CustomerFields
import org.example.project.firebase.StringValue
import org.example.project.firebase.DoubleValue
import org.example.project.viewmodels.CustomerViewModel
import org.example.project.viewmodels.CustomerUiState
import java.text.SimpleDateFormat
import java.util.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerViewModel: CustomerViewModel,
    customer: FirestoreCustomer,
    onBack: () -> Unit
) {
    val customerState = customerViewModel.uiState
    var showEditDialog by remember { mutableStateOf(false) }

    // This local state is crucial for reflecting optimistic updates from the ViewModel
    val currentCustomer by remember(customerState.selectedCustomer) {
        mutableStateOf(customerState.selectedCustomer ?: customer)
    }

    // Removed LaunchedEffect, as data loading is now handled by
    // customerViewModel.selectCustomer() before navigating here.

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
                            verticalAlignment = Alignment.CenterVertically
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
                            currentCustomer.fields.Amount.doubleValue - currentCustomer.fields.amountPaid.doubleValue
                        CustomerDetailRow(
                            "Remaining Amount",
                            "₹%.2f".format(remainingAmount),
                            isHighlight = remainingAmount > 0
                        )
                    }
                }
            }

            // --- UPI IDs Section ---
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active UPI IDs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { customerViewModel.openAddUpiIdDialog() }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add UPI ID")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        val upiIds = customerState.upiIds
                        if (upiIds.isNullOrEmpty()) {
                            Text(
                                "No active UPI IDs found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            upiIds.forEach { upiId ->
                                Text(
                                    text = upiId.fields.upiId.stringValue,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }


            // Transactions Section
            item {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
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
                                Row {
                                    IconButton(onClick = {
                                        customerViewModel.openEditTransactionDialog(transaction)
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Fine")
                                    }
                                    IconButton(onClick = {
                                        customerViewModel.openDeleteTransactionDialog(transaction)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Transaction", tint = MaterialTheme.colorScheme.error)
                                    }
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

    // --- DIALOGS ---

    // Edit Customer Dialog
    if (showEditDialog) {
        EditCustomerDialog(
            customer = currentCustomer,
            onDismiss = { showEditDialog = false },
            onSave = { updatedCustomerFields ->
                val customerId = currentCustomer.name.substringAfterLast('/')
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
            onConfirm = { customerViewModel.confirmAddUpiId() },
            onUpiIdChange = { customerViewModel.onNewUpiIdChange(it) }
        )
    }

    // Edit Transaction Dialog
    if (customerState.isEditTransactionDialogOpen) {
        EditTransactionDialog(
            uiState = customerState,
            onDismiss = { customerViewModel.closeEditTransactionDialog() },
            onConfirm = { customerViewModel.confirmEditTransaction() },
            onFineChange = { customerViewModel.onEditTransactionFineChange(it) }
        )
    }

    // Delete Transaction Dialog
    if (customerState.isDeleteTransactionDialogOpen) {
        DeleteTransactionDialog(
            uiState = customerState,
            onDismiss = { customerViewModel.closeDeleteTransactionDialog() },
            onConfirm = { customerViewModel.confirmDeleteTransaction() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerDialog(
    customer: FirestoreCustomer,
    onDismiss: () -> Unit,
    onSave: (CustomerFields) -> Unit
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

                    val updatedFields = customer.fields.copy(
                        Name = StringValue(name),
                        PhoneNo = StringValue(phoneNo),
                        VehicleNo = StringValue(vehicleNo),
                        installmentAmount = DoubleValue(installmentValue),
                        Amount = DoubleValue(amountValue)
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
    onConfirm: () -> Unit,
    onUpiIdChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New UPI ID") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isAddingUpiId) {
                    CircularProgressIndicator()
                } else {
                    OutlinedTextField(
                        value = uiState.newUpiId,
                        onValueChange = onUpiIdChange,
                        label = { Text("UPI ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.addUpiIdError != null
                    )
                    if (uiState.addUpiIdError != null) {
                        Text(
                            text = uiState.addUpiIdError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !uiState.isAddingUpiId
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isAddingUpiId) {
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
    onConfirm: () -> Unit,
    onFineChange: (String) -> Unit
) {
    val transaction = uiState.transactionToEdit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction Fine") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isEditingTransaction) {
                    CircularProgressIndicator()
                } else if (transaction != null) {
                    Text(
                        "Editing transaction from ${formatTimestamp(transaction.fields.date.timestampValue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
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
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !uiState.isEditingTransaction
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isEditingTransaction) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteTransactionDialog(
    uiState: CustomerUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Transaction?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isDeletingTransaction) {
                    CircularProgressIndicator()
                } else {
                    val amount = uiState.transactionToDelete?.fields?.amount?.doubleValue ?: 0.0
                    val date = formatTimestamp(uiState.transactionToDelete?.fields?.date?.timestampValue ?: "")
                    Text("Are you sure you want to delete the transaction of ₹$amount from $date?\n\nThis will also subtract ₹$amount from the customer's 'Amount Paid'. This action cannot be undone.")
                }
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !uiState.isDeletingTransaction,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isDeletingTransaction) {
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
            text = value,
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
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        timestamp // Fallback to raw string
    }
}

