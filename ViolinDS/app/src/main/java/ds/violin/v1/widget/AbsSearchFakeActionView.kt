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

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import ds.violin.v1.Global
import ds.violin.v1.app.ViolinRecyclerViewFragment
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.R
import ds.violin.v1.app.ViolinActivity
import ds.violin.v1.util.common.Debug
import ds.violin.v1.util.common.showKeyboard
import ds.violin.v1.viewmodel.AbsModelViewBinder
import ds.violin.v1.viewmodel.binding.ViewBinding

/**
 * [AbsSearchFakeActionView] is a mimic of the ToolBar's search action view behavior
 * it is a FragmentDialog which appears above the ToolBar, animating out ( Api >= LOLLIPOP )
 */
abstract class AbsSearchFakeActionView : ViolinRecyclerViewFragment(), FullScreenDialog {

    companion object {
        const val VOICE_INPUT_CODE = 2293
    }

    override var Id: String = "search_fake_action_view"
    override val layoutResID: Int? = R.layout.search_fake_action_view

    lateinit var searchEditText: SearchEditText
    var initialText: String? = null
    lateinit var fakeAVCardView: View
    lateinit var separatorView: View

    var playBinder: AbsModelViewBinder? = null
    var closing = false
    var donePressed: Boolean = false

    val searchRunnable = Runnable { startSearch() }
    val searchDelay = 500L

    override val parentCanHoldHeader: Boolean = false
    override val parentCanHoldFooter: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super<FullScreenDialog>.onCreate(savedInstanceState)
        super<ViolinRecyclerViewFragment>.onCreate(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super<FullScreenDialog>.onCreateDialog(savedInstanceState)
    }

    override fun onStart() {
        super<ViolinRecyclerViewFragment>.onStart()
        super<FullScreenDialog>.onStart()
    }

    override fun play() {

        /** create your [adapter] and [adapterViewBinder] before calling super */

        donePressed = false

        if (!played) {
            searchEditText = findViewById(R.id.et_search) as SearchEditText
            searchEditText.onBackListener = object : SearchEditText.OnBackListener {
                override fun onBack() {
                    closeDialog()
                }

            }
            fakeAVCardView = findViewById(R.id.cv_fake_action_view)!!
            separatorView = findViewById(R.id.separator)!!

            playBinder = createPlayBinder()
            playBinder!!.bind(null)

            openDialog()
        }
        if (initialText != null) {
            searchEditText.setText(initialText)
            initialText = null
        }

        super.play()
    }

    open fun createPlayBinder(): AbsModelViewBinder {
        return PlayBinder(this, rootView!!)
    }

    /**
     * start activity with intent for voice input
     */
    fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.speak_now)
            startActivityForResult(intent, VOICE_INPUT_CODE)
        } catch(e: Throwable) {
            ds.violin.v1.util.common.toastMessage(violinActivity as Context, R.string.search_error_no_voice_recognizer_available)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Any?) {
        if (requestCode == VOICE_INPUT_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                /** got result from voice input */
                val textMatchList = (result as Intent)
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                if (!textMatchList.isEmpty()) {
                    val query = textMatchList[0]
                    searchEditText.append(query)
                }
            } else if (resultCode == RecognizerIntent.RESULT_NO_MATCH) {

                /** voice input failed to recognice anything */
                ds.violin.v1.util.common.toastMessage(violinActivity as Context, R.string.search_error_no_match)
            } else  {

                /** other errors */
                ds.violin.v1.util.common.toastMessage(violinActivity as Context, R.string.search_error)
            }
        }
        super.onActivityResult(requestCode, resultCode, result)
    }

    /**
     * close dialog - animating when Api >= LOLLIPOP
     */
    override fun closeDialog() {
        if (closing) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val anim = ViewAnimationUtils.createCircularReveal(
                    fakeAVCardView,
                    (fakeAVCardView.measuredWidth - Global.dipMultiplier * 16).toInt(),
                    fakeAVCardView.measuredHeight / 2,
                    fakeAVCardView.measuredWidth - Global.dipMultiplier * 16,
                    0f
            )
            anim.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {
                }

                override fun onAnimationEnd(animation: Animator?) {
                    dismissAllowingStateLoss()
                    closing = false
                }

                override fun onAnimationCancel(animation: Animator?) {
                    dismissAllowingStateLoss()
                    closing = false
                }

                override fun onAnimationStart(animation: Animator?) {
                    closing = true
                }

            })
            anim.start()

            val dialogBackgroundColors = arrayOf(
                    ColorDrawable(ContextCompat.getColor(violinActivity as Context, R.color.dialog_transparent)),
                    ColorDrawable(ContextCompat.getColor(violinActivity as Context, android.R.color.transparent))
            )
            val dialogBackgroundTransition = TransitionDrawable(dialogBackgroundColors)
            rootView!!.background = dialogBackgroundTransition
            dialogBackgroundTransition.startTransition(anim.duration.toInt())

            val appBar = (violinActivity as ViolinActivity).appBar
            if (appBar != null) {
                val appBarAlphaAnimation = ObjectAnimator.ofFloat(appBar, View.ALPHA, 0f, 1f)
                appBarAlphaAnimation.duration = anim.duration
                appBarAlphaAnimation.start()
            }
            val appBarShadow = (violinActivity as ViolinActivity).appBarShadow
            if (appBarShadow != null) {
                val appBarShadowAlphaAnimation = ObjectAnimator.ofFloat(appBarShadow, View.ALPHA, 0f, 1f)
                appBarShadowAlphaAnimation.duration = anim.duration
                appBarShadowAlphaAnimation.start()
            }
        } else {
            dismissAllowingStateLoss()
        }
    }

    /**
     * open dialog - animating when Api >= LOLLIPOP
     */
    open protected fun openDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fakeAVCardView.visibility = View.GONE
            recyclerView.visibility = View.GONE
            separatorView.visibility = View.GONE

            fakeAVCardView.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {

                        override fun onGlobalLayout() {
                            fakeAVCardView.visibility = View.VISIBLE
                            val anim = ViewAnimationUtils.createCircularReveal(
                                    fakeAVCardView,
                                    (fakeAVCardView.measuredWidth - Global.dipMultiplier * 16).toInt(),
                                    fakeAVCardView.measuredHeight / 2,
                                    0f,
                                    fakeAVCardView.measuredWidth - Global.dipMultiplier * 16
                            )
                            val onGlobalLayoutListener = this
                            anim.addListener(object : Animator.AnimatorListener {
                                override fun onAnimationRepeat(animation: Animator?) {
                                }

                                override fun onAnimationEnd(animation: Animator?) {
                                    fakeAVCardView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
                                    showKeyboard(violinActivity as Activity, searchEditText)
                                }

                                override fun onAnimationCancel(animation: Animator?) {
                                    fakeAVCardView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
                                }

                                override fun onAnimationStart(animation: Animator?) {
                                }

                            })
                            anim.start()

                            val dialogBackgroundColors = arrayOf(
                                    ColorDrawable(ContextCompat.getColor(violinActivity as Context, android.R.color.transparent)),
                                    ColorDrawable(ContextCompat.getColor(violinActivity as Context, R.color.dialog_transparent))
                            )
                            val dialogBackgroundTransition = TransitionDrawable(dialogBackgroundColors)
                            rootView!!.background = dialogBackgroundTransition
                            dialogBackgroundTransition.startTransition(anim.duration.toInt())

                            val appBar = (violinActivity as ViolinActivity).appBar
                            if (appBar != null) {
                                val appBarAlphaAnimation = ObjectAnimator.ofFloat(appBar, View.ALPHA, 1f, 0f)
                                appBarAlphaAnimation.duration = anim.duration
                                appBarAlphaAnimation.start()
                            }
                            val appBarShadow = (violinActivity as ViolinActivity).appBarShadow
                            if (appBarShadow != null) {
                                val appBarShadowAlphaAnimation = ObjectAnimator.ofFloat(appBarShadow, View.ALPHA, 1f, 0f)
                                appBarShadowAlphaAnimation.duration = anim.duration
                                appBarShadowAlphaAnimation.start()
                            }
                        }
                    }
            )
        } else {
            searchEditText.post({
                showKeyboard(violinActivity as Activity, searchEditText)
            })
        }
    }

    /**
     * voice input and clear button visibility
     */
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

    /**
     * start the search
     */
    open protected fun startSearch() {
        try {
            onSearchTextChanged(searchEditText.text.toString(), donePressed)
        } catch(e: Throwable) {
            Debug.logException(e)
        }
    }

    /**
     * receive search text change
     */
    abstract fun onSearchTextChanged(searchText: String, donePressed: Boolean = false)

    inner open class PlayBinder(on: PlayingViolin, view: View) : AbsModelViewBinder(on, view) {

        override fun bind(model: Modeling<*, *>?) {
            (on as AbsSearchFakeActionView).searchEditText.addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        }

                        override fun afterTextChanged(s: Editable?) {
                            try {
                                val on = on as AbsSearchFakeActionView
                                on.setButtonVisibility()
                                on.searchEditText.handler.removeCallbacks(
                                        on.searchRunnable
                                )
                                on.searchEditText.handler.postDelayed(on.searchRunnable, on.searchDelay)
                            } catch(e: Throwable) {

                            }
                        }

                    }
            )
            (on as AbsSearchFakeActionView).searchEditText.setOnEditorActionListener(
                    TextView.OnEditorActionListener { v, actionId, event ->

                        if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                                actionId == EditorInfo.IME_ACTION_DONE ||
                                event.action === KeyEvent.ACTION_DOWN && event.keyCode === KeyEvent.KEYCODE_ENTER) {

                            val on = on as AbsSearchFakeActionView
                            on.setButtonVisibility()
                            on.searchEditText.handler.removeCallbacks(
                                    on.searchRunnable
                            )
                            donePressed = true
                            on.searchRunnable.run()
                            on.closeDialog()
                            return@OnEditorActionListener true
                        }
                        false
                    })

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