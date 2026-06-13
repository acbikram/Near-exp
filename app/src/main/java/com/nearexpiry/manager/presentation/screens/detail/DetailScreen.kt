package com.nearexpiry.manager.presentation.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.presentation.screens.detail.viewmodel.DetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    itemId: Long,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Item Details") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.item != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        if (!uiState.item!!.productName.isNullOrBlank()) {
                            Text(uiState.item!!.productName!!, style = MaterialTheme.typography.titleLarge)
                            if (!uiState.item!!.productNameArabic.isNullOrBlank()) {
                                Text(
                                    uiState.item!!.productNameArabic!!,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                "Barcode: ${uiState.item!!.barcode}" +
                                    (uiState.item!!.unit?.let { "  •  Unit: $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text("Barcode: ${uiState.item!!.barcode}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    OutlinedTextField(
                        value = uiState.expiryDate,
                        onValueChange = viewModel::updateExpiryDate,
                        label = { Text("Expiry Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    )
                    OutlinedTextField(
                        value = uiState.quantityText,
                        onValueChange = viewModel::updateQuantity,
                        label = { Text("Quantity") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.quantityError != null,
                        supportingText = {
                            uiState.quantityError?.let { Text(it) }
                        },
                        enabled = !uiState.isSaving
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveChanges() },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving && uiState.quantityError == null
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Save Changes")
                        }
                        Button(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !uiState.isSaving
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!)
                viewModel.clearError()
            }
        }
    }

    if (uiState.navigateBack) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
    }
}
