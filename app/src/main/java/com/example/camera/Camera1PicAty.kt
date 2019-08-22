package com.example.camera

import android.app.Activity
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.aty_camera1_pic.*
import java.io.File
import java.io.IOException
import kotlin.math.abs

const val TAG = "CameraPicAty"
const val MODE_PICTURE = 0
const val MODE_VIDEO = 1

class Camera1PicAty : Activity() {
    var mCamera: Camera? = null
    var mCameraId: Int? = null
    var mCameraNeedOrientation: Int? = null
    var mMediaRecorder: MediaRecorder? = null

    var mMode = MODE_PICTURE
    var mIsReocrding = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aty_camera1_pic)

        mCameraId = findDesiredCameraId()
        btnPicture.setOnClickListener {
            if (mIsReocrding) {
                stopRecord()
            } else {
                takePicture()
            }
        }
        btnPicture.setOnLongClickListener {
            recordVideo()
            true
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
            mIsReocrding = true
        }
    }

    private fun stopRecord() {
        releaseMediaRecorder()
        btnPicture.text = "拍照(长按拍摄)"
        mIsReocrding = false
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
                preview.setCamera(it)
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


}