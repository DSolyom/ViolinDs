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

import android.content.Context
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.GoogleApiClient

interface GoogleApiViolin {

    val requiredApis: MutableList<Api<*>>

    var googleApiClient: GoogleApiClient?

    fun onCreate(savedInstanceState: Bundle?) {
        if (!requiredApis.isEmpty() && googleApiClient == null) {
            val builder = GoogleApiClient.Builder((this as PlayingViolin).violinActivity as Context)
                    .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {

                        override fun onConnectionSuspended(p0: Int) {
                        }

                        override fun onConnected(connectionHint: Bundle?) {
                            onGoogleApiConnected(connectionHint)
                        }

                    })
                    .addOnConnectionFailedListener { result -> onGoogleApiConnectionFailed(result) }

            for(api in requiredApis) {
                builder.addApi(api as Api<Api.ApiOptions.NotRequiredOptions>)
            }

            googleApiClient = builder.build()
        }
    }

    fun onStart() {
        googleApiClient?.connect()
    }

    fun onStop() {
        googleApiClient?.disconnect()
    }

    fun onGoogleApiConnected(connectionHint: Bundle?)

    fun onGoogleApiConnectionFailed(result: ConnectionResult)
}