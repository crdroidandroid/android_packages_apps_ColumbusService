package org.protonaosp.columbus.gates

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.LinkedHashSet

abstract class Gate(var context: Context, handler: Handler, handlerType: Int) {
    private var active: Boolean = false
    private var isBlocked: Boolean = false
    private val listeners = LinkedHashSet<Listener>()
    private var notifyHandler: Handler =
        if (handlerType == 2) {
            Handler.createAsync(Looper.getMainLooper())
        } else {
            handler
        }

    interface Listener {
        fun onGateChanged(gate: Gate)
    }

    private fun maybeActivate() {
        if (active || listeners.isEmpty()) {
            return
        }
        active = true
        onActivate()
    }

    private fun maybeDeactivate() {
        if (!active || listeners.isNotEmpty()) {
            return
        }
        active = false
        onDeactivate()
    }

    private fun notifyListeners() {
        if (!active) return
        for (it in listeners) {
            notifyHandler.post { it.onGateChanged(this) }
        }
    }

    fun isBlocking(): Boolean {
        return active && isBlocked
    }

    abstract fun onActivate()

    abstract fun onDeactivate()

    fun registerListener(listener: Listener) {
        listeners.add(listener)
        maybeActivate()
    }

    fun setBlocking(isBlocked: Boolean) {
        if (this.isBlocked == isBlocked) return
        this.isBlocked = isBlocked
        notifyListeners()
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
        maybeDeactivate()
    }
}
