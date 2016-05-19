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

package ds.violin.v1

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.DisplayMetrics
import ds.violin.v1.app.violin.ActivityViolin
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.util.common.Debug
import ds.violin.v1.datasource.dataloading.SingleUseBackgroundWorkerThread
import ds.violin.v1.datasource.sqlite.SQLiteSessionHandling
import ds.violin.v1.util.ConnectionChecker
import java.util.*

private val TAG = "GLOBAL"

/**
 * this interface must be implemented in Application
 */
interface Global {

    companion object {

        const val TAG_ACTIVITY_REFRESH = "__VIOLIN_REFRESH_"

        /** application context */
        lateinit var context: Context
        /** current running activity */
        var currentActivity: Activity? = null

        /** app running in debug mode? */
        var isDebug: Boolean = false
        /** device has large screen? */
        var isLargeScreen: Boolean = false
        /** dimensions and dip multiplier, etc */
        var screenMetrics: DisplayMetrics? = null

        // TODO: move these somewhere far better
        lateinit var httpSessionsTODO: HashMap<*, *>
        lateinit var sqliteOpenHelpersTODO: HashMap<*, *>

        /**
         * instance of [SharedPreferences] according to your preferences set in the constructor
         */
        lateinit var preferences: SharedPreferences

        /**
         * a saved [SharedPreferences.Editor] to chain edit through calls/objects
         *
         * !note: if you would just quick commit something, please use preferences#edit
         */
        lateinit var editor: SharedPreferences.Editor

        /** --------------------------------------------------------------------------------------
         * [screenMetrics]
         */

        val dipMultiplier: Float
            get() {
                return screenMetrics!!.density
            }

        val dipImageMultiplier: Float
            get() {
                return dipMultiplier * when (isLargeScreen) {
                    true -> 2
                    false -> 1
                }
            }

        val screenWidth: Int
            get() {
                return screenMetrics!!.widthPixels
            }

        val screenHeight: Int
            get() {
                return screenMetrics!!.heightPixels
            }

        /** --------------------------------------------------------------------------------------
         * [Locale]
         */
        var language: String?
            get() {
                return preferences.getString(TAG + "_language", null)
            }
            set(value) {
                val editor = preferences.edit()
                editor.putString(TAG + "_language", value)
                editor.commit()
                forceLocale()
            }

        /**
         * timezone to use - phones timezone is the default set in [ActivityLifecycleCallbacks.onActivityCreated]
         */
        lateinit var timezone: TimeZone

        /**
         *
         */
        internal fun forceLocale() {
            val language = language ?: return

            val res = context.resources;

            val config = res.configuration;
            try {
                config.locale = Locale(language);
                res.updateConfiguration(config, context.resources.displayMetrics);

                Debug.logD(TAG, "forced locale: " + language);
            } catch(e: Throwable) {
                ;
            }
        }

        /** --------------------------------------------------------------------------------------
         * [Activity]
         *
         * request an [ViolinActivity] to reload it's entities when it next becomes active
         * and to tell it's Violins to do their too
         */
        fun invalidateEntitiesIn(activityId: String) {
            val editor = preferences.edit()
            editor.putBoolean(TAG_ACTIVITY_REFRESH + activityId, true)
            editor.apply()
        }

        /**
         * check activity if data reloading is requested
         */
        fun shouldInvalidateEntities(myId: String): Boolean {
            if (preferences.getBoolean(TAG_ACTIVITY_REFRESH + myId, false)) {
                val editor = preferences.edit()
                editor.putBoolean(TAG_ACTIVITY_REFRESH + myId, false)
                editor.apply()
                return true
            }
            return false
        }
    }

    fun initStaticHolding(application: Application, httpSessions: HashMap<*, *>, sqliteOpenHelpers: HashMap<*, *>) {
        context = application.applicationContext

        httpSessionsTODO = httpSessions
        sqliteOpenHelpersTODO = sqliteOpenHelpers

        try {
            val pm = context!!.packageManager
            val debuggableFlagValue = pm?.getApplicationInfo(context!!.packageName, 0)!!.flags and ApplicationInfo.FLAG_DEBUGGABLE
            isDebug = debuggableFlagValue != 0
        } catch(e: Throwable) {
            e.printStackTrace();
        }

        /** clean up any [SingleUseBackgroundWorkerThread] / results */
        SingleUseBackgroundWorkerThread.cleanup()

        application.registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks)
    }
}

/** -------------------------------------------------------------------------------------------
 * Application.ActivityLifecycleCallbacks
 */

private object ActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

        Global.currentActivity = activity

        Global.preferences = activity.getSharedPreferences("ds.violin.shared_preferences", Context.MODE_PRIVATE)
        Global.editor = Global.preferences.edit()

        try {
            if (Global.screenMetrics == null) {
                Global.screenMetrics = DisplayMetrics()
                activity.windowManager.defaultDisplay.getMetrics(Global.screenMetrics)
                Global.isLargeScreen = (Math.min(Global.screenHeight, Global.screenWidth).toFloat() / Global.dipMultiplier) >= 600
                Global.currentActivity!!.windowManager.defaultDisplay.getMetrics(Global.screenMetrics)
            }
        } catch(e: Throwable) {
            Debug.logException(e);
        }

        if (activity is ActivityViolin) {
            activity.onCreated(savedInstanceState)
        }

        Global.timezone = Calendar.getInstance().timeZone

        Global.forceLocale()
    }

    override fun onActivityStarted(activity: Activity) {

        Global.currentActivity = activity

        if (activity is ActivityViolin) {
            ConnectionChecker.registerReceiver(activity, activity)
            activity.onStarted()
        }

        Global.forceLocale()
    }

    override fun onActivityResumed(activity: Activity) {

        Global.currentActivity = activity

        if (activity is ActivityViolin) {
            activity.activityActivated = true
        }
    }

    override fun onActivityPaused(activity: Activity) {

        if (activity == Global.currentActivity) {
            Global.currentActivity = null
        }

        if (activity is ActivityViolin) {
            activity.activityActivated = false
            ConnectionChecker.unregisterReceiver(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
}
