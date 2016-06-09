/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.app.violin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.location.*
import ds.violin.v1.Global
import ds.violin.v1.util.common.Debug

interface LocationViolin : LocationListener {

    companion object {
        const val CHECK_SETTINGS_CODE = 983450
        const val ASK_PERMISSION_LOCATION = "DS_Ask_LocationViolin_Ask_Perimission_"

        const val TAG_I_STATE = "__location_violin_"
    }

    /** @see [LocationRequest] */
    val locationUpdateInterval: Long
    /** @see [LocationRequest] */
    val locationFastestUpdateInterval: Long
    /** @see [LocationRequest] */
    val locationPriority: Int
    /**
     * required user permissions for location - choose one or more from:
     * [Manifest.permission.ACCESS_COARSE_LOCATION],
     * [Manifest.permission.ACCESS_FINE_LOCATION]
     */
    val requiredUserPermissions: Array<String>

    /** = null, last known location - or [currentLocation] if that is set */
    var lastKnownLocation: Location?
    /** = null, current location or null if no location has been found */
    var currentLocation: Location?
    /** = ArrayList(), #Private */
    var locationRequests: MutableList<LocationRequest>
    /** should look for location - should call [initLocationChecking] when setting after the first time */
    var locationCheckEnabled: Boolean
    /** = false, #Private */
    var checkedLocationSettings: Boolean
    /** = false, #Private */
    var locationRequestsStarted: Boolean
    /** = true, #Private */
    var locationDisabledNotified: Boolean

    /**
     * call before [GoogleApiViolin.onCreate]
     */
    fun onCreate(savedInstanceState: Bundle?) {
        (this as GoogleApiViolin).requiredApis.add(LocationServices.API)

        checkedLocationSettings = Global.preferences.getBoolean(TAG_I_STATE + "checked_settings", checkedLocationSettings)
    }

    /**
     * save [checkedLocationSettings] - call this in [onLocationSettingsDisabled] if locations are not
     * that important and one failed question was enough
     */
    fun saveCheckedLocationSettings() {
        Global.preferences.edit().
                putBoolean(TAG_I_STATE + "checked_settings", checkedLocationSettings).apply()
    }

    fun onResume() {

        if (locationCheckEnabled && (this as GoogleApiViolin).googleApiClient?.isConnected ?: false) {

            // check location settings knowing that either it is either already working or
            // already checked and no further message to the user will be showing
            initLocationChecking()
        }
    }

    fun onPause() {
        if (locationCheckEnabled && (this as GoogleApiViolin).googleApiClient?.isConnected ?: false) {
            stopLocationRequests()
        }
    }

    fun onGoogleApiConnected(connectionHint: Bundle?) {
        lastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(
                (this as GoogleApiViolin).googleApiClient)

        initLocationChecking()
    }

    fun onGoogleApiConnectionFailed(result: ConnectionResult) {
        onLocationSettingsDisabled()    // TODO: sure?
    }

    fun initLocationChecking() {
        if (locationCheckEnabled) {
            if (locationRequests.isEmpty()) {
                addLocationRequests()
            }
            if (!locationRequests.isEmpty()) {
                startLocationRequests()
            }
        } else {
            stopLocationRequests()
        }
    }

    fun addLocationRequests() {
        locationRequests.add(createDefaultLocationRequest())
    }

    /**
     * check if getting location is enabled on the device - start resolution when required
     */
    fun checkLocationSettings() {
        if (checkedLocationSettings) {
            return
        }
        val locationSettingsRequest = LocationSettingsRequest.Builder()
                .addAllLocationRequests(locationRequests)
                .build()
        val result = LocationServices.SettingsApi.
                checkLocationSettings((this as GoogleApiViolin).googleApiClient, locationSettingsRequest)
        result.setResultCallback { result ->
            val status = result.status
            when (status.statusCode) {
                LocationSettingsStatusCodes.SUCCESS -> {
                    // make sure we ask for settings again if it was turned off
                    checkedLocationSettings = false
                    /** */
                    startLocationRequests()
                }

                LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->
                    if (checkedLocationSettings) {
                        if (!locationDisabledNotified) {
                            onLocationSettingsDisabled()
                        }
                    } else {
                        try {
                            status.startResolutionForResult(
                                    (this@LocationViolin as PlayingViolin).violinActivity as Activity,
                                    CHECK_SETTINGS_CODE)
                            checkedLocationSettings = true
                        } catch (e: IntentSender.SendIntentException) {

                        }
                    }

                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE ->
                    if (!locationDisabledNotified) {
                        onLocationSettingsDisabled()
                    }
            }
        }
    }

    fun createDefaultLocationRequest(): LocationRequest {
        val locationRequest = LocationRequest();
        locationRequest.interval = locationUpdateInterval;
        locationRequest.fastestInterval = locationFastestUpdateInterval;
        locationRequest.priority = locationPriority;
        return locationRequest
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, result: Any?) {
        if (requestCode == CHECK_SETTINGS_CODE) {

            // need to check manually if location settings are now enabled because resultCode could always be 0 on some devices
            var lm = ((this as PlayingViolin).violinActivity as Context).getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var good = false
            for (permission in requiredUserPermissions) {
                good = good or when (permission) {
                    Manifest.permission.ACCESS_COARSE_LOCATION -> lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    Manifest.permission.ACCESS_FINE_LOCATION -> lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    else -> false
                }
                if (good) {
                    startLocationRequests()
                    break
                }
            }
            if (!good) {
                onLocationSettingsDisabled()
            }
        }
    }

    fun startLocationRequests() {
        try {
            if (!locationRequestsStarted) {
                for (request in locationRequests) {
                    LocationServices.FusedLocationApi.requestLocationUpdates(
                            (this as GoogleApiViolin).googleApiClient, request, this)
                }
                locationRequestsStarted = true
                checkLocationSettings()
            }
        } catch(e: SecurityException) {
            if (!checkedLocationSettings) {
                (this as PlayingViolin).
                        askUserForPermission(ASK_PERMISSION_LOCATION,
                                requiredUserPermissions) { permissions, grantResults ->
                            val success = grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                            if (success) {
                                startLocationRequests()
                            } else {
                                onLocationSettingsDisabled()
                            }
                        }
            }
        }
    }

    fun stopLocationRequests() {
        locationRequestsStarted = false
        LocationServices.FusedLocationApi.removeLocationUpdates(
                (this as GoogleApiViolin).googleApiClient, this)
    }

    /**
     * override to act when location settings are still disabled or permissions are still not given after asking for them
     *
     * if location's are not required you can set [checkedLocationSettings] to true and call [saveCheckedLocationSettings]
     * not to check for permission again
     *
     * !note: always call this (super) to set [locationDisabledNotified]
     */
    fun onLocationSettingsDisabled() {
        locationDisabledNotified = true
        Debug.logD("LocationViolin", "Location settings disabled")
    }

    override fun onLocationChanged(location: Location?) {
        lastKnownLocation = location
        currentLocation = location
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        lastKnownLocation = savedInstanceState.getParcelable(TAG_I_STATE + "lastknownlocation_")
        currentLocation = savedInstanceState.getParcelable(TAG_I_STATE + "currentlocation_")
        locationCheckEnabled = savedInstanceState.getSerializable(TAG_I_STATE + "locationcheckenabled_") as Boolean
        checkedLocationSettings = savedInstanceState.getSerializable(TAG_I_STATE + "checkedlocationsettings_") as Boolean
        locationDisabledNotified = savedInstanceState.getSerializable(TAG_I_STATE + "locationdisablednotified_") as Boolean
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putParcelable(TAG_I_STATE + "lastknownlocation_", lastKnownLocation)
        outState.putParcelable(TAG_I_STATE + "currentlocation_", currentLocation)
        outState.putSerializable(TAG_I_STATE + "locationcheckenabled_", locationCheckEnabled)
        outState.putSerializable(TAG_I_STATE + "checkedlocationsettings_", checkedLocationSettings)
        outState.putSerializable(TAG_I_STATE + "locationdisablednotified_", locationDisabledNotified)
    }
}