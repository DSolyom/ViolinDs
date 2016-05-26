/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.util

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo

object ConnectionChecker : BroadcastReceiver() {

    private var connectionChangedListener: ConnectionChangedListener? = null
    private var receiverIntentFilter: IntentFilter? = null
    private var registered: Boolean = false
    private var connected: Boolean = true

    /**
     * register connectivity broadcast receiver
     */
    fun registerReceiver(context: Context, listener: ConnectionChangedListener) {
        val pm = context.packageManager
        if (pm.checkPermission(
                Manifest.permission.ACCESS_NETWORK_STATE,
                context.packageName) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (registered) {
            if (listener === connectionChangedListener) {

                // already registered
                return
            }
            unregisterReceiver(context)
        }
        connectionChangedListener = listener
        receiverIntentFilter = IntentFilter()
        receiverIntentFilter!!.addAction(ConnectivityManager.CONNECTIVITY_ACTION)

        context.registerReceiver(this, receiverIntentFilter)
        registered = true
    }

    /**
     * unregister connectivity broadcast receiver
     */
    fun unregisterReceiver(context: Context) {
        if (registered) {
            try {
                context.unregisterReceiver(this)
            } catch (e: IllegalArgumentException) {
            }

            registered = false
        }
        connectionChangedListener = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) {
            return
        }

        val oldState = connected
        connected = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
        if (oldState != connected && connectionChangedListener != null) {
            connectionChangedListener!!.onConnectionChanged(connected)
        }
    }

    interface ConnectionChangedListener {
        fun onConnectionChanged(connected: Boolean)
    }

    val TIMEOUT = 10

    private var mTries: Int = 0
    private var mOnlyWifi = false


    /**
     * check for connection
     *
     * @param context
     * @param waitForFullConnection
     * @return
     */
    fun check(context: Context, waitForFullConnection: Boolean = true): Boolean {
        val cm = context.getSystemService(Activity.CONNECTIVITY_SERVICE) as ConnectivityManager
        var ni: NetworkInfo = cm.activeNetworkInfo ?: return false
        if (mOnlyWifi && ni.type != ConnectivityManager.TYPE_WIFI) {
            return false
        }

        if (!waitForFullConnection) {
            return ni.isConnected
        }
        if (ni.isConnectedOrConnecting) {
            mTries = 0
            while (!ni.isConnected) {
                if (mTries++ == TIMEOUT) {
                    return false
                }
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                }

                ni = cm.activeNetworkInfo
                if (ni == null || !ni.isConnectedOrConnecting) {
                    return false
                }
            }
            return true
        }
        return false
    }

    fun onlyWifi(onlyWifi: Boolean) {
        mOnlyWifi = onlyWifi
    }
}
