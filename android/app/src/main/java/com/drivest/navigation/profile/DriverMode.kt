package com.drivest.navigation.profile

enum class DriverMode(val storageValue: String) {
    LEARNER("learner"),
    NEW_DRIVER("new_driver"),
    STANDARD("standard");

    companion object {
        fun fromStorage(value: String?): DriverMode {
            return entries.firstOrNull { it.storageValue == value } ?: LEARNER
        }
    }
}
