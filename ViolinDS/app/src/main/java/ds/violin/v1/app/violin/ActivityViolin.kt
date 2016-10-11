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

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Pair
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import ds.violin.v1.Global
import ds.violin.v1.R
import java.io.Serializable

internal object NoTransportData : Serializable {}

interface ActivityViolin : PlayingViolin {

    companion object {
        const val ONACTIVITYRESULT_ACTION = "DS_OnActivityResult_Action"
        const val TRANSPORT_ACTION = "DS_Transport_Action"
        const val GOBACK_ACTION = "DS_Goback_Action"
        const val GOBACKTO_ACTION = "DS_Gobackto_Action"
        const val FINISH_ALL_ACTION = "DS_Finish_All_Action"

        const val TRANSPORT_ACTION_CODE = 2013
        const val ONACTIVITYRESULT_RESULT_CODE = "DS_OnActivityResult_Result_Code"
        const val ACTION_MODE_BAR = 0
        const val ACTION_MODE_SEARCH = 1

        const val TRANSITION_app_bar = "DS_TR_app_bar"
        const val TRANSITION_app_bar_shadow = "DS_TR_app_bar_shadow"
        val TRANSITION_Z_app_bar = Float.MAX_VALUE
    }

    /** = false, #Private - used for isActive() - set through lifecycle callbacks in Global */
    var activityActivated: Boolean

    /** = NoTransportData, #Private */
    var transportData: Serializable?
    /** = null, #Private */
    var activityResult: Any?
    /** = TRANSPORT_ACTION_CODE, #Private */
    var activityRequestCode: Int
    /** = RESULT_OK, #Private */
    var activityResultCode: Int
    /** = false, #Private */
    var afterTransport: Boolean

    /** = false, #Private */
    var hasSceneTransition: Boolean
    /** = false, #Private */
    var enterTransitionPostponed: Boolean
    /** = false, #Private */
    var startedEnterTransition: Boolean
    /** = null, #Private */
    var appBar: Toolbar?
    /** = null, #Private */
    var appBarShadow: View?
    /** = false - set true if shared element animation should overlap the app bar */
    var excludeAppBarFromTransportAnimation: Boolean
    /** = false - set true if shared element animation should overlap the phones navigation bar */
    var excludeNavigationBarFromTransportAnimation: Boolean
    /** = false - set true if shared element animation should overlap the status bar */
    var excludeStatusBarFromTransportAnimation: Boolean

    /**
     * restoring instance state, creating/setting root view (content view) for activity
     *
     * #Private (called through lifecycle callbacks in Global)
     */
    fun onCreated(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }

        val asActivity = this as Activity

        // separated setContentView
        setContentView()

        rootView = findViewById(android.R.id.content)
        onViewCreated(rootView!!, savedInstanceState)

        if (savedInstanceState != null) {

            // no transition after orientation change as it won't work anyway
            asActivity.intent.removeExtra("has-scene-transition");
            hasSceneTransition = false;
        }
        onEnter(asActivity.intent)
    }

    /**
     * just to have content view creating separated
     * call [Activity.setContentView] inside
     */
    fun setContentView() {
        when {
            layoutResID != null -> (this as Activity).setContentView(layoutResID!!)
            rootView != null -> (this as Activity).setContentView(rootView)
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        var appBarTobe = findViewById(R.id.app_bar)
        if (appBarTobe != null && appBarTobe is Toolbar) {
            appBar = appBarTobe

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appBar!!.transitionName = TRANSITION_app_bar;
                appBar!!.translationZ = TRANSITION_Z_app_bar;


                appBarShadow = findViewById(R.id.app_bar_shadow);
                if (appBarShadow != null) {
                    appBarShadow!!.transitionName = TRANSITION_app_bar_shadow;
                    appBarShadow!!.translationZ = TRANSITION_Z_app_bar;
                }
            }
            (this as AppCompatActivity).setSupportActionBar(appBar);
        }
    }

    /**
     * #Protected (called through lifecycle callbacks in Global)
     * this is the last place (before calling super.onStarted) to add child violins before
     * [transportData] is cleared and reload request is dealt with (@see [Global.invalidateEntitiesIn])
     */
    fun onStarted() {
        onEnter((this as Activity).intent)

        startSharedElementEnterTransition()
    }

    /**
     * #Protected - [Activity.onResume]
     */
    fun onResume() {
        afterTransport = false

        if (Global.shouldInvalidateEntities(this.Id)) {
            invalidateRegisteredEntities(true)
        }
    }

    /**
     * #Protected - called from [onCreated]
     */
    fun onEnter(intent: Intent?) {
        if (intent == null) {
            return
        }

        val action = intent.action

        if (FINISH_ALL_ACTION.equals(action)) {
            finishAll()
            return
        }

        if (!hasSceneTransition) {
            hasSceneTransition = intent.getBooleanExtra("has-scene-transition", false)
            if (hasSceneTransition) {
                if (!enterTransitionPostponed) {
                    (this as Activity).postponeEnterTransition()
                    enterTransitionPostponed = true
                }
            } else {
                startedEnterTransition = true
            }
        }

        if (transportData is NoTransportData) {

            transportData = intent.getSerializableExtra("transport-data") ?: null

            when (action) {
                TRANSPORT_ACTION -> {

                    /** transport */
                    transportData = onTransport(transportData)
                }
                GOBACK_ACTION -> {

                    /** after [goBack] 1 step or steps are handled via manifest definition of this activity */
                    onActivityResult(activityRequestCode, intent.getIntExtra(ONACTIVITYRESULT_RESULT_CODE, Activity.RESULT_OK), transportData)
                    removeSavedTransportData()
                }
                GOBACKTO_ACTION -> {

                    /** after [goBackTo] with target id */
                    val target = intent.getStringExtra("back-target")
                    if (target != null && target != Id) {

                        /** need to [goBackTo] further */
                        goBackTo(target, transportData)
                        removeTransportData(intent)
                        return

                    } else {

                        /** we were the target */
                        onActivityResult(activityRequestCode, intent.getIntExtra(ONACTIVITYRESULT_RESULT_CODE, Activity.RESULT_OK), transportData)
                    }
                }
                ONACTIVITYRESULT_ACTION -> {
                    onActivityResult(activityRequestCode, intent.getIntExtra(ONACTIVITYRESULT_RESULT_CODE, Activity.RESULT_OK), transportData ?: intent as Any?)
                }
            }

            removeTransportData(intent)
        }

        // for all intention and purposes
        transportDone = true

        if (canPlay()) {
            play()
        }
    }

    override fun canPlay(): Boolean {
        return rootView != null && super.canPlay()
    }

    fun onNewIntent(intent: Intent) {
        removeSavedTransportData()  // TODO: can we have this here? or onNewIntent can be called before onViolinAttached finishes
        onEnter(intent)
    }

    /**
     * #Private except for: call as super from [Activity.onActivityResult]
     *          this is to merge [goBackTo] with normal [Activity.onActivityResult]
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data != null) {
            if (data.action == null || data.action.length == 0) {
                data.action = ONACTIVITYRESULT_ACTION
            }
            data.putExtra(ONACTIVITYRESULT_RESULT_CODE, resultCode)

            removeSavedTransportData()
            activityRequestCode = requestCode
            onEnter(data)
        } else {
            onActivityResult(requestCode, resultCode, null as Any?)
        }
    }

    override fun isActive(): Boolean {
        return activityActivated
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Any?) {
        if (violins.isEmpty()) {
            activityResult = result
            activityResultCode = resultCode
        }
        super.onActivityResult(requestCode, resultCode, result)
    }

    override fun onViolinAttached(violin: PlayingViolin) {
        if (!violins.containsKey(violin.Id)) {
            if (transportData != NoTransportData) {
                violin.onTransport(transportData)
            } else {

                // for all intention and purposes it is called
                violin.transportDone = transportDone
            }
            if (activityResult != null) {
                violin.onActivityResult(activityRequestCode, activityResultCode, activityResult)
            }
        }
        activityResult = null

        super.onViolinAttached(violin)
    }

    /** ---------------------------------------------------------------------------------------- */

    /**
     * #Private
     */
    fun removeTransportData(intent: Intent?) {
        if (intent == null) {
            return;
        }
        intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION);
        intent.action = "";
        intent.removeExtra("transport-data");
        intent.removeExtra("back-target");
        intent.removeExtra(ONACTIVITYRESULT_RESULT_CODE)
    }

    /**
     * #Private
     */
    fun removeSavedTransportData() {
        transportData = NoTransportData
    }

    /**
     * moving to target sending data and doing transition animation with shared elements
     *
     * @param target Class (Any -> subclasses may use Strings)
     * @param sharedViews
     * @param data
     */
    fun transport(target: Any, data: Serializable? = null, sharedViews: Array<Pair<View, String>>? = null) {

        if (afterTransport) {
            return
        }

        if (!beforeTransport(target, data)) {
            return
        }

        afterTransport = true

        val intent = Intent(this as Activity, target as Class<*>)
        intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION)

        intent.putExtra("transport-data", data)

        intent.action = TRANSPORT_ACTION

        val optionsBundle = createTransportOptionsBundle(sharedViews)

        if (optionsBundle != null) {
            intent.putExtra("has-scene-transition", true)

            // note: optionsBundle is null below LOLLIPOP
            this.startActivityForResult(intent, TRANSPORT_ACTION_CODE, optionsBundle)
        } else {
            this.startActivityForResult(intent, TRANSPORT_ACTION_CODE)
        }
    }

    /**
     * move to target sending data while finishing this activity
     *
     * @param target Class (Any -> subclasses may use Strings)
     * @param data
     */
    fun forward(target: Any, data: Serializable? = null) {

        if (afterTransport) {
            return
        }

        if (!beforeTransport(target, data)) {
            return
        }

        afterTransport = true

        // stop everything
        stopEverything()

        // actual forwarding
        val intent = Intent(this as Activity, target as Class<*>)
        intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)

        intent.putExtra("transport-data", data)

        intent.action = TRANSPORT_ACTION

        this.finish()

        this.startActivity(intent)
    }

    /**
     * move to target sending data while clearing this task and creating a new
     *
     * @param target Class (Any -> subclasses may use Strings)
     * @param data
     */
    fun forwardAndClear(target: Any, data: Serializable? = null) {

        if (afterTransport) {
            return
        }

        if (!beforeTransport(target, data)) {
            return
        }

        afterTransport = true

        // stop everything
        stopEverything()

        // actual forwarding
        val intent = Intent(this as Activity, target as Class<*>)
        intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION or
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        intent.putExtra("transport-data", data)

        intent.action = TRANSPORT_ACTION

        this.finish()

        this.startActivity(intent)
    }


    /**
     * override to act before leaving the activity via Transport or overrule it by returning false
     *
     * @param target Class (Any -> subclasses may use Strings)
     * @param data
     * @return
     */
    fun beforeTransport(target: Any, data: Serializable? = null): Boolean {

        return true
    }

    override fun goBack(result: Serializable?) {

        // stop everything
        stopEverything()

        val intent = Intent()
        intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION)
        intent.putExtra("transport-data", result)
        intent.action = GOBACK_ACTION
        (this as Activity).setResult(Activity.RESULT_OK, intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.finishAfterTransition()
        } else {
            this.finish()
        }
    }

    override fun goBackTo(target: Any, result: Serializable?) {

        // stop everything
        stopEverything()

        if (target is Class<*>) {
            val intent = Intent(this as Activity, target)
            intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("transport-data", result)
            intent.action = GOBACK_ACTION
            this.startActivity(intent)
            this.finish()
        } else {
            val intent = Intent()
            intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION)
            intent.putExtra("transport-data", result)
            intent.putExtra("back-target", target as String)
            intent.action = GOBACKTO_ACTION
            (this as Activity).setResult(Activity.RESULT_OK, intent)
            this.finish()
        }
    }

    /**
     *
     */
    fun finishAll() {

        // stop everything
        stopEverything()

        val intent = Intent()
        intent.addFlags(Intent.FILL_IN_DATA or Intent.FILL_IN_ACTION)
        intent.action = FINISH_ALL_ACTION
        (this as Activity).setResult(Activity.RESULT_OK, intent)
        this.finish()
    }

    /** ---------------------------------------------------------------------------------------- */

    /**
     * @Protected, call when layouts from shared views are done and are in the view structure
     */
    fun startSharedElementEnterTransition() {
        if (!startedEnterTransition) {
            startedEnterTransition = true
            val decor = (this as Activity).window.decorView
            decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {

                override fun onPreDraw(): Boolean {
                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                    startEnterTransition()
                    return true;
                }
            });
        }
    }

    /**
     *
     */
    fun startEnterTransition() {
        (this as Activity).startPostponedEnterTransition()
    }

    /**
     * exclude views from transition<br/>
     * call before startPostponedEnterTransition is called
     *
     * @param viewID
     * @param exclude
     */
    fun excludeEnterTarget(viewID: Int, exclude: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (this as Activity).window.enterTransition.excludeTarget(viewID, exclude);
        }
    }

    fun createTransportOptionsBundle(sharedViews: Array<Pair<View, String>>?): Bundle? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (sharedViews != null) {

                val sharedViewList =
                        extendsSharedViewsForTransport(sharedViews.toMutableList())
                val sharedViews = sharedViewList.toTypedArray()

                return ActivityOptions.makeSceneTransitionAnimation(this as Activity, *sharedViews).toBundle()
            }
        }
        return null
    }

    /**
     * add bar backgrounds (topbar, navigationbar, appbar, ... )
     *
     * @param sharedViewList
     * @return
     */
    fun extendsSharedViewsForTransport(sharedViewList: MutableList<Pair<View, String>>):
            MutableList<Pair<View, String>> {
        val decor = (this as Activity).window.decorView

        if (appBar != null && !excludeAppBarFromTransportAnimation) {
            sharedViewList.add(Pair(appBar!!, TRANSITION_app_bar))
            if (appBarShadow != null) {
                sharedViewList.add(Pair(appBarShadow!!, TRANSITION_app_bar_shadow))
            }
        }

        val navbar = decor.findViewById(android.R.id.navigationBarBackground)
        if (navbar != null && !excludeNavigationBarFromTransportAnimation) {
            sharedViewList.add(Pair(navbar, Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME))
        }

        val statusbar = decor.findViewById(android.R.id.statusBarBackground)
        if (statusbar != null && !excludeStatusBarFromTransportAnimation) {
            sharedViewList.add(Pair(statusbar, Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME))
        }

        return sharedViewList
    }
}
