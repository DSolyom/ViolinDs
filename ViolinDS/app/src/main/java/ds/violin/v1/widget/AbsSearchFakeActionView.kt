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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import ds.violin.v1.R
import ds.violin.v1.app.ViolinRecyclerViewFragment
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.viewmodel.AbsModelViewBinder
import ds.violin.v1.viewmodel.binding.ViewBinding

abstract class AbsSearchFakeActionView : ViolinRecyclerViewFragment() {

    companion object {
        const val VOICE_INPUT_CODE = 2293
    }

    override var Id: String = "search_fake_action_view"
    override val layoutResID: Int? = R.layout.search_fake_action_view

    lateinit var searchEditText: EditText

    var playBinder: AbsModelViewBinder? = null

    init {
        showsDialog = true

        setStyle(STYLE_NO_FRAME and STYLE_NO_TITLE, R.style.Theme_Transparent)
    }

    override fun onStart() {
        super.onStart()

        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog.window.setLayout(width, height)
    }

    override fun play() {

        /** create your [adapter] and [adapterViewBinder] before calling super */

        super.play()

        if (playBinder == null) {
            searchEditText = findViewById(R.id.et_search) as EditText

            playBinder = createPlayBinder()
            playBinder!!.bind(null)

            openDialog()
        }

    }

    open fun createPlayBinder(): AbsModelViewBinder {
        return PlayBinder(this, rootView!!)
    }

    fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the word");
            startActivityForResult(intent, VOICE_INPUT_CODE)
        } catch(e: Throwable) {
            ds.violin.v1.util.common.toastMessage(violinActivity as Context, R.string.search_no_voice_recognizer_available)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Any?) {
        if (requestCode == VOICE_INPUT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val textMatchList = (result as Intent)
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                if (!textMatchList.isEmpty()) {
                    val query = textMatchList[0]
                    searchEditText.append(query)
                }
                // Result code for various error.
            } else if (resultCode == RecognizerIntent.RESULT_NETWORK_ERROR) {
                // TODO: errors
            } else if (resultCode == RecognizerIntent.RESULT_NO_MATCH) {
                // TODO: errors
            } else if (resultCode == RecognizerIntent.RESULT_SERVER_ERROR) {
                // TODO: errors
            }
        }
        super.onActivityResult(requestCode, resultCode, result)
    }

    open protected fun closeDialog() {
        dismissAllowingStateLoss()
    }

    open protected fun openDialog() {

    }

    private fun setButtonVisibility() {
        val btnClear = findViewById(R.id.btn_search_clear)!!
        val btnVoice = findViewById(R.id.btn_search_voice)!!

        when (searchEditText.text.length > 0) {
            true -> {
                btnClear.visibility = View.VISIBLE
                btnVoice.visibility = View.GONE
            }
            false -> {
                btnClear.visibility = View.GONE
                btnVoice.visibility = View.VISIBLE
            }

        }
    }

    open class PlayBinder(on: PlayingViolin, view: View) : AbsModelViewBinder(on, view) {

        override fun bind(model: Modeling<*>?) {
            (on as AbsSearchFakeActionView).searchEditText.addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        }

                        override fun afterTextChanged(s: Editable?) {
                            (on as AbsSearchFakeActionView).setButtonVisibility()
                        }

                    }
            )

            (on as AbsSearchFakeActionView).setButtonVisibility()

            bind(View.OnClickListener { (on as AbsSearchFakeActionView).closeDialog() },
                    R.id.btn_search_close,
                    ViewBinding.ONCLICK_LISTENER
            )
            bind(View.OnClickListener { (on as AbsSearchFakeActionView).startVoiceInput() },
                    R.id.btn_search_voice,
                    ViewBinding.ONCLICK_LISTENER
            )
            bind(View.OnClickListener { (on as AbsSearchFakeActionView).searchEditText.setText("") },
                    R.id.btn_search_clear,
                    ViewBinding.ONCLICK_LISTENER
            )
        }

    }
}