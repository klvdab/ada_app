// BYOD 방송 — 뮤 소리 줄이기 (1.0.0판, 빌드 261003-B1)
// 노트북판(현장 해설방송 3.3판)과 똑같이 16비트 소리를 8비트 뮤(G.711 μ-law)로 줄입니다.
// 듣는 화면(deut.html)의 DEC 표가 이것을 되돌려 틉니다.
package kr.or.ada.app.byod

object MuLaw {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun hana(sample: Int): Byte {
        var s = sample
        val sign = if (s < 0) 0x80 else 0
        if (s < 0) s = -s
        if (s > CLIP) s = CLIP
        s += BIAS
        var exponent = 7
        var mask = 0x4000
        while ((s and mask) == 0 && exponent > 0) { exponent--; mask = mask shr 1 }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return ((sign or (exponent shl 4) or mantissa).inv() and 0xFF).toByte()
    }

    // pcm 의 앞 n 개를 줄여 담아 돌려줌
    fun jurigi(pcm: ShortArray, n: Int): ByteArray {
        val out = ByteArray(n)
        for (i in 0 until n) out[i] = hana(pcm[i].toInt())
        return out
    }

    // 소리 없음(뮤 0xFF)
    fun goyo(n: Int): ByteArray = ByteArray(n) { 0xFF.toByte() }
}
