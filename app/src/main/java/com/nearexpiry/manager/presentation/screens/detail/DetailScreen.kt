package com.nearexpiry.manager.presentation.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.screens.detail.viewmodel.DetailViewModel
import com.nearexpiry.manager.utils.LanguageManager
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.item_details)) }) },
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
                        val item = uiState.item!!
                        val isArabic = LanguageManager.isArabic()
                        // Title: name in the app's current language (falls back to
                        // the other language, then item code, then barcode).
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        // Secondary line: name in the *other* language, if available
                        val secondaryName = if (isArabic) {
                            item.productName?.takeIf { it.isNotBlank() }
                        } else {
                            item.productNameArabic?.takeIf { it.isNotBlank() }
                        }
                        if (!secondaryName.isNullOrBlank()) {
                            Text(
                                secondaryName,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        // Item Code
                        if (!item.itemCode.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.item_code_format, item.itemCode),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        // Barcode + Unit
                        Text(
                            text = if (!item.unit.isNullOrBlank())
                                stringResource(R.string.barcode_unit_format, item.barcode, item.unit)
                            else
                                stringResource(R.string.barcode_format, item.barcode),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedTextField(
                        value = uiState.expiryDate,
                        onValueChange = viewModel::updateExpiryDate,
                        label = { Text(stringResource(R.string.expiry_date_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    )
                    OutlinedTextField(
                        value = uiState.quantityText,
                        onValueChange = viewModel::updateQuantity,
                        label = { Text(stringResource(R.string.quantity_label)) },
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
                            Text(stringResource(R.string.save_changes))
                        }
                        Button(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !uiState.isSaving
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { Text(stringResource(R.string.delete_item_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
