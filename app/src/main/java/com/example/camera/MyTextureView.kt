package com.example.camera

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

class MyTextureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {

    private var mAspectWidth = 1
    private var mAspectHeight = 1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        setMeasuredDimension(width, (width.toFloat() * mAspectHeight / mAspectWidth).toInt())
    }

    fun setAspect(width: Int, height: Int) {
        mAspectWidth = width
        mAspectHeight = height
        requestLayout()
    }

}