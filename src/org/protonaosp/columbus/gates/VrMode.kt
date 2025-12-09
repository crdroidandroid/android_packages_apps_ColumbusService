/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.Context
import android.os.Handler
import android.os.RemoteException
import android.os.ServiceManager
import android.service.vr.IVrManager
import android.service.vr.IVrStateCallbacks
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog

class VrMode(context: Context, handler: Handler) : Gate(context, handler, 2) {
    private var inVrMode: Boolean = false
    private var vrManager: IVrManager? = null

    private val vrStateCallbacks =
        object : IVrStateCallbacks.Stub() {
            override fun onVrStateChanged(enabled: Boolean) {
                inVrMode = enabled
                updateBlocking()
            }
        }

    private fun initializeVrManager() {
        val service = ServiceManager.getService(Context.VR_SERVICE)
        vrManager = IVrManager.Stub.asInterface(service)
    }

    init {
        initializeVrManager()
    }

    fun updateBlocking() {
        setBlocking(inVrMode)
    }

    override fun onActivate() {
        try {
            vrManager?.also {
                inVrMode = it.getVrModeState()
                it.registerListener(vrStateCallbacks)
            }
        } catch (e: RemoteException) {
            dlog(TAG, "Error registering IVrManager listener: ${e.message} ${e}")
            inVrMode = false
        }
        updateBlocking()
    }

    override fun onDeactivate() {
        try {
            vrManager?.unregisterListener(vrStateCallbacks)
        } catch (e: RemoteException) {
            dlog(TAG, "Error unregistering IVrManager listener: ${e.message} ${e}")
        }
        inVrMode = false
        setBlocking(false)
    }
}
