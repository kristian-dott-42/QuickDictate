package com.quickdictate.android

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The piece that lets the floating button type into *other* apps.
 *
 * The overlay button can't insert text on its own — it isn't a keyboard. This
 * accessibility service finds the field that currently has input focus and pastes
 * the cleaned transcript at the cursor (the Android analogue of the desktop app's
 * synthesized ⌘V).
 */
class DictationAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // We don't react to events; we only act on demand from the overlay.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Insert [text] into whatever editable field currently has focus. */
    fun insertText(text: String) {
        main.post {
            val node = findFocusedEditable()
            if (node == null) {
                Log.w(TAG, "no focused editable field to insert into")
                return@post
            }
            // Prefer pasting at the cursor (preserves existing text + selection).
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("dictation", text))
            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (!pasted) {
                // Fallback: append to the field's existing contents.
                val existing = node.text?.toString() ?: ""
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        existing + text,
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        return firstEditable(root)
    }

    /** Breadth-ish search for any editable node as a fallback. */
    private fun firstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val hit = firstEditable(node.getChild(i))
            if (hit != null) return hit
        }
        return null
    }

    companion object {
        private const val TAG = "QuickDictate"

        /** Set while the service is connected, so the overlay can reach it. */
        @Volatile
        var instance: DictationAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
