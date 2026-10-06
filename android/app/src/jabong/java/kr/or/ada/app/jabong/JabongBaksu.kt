// 안드로이드 자봉 — 박수 소리 (2.7.0, 빌드 261006-A1, 이사장님 승인 2026-10-06 "정답을 맞추면 박수 소리도 나오게")
// 아이폰 JabongBaksu.swift 와 짝. 자봉 앱 안에서만 만들어 쓰고 길눈 소리 부품(Sori)은 건드리지 않음.
package kr.or.ada.app.jabong

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

object Baksu {
    private var track: AudioTrack? = null

    /** 박수 치기 — keuge 면 여러 사람이 더 오래 치는 박수(등록을 마쳤을 때, 다 그렸을 때) */
    fun chigi(keuge: Boolean = false) {
        val rate = 22050
        val n = (rate * (if (keuge) 2.4 else 1.1)).toInt()
        val su = if (keuge) 26 else 10
        val s = FloatArray(n)
        val ttae = rate / 25                       // 한 번 치는 소리 길이 40천분의 1초
        for (k in 0 until su) {
            val pyeon = (k + Random.nextDouble()) / su
            val start = (maxOf(1, n - ttae) * pyeon).toInt()
            val keugi = 0.35f + Random.nextFloat() * 0.4f
            for (i in 0 until ttae) {
                val j = start + i
                if (j >= n) break
                s[j] += (Random.nextFloat() * 2f - 1f) * keugi * exp(-i.toFloat() / ttae * 6f)
            }
        }
        val kkeut = minOf(n, rate / 4)              // 끝을 부드럽게 줄임
        for (i in 0 until kkeut) s[n - 1 - i] *= i.toFloat() / kkeut
        var goj = 0.0001f
        for (v in s) if (abs(v) > goj) goj = abs(v)
        val pcm = ShortArray(n) { ((s[it] / goj * 0.9f).coerceIn(-1f, 1f) * 32000f).toInt().toShort() }
        try {
            track?.release()
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, n)
            t.play()
            track = t
        } catch (_: Exception) {
        }
    }
}
