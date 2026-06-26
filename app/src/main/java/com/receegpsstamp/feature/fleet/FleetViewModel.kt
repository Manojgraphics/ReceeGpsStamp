package com.receegpsstamp.feature.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receegpsstamp.data.auth.AuthRepository
import com.receegpsstamp.data.local.LocalStore
import com.receegpsstamp.data.location.LocationProvider
import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.ServiceLog
import com.receegpsstamp.data.model.Vehicle
import com.receegpsstamp.data.sync.FirestoreSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Fleet screen's slice of the local DB — company vehicles and their fuel/service logs. */
data class FleetUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val fuelLogs: List<FuelLog> = emptyList(),
    val serviceLogs: List<ServiceLog> = emptyList(),
)

/**
 * Focused state holder for the Fleet maintenance screen. Reads the same offline [LocalStore] as the
 * rest of the app but exposes only the three fleet lists, so the screen recomposes on fleet changes
 * alone. Carved out of AppViewModel — see also the still-shared LocalStore fleet mutations.
 */
@HiltViewModel
class FleetViewModel @Inject constructor(
    private val localStore: LocalStore,
    private val locationProvider: LocationProvider,
    private val firestoreSync: FirestoreSync,
    private val authRepo: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<FleetUiState> = localStore.db
        .map { FleetUiState(it.vehicles, it.fuelLogs, it.serviceLogs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetUiState())

    private val userId: String get() = authRepo.currentUser?.uid ?: "local"

    fun updateVehicle(v: Vehicle) { localStore.updateVehicle(v) }
    fun deleteVehicle(id: String) { localStore.deleteVehicle(id); firestoreSync.deleteSharedVehicle(id) }
    fun addFuelLog(f: FuelLog) { localStore.addFuelLog(f.copy(userId = userId)); stampVehicleLocation(f.vehicleId) }
    fun deleteFuelLog(id: String) { localStore.deleteFuelLog(id) }
    fun addServiceLog(s: ServiceLog) { localStore.addServiceLog(s.copy(userId = userId)); stampVehicleLocation(s.vehicleId) }
    fun deleteServiceLog(id: String) { localStore.deleteServiceLog(id) }

    /** Capture current GPS and store it as the vehicle's last known location (shown on the web fleet map). */
    private fun stampVehicleLocation(vehicleId: String) {
        if (vehicleId.isBlank()) return
        viewModelScope.launch {
            val gps = runCatching { locationProvider.getCurrentLocation() }.getOrNull() ?: return@launch
            localStore.db.value.vehicles.find { it.id == vehicleId }?.let { localStore.updateVehicle(it.copy(lat = gps.lat, lng = gps.lng)) }
        }
    }
}
