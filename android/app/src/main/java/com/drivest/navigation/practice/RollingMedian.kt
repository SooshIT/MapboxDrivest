package com.drivest.navigation.practice

import java.util.ArrayDeque

class RollingMedian(
    private val windowSize: Int
) {
    private val values = ArrayDeque<Double>()

    init {
        require(windowSize > 0) { "windowSize must be > 0" }
    }

    fun add(value: Double): Double {
        if (!value.isFinite()) {
            return median() ?: Double.NaN
        }
        values.addLast(value)
        while (values.size > windowSize) {
            values.removeFirst()
        }
        return median() ?: value
    }

    fun reset() {
        values.clear()
    }

    fun median(): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}
