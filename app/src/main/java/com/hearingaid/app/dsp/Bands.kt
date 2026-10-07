package com.hearingaid.app.dsp

import kotlin.math.sqrt

/** The processing bands line up with the audiogram test frequencies so a test result maps 1:1 onto gains. */
object Bands {
    val CENTERS_HZ = intArrayOf(250, 500, 1000, 2000, 4000, 8000)
    val COUNT = CENTERS_HZ.size

    /** Crossover between neighbouring bands sits at the geometric mean of their centres. */
    val CROSSOVERS_HZ = FloatArray(COUNT - 1) { sqrt(CENTERS_HZ[it].toFloat() * CENTERS_HZ[it + 1]) }
}

object Ear {
    const val LEFT = 0
    const val RIGHT = 1
}
