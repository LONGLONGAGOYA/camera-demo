package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_preview_size.view.*

class PreviewSizeAdapter(
    val context: Context,
    var data: List<Size>,
    var onSelect: OnClickListener,
    val defaultSelect: Int = 0
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var mSelectedIndex = defaultSelect

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PreviewSizeViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_preview_size,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PreviewSizeViewHolder) {
            holder.onBindData(data[position], object : OnClickListener {
                override fun onSelect(size: Size) {
                    if (mSelectedIndex != position) {
                        mSelectedIndex = position
                        notifyDataSetChanged()
                        onSelect.onSelect(size)
                    }
                }
            }, position == mSelectedIndex)
        }
    }

}

class PreviewSizeViewHolder(override val containerView: View) :
    RecyclerView.ViewHolder(containerView), LayoutContainer {

    @SuppressLint("SetTextI18n")
    fun onBindData(
        size: Size,
        onSelect: OnClickListener,
        selected: Boolean
    ) {
        containerView.btnSize.let {
            it.text = "${size.width}x${size.height}"
            it.isEnabled = !selected
            it.setOnClickListener {
                onSelect.onSelect(size)
            }
        }
    }

}

interface OnClickListener {
    fun onSelect(size: Size)
}