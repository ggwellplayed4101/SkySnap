package com.bhargav.skysnap

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var etCity: EditText
    private lateinit var tvCurrentLocation: TextView
    private lateinit var tvSunrise: TextView
    private lateinit var tvSunset: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvWindSpeed: TextView
    private lateinit var tvCloudCover: TextView
    private lateinit var tvVisibility: TextView
    private lateinit var btnFetch: Button

    private val client = OkHttpClient()

    // Default coordinates (New York City)
    private var currentLatitude = 40.7128
    private var currentLongitude = -74.0060
    private var currentCityName = "New York City, NY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize all views
        etCity = findViewById(R.id.etCity)
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)
        tvSunrise = findViewById(R.id.tvSunrise)
        tvSunset = findViewById(R.id.tvSunset)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvWindSpeed = findViewById(R.id.tvWindSpeed)
        tvCloudCover = findViewById(R.id.tvCloudCover)
        tvVisibility = findViewById(R.id.tvVisibility)
        btnFetch = findViewById(R.id.btnFetch)

        btnFetch.setOnClickListener {
            val cityInput = etCity.text.toString().trim()
            if (cityInput.isNotEmpty()) {
                // User entered a city, get coordinates first
                getCoordinatesForCity(cityInput)
            } else {
                // No city entered, use current location
                fetchWeatherData()
            }
        }

        // Initial fetch with default location
        fetchWeatherData()
    }

    private fun getCoordinatesForCity(cityName: String) {
        // Use Open-Meteo's geocoding API
        val geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${cityName}&count=1&language=en&format=json"

        val request = Request.Builder()
            .url(geocodingUrl)
            .build()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val json = JSONObject(responseBody)
                        val results = json.getJSONArray("results")

                        if (results.length() > 0) {
                            val firstResult = results.getJSONObject(0)
                            currentLatitude = firstResult.getDouble("latitude")
                            currentLongitude = firstResult.getDouble("longitude")

                            // Build location name
                            val name = firstResult.getString("name")
                            val country = firstResult.optString("country", "")
                            val admin1 = firstResult.optString("admin1", "")

                            currentCityName = when {
                                admin1.isNotEmpty() -> "$name, $admin1, $country"
                                country.isNotEmpty() -> "$name, $country"
                                else -> name
                            }

                            // Update location display and fetch weather
                            tvCurrentLocation.text = "📍 $currentCityName"
                            fetchWeatherData()
                        } else {
                            Toast.makeText(this@MainActivity, "City not found. Please try a different name.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Error finding city: ${response.code}", Toast.LENGTH_SHORT).show()
                }
                response.close()
            } catch (e: IOException) {
                Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchWeatherData() {
        // Show loading state
        setLoadingState()

        // Enhanced URL with current coordinates
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$currentLatitude&longitude=$currentLongitude&" +
                "daily=sunrise,sunset,temperature_2m_max,temperature_2m_min&" +
                "current=temperature_2m,relative_humidity_2m,wind_speed_10m,cloud_cover,visibility&" +
                "timezone=auto&" +
                "temperature_unit=fahrenheit&" +
                "wind_speed_unit=mph"

        val request = Request.Builder()
            .url(url)
            .build()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val json = JSONObject(responseBody)
                        parseWeatherData(json)
                    } else {
                        showError("Empty response")
                    }
                } else {
                    showError("HTTP Error: ${response.code}")
                }
                response.close()
            } catch (e: IOException) {
                showError("Network error: ${e.message}")
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private fun setLoadingState() {
        tvSunrise.text = "🌅 Sunrise: Loading..."
        tvSunset.text = "🌇 Sunset: Loading..."
        tvTemperature.text = "🌡️ Temperature: Loading..."
        tvHumidity.text = "💧 Humidity: Loading..."
        tvWindSpeed.text = "💨 Wind: Loading..."
        tvCloudCover.text = "☁️ Cloud Cover: Loading..."
        tvVisibility.text = "👁️ Visibility: Loading..."
    }

    private fun parseWeatherData(json: JSONObject) {
        try {
            // Parse daily data (sunrise, sunset, temperature range)
            val daily = json.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray = daily.getJSONArray("sunset")
            val maxTempArray = daily.getJSONArray("temperature_2m_max")
            val minTempArray = daily.getJSONArray("temperature_2m_min")

            // Parse current weather data
            val current = json.getJSONObject("current")
            val currentTemp = current.getDouble("temperature_2m")
            val humidity = current.getInt("relative_humidity_2m")
            val windSpeed = current.getDouble("wind_speed_10m")
            val cloudCover = current.getInt("cloud_cover")
            val visibility = current.getDouble("visibility")

            // Get today's data (first element for daily arrays)
            val sunrise = if (sunriseArray.length() > 0) sunriseArray.getString(0) else "N/A"
            val sunset = if (sunsetArray.length() > 0) sunsetArray.getString(0) else "N/A"
            val maxTemp = if (maxTempArray.length() > 0) maxTempArray.getDouble(0) else 0.0
            val minTemp = if (minTempArray.length() > 0) minTempArray.getDouble(0) else 0.0

            // Format and display all data
            tvSunrise.text = "🌅 Sunrise: ${formatTime(sunrise)}"
            tvSunset.text = "🌇 Sunset: ${formatTime(sunset)}"
            tvTemperature.text = "🌡️ Current: ${currentTemp.toInt()}°F (H:${maxTemp.toInt()}° L:${minTemp.toInt()}°)"
            tvHumidity.text = "💧 Humidity: $humidity%"
            tvWindSpeed.text = "💨 Wind: ${windSpeed.toInt()} mph"
            tvCloudCover.text = "☁️ Cloud Cover: $cloudCover%"
            tvVisibility.text = "👁️ Visibility: ${(visibility / 1000).toInt()} km"

            // Clear the input field after successful fetch
            etCity.text.clear()

        } catch (e: Exception) {
            showError("Error parsing data: ${e.message}")
        }
    }

    private fun showError(message: String) {
        tvSunrise.text = message
        tvSunset.text = ""
        tvTemperature.text = ""
        tvHumidity.text = ""
        tvWindSpeed.text = ""
        tvCloudCover.text = ""
        tvVisibility.text = ""
    }

    private fun formatTime(timeString: String): String {
        return try {
            // The API returns ISO 8601 format like "2024-01-15T07:23"
            val timePart = timeString.split("T")[1]
            val hours = timePart.split(":")[0].toInt()
            val minutes = timePart.split(":")[1]

            val amPm = if (hours >= 12) "PM" else "AM"
            val displayHours = if (hours > 12) hours - 12 else if (hours == 0) 12 else hours

            "$displayHours:$minutes $amPm"
        } catch (e: Exception) {
            timeString // Return original if formatting fails
        }
    }
}