package com.example.camera.ui

import android.content.Context
import android.graphics.Rect
import android.hardware.Camera
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.camera.Camera2PicAty.Companion.TAG

class Preview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) :
    SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {


    var mCamera: Camera? = null
    var mSupportPreviewSizes: List<Camera.Size>? = null
    var mPreviewSize: Camera.Size? = null
    var onReady: OnReady? = null
    var onFocusAreaChangeListener: FocusAreaChangeListener? = null
    var focusAreaRect: Rect? = null

    init {
        holder.addCallback(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (holder?.surface == null) {
            // preview surface does not exist
            return
        }
        Log.d(TAG, "surfaceChanged  width:$width   height:$height    format:$format")

        // stop preview before making changes
        try {
            mCamera?.stopPreview()
        } catch (e: Exception) {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        mCamera?.apply {
            try {
                mPreviewSize = mSupportPreviewSizes?.get(0)
                mPreviewSize?.let {
                    Log.d(TAG, "previewSize:${it.width}-${it.height}")
                    holder.setFixedSize(it.height, it.width)
                }
                setPreviewDisplay(holder)
                startPreview()
                onReady?.onDidStartPreview()
            } catch (e: Exception) {
                Log.d(TAG, "Error starting camera preview: ${e.message}")
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        mCamera?.stopPreview()
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        mCamera?.apply {
            try {
                setPreviewDisplay(holder)
                startPreview()
                onReady?.onDidStartPreview()
            } catch (e: Exception) {

            }
        }
    }

    fun setCamera(camera: Camera) {
        stopPreviewAndFreeCamera()
        mCamera = camera
        mCamera?.apply {
            mSupportPreviewSizes = parameters.supportedPreviewSizes
            requestLayout()

            setPreviewDisplay(holder)
            if (holder.surface.isValid) {
                startPreview()
                onReady?.onDidStartPreview()
            }
        }
    }


    fun stopPreviewAndFreeCamera() {
        mCamera?.apply {
            // Call stopPreview() to stop updating the preview surface.
            stopPreview()

            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            release()

            mCamera = null
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d(TAG, "onTouchEvent : ${focusAreaRect}")
        event?.let {
            val width = measuredWidth
            val height = measuredHeight
            val distance = 100
            focusAreaRect = Rect(
                (it.x - distance).toInt(),
                (it.y - distance).toInt(), (it.x + distance).toInt(), (it.y + distance).toInt()
            )
            val focusRect = Rect()

            Log.d(TAG, "onTouchEvent  focusRect:${focusAreaRect}")
            onFocusAreaChangeListener?.onAreaChange(Camera.Area(focusAreaRect, 900))
        }
        return super.onTouchEvent(event)
    }

}

interface FocusAreaChangeListener {
    /**
     * area:
     */
    fun onAreaChange(area: Camera.Area)

}

interface OnReady {
    fun onDidStartPreview()
}