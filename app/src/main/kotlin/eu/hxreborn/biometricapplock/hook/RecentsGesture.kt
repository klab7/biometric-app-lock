package eu.hxreborn.biometricapplock.hook

// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/app/ActivityOptions.java
// stable since API 28
private const val ANIM_NONE = 0
private const val ANIM_CUSTOM = 1
private const val ANIM_SCALE_UP = 2
private const val ANIM_THUMBNAIL_SCALE_UP = 3
private const val ANIM_THUMBNAIL_SCALE_DOWN = 4
private const val ANIM_SCENE_TRANSITION = 5
private const val ANIM_DEFAULT = 6
private const val ANIM_LAUNCH_TASK_BEHIND = 7
private const val ANIM_THUMBNAIL_ASPECT_SCALE_UP = 8
private const val ANIM_THUMBNAIL_ASPECT_SCALE_DOWN = 9
private const val ANIM_CUSTOM_IN_PLACE = 10
private const val ANIM_CLIP_REVEAL = 11
private const val ANIM_OPEN_CROSS_PROFILE_APPS = 12
private const val ANIM_REMOTE_ANIMATION = 13
private const val ANIM_FROM_STYLE = 14

private fun animTypeName(type: Int): String =
    when (type) {
        ANIM_NONE -> "none"
        ANIM_CUSTOM -> "custom"
        ANIM_SCALE_UP -> "scale_up"
        ANIM_THUMBNAIL_SCALE_UP -> "thumbnail_scale_up"
        ANIM_THUMBNAIL_SCALE_DOWN -> "thumbnail_scale_down"
        ANIM_SCENE_TRANSITION -> "scene_transition"
        ANIM_DEFAULT -> "default"
        ANIM_LAUNCH_TASK_BEHIND -> "launch_task_behind"
        ANIM_THUMBNAIL_ASPECT_SCALE_UP -> "thumbnail_aspect_scale_up"
        ANIM_THUMBNAIL_ASPECT_SCALE_DOWN -> "thumbnail_aspect_scale_down"
        ANIM_CUSTOM_IN_PLACE -> "custom_in_place"
        ANIM_CLIP_REVEAL -> "clip_reveal"
        ANIM_OPEN_CROSS_PROFILE_APPS -> "open_cross_profile_apps"
        ANIM_REMOTE_ANIMATION -> "remote_animation"
        ANIM_FROM_STYLE -> "from_style"
        else -> "type$type"
    }

// remote_animation is the nav-bar quick switch, custom and thumbnail types are a card tap.
// the raw int stays in the label since launchers may map gestures differently
internal fun recentsGesture(options: Any?): String =
    runCatching {
        if (options == null) return "gesture=none"
        val ao =
            sequenceOf("getOriginalOptions", "getOptions").firstNotNullOfOrNull { name ->
                runCatching { options.javaClass.getMethod(name).invoke(options) }.getOrNull()
            } ?: options
        val type = ao.javaClass.getMethod("getAnimationType").invoke(ao) as Int
        val kind =
            when (type) {
                ANIM_REMOTE_ANIMATION -> "quickswitch"

                ANIM_CUSTOM,
                ANIM_THUMBNAIL_SCALE_UP,
                ANIM_THUMBNAIL_SCALE_DOWN,
                ANIM_THUMBNAIL_ASPECT_SCALE_UP,
                ANIM_THUMBNAIL_ASPECT_SCALE_DOWN,
                -> "cardtap"

                else -> "other"
            }
        "gesture=$kind(${animTypeName(type)}/$type)"
    }.getOrDefault("gesture=?")
