package com.ixume.optimization

import com.ixume.optimization.math.Quaternion
import kotlin.math.acos
import kotlin.math.sqrt

data class TimestampedDisplayData(
    val t: Int,
    
    val scaleX: Double,
    val scaleY: Double,
    val scaleZ: Double,
    
    val rot: Quaternion,
) {
    constructor(t: Int, s: Double, rx: Double, ry: Double, rz: Double, rw: Double) : this(t, s, s, s, Quaternion(rx, ry, rz, rw))

    fun scaleDistance(scaleX: Double, scaleY: Double, scaleZ: Double): Double {
        return sqrt(
            Math.fma(
                this.scaleX - scaleX,
                this.scaleX - scaleX,
                Math.fma(
                    this.scaleY - scaleY,
                    this.scaleY - scaleY,
                    (this.scaleZ - scaleZ) * (this.scaleZ - scaleZ)
                )
            )
        )
    }
    
    fun rotDistance(other: Quaternion): Double {
        val dQ = rot * (-other)
        if (dQ.w < 0.0) dQ *= -1.0
        dQ.normalize()
        val angularDelta = 2.0 * acos(dQ.w.coerceIn(-1.0, 1.0))
        
        return angularDelta
    }
}