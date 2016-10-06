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

import android.app.Fragment
import android.app.Activity
import android.app.DialogFragment
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import ds.violin.v1.app.ViolinActivity
import ds.violin.v1.app.violin.ActivityViolin
import ds.violin.v1.util.ConnectionChecker
import ds.violin.v1.util.common.Debug
import java.io.Serializable
import java.util.*

interface PlayingViolin : ConnectionChecker.ConnectionChangedListener {

    companion object {
        const val PERMISSION_REQUEST_CODE = 16183
    }

    /** = HashMap(), sub controllers (ie. fragments in an activity, ...) */
    val violins: HashMap<String, PlayingViolin>

    /** =unique id - Id of this controller - can be null for abstract use */
    var Id: String

    /** layout resource ID to inflate / set to content view */
    val layoutResID: Int?
    /** = null, #PrivateSet - root of the view structure for this controller */
    var rootView: View?
    /** = usually null - #Private - null or id of the tobe root view in other Violins when it will come from a view structure */
    var rootViewId: Int?
    /** = null, #PrivateSet - parent for this Violin (activity for fragments, fragment for child fragments) */
    var parentViolin: PlayingViolin?
    /** = false - flag to indicate that [play] was called at least once after the view structure is created */
    var played: Boolean

    /**
     * fragment: lateinit - #PrivateSet - the [ActivityViolin] every [FragmentViolin] is in
     * activity: 'this' - #PrivateSet
     */
    var violinActivity: ActivityViolin

    /** = HashMap, #Private */
    val requestedPermissions: MutableMap<String, RequestedPermission>

    /**
     * is this violin currently active? (visible and user interactions are enabled)
     */
    fun isActive(): Boolean

    /**
     * go back to previous activity creating result
     *
     * @param result
     */
    fun goBack(result: Serializable? = null)

    /**
     * go back to a previous activity creating result
     * !note: make sure the previous activity is indeed an existing activity below in the activity stack
     *        and if the target is a [Class] it will come to front for [Activity.startActivity]
     *
     * @param target Class or [Id]
     * @param result
     */
    fun goBackTo(target: Any, result: Serializable? = null)

    /**
     * override to act when receiving data from another Violin - it's generally good to call super (this)
     */
    fun onTransport(data: Serializable?): Serializable? {
        for (violin in violins.values) {
            violin.onTransport(data)
        }
        return data
    }

    /**
     * to have the same function for this event both in [ActivityViolin]s and [FragmentViolin]s
     */
    fun onViewCreated(view: View?, savedInstanceState: Bundle?)

    /**
     * to have this in other Violins, not just in [Activity]s
     */
    fun findViewById(layoutResID: Int): View? {
        if (rootView?.id == layoutResID) {
            return rootView
        }
        return rootView?.findViewById(layoutResID)
    }

    /**
     * #Protected, this is where stuff should happen :)
     */
    fun play() {
        played = true
    }

    /**
     * #Protected, can call play? override when needed
     */
    fun canPlay(): Boolean {
        return true
    }

    /**
     * handle activity result here, will trigger [onActivityResult] in all child Violins
     * [ActivityViolin]: call if this is needed, calling this will also call [play] if [canPlay]
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, result: Any?) {

        for (violin in violins.values) {
            violin.onActivityResult(requestCode, resultCode, result)
        }

        if (canPlay()) {
            play()
        }
    }

    /**
     * Instance State
     *
     * ---------------------------------------------------------------------------------------- */

    /**
     * restore instance state
     */
    fun restoreInstanceState(savedInstanceState: Bundle)

    /**
     * call from [Activity.onSaveInstanceState] and [Fragment.onSaveInstanceState]
     */
    fun saveInstanceState(outState: Bundle)

    /** ---------------------------------------------------------------------------------------- */

    /**
     * show and return a dialog by its [dialogClass] and [tag] or just return it if it is already showing
     *
     * @param dialogClass - class of the dialog which should be a subclass of [DialogFragment]
     * @param tag
     */
    fun showDialog(dialogClass: Class<*>, tag: String): DialogFragment? {
        val fm = (violinActivity as Activity).fragmentManager
        var fragmentDialog: DialogFragment? = fm.findFragmentByTag(tag) as DialogFragment?

        if (fragmentDialog == null) {
            try {
                fragmentDialog = dialogClass.getConstructor().newInstance() as DialogFragment
            } catch (e: Throwable) {
                Debug.logException(e);
                return null
            }
        }

        val dialog = fragmentDialog!!.dialog
        if (dialog == null || !dialog.isShowing) {
            fragmentDialog.show(fm, tag)
        }

        return fragmentDialog
    }

    /**
     * dismiss dialog by its [tag], of course only if it is showing
     *
     * @param tag
     */
    fun dismissDialog(tag: String) {
        val fm = (violinActivity as Activity).fragmentManager
        val fragmentDialog = fm.findFragmentByTag(tag) as DialogFragment?

        val dialog = fragmentDialog?.dialog
        if (dialog != null && dialog.isShowing) {
            fragmentDialog!!.dismiss()
        }
    }

    /**
     * Permissions
     *
     * ---------------------------------------------------------------------------------------- */

    class RequestedPermission(val permissions: Array<String>, val completion: (Array<out String>, IntArray) -> Unit)

    /**
     * @param permissionPackId
     * @param permissions
     * @param completion
     *
     * @return - true if already has permission
     */
    fun askUserForPermission(permissionPackId: String,
                             permissions: Array<String>,
                             completion: (Array<out String>, IntArray) -> Unit): Boolean {
        var needPermission = false
        var needRationale = false
        for(permission in permissions) {
            if (ContextCompat.checkSelfPermission(violinActivity as Context,
                    permission)
                    != PackageManager.PERMISSION_GRANTED) {

                needPermission = true
                if (ActivityCompat.shouldShowRequestPermissionRationale(violinActivity as Activity,
                        permission)) {
                    needRationale = true
                    break
                }
            }
        }
        if (needRationale) {
            showRequestPermissionRationale(permissionPackId) {
                requestPermissions(permissions)
                violinActivity.requestedPermissions.put(permissionPackId, RequestedPermission(permissions, completion))
            }
            return false
        } else if (needPermission) {
            requestPermissions(permissions)
            violinActivity.requestedPermissions.put(permissionPackId, RequestedPermission(permissions, completion))
            return false
        }
        return true
    }

    /**
     * override to
     * - show request permission rationale (dialog explaining why the permissions with [permissionPackId] is required)
     * and call onAgreed if user accepted the request
     * - or just do nothing if the app can work without the permission
     *
     * default behavior is to just ask for the permission again
     *
     * @param permissionPackId
     * @param onAgreed
     */
    fun showRequestPermissionRationale(permissionPackId: String, onAgreed: () -> Unit) {
        onAgreed()
    }

    fun requestPermissions(permissions: Array<String>) {
        ActivityCompat.requestPermissions(violinActivity as Activity,
                permissions, PERMISSION_REQUEST_CODE)
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {

            // find the requested permission group
            for(requestedPermission in requestedPermissions.values) {
                var found = true
                for(permission in requestedPermission.permissions) {
                    if (!permissions.contains(permission)) {
                        found = false
                        break
                    }
                }
                if (found) {
                    requestedPermission.completion(permissions, grantResults)
                    break
                }
            }
        }
    }

    /** ---------------------------------------------------------------------------------------- */

    fun inflate(resource: Int, root: ViewGroup? = null, attachToRoot: Boolean = false): View {
        return (violinActivity as ViolinActivity).layoutInflater.inflate(resource, root, attachToRoot)
    }

    /** ---------------------------------------------------------------------------------------- */

    /**
     * override to act on back press
     *
     * [Activity]: call from [Activity.onBackPressed]
     *             !note: only call [Activity.onBackPressed] when this returns false
     * [Fragment]: don't call - either return true or return super.actOnBackPressed
     *
     * TODO: Please find a better name for me !
     *
     * @return true when handled
     */
    fun actOnBackPressed(): Boolean {
        for (violin in violins.values) {
            if (violin.actOnBackPressed()) {
                return true
            }
        }

        if (this is ActivityViolin) {
            goBack()
            return true
        }
        return false
    }

    /**
     * stop everything, #Protected
     */
    fun stopEverything() {
        for (violin in violins.values) {
            violin.stopEverything()
        }
    }

    /** ---------------------------------------------------------------------------------------- */

    override fun onConnectionChanged(connected: Boolean) {
        for (violin in violins.values) {
            violin.onConnectionChanged(connected)
        }
    }

    /** ---------------------------------------------------------------------------------------- */

    /**
     * invalidate registered entities in Violins like [LoadingViolin] under the same method signature
     *
     * @param subViolinsToo if true tell all sub Violins to invalidate their entities too
     */
    fun invalidateRegisteredEntities(subViolinsToo: Boolean = false) {
        if (subViolinsToo) {
            for (violin in violins.values) {
                violin.invalidateRegisteredEntities()
            }
        }
    }

    /**
     *
     * TODO: should throw Exception when already has the same Id? #Private
     */
    fun onViolinAttached(violin: PlayingViolin) {

        // violin.Id should not be null at this point!
        violins.put(violin.Id!!, violin)
    }

    /**
     * #Private
     */
    fun onViolinDetached(violin: PlayingViolin) {
        violins.remove(violin.Id)
    }
}