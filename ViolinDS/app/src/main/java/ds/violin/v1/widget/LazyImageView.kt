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
import android.animation.AnimatorInflater
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import ds.violin.v1.R
import ds.violin.v1.util.common.AnimationStarter
import ds.violin.v1.util.ImageDescriptor
import ds.violin.v1.util.ImageLoader

/**
 * copied from [ImageView] to identify scale types
 */
private var ScaleTypeArray = arrayOf(
        ScaleType.MATRIX,
        ScaleType.FIT_XY,
        ScaleType.FIT_START,
        ScaleType.FIT_CENTER,
        ScaleType.FIT_END,
        ScaleType.CENTER,
        ScaleType.CENTER_CROP,
        ScaleType.CENTER_INSIDE
)

/**
 * listener for image (set) states
 */
interface OnImageSetListener {
    fun onDefaultSet(view: LazyLoadingImageView)
    fun onLoadingSet(view: LazyLoadingImageView)
    fun onErrorSet(view: LazyLoadingImageView)
    fun onImageSet(view: LazyLoadingImageView)
}

/**
 * interface for an [ImageView] to lazy load images with default, loading and error images
 *
 * @use implement in an [ImageView] subclass using initial values as noted below
 *      also see [LazyImageView] for more clues
 *      set default, loading, or error images, scale types and if any of those images are animated
 *      and more in the layout (@see [readAttributes])
 *      call [loadImage] with an [ImageDescriptor] after
 */
interface LazyLoadingImageView {

    /** false - call [readAttributes] in constructors to set */
    var isAnimated: Boolean
    /** 0 - call [readAttributes] in constructors to set */
    var defaultResID: Int
    /** false - call [readAttributes] in constructors to set */
    var defaultIsAnimated: Boolean
    /** null - call [readAttributes] in constructors to set */
    var defaultScaleType: ScaleType?
    /** 0 - call [readAttributes] in constructors to set */
    var loadingResID: Int
    /** false - call [readAttributes] in constructors to set */
    var loadingIsAnimated: Boolean
    /** null - call [readAttributes] in constructors to set */
    var loadingScaleType: ScaleType?
    /** 0 - call [readAttributes] in constructors to set */
    var errorResID: Int
    /** false - call [readAttributes] in constructors to set */
    var errorIsAnimated: Boolean
    /** null - call [readAttributes] in constructors to set */
    var errorScaleType: ScaleType?
    /** false - call [readAttributes] in constructors to set */
    var alwaysLoadInBackground: Boolean
    /** false - call [readAttributes] in constructors to set */
    var shouldScaleImageSize: Boolean
    /** null - call [readAttributes] in constructors to set */
    var setBitmapAnimation: Any?

    /** null, #Private */
    var _originalVisibility: Int?
    /** null, #Private */
    var _originalScaleType: ScaleType?
    /** false, #Private */
    var _loadedFromCache: Boolean

    var onImageSetListener: OnImageSetListener?

    var _descriptor: ImageDescriptor     // #Private
    var _completion: ((ImageDescriptor, Bitmap?, Throwable?) -> Unit)?   // #Private

    fun readAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.LazyLoadingImageView, defStyleAttr, defStyleRes);

        /** false = load from file in the ui thread */
        alwaysLoadInBackground = a.getBoolean(R.styleable.LazyLoadingImageView_alwaysLoadInBackground, true)

        /** NOT IMPLEMENTED YET: scale image to the result's width/height ratio? */
        shouldScaleImageSize = a.getBoolean(R.styleable.LazyLoadingImageView_shouldScaleImageSize, false)

        /** NOT TOO GOOD - should start animation on loaded image */
        isAnimated = a.getBoolean(R.styleable.LazyLoadingImageView_isAnimated, false)

        /** default image drawable resource id, if it is animated and it's scaleType */
        defaultResID = a.getResourceId(R.styleable.LazyLoadingImageView_defaultImage, 0)
        defaultIsAnimated = a.getBoolean(R.styleable.LazyLoadingImageView_defaultIsAnimated, false)
        var index = a.getInt(R.styleable.LazyLoadingImageView_defaultScaleType, -1)
        if (index != -1) {
            defaultScaleType = ScaleTypeArray[index]
        }

        /** loading image drawable resource id, if it is animated and it's scaleType */
        loadingResID = a.getResourceId(R.styleable.LazyLoadingImageView_loadingImage, 0)
        loadingIsAnimated = a.getBoolean(R.styleable.LazyLoadingImageView_loadingIsAnimated, false)
        index = a.getInt(R.styleable.LazyLoadingImageView_errorScaleType, -1)
        if (index != -1) {
            errorScaleType = ScaleTypeArray[index]
        }

        /** error image drawable resource id, if it is animated and it's scaleType */
        errorResID = a.getResourceId(R.styleable.LazyLoadingImageView_errorImage, 0)
        errorIsAnimated = a.getBoolean(R.styleable.LazyLoadingImageView_errorIsAnimated, false)
        index = a.getInt(R.styleable.LazyLoadingImageView_loadingScaleType, -1)
        if (index != -1) {
            loadingScaleType = ScaleTypeArray[index]
        }

        /**
         * bitmap animation when the loaded image becomes set
         * using a cool fade in animation if left unset unless it was loaded immediately from cache
         */
        setBitmapAnimation = a.getResourceId(R.styleable.LazyLoadingImageView_setBitmapAnimation, 0)
        if (setBitmapAnimation != 0) {
            try {
                AnimationUtils.loadAnimation((this as ImageView).context, setBitmapAnimation as Int)
            } catch(e: Throwable) {
                AnimatorInflater.loadAnimator((this as ImageView).context, setBitmapAnimation as Int)
            }
        }

        a.recycle()
    }

    /**
     * load the image immediately from cache or in the background if it wasn't found
     *
     * @param descriptor the [ImageDescriptor] holding the image url as a minimum
     * @param onImageSetListener set [OnImageSetListener] for states when needed
     *
     * @return true if loading is started
     */
    fun loadImage(descriptor: ImageDescriptor, onImageSetListener: OnImageSetListener? = null): Boolean {
        this.onImageSetListener = onImageSetListener
        if (_completion != null) {
            when (descriptor) {
                _descriptor -> return false
                else -> ImageLoader.stopLoading(_descriptor, _completion!!)
            }
        }

        _descriptor = descriptor
        if (_descriptor.url.length == 0) {
            showDefault()
            return false
        }

        _completion = fun(descriptor, bitmap, error) {
            if (_completion != null && _descriptor == descriptor) {
                onBitmapLoaded(bitmap, error)
            }
        }

        _loadedFromCache = false
        if (ImageLoader.load(_descriptor, _completion!!)) {
            showLoading()
        } else {
            _loadedFromCache = true
        }
        return true
    }

    fun stopLoading() {
        if (_completion != null) {
            ImageLoader.stopLoading(_descriptor, _completion!!)
            _completion = null
        }
    }

    fun onBitmapLoaded(bmp: Bitmap?, error: Throwable?) {
        when {
            error != null -> showError()
            bmp != null -> setBitmap(bmp)
        }
        _completion = null
    }

    fun setBitmap(bmp: Bitmap) {
        if (bmp.isRecycled) {
            _completion = null
            loadImage(_descriptor, onImageSetListener);
            return;
        }

        val thisAsImageView = this as ImageView

        if (isAnimated) {
            AnimationStarter.start(thisAsImageView)
        } else when (setBitmapAnimation) {
            0 -> {
                val oldDrawable: Drawable?
                val fadingMatrix: Matrix?

                if (_loadedFromCache || thisAsImageView.drawable == null) {
                    oldDrawable = null
                    fadingMatrix = null
                } else {
                    oldDrawable = thisAsImageView.drawable
                    fadingMatrix = _configureFadingDrawable(oldDrawable, scaleType)
                }

                thisAsImageView.setImageDrawable(
                        FadeInBitmapDrawable(resources, bmp, oldDrawable, fadingMatrix)
                )
            }
            is Animation -> {
                thisAsImageView.setImageBitmap(bmp)
                this.animation = setBitmapAnimation as Animation
                AnimationStarter.start(this)
            }
            is Animator -> {
                thisAsImageView.setImageBitmap(bmp)
                (setBitmapAnimation as Animator).setTarget(this)
                (setBitmapAnimation as Animator).start()
            }

        }

        if (_originalVisibility != null) {
            thisAsImageView.visibility = _originalVisibility!!
        }

        if (_originalScaleType != null) {
            thisAsImageView.scaleType = _originalScaleType
        }

        onImageSetListener?.onImageSet(this)
    }

    fun showDefault() {

        if (defaultResID == 0) {
            if (_originalVisibility == null) {
                _originalVisibility = (this as ImageView).visibility
            }
            (this as ImageView).visibility = View.GONE;
        } else {
            _setTempScaleType(defaultScaleType)

            if (_originalVisibility == View.VISIBLE) {
                (this as ImageView).visibility = View.VISIBLE
            }
            (this as ImageView).setImageResource(defaultResID)

            if (defaultIsAnimated) {
                AnimationStarter.start(this)
            }
        }

        onImageSetListener?.onDefaultSet(this)
    }

    fun showLoading() {
        if (loadingResID == 0) {
            showDefault()
            return;
        }

        _setTempScaleType(loadingScaleType)

        if (_originalVisibility == View.VISIBLE) {
            (this as ImageView).visibility = View.VISIBLE
        }
        (this as ImageView).setImageResource(loadingResID)

        if (loadingIsAnimated) {
            AnimationStarter.start(this)
        }

        onImageSetListener?.onLoadingSet(this)
    }

    fun showError() {
        if (errorResID == 0) {
            showDefault()
            return;
        }

        _setTempScaleType(errorScaleType)

        if (_originalVisibility == View.VISIBLE) {
            (this as ImageView).visibility = View.VISIBLE
        }
        (this as ImageView).setImageResource(errorResID)

        if (errorIsAnimated) {
            AnimationStarter.start(this)
        }

        onImageSetListener?.onErrorSet(this)
    }

    fun _setTempScaleType(scaleType: ScaleType?) {
        if (scaleType == null) {
            return;
        }
        if (_originalScaleType == null) {
            _originalScaleType = (this as ImageView).scaleType
        }
        (this as ImageView).scaleType = scaleType;
    }

    /**
     * copied from [ImageView]'s code (and modified a bit) to create draw matrix for the fade animation
     * for the default/loading drawable
     */
    fun _configureFadingDrawable(fadingDrawable: Drawable, scaleType: ScaleType): Matrix? {
        var drawMatrix: Matrix?

        val dwidth = fadingDrawable.intrinsicWidth
        val dheight = fadingDrawable.intrinsicHeight

        val vwidth = (this as ImageView).width - paddingLeft - paddingRight
        val vheight = height - paddingTop - paddingBottom

        val fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight)

        if (dwidth <= 0 || dheight <= 0 || ScaleType.FIT_XY == scaleType) {
            fadingDrawable.setBounds(0, 0, vwidth, vheight)
            drawMatrix = null
        } else {
            fadingDrawable.setBounds(0, 0, dwidth, dheight)

            if (ScaleType.MATRIX == scaleType) {

                if (matrix.isIdentity) {
                    drawMatrix = null
                } else {
                    drawMatrix = matrix
                }
            } else if (fits) {

                drawMatrix = null
            } else if (ScaleType.CENTER == scaleType) {

                drawMatrix = matrix
                drawMatrix!!.setTranslate(Math.round((vwidth - dwidth) * 0.5f).toFloat(),
                        Math.round((vheight - dheight) * 0.5f).toFloat())
            } else if (ScaleType.CENTER_CROP == scaleType) {
                drawMatrix = matrix

                val scale: Float
                var dx = 0f
                var dy = 0f

                if (dwidth * vheight > vwidth * dheight) {
                    scale = vheight.toFloat() / dheight.toFloat()
                    dx = (vwidth - dwidth * scale) * 0.5f
                } else {
                    scale = vwidth.toFloat() / dwidth.toFloat()
                    dy = (vheight - dheight * scale) * 0.5f
                }

                drawMatrix!!.setScale(scale, scale)
                drawMatrix.postTranslate(Math.round(dx).toFloat(), Math.round(dy).toFloat())
            } else if (ScaleType.CENTER_INSIDE == scaleType) {
                drawMatrix = matrix
                val scale: Float
                val dx: Float
                val dy: Float

                if (dwidth <= vwidth && dheight <= vheight) {
                    scale = 1.0f
                } else {
                    scale = Math.min(vwidth.toFloat() / dwidth.toFloat(),
                            vheight.toFloat() / dheight.toFloat())
                }

                dx = Math.round((vwidth - dwidth * scale) * 0.5f).toFloat()
                dy = Math.round((vheight - dheight * scale) * 0.5f).toFloat()

                drawMatrix!!.setScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            } else {
                var tempSrc = RectF()
                var tempDst = RectF()

                tempSrc.set(0f, 0f, dwidth.toFloat(), dheight.toFloat())
                tempDst.set(0f, 0f, vwidth.toFloat(), vheight.toFloat())

                drawMatrix = matrix

                val scaleToFit = when (scaleType) {
                    ScaleType.FIT_START -> Matrix.ScaleToFit.START
                    ScaleType.FIT_END -> Matrix.ScaleToFit.END
                    else -> Matrix.ScaleToFit.CENTER
                }
                drawMatrix!!.setRectToRect(tempSrc, tempDst, scaleToFit)
            }
        }
        return drawMatrix
    }

}

/**
 * FadeInBitmapDrawable for fade in animation
 */
open class FadeInBitmapDrawable(res: Resources, bmp: Bitmap, fadingDrawable: Drawable?, fadingMatrix: Matrix?) :
        BitmapDrawable(res, bmp) {

    var fadingDrawable: Drawable? = fadingDrawable
    protected val fadingMatrix: Matrix?
    private var animationStarted: Long = 0
    protected val animationLength: Long = 220L
    private var fadingAlpha: Int = 255

    init {
        if (fadingDrawable != null) {
            this.fadingMatrix = Matrix(fadingMatrix)
            if (fadingDrawable.bounds.width() == 0) {
                this.fadingDrawable = null
            } else {
                animationStarted = SystemClock.uptimeMillis()
                invalidateSelf()
            }
        } else {
            this.fadingMatrix = null
        }
    }

    fun countAlpha() {
        val elapsedTime = SystemClock.uptimeMillis() - animationStarted
        if (elapsedTime >= animationLength) {
            fadingAlpha = 255
            animationStarted = 0L
            fadingDrawable = null
            alpha = 255
        }

        fadingAlpha = ((elapsedTime * 255) / animationLength).toInt()
    }

    override fun draw(canvas: Canvas?) {
        if (animationStarted == 0L) {
            super.draw(canvas)
            return
        }

        alpha = fadingAlpha
        super.draw(canvas)
        alpha = 255
    }

    fun drawFadingDrawable(canvas: Canvas, imageView: ImageView) {
        if (fadingDrawable == null) {
            return
        }

        countAlpha()

        if (fadingDrawable == null) {
            return
        }

        fadingDrawable!!.alpha = 255 - fadingAlpha
        if (fadingMatrix == null && imageView.paddingTop === 0 && imageView.paddingLeft === 0) {
            fadingDrawable!!.draw(canvas)
        } else {
            val saveCount = canvas.saveCount
            canvas.save()

            if (imageView.cropToPadding) {
                val scrollX = imageView.scrollX
                val scrollY = imageView.scrollY
                canvas.clipRect(scrollX + imageView.paddingLeft, scrollY + imageView.paddingTop,
                        scrollX + imageView.right - imageView.left - imageView.paddingRight,
                        scrollY + imageView.bottom - imageView.top - imageView.paddingBottom)
            }

            canvas.translate(imageView.paddingLeft.toFloat(), imageView.paddingTop.toFloat())

            if (fadingMatrix != null) {
                canvas.concat(fadingMatrix)
            }

            fadingDrawable!!.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
        fadingDrawable!!.alpha = 255
    }
}

/**
 * an [ImageView] with lazy loading - with default, loading and error images
 */
class LazyImageView : ImageView, LazyLoadingImageView {

    override var onImageSetListener: OnImageSetListener? = null
    override var isAnimated: Boolean = false
    override var defaultResID: Int = 0
    override var defaultIsAnimated: Boolean = false
    override var defaultScaleType: ScaleType? = null
    override var loadingResID: Int = 0
    override var loadingIsAnimated: Boolean = false
    override var loadingScaleType: ScaleType? = null
    override var errorResID: Int = 0
    override var errorIsAnimated: Boolean = false
    override var errorScaleType: ScaleType? = null
    override var alwaysLoadInBackground: Boolean = false
    override var shouldScaleImageSize: Boolean = false
    override var setBitmapAnimation: Any? = null
    override var _originalVisibility: Int? = null
    override var _originalScaleType: ScaleType? = null
    override var _loadedFromCache: Boolean = false
    override lateinit var _descriptor: ImageDescriptor
    override var _completion: ((ImageDescriptor, Bitmap?, Throwable?) -> Unit)? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {

        /** initialize [LazyLoadingImageView] */
        readAttributes(context, attrs, defStyleAttr, 0)
    }


    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    super(context, attrs, defStyleAttr, defStyleRes) {

        /** initialize [LazyLoadingImageView] */
        readAttributes(context, attrs, defStyleAttr, defStyleRes)
    }

    override fun onDraw(canvas: Canvas) {
        if (drawable is FadeInBitmapDrawable) {

            // unfortunately to be able to use different scale types for different states we need to do this here
            // with the unmodified canvas
            (drawable as FadeInBitmapDrawable).drawFadingDrawable(canvas, this)
        }

        super.onDraw(canvas)
    }
}