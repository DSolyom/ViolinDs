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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ds.violin.v1.app.violin.PlayingViolin
import java.io.Serializable

interface FragmentViolin : PlayingViolin {

    override fun isActive(): Boolean {
        return (this as Fragment).userVisibleHint
    }

    override fun onTransport(data: Serializable?): Serializable? {

        for (violin in violins.values) {
            violin.onTransport(data)
        }

        return data
    }

    /**
     * create root view for fragments and other violins
     *
     * [Fragment]: call from [Fragment.onCreateView]
     */
    fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }

        return when {
            rootView != null -> null
            layoutResID != null -> {
                rootView = inflater!!.inflate(layoutResID!!, container, false)
                rootView
            }
            rootViewId != null -> {
                rootView = container!!.findViewById(rootViewId!!)
                null
            }
            else -> null
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        if (canPlay()) {
            play()
        }
    }

    fun onResume() {
        if (canPlay()) {
            play()
        }
    }

    override fun findViewById(layoutResID: Int): View? {
        return rootView?.findViewById(layoutResID)
    }

    /**
     * [Fragment]: call from [Fragment.onAttach]
     * (for child fragments call [setParent] before adding them, or the [Activity] will be their parents too)
     */
    fun onAttach(parent: PlayingViolin) {
        if (parentViolin == null) {
            setParent(parent)
        }

        if (this is Fragment && Id == null) {
            Id = tag
        }

        if (parentViolin != null) {
            parentViolin!!.onViolinAttached(this)
        }

        if (canPlay()) {
            play()
        }
    }

    /**
     * [Fragment]: call from [Fragment.onDetach]
     */
    fun onDetach() {
        if (parentViolin != null) {
            parentViolin!!.onViolinDetached(this)
        }
    }

    /**
     * [Fragment]: call from [Fragment.onDestroyView]
     */
    fun onDestroyView() {
        rootView = null
    }

    /**
     * #Protected - call for child fragments before attaching them
     *              as onAttach would set the activity to be the parent
     */
    fun setParent(parent: PlayingViolin) {
        parentViolin = parent
        violinActivity = parent.violinActivity
    }

    override fun canPlay(): Boolean {
        return rootView != null && parentViolin != null
    }
}