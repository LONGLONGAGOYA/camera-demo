package com.example.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.aty_selector.*

class SelectAty : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.aty_selector)
        RxPermissions(this).request(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ).subscribe {
            if (!it) {
                finish()
            }
        }

        btn1Pic.setOnClickListener {
            startActivity(Intent(this, Camera1PicAty::class.java))
        }
        btn2Pic.setOnClickListener {
            startActivity(Intent(this, Camera2PicAty::class.java))
        }
    }


}