package com.example.camera.ui

import android.content.Context
import android.hardware.Camera
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.camera.Camera2PicAty.Companion.TAG

class Previewcontext @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) :
    SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    var mCamera: Camera? = null
    var mSupportPreviewSizes: List<Camera.Size>? = null

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
                setPreviewDisplay(holder)
                startPreview()
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
            startPreview()
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

}