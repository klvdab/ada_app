// LVD 송출 — 아이피타임 방식 행사 녹음 (1.7.0판, 빌드 261021-B15, 이사장님 승인 2026-10-21, 방클)
// 아이피타임 공유기는 녹음을 할 수 없어, 방송 서버인 이 기기가 해설 소리를 직접 녹음합니다(녹음·녹화는 무조건 나스에).
// 플린트 2 공유기 서버(byod_flint.py)의 행사 녹음과 똑같은 꼴로 담아, 같은 길(Haengsa)로 LVD 방송 다시 듣기에 올립니다.
//   1. 방송을 켜면 행사 하나를 엶 — 이름 byod년월일시분초(이 기기 시각, 한국 시간)
//   2. 해설 소리(뮤 8비트, 1초에 16000바이트)를 1분 조각(0001.ul …)으로 씀, 10초마다 저장 장치에 확실히 씀(전기가 끊겨도 10초 안쪽만 잃음)
//   3. 1분마다 듣는 분 수, 도움 요청이 오면 그때를 기록(girok_jinhaeng.json)
//   4. 방송을 멈추면 조각을 이어 nokeum.ul, 결과보고 bogo.txt(EUC-KR, CRLF), 마지막에 girok.json 을 씀 — girok.json 이 있어야 목록·올리기에 나옴
//   5. 방송 중에 기기가 꺼졌으면, 다음에 앱이 켜질 때 남은 조각으로 마감해 올림
package kr.or.ada.app.byod

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone

object GigiNokeum {
    private const val JOGAK_CHO = 60_000L     // 조각 하나 1분
    private const val BYTE_CHO = 16000.0     // 뮤 1초 바이트

    private var teo: File? = null
    private var g: JSONObject? = null
    private var f: FileOutputStream? = null
    private var fTtae = 0L
    private var jeojangTtae = 0L
    private var bunTtae = 0L
    private var yocheongBon = 0
    @Volatile var sid = ""                   // 지금 녹음 중인 행사(없으면 빈 글)
    @Volatile var sseunBaiteu = 0L           // 이번 행사에 쓴 바이트(현장 점검용)

    private fun kst(ms: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply { timeInMillis = ms }

    private fun sigakMal(cho: Double): String {
        val c = kst((cho * 1000).toLong())
        val h = c.get(Calendar.HOUR_OF_DAY)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return (if (h < 12) "오전 " else "오후 ") + h12 + "시 " + c.get(Calendar.MINUTE) + "분"
    }

    private fun nalMal(cho: Double): String {
        val c = kst((cho * 1000).toLong())
        val yoil = arrayOf("일", "월", "화", "수", "목", "금", "토")[c.get(Calendar.DAY_OF_WEEK) - 1]
        return "${c.get(Calendar.YEAR)}년 ${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일 ${yoil}요일"
    }

    private fun gileeMal(cho0: Double): String {
        val cho = Math.round(cho0).toInt()
        val h = cho / 3600; val m = (cho % 3600) / 60; val s = cho % 60
        return when {
            h > 0 -> "${h}시간 ${m}분"
            m > 0 -> if (s > 0) "${m}분 ${s}초" else "${m}분"
            else -> "${s}초"
        }
    }

    /** 방송을 켤 때 — 행사를 엶 */
    @Synchronized fun sijak(ctx: Context, jemok0: String, haeseolsa: String) {
        if (sid.isNotEmpty()) return
        try {
            val now = System.currentTimeMillis()
            val c = kst(now)
            val s = String.format("byod%02d%02d%02d%02d%02d%02d", c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
            val d = File(Haengsa.teo(ctx), s).also { it.mkdirs() }
            val t = now / 1000.0
            val jemok = jemok0.ifEmpty { "현장영상해설 방송 " + nalMal(t) + " " + sigakMal(t) }
            g = JSONObject().apply {
                put("sid", s); put("jemok", jemok); put("haeseolsa", haeseolsa)
                put("sijak", t); put("kkeut", 0); put("majimak", t)
                put("bun", JSONArray()); put("yocheong", JSONArray())
                put("kkeunkim", JSONArray().put(JSONArray().put(t).put("butum").put("아이피타임 방식")))
                put("jogak", JSONArray()); put("sangtae", "jinhaeng"); put("olim", 0); put("hyeongsik", "mulaw")
                put("pan", "LVD 송출 " + Bang.PAN); put("bangsik", "iptime"); put("chobeon_deut", 0)
            }
            teo = d; sid = s; f = null; fTtae = 0L; jeojangTtae = now; bunTtae = now
            yocheongBon = Bang.yocheongSeq; sseunBaiteu = 0L
            jeojang()
        } catch (_: Exception) { sid = "" }
    }

    /** 해설 소리 한 조각(0.1초) — 소리 일꾼 실 위에서 부름 */
    @Synchronized fun sseugi(b: ByteArray) {
        val gg = g ?: return
        val d = teo ?: return
        val now = System.currentTimeMillis()
        try {
            if (f == null || now - fTtae >= JOGAK_CHO) {
                jogakDatgi()
                val l = gg.getJSONArray("jogak")
                val nm = String.format("%04d.ul", l.length() + 1)
                f = FileOutputStream(File(d, nm), true)
                fTtae = now
                l.put(nm)
                jeojang()
            }
            f?.write(b)
            sseunBaiteu += b.size
            gg.put("majimak", now / 1000.0)
            if (now - jeojangTtae >= 10_000) {
                jeojangTtae = now
                try { f?.flush(); f?.fd?.sync() } catch (_: Exception) { }
                jeojang()
            }
            // 1분마다 듣는 분 수
            if (now - bunTtae >= 60_000) {
                bunTtae = now
                gg.getJSONArray("bun").put(JSONArray().put(now / 1000.0).put(Bang.deutnunSu))
                jeojang()
            }
            // 새 도움 요청
            val seq = Bang.yocheongSeq
            if (seq != yocheongBon) {
                val sae = seq - yocheongBon
                yocheongBon = seq
                val l = Bang.yocheong.toList().takeLast(maxOf(0, minOf(sae, 200)))
                for (y in l) gg.getJSONArray("yocheong").put(JSONArray().put(y.ttae / 1000.0).put(y.jongryu).put(y.beonho))
                jeojang()
            }
        } catch (_: Exception) { }
    }

    private fun jogakDatgi() {
        val x = f ?: return
        try { x.flush(); x.fd.sync(); x.close() } catch (_: Exception) { }
        f = null
    }

    private fun jeojang() {
        val gg = g ?: return
        val d = teo ?: return
        try {
            val tmp = File(d, "girok_jinhaeng.json.tmp")
            FileOutputStream(tmp).use { o -> o.write(gg.toString().toByteArray(Charsets.UTF_8)); o.flush(); o.fd.sync() }
            tmp.renameTo(File(d, "girok_jinhaeng.json"))
        } catch (_: Exception) { }
    }

    /** 방송을 멈출 때 — 마감(녹음 한 덩어리·결과보고·girok.json). 돌려주는 값: 마감한 행사 sid(없으면 빈 글) */
    @Synchronized fun maegam(): String {
        val gg = g ?: return ""
        val d = teo ?: return ""
        jogakDatgi()
        val s = sid
        g = null; teo = null; sid = ""
        return if (maegamHagi(d, gg)) s else ""
    }

    /** 조각을 이어 녹음 한 덩어리로, 결과보고와 girok.json 을 씀 */
    private fun maegamHagi(d: File, gg: JSONObject): Boolean {
        return try {
            gg.put("kkeut", gg.optDouble("majimak", gg.optDouble("sijak")))
            gg.put("sangtae", "kkeut")
            val l = gg.getJSONArray("jogak")
            var hap = 0L
            val tmp = File(d, "nokeum.ul.tmp")
            FileOutputStream(tmp).use { o ->
                for (i in 0 until l.length()) {
                    val j = File(d, l.getString(i))
                    if (j.isFile) { j.inputStream().use { it.copyTo(o, 65536) }; hap += j.length() }
                }
                o.flush(); o.fd.sync()
            }
            tmp.renameTo(File(d, "nokeum.ul"))
            gg.put("gilee", hap / BYTE_CHO)
            File(d, "bogo.txt").writeBytes(bogoMandeul(gg).replace("\n", "\r\n").toByteArray(charset("EUC-KR")))
            File(d, "girok.json").writeText(gg.toString(), Charsets.UTF_8)   // 마지막에 써서, 다 된 것만 목록에 나오게
            File(d, "girok_jinhaeng.json").delete()
            true
        } catch (_: Exception) { false }
    }

    /** 방송 중에 기기가 꺼져 마감하지 못한 행사를 남은 조각으로 마감(앱이 켜질 때·올리기 전에 부름) */
    @Synchronized fun namunGeotMaegam(ctx: Context) {
        try {
            for (d in Haengsa.teo(ctx).listFiles() ?: emptyArray()) {
                if (d.name == sid) continue
                val jin = File(d, "girok_jinhaeng.json")
                if (!jin.isFile || File(d, "girok.json").isFile) continue
                val gg = JSONObject(jin.readText(Charsets.UTF_8))
                gg.getJSONArray("kkeunkim").put(JSONArray().put(gg.optDouble("majimak")).put("kkeunkim").put("기기가 꺼짐"))
                maegamHagi(d, gg)
            }
        } catch (_: Exception) { }
    }

    /** 한 장짜리 결과보고 — 공유기 서버의 결과보고와 같은 꼴, 낭독기와 점자정보단말기로 읽히게 기호 없이 */
    private fun bogoMandeul(g: JSONObject): String {
        val L = ArrayList<String>()
        val sijak = g.optDouble("sijak"); val kkeut = g.optDouble("kkeut")
        L.add("현장영상해설 방송 결과보고")
        L.add("사단법인 한국시각장애인현장영상해설협회")
        L.add("")
        L.add("행사 이름: " + g.optString("jemok"))
        L.add("날짜: " + nalMal(sijak))
        L.add("현장영상해설사: " + g.optString("haeseolsa").ifEmpty { "적지 않음" })
        L.add("시작: " + sigakMal(sijak))
        L.add("끝: " + sigakMal(kkeut))
        L.add("방송 시간: " + gileeMal(maxOf(0.0, kkeut - sijak)))
        val bun = g.optJSONArray("bun") ?: JSONArray()
        if (bun.length() > 0) {
            var mx = bun.getJSONArray(0); var hap = 0.0
            for (i in 0 until bun.length()) { val x = bun.getJSONArray(i); hap += x.getInt(1); if (x.getInt(1) > mx.getInt(1)) mx = x }
            L.add("듣는 분: 가장 많을 때 " + mx.getInt(1) + "명(" + sigakMal(mx.getDouble(0)) + "), 평균 " + Math.round(hap / bun.length()) + "명")
            L.add("")
            L.add("10분 간격 듣는 분 수")
            var dah = sijak
            for (i in 0 until bun.length()) {
                val x = bun.getJSONArray(i)
                if (x.getDouble(0) >= dah) { L.add(sigakMal(x.getDouble(0)) + " " + x.getInt(1) + "명"); dah = x.getDouble(0) + 600 }
            }
        } else L.add("듣는 분: 기록 없음")
        val yc = g.optJSONArray("yocheong") ?: JSONArray()
        L.add("")
        if (yc.length() > 0) {
            val se = LinkedHashMap<String, Int>()
            for (i in 0 until yc.length()) { val k = yc.getJSONArray(i).getString(1); se[k] = (se[k] ?: 0) + 1 }
            L.add("도움 요청: 모두 " + yc.length() + "번 (" + se.entries.joinToString(", ") { Bang.jongryuMal(it.key) + " " + it.value + "번" } + ")")
            for (i in 0 until yc.length()) {
                val x = yc.getJSONArray(i); val n = x.optString(2)
                L.add(sigakMal(x.getDouble(0)) + " " + Bang.jongryuMal(x.getString(1)) + " (" + (if (n.isNotEmpty()) n + "번" else "번호 없음") + ")")
            }
        } else L.add("도움 요청: 없음")
        val kk = g.optJSONArray("kkeunkim") ?: JSONArray()
        var kkSu = 0
        for (i in 0 until kk.length()) if (kk.getJSONArray(i).getString(1) == "kkeunkim") kkSu++
        L.add("")
        L.add("방송 기기 끊김: " + (if (kkSu == 0) "없음" else "${kkSu}번"))
        for (i in 0 until kk.length()) {
            val x = kk.getJSONArray(i)
            if (x.getString(1) == "kkeunkim") L.add(sigakMal(x.getDouble(0)) + " 끊김 (" + x.optString(2) + ")")
        }
        L.add("")
        L.add("녹음: " + g.optString("sid") + ".ul, 길이 " + gileeMal(g.optDouble("gilee", 0.0)))
        L.add("LVD 송출 " + Bang.PAN + "판, 아이피타임 방식(이 기기가 녹음)")
        return L.joinToString("\n") + "\n"
    }
}
