package com.example.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.example.camera.Camera2PicAty.Companion.TAG

class ShowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val focusPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 9f
    }
    private val facePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 9f
    }

    private val mFocusRect = Rect()
    private var mFacesRects: List<Rect>? = null

    fun setFocusArea(focusRect: Rect) {
        mFocusRect.set(focusRect)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            it.drawRect(mFocusRect, focusPaint)
            mFacesRects?.forEach { rect ->
                it.drawRect(rect, facePaint)
            }
        }
    }

    fun setFaceAreas(map: List<Rect>) {
        if (map == mFacesRects) {
            return
        }
        mFacesRects = map
        postInvalidate()
    }


}