package com.example.camera.util

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.camera.TAG

class FaceHelper(
    val sensorOrientation: Int,
    val currentOrientation: Int,
    val activeArraySize: Rect,
    val previewSize: Rect
) {
    private var mMatrix: Matrix? = null
    private var mReverseMatrix: Matrix? = null

    init {
        mMatrix = Matrix()
        mReverseMatrix = Matrix()
        mMatrix?.let {
            //rotate should change by sensorOrientation and currentOrientation
            it.postRotate(sensorOrientation.toFloat())
            mReverseMatrix!!.preRotate(-sensorOrientation.toFloat())
            //translate
            it.postTranslate(1000f, 1000f)
            mReverseMatrix!!.preTranslate(-1000f, -1000f)
            //scale
            //swapDimension's value should change by sensorOrientation and currentOrientation
            val swapDimension = true
            if (swapDimension) {
                it.postScale(
                    (previewSize.width().toFloat() / activeArraySize.height()),
                    (previewSize.height().toFloat() / activeArraySize.width())
                )
                mReverseMatrix!!.preScale(
                    activeArraySize.height().toFloat() / previewSize.width(),
                    activeArraySize.width().toFloat() / previewSize.height()
                )
            } else {
                it.postScale(
                    (previewSize.width() / activeArraySize.width()).toFloat(),
                    (previewSize.height() / activeArraySize.height()).toFloat()
                )
                mReverseMatrix!!.preScale(
                    activeArraySize.width().toFloat() / previewSize.width(),
                    activeArraySize.height().toFloat() / previewSize.height()
                )
            }
            test()
        }
    }

    private fun test() {
        val r1 = RectF(-1000f, -1000f, 0f, 0f)
        val r2 = RectF(0f, -1000f, 1000f, -0f)
        val r3 = RectF(0f, 0f, 1000f, 1000f)
        val r4 = RectF(-1000f, 0f, 0f, 1000f)
        Log.d(TAG, "FaceUtil r1->${r1.apply { mMatrix!!.mapRect(this) }}")
        Log.d(TAG, "FaceUtil r2->${r2.apply { mMatrix!!.mapRect(this) }}")
        Log.d(TAG, "FaceUtil r3->${r3.apply { mMatrix!!.mapRect(this) }}")
        Log.d(TAG, "FaceUtil r4->${r4.apply { mMatrix!!.mapRect(this) }}")

        Log.d(TAG, "FaceUtil r1->${r1.apply { mReverseMatrix!!.mapRect(this) }}")
        Log.d(TAG, "FaceUtil r2->${r2.apply { mReverseMatrix!!.mapRect(this) }}")
        Log.d(TAG, "FaceUtil r3->${r3.apply { mReverseMatrix!!.mapRect(this) }}")
        Log.d(TAG, "FaceUtil r4->${r4.apply { mReverseMatrix!!.mapRect(this) }}")
    }

    fun convertFaceCoordinate(rawRect: Rect): Rect {
        val rf = RectF(rawRect)
        val ret = mMatrix!!.mapRect(rf)
        Log.d(TAG, "FaceHelper:$this   rawRect:${rawRect.toString()}   retRect:${rf}")
        return Rect(rf.left.toInt(), rf.top.toInt(), rf.right.toInt(), rf.bottom.toInt())
    }

    fun reverseFaceCoordinate(rawRect: Rect): Rect {
        val rf = RectF(rawRect)
        val ret = mReverseMatrix!!.mapRect(rf)
        Log.d(TAG, "FaceHelper:$this   rawRect:${rawRect.toString()}   retRect:${rf}")
        return Rect(rf.left.toInt(), rf.top.toInt(), rf.right.toInt(), rf.bottom.toInt())
    }

    override fun toString(): String {
        return "FaceHelper(sensorOrientation=$sensorOrientation, currentOrientation=$currentOrientation, activeArraySize=$activeArraySize, previewSize=$previewSize, mMatrix=$mMatrix)"
    }


}