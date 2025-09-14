package com.ixume.optimization

import kotlin.math.sqrt

data class TimestampedDisplayData(
    val t: Int,
    val scaleX: Double,
    val scaleY: Double,
    val scaleZ: Double,
) {
    constructor(t: Int, s: Double) : this(t, s, s, s)
   
    fun distance(scaleX: Double, scaleY: Double, scaleZ: Double): Double {
        return sqrt(
            Math.fma(
                this.scaleX - scaleX,
                this.scaleX - scaleX,
                Math.fma(this.scaleY - scaleY, this.scaleY - scaleY, (this.scaleZ - scaleZ) * (this.scaleZ - scaleZ))
            )
        )
    }
}