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
package ds.violin.v1.widget

import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.R

interface FullScreenDialog {

    fun onCreate(savedInstanceState: Bundle?) {
        (this as DialogFragment).showsDialog = true

        setStyle(DialogFragment.STYLE_NO_FRAME and DialogFragment.STYLE_NO_TITLE, R.style.Theme_Transparent)
    }

    fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog((this as PlayingViolin).violinActivity as Context, (this as DialogFragment).theme.toInt()) {

            override fun onBackPressed() {
                closeDialog()
            }
        }
    }

    fun onStart() {
        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        (this as DialogFragment).dialog.window.setLayout(width, height)
    }

    fun closeDialog()
}