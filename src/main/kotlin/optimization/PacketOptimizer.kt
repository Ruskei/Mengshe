package com.ixume.optimization

import kotlin.math.min

/*
Context
- Teleportation updates can be sent periodically with no need for changing teleportation duration
- Teleportation duration change requires entity metadata update
- Entity metadata updates have to be sent regularly anyway so changing interpolation duration is not as big of a deal

Theory
- In order to allow maximum flexibility, have the optimizer take in data iteratively, not requiring the entire life to be known at start time
- Do this by feeding it timestamped data, and optimizer will return packets when it deems fit
- Max tolerance for every data type is provided
- Match up metadata updates with tp duration updates

an entity metadata update of any kind requires sending new data
enforce rules with cost function

entity metadata and location independently can have regions
    location region boundaries must align with entity metadata updates
    entity metadata region boundaries don't have special requirements

A region R with tp duration T has data that can be well approximated by a discrete point every T ticks

given metadata regions and loc regions... cost is calculated as the following:
// simplify bounds cost to update cost, so that gets merged into total_updates

metadata_regions.total_updates * UPDATE_COST +
loc_regions.total_tps * TP_COST +
loc_regions.bounds * (UPDATE_COST + TD_UPDATE_COST)

output:
    metadata region bounds
    loc region bounds
 */

/**
 * @param posData Timestamped but still must not have any holes
 */
fun optimize(
    ptol: Double,
    stol: Double,
    posData: List<TimestampedPos>,
    displayData: List<TimestampedDisplayData>,
): OptimizedPath {
    val minPosCosts = DoubleArray(posData.size) {
        if (it == 0) 0.0
        else Double.MAX_VALUE
    }

    val posPath = IntArray(posData.size) { -1 }

    var i = 0
    while (i < posData.size) {
        var j = 0
        while (j < i) {
            val currentCost = minPosCosts[j] + posData.regionCost(ptol, j, i)

            if (currentCost < minPosCosts[i]) {
                minPosCosts[i] = currentCost
                posPath[i] = j
            }

            j++
        }

        i++
    }

    val posIndices = mutableListOf<Int>()
    posIndices += posPath.size - 1

    var k = posPath[posPath.size - 1]
    do {
        posIndices.addFirst(k)
        k = posPath[k]
    } while (k != -1)

    val minDisplayCosts = DoubleArray(displayData.size) {
        if (it == 0) 0.0
        else Double.MAX_VALUE
    }

    val displayPath = IntArray(displayData.size) { -1 }

    var l = 0
    while (l < displayData.size) {
        var m = 0
        while (m < l) {
            val currentCost = minDisplayCosts[m] + displayData.regionCost(stol, posIndices, m, l)

            if (currentCost < minDisplayCosts[l]) {
                minDisplayCosts[l] = currentCost
                displayPath[l] = m
            }

            m++
        }

        l++
    }

    val displayIndices = mutableListOf<Int>()
    if (displayData.isNotEmpty()) {
        displayIndices += displayPath.size - 1

        var n = displayPath[displayPath.size - 1]
        do {
            displayIndices.addFirst(n)
            n = displayPath[n]
        } while (n != -1)
    }

    return OptimizedPath(posIndices.actualizePositions(ptol, posData), displayIndices.actualizeDisplayData(stol, displayData))
}

fun List<Int>.actualizePositions(tolerance: Double, positions: List<TimestampedPos>): List<Int> {
    val out = mutableListOf<Int>()
    var i = 0
    while (i < size - 1) {
        val start = this[i]
        val end = this[i + 1]

        val duration = end - start
        var t = duration

        var minCost = duration * TP_COST
        var bestPeriod = 1

        while (t > 1) {
            if (duration % t != 0) {
                t--
                continue
            }

            var valid = true
            // test if this period fits the data within tolerance
            run check@{
                var j = start
                while (j < end) {
                    // test all points (j + 1)..<(j + t)
                    val s = positions[j]
                    val e = positions[j + t]

                    var k = j + 1
                    while (k < j + t) {
                        val f = (k - j) / t.toDouble()
                        val p = positions[k]
                        val d = p.distance(
                            (e.x - s.x) * f + s.x,
                            (e.y - s.y) * f + s.y,
                            (e.z - s.z) * f + s.z,
                        )

                        if (d > tolerance) {
                            valid = false
                            return@check
                        }

                        k++
                    }

                    j += t
                }
            }

            if (valid) {
                val c = duration / t * TP_COST
                if (c < minCost) {
                    minCost = c
                    bestPeriod = t
                }
            }

            t--
        }

        var q = start
        while (q < end) {
            out += q

            q += bestPeriod
        }

        i++
    }

    out += last()

    return out
}

fun List<Int>.actualizeDisplayData(tolerance: Double, displayData: List<TimestampedDisplayData>): List<Int> {
    if (isEmpty()) return emptyList()
    
    val out = mutableListOf<Int>()
    var i = 0
    while (i < size - 1) {
        val start = this[i]
        val end = this[i + 1]

        val duration = end - start
        var t = duration

        var minCost = duration * UPDATE_COST
        var bestPeriod = 1

        while (t > 1) {
            if (duration % t != 0) {
                t--
                continue
            }

            var valid = true
            // test if this period fits the data within tolerance
            run check@{
                var j = start
                while (j < end) {
                    // test all points (j + 1)..<(j + t)
                    val s = displayData[j]
                    val e = displayData[j + t]

                    var k = j + 1
                    while (k < j + t) {
                        val f = (k - j) / t.toDouble()
                        val p = displayData[k]
                        val d = p.distance((e.scale - s.scale) * f + s.scale)

                        if (d > tolerance) {
                            valid = false
                            return@check
                        }

                        k++
                    }

                    j += t
                }
            }

            if (valid) {
                val c = duration / t * TP_COST
                if (c < minCost) {
                    minCost = c
                    bestPeriod = t
                }
            }

            t--
        }

        var q = start
        while (q < end) {
            out += q

            q += bestPeriod
        }

        i++
    }

    out += last()

    return out
}

/**
 * @param start inclusive
 * @param end exclusive, but must be in array
 */
fun List<TimestampedPos>.regionCost(
    tolerance: Double,
    start: Int, end: Int,
): Double {
    require(end > start)
    require(start >= 0)
    require(end <= size)

    val duration = end - start
    var t = duration

    var minCost = duration * TP_COST

    while (t > 1) {
        if (duration % t != 0) {
            t--
            continue
        }

        var valid = true
        // test if this period fits the data within tolerance
        run check@{
            var j = start
            while (j < end) {
                // test all points (j + 1)..<(j + t)
                val s = this[j]
                val e = this[j + t]

                var k = j + 1
                while (k < j + t) {
                    val f = (k - j) / t.toDouble()
                    val p = this[k]
                    val d = p.distance(
                        (e.x - s.x) * f + s.x,
                        (e.y - s.y) * f + s.y,
                        (e.z - s.z) * f + s.z,
                    )

                    if (d > tolerance) {
                        valid = false
                        return@check
                    }

                    k++
                }

                j += t
            }
        }

        if (valid) {
            val c = duration / t * TP_COST
            minCost = min(minCost, c)
        }

        t--
    }

    return minCost + TD_UPDATE_COST
}

fun List<TimestampedDisplayData>.regionCost(
    tolerance: Double,
    posIndices: List<Int>,
    start: Int, end: Int,
): Double {
    require(end > start)
    require(start >= 0)
    require(end <= size)

    val relevant = posIndices.filter { it in start..<end }

    val duration = end - start
    var t = duration

    var minCost = duration * UPDATE_COST

    while (t > 1) {
        if (duration % t != 0) {
            t--
            continue
        }

        // must also make sure that all pos data that are within this range are covered by this period
        if (relevant.any { (it - start) % t != 0 }) {
            t--
            continue
        }

        var valid = true
        // test if this period fits the data within tolerance
        run check@{
            var j = start
            while (j < end) {
                // test all points (j + 1)..<(j + t)
                val s = this[j]
                val e = this[j + t]

                var k = j + 1
                while (k < j + t) {
                    val f = (k - j) / t.toDouble()
                    val p = this[k]
                    val d = p.distance((e.scale - s.scale) * f + s.scale)

                    if (d > tolerance) {
                        valid = false
                        return@check
                    }

                    k++
                }

                j += t
            }
        }

        if (valid) {
            val c = duration / t * UPDATE_COST
            minCost = min(minCost, c)
        }

        t--
    }

    return minCost
}


private const val TP_COST = 1.0
private const val UPDATE_COST = 2.0
private const val TD_UPDATE_COST = 3.0
