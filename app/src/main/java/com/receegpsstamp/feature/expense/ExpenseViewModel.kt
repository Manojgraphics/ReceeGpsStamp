package com.receegpsstamp.feature.expense

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receegpsstamp.data.auth.AuthRepository
import com.receegpsstamp.data.export.ReportExporter
import com.receegpsstamp.data.image.ImageCompressor
import com.receegpsstamp.data.local.LocalStore
import com.receegpsstamp.data.local.ProfileStore
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** The Expense Manager screen's slice of the local DB (the screen filters by project itself). */
data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val approvals: Map<String, String> = emptyMap(),
    val vehicleNumber: String = "",
    val vehicleType: String = "",
    val vehicles: List<Vehicle> = emptyList(),
    val projects: List<Distributor> = emptyList(),
)

/**
 * Focused state holder for the Expense Manager. Carved out of AppViewModel — reads the shared offline
 * [LocalStore] and owns the expense CRUD, bill-photo compression and reimbursement exports. Exposes
 * its own one-off [messages] (toasts) and [shareFile] (share-sheet) streams, like AppViewModel does.
 */
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val localStore: LocalStore,
    private val reportExporter: ReportExporter,
    private val imageCompressor: ImageCompressor,
    private val profileStore: ProfileStore,
    private val authRepo: AuthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val uiState: StateFlow<ExpenseUiState> = localStore.db
        .map {
            ExpenseUiState(
                expenses = it.expenses,
                approvals = it.approvals,
                vehicleNumber = it.vehicleNumber,
                vehicleType = it.vehicleType,
                vehicles = it.vehicles,
                projects = it.distributors,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    // Transient one-off messages surfaced as a toast by the UI.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private fun notify(msg: String) { _messages.tryEmit(msg) }

    // A generated report file the UI turns into a share-sheet intent (it owns the Context + FileProvider).
    private val _shareFile = Channel<Pair<Uri, String>>(Channel.BUFFERED)
    val shareFile: Flow<Pair<Uri, String>> = _shareFile.receiveAsFlow()

    private val safe = CoroutineExceptionHandler { _, e ->
        Log.e("ExpenseViewModel", "action failed", e)
        notify("Something went wrong")
    }

    private val userId: String get() = authRepo.currentUser?.uid ?: "local"
    private val userName: String
        get() = profileStore.profile.value.fullName.ifBlank { authRepo.currentUser?.displayName ?: "" }

    /** Adds an expense/advance, stamping the live project name so it survives project deletion. */
    fun addExpense(e: Expense) {
        val name = if (e.projectId.isBlank()) "" else
            localStore.db.value.distributors.find { it.id == e.projectId }?.name ?: e.projectName
        localStore.addExpense(e.copy(projectName = name, userId = userId))
        // If a company vehicle was chosen on a fuel expense, also log it to the Fleet module
        // so that vehicle's odometer / mileage / service alerts stay up to date.
        if (e.category == "Fuel" && e.vehicleId.isNotBlank() && e.odometer > 0) {
            localStore.addFuelLog(
                FuelLog(
                    vehicleId = e.vehicleId, date = if (e.date > 0) e.date else System.currentTimeMillis(),
                    odometer = e.odometer.toInt(), litres = e.litres, amount = e.amount, userId = userId,
                ),
            )
        }
    }

    fun deleteExpense(id: String) { localStore.deleteExpense(id) }

    /** Compresses a picked/captured bill image to ~200 KB (1600px, Q72); returns the saved local path. */
    suspend fun compressBillPhoto(src: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val tmp = File(context.cacheDir, "bill_src_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(src)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return@withContext null
            val bmp = imageCompressor.prepareImageForPdf(tmp, 1600).getOrNull()
            tmp.delete()
            if (bmp == null) return@withContext null
            val dir = File(context.getExternalFilesDir(null), "Photos/Bills").apply { mkdirs() }
            val out = File(dir, "BILL_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 72, it) }
            bmp.recycle()
            out.absolutePath
        } catch (_: Exception) { null }
    }

    /** Exports the given expenses (current scope filter) as a PDF reimbursement sheet → share sheet. */
    fun exportExpensesPdf(expenses: List<Expense>, scope: String) {
        if (expenses.isEmpty()) { notify("No expenses to export"); return }
        notify("Generating report…")
        viewModelScope.launch(safe) {
            val uri = withContext(Dispatchers.IO) { reportExporter.exportExpensesPdf(scope, userName, expenses) }
            _shareFile.trySend(uri to "application/pdf")
        }
    }

    /** Exports the given expenses as an Excel/CSV reimbursement sheet → share sheet. */
    fun exportExpensesCsv(expenses: List<Expense>, scope: String) {
        if (expenses.isEmpty()) { notify("No expenses to export"); return }
        viewModelScope.launch(safe) {
            val uri = withContext(Dispatchers.IO) { reportExporter.exportExpensesCsv(scope, userName, expenses) }
            _shareFile.trySend(uri to "text/csv")
        }
    }
}
