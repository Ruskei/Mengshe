package com.ixume.optimization

import kotlin.math.abs

data class TimestampedDisplayData(
    val t: Int,
    val scale: Double,
) {
    fun distance(scale: Double): Double {
        return abs(this.scale - scale)
    }
}