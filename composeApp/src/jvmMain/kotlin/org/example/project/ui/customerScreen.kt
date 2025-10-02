package org.example.project.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.project.firebase.FirestoreCustomer
import org.example.project.viewmodels.AuthViewModel
import org.example.project.viewmodels.CustomerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    customerViewModel: CustomerViewModel,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onCustomerClick: (FirestoreCustomer) -> Unit,
    onNavigateToPending: () -> Unit
) {
    val customerState = customerViewModel.uiState
    var isAddTransactionDialogOpen by remember { mutableStateOf(false) }

    // NEW: State for the search query
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        customerViewModel.loadCustomers()
    }

    // NEW: Memoized filtered list. It recalculates only when searchQuery or customers list changes.
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
        if (customerState.isAddCustomerDialogOpen) {
            AddCustomerDialog(
                viewModel = customerViewModel,
                onDismiss = customerViewModel::closeAddCustomerDialog,
                onConfirm = { customerViewModel.addCustomer() }
            )
        }

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

        // MODIFIED: Changed Box to Column to hold the search bar and the list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // NEW: Search Bar UI
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

            // MODIFIED: Box now wraps only the content area to keep status messages centered
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (customerState.isLoading) {
                    CircularProgressIndicator()
                } else if (customerState.errorMessage != null) {
                    Text("Error loading customers: ${customerState.errorMessage}")
                } else if (customerState.customers?.documents.isNullOrEmpty()) {
                    Text("No customers found. Add one!")
                } else if (filteredCustomers.isEmpty()) { // NEW: Handle case where search yields no results
                    Text("No matching customers found.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // MODIFIED: Adjusted padding to account for the search bar
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // MODIFIED: Use the filtered list
                        items(filteredCustomers) { customerDoc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCustomerClick(customerDoc) }
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(customerDoc.fields.Name.stringValue)
                                    },
                                    supportingContent = {
                                        Text("Acc: ${customerDoc.fields.Accno.stringValue} | Vehicle: ${customerDoc.fields.VehicleNo.stringValue}")
                                    },
                                    trailingContent = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("₹${customerDoc.fields.Amount.doubleValue}")
                                            Text(
                                                "Paid: ₹${customerDoc.fields.amountPaid.doubleValue}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
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
fun AddTransactionDialog(
    customers: List<FirestoreCustomer>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double,Double) -> Unit
) {

    var selectedAccountNo by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var fine by remember { mutableStateOf("") }
    fine = 0.toString()

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
                    isError = isError
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
                    label = { Text("Fine") },
                    placeholder = { Text("Enter payment fine") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") })

                if (isError) {
                    Text(
                        text = when {
                            selectedAccountNo.isEmpty() -> "Please select a customer"
                            amount.isEmpty() -> "Please enter an amount"
                            amount.toDoubleOrNull() == null -> "Please enter a valid amount"
                            amount.toDoubleOrNull()!! <= 0 -> "Amount must be greater than 0"
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
                            val fineamount  = fine.toDouble()
                            if (selectedAccountNo.isNotEmpty() && amountValue != null && amountValue > 0) {
                                onConfirm(selectedAccountNo, amountValue, fineamount)
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