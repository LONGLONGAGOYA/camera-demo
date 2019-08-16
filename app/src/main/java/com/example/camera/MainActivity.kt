package com.example.camera

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) {
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {

    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean {
        return false
    }


    override fun onSurfaceTextureAvailable(p0: SurfaceTexture?, p1: Int, p2: Int) {
    }

    lateinit var cameraManager: CameraManager

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissions = RxPermissions(this)
        permissions.request(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
        )
            .subscribe {
                if (it) {
                    cameraManager = getSystemService(CameraManager::class.java)
                    cameraManager.cameraIdList.forEach {
                        val cameraCharacteristic = cameraManager.getCameraCharacteristics(it)
                    }
                } else {
                    finish()
                }
            }
    }

    override fun onResume() {
        super.onResume()

        if (textureView.isAvailable) {

        } else {
            textureView.surfaceTextureListener = this
        }
    }

    override fun onPause() {
        super.onPause()
    }


}
