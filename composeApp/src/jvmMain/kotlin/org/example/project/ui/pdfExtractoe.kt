package org.example.project.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import technology.tabula.ObjectExtractor
import technology.tabula.extractors.BasicExtractionAlgorithm
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.JPasswordField
import javax.swing.JOptionPane

@Composable
fun PDFTableExtractor() {
    var isProcessing by remember { mutableStateOf(false) }
    var lastProcessedFile by remember { mutableStateOf<String?>(null) }
    var processingStatus by remember { mutableStateOf("") }
    var columnIndex by remember { mutableStateOf("1") }
    var extractedData by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PDF Table Extractor",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Extract specific columns from PDF tables and print to terminal",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Column index input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Column Index:",
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            OutlinedTextField(
                value = columnIndex,
                onValueChange = { columnIndex = it },
                modifier = Modifier.width(100.dp),
                singleLine = true,
                label = { Text("Index") },
                enabled = !isProcessing
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // File selection area
        Box(
            modifier = Modifier
                .size(300.dp, 150.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 2.dp,
                    color = if (isProcessing) MaterialTheme.colors.primary else Color.Gray,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    color = if (isProcessing) MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    else Color.Gray.copy(alpha = 0.1f)
                )
                .clickable(enabled = !isProcessing) {
                    val colIndex = columnIndex.toIntOrNull() ?: 1
                    selectAndProcessPDF(colIndex) { fileName, status, data ->
                        lastProcessedFile = fileName
                        processingStatus = status
                        extractedData = data
                        isProcessing = false
                    }
                    isProcessing = true
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing PDF...",
                        fontSize = 16.sp,
                        color = MaterialTheme.colors.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload PDF",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Click to select PDF file",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status information
        if (processingStatus.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Status:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = processingStatus,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    if (lastProcessedFile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "File: $lastProcessedFile",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        if (extractedData.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Extracted ${extractedData.size} items from column $columnIndex",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        if (!isProcessing && processingStatus.isEmpty()) {
            Text(
                text = "No PDF processed yet",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun selectAndProcessPDF(
    columnIndex: Int,
    onComplete: (String?, String, List<String>) -> Unit
) {
    val fileChooser = JFileChooser().apply {
        dialogTitle = "Select PDF File"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter("PDF Files", "pdf")
    }

    val result = fileChooser.showOpenDialog(null)

    if (result == JFileChooser.APPROVE_OPTION) {
        val selectedFile = fileChooser.selectedFile
        extractTableDataFromPDF(selectedFile, columnIndex) { success, message, data ->
            onComplete(selectedFile.name, message, data)
        }
    } else {
        onComplete(null, "File selection cancelled", emptyList())
    }
}

private fun extractTableDataFromPDF(
    file: File,
    columnIndex: Int,
    onComplete: (Boolean, String, List<String>) -> Unit
) {
    var password: String? = null
    var document: PDDocument? = null

    try {
        println("\n" + "=".repeat(80))
        println("PROCESSING PDF: ${file.name}")
        println("Extracting column index: $columnIndex")
        println("=".repeat(80))

        // Try to load document, prompt for password if needed
        document = try {
            PDDocument.load(file)
        } catch (e: InvalidPasswordException) {
            // Prompt for password
            password = promptForPassword()
            if (password != null) {
                PDDocument.load(file, password)
            } else {
                onComplete(false, "❌ Password required but not provided", emptyList())
                return
            }
        }

        val extractedData = mutableListOf<String>()
        val objectExtractor = ObjectExtractor(document)

        // Process each page
        for (pageNum in 1..document.numberOfPages) {
            val page = objectExtractor.extract(pageNum)
            val extractor = BasicExtractionAlgorithm()
            val tables = extractor.extract(page)

            println("\nPage $pageNum - Found ${tables.size} tables")

            tables.forEachIndexed { tableIndex, table ->
                println("  Table ${tableIndex + 1}:")
                val rows = table.rows

                rows.forEachIndexed { rowIndex, row ->
                    if (row.size > columnIndex) {
                        val cellContent = row[columnIndex].text.trim()
                        if (cellContent.isNotEmpty()) {
                            extractedData.add(cellContent)
                            println("    Row ${rowIndex + 1}, Col ${columnIndex + 1}: $cellContent")
                        }
                    }
                }
            }
        }

        // Filter out "Details" entries (similar to Python code)
        val filteredData = extractedData.filter { it != "Details" }

        println("\n" + "-".repeat(80))
        println("FINAL EXTRACTED DATA (${filteredData.size} items):")
        println("-".repeat(80))
        filteredData.forEachIndexed { index, item ->
            println("${index + 1}. $item")
        }
        println("-".repeat(80))

        document?.close()
        onComplete(
            true,
            "✅ Extracted ${filteredData.size} items from column ${columnIndex + 1}",
            filteredData
        )

    } catch (e: InvalidPasswordException) {
        document?.close()
        val errorMessage = "❌ Invalid password for PDF"
        println("\nERROR: $errorMessage")
        onComplete(false, errorMessage, emptyList())

    } catch (e: IOException) {
        document?.close()
        val errorMessage = "❌ Error processing PDF: ${e.message}"
        println("\nERROR: $errorMessage")
        onComplete(false, errorMessage, emptyList())

    } catch (e: Exception) {
        document?.close()
        val errorMessage = "❌ Unexpected error: ${e.message}"
        println("\nERROR: $errorMessage")
        onComplete(false, errorMessage, emptyList())
    }
}

private fun promptForPassword(): String? {
    val passwordField = JPasswordField()
    val result = JOptionPane.showConfirmDialog(
        null,
        passwordField,
        "Enter PDF Password",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE
    )

    return if (result == JOptionPane.OK_OPTION) {
        String(passwordField.password)
    } else {
        null
    }
}

@Preview
@Composable
fun PDFTableExtractorPreview() {
    MaterialTheme {
        PDFTableExtractor()
    }
}

