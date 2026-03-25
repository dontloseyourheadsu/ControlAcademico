package com.udlap.controlacademico.viewmodel

/**
 * One-time consumable event used for navigation and transient messages.
 */
class Event<out T>(private val content: T) {
    private var handled: Boolean = false

    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
