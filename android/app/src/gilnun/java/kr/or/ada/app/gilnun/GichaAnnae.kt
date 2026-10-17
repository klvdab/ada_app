// 기차 안내 — 2.61.0 (261017-A49, 이사장님 승인 2026-10-17 「그래 모두 승인」, 아이폰 2.91.0 GichaAnnae.swift 와 같음)
// 웹 길눈(chatta.html)에서 하던 기차 안내(2026-09-19 이사장님 지시)와 터널 미리 알림(2026-09-14 이사장님 지시)을
// 앱으로 옮기지 못해, KTX 부산행에서 남은 거리와 고장 이름만 말하던 일을 바로잡음.
//   역에 다가가면 「곧 ○○역에 도착합니다」, 역에 서면 「○○역에 도착했습니다」, 떠나면 「○○역을 떠났습니다. 다음 역은」,
//   지나치면 「지금 ○○역을 지나고 있습니다」, 내리실 역이면 「여기서 내리십니다」.
//   터널은 들어가기 전에 「곧 터널입니다」 — 협회 서버 cheolteoneol.php(오픈스트리트맵 기찻길 터널).
//   열차 시간표(공공데이터 TrainInfo, 협회 서버 gyotong.php)로 타신 열차를 맞춰 다음 역과 내리실 역의 도착 예정 시각을 함께.
package kr.or.ada.app.gilnun

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GichaAnnae {
    /** 2.66.0 지금 시각 — 여정 다시 돌려 보기 시험(tests/gicha)에서 기록의 시각을 넣으려고 바꿀 수 있게 함. 앱에서는 늘 지금 */
    var sigye: () -> Long = { System.currentTimeMillis() }
    private data class Yeok(val ireum: String, val lat: Double, val lon: Double)
    private data class Teoneol(val ireum: String, val m: Int, val a: Pair<Double, Double>, val b: Pair<Double, Double>)
    private data class Yeolcha(val beon: String, val jong: String, val chul: String, val chulId: String, val doIreum: String, val doTtae: Long)
    private data class Fx(val t: Long, val lat: Double, val lon: Double)

    var kyeojim = false
        private set
    private val fx = ArrayList<Fx>()
    private val vh = ArrayList<Pair<Long, Double>>()
    private var yeok: List<Yeok> = emptyList()
    private var yeokQ: Pair<Double, Double>? = null
    private var yeokQTtae = 0L
    private var yeokBatneun = false
    private var mokYeok: Yeok? = null
    private var naerilM = 8000.0
    private val said = HashMap<String, Long>()
    /** 2.67.0 역마다 가장 가까웠던 거리 — 「지나고 있습니다」는 역을 실제로 지날 때만(아이폰과 같음) */
    private val gajangGakka = HashMap<String, Double>()
    private val got = HashMap<String, Long>()
    private var stopTtae = 0L
    private var stopSaid = ""
    private var tteonSaid = ""
    private var head: Double? = null
    private var headFrom: Pair<Double, Double>? = null
    private var teoneol: List<Teoneol> = emptyList()
    private var teoneolQ: Pair<Double, Double>? = null
    private var teoneolQTtae = 0L
    private var teoneolBatneun = false
    private val teoneolSaid = HashSet<String>()
    private var yeolcha: Yeolcha? = null
    private var yeolchaChatneun = false
    private val yeolchaChateun = HashSet<String>()
    private val modu = HashMap<String, String>()
    private var namTtae = 0L
    private val daeumMalhan = HashSet<String>()
    private val yeokSigakPyo = HashMap<String, Long>()

    fun kkeut() {
        if (!kyeojim) return
        kyeojim = false
        fx.clear(); vh.clear(); said.clear(); gajangGakka.clear(); got.clear(); stopTtae = 0L; stopSaid = ""; tteonSaid = ""
        head = null; headFrom = null; teoneolSaid.clear(); yeolcha = null; yeolchaChateun.clear(); mokYeok = null
        daeumMalhan.clear(); yeokSigakPyo.clear()
        Girok.namgi("gicha_annae_kkeut")
    }

    /** AnnaeEngine 이 기차 안(타고 가는 중·기차)일 때 자리마다 부름 */
    fun wichi(w: Jari, y: Yeojeong) {
        if (!kyeojim) {
            kyeojim = true
            namTtae = sigye()
            Girok.namgi("gicha_annae_sijak")
            mal("기차 안내를 시작합니다. 역에 다가가거나 서고 떠날 때, 터널에 들어가기 전에 알려 드립니다.")
        }
        salpim(w, y)
    }

    private fun mal(t: String) {
        Sori.mal(t, MalGeup.ANNAE)
        Girok.namgi("gicha_mal", mapOf("mal" to t))
    }

    private fun geori(a: Pair<Double, Double>, b: Pair<Double, Double>) = Wichi.geori(a.first, a.second, b.first, b.second)
    private fun bangwi(a: Pair<Double, Double>, b: Pair<Double, Double>) = Jeomjido.bangwi(a.first, a.second, b.first, b.second)
    private fun gakCha(a: Double, b: Double) = Math.abs(((a - b + 540) % 360) - 180)

    // MARK: 살피기 — 웹 길눈 gichaSalpim 을 그대로 옮김

    private fun salpim(w: Jari, y: Yeojeong) {
        val now = sigye()
        val me = Pair(w.lat, w.lon)
        if (w.ochae <= 300) fx.add(Fx(now, w.lat, w.lon))
        fx.removeAll { now - it.t > 60_000 }
        var v = 0.0
        if (fx.size >= 2) {
            val a = fx.first(); val b = fx.last()
            val dt = (b.t - a.t) / 1000.0
            if (dt >= 20) v = geori(Pair(a.lat, a.lon), Pair(b.lat, b.lon)) / dt
        }
        val pv: Double? = if (w.sokdo >= 0) w.sokdo else null
        val sfx = fx.filter { now - it.t <= 20_000 }
        var sv: Double? = null
        if (sfx.size >= 2) {
            val a = sfx.first(); val b = sfx.last()
            val dt = (b.t - a.t) / 1000.0
            if (dt >= 10) sv = geori(Pair(a.lat, a.lon), Pair(b.lat, b.lon)) / dt
        }
        if (sv == null) sv = pv
        sv?.let { vh.add(Pair(now, it)) }
        vh.removeAll { now - it.first > 180_000 }
        val vmax = vh.maxOfOrNull { it.second } ?: 0.0

        val hf = headFrom
        if (hf != null) {
            if (geori(hf, me) > 300) { head = bangwi(hf, me); headFrom = me }
        } else headFrom = me
        if (head == null && w.banghyang >= 0 && (pv ?: 0.0) > 5) head = w.banghyang

        val yq = yeokQ
        if (yq == null || now - yeokQTtae > 120_000 || geori(yq, me) > 8000) yeokBatgi(me)
        if (mokYeok == null) mokYeokJeonghagi(y)
        val tq = teoneolQ
        if (tq == null || now - teoneolQTtae > 180_000 || geori(tq, me) > 3000) teoneolBatgi(me)

        val s0 = sv
        if (s0 != null && s0 > 8) teoneolBoda(me, s0)

        var near: Pair<Yeok, Double>? = null
        for (k in yeok) {
            val m = geori(me, Pair(k.lat, k.lon))
            if (near == null || m < near.second) near = Pair(k, m)
        }
        val seo = (sv?.let { it < 1.5 } ?: false) && (pv?.let { it < 1.5 } ?: true)
        if (seo) {
            if (stopTtae == 0L) stopTtae = now
            val n = near
            if (now - stopTtae >= 10_000 && n != null && n.second < 1500 && stopSaid != n.first.ireum && yeokSeoji.contains(n.first.ireum)) {
                // 2.66.0 시간표로 서지 않는 역 — 가까이에서 멈춰도 도착이 아님
                if (said["멈춤:" + n.first.ireum] == null) {
                    said["멈춤:" + n.first.ireum] = now
                    mal("${n.first.ireum} 가까이에서 잠시 멈췄습니다. 타신 열차는 이 역에 서지 않으니 그대로 계십시오.")
                }
                return
            }
            if (now - stopTtae >= 10_000 && n != null && n.second < 1500 && stopSaid != n.first.ireum) {
                stopSaid = n.first.ireum; tteonSaid = ""
                said[n.first.ireum] = now; got[n.first.ireum] = now
                when (naeril(n.first)) {
                    true -> mal("${n.first.ireum}에 도착했습니다. 여기서 내리십시오.${namMal(me, y)} 내리시면 맨 위의 내렸습니다를 누르십시오.")
                    false -> mal("${n.first.ireum}에 도착했습니다. 내리실 역이 아니니 그대로 계십시오.${namMal(me, y)}")
                    null -> mal("${n.first.ireum}에 도착했습니다.")
                }
            }
            return
        }
        stopTtae = 0L
        if (stopSaid.isNotEmpty() && tteonSaid != stopSaid && s0 != null && s0 >= 4) {
            tteonSaid = stopSaid
            val tteonYeok = stopSaid
            val dm = daeum(me)
            var t = "${tteonYeok}을 떠났습니다."
            if (dm != null) t += " 가는 쪽으로 다음 역은 ${dm.first.ireum}, 약 ${Annae.geoMal(dm.second)}입니다."
            mal(t + namMal(me, y))
            yeolchaMatchugi(tteonYeok, now, dm?.first)
            return
        }
        var ap: Pair<Yeok, Double>? = null
        val h = head
        if (h != null) {
            for (k in yeok) {
                val m = geori(me, Pair(k.lat, k.lon))
                if (m >= 4000) continue
                val df = gakCha(bangwi(me, Pair(k.lat, k.lon)), h)
                if (df > 60 && m > 300) continue
                if (ap == null || m < ap.second) ap = Pair(k, m)
            }
        }
        if (ap != null && got[ap.first.ireum] == null && !yeokSeoji.contains(ap.first.ireum) && s0 != null && vmax > 20 && s0 < vmax * 0.65 && s0 < 25) {
            got[ap.first.ireum] = now; said[ap.first.ireum] = now
            val si = yeokSigak(ap.first)
            when (naeril(ap.first)) {
                true -> mal("곧 ${ap.first.ireum}에 도착합니다. 여기서 내리십니다. 내릴 준비를 하십시오.")
                false -> mal("곧 ${ap.first.ireum}에 섭니다. 내리실 역은 아닙니다.$si")
                null -> mal("곧 ${ap.first.ireum}에 도착합니다.$si")
            }
            return
        }
        val n = near
        // 2.67.0 (시험에서 찾음) 역 1.2킬로미터 앞에서 「지나고 있습니다」라고 하던 일 — 가장 가까웠던 거리보다 80미터 넘게 멀어지고 초속 8미터 넘게 달릴 때만.
        //   250미터 안이면 바로 말하던 것은 뺌 — 서려고 속도를 줄이며 역에 들어설 때 먼저 나왔음(광명)
        if (n != null && n.second < 1200) gajangGakka[n.first.ireum] = Math.min(gajangGakka[n.first.ireum] ?: Double.MAX_VALUE, n.second)
        if (n != null && n.second < 1200 && said[n.first.ireum] == null && (sv ?: 0.0) > 8 && n.second > (gajangGakka[n.first.ireum] ?: n.second) + 80) {
            said[n.first.ireum] = now
            var t = "지금 ${n.first.ireum}을 지나고 있습니다."
            val dm = daeum(me)
            if (dm != null) t += " 가는 쪽으로 다음 역은 ${dm.first.ireum}, 약 ${Annae.geoMal(dm.second)}입니다."
            mal(t)
            // 2.66.0 서지 않고 지나는 역에서도 다음 역을 시간표로 봄(아이폰과 같음)
            val yc = yeolcha
            if (yc != null && dm != null) daeumSigakMal(yc, dm.first)
            return
        }
        if (v > 10 && now - namTtae > 600_000) {
            namTtae = now
            var t = "기차로 가시는 중입니다." + namMal(me, y)
            val yc = yeolcha
            if (yc != null) {
                t += if (now > yc.doTtae + 60_000) " ${yc.doIreum} 도착 예정 시각 ${sigakMal(yc.doTtae)}을 조금 넘겼습니다. 열차가 늦어지고 있습니다."
                     else " ${yc.doIreum} 도착 예정은 ${sigakMal(yc.doTtae)}입니다."
            }
            else {
                val bun = Math.round(geori(me, Pair(y.mokjeok.lat, y.mokjeok.lon)) / v / 60).toInt()
                if (bun > 0) t += " 지금 빠르기로 곧장 가면 약 ${bun}분 거리입니다."
            }
            mal(t)
        }
    }

    private fun namMal(me: Pair<Double, Double>, y: Yeojeong) =
        " ${y.mokjeok.ireum}까지 곧장 잰 거리로 ${Annae.geoMal(geori(me, Pair(y.mokjeok.lat, y.mokjeok.lon)))} 남았습니다."

    private fun daeum(me: Pair<Double, Double>): Pair<Yeok, Double>? {
        val h = head ?: return null
        var best: Pair<Yeok, Double>? = null
        for (k in yeok) {
            val m = geori(me, Pair(k.lat, k.lon))
            if (m < 2500) continue
            if (gakCha(bangwi(me, Pair(k.lat, k.lon)), h) > 35) continue
            if (best == null || m < best.second) best = Pair(k, m)
        }
        return best
    }

    private fun naeril(k: Yeok): Boolean? {
        YeojeongEngine.jigeum ?: return null
        // 2.66.0 (261017-A54, 이사장님 KTX에서 겪으심 2026-10-17, 아이폰과 같음) 목적지 8킬로미터 안의 역을 모두 내릴 역으로 보아
        //   서울역에 가시는데 영등포역·용산역에서 「여기서 내리십시오」라고 하던 일 — 내릴 역은 한 곳만
        if (yeokSeoji.contains(k.ireum)) return false
        val yc = yeolcha
        if (yc != null) return yeokIreumMan(k.ireum) == yeokIreumMan(yc.doIreum)
        val mk = mokYeok ?: return null
        return yeokIreumMan(k.ireum) == yeokIreumMan(mk.ireum) || geori(Pair(k.lat, k.lon), Pair(mk.lat, mk.lon)) < 300
    }
    private fun yeokIreumMan(n: String) = if (n.endsWith("역")) n.dropLast(1) else n
    private val yeokSeoji = HashSet<String>()

    private fun mokYeokJeonghagi(y: Yeojeong) {
        var best: Pair<Yeok, Double>? = null
        for (g in GichaYeok.mokrok) {
            val m = Wichi.geori(y.mokjeok.lat, y.mokjeok.lon, g.lat, g.lon)
            if (best == null || m < best.second) best = Pair(Yeok(g.ireum + "역", g.lat, g.lon), m)
        }
        val b = best ?: return
        mokYeok = b.first
        naerilM = Math.max(8000.0, b.second * 2)
    }

    // MARK: 역 목록

    private val yeokMoyang = Regex("^[^\\s]+역(\\s+[^\\s]+선)?$")
    private val yeokBbaem = Regex("폐역|화물|신호장|기지|호선")

    private fun yeokBatgi(me: Pair<Double, Double>) {
        if (yeokBatneun) return
        yeokBatneun = true
        yeokQ = me; yeokQTtae = sigye()
        val mok = LinkedHashMap<String, Yeok>()
        for (g in GichaYeok.mokrok) if (Wichi.geori(me.first, me.second, g.lat, g.lon) <= 30_000) mok[g.ireum + "역"] = Yeok(g.ireum + "역", g.lat, g.lon)
        if (yeok.isEmpty()) yeok = mok.values.toList()
        val q = mapOf("a" to "chatgi", "q" to "기차역", "m" to "20000", "lat" to String.format(Locale.US, "%.6f", me.first), "lon" to String.format(Locale.US, "%.6f", me.second))
        Tongsin.json("dulle.php", q) { o ->
            yeokBatneun = false
            val rows = o?.optJSONArray("rows")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    var n = r.optString("ireum").trim()
                    val la = r.optDouble("lat", Double.NaN); val lo = r.optDouble("lon", Double.NaN)
                    if (n.isEmpty() || la.isNaN() || lo.isNaN()) continue
                    if (yeokBbaem.containsMatchIn(n) || !yeokMoyang.matches(n)) continue
                    val sp = n.indexOf(' ')
                    if (sp > 0) n = n.substring(0, sp)
                    if (!mok.containsKey(n)) mok[n] = Yeok(n, la, lo)
                }
            }
            yeok = mok.values.toList()
        }
    }

    // MARK: 터널 미리 알림

    private fun teoneolBatgi(me: Pair<Double, Double>) {
        if (teoneolBatneun) return
        teoneolBatneun = true
        teoneolQ = me; teoneolQTtae = sigye()
        val q = mapOf("lat" to String.format(Locale.US, "%.6f", me.first), "lon" to String.format(Locale.US, "%.6f", me.second), "ban" to "8000")
        Tongsin.json("cheolteoneol.php", q) { o ->
            teoneolBatneun = false
            val rows = o?.optJSONArray("rows") ?: return@json
            val l = ArrayList<Teoneol>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val a = r.optJSONArray("a") ?: continue
                val b = r.optJSONArray("b") ?: continue
                if (a.length() != 2 || b.length() != 2) continue
                l.add(Teoneol(r.optString("ireum"), r.optInt("m"), Pair(a.optDouble(0), a.optDouble(1)), Pair(b.optDouble(0), b.optDouble(1))))
            }
            teoneol = l
        }
    }

    private fun teoneolBoda(me: Pair<Double, Double>, sv: Double) {
        val h = head ?: return
        val ap = Math.max(1500.0, sv * 45)
        for (t in teoneol) {
            val k = "${t.a.first},${t.a.second}"
            if (teoneolSaid.contains(k)) continue
            val da = geori(me, t.a); val db = geori(me, t.b)
            val ip = if (da < db) t.a else t.b
            val dIp = Math.min(da, db)
            if (dIp < 120 || dIp > ap) continue
            if (gakCha(bangwi(me, ip), h) > 30) continue
            // 2.66.0 터널이 가는 쪽으로 뻗어야 함 — 서울역·용산역 둘레에서 갈라져 나가는 다른 노선 터널을 잘못 알리던 일
            val meonKkeut = if (da < db) t.b else t.a
            if (t.m >= 300 && gakCha(bangwi(ip, meonKkeut), h) > 45) continue
            teoneolSaid.add(k)
            for (u in teoneol) if (geori(u.a, t.a) < 300 || geori(u.b, t.b) < 300 || geori(u.a, t.b) < 300 || geori(u.b, t.a) < 300) teoneolSaid.add("${u.a.first},${u.a.second}")
            var m = if (t.ireum.isNotEmpty()) "곧 ${t.ireum}입니다." else "곧 터널입니다."
            if (t.m >= 100) m += " 길이 약 ${Annae.geoMal(t.m.toDouble())}"
            if (t.m >= 2000 && sv > 10) {
                val bun = Math.round(t.m / sv / 60).toInt()
                m += if (bun >= 1) ", 약 ${bun}분 동안 터널 안을 지납니다." else "입니다."
            } else if (t.m >= 100) m += "입니다."
            mal(m)
            return
        }
    }

    // MARK: 열차 시간표로 도착 예정 시각

    private fun pyo(f: String) = SimpleDateFormat(f, Locale.KOREA).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }

    /** 2.62.0 말로 하기 「얼마나 걸려」에서 씀 — 맞춘 열차의 내리실 역 도착 예정 */
    val dochakYejeong: Pair<String, Long>? get() = yeolcha?.let { Pair(it.doIreum, it.doTtae) }
    fun sigakMalHagi(t: Long) = sigakMal(t)

    private fun sigakMal(t: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")); c.timeInMillis = t
        val h = c.get(Calendar.HOUR_OF_DAY); val m = c.get(Calendar.MINUTE)
        return if (m == 0) "${h}시" else "${h}시 ${m}분"
    }

    private fun yeokIreum(n: String) = if (n.endsWith("역")) n.dropLast(1) else n

    private fun sigakBatgi(s: String): Long? = try { pyo("yyyyMMddHHmmss").parse(s)?.time } catch (e: Exception) { null }

    private fun moduBatgi(kkeut: () -> Unit) {
        if (modu.isNotEmpty()) { kkeut(); return }
        Tongsin.json("gyotong.php", mapOf("a" to "modu", "gal" to "gicha"), 15000) { o ->
            val rows = o?.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("id"); val n = r.optString("ireum")
                if (id.isNotEmpty() && n.isNotEmpty() && !modu.containsKey(n)) modu[n] = id
            }
            kkeut()
        }
    }

    private fun pyeon(chulId: String, doId: String, kkeut: (JSONArray?) -> Unit) {
        val q = mapOf("a" to "pyeon", "gal" to "gicha", "chul" to chulId, "do" to doId, "nal" to pyo("yyyyMMdd").format(Date()))
        Tongsin.json("gyotong.php", q, 15000) { o -> kkeut(o?.optJSONArray("rows")) }
    }

    private fun yeolchaMatchugi(tteon: String, ttae: Long, daeumYeok: Yeok?) {
        val yc0 = yeolcha
        if (yc0 != null) { if (daeumYeok != null) daeumSigakMal(yc0, daeumYeok); return }
        val mk = mokYeok ?: return
        if (yeolchaChatneun || yeolchaChateun.contains(tteon) || mk.ireum == tteon) return
        yeolchaChatneun = true
        yeolchaChateun.add(tteon)
        moduBatgi {
            val chul = yeokIreum(tteon); val doN = yeokIreum(mk.ireum)
            val cid = modu[chul]; val did = modu[doN]
            if (cid == null || did == null) { yeolchaChatneun = false; return@moduBatgi }
            pyeon(cid, did) { rows ->
                yeolchaChatneun = false
                if (rows == null) return@pyeon
                var best: JSONObject? = null; var bestCha = 0.0
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val dt = sigakBatgi(r.optString("depplandtime")) ?: continue
                    val cha = (ttae - dt) / 1000.0
                    if (cha < -180 || cha > 1200) continue
                    if (best == null || Math.abs(cha) < Math.abs(bestCha)) { best = r; bestCha = cha }
                }
                val b = best
                val at = b?.let { sigakBatgi(it.optString("arrplandtime")) }
                if (b == null || at == null) {
                    Girok.namgi("gicha_yeolcha_mot", mapOf("chul" to chul, "do" to doN, "su" to rows.length()))
                    return@pyeon
                }
                val beon = b.optString("trainno"); val jong = b.optString("traingradename", "열차")
                val yc = Yeolcha(beon, jong, chul, cid, mk.ireum, at)
                yeolcha = yc
                val bn = beon.toIntOrNull() ?: 0
                Girok.namgi("gicha_yeolcha", mapOf("beon" to beon, "jong" to jong, "chul" to chul, "do" to doN))
                mal("타신 열차는 $jong ${if (bn > 0) "${bn}호" else ""}로 보입니다. ${mk.ireum} 도착 예정은 ${sigakMal(at)}입니다.")
                if (daeumYeok != null && daeumYeok.ireum != mk.ireum) daeumSigakMal(yc, daeumYeok)
            }
        }
    }

    private fun daeumSigakMal(yc: Yeolcha, dy: Yeok) {
        val n = yeokIreum(dy.ireum)
        val did = modu[n] ?: return
        if (daeumMalhan.contains(n)) return
        daeumMalhan.add(n)
        pyeon(yc.chulId, did) { rows ->
            if (rows == null) return@pyeon
            var gat: JSONObject? = null
            for (i in 0 until rows.length()) { val r = rows.optJSONObject(i) ?: continue; if (r.optString("trainno") == yc.beon) { gat = r; break } }
            val t = gat?.let { sigakBatgi(it.optString("arrplandtime")) }
            if (t != null) {
                yeokSigakPyo[dy.ireum] = t
                mal("${dy.ireum} 도착 예정은 ${sigakMal(t)}입니다.")
            } else if (rows.length() > 0) {
                yeokSeoji.add(dy.ireum)
                mal("타신 열차는 ${dy.ireum}에 서지 않고 지나갑니다.")
            }
        }
    }

    private fun yeokSigak(k: Yeok): String {
        val t = yeokSigakPyo[k.ireum] ?: return ""
        return " 도착 예정은 ${sigakMal(t)}입니다."
    }
}
