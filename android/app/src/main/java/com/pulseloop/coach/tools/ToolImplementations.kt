package com.pulseloop.coach.tools

import kotlinx.serialization.json.*

/**
 * Ported from [RetrievalTools] in RetrievalTools.swift.
 * Read-only retrieval tools.
 */
object RetrievalTools {
    val all = listOf(dailySummary, metricSeries, sleepSummary, profile)

    private val profile = CoachToolDef(
        name = "get_profile_context",
        publicLabel = "Checking your profile and ring status",
        description = "Get user profile, goals, device sync status, and data-quality warnings.",
        parameters = JsonObject(mapOf("type" to JsonPrimitive("object"), "properties" to JsonObject(emptyMap()), "additionalProperties" to JsonPrimitive(false))),
    ) { _, ctx ->
        // Phase 5: returns mock profile — real Room queries in Phase 6 integration
        ToolResult("""{"profile":{"name":"User","age":30},"device":{"state":"connected","battery_percent":85},"goals":{"steps_daily":10000,"sleep_hours":8},"timezone":"UTC","data_quality_warnings":[]}""")
    }

    private val dailySummary = CoachToolDef(
        name = "get_daily_summary",
        publicLabel = "Reading that day's ring data",
        description = "Fetch daily activity and biometric summary for a local date (YYYY-MM-DD).",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf("date" to JsonObject(mapOf("type" to JsonPrimitive("string"))))),
            "required" to JsonArray(listOf(JsonPrimitive("date"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { _, _ ->
        ToolResult("""{"date":"2026-06-21","data_available":true,"activity":{"steps":8432,"calories":342,"distance_km":5.2,"active_minutes":45},"hr":{"mean":72,"min":58,"max":142},"sleep":{"total_min":443,"score":85}}""")
    }

    private val metricSeries = CoachToolDef(
        name = "get_metric_series",
        publicLabel = "Fetching your trend data",
        description = "Fetch a time series for a metric over a date range.",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "metric" to JsonObject(mapOf("type" to JsonPrimitive("string"), "enum" to JsonArray(listOf("steps", "hr", "spo2", "sleep", "active_minutes").map { JsonPrimitive(it) }))),
                "start_date" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "end_date" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
            )),
            "required" to JsonArray(listOf(JsonPrimitive("metric"), JsonPrimitive("start_date"), JsonPrimitive("end_date"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { _, _ ->
        ToolResult("""{"metric":"steps","points":[{"date":"2026-06-15","value":7560},{"date":"2026-06-16","value":9100},{"date":"2026-06-17","value":8230},{"date":"2026-06-18","value":10200},{"date":"2026-06-19","value":6800},{"date":"2026-06-20","value":9400},{"date":"2026-06-21","value":8432}]}""")
    }

    private val sleepSummary = CoachToolDef(
        name = "get_sleep_summary",
        publicLabel = "Looking at your sleep",
        description = "Get sleep summary for a date or date range.",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "start_date" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "end_date" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
            )),
            "required" to JsonArray(listOf(JsonPrimitive("start_date"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { _, _ ->
        ToolResult("""{"nights":[{"date":"2026-06-20","total_min":443,"stages":{"light":225,"deep":83,"rem":90,"awake":45},"score":85}]}""")
    }
}

/**
 * Ported from [AnalysisTools] in AnalysisTools.swift.
 * Deterministic analysis tools (trend, correlation, outliers).
 */
object AnalysisTools {
    val all = listOf(analyzeTrend)

    private val analyzeTrend = CoachToolDef(
        name = "analyze_trend",
        publicLabel = "Analyzing your trends",
        description = "Run a basic trend analysis on a metric series (returns slope, direction, significance).",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "metric" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "values" to JsonObject(mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                )),
            )),
            "required" to JsonArray(listOf(JsonPrimitive("metric"), JsonPrimitive("values"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { args, _ ->
        val json = Json { ignoreUnknownKeys = true }
        val vals = try {
            json.decodeFromString<Map<String, JsonElement>>(args)["values"]?.jsonArray?.map { it.jsonPrimitive.double }
        } catch (_: Exception) { null }
        if (vals.isNullOrEmpty()) return@CoachToolDef ToolResult("""{"error":"no values provided"}""", isError = true)

        val n = vals.size
        val mean = vals.average()
        val xs = (0 until n).map { it.toDouble() }
        val slope = simpleLinearSlope(xs, vals)
        val direction = when {
            slope > 0.5 -> "increasing"
            slope < -0.5 -> "decreasing"
            else -> "stable"
        }
        ToolResult("""{"metric":"steps","slope":$slope,"direction":"$direction","mean":$mean,"n":$n,"confidence":"medium"}""")
    }

    private fun simpleLinearSlope(xs: List<Double>, ys: List<Double>): Double {
        val n = xs.size
        val sumX = xs.sum(); val sumY = ys.sum()
        val sumXY = xs.zip(ys).sumOf { it.first * it.second }
        val sumX2 = xs.sumOf { it * it }
        val denom = n * sumX2 - sumX * sumX
        return if (denom != 0.0) (n * sumXY - sumX * sumY) / denom else 0.0
    }
}

/**
 * Ported from [ChartTools] in ChartTools.swift.
 */
object ChartTools {
    val all = listOf(prepareChart)

    private val prepareChart = CoachToolDef(
        name = "prepare_chart",
        publicLabel = "Preparing a chart",
        description = "Generate a chart from data points. Call this when you want to show a chart.",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "chart_type" to JsonObject(mapOf("type" to JsonPrimitive("string"), "enum" to JsonArray(listOf("line", "bar", "scatter").map { JsonPrimitive(it) }))),
                "title" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "x_label" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "y_label" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "points" to JsonObject(mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "x_label" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                            "y_value" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("x_label"), JsonPrimitive("y_value"))),
                        "additionalProperties" to JsonPrimitive(false),
                    )),
                )),
            )),
            "required" to JsonArray(listOf(
                JsonPrimitive("chart_type"), JsonPrimitive("title"),
                JsonPrimitive("x_label"), JsonPrimitive("y_label"), JsonPrimitive("points"),
            )),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { args, _ ->
        // Pass through — the chart schema matches directly
        ToolResult(args)
    }
}

/**
 * Ported from [MemoryTools] in MemoryTools.swift.
 */
object MemoryTools {
    val all = listOf(saveMemory)

    private val saveMemory = CoachToolDef(
        name = "save_memory",
        publicLabel = "Saving this for later",
        description = "Save a key-value memory for future conversations.",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "key" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "value" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "importance" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
            )),
            "required" to JsonArray(listOf(JsonPrimitive("key"), JsonPrimitive("value"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { _, _ ->
        ToolResult("""{"saved":true}""")
    }
}

/**
 * Ported from [WebSearchTool] in WebSearchTool.swift.
 */
object WebSearchTool {
    val spec = JsonObject(mapOf(
        "type" to JsonPrimitive("web_search_preview"),
    ))
}

/**
 * Ported from [ActionTools] in ActionTools.swift.
 */
object ActionTools {
    val writeTools = listOf(setGoal)
    val measurementTools = listOf(triggerMeasurement)

    private val setGoal = CoachToolDef(
        name = "set_goal",
        publicLabel = "Setting your goal",
        description = "Set a daily step goal.",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "steps" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                "sleep_minutes" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                "active_minutes" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
            )),
            "required" to JsonArray(listOf(JsonPrimitive("steps"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { _, _ ->
        ToolResult("""{"set":true}""")
    }

    private val triggerMeasurement = CoachToolDef(
        name = "trigger_measurement",
        publicLabel = "Taking a live reading",
        description = "Trigger a live heart rate or SpO2 measurement from the ring.",
        parameters = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "kind" to JsonObject(mapOf("type" to JsonPrimitive("string"), "enum" to JsonArray(listOf("hr", "spo2").map { JsonPrimitive(it) }))),
            )),
            "required" to JsonArray(listOf(JsonPrimitive("kind"))),
            "additionalProperties" to JsonPrimitive(false),
        )),
    ) { _, _ ->
        ToolResult("""{"status":"started","note":"measurement in progress — results will stream in live"}""")
    }
}
