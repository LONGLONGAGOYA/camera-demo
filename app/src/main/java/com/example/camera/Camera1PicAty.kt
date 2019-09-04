package com.example.camera

import android.app.Activity
import android.graphics.Rect
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.camera.ui.FocusAreaChangeListener
import com.example.camera.ui.OnReady
import com.example.camera.util.FaceHelper
import kotlinx.android.synthetic.main.aty_camera1_pic.*
import java.io.File
import java.io.IOException
import kotlin.math.abs

const val TAG = "CameraPicAty"

class Camera1PicAty : Activity() {
    var mCamera: Camera? = null
    var mCameraId: Int? = null
    var mCameraNeedOrientation: Int? = null
    var mMediaRecorder: MediaRecorder? = null
    var mFaceHelper: FaceHelper? = null

    private var mIsRecording = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aty_camera1_pic)

        mCameraId = findDesiredCameraId()
        btnPicture.setOnClickListener {
            if (mIsRecording) {
                stopRecord()
            } else {
                takePicture()
            }
        }
        btnPicture.setOnLongClickListener {
            recordVideo()
            true
        }

        preview.onFocusAreaChangeListener = object : FocusAreaChangeListener {
            override fun onAreaChange(area: Camera.Area) {
                //set param
                mCamera?.let {
                    val p = it.parameters
                    if (p.maxNumFocusAreas == 0) {
                        return
                    }
                    val areas = p.focusAreas ?: mutableListOf()
                    areas.clear()
                    areas.add(area)
                    p.focusAreas = areas
                    it.stopPreview()
                    try {
                        it.parameters = p
                        Toast.makeText(this@Camera1PicAty, "change success ", Toast.LENGTH_SHORT)
                            .show()
                    } catch (e: java.lang.Exception) {
                        Toast.makeText(this@Camera1PicAty, "change error", Toast.LENGTH_SHORT)
                            .show()
                    }
                    it.startPreview()
                    //show ui
                    showView.setFocusArea(mFaceHelper!!.convertFaceCoordinate(area.rect))
                }

            }
        }
    }

    private fun takePicture() {
        mCamera?.let {
            it.takePicture(
                {
                    Toast.makeText(this, "shutter now", Toast.LENGTH_SHORT).show()
                },
                { data, camera ->
                    //row data
                },
                { data, camera ->
                    //jpeg data write to file, now just show in console
                    //note: the orientation of saved image is same as sensor orientation,so need to rotate
                    val file = Environment.getExternalStorageDirectory()
                    val toFile = File(file, "a_demo${System.currentTimeMillis()}.jpg")
                    toFile.writeBytes(data)
                    mCamera?.startPreview()
                })
        }
    }

    private fun recordVideo() {
        if (prepareVideoRecorder()) {
            mMediaRecorder?.start()
            btnPicture.text = "stop"
            mIsRecording = true
        }
    }

    private fun stopRecord() {
        releaseMediaRecorder()
        btnPicture.text = "拍照(长按拍摄)"
        mIsRecording = false
    }

    private fun prepareVideoRecorder(): Boolean {
        releaseMediaRecorder()
        mMediaRecorder = MediaRecorder()
        val mediaRecorder = mMediaRecorder
        mCamera?.let { camera ->
            mediaRecorder?.run {
                camera.unlock()
                setCamera(camera)

                // Step 2: Set sources
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)

                // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
                //bug for both execute setProfile and setOutputFormat ,reference: https://stackoverflow.com/questions/21632769/setoutputformat-called-in-an-invalid-state-4-where-and-why
//                setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))

                // Step 4: Set output file
                setOutputFile(
                    File(
                        Environment.getExternalStorageDirectory(),
                        "a_video_" + System.currentTimeMillis() + ".mp4"
                    ).toString()
                )

                // Step 5: Set the preview output
                setPreviewDisplay(preview.holder.surface)

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)


                // Step 6: Prepare configured MediaRecorder
                return try {
                    prepare()

                    true
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "IllegalStateException preparing MediaRecorder: ${e.message}")
                    releaseMediaRecorder()
                    false
                } catch (e: IOException) {
                    Log.d(TAG, "IOException preparing MediaRecorder: ${e.message}")
                    releaseMediaRecorder()
                    false
                }
            }

        }
        return false
    }

    private fun releaseMediaRecorder() {
        mMediaRecorder?.reset()
        mMediaRecorder?.release()
        mMediaRecorder = null
    }

    override fun onResume() {
        super.onResume()
        mCameraId?.let { that ->
            safeOpenCamera(that)
            mCamera?.let {
                mCamera?.stopFaceDetection()
                mCamera?.stopPreview()
                configMeterArea()
                configFocusArea()
                preview.setCamera(it)
                preview.onReady = object : OnReady {
                    override fun onDidStartPreview() {
                        mCamera?.autoFocus(null)
                        configFaceDet()
                    }
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()
        releaseCameraAndPreview()
    }

    fun findDesiredCameraId(): Int? {
        val count = Camera.getNumberOfCameras()
        for (i in 0 until count) {
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(i, cameraInfo)
            mCameraNeedOrientation = cameraInfo.orientation
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i
            }
        }
        return null
    }

    fun safeOpenCamera(id: Int): Boolean {
        return try {
            releaseCameraAndPreview()
            mCamera = Camera.open(id)
            mCamera?.setDisplayOrientation(getOrientation(mCameraNeedOrientation!!))
            true
        } catch (e: Exception) {
            Log.e(TAG, "open error:$e")
            false
        }
    }

    private fun getOrientation(sensorOrientation: Int): Int {
        val naturalOrientation = windowManager.defaultDisplay.rotation
        Log.d(TAG, "getOrientation:$naturalOrientation")
        //this method is not right for all devices
        return abs(naturalOrientation - 1) * sensorOrientation
    }

    private fun releaseCameraAndPreview() {
        preview.stopPreviewAndFreeCamera()
    }

    //config manual focus
    private fun configMeterArea() {
        mCamera?.let {
            val param = it.parameters
            val maxNum = param.maxNumMeteringAreas
            if (maxNum > 0) {
                param.meteringAreas?.apply {
                    val r1 = Rect(-1000, -1000, -500, -500)
                    add(Camera.Area(r1, 60))
                    param.meteringAreas = this
                }
                it.parameters = param
            }
        }


    }

    private fun configFocusArea() {
        mCamera?.let {
            val param = it.parameters
            val maxNum = param.maxNumFocusAreas
            if (maxNum > 0) {
                param.focusAreas.apply {
                    val r1 = Rect(-1000, -1000, -500, -500)
                    val areas = mutableListOf<Camera.Area>()
                    areas.add(Camera.Area(r1, 1000))
                    Log.d(TAG, "focusArea:${this}")
                    param.focusAreas = areas
                }
                it.parameters = param
            }
        }
    }


    private fun configFaceDet() {
        mCamera?.let {
            val param = it.parameters
            val maxNum = param.maxNumDetectedFaces
            Log.d(
                TAG, "configMeterArea maxFocus:${param.maxNumFocusAreas}" +
                        "    maxMeterArea:${param.maxNumMeteringAreas}" +
                        "    maxDetectedFace:${param.maxNumDetectedFaces}"
            )
            if (maxNum > 0) {
                mFaceHelper = FaceHelper(
                    mCameraNeedOrientation ?: 0,
                    windowManager.defaultDisplay.rotation,
                    Rect(-1000, -1000, 1000, 1000),
                    Rect(0, 0, preview.measuredWidth, preview.measuredHeight)
                )
                it.setFaceDetectionListener { faces, camera ->
                    showView.setFaceAreas(faces.map { face ->
                        mFaceHelper!!.convertFaceCoordinate(face.rect)
                    })
                }
                mCamera?.startFaceDetection()
            }
        }
    }
}