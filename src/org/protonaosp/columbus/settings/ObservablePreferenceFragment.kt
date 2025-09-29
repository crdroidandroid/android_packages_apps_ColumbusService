/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.annotation.CallSuper
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_PAUSE
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceScreen
import com.android.settingslib.core.lifecycle.Lifecycle
import com.android.settingslib.preference.PreferenceFragment

abstract class ObservablePreferenceFragment : PreferenceFragment(), LifecycleOwner {

    private val _lifecycle = Lifecycle(this)

    fun getSettingsLifecycle(): Lifecycle {
        return _lifecycle
    }

    @CallSuper
    override fun onAttach(context: Context) {
        super.onAttach(context)
        _lifecycle.onAttach(context)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        _lifecycle.onCreate(savedInstanceState)
        _lifecycle.handleLifecycleEvent(ON_CREATE)
        super.onCreate(savedInstanceState)
    }

    override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
        _lifecycle.setPreferenceScreen(preferenceScreen)
        super.setPreferenceScreen(preferenceScreen)
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _lifecycle.onSaveInstanceState(outState)
    }

    @CallSuper
    override fun onStart() {
        _lifecycle.handleLifecycleEvent(ON_START)
        super.onStart()
    }

    @CallSuper
    override fun onResume() {
        _lifecycle.handleLifecycleEvent(ON_RESUME)
        super.onResume()
    }

    @CallSuper
    override fun onPause() {
        _lifecycle.handleLifecycleEvent(ON_PAUSE)
        super.onPause()
    }

    @CallSuper
    override fun onStop() {
        _lifecycle.handleLifecycleEvent(ON_STOP)
        super.onStop()
    }

    @CallSuper
    override fun onDestroy() {
        _lifecycle.handleLifecycleEvent(ON_DESTROY)
        super.onDestroy()
    }

    @CallSuper
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        _lifecycle.onCreateOptionsMenu(menu, inflater)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @CallSuper
    override fun onPrepareOptionsMenu(menu: Menu) {
        _lifecycle.onPrepareOptionsMenu(menu)
        super.onPrepareOptionsMenu(menu)
    }

    @CallSuper
    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        val lifecycleHandled = _lifecycle.onOptionsItemSelected(menuItem)
        if (!lifecycleHandled) {
            return super.onOptionsItemSelected(menuItem)
        }
        return lifecycleHandled
    }
}
