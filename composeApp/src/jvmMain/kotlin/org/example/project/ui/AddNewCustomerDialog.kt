package org.example.project.ui



import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.viewmodels.CustomerUiState
import org.example.project.viewmodels.CustomerViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale


@Composable
fun DateFields(

    closingDate: String,
    onClosingDateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showOpeningDatePicker by remember { mutableStateOf(false) }
    var showClosingDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = dateFormatter.format(Date())

    // Parse dates for validation

    val openingDate = try { dateFormatter.parse(today) } catch (e: Exception) { Date() }
    val selectedClosingDate = try { dateFormatter.parse(closingDate) } catch (e: Exception) { null }

    // Validation
    val isClosingDateValid = selectedClosingDate?.let { it.after(openingDate) } ?: true

    Column(modifier = modifier) {
        // Opening Date Field
        OutlinedTextField(
            value = today,
            onValueChange = {},
            label = { Text("Opening Date") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showOpeningDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select opening date")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Closing Date Field
        OutlinedTextField(
            value = closingDate,
            onValueChange = {},
            label = { Text("Closing Date") },
            readOnly = true,
            isError = !isClosingDateValid,
            supportingText = {
                if (!isClosingDateValid) {
                    Text(
                        text = "Closing date must be after opening date",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            trailingIcon = {
                IconButton(onClick = { showClosingDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select closing date")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Opening Date Picker Dialog
    if (showOpeningDatePicker) {
        DatePickerDialog(
            onDateSelected = { selectedDate ->
                // Handle opening date change if needed
                showOpeningDatePicker = false
            },
            onDismiss = { showOpeningDatePicker = false },
            initialDate = openingDate
        )
    }

    // Closing Date Picker Dialog
    if (showClosingDatePicker) {
        DatePickerDialog(
            onDateSelected = { selectedDate ->
                val formattedDate = dateFormatter.format(selectedDate)
                onClosingDateChange(formattedDate)
                showClosingDatePicker = false
            },
            onDismiss = { showClosingDatePicker = false },
            initialDate = selectedClosingDate ?: Date(),
            minDate = openingDate // Restrict selection to dates after opening date
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit,
    initialDate: Date,
    minDate: Date? = null
) {
    val calendar = Calendar.getInstance()
    calendar.time = initialDate

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.time,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return minDate?.let { utcTimeMillis >= it.time } ?: true
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Date(millis))
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun AddCustomerDialog(
    viewModel: CustomerViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val uiState = viewModel.uiState
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Customer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = uiState.newCustomerAccNo, onValueChange = viewModel::onNewCustomerAccNoChange, label = { Text("Account Number") })
                OutlinedTextField(value = uiState.newCustomerName, onValueChange = viewModel::onNewCustomerNameChange, label = { Text("Name") })
                OutlinedTextField(value = uiState.newCustomerPhoneNo, onValueChange = viewModel::onNewCustomerPhoneNoChange, label = { Text("Phone Number") })
                OutlinedTextField(value = uiState.newCustomerVehicleNo, onValueChange = viewModel::onNewCustomerVehicleNoChange, label = { Text("Vehicle Number") })
                OutlinedTextField(value = uiState.newCustomerInstallmentAmount, onValueChange = viewModel::onNewCustomerInstallmentChange, label = { Text("Installment Amount") })
                OutlinedTextField(value = uiState.newCustomerAmount, onValueChange = viewModel::onNewCustomerAmountChange, label = { Text("Total Amount") })
                DateFields(
                    closingDate = uiState.newCustomerClosingDate,
                    onClosingDateChange = viewModel::onNewCustomerClosingDateChange
                )
                OutlinedTextField(value = uiState.newCustomerUpiId, onValueChange = viewModel::onNewCustomerUpiIdChange, label = { Text("UPI ID") })
                if (uiState.addCustomerError != null) {
                    Text(
                        text = uiState.addCustomerError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !uiState.isAddingCustomer) {
                if (uiState.isAddingCustomer) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
