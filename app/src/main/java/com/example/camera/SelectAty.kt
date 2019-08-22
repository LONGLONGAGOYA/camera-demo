package com.example.camera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.aty_selector.*

class SelectAty:Activity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.aty_selector)

        btn1Pic.setOnClickListener {
            startActivity(Intent(this,Camera1PicAty::class.java))
        }
        btn2Pic.setOnClickListener {
            startActivity(Intent(this,Camera2PicAty::class.java))
        }
    }


}