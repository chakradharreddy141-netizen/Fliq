package com.fliq.app

import android.content.Context
import android.content.SharedPreferences

object GesturePreferences {
    private const val PREF_NAME = "fliq_gesture_prefs"
    
    // Gestures
    const val KEY_VICTORY = "gesture_victory"
    const val KEY_POINTING_UP = "gesture_pointing_up"
    const val KEY_THUMB_UP = "gesture_thumb_up"
    const val KEY_THUMB_DOWN = "gesture_thumb_down"
    const val KEY_CLOSED_FIST = "gesture_closed_fist"
    const val KEY_OPEN_PALM = "gesture_open_palm"
    const val KEY_I_LOVE_YOU = "gesture_i_love_you"

    // Action constants (Apps)
    const val ACTION_OPEN_WHATSAPP = "open_whatsapp"
    const val ACTION_OPEN_INSTAGRAM = "open_instagram"
    const val ACTION_OPEN_YOUTUBE = "open_youtube"
    const val ACTION_OPEN_CAMERA = "open_camera"
    const val ACTION_OPEN_CHROME = "open_chrome"
    const val ACTION_OPEN_SPOTIFY = "open_spotify"
    const val ACTION_OPEN_MAPS = "open_maps"
    const val ACTION_OPEN_PHONE = "open_phone"
    const val ACTION_OPEN_MESSAGES = "open_messages"
    const val ACTION_OPEN_SETTINGS = "open_settings"
    
    // Action constants (Utilities & System)
    const val ACTION_TOGGLE_TORCH = "toggle_torch"
    const val ACTION_SCREENSHOT = "take_screenshot"
    const val ACTION_PLAY_PAUSE = "play_pause_media"
    const val ACTION_NEXT_TRACK = "next_track"
    const val ACTION_PREV_TRACK = "prev_track"
    const val ACTION_NOTIFICATIONS = "open_notifications"
    const val ACTION_QUICK_SETTINGS = "open_quick_settings"
    const val ACTION_RECENT_APPS = "open_recent_apps"
    const val ACTION_LOCK_SCREEN = "lock_screen"
    const val ACTION_GOOGLE_ASSISTANT = "open_google_assistant"
    const val ACTION_VOLUME_UP = "volume_up"
    const val ACTION_VOLUME_DOWN = "volume_down"
    
    // Action constants (Navigation & Fliq)
    const val ACTION_SCROLL_UP = "scroll_up"
    const val ACTION_SCROLL_DOWN = "scroll_down"
    const val ACTION_SCROLL_LEFT = "scroll_left"
    const val ACTION_SCROLL_RIGHT = "scroll_right"
    const val ACTION_BACK = "navigate_back"
    const val ACTION_HOME = "navigate_home"
    const val ACTION_PAUSE_RESUME = "pause_resume_fliq"

    val ALL_ACTIONS = listOf(
        // Apps
        ACTION_OPEN_WHATSAPP to "Open WhatsApp",
        ACTION_OPEN_INSTAGRAM to "Open Instagram",
        ACTION_OPEN_YOUTUBE to "Open YouTube",
        ACTION_OPEN_CAMERA to "Open Camera",
        ACTION_OPEN_CHROME to "Open Chrome",
        ACTION_OPEN_SPOTIFY to "Open Spotify",
        ACTION_OPEN_MAPS to "Open Google Maps",
        ACTION_OPEN_PHONE to "Open Phone Dialer",
        ACTION_OPEN_MESSAGES to "Open Messages",
        ACTION_OPEN_SETTINGS to "Open Settings",
        
        // Fliq & Navigation
        ACTION_SCROLL_UP to "Scroll Up \u2B06\uFE0F",
        ACTION_SCROLL_DOWN to "Scroll Down \u2B07\uFE0F",
        ACTION_SCROLL_LEFT to "Scroll Left \u2B05\uFE0F",
        ACTION_SCROLL_RIGHT to "Scroll Right \u27A1\uFE0F",
        ACTION_BACK to "Go Back \u2B05\uFE0F",
        ACTION_HOME to "Go Home \uD83C\uDFE0",
        ACTION_PAUSE_RESUME to "Pause/Resume Fliq \u23F8\uFE0F",
        
        // Utilities & System
        ACTION_TOGGLE_TORCH to "Toggle Flashlight \uD83D\uDD26",
        ACTION_SCREENSHOT to "Take Screenshot \uD83D\uDCF8",
        ACTION_PLAY_PAUSE to "Play/Pause Media \u23EF\uFE0F",
        ACTION_NEXT_TRACK to "Next Track \u23ED\uFE0F",
        ACTION_PREV_TRACK to "Previous Track \u23EE\uFE0F",
        ACTION_NOTIFICATIONS to "Open Notifications",
        ACTION_QUICK_SETTINGS to "Open Quick Settings",
        ACTION_RECENT_APPS to "Open Recent Apps",
        ACTION_LOCK_SCREEN to "Lock Screen \uD83D\uDD12",
        ACTION_GOOGLE_ASSISTANT to "Open Google Assistant",
        ACTION_VOLUME_UP to "Volume Up \uD83D\uDD0A",
        ACTION_VOLUME_DOWN to "Volume Down \uD83D\uDD09"
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getActionForGesture(context: Context, gestureKey: String, defaultAction: String): String =
        getPrefs(context).getString(gestureKey, defaultAction) ?: defaultAction

    fun setActionForGesture(context: Context, gestureKey: String, action: String) =
        getPrefs(context).edit().putString(gestureKey, action).apply()

    fun getActionLabel(actionKey: String): String =
        ALL_ACTIONS.find { it.first == actionKey }?.second ?: actionKey
}
