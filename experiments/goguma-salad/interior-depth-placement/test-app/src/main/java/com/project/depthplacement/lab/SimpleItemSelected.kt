package com.project.depthplacement.lab

import android.view.View
import android.widget.AdapterView

class SimpleItemSelected(private val selected: (Int) -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = selected(position)
    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
