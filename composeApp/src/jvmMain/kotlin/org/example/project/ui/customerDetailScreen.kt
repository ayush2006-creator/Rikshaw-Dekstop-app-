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
import org.example.project.viewmodels.AuthViewModel
import org.example.project.viewmodels.CustomerViewModel
import java.text.SimpleDateFormat
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

    // Update current customer when the state changes
    LaunchedEffect(customerState.selectedCustomer) {
        customerState.selectedCustomer?.let {
            currentCustomer = it
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
                        Row {
                            Text(
                                text = "Customer Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp).weight(5f)
                            )
                            Spacer(modifier = Modifier.weight(6f))
                            IconButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.padding(bottom = 18.dp, start = 30.dp)
                                    .weight(1f).scale(1.5f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }

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
                            "₹$remainingAmount",
                            isHighlight = remainingAmount > 0
                        )
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

            if (customerState.isLoading) {
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
                                Text("Balance: ${(transaction.fields.Balance.doubleValue)}")
                                Text("fine: ${(transaction.fields.Fine.doubleValue)}")
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

    // Edit Dialog
    if (showEditDialog) {
        EditCustomerDialog(
            customer = currentCustomer,
            onDismiss = { showEditDialog = false },
            onSave = { updatedCustomer ->
                val customerId = currentCustomer.name.substringAfterLast('/')

                // Optimistically update the local state immediately
                currentCustomer = currentCustomer.copy(
                    fields = updatedCustomer
                )

                // Then call the API
                customerViewModel.updateCustomer(
                    customerId = customerId,
                    updatedFields = updatedCustomer,
                    fieldsToUpdate = listOf("Name", "PhoneNo", "VehicleNo", "Amount", "installmentAmount")
                )
                showEditDialog = false
            }
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
            color = if (isHighlight && value.contains("₹") && !value.contains("₹0")) {
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
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        timestamp
    } catch (e: Exception) {
        timestamp
    }
}