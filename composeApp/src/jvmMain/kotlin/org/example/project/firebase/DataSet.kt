package org.example.project.firebase



import kotlinx.serialization.Serializable

@Serializable data class StringValue(val stringValue: String)
@Serializable data class DoubleValue(val doubleValue: Double)
@Serializable data class TimestampValue(val timestampValue: String)
@Serializable data class IntegerValue(val integerValue: Long)
@Serializable data class BooleanValue(val booleanValue: Boolean)

// UPI ID document structure
@Serializable
data class UpiIdFields(
    val upiId: StringValue,
    val customerId: StringValue,
    val isActive: BooleanValue,
    val createdAt: TimestampValue
)
@Serializable data class FirestoreUpiId(val name: String, val fields: UpiIdFields)
@Serializable data class UpiIdListResponse(val documents: List<FirestoreUpiId>? = null)

// Enhanced Customer document structure
@Serializable
data class CustomerFields(
    val Accno: StringValue,
    val Name: StringValue,
    val PhoneNo: StringValue,
    val VehicleNo: StringValue,
    val OpeningDate: TimestampValue,
    val ClosingDate: TimestampValue,
    val installmentAmount: DoubleValue,
    val Amount: DoubleValue,
    val amountPaid: DoubleValue,



    )

@Serializable data class FirestoreCustomer(val name: String, val fields: CustomerFields)
@Serializable data class CustomerListResponse(val documents: List<FirestoreCustomer>? = null)

// Enhanced Transaction document structure
@Serializable
data class TransactionFields(
    val amount: DoubleValue,
    val Balance: DoubleValue,
    val date: TimestampValue,
    val Fine: DoubleValue,
    val transactionType: StringValue = StringValue("payment"),
    val description: StringValue = StringValue(""),
    val installmentsCovered: IntegerValue = IntegerValue(1) // How many installments this payment covers
)
@Serializable data class FirestoreTransaction(val name: String, val fields: TransactionFields)
@Serializable data class TransactionListResponse(val documents: List<FirestoreTransaction>? = null)

// Defaulter data class
data class DefaulterInfo(
    val customerId: String,
    val customerName: String,
    val accountNo: String,
    val phoneNo: String,
    val daysDefaulted: Long,
    val amountDue: Double,
    val fineAmount: Double,
    val lastPaymentDate: String?
)
