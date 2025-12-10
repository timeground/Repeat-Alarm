package com.timeground.repeatreminder.data

data class Alarm(
    val id: Long = -1,
    var hour: Int = 0,
    var minute: Int = 0,
    var isEnabled: Boolean = true,
    var days: String = "0,1,2,3,4,5,6", // 0=Sun, 1=Mon, etc. Default all days implies one-off logic or daily? Standard Android alarm usually daily if recurring.
    var label: String = ""
) {
    fun getFormattedTime(): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        return String.format("%02d:%02d %s", h, minute, amPm)
    }
}
