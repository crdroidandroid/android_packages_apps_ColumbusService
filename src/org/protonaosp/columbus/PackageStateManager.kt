/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

object PackageStateManager {

    interface PackageStateListener {
        fun onPackageAdded(packageName: String, uid: Int) {}

        fun onPackageRemoved(packageName: String, uid: Int) {}

        fun onPackageChanged(packageName: String, uid: Int) {}
    }

    private val listeners: HashSet<PackageStateListener> = HashSet()
    private val listenersLock = Any()
    private var isReceiverRegistered = false
    private lateinit var receiver: BroadcastReceiver
    private lateinit var applicationContext: Context

    fun onCreate(context: Context) {
        applicationContext = context.applicationContext
        setupReceiver()
        registerReceiver()
    }

    fun onDestroy() {
        unregisterReceiver()
    }

    private fun setupReceiver() {
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val packageName = intent?.data?.schemeSpecificPart ?: return
                    val uid = intent?.getIntExtra(Intent.EXTRA_UID, 0) ?: return

                    val listenersCopy: HashSet<PackageStateListener>
                    synchronized(listenersLock) { listenersCopy = HashSet(listeners) }

                    listenersCopy.forEach { listener ->
                        when (intent.action) {
                            Intent.ACTION_PACKAGE_ADDED -> listener.onPackageAdded(packageName, uid)
                            Intent.ACTION_PACKAGE_REMOVED ->
                                listener.onPackageRemoved(packageName, uid)
                            Intent.ACTION_PACKAGE_CHANGED ->
                                listener.onPackageChanged(packageName, uid)
                        }
                    }
                }
            }
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val intentFilter =
                IntentFilter().apply {
                    addDataScheme("package")
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                }
            applicationContext.registerReceiver(receiver, intentFilter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            applicationContext.unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    fun registerListener(listener: PackageStateListener) {
        synchronized(listenersLock) { listeners.add(listener) }
    }

    fun unregisterListener(listener: PackageStateListener) {
        synchronized(listenersLock) { listeners.remove(listener) }
    }
}
