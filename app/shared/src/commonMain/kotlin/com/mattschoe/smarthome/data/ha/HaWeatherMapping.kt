package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.WeatherCondition
import kotlin.math.exp
import kotlin.math.min

/**
 * Pure mappers from a Home Assistant `weather.*` entity's wire shape to the domain climate model.
 * Free of I/O, so the formula and the unit handling are unit-tested without a live instance.
 */

/** HA's kebab-case condition state to [WeatherCondition]; `null` for `unknown`/`unavailable`/anything new. */
fun weatherConditionFrom(state: String?): WeatherCondition? = when (state) {
    "clear-night" -> WeatherCondition.ClearNight
    "cloudy" -> WeatherCondition.Cloudy
    "exceptional" -> WeatherCondition.Exceptional
    "fog" -> WeatherCondition.Fog
    "hail" -> WeatherCondition.Hail
    "lightning" -> WeatherCondition.Lightning
    "lightning-rainy" -> WeatherCondition.LightningRainy
    "partlycloudy" -> WeatherCondition.PartlyCloudy
    "pouring" -> WeatherCondition.Pouring
    "rainy" -> WeatherCondition.Rainy
    "snowy" -> WeatherCondition.Snowy
    "snowy-rainy" -> WeatherCondition.SnowyRainy
    "sunny" -> WeatherCondition.Sunny
    "windy" -> WeatherCondition.Windy
    "windy-variant" -> WeatherCondition.WindyVariant
    else -> null
}

/** W/m² of solar radiation attributed to one point of UV index, and the ceiling the proxy is capped at. */
private const val RadiationPerUvIndex = 25.0
private const val RadiationCap = 120.0

/**
 * Apparent ("feels like") temperature in °C — the Bureau of Meteorology formula including the solar
 * radiation term:
 *
 * ```
 * AT = Ta + 0.348·e − 0.70·ws + 0.70·(Q / (ws + 10)) − 4.25
 * ```
 *
 * where `e` is water vapour pressure in hPa, `ws` wind speed in m/s and `Q` a solar radiation proxy
 * derived from [uvIndex]. Unlike wind chill and the heat index it is continuous across the whole
 * range rather than only below 10 °C or above 27 °C, which is where Danish weather sits year-round.
 * Vapour pressure comes from [humidityPct] when reported and from [dewPointC] otherwise; absent wind
 * counts as still air, and an absent UV index (at night) as no sun.
 *
 * `null` [tempC] yields `null`. With humidity, dew point **and** wind all missing there is nothing to
 * adjust for, so the air temperature is returned unchanged rather than shifted by the bare constant.
 */
fun feelsLikeC(
    tempC: Double?,
    humidityPct: Int?,
    dewPointC: Double?,
    windMs: Double?,
    uvIndex: Double?,
): Double? {
    val ta = tempC ?: return null
    if (humidityPct == null && dewPointC == null && windMs == null) return ta
    val e = vapourPressureHpa(ta, humidityPct, dewPointC) ?: 0.0
    val ws = windMs?.coerceAtLeast(0.0) ?: 0.0
    val q = min(RadiationPerUvIndex * (uvIndex ?: 0.0).coerceAtLeast(0.0), RadiationCap)
    return ta + 0.348 * e - 0.70 * ws + 0.70 * (q / (ws + 10.0)) - 4.25
}

/**
 * Water vapour pressure in hPa, from relative humidity against the saturation pressure at [tempC],
 * or — when humidity is absent — as the saturation pressure at the dew point, which is the same
 * quantity by definition. `null` when neither is reported.
 */
private fun vapourPressureHpa(tempC: Double, humidityPct: Int?, dewPointC: Double?): Double? = when {
    humidityPct != null -> humidityPct / 100.0 * saturationPressureHpa(tempC)
    dewPointC != null -> saturationPressureHpa(dewPointC)
    else -> null
}

/** Saturation vapour pressure over water at [tempC], in hPa (Magnus-Tetens). */
private fun saturationPressureHpa(tempC: Double): Double =
    6.105 * exp(17.27 * tempC / (237.7 + tempC))

/**
 * Wind speed in m/s from a value in whatever [unit] the weather integration reports (`wind_speed_unit`
 * — met.no says `km/h`, others differ, so it is read rather than assumed). An unrecognized unit is
 * taken as m/s.
 */
fun windSpeedToMs(value: Double?, unit: String?): Double? = value?.times(
    when (unit?.trim()?.lowercase()) {
        "km/h", "kmh", "kph" -> 1000.0 / 3600.0
        "mph", "mi/h" -> 1609.344 / 3600.0
        "ft/s" -> 0.3048
        else -> 1.0
    }
)

/** Temperature in °C from a value in the entity's own `temperature_unit`. Unrecognized reads as °C. */
fun temperatureToC(value: Double?, unit: String?): Double? = value?.let {
    if (unit?.trim()?.uppercase()?.endsWith("F") == true) (it - 32.0) * 5.0 / 9.0 else it
}
