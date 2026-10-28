package org.yaqmc.android.nativeui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeMaintenanceModelsTest {
    @Test fun statisticsSnapshotProjectsAllDimensionsWithoutChangingUnits() {
        val snapshot = NativeStatisticsSnapshot.parse(JSONObject("""
            {
              "range":"7-days","recordCount":3,"qualifiedListeningMs":90000,
              "qualifiedPlayCount":2,"completedCount":1,"skipRate":0.25,"databaseBytes":1234,
              "daily":[{"dayStartMs":1000,"listenedMs":60000}],
              "topSongs":[{"providerId":"qqmusic","id":"42","title":"Song","subtitle":"Artist","listenedMs":70000}],
              "topArtists":[{"providerId":"qqmusic","id":"7","title":"Artist","listenedMs":80000}],
              "topAlbums":[{"providerId":"qqmusic","id":"8","title":"Album","listenedMs":90000}],
              "qualities":[{"key":"lossless","listenedMs":30000}],
              "providers":[{"key":"qqmusic","listenedMs":90000}]
            }
        """))
        assertEquals(90000L, snapshot.listeningMs)
        assertEquals(2L, snapshot.playCount)
        assertEquals(1L, snapshot.completed)
        assertEquals(0.25, snapshot.skipRate, 0.0001)
        assertEquals(1000L, snapshot.daily.single().startMs)
        assertEquals("42", snapshot.songs.single().id)
        assertEquals("qqmusic", snapshot.songs.single().providerId)
        assertEquals(1, snapshot.artists.size)
        assertEquals(1, snapshot.albums.size)
        assertEquals("lossless", snapshot.qualities.single().label)
        assertEquals("qqmusic", snapshot.providers.single().label)
    }

    @Test fun emptyAndMalformedStatisticsRemainsFiniteAndSafe() {
        val snapshot = NativeStatisticsSnapshot.parse(JSONObject("""{"skipRate":"invalid","recordCount":-3,"daily":[{"listenedMs":-4}]}"""))
        assertEquals(0L, snapshot.recordCount)
        assertEquals(0.0, snapshot.skipRate, 0.0)
        assertEquals(0L, snapshot.daily.single().listenedMs)
        assertTrue(snapshot.songs.isEmpty())
        assertEquals(0L, snapshot.databaseBytes)
    }

    @Test fun cacheStatsUsesFullByteAndEntryCounts() {
        val stats = NativeCacheStats.parse(JSONObject("""
            {"totalBytes":2097152,"mediaBytes":1048576,"artworkBytes":512000,
             "mediaEntries":4,"artworkEntries":5,"metadataEntries":27,"lyricEntries":8,
             "mediaLimitBytes":104857600,"artworkLimitBytes":52428800}
        """))
        assertEquals(2097152L, stats.totalBytes)
        assertEquals(4L, stats.mediaEntries)
        assertEquals(5L, stats.artworkEntries)
        assertEquals(27L, stats.metadataEntries)
        assertEquals(8L, stats.lyricEntries)
        assertEquals(104857600L, stats.mediaLimitBytes)
        assertEquals("2.0 MB", readableBytes(stats.totalBytes))
        assertEquals("0 B", readableBytes(-1))
    }

    @Test fun negativeProviderCountsAreClamped() {
        val stats = NativeCacheStats.parse(JSONObject("""{"totalBytes":-10,"mediaEntries":-2}"""))
        assertEquals(0L, stats.totalBytes)
        assertEquals(0L, stats.mediaEntries)
    }
}
