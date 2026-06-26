package com.receegpsstamp.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide current selection (company + distributor).
 *
 * MUST be a @Singleton — like PhotoCaptureBuffer. Each navigation destination gets its own
 * AppViewModel instance (hiltViewModel scopes to the NavBackStackEntry), so without this every
 * screen (Project, Dashboard, Gallery, Settings) would track its own selection and could show a
 * different distributor. Holding it here makes every screen agree on the same project.
 *
 * The selection is persisted to SharedPreferences so the chosen project stays selected across
 * app restarts — until the user picks a different one.
 */
@Singleton
class SelectionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("rgs_selection", Context.MODE_PRIVATE)

    val companyId = MutableStateFlow(prefs.getString("companyId", null))
    val distributorId = MutableStateFlow(prefs.getString("distributorId", null))
    // A persisted selection counts as already-chosen, so auto-select doesn't override it on launch.
    var hasAutoSelectedCompany = companyId.value != null
    var hasAutoSelectedDistributor = distributorId.value != null

    // Cross-screen "edit this recce" request: set from the Dashboard, consumed by the Start Work form.
    val editRecceId = MutableStateFlow<String?>(null)

    // Photo path to open in the Annotate screen.
    val annotatePhoto = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        // Persist whenever the selection changes (drop(1) = skip the value we just loaded).
        scope.launch { companyId.drop(1).collect { prefs.edit().putString("companyId", it).apply() } }
        scope.launch { distributorId.drop(1).collect { prefs.edit().putString("distributorId", it).apply() } }
    }
}
