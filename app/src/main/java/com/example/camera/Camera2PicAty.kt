package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.math.max
import kotlin.math.sign


class Camera2PicAty : AppCompatActivity(), TextureView.SurfaceTextureListener {
    companion object {
        const val TAG = "Camera2PicAty"
        private const val STATE_IDLE = 0
        private const val STATE_WAITING_LOCK = 1
        private const val STATE_WAITING_AE_DONE = 2
    }

    private lateinit var mCameraManager: CameraManager
    var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    var mLargestSize: Size? = null
    private var mCameraId: String? = null
    private var mRequestBuilder: CaptureRequest.Builder? = null
    private var mState = STATE_IDLE
    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            super.onCaptureProgressed(session, request, partialResult)
//            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            process(result)
        }

        private fun process(result: CaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)
            Log.d(TAG, "process AF_STATE:$afState  AE_STATE:$aeState  AWB_STATE:$awbState")
            Log.d(TAG, "process frameNumber:${result.frameNumber}")
            when (mState) {
                STATE_IDLE -> {
                }
                STATE_WAITING_LOCK -> {
                    //waiting lock 根据返回结果判断AF状态
                    if (afState == null) {
                        captureStillPicture()
                    } else if (afState in listOf(
                            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                            CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                        )
                    ) {
                        //af locked 处理ae
                        if (aeState == null) {
                            captureStillPicture()
                        } else if (aeState !in listOf(CaptureResult.CONTROL_AE_STATE_CONVERGED)) {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_AE_DONE -> {

                }
            }
        }

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            Log.d(TAG, "capture started request:")
//            request.keys.forEach {
//                Log.d(TAG, "map:${it.toString()}->${request.get(it)?.toString()}")
//            }
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
            Log.d(TAG, "capture failed request:${request.keys}")
        }

    }

    private fun runPrecaptureSequence() {
        Log.d(TAG, "runPrecaptureSequence")
        mRequestBuilder?.let {
            it.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            mState = STATE_WAITING_AE_DONE
            mCaptureSession?.capture(
                it.build(),
                mCaptureCallback, mBackgroundHandler
            )
        }
    }

    private fun captureStillPicture() {

    }

    var mBackgroundHandler: Handler? = null
    private var mSensorOrientation: Int? = null
    private var mPreviewSize: Size? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createHandler()
        RxPermissions(this).request(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ).subscribe {
            if (it) {
                mCameraManager = getSystemService(Service.CAMERA_SERVICE) as CameraManager
            } else {
                finish()
            }
        }
        btnPicture.setOnClickListener {
            takePicture()
        }
    }

    override fun onResume() {
        super.onResume()
        createHandler()
        if (textureView.isAvailable) {
            Log.d(TAG, "onResume available")
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = this
            Log.d(TAG, "onResume not available")
        }
    }

    override fun onPause() {
        closeCamera()
        clearHandler()
        super.onPause()
    }

    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChange width:$p1   height:$p2")
        configSurface(p1, p2)
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean {
        return false
    }

    override fun onSurfaceTextureAvailable(p0: SurfaceTexture?, p1: Int, p2: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable  width:$p1   height:$p2")
        openCamera(textureView.width, textureView.height)
    }


    private fun takePicture() {
        //first lock focus
        mRequestBuilder?.let {
            it.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            mState = STATE_WAITING_LOCK
            mCaptureSession?.capture(it.build(), mCaptureCallback, mBackgroundHandler)
            Log.d(TAG, "takePicture")
        }
    }

    private fun createHandler() {
        val thread = HandlerThread("camera-bg")
        thread.start()
        mBackgroundHandler = Handler(thread.looper)
    }

    private fun clearHandler() {
        mBackgroundHandler?.looper?.thread?.let {
            (it as HandlerThread).apply {
                quitSafely()
                try {
                    join()
                } catch (e: Exception) {
                }
            }
        }
        mBackgroundHandler = null
    }

    private fun closeCamera() {
        mCaptureSession?.close()
        mCameraDevice?.close()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        setupCameraOutputs(width, height)
        configSurface(width, height)
        val obj = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                mCameraDevice?.let {
                    val outputSurfaces = mutableListOf(Surface(textureView.surfaceTexture.apply {
                        setDefaultBufferSize(mLargestSize!!.width, mLargestSize!!.height)
                    }))
                    it.createCaptureSession(
                        outputSurfaces,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                            }

                            override fun onConfigured(session: CameraCaptureSession) {
                                mCaptureSession = session
                                mRequestBuilder =
                                    it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                mRequestBuilder!!.addTarget(outputSurfaces[0])
                                session.setRepeatingRequest(
                                    mRequestBuilder!!.build(),
                                    null, null
                                )
                            }
                        },
                        mBackgroundHandler
                    )
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }
        }
        mCameraManager.openCamera(mCameraId!!, obj, mBackgroundHandler)
    }

    private fun setupCameraOutputs(width: Int, height: Int) {
        mCameraManager.cameraIdList.forEach {
            val cameraCharacteristic = mCameraManager.getCameraCharacteristics(it)
            val facing = cameraCharacteristic.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                mCameraId = it
                val map =
                    cameraCharacteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val size = map?.getOutputSizes(ImageFormat.JPEG)
                mLargestSize = size?.firstOrNull()
                mSensorOrientation =
                    cameraCharacteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)
                val displayRotation = windowManager.defaultDisplay.rotation
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> {
                        if (mSensorOrientation!! in listOf(90, 270)) {
                            swappedDimensions = true
                        }
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> {
                        if (mSensorOrientation!! in listOf(0, 180)) {
                            swappedDimensions = true
                        }
                    }
                }
                val displaySize = Point()
                windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y
                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }
                mPreviewSize = chooseOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    rotatedPreviewWidth,
                    rotatedPreviewHeight,
                    maxPreviewWidth,
                    maxPreviewHeight,
                    mLargestSize!!
                )
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspect(mPreviewSize!!.width, mPreviewSize!!.height)
                } else {
                    textureView.setAspect(mPreviewSize!!.height, mPreviewSize!!.width)
                }
            }
        }
    }

    private fun configSurface(width: Int, height: Int) {
        if (null == textureView || null == mPreviewSize) {
            return
        }
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0.toFloat(), 0.toFloat(), width.toFloat(), height.toFloat())
        val bufferRect =
            RectF(0.toFloat(), 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                height.toFloat() / mPreviewSize!!.height,
                width.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private fun chooseOptimalSize(
        choices: Array<out Size>,
        textureViewWidth: Int,
        textureViewHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        aspectRatio: Size
    ): Size? {
        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough = mutableListOf<Size>()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough = mutableListOf<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight &&
                option.height == option.width * h / w
            ) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return when {
            bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> choices[0]
        }
    }

}

/**
 * Compares two `Size`s based on their areas.
 */
class CompareSizesByArea : Comparator<Size> {

    override fun compare(lhs: Size, rhs: Size): Int {
        // We cast here to ensure the multiplications won't overflow
        return sign(lhs.width.toDouble() * lhs.height - rhs.width.toDouble() * rhs.height).toInt()
    }

}
