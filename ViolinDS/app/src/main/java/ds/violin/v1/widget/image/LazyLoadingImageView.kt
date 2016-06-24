package ds.violin.v1.widget.image

import android.animation.Animator
import android.animation.AnimatorInflater
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.res.ResourcesCompat
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import ds.violin.v1.R
import ds.violin.v1.util.ImageDescriptor
import ds.violin.v1.util.ImageLoader
import ds.violin.v1.util.common.AnimationStarter

/**
 * copied from [ImageView] to identify scale types
 */
private var ScaleTypeArray = arrayOf(
        ImageView.ScaleType.MATRIX,
        ImageView.ScaleType.FIT_XY,
        ImageView.ScaleType.FIT_START,
        ImageView.ScaleType.FIT_CENTER,
        ImageView.ScaleType.FIT_END,
        ImageView.ScaleType.CENTER,
        ImageView.ScaleType.CENTER_CROP,
        ImageView.ScaleType.CENTER_INSIDE
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
    var defaultScaleType: ImageView.ScaleType?
    /** 0 - call [readAttributes] in constructors to set */
    var loadingResID: Int
    /** false - call [readAttributes] in constructors to set */
    var loadingIsAnimated: Boolean
    /** null - call [readAttributes] in constructors to set */
    var loadingScaleType: ImageView.ScaleType?
    /** 0 - call [readAttributes] in constructors to set */
    var errorResID: Int
    /** false - call [readAttributes] in constructors to set */
    var errorIsAnimated: Boolean
    /** null - call [readAttributes] in constructors to set */
    var errorScaleType: ImageView.ScaleType?
    /** false - call [readAttributes] in constructors to set */
    var alwaysLoadInBackground: Boolean
    /** false - call [readAttributes] in constructors to set */
    var shouldScaleImageSize: Boolean
    /** null - call [readAttributes] in constructors to set */
    var setBitmapAnimation: Any?

    /** null, #Private */
    var originalVisibility: Int?
    /** lateinit, #Private */
    var originalScaleType: ImageView.ScaleType
    /** false, #Private */
    var loadedFromCache: Boolean

    var onImageSetListener: OnImageSetListener?

    var descriptor: ImageDescriptor     // #Private
    var completion: ((ImageDescriptor, Bitmap?, Throwable?) -> Unit)?   // #Private

    fun readAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.LazyImageView, defStyleAttr, defStyleRes)

        /** false = load from file in the ui thread */
        alwaysLoadInBackground = a.getBoolean(R.styleable.LazyImageView_alwaysLoadInBackground, true)

        /** NOT IMPLEMENTED YET: scale image to the result's width/height ratio? */
        shouldScaleImageSize = a.getBoolean(R.styleable.LazyImageView_shouldScaleImageSize, false)

        /** NOT TOO GOOD - should start animation on loaded image */
        isAnimated = a.getBoolean(R.styleable.LazyImageView_isAnimated, false)

        /** default image drawable resource id, if it is animated and it's scaleType */
        defaultResID = a.getResourceId(R.styleable.LazyImageView_defaultImage, 0)
        defaultIsAnimated = a.getBoolean(R.styleable.LazyImageView_defaultIsAnimated, false)
        var index = a.getInt(R.styleable.LazyImageView_defaultScaleType, -1)
        if (index != -1) {
            defaultScaleType = ScaleTypeArray[index]
        }

        /** loading image drawable resource id, if it is animated and it's scaleType */
        loadingResID = a.getResourceId(R.styleable.LazyImageView_loadingImage, 0)
        loadingIsAnimated = a.getBoolean(R.styleable.LazyImageView_loadingIsAnimated, false)
        index = a.getInt(R.styleable.LazyImageView_errorScaleType, -1)
        if (index != -1) {
            errorScaleType = ScaleTypeArray[index]
        }

        /** error image drawable resource id, if it is animated and it's scaleType */
        errorResID = a.getResourceId(R.styleable.LazyImageView_errorImage, 0)
        errorIsAnimated = a.getBoolean(R.styleable.LazyImageView_errorIsAnimated, false)
        index = a.getInt(R.styleable.LazyImageView_loadingScaleType, -1)
        if (index != -1) {
            loadingScaleType = ScaleTypeArray[index]
        }

        /**
         * bitmap animation when the loaded image becomes set
         * using a cool fade in animation if left unset unless it was loaded immediately from cache
         */
        setBitmapAnimation = a.getResourceId(R.styleable.LazyImageView_setBitmapAnimation, 0)
        if (setBitmapAnimation != 0) {
            try {
                AnimationUtils.loadAnimation((this as ImageView).context, setBitmapAnimation as Int)
            } catch(e: Throwable) {
                AnimatorInflater.loadAnimator((this as ImageView).context, setBitmapAnimation as Int)
            }
        }

        a.recycle()

        originalScaleType = (this as ImageView).scaleType
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
        if (completion != null) {
            when (descriptor) {
                this.descriptor -> return false
                else -> ImageLoader.stopLoading(this.descriptor, completion!!)
            }
        }

        this.descriptor = descriptor
        if (descriptor.url.length == 0) {
            showDefault()
            return false
        }

        completion = fun(descriptor, bitmap, error) {
            if (completion != null && descriptor == descriptor) {
                onBitmapLoaded(bitmap, error)
            }
        }

        loadedFromCache = false
        if (ImageLoader.load(descriptor, completion!!)) {
            showLoading()
        } else {
            loadedFromCache = true
        }
        return true
    }

    fun stopLoading() {
        if (completion != null) {
            ImageLoader.stopLoading(descriptor, completion!!)
            completion = null
        }
    }

    fun onBitmapLoaded(bmp: Bitmap?, error: Throwable?) {
        when {
            error != null -> showError()
            bmp != null -> setBitmap(bmp)
        }
        completion = null
    }

    fun setBitmap(bmp: Bitmap) {
        if (bmp.isRecycled) {
            completion = null
            loadImage(descriptor, onImageSetListener);
            return;
        }

        this as ImageView

        val fadingScaleType = scaleType

        if (originalVisibility != null) {
            visibility = originalVisibility!!
        }

        scaleType = originalScaleType

        if (isAnimated) {
            AnimationStarter.start(this)
        } else when (setBitmapAnimation) {
            0 -> {
                val oldDrawable: Drawable?
                val fadingMatrix: Matrix?
                if (loadedFromCache || drawable == null) {
                    oldDrawable = null
                    fadingMatrix = null
                } else {
                    oldDrawable = drawable
                    fadingMatrix = createScaleTypeMatrix(oldDrawable, fadingScaleType)
                }

                setImageDrawable(
                        FadeInBitmapDrawable(resources, bmp, oldDrawable, fadingMatrix)
                )
            }
            is Animation -> {
                setImageBitmap(bmp)
                this.animation = setBitmapAnimation as Animation
                AnimationStarter.start(this)
            }
            is Animator -> {
                setImageBitmap(bmp)
                (setBitmapAnimation as Animator).setTarget(this)
                (setBitmapAnimation as Animator).start()
            }

        }

        onImageSetListener?.onImageSet(this)
    }

    fun showDefault() {

        if (defaultResID == 0) {
            if (originalVisibility == null) {
                originalVisibility = (this as ImageView).visibility
            }
            (this as ImageView).visibility = View.GONE;
        } else {
            _setTempScaleType(defaultScaleType)

            if (originalVisibility == View.VISIBLE) {
                (this as ImageView).visibility = View.VISIBLE
            }
            (this as ImageView).setImageDrawable(ResourcesCompat.getDrawable(resources, defaultResID, null))

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

        if (originalVisibility == View.VISIBLE) {
            (this as ImageView).visibility = View.VISIBLE
        }
        (this as ImageView).setImageDrawable(ResourcesCompat.getDrawable(resources, loadingResID, null))

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

        if (originalVisibility == View.VISIBLE) {
            (this as ImageView).visibility = View.VISIBLE
        }
        (this as ImageView).setImageDrawable(ResourcesCompat.getDrawable(resources, errorResID, null))

        if (errorIsAnimated) {
            AnimationStarter.start(this)
        }

        onImageSetListener?.onErrorSet(this)
    }

    fun _setTempScaleType(scaleType: ImageView.ScaleType?) {
        if (scaleType == null) {
            return;
        }

        (this as ImageView).scaleType = scaleType;
    }

    /**
     * copied from [ImageView]'s code (and modified a bit) to create draw matrix for the fade animation
     * and for rounded corners for the default/loading drawable
     */
    fun createScaleTypeMatrix(drawable: Drawable, scaleType: ImageView.ScaleType, setDrawableBounds: Boolean = true): Matrix? {
        var drawMatrix: Matrix?

        val dwidth = drawable.intrinsicWidth
        val dheight = drawable.intrinsicHeight

        val vwidth = (this as ImageView).width - paddingLeft - paddingRight
        val vheight = height - paddingTop - paddingBottom

        val fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight)

        if (dwidth <= 0 || dheight <= 0 || ImageView.ScaleType.FIT_XY == scaleType) {
            if (setDrawableBounds) {
                drawable.setBounds(0, 0, vwidth, vheight)
            }
            drawMatrix = null
        } else {
            if (setDrawableBounds) {
                drawable.setBounds(0, 0, dwidth, dheight)
            }

            if (ImageView.ScaleType.MATRIX == scaleType) {

                if (matrix.isIdentity) {
                    drawMatrix = null
                } else {
                    drawMatrix = Matrix(matrix)
                }
            } else if (fits) {

                drawMatrix = null
            } else if (ImageView.ScaleType.CENTER == scaleType) {

                drawMatrix = Matrix(matrix)
                drawMatrix!!.setTranslate(Math.round((vwidth - dwidth) * 0.5f).toFloat(),
                        Math.round((vheight - dheight) * 0.5f).toFloat())
            } else if (ImageView.ScaleType.CENTER_CROP == scaleType) {
                drawMatrix = Matrix(matrix)

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
            } else if (ImageView.ScaleType.CENTER_INSIDE == scaleType) {
                drawMatrix = Matrix(matrix)
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

                drawMatrix = Matrix(matrix)

                val scaleToFit = when (scaleType) {
                    ImageView.ScaleType.FIT_START -> Matrix.ScaleToFit.START
                    ImageView.ScaleType.FIT_END -> Matrix.ScaleToFit.END
                    else -> Matrix.ScaleToFit.CENTER
                }
                drawMatrix!!.setRectToRect(tempSrc, tempDst, scaleToFit)
            }
        }
        return drawMatrix
    }

    /**
     * to be able to use different scale types for different states we need to do this here
     * with the unmodified canvas so call this before [ImageView.onDraw]
     */
    fun onDraw(canvas: Canvas) {
        val drawable = (this as ImageView).drawable
        if (drawable is FadeInBitmapDrawable) {
            drawable.drawFadingDrawable(canvas, this)
        }
    }
}

