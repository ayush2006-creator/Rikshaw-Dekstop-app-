package org.example.project.ui



import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.viewmodels.CustomerViewModel
import org.example.project.viewmodels.PendingCustomerInfo
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingInstallmentsScreen(
    viewModel: CustomerViewModel,
    onNavigateBack: () -> Unit
) {
    // Get the sorted list of customers with pending payments from the ViewModel
    val pendingCustomers = viewModel.getPendingInstallmentCustomers()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Installments") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (pendingCustomers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pending installments found.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingCustomers) { pendingInfo ->
                        PendingCustomerCard(pendingInfo = pendingInfo)
                    }
                }
            }
        }
    }
}

@Composable
fun PendingCustomerCard(pendingInfo: PendingCustomerInfo) {
    val customerFields = pendingInfo.customer.fields
    val overdueColor = when {
        pendingInfo.daysOverdue > 7 -> MaterialTheme.colorScheme.errorContainer
        pendingInfo.daysOverdue > 2 -> Color(0xFFFFF3E0) // A light orange
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Customer Name and Account Number
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customerFields.Name.stringValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A/C: ${customerFields.Accno.stringValue}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Overdue Status Chip
                Box(
                    modifier = Modifier
                        .background(color = overdueColor, shape = RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (pendingInfo.daysOverdue == 0L) "Due Today" else "${pendingInfo.daysOverdue} days overdue",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Due Date and Partial Payment Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                InfoColumn(
                    title = "Next Due Date",
                    value = pendingInfo.nextDueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                )
                InfoColumn(
                    title = "Installment",
                    value = "₹${customerFields.installmentAmount.doubleValue}"
                )
                if (pendingInfo.partialPaymentLeft>0){
                InfoColumn(
                    title = "Partial payment left",
                    value = "₹${pendingInfo.partialPaymentLeft}",
                    isHighlighted = true
                )}
                InfoColumn(
                    title = "Pending Amount",
                    value = "₹${pendingInfo.totalAmountDue}",
                    isHighlighted = true
                )
            }
        }
    }
}

@Composable
private fun InfoColumn(title: String, value: String, isHighlighted: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
