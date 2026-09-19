package com.critterfarm.data

/**
 * Unit conversions in one place.
 *
 * Health Connect returns SI values (millilitres, kilograms); the farm's owner reads fl oz and
 * lb. Both the stats rows and the history view need these, so they must not be duplicated.
 */
object Units {
    const val ML_PER_FL_OZ = 29.5735
    const val LB_PER_KG = 2.2046226

    fun mlToFlOz(ml: Double): Double = ml / ML_PER_FL_OZ

    fun flOzToMl(flOz: Double): Double = flOz * ML_PER_FL_OZ

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG
}
