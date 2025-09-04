/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: Apache-2.0
 */

package org.protonaosp.columbus.widget

import android.content.Context
import android.widget.ImageView
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.preference.selector.R as selectorR
import com.android.settingslib.widget.theme.R as themeR
import org.protonaosp.columbus.R

class RadioButtonPreference : SelectorWithWidgetPreference {
    private var extraWidgetView: ImageView? = null
    private var metric: Int = 0
    private val iconSize: Int
    private var _contextualSummaryProvider: ContextualSummaryProvider? = null

    interface ContextualSummaryProvider {
        fun getSummary(context: Context): CharSequence
    }

    constructor(context: Context) : super(context) {
        iconSize =
            context.getResources().getDimensionPixelSize(themeR.dimen.secondary_app_icon_size)
    }

    private fun makeIconFixedSize(imageView: ImageView) {
        imageView.layoutParams?.apply {
            width = iconSize
            height = iconSize
        }
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun updateAccessibilityDescription() {
        extraWidgetView?.setContentDescription(
            getContext().getString(R.string.radio_button_extra_widget_a11y_label, getTitle())
        )
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        extraWidgetView = holder.findViewById(selectorR.id.selector_extra_widget) as ImageView?
        val icon = holder.findViewById(android.R.id.icon) as ImageView?
        icon?.let { makeIconFixedSize(it) }
        updateAccessibilityDescription()
    }

    fun setContextualSummaryProvider(summaryProvider: ContextualSummaryProvider?) {
        _contextualSummaryProvider = summaryProvider
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        updateAccessibilityDescription()
    }

    fun updateSummary(context: Context) {
        setSummary(_contextualSummaryProvider?.getSummary(context))
    }
}
