package com.endfield.pearmusic.lyrics

import java.util.regex.Pattern

object LrcParser {
    // Robust pattern for [mm:ss.ms] or [mm:ss:ms]
    private val timePattern = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})[.:](\\d{1,3})\\]")
    // Pattern for per-word tags: <mm:ss.ms>
    private val wordTagPattern = Pattern.compile("<(\\d{1,2}):(\\d{1,2})[.:](\\d{1,3})>")

    fun parse(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        if (lrcContent.isBlank()) return lines

        lrcContent.lines().forEach { line ->
            val matcher = timePattern.matcher(line)
            val timeTags = mutableListOf<Long>()
            var lastEnd = 0
            
            while (matcher.find()) {
                val time = parseTimeTag(matcher.group(1), matcher.group(2), matcher.group(3))
                timeTags.add(time)
                lastEnd = matcher.end()
            }

            if (timeTags.isNotEmpty()) {
                val rawContent = line.substring(lastEnd)
                val (cleanContent, words) = parseWords(rawContent)
                
                timeTags.forEach { time ->
                    lines.add(LyricLine(time, cleanContent, words))
                }
            }
        }
        return lines.sortedBy { it.startTime }
    }

    private fun parseTimeTag(minStr: String?, secStr: String?, msStr: String?): Long {
        val min = minStr?.toLong() ?: 0L
        val sec = secStr?.toLong() ?: 0L
        var ms = msStr?.toLong() ?: 0L
        
        if (msStr != null) {
            if (msStr.length == 2) ms *= 10
            else if (msStr.length == 1) ms *= 100
        }

        return (min * 60000) + (sec * 1000) + ms
    }

    private fun parseWords(content: String): Pair<String, List<LyricWord>> {
        val words = mutableListOf<LyricWord>()
        val matcher = wordTagPattern.matcher(content)
        val parts = mutableListOf<String>()
        val timeTags = mutableListOf<Long>()
        
        var lastEnd = 0
        while (matcher.find()) {
            timeTags.add(parseTimeTag(matcher.group(1), matcher.group(2), matcher.group(3)))
            parts.add(content.substring(lastEnd, matcher.start()))
            lastEnd = matcher.end()
        }
        parts.add(content.substring(lastEnd))
        
        if (timeTags.isEmpty()) {
            return Pair(content.trim(), emptyList())
        }
        
        val cleanContentBuilder = StringBuilder()
        for (i in 0 until timeTags.size) {
            var text = parts[i+1]
            // If there's text before the first tag, attach it to the first word
            if (i == 0 && parts[0].isNotEmpty()) {
                text = parts[0] + text
            }
            words.add(LyricWord(timeTags[i], text))
            cleanContentBuilder.append(text)
        }
        
        return Pair(cleanContentBuilder.toString().trim(), words)
    }

    fun format(lines: List<LyricLine>): String {
        return lines.joinToString("\n") { line ->
            val min = line.startTime / 60000
            val sec = (line.startTime % 60000) / 1000
            val ms = (line.startTime % 1000) / 10 // centiseconds for standard LRC
            val lineTag = String.format("[%02d:%02d.%02d]", min, sec, ms)
            
            if (line.words.isNotEmpty()) {
                val wordsFormatted = line.words.joinToString("") { word ->
                    val wMin = word.startTime / 60000
                    val wSec = (word.startTime % 60000) / 1000
                    val wMs = (word.startTime % 1000) / 10
                    String.format("<%02d:%02d.%02d>%s", wMin, wSec, wMs, word.text)
                }
                "$lineTag$wordsFormatted"
            } else {
                "$lineTag${line.content}"
            }
        }
    }
}
