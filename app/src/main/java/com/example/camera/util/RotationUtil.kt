package com.example.camera.util

import android.util.Size

object RotationUtil {

    /**
     * @param sensorRotation  传感器角度 0,90,180,270
     * @param displayRotation 手机自然角度  Surface.ROTATION_0/90/180/270
     * @param maxDisplaySize 最大显示宽高，当前
     *
     */
    fun getPreviewAspectRatio(sensorRotation: Int,displayRotation:Int,maxDisplaySize:Size): Size {


        return Size(1, 1)
    }


}