package org.yaqmc.android.nativeui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeLyricsTest {
    @Test fun wordSynchronizationComesFromTheDocumentMode() {
        assertTrue(NativeModels.lyrics(JSONObject("""{"syncMode":"word"}""")).wordSynchronized)
        assertFalse(NativeModels.lyrics(JSONObject("""{"syncMode":"line"}""")).wordSynchronized)
        assertFalse(NativeModels.lyrics(JSONObject("""{"syncMode":"unsynchronized"}""")).wordSynchronized)
    }

    @Test fun userAdjustmentAndFileOffsetAreInverseWhenSeeking() {
        for (documentOffset in listOf(-500L, 0L, 800L)) for (userOffset in listOf(-1000L, 0L, 1000L)) {
            val lyrics = Lyrics("song", documentOffset, listOf(LyricLine(5000, 6000, "line")))
            val seek = lyrics.seekPosition(0, userOffset)!!
            assertEquals(5000, lyrics.time(seek, userOffset))
            assertEquals(0, lyrics.activeIndex(seek, userOffset))
            assertEquals(-1, lyrics.activeIndex(seek - 1, userOffset))
        }
        assertEquals(0L, Lyrics(lines = listOf(LyricLine(50, 100, "short"))).seekPosition(0, 100))
    }

    @Test fun missingEndsUseNextTimedLineWithoutTimingPlainText() {
        val lyrics = Lyrics(lines = listOf(LyricLine(100, null, "a"), LyricLine(null, null, "credits"), LyricLine(500, null, "b")))
        assertEquals(0, lyrics.activeIndex(499))
        assertEquals(2, lyrics.activeIndex(500))
        assertEquals(2, lyrics.activeIndex(100000))
        assertNull(lyrics.seekPosition(1))
    }

    @Test fun explicitlyUnsynchronizedDocumentDoesNotSeekEvenWithStrayTimestamps() {
        val lyrics = NativeModels.lyrics(JSONObject("""{"syncMode":"unsynchronized","lines":[{"text":"plain","startMs":10}]}"""))
        assertEquals(-1, lyrics.activeIndex(20))
        assertNull(lyrics.seekPosition(0))
    }

    @Test fun wordRangesPreserveRepeatedWordsPunctuationAndSurrogatePairs() {
        val line = LyricLine(0, 1000, "go, go 🎵!", words = listOf(LyricWord(0, 200, "go"), LyricWord(200, 500, "go"), LyricWord(500, 1000, "🎵")))
        assertEquals(listOf(0 to 2, 4 to 6, 7 to 9), line.wordSpans().map { it.start to it.end })
        assertEquals(listOf("go", "go", "🎵"), line.wordSpans().map { line.text.substring(it.start, it.end) })
        assertTrue(line.copy(words = listOf(LyricWord(0, 100, "other"))).wordSpans().isEmpty())
    }

    @Test fun wordProgressHandlesBoundariesPausesAndZeroDuration() {
        val word = LyricWord(1000, 2000, "word")
        assertEquals(0f, word.progress(0))
        assertEquals(0.5f, word.progress(1500))
        assertEquals(1f, word.progress(5000))
        assertEquals(0f, LyricWord(1000, 1000, "word").progress(1000))
        assertEquals(1f, LyricWord(1000, 1000, "word").progress(1001))
    }

    @Test fun secondaryAutomaticMatchesOldAndroidBehavior() {
        assertFalse(showLyricSecondary("auto", "HELLO", " hello ", false))
        assertFalse(showLyricSecondary("auto", "hello", "Hello there", true))
        assertTrue(showLyricSecondary("auto", "ni hao", "你好", true))
        assertTrue(showLyricSecondary("auto", "你好", "Hello", false))
        assertTrue(showLyricSecondary("show", "hello", "hello", true))
        assertFalse(showLyricSecondary("hide", "ni hao", "你好", true))
        assertFalse(showLyricSecondary("show", "", "你好", false))
    }

    @Test fun frameClockStopsOnPauseAndCannotInventProgressBeyondOneSecondOrTheEnd() {
        assertEquals(2000L, lyricFramePosition(2000, 10000, false, 800))
        assertEquals(2500L, lyricFramePosition(2000, 10000, true, 500))
        assertEquals(3000L, lyricFramePosition(2000, 10000, true, 9000))
        assertEquals(10000L, lyricFramePosition(9800, 10000, true, 500))
        assertEquals(2000L, lyricFramePosition(2000, 10000, true, -500))
    }

    @Test fun malformedSettingsFallBackAndOutOfRangeValuesCannotBreakLayout() {
        assertEquals(NativeLyricPreferences(), NativeLyricPreferences.decode("broken"))
        val normalized = NativeLyricPreferences.decode("""{"fontScale":99,"lineHeight":-2,"fontWeight":42,"timingOffsetMs":-99999,"fadeWidth":9,"translation":"invalid","wordEffect":"invalid"}""")
        assertEquals(1.45f, normalized.fontScale)
        assertEquals(1.05f, normalized.lineHeight)
        assertEquals(700, normalized.fontWeight)
        assertEquals(-2000L, normalized.timingOffsetMs)
        assertEquals(1f, normalized.fadeWidth)
        assertEquals("auto", normalized.translation)
        assertEquals("jump", normalized.wordEffect)
        assertEquals(NativeLyricPreferences(), NativeLyricPreferences(fontScale = Float.NaN, lineHeight = Float.POSITIVE_INFINITY, fadeWidth = Float.NaN).normalized())
    }
}
