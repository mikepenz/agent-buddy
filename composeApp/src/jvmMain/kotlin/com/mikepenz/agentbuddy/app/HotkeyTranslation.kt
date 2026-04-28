package com.mikepenz.agentbuddy.app

import com.mikepenz.agentbuddy.model.HotkeyModifier
import java.awt.event.KeyEvent

/**
 * Translates AWT virtual-key codes + portable [HotkeyModifier]s into the
 * platform-native values that Nucleus' global-hotkey native bridges hand
 * straight to the OS APIs.
 *
 * Nucleus does **not** translate inputs — its macOS `.dylib` passes them
 * directly to Carbon's `RegisterEventHotKey`, which requires macOS virtual
 * key codes (kVK_*) and Carbon modifier flags. Linux similarly uses X11
 * keycodes/keysyms, while Windows expects WM_HOTKEY VKs and MOD_*. We do
 * the translation here so the rest of the app can store AWT codes (which
 * Compose's KeyEvent produces directly).
 */
internal object HotkeyTranslation {

    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isMac = osName.contains("mac")
    private val isWindows = osName.contains("win")

    fun nativeModifiers(modifiers: Set<HotkeyModifier>): Int = modifiers.fold(0) { acc, m ->
        acc or when {
            isMac -> macModifierFlag(m)
            isWindows -> windowsModifierFlag(m)
            else -> linuxModifierFlag(m)
        }
    }

    fun nativeKeyCode(awtKeyCode: Int): Int = when {
        isMac -> macKeyCode(awtKeyCode)
        isWindows -> awtKeyCode  // Windows MapVirtualKey codes match AWT VK_* for most keys.
        else -> awtKeyCode  // Linux X11 keycodes also align with AWT for letters/digits in practice.
    }

    // ── Modifier mask tables ─────────────────────────────────────────────

    // Carbon (`MacTypes.h` / `Events.h`) modifier flags.
    private fun macModifierFlag(m: HotkeyModifier): Int = when (m) {
        HotkeyModifier.META -> 256        // cmdKey
        HotkeyModifier.SHIFT -> 512       // shiftKey
        HotkeyModifier.ALT -> 2048        // optionKey
        HotkeyModifier.CONTROL -> 4096    // controlKey
    }

    // Windows MOD_* values (matches Nucleus' internal `nativeFlag` defaults).
    private fun windowsModifierFlag(m: HotkeyModifier): Int = when (m) {
        HotkeyModifier.ALT -> 1
        HotkeyModifier.CONTROL -> 2
        HotkeyModifier.SHIFT -> 4
        HotkeyModifier.META -> 8
    }

    // X11 / Linux: same encoding as Windows is what the Nucleus internal
    // table assumes; the .so layer translates to XGrabKey masks.
    private fun linuxModifierFlag(m: HotkeyModifier): Int = windowsModifierFlag(m)

    // ── macOS keycode table ──────────────────────────────────────────────
    //
    // AWT java.awt.event.KeyEvent VK_* → kVK_* (HIToolbox/Events.h).
    // Covers letters, digits, common punctuation, F1-F15, arrows, and a
    // selection of editing keys. Anything not in the map falls back to the
    // AWT code (which usually means `RegisterEventHotKey` will reject it
    // and we'll log the failure — better than silently registering the
    // wrong key).

    private val macKeyMap: Map<Int, Int> = buildMap {
        // Letters
        put(KeyEvent.VK_A, 0x00); put(KeyEvent.VK_S, 0x01); put(KeyEvent.VK_D, 0x02)
        put(KeyEvent.VK_F, 0x03); put(KeyEvent.VK_H, 0x04); put(KeyEvent.VK_G, 0x05)
        put(KeyEvent.VK_Z, 0x06); put(KeyEvent.VK_X, 0x07); put(KeyEvent.VK_C, 0x08)
        put(KeyEvent.VK_V, 0x09); put(KeyEvent.VK_B, 0x0B); put(KeyEvent.VK_Q, 0x0C)
        put(KeyEvent.VK_W, 0x0D); put(KeyEvent.VK_E, 0x0E); put(KeyEvent.VK_R, 0x0F)
        put(KeyEvent.VK_Y, 0x10); put(KeyEvent.VK_T, 0x11); put(KeyEvent.VK_O, 0x1F)
        put(KeyEvent.VK_U, 0x20); put(KeyEvent.VK_I, 0x22); put(KeyEvent.VK_P, 0x23)
        put(KeyEvent.VK_L, 0x25); put(KeyEvent.VK_J, 0x26); put(KeyEvent.VK_K, 0x28)
        put(KeyEvent.VK_N, 0x2D); put(KeyEvent.VK_M, 0x2E)
        // Digits (top row)
        put(KeyEvent.VK_1, 0x12); put(KeyEvent.VK_2, 0x13); put(KeyEvent.VK_3, 0x14)
        put(KeyEvent.VK_4, 0x15); put(KeyEvent.VK_6, 0x16); put(KeyEvent.VK_5, 0x17)
        put(KeyEvent.VK_9, 0x19); put(KeyEvent.VK_7, 0x1A); put(KeyEvent.VK_8, 0x1C)
        put(KeyEvent.VK_0, 0x1D)
        // Punctuation
        put(KeyEvent.VK_EQUALS, 0x18); put(KeyEvent.VK_MINUS, 0x1B)
        put(KeyEvent.VK_CLOSE_BRACKET, 0x1E); put(KeyEvent.VK_OPEN_BRACKET, 0x21)
        put(KeyEvent.VK_QUOTE, 0x27); put(KeyEvent.VK_SEMICOLON, 0x29)
        put(KeyEvent.VK_BACK_SLASH, 0x2A); put(KeyEvent.VK_COMMA, 0x2B)
        put(KeyEvent.VK_SLASH, 0x2C); put(KeyEvent.VK_PERIOD, 0x2F)
        put(KeyEvent.VK_BACK_QUOTE, 0x32)
        // Whitespace + editing
        put(KeyEvent.VK_SPACE, 0x31); put(KeyEvent.VK_ENTER, 0x24)
        put(KeyEvent.VK_TAB, 0x30); put(KeyEvent.VK_BACK_SPACE, 0x33)
        put(KeyEvent.VK_ESCAPE, 0x35); put(KeyEvent.VK_DELETE, 0x75)
        put(KeyEvent.VK_HOME, 0x73); put(KeyEvent.VK_END, 0x77)
        put(KeyEvent.VK_PAGE_UP, 0x74); put(KeyEvent.VK_PAGE_DOWN, 0x79)
        put(KeyEvent.VK_LEFT, 0x7B); put(KeyEvent.VK_RIGHT, 0x7C)
        put(KeyEvent.VK_DOWN, 0x7D); put(KeyEvent.VK_UP, 0x7E)
        // F-keys (kVK_F1..kVK_F12 are not contiguous)
        put(KeyEvent.VK_F1, 0x7A); put(KeyEvent.VK_F2, 0x78); put(KeyEvent.VK_F3, 0x63)
        put(KeyEvent.VK_F4, 0x76); put(KeyEvent.VK_F5, 0x60); put(KeyEvent.VK_F6, 0x61)
        put(KeyEvent.VK_F7, 0x62); put(KeyEvent.VK_F8, 0x64); put(KeyEvent.VK_F9, 0x65)
        put(KeyEvent.VK_F10, 0x6D); put(KeyEvent.VK_F11, 0x67); put(KeyEvent.VK_F12, 0x6F)
    }

    private fun macKeyCode(awtKeyCode: Int): Int = macKeyMap[awtKeyCode] ?: awtKeyCode
}
