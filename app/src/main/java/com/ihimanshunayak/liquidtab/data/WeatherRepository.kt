/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — weather.
 *
 * Name      : WeatherRepository.kt
 * Version   : 1.0.0
 * Purpose   : Current conditions and a short forecast for the weather widget,
 *             from Open-Meteo.
 *
 * Notes     : Open-Meteo is used because it needs no API key and no account,
 *             which matters for a launcher: there is nowhere to store a key
 *             that would not be a secret on a device the user owns, and a
 *             widget that only works after a sign-in is a widget that shows an
 *             error to most users. The city→coordinates step uses the same
 *             provider's geocoding endpoint.
 *
 *             Requests are cached for [CACHE_TTL_MS] and the whole thing runs
 *             on the IO dispatcher. A failed request keeps the last good value
 *             and marks it stale rather than replacing a temperature with
 *             nothing.
 */

package com.ihimanshunayak.liquidtab.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Current conditions plus today's range. */
data class Weather(
    val temperatureC: Float,
    val apparentC: Float,
    val weatherCode: Int,
    val highC: Float,
    val lowC: Float,
    val isDay: Boolean,
    val fetchedAtMs: Long,
) {
    /** A short human description, from the WMO code Open-Meteo returns. */
    val description: String get() = WeatherRepository.describe(weatherCode)
}

/** A place the user can pick as their weather city. */
data class Place(
    val name: String,
    val country: String,
    val latitude: Float,
    val longitude: Float,
) {
    val label: String get() = if (country.isBlank()) name else "$name, $country"
}

object WeatherRepository {

    private const val TAG = "WeatherRepository"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    @Volatile
    private var cached: Weather? = null

    @Volatile
    private var cachedFor: Pair<Float, Float>? = null

    /**
     * Current conditions for [latitude]/[longitude], served from cache inside
     * the TTL. Returns null only when there is neither a fresh response nor a
     * previous one to fall back on.
     */
    suspend fun current(latitude: Float, longitude: Float, force: Boolean = false): Weather? {
        val fresh = cached?.takeIf { !force }?.takeIf {
            cachedFor == latitude to longitude &&
                System.currentTimeMillis() - it.fetchedAtMs < CACHE_TTL_MS
        }
        if (fresh != null) return fresh

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=1"
                val json = JSONObject(httpGet(url))
                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                val weather = Weather(
                    temperatureC = current.getDouble("temperature_2m").toFloat(),
                    apparentC = current.getDouble("apparent_temperature").toFloat(),
                    weatherCode = current.getInt("weather_code"),
                    highC = daily.getJSONArray("temperature_2m_max").getDouble(0).toFloat(),
                    lowC = daily.getJSONArray("temperature_2m_min").getDouble(0).toFloat(),
                    isDay = current.optInt("is_day", 1) == 1,
                    fetchedAtMs = System.currentTimeMillis(),
                )
                cached = weather
                cachedFor = latitude to longitude
                weather
            } catch (t: Throwable) {
                Log.w(TAG, "Weather request failed", t)
                // Keeping the last good value is better than a blank widget, but
                // only when it was for these coordinates: the widget's city is
                // user-editable, and falling back to a previous city's number
                // would label one place's weather with another's.
                cached.takeIf { cachedFor == latitude to longitude }
            }
        }
    }

    /** Resolves a city name to candidates the user can choose from. */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=en&format=json"
            val json = JSONObject(httpGet(url))
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until results.length()) {
                    val item = results.getJSONObject(index)
                    add(
                        Place(
                            name = item.optString("name"),
                            country = item.optString("country"),
                            latitude = item.getDouble("latitude").toFloat(),
                            longitude = item.getDouble("longitude").toFloat(),
                        ),
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Geocoding failed", t)
            emptyList()
        }
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "—"
    }
}
