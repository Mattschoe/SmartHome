package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.model.WeatherCondition
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaWeatherMappingTest {

    // --- Conditions ---

    @Test
    fun conditions_mapEveryStateHomeAssistantReports() {
        val expected = mapOf(
            "clear-night" to WeatherCondition.ClearNight,
            "cloudy" to WeatherCondition.Cloudy,
            "exceptional" to WeatherCondition.Exceptional,
            "fog" to WeatherCondition.Fog,
            "hail" to WeatherCondition.Hail,
            "lightning" to WeatherCondition.Lightning,
            "lightning-rainy" to WeatherCondition.LightningRainy,
            "partlycloudy" to WeatherCondition.PartlyCloudy,
            "pouring" to WeatherCondition.Pouring,
            "rainy" to WeatherCondition.Rainy,
            "snowy" to WeatherCondition.Snowy,
            "snowy-rainy" to WeatherCondition.SnowyRainy,
            "sunny" to WeatherCondition.Sunny,
            "windy" to WeatherCondition.Windy,
            "windy-variant" to WeatherCondition.WindyVariant,
        )

        expected.forEach { (state, condition) -> assertEquals(condition, weatherConditionFrom(state)) }
        // Every condition is covered, so a new enum entry without a mapping fails here.
        assertEquals(WeatherCondition.entries.toSet(), expected.values.toSet())
    }

    @Test
    fun conditions_areNullForAnythingUnrecognized() {
        assertNull(weatherConditionFrom("unknown"))
        assertNull(weatherConditionFrom("unavailable"))
        assertNull(weatherConditionFrom("mildly-ominous")) // a future HA condition
        assertNull(weatherConditionFrom(null))
    }

    // --- Units ---

    @Test
    fun windSpeed_isNormalizedFromTheUnitTheIntegrationReports() {
        assertEquals(4.0, windSpeedToMs(14.4, "km/h")!!, 1e-9)   // met.no
        assertEquals(4.0, windSpeedToMs(4.0, "m/s")!!, 1e-9)
        assertEquals(4.4704, windSpeedToMs(10.0, "mph")!!, 1e-4)
        assertEquals(3.048, windSpeedToMs(10.0, "ft/s")!!, 1e-9)
        assertEquals(4.0, windSpeedToMs(4.0, null)!!, 1e-9)      // unrecognized reads as m/s
        assertNull(windSpeedToMs(null, "km/h"))
    }

    @Test
    fun temperature_isNormalizedFromTheEntitysOwnUnit() {
        assertEquals(18.8, temperatureToC(18.8, "°C")!!, 1e-9)
        assertEquals(0.0, temperatureToC(32.0, "°F")!!, 1e-9)
        assertEquals(100.0, temperatureToC(212.0, "°F")!!, 1e-9)
        assertEquals(18.8, temperatureToC(18.8, null)!!, 1e-9)
        assertNull(temperatureToC(null, "°C"))
    }

    // --- Apparent temperature ---

    @Test
    fun feelsLike_matchesTheLiveMildReading() {
        // The live met.no reading: 18.8 °C, 53 %, 14.4 km/h, UVI 2.9 — a touch above the air temp.
        val at = feelsLikeC(
            tempC = 18.8,
            humidityPct = 53,
            dewPointC = 8.9,
            windMs = windSpeedToMs(14.4, "km/h"),
            uvIndex = 2.9,
        )

        assertNotNull(at)
        assertEquals(19, at.roundToInt())
    }

    @Test
    fun feelsLike_tracksWindChillWhenColdAndWindy() {
        // 0 °C in 36 km/h: the Environment Canada wind chill standard says −8.5°; this stays within a
        // couple of degrees of it rather than falling into the heat index's dead zone.
        val at = feelsLikeC(tempC = 0.0, humidityPct = 80, dewPointC = null, windMs = 10.0, uvIndex = 0.0)

        assertNotNull(at)
        assertTrue(at in -11.0..-7.0, "expected roughly wind-chill cold, got $at")
    }

    @Test
    fun feelsLike_fallsBackToTheDewPointWhenHumidityIsAbsent() {
        val fromHumidity = feelsLikeC(18.8, humidityPct = 53, dewPointC = null, windMs = 4.0, uvIndex = 2.9)!!
        val fromDewPoint = feelsLikeC(18.8, humidityPct = null, dewPointC = 8.9, windMs = 4.0, uvIndex = 2.9)!!

        // Both describe the same air, so the two routes agree to within rounding of the reported values.
        assertEquals(fromHumidity, fromDewPoint, 0.5)
    }

    @Test
    fun feelsLike_treatsAbsentWindAsStillAirAndAbsentUvAsNoSun() {
        val night = feelsLikeC(10.0, humidityPct = 90, dewPointC = null, windMs = null, uvIndex = null)!!
        val sunlit = feelsLikeC(10.0, humidityPct = 90, dewPointC = null, windMs = null, uvIndex = 4.0)!!

        assertTrue(sunlit > night, "sun should warm the apparent temperature")
    }

    @Test
    fun feelsLike_isTheAirTemperatureWhenNothingCanBeAdjustedFor() {
        // No humidity, no dew point, no wind: applying the bare −4.25 constant to nothing would lie.
        assertEquals(18.8, feelsLikeC(18.8, null, null, null, null)!!, 1e-9)
    }

    @Test
    fun feelsLike_isNullWithoutATemperature() {
        assertNull(feelsLikeC(null, humidityPct = 53, dewPointC = 8.9, windMs = 4.0, uvIndex = 2.9))
    }
}
