package org.example.project.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.project.firebase.FirestoreCustomer
import org.example.project.viewmodels.CustomerViewModel
import org.example.project.viewmodels.UploadDialogState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    customerViewModel: CustomerViewModel,
    onSignOut: () -> Unit,
    onCustomerClick: (FirestoreCustomer) -> Unit,
    onNavigateToPending: () -> Unit
) {
    val customerState = customerViewModel.uiState
    var isAddTransactionDialogOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        customerViewModel.loadCustomers()
    }

    val filteredCustomers = remember(searchQuery, customerState.customers?.documents) {
        val allCustomers = customerState.customers?.documents ?: emptyList()
        if (searchQuery.isBlank()) {
            allCustomers
        } else {
            allCustomers.filter { customer ->
                customer.fields.Name.stringValue.contains(searchQuery, ignoreCase = true) ||
                        customer.fields.Accno.stringValue.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Customers") },
                actions = {
                    IconButton(onClick = onNavigateToPending) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "View Pending Installments"
                        )
                    }
                    Button(onClick = onSignOut) {
                        Text("Sign Out")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { customerViewModel.openUploadDialog() },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Upload Statement")
                }
                FloatingActionButton(
                    onClick = { isAddTransactionDialogOpen = true },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Payment, contentDescription = "Add Transaction")
                }
                FloatingActionButton(
                    onClick = customerViewModel::openAddCustomerDialog
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Customer")
                }
            }
        }
    ) { padding ->
        // Add Customer Dialog
        if (customerState.isAddCustomerDialogOpen) {
            AddCustomerDialog(
                viewModel = customerViewModel,
                onDismiss = customerViewModel::closeAddCustomerDialog,
                onConfirm = { customerViewModel.addCustomer() }
            )
        }

        // Add Transaction Dialog
        if (isAddTransactionDialogOpen) {
            AddTransactionDialog(
                customers = customerState.customers?.documents ?: emptyList(),
                onDismiss = { isAddTransactionDialogOpen = false },
                onConfirm = { accountNo, amount, fine ->
                    customerViewModel.addDetailedTransaction(accountNo, amount, fine)
                    isAddTransactionDialogOpen = false
                }
            )
        }

        // Statement Upload Dialog
        if (customerState.isUploadDialogOpen) {
            StatementUploadDialog(
                uiState = customerState,
                onDismiss = { customerViewModel.closeUploadDialog() },
                onProcess = { inputStream ->
                    customerViewModel.processBankStatement(inputStream)
                }
            )
        }

        // Delete Customer Dialog
        if (customerState.isDeleteCustomerDialogOpen && customerState.customerToDelete != null) {
            DeleteCustomerDialog(
                customer = customerState.customerToDelete!!,
                onDismiss = customerViewModel::closeDeleteCustomerDialog,
                onConfirm = customerViewModel::confirmDeleteCustomer,
                isDeleting = customerState.isDeletingCustomer
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Search by name or account no.") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search Icon")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (customerState.isLoading) {
                    CircularProgressIndicator()
                } else if (customerState.errorMessage != null) {
                    Text("Error: ${customerState.errorMessage}")
                } else if (customerState.customers?.documents.isNullOrEmpty()) {
                    Text("No customers found. Add one!")
                } else if (filteredCustomers.isEmpty()) {
                    Text("No matching customers found.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCustomers) { customerDoc ->
                            CustomerListItem(
                                customer = customerDoc,
                                onClick = { onCustomerClick(customerDoc) },
                                onDelete = { customerViewModel.openDeleteCustomerDialog(customerDoc) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerListItem(
    customer: FirestoreCustomer,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp)
            ) {
                Text(
                    text = customer.fields.Name.stringValue,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Acc: ${customer.fields.Accno.stringValue} | Vehicle: ${customer.fields.VehicleNo.stringValue}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Amount info
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "₹${customer.fields.Amount.doubleValue}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Paid: ₹${"%.2f".format(customer.fields.amountPaid.doubleValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete button
            IconButton(
                onClick = { onDelete() },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Customer"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    customers: List<FirestoreCustomer>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double) -> Unit
) {
    var selectedAccountNo by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var fine by remember { mutableStateOf("0") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Transaction",
                    style = MaterialTheme.typography.headlineSmall
                )

                SearchableCustomerDropdown(
                    customers = customers,
                    selectedAccountNo = selectedAccountNo,
                    onAccountSelected = { accountNo ->
                        selectedAccountNo = accountNo
                    },
                    isError = isError && selectedAccountNo.isEmpty()
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        isError = false
                    },
                    label = { Text("Amount") },
                    placeholder = { Text("Enter payment amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    isError = isError && (amount.isEmpty() || amount.toDoubleOrNull() == null || amount.toDoubleOrNull()!! <= 0)
                )

                OutlinedTextField(
                    value = fine,
                    onValueChange = {
                        fine = it
                        isError = false
                    },
                    label = { Text("Fine (Optional)") },
                    placeholder = { Text("Enter fine amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") }
                )

                if (isError) {
                    Text(
                        text = when {
                            selectedAccountNo.isEmpty() -> "Please select a customer"
                            amount.isEmpty() -> "Please enter an amount"
                            amount.toDoubleOrNull() == null -> "Please enter a valid amount"
                            amount.toDoubleOrNull()!! <= 0 -> "Amount must be greater than 0"
                            fine.toDoubleOrNull() == null -> "Please enter a valid fine (or 0)"
                            else -> ""
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull()
                            val fineAmount = fine.toDoubleOrNull() ?: 0.0
                            if (selectedAccountNo.isNotEmpty() && amountValue != null && amountValue > 0 && fineAmount != null) {
                                onConfirm(selectedAccountNo, amountValue, fineAmount)
                            } else {
                                isError = true
                            }
                        }
                    ) {
                        Text("Add Transaction")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableCustomerDropdown(
    customers: List<FirestoreCustomer>,
    selectedAccountNo: String,
    onAccountSelected: (String) -> Unit,
    isError: Boolean = false
) {
    var inputText by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }

    val suggestions = if (inputText.length >= 2 && showDropdown) {
        customers.filter {
            it.fields.Accno.stringValue.contains(inputText, ignoreCase = true) ||
                    it.fields.Name.stringValue.contains(inputText, ignoreCase = true)
        }.take(5)
    } else emptyList()

    Column {
        OutlinedTextField(
            value = inputText,
            onValueChange = { newText ->
                inputText = newText
                showDropdown = newText.isNotEmpty()
                if (newText.isEmpty()) {
                    onAccountSelected("")
                }
            },
            label = { Text("Customer Account") },
            placeholder = { Text("Type account number or name") },
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            singleLine = true,
            trailingIcon = {
                if (inputText.isNotEmpty()) {
                    IconButton(onClick = {
                        inputText = ""
                        onAccountSelected("")
                        showDropdown = false
                    }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            }
        )

        if (suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(suggestions) { customer ->
                        Text(
                            text = "${customer.fields.Name.stringValue} - ${customer.fields.Accno.stringValue}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val accountNo = customer.fields.Accno.stringValue
                                    inputText = "${customer.fields.Name.stringValue} (${accountNo})"
                                    onAccountSelected(accountNo)
                                    showDropdown = false
                                }
                                .padding(16.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (customer != suggestions.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementUploadDialog(
    uiState: org.example.project.viewmodels.CustomerUiState,
    onDismiss: () -> Unit,
    onProcess: (InputStream) -> Unit
) {
    var isError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Upload Bank Statement",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (uiState.uploadDialogState) {
                    UploadDialogState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Processing... Do not close.")
                            }
                        }
                    }

                    UploadDialogState.Success, UploadDialogState.Error -> {
                        Text(
                            "Processing Results",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            SelectionContainer {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = uiState.uploadSummary,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (uiState.uploadDialogState == UploadDialogState.Error && uiState.uploadSummary.contains("FATAL"))
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Close")
                            }
                        }
                    }

                    UploadDialogState.Idle -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "Select an Excel bank statement file (.xls or .xlsx) to process.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    try {
                                        val fileDialog = FileDialog(null as Frame?, "Select Excel File", FileDialog.LOAD)
                                        fileDialog.setFile("*.xlsx;*.xls")
                                        fileDialog.isVisible = true

                                        val selectedFile = fileDialog.file
                                        val selectedDirectory = fileDialog.directory

                                        if (selectedFile != null && selectedDirectory != null) {
                                            val file = File(selectedDirectory, selectedFile)
                                            if (file.exists() && file.canRead()) {
                                                onProcess(FileInputStream(file))
                                                isError = null
                                            } else {
                                                isError = "Cannot read the selected file."
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isError = "Error selecting file: ${e.message}"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Select Excel File")
                            }

                            if (isError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = isError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteCustomerDialog(
    customer: FirestoreCustomer,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isDeleting: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Delete Customer")
            }
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete this customer?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = customer.fields.Name.stringValue,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Acc: ${customer.fields.Accno.stringValue}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Vehicle: ${customer.fields.VehicleNo.stringValue}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚠️ This action cannot be undone. All transactions and UPI IDs associated with this customer will remain in the database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isDeleting) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}