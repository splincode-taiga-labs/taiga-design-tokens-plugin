package org.taigaui.designtokens.cache

import java.lang.ref.WeakReference

internal class CoalescingRefreshCallbacks {
    private val callbacks = mutableListOf<RefreshCallback<*>>()

    val size: Int
        get() = callbacks.size

    fun add(callback: RefreshCallback<*>) {
        callbacks.removeAll { current ->
            !current.isActive || current.sameTargetAs(callback)
        }

        if (callback.isActive) {
            callbacks.add(callback)
        }
    }

    fun take(entriesChanged: Boolean = true): List<RefreshCallback<*>> {
        val result =
            callbacks.filter { callback ->
                callback.isActive &&
                    (entriesChanged || callback.notifyWhenUnchanged)
            }

        callbacks.clear()

        return result
    }
}

internal class RefreshCallback<T : Any>(
    owner: T,
    internal val kind: Any,
    internal val notifyWhenUnchanged: Boolean = false,
    private val isActivePredicate: (T) -> Boolean = { true },
    private val callback: (T) -> Unit,
) {
    private val owner = WeakReference(owner)

    internal val isActive: Boolean
        get() = owner.get()?.let(isActivePredicate) == true

    internal fun sameTargetAs(other: RefreshCallback<*>): Boolean {
        val currentOwner = owner.get()
        val otherOwner = other.owner.get()

        return currentOwner != null &&
            currentOwner === otherOwner &&
            kind == other.kind
    }

    fun invokeIfActive() {
        owner
            .get()
            ?.takeIf(isActivePredicate)
            ?.let(callback)
    }
}
