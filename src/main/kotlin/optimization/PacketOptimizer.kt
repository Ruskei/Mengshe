package com.ixume.optimization

import com.ixume.optimization.math.Quaternion
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

the text itself can also be changed in 2 ways for particle purposes...
    color: can be optimized
    content: cannot be optimized

for content updates, just detect when it changes and insert that as a mandatory display data update point

output:
    metadata region bounds
    loc region bounds
 */

/**
 * @param posData Timestamped but still must not have any holes
 */
fun optimize(
    positionTolerance: Double,
    scaleTolerance: Double,
    rotTolerance: Double,
    colorTolerance: Double,
    posData: List<TimestampedPos>,
    displayData: List<TimestampedDisplayData>,
    textData: List<TimestampedTextData>,
    costs: Costs,
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
            val currentCost = minPosCosts[j] + posData.regionCost(positionTolerance, j, i, costs)

            if (currentCost < minPosCosts[i]) {
                minPosCosts[i] = currentCost
                posPath[i] = j
            }

            j++
        }

        i++
    }

    val posRegionIndices = mutableListOf<Int>()
    posRegionIndices += posPath.size - 1

    var k = posPath[posPath.size - 1]
    do {
        posRegionIndices.addFirst(k)
        k = posPath[k]
    } while (k != -1)

    println("posRegionIndices: $posRegionIndices")
    
    val actualizedPositions = posRegionIndices.actualizePositions(positionTolerance, posData, costs)
    
    val updateAreas = constructPosTPUpdateAreas(
        regions = posRegionIndices,
        actual = actualizedPositions,
    )

    val optimizedTextData = textData.deltas(colorTolerance)

    val anchors = optimizedTextData

    println("anchors: $anchors")

    val minDisplayCosts = DoubleArray(displayData.size) {
        if (it == 0) 0.0
        else Double.MAX_VALUE
    }

    val displayPath = IntArray(displayData.size) { -1 }

    var l = 0
    while (l < displayData.size) {
        var m = 0
        while (m < l) {
            val currentCost = minDisplayCosts[m] + displayData.regionCost(scaleTolerance, rotTolerance, anchors, updateAreas, m, l, costs)

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

    println("displayIndices: $displayIndices")

    return OptimizedPath(
        actualizedPositions,
        displayIndices.actualizeDisplayData(scaleTolerance, rotTolerance, displayData, anchors, updateAreas, costs),
        optimizedTextData,
    )
}

fun List<TimestampedTextData>.deltas(ctol: Double): List<Int> {
    if (this.isEmpty()) return emptyList()
    if (this.size == 1) return listOf(0)

    val out = mutableListOf(0)

    var currContent = first().content
    var currColor = first().color
    var i = 1
    while (i < size) {
        if (this[i].content != currContent || currColor.scaleDistance(this[i].color) > ctol) {
            out += i
            currContent = this[i].content
            currColor = this[i].color
        }

        i++
    }

    out.add(size - 1)

    return out
}

fun List<Int>.actualizePositions(
    tolerance: Double, 
    positions: List<TimestampedPos>,
    costs: Costs,
): List<Int> {
    val out = mutableListOf<Int>()
    var i = 0
    while (i < size - 1) {
        val start = this[i]
        val end = this[i + 1]

        val duration = end - start
        var t = duration

        var minCost = duration * costs.tpCost
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
                val c = duration / t * costs.tpCost
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

fun List<Int>.actualizeDisplayData(
    scaleTolerance: Double,
    rotTolerance: Double,
    displayData: List<TimestampedDisplayData>,
    anchors: List<Int>,
    tpUpdateAreas: List<Pair<Int, Int>>,
    costs: Costs,
): List<Int> {
    if (isEmpty()) return emptyList()

    val out = mutableListOf<Int>()
    var i = 0
    while (i < size - 1) {
        val start = this[i]
        val end = this[i + 1]
        
        val bestPeriod = bestPeriod(
            displayData = displayData,
            scaleTolerance = scaleTolerance,
            rotTolerance = rotTolerance,
            anchors = anchors,
            tpUpdateAreas = tpUpdateAreas,
            start = start, end = end,
            costs = costs,
        )

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
    costs: Costs,
): Double {
    require(end > start)
    require(start >= 0)
    require(end <= size)

    val duration = end - start
    var t = duration

    var minCost = duration * costs.tpCost

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
            val c = duration / t * costs.tpCost
            minCost = min(minCost, c)
        }

        t--
    }

    return minCost + costs.updateCost.toDouble()
}

/**
 * Constructs the areas where teleportation duration can be updated
 */
fun constructPosTPUpdateAreas(
    regions: List<Int>,
    actual: List<Int>,
): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>() 
    var i = 1
    var j = -1
    while (i < regions.size - 1) { // don't create an update region for the last one
        val r = regions[i]
        while (j + 1 < actual.size && actual[j + 1] < r) {
            j++
        }

        if (j != -1) {
            // for first region we need to give extra 1 tick buffer
            check(actual[j] < r)
            if (i == 1) {
                if (actual[j] + 1 >= r) {
                    println("| FAILURE! ${actual[j] + 1} -> r")
                } else {
                    println("|0 updateArea: ${actual[j] + 1} -> $r")
                    out += (actual[j] + 1) to r
                }
            } else {
                println("| updateArea: ${actual[j]} -> $r")
                out += actual[j] to r
            }
        }

        i++
    }

    return out
}

fun List<TimestampedDisplayData>.regionCost(
    scaleTolerance: Double,
    rotTolerance: Double,
    anchors: List<Int>,
    tpUpdateAreas: List<Pair<Int, Int>>,
    start: Int, end: Int,
    costs: Costs,
): Double {
    require(end > start)
    require(start >= 0)
    require(end <= size)

    /*
    we must ensure that we send an update somewhere between the the last tp before a region update (inclusive) and the region update (exclusive)
    so we have a list of ranges which we need to fill
        min list
        max list
    a region will never try to cover an entry in its list which goes outside of its max bounds (avoid duplicates)
     */

    val duration = end - start
    
    val bestPeriod = bestPeriod(
        displayData = this,
        scaleTolerance = scaleTolerance,
        rotTolerance = rotTolerance,
        anchors = anchors,
        tpUpdateAreas = tpUpdateAreas,
        start = start, end = end,
        costs = costs,
    )
    
    val cost = duration / bestPeriod * costs.updateCost

    return cost.toDouble()
}

fun bestPeriod(
    displayData: List<TimestampedDisplayData>,
    scaleTolerance: Double, rotTolerance: Double,
    anchors: List<Int>,
    tpUpdateAreas: List<Pair<Int, Int>>,
    start: Int, end: Int,
    costs: Costs,
): Int {
    val relevant = anchors.filter { it in start..<end }
    val relevantUpdateAreas = mutableListOf<Pair<Int, Int>>()
    for (updateArea in tpUpdateAreas) {
        if (updateArea.first < end && updateArea.second > start) {
            relevantUpdateAreas += updateArea
        }
    }

    val duration = end - start
    var t = duration

    var minCost = duration * costs.updateCost
    var bestPeriod = 1

    while (t > 1) {
        if (duration % t != 0) {
            t--
            continue
        }

        // must also make sure that all anchors that are within this range are covered by this period
        if (relevant.any { (it - start) % t != 0 }) {
            t--
            continue
        }

        if (!relevantUpdateAreas.all { (it.first - start) % t == 0 || it.first + (t - (it.first - start) % t) < it.second }) {
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
                    val d = p.scaleDistance(
                        (e.scaleX - s.scaleX) * f + s.scaleX,
                        (e.scaleY - s.scaleY) * f + s.scaleY,
                        (e.scaleZ - s.scaleZ) * f + s.scaleZ,
                    )

                    if (d > scaleTolerance) {
                        valid = false
                        return@check
                    }
                    
                    val q = Quaternion(s.rot.x, s.rot.y, s.rot.z, s.rot.w).nlerp(e.rot, f)
                    val rd = p.rotDistance(q)
                    
                    if (rd > rotTolerance) {
                        valid = false
                        return@check
                    }

                    k++
                }

                j += t
            }
        }

        if (valid) {
            val c = duration / t * costs.tpCost
            if (c < minCost) {
                minCost = c
                bestPeriod = t
            }
        }

        t--
    }
    
    return bestPeriod
}