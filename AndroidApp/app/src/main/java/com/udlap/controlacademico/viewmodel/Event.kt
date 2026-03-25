package com.udlap.controlacademico.viewmodel

/**
 * One-time consumable wrapper used by LiveData for transient UI effects.
 *
 * In MVVM, plain LiveData can re-emit the last value after configuration changes.
 * This wrapper prevents duplicate handling for events such as toasts/navigation.
 *
 * @property content Payload to emit once.
 * @property handled Flag that tracks whether the UI has already consumed [content].
 */
class Event<out T>(private val content: T) {
    private var handled: Boolean = false

    /**
     * Returns content only on first consumption; returns null afterward.
     */
    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
