package com.example.camera

import android.annotation.SuppressLint
import android.app.Service
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.camera.util.convertFace2ViewRect
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.max
import kotlin.math.sign

private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
    append(Surface.ROTATION_0, 90)
    append(Surface.ROTATION_90, 0)
    append(Surface.ROTATION_180, 270)
    append(Surface.ROTATION_270, 180)
}
private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
    append(Surface.ROTATION_0, 270)
    append(Surface.ROTATION_90, 180)
    append(Surface.ROTATION_180, 90)
    append(Surface.ROTATION_270, 0)
}


class Camera2PicAty : AppCompatActivity(), TextureView.SurfaceTextureListener {
    companion object {
        const val TAG = "Camera2PicAty"
        private const val STATE_IDLE = 0
        private const val STATE_WAITING_LOCK = 1
        private const val STATE_WAITING_AE_PRECAPTURE = 2
        private const val STATE_WAITING_AE_NO_PRECAPTURE = 3
        private const val STATE_PICTURE_TAKEN = 4
    }

    private lateinit var mCameraManager: CameraManager
    var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mRecordVideoSession: CameraCaptureSession? = null
    var mLargestSize: Size? = null
    private var mCameraId: String? = null
    private var mRequestBuilder: CaptureRequest.Builder? = null
    private var mState = STATE_IDLE
    private var mIsRecording = false

    private var mImageReader: ImageReader? = null
    private var mFile: File? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var mPath: String? = null
    private var mImageSensorArea: Rect? = null

    private var mBackgroundHandler: Handler? = null
    private var mSensorOrientation: Int? = null
    private var mPreviewSize: Size? = null
    private var mChoicePreviewSize: Size? = null

    private var mPreviewSizeAdapter: PreviewSizeAdapter? = null

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            super.onCaptureProgressed(session, request, partialResult)
//            Log.d(TAG, "partialResult:${partialResult}")
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            process(result)
            processForFace(result)
        }

        private fun processForFace(result: TotalCaptureResult) {
            val faces = result.get(CaptureResult.STATISTICS_FACES)
            val scalerCropRegion = result.get(CaptureResult.SCALER_CROP_REGION)
            faces?.forEach {
                Log.d(TAG, "scaleCropRegion:${scalerCropRegion.toString()}")

                Log.d(TAG, "face:${it.bounds}")

            }
            faces?.let {
                showView.setFaceAreas(it.map {
                    convertFace2ViewRect(
                        mPreviewSize!!, mImageSensorArea!!, it.bounds, Size(
                            textureView.width,
                            textureView.height
                        )
                    )
                })
            }

        }

        private fun process(result: CaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)
//            Log.d(TAG, "process AF_STATE:$afState  AE_STATE:$aeState  AWB_STATE:$awbState")
//            Log.d(TAG, "process frameNumber:${result.frameNumber}")
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
                STATE_WAITING_AE_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                    ) {
                        mState = STATE_WAITING_AE_NO_PRECAPTURE
                    }
                }
                STATE_WAITING_AE_NO_PRECAPTURE -> {
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
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
//            Log.d(TAG, "**********************capture started request:")
            request.keys.forEach {
                //                if (it.toString().toLowerCase().contains("ae"))
//                    Log.d(TAG, "map:${it.toString()}->${request.get(it)?.toString()}")
            }
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


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mCameraManager = getSystemService(Service.CAMERA_SERVICE) as CameraManager

        btnPicture.setOnClickListener {
            if (mIsRecording) {
                finishRecord()
            } else {
                takePicture()
            }
        }
        btnPicture.setOnLongClickListener {
            recordVideo()
            mIsRecording = true
            btnPicture.text = "完成录制"
            true
        }
        rvPreviews?.let {
            it.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            it.adapter = PreviewSizeAdapter(
                this, emptyList(),
                null, 0
            ).apply {
                mPreviewSizeAdapter = this
            }
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
        }
    }

    private fun recordVideo() {
        mCaptureSession?.close()
        prepareVideoRecorder()
        mCameraDevice!!.createCaptureSession(
            mutableListOf(Surface(textureView.surfaceTexture.apply {
                setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            }), mMediaRecorder!!.surface), object :
                CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {

                }

                override fun onConfigured(session: CameraCaptureSession) {
                    mRecordVideoSession = session
                    mRecordVideoSession?.let {
                        val request = mCameraDevice!!.createCaptureRequest(
                            CameraDevice.TEMPLATE_RECORD
                        )
                        request.apply {
                            addTarget(Surface(textureView.surfaceTexture))
                            addTarget(mMediaRecorder!!.surface)
                        }
                        it.setRepeatingRequest(request.build(), null, null)
                        mMediaRecorder?.start()
                    }
                }

            }, mBackgroundHandler
        )
    }

    private fun finishRecord() {
        mMediaRecorder?.stop()
        mMediaRecorder?.reset()
        mIsRecording = false
        btnPicture.text = "点击拍照（长按录制）"

        mRecordVideoSession?.close()
        startPreviewSession()
    }

    private fun prepareVideoRecorder(): Boolean {
        releaseMediaRecorder()
        mMediaRecorder = MediaRecorder()
        mMediaRecorder?.let { mMediaRecorder ->
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mPath = mPath ?: File(
                Environment.getExternalStorageDirectory(), "b_video_${System.currentTimeMillis()
                }.mp4"
            ).path
            mMediaRecorder.setOutputFile(mPath)
            mMediaRecorder.setVideoEncodingBitRate(10000000)
            mMediaRecorder.setVideoFrameRate(30)
            mMediaRecorder.setVideoSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            val rotation = windowManager.defaultDisplay.rotation
            when (mSensorOrientation) {
                SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder.setOrientationHint(
                    DEFAULT_ORIENTATIONS.get(rotation)
                )
                SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder.setOrientationHint(
                    INVERSE_ORIENTATIONS.get(rotation)
                )
            }
            mMediaRecorder.prepare()
        }
        return false
    }

    private fun releaseMediaRecorder() {
        mMediaRecorder?.reset()
        mMediaRecorder?.release()
        mMediaRecorder = null
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
        try {
            mCaptureSession?.close()
            mCaptureSession = null
            mCameraDevice?.close()
            mCameraDevice = null
            mImageReader?.close()
            mImageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        setupCameraOutputs(width, height)
        configSurface(width, height)
        val obj = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                startPreviewSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.d(TAG, "error:$error ")
            }
        }
        mCameraManager.openCamera(mCameraId!!, obj, mBackgroundHandler)
    }

    private fun startPreviewSession(size: Size? = null) {
        mCaptureSession?.close()
        mCameraDevice?.let {
            Log.d(TAG, "mPreviewSize:$mPreviewSize")
            val outputSurfaces = mutableListOf(
                Surface(textureView.surfaceTexture.apply {
                    setDefaultBufferSize(
                        size?.height ?: mPreviewSize!!.width,
                        size?.width ?: mPreviewSize!!.height
                    )
                })
                , mImageReader!!.surface
            )
            Log.d(TAG, "startPreviewSession:$size")
            size?.let {
                textureView.setAspect(
                    size.height,
                    size.width
                )
            }
            it.createCaptureSession(
                outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "configSessionFailed $session")
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        mCaptureSession = session
                        //根据镜头初始化一个合适的requestBuilder
                        mRequestBuilder =
                            it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        mRequestBuilder!!.addTarget(outputSurfaces[0])
                        configFaceDetected(mRequestBuilder!!)
                        session.setRepeatingRequest(
                            mRequestBuilder!!.build(),
                            mCaptureCallback, mBackgroundHandler
                        )
                    }
                },
                mBackgroundHandler
            )
        }
    }

    private fun setupCameraOutputs(width: Int, height: Int) {
        Log.d(TAG, "setupCameraOutputs width:${width}  height:${height}")
        mCameraManager.cameraIdList.forEach {
            val cameraCharacteristic = mCameraManager.getCameraCharacteristics(it)
            val facing = cameraCharacteristic.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                mCameraId = it
                val pixelSize =
                    cameraCharacteristic.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                Log.d(TAG, "mPixelArraySize:${pixelSize?.toString()}")
                mImageSensorArea =
                    cameraCharacteristic.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                Log.d(TAG, "mImageSensorArea:${mImageSensorArea.toString()}")
                val map =
                    cameraCharacteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                Log.d(
                    TAG, "support formats:${Arrays.toString(map.outputFormats)}"
                )
                val size = map?.getOutputSizes(ImageFormat.JPEG)
                Log.d(TAG, "JPEG outputSizes:${Arrays.toString(size)}")
                mLargestSize = size?.firstOrNull()
                Log.d(TAG, "mLargestSize:${mLargestSize.toString()}")
                mLargestSize = chooseOptimalSize(
                    map!!.getOutputSizes(ImageFormat.JPEG),
                    width,
                    height,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    mChoicePreviewSize ?: mLargestSize!!
                )
                mImageReader?.close()
                mImageReader = ImageReader.newInstance(
                    mLargestSize!!.width,
                    mLargestSize!!.height,
                    ImageFormat.JPEG, 2
                )
                mFile = File(
                    Environment.getExternalStorageDirectory(),
                    "a_demo${System.currentTimeMillis()}.jpg"
                )
                mImageReader!!.setOnImageAvailableListener({
                    mBackgroundHandler?.post(ImageSaver(it.acquireNextImage(), mFile!!))
                }, mBackgroundHandler)
                mSensorOrientation =
                    cameraCharacteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)
                Log.d(TAG, "sensorOrientation:${mSensorOrientation}")
                val displayRotation = windowManager.defaultDisplay.rotation
                Log.d(TAG, "displayRotation:${displayRotation}")
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
                Log.d(TAG, "window defaultDisplay size:${displaySize.toString()}")
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
                    map.getOutputSizes(SurfaceTexture::class.java).apply {
                        mPreviewSizeAdapter?.let {
                            it.data = this.toList()
                            it.notifyDataSetChanged()
                            it.onSelect = object : OnClickListener {
                                override fun onSelect(size: Size) {
                                    mChoicePreviewSize = size
                                    closeCamera()
                                    openCamera(size.height, size.width)
                                }
                            }
                        }
                    },
                    rotatedPreviewWidth,
                    rotatedPreviewHeight,
                    maxPreviewWidth,
                    maxPreviewHeight,
                    mChoicePreviewSize ?: mLargestSize!!
                )
                mPreviewSize = mChoicePreviewSize ?: mPreviewSize
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


    private fun runPrecaptureSequence() {
        Log.d(TAG, "runPrecaptureSequence")
        mRequestBuilder?.let {
            it.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            mState = STATE_WAITING_AE_PRECAPTURE
            mCaptureSession?.capture(
                it.build(),
                mCaptureCallback, mBackgroundHandler
            )
        }
    }

    private fun captureStillPicture() {
        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            // Orientation
            val rotation = windowManager.defaultDisplay.rotation
            Log.d(TAG, "captureStillPicture: rotation:$rotation")
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Toast.makeText(this@Camera2PicAty, "Saved: $mFile", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "captureStillPicture: saved")
                    unlockFocus()
                }
            }

            mCaptureSession!!.stopRepeating()
            mCaptureSession!!.abortCaptures()
            mCaptureSession!!.capture(captureBuilder.build(), captureCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mRequestBuilder?.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            mCaptureSession?.capture(
                mRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_IDLE
            mCaptureSession?.setRepeatingRequest(
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(Surface(textureView.surfaceTexture))
                }.build(),
                mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int): Int {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (when (rotation) {
            Surface.ROTATION_0 -> Surface.ROTATION_90
            Surface.ROTATION_90 -> Surface.ROTATION_0
            Surface.ROTATION_270 -> Surface.ROTATION_180
            Surface.ROTATION_180 -> Surface.ROTATION_270
            else -> 2
        } + mSensorOrientation!! + 270) % 360
    }

    private fun configFaceDetected(request: CaptureRequest.Builder) {
        request.set(
            CaptureRequest.STATISTICS_FACE_DETECT_MODE,
            CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
        )
    }

}


/**
 * Saves a JPEG [Image] into the specified [File].
 */
private class ImageSaver internal constructor(
    /**
     * The JPEG image
     */
    private val mImage: Image,
    /**
     * The file we save the image into.
     */
    private val mFile: File
) : Runnable {

    override fun run() {
        val buffer = mImage.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(mFile)
            output.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            mImage.close()
            if (null != output) {
                try {
                    output.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
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
