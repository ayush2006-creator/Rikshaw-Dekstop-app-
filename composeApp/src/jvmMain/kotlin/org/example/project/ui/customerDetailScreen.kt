package org.example.project.ui


import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Edit

import androidx.compose.material3.*

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

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



    LaunchedEffect(customer.name) {

        customerViewModel.loadTransactions()

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text(customer.fields.Name.stringValue) },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")

                    }

                },


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
                                onClick = {},
                                modifier = Modifier.padding(bottom = 18.dp, start = 30.dp)
                                    .weight(1f).scale(1.5f)
                            ) {

                                Icon(Icons.Default.Edit, contentDescription = "Edit")

                            }
                        }



                        CustomerDetailRow("Account No", customer.fields.Accno.stringValue)

                        CustomerDetailRow("Name", customer.fields.Name.stringValue)

                        CustomerDetailRow("Phone No", customer.fields.PhoneNo.stringValue)

                        CustomerDetailRow("Vehicle No", customer.fields.VehicleNo.stringValue)

                        CustomerDetailRow(

                            "Opening Date",

                            formatTimestamp(customer.fields.OpeningDate.timestampValue)

                        )

                        CustomerDetailRow(

                            "Closing Date",

                            formatTimestamp(customer.fields.ClosingDate.timestampValue)

                        )

                        CustomerDetailRow("Total Amount", "₹${customer.fields.Amount.doubleValue}")

                        CustomerDetailRow(
                            "Amount Paid",
                            "₹${customer.fields.amountPaid.doubleValue}"
                        )

                        CustomerDetailRow(
                            "Installment Amount",
                            "₹${customer.fields.installmentAmount.doubleValue}"
                        )


// Calculate remaining amount

                        val remainingAmount =
                            customer.fields.Amount.doubleValue - customer.fields.amountPaid.doubleValue

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


// Helper function to format timestamp

fun formatTimestamp(timestamp: String): String {

    return try {



        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())


        timestamp

    } catch (e: Exception) {

        timestamp

    }

}