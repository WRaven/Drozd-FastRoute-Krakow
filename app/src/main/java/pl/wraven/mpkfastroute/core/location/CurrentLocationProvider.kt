package pl.wraven.mpkfastroute.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class CurrentLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getCurrentLocation(): Location {
        val hasFineLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            throw LocationPermissionRequiredException()
        }

        val priority = if (hasFineLocation) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val cancellationTokenSource = CancellationTokenSource()
        val currentLocation = try {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
                fusedLocationClient.getCurrentLocation(
                    priority,
                    cancellationTokenSource.token
                ).await(cancellationTokenSource)
            }
        } catch (_: ApiException) {
            null
        } finally {
            cancellationTokenSource.cancel()
        }
        if (currentLocation != null) {
            return currentLocation
        }

        return try {
            fusedLocationClient.lastLocation.await()
        } catch (exception: ApiException) {
            throw LocationUnavailableException()
        } ?: throw LocationUnavailableException()
    }

    private companion object {
        const val CURRENT_LOCATION_TIMEOUT_MILLIS = 10_000L
    }
}

class LocationPermissionRequiredException :
    SecurityException("Location permission is required for nearby-stop actions.")

class LocationUnavailableException :
    IllegalStateException("The device did not provide a current location.")
