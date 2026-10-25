package org.yaqmc.android.nativeui

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/** Runs under the test APK's separate UID so Android delivers a real focus loss to the player. */
class NativeAudioFocusProbeActivity : Activity() {
    private var focus: AudioFocusRequest? = null
    private var audio: AudioTrack? = null
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "Temporary audio focus test" }
        setContentView(status)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || focus != null) return
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper())).build().also {
                val result = getSystemService(AudioManager::class.java).requestAudioFocus(it)
                status.text = "Focus request: $result"
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audio = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(3200).build().apply {
                            write(ShortArray(1600), 0, 1600)
                            setLoopPoints(0, 1600, -1)
                            setVolume(0f)
                            play()
                        }
                }
            }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "org.yaqmc.test.RELEASE_FOCUS") finish()
    }
    override fun onDestroy() {
        audio?.release()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        super.onDestroy()
    }
}
