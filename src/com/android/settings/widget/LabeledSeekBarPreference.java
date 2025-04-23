/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.android.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;

import org.protonaosp.columbus.R;

/** A slider preference with left and right labels **/
public class LabeledSeekBarPreference extends SeekBarPreference {

    private final int mTextStartId;
    private final int mTextEndId;
    private final int mTickMarkId;
    private OnPreferenceChangeListener mStopListener;

    public LabeledSeekBarPreference(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {

        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_labeled_slider);

        final TypedArray styledAttrs =
                context.obtainStyledAttributes(attrs, R.styleable.LabeledSeekBarPreference);
        mTextStartId =
                styledAttrs.getResourceId(
                        R.styleable.LabeledSeekBarPreference_textStart,
                        R.string.summary_placeholder);
        mTextEndId =
                styledAttrs.getResourceId(
                        R.styleable.LabeledSeekBarPreference_textEnd, R.string.summary_placeholder);
        mTickMarkId =
                styledAttrs.getResourceId(
                        R.styleable.LabeledSeekBarPreference_tickMark, /* defValue= */ 0);
        styledAttrs.recycle();
    }

    public LabeledSeekBarPreference(Context context, AttributeSet attrs) {
        this(
                context,
                attrs,
                TypedArrayUtils.getAttr(
                        context,
                        androidx.preference.R.attr.seekBarPreferenceStyle,
                        com.android.internal.R.attr.seekBarPreferenceStyle),
                0);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final TextView startText = (TextView) holder.findViewById(android.R.id.text1);
        final TextView endText = (TextView) holder.findViewById(android.R.id.text2);
        startText.setText(mTextStartId);
        endText.setText(mTextEndId);

        if (mTickMarkId != 0) {
            final Drawable tickMark = getContext().getDrawable(mTickMarkId);
            final SeekBar seekBar =
                    (SeekBar) holder.findViewById(com.android.internal.R.id.seekbar);
            seekBar.setTickMark(tickMark);
        }
    }

    public void setOnPreferenceChangeStopListener(OnPreferenceChangeListener listener) {
        mStopListener = listener;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);

        if (mStopListener != null) {
            mStopListener.onPreferenceChange(this, seekBar.getProgress());
        }
    }
}
