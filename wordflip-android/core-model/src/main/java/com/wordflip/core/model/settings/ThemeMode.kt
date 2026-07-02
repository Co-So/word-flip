package com.wordflip.core.model.settings

/** 外观模式，对齐 openapi `themeMode` 与 REQ-SETTINGS-7 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

fun parseThemeMode(value: String?): ThemeMode {
    return when (value?.lowercase()) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }
}

fun ThemeMode.storageValue(): String = when (this) {
    ThemeMode.SYSTEM -> "system"
    ThemeMode.LIGHT -> "light"
    ThemeMode.DARK -> "dark"
}
