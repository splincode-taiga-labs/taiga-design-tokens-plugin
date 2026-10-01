package org.taigaui.designtokens.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class CoalescingRefreshCallbacksTest {
    @Test
    fun `replaces duplicate callback for the same owner and kind`() {
        val owner = Owner()
        val invocations = mutableListOf<String>()
        val callbacks = CoalescingRefreshCallbacks()

        callbacks.add(refresh(owner, kind = "completion") { invocations += "first" })
        callbacks.add(refresh(owner, kind = "completion") { invocations += "latest" })

        assertEquals(1, callbacks.size)

        callbacks.take().forEach(RefreshCallback<*>::invokeIfActive)

        assertEquals(listOf("latest"), invocations)
    }

    @Test
    fun `keeps callbacks for distinct owners and kinds`() {
        val firstOwner = Owner()
        val secondOwner = Owner()
        val invocations = mutableListOf<String>()
        val callbacks = CoalescingRefreshCallbacks()

        callbacks.add(refresh(firstOwner, kind = "completion") { invocations += "first-completion" })
        callbacks.add(refresh(firstOwner, kind = "inspection") { invocations += "first-inspection" })
        callbacks.add(refresh(secondOwner, kind = "completion") { invocations += "second-completion" })

        assertEquals(3, callbacks.size)

        callbacks.take().forEach(RefreshCallback<*>::invokeIfActive)

        assertEquals(
            listOf("first-completion", "first-inspection", "second-completion"),
            invocations,
        )
    }

    @Test
    fun `drops inactive owners before publication`() {
        val owner = Owner()
        var invocations = 0
        val callbacks = CoalescingRefreshCallbacks()

        callbacks.add(refresh(owner, kind = "completion") { invocations++ })
        owner.active = false

        assertEquals(emptyList<RefreshCallback<*>>(), callbacks.take())
        assertEquals(0, invocations)
    }

    @Test
    fun `keeps notify when unchanged callbacks only when requested`() {
        val regularOwner = Owner()
        val inspectionOwner = Owner()
        val invocations = mutableListOf<String>()
        val callbacks = CoalescingRefreshCallbacks()

        callbacks.add(refresh(regularOwner, kind = "completion") { invocations += "completion" })
        callbacks.add(
            refresh(
                inspectionOwner,
                kind = "inspection",
                notifyWhenUnchanged = true,
            ) { invocations += "inspection" },
        )

        callbacks
            .take(entriesChanged = false)
            .forEach(RefreshCallback<*>::invokeIfActive)

        assertEquals(listOf("inspection"), invocations)
    }

    private fun refresh(
        owner: Owner,
        kind: String,
        notifyWhenUnchanged: Boolean = false,
        callback: () -> Unit,
    ): RefreshCallback<Owner> =
        RefreshCallback(
            owner = owner,
            kind = kind,
            notifyWhenUnchanged = notifyWhenUnchanged,
            isActivePredicate = Owner::active,
        ) {
            callback()
        }

    private data class Owner(
        var active: Boolean = true,
    )
}
