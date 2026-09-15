package com.luogen.music.util

/** LRC 歌词行 */
data class LrcLine(val timeMs: Long, val text: String)

/** LRC 解析器（真实歌词文本 → 时间轴行列表） */
object LrcParser {

    private val lineRegex = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]")

    fun parse(lrc: String): List<LrcLine> {
        if (lrc.isBlank()) return emptyList()
        val out = mutableListOf<LrcLine>()
        lrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val matches = lineRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                if (out.isEmpty()) out += LrcLine(0, line)
                return@forEach
            }
            val text = line.substring(matches.last().range.last + 1)
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val msPart = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                out += LrcLine(min * 60_000 + sec * 1000 + msPart, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** 按当前播放位置取当前句歌词 */
    fun current(lines: List<LrcLine>, positionMs: Long): LrcLine? {
        if (lines.isEmpty()) return null
        var cur = lines.first()
        for (l in lines) {
            if (l.timeMs <= positionMs) cur = l else break
        }
        return cur
    }
}