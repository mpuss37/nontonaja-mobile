package com.example.nonton_aja.ui

import android.text.Html
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    companion object {
        fun binarySearchCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
            if (cues.isEmpty()) return null
            var lo = 0
            var hi = cues.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val cue = cues[mid]
                when {
                    positionMs < cue.startMs -> hi = mid - 1
                    positionMs > cue.endMs -> lo = mid + 1
                    else -> return cue
                }
            }
            return null
        }
    }
}

object VttParser {

    fun downloadAndParse(vttUrl: String): List<SubtitleCue> {
        val url = URL(vttUrl)
        val reader = BufferedReader(InputStreamReader(url.openStream()))
        val content = reader.use { it.readText() }
        return parse(content)
    }

    fun parse(vttContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = vttContent.trim().split(Regex("\n\\s*\n"))

        for (block in blocks) {
            val lines = block.trim().split("\n")
            val timeLine = lines.find { it.contains("-->") } ?: continue
            val textLines = lines.filter { line ->
                !line.contains("-->") && !line.startsWith("WEBVTT") && !line.startsWith("NOTE") &&
                line.isNotBlank() && !line.trim().matches(Regex("^\\d+$"))
            }

            if (textLines.isEmpty()) continue

            val times = timeLine.split("-->")
            if (times.size < 2) continue

            val startMs = parseTimestamp(times[0].trim())
            val endMs = parseTimestamp(times[1].trim())
            val text = textLines.joinToString("\n") { cleanHtml(it.trim()) }

            if (startMs >= 0 && endMs > startMs && text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs, endMs, text))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    private fun parseTimestamp(ts: String): Long {
        val parts = ts.split(":")
        if (parts.size < 2) return -1
        return try {
            val hours = parts[0].trim().toLong()
            val minutes = parts[1].trim().toLong()
            val secAndMs = parts[2].trim().split(".")
            val seconds = secAndMs[0].toLong()
            val millis = if (secAndMs.size > 1) secAndMs[1].take(3).padEnd(3, '0').toLong() else 0
            hours * 3600_000 + minutes * 60_000 + seconds * 1000 + millis
        } catch (_: Exception) {
            -1
        }
    }

    private fun cleanHtml(text: String): String {
        return if (text.contains("<")) {
            @Suppress("DEPRECATION")
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            text.trim()
        }
    }
}
