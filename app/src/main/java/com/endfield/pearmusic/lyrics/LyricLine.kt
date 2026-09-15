package com.endfield.pearmusic.lyrics

data class LyricWord(
    val startTime: Long,
    val text: String
)

data class LyricLine(
    val startTime: Long, // In milliseconds
    val content: String,
    val words: List<LyricWord> = emptyList()
)
