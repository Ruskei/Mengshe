package com.ixume

import com.ixume.optimization.Costs
import com.ixume.optimization.OptimizedPath
import com.ixume.optimization.TimestampedDisplayData
import com.ixume.optimization.TimestampedPos
import com.ixume.optimization.optimize
import kotlin.system.measureNanoTime
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main() {
    val positions = listOf(
        TimestampedPos(0, 0.0, 0.0, 0.0),
        TimestampedPos(0, 0.0, 1.0, 0.0),
        TimestampedPos(0, 0.0, 2.0, 0.0),
        TimestampedPos(0, 0.0, 3.0, 0.0),
        TimestampedPos(0, 0.0, 4.0, 0.0),
    )

    val display = listOf(
        TimestampedDisplayData(0, 0.0, 1.0, 0.0, 0.0, 0.0),
        TimestampedDisplayData(0, 0.0, 1.0, 0.0, 0.0, 0.0),
        TimestampedDisplayData(0, 0.0, 1.0, 0.0, 0.0, 0.0),
        TimestampedDisplayData(0, 0.0, 1.0, 0.0, 0.0, 0.0),
        TimestampedDisplayData(0, 0.0, 1.0, 0.0, 0.0, 0.0),
    )

    val ptol = 0.1
    val stol = 0.1

    val path: OptimizedPath
    val t = measureNanoTime {
        path = optimize(ptol, stol, 2.0, 1.0, positions, display, emptyList(), Costs.DEFAULT, true)
    }

    println("positions: ${path.positions}")
    println("display: ${path.displayData}")

    println("took: ${t.toDuration(DurationUnit.NANOSECONDS)}")
}

/*
positions: [0, 7, 14, 21, 31, 41, 51]
display: [0, 6, 12, 17, 21, 22, 27, 32, 37, 41, 45, 51]
text: [0, 6, 12, 17, 22, 27, 32, 37, 41, 45, 51]
 */