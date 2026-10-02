// 안드로이드 길눈 — 나만의 점지도·내 문·점지도 설정(2.7.0, 묶음 b2 점지도 마저, 대표님 지시)
// 아이폰 Jeomjido.swift 의 NaeGil·NaeMun, JeomView.swift 의 NaeGeurigi 를 같은 꼴, 같은 말로 옮겼습니다.
//   나만의 점지도는 폰 안(앱 자료 칸 gilnun_naegil.json)에 둡니다. 원하실 때만 잠금말을 걸어 협회 서버에 맡깁니다.
//   맡기기 — 웹·아이폰과 같은 방식: 잠금말에서 PBKDF2(HMAC-SHA256, 12만 번, 소금 "jeomnae-salt-2026")로 열쇠를 만들고
//     AES-GCM 으로 잠금. 보내는 꼴은 base64(iv) + "." + base64(암호문+꼬리표). 서버에는 잠금말의 지문(SHA-256)만 갑니다.
//     그래서 아이폰·웹 길눈에서 맡긴 길을 안드로이드에서 같은 잠금말로 찾아오고, 그 반대도 됩니다.
//   잠금말은 폰의 열쇠 칸(안드로이드 키스토어로 잠근 값)에만 둡니다.
//   내 문 — 지금 선 자리를 내 문으로(두 번 찍기: 문 두 걸음 앞에서 한 번, 문을 지나 두 걸음 들어가서 한 번)
//   카메라 문 찾기(문 둘레 글자 읽기)는 묶음 3이 JeomMunKamera 에 이어 붙입니다 — 없으면 위성 자리만으로 담음
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 2.7.0 점지도 설정 — 폰에 담아 둠. 점지도 묶음만 쓰는 것은 "gilnun_jeom" 칸에,
 *  다른 묶음(안내 엔진 AnnaeSeoljeong·설정 SeoljeongDeo)과 함께 쓰는 확신음·지나는 곳은 "gilnun" 칸의 같은 열쇠(hwaksinEum·gilOn)에 — 아이폰 Seoljeong 하나와 같게 */
object JeomSeol {
    private var d: SharedPreferences? = null
    private var g: SharedPreferences? = null
    fun sijak(c: Context) {
        if (d == null) d = c.applicationContext.getSharedPreferences("gilnun_jeom", Context.MODE_PRIVATE)
        if (g == null) g = c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
    }

    private fun b(k: String, mo: Boolean) = d?.getBoolean(k, mo) ?: mo
    private fun bSseugi(k: String, v: Boolean) { d?.edit()?.putBoolean(k, v)?.apply() }

    /** 걸을 때 확신음(처음부터 켜짐) — 끄면 점지도 알림 소리를 쉬고 말은 그대로(아이폰 hwaksinEum 과 같음). "gilnun" 칸 */
    var hwaksinEum: Boolean
        get() = g?.getBoolean("hwaksinEum", true) ?: true
        set(v) { g?.edit()?.putBoolean("hwaksinEum", v)?.apply() }
    /** "제대로 가고 있습니다" 간격(5·10·20미터, 처음 10) */
    var hwaksinGan: Int
        get() { val v = d?.getInt("hwaksinGan", 10) ?: 10; return if (v == 5 || v == 10 || v == 20) v else 10 }
        set(v) { d?.edit()?.putInt("hwaksinGan", v)?.apply() }
    /** 지나는 곳 안내(처음부터 켜짐). "gilnun" 칸 */
    var gilOn: Boolean
        get() = g?.getBoolean("gilOn", true) ?: true
        set(v) { g?.edit()?.putBoolean("gilOn", v)?.apply() }
    /** 안내 중 이어폰 단추 받기(처음부터 켜짐) */
    var ieoponDanchu: Boolean
        get() = b("ieoponDanchu", true)
        set(v) = bSseugi("ieoponDanchu", v)
    /** 폰 흔들기(처음에는 꺼짐) */
    var heundeulKyeojim: Boolean
        get() = b("heundeul", false)
        set(v) = bSseugi("heundeul", v)
    /** 흔들면 하는 일 — "gingeup" 긴급통화 열기, "jari" 내 자리 번호 말하기 */
    var heundeulIl: String
        get() = if (d?.getString("heundeulIl", "gingeup") == "jari") "jari" else "gingeup"
        set(v) { d?.edit()?.putString("heundeulIl", v)?.apply() }
    /** 리모컨 단추 — 자리("1","2","3") → 단추 이름 */
    var rimo: Map<String, String>
        get() {
            val s = d?.getString("rimo", null) ?: return emptyMap()
            return try {
                val o = JSONObject(s)
                o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotEmpty() }
            } catch (e: Exception) { emptyMap() }
        }
        set(v) { d?.edit()?.putString("rimo", JSONObject(v).toString())?.apply() }

    fun geul(k: String): String? = d?.getString(k, null)
    fun geulSseugi(k: String, v: String?) { d?.edit()?.apply { if (v == null) remove(k) else putString(k, v) }?.apply() }
}

/** 2.7.0 카메라 문 찾기 이음 자리 — 묶음 3(MunChatgi)이 채움. 비어 있으면 카메라 없이 위성 자리로만 */
object JeomMunKamera {
    /** 이 폰이 카메라로 문을 찾을 수 있는가 */
    var gigiGaneung: () -> Boolean = { false }
    /** 문 찾기 켜기 — eodiseo("munkkaji" 문까지 안내, "jjikgi" 내 문 두 번 찍기), gidae(찍어 두신 문의 이름과 글자 — 맞는 문인지 가림) */
    var kyeogi: ((eodiseo: String, gidae: Pair<String, List<String>>?) -> Unit)? = null
    /** 카메라가 지금 문을 보고 있는가(보고 있으면 문까지 안내의 위성 말을 쉼) */
    var munBoim: () -> Boolean = { false }
    /** 두 번 찍기를 마치며 카메라가 읽은 문 둘레 글자를 돌려받음(카메라를 끔) */
    var jjikgiKkeut: () -> List<String> = { emptyList() }
}

// MARK: 나만의 점지도

object NaeGil {
    private var pail: File? = null
    private var ctx: Context? = null
    private val il = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mok = ArrayList<JeomGil>()
    private var yeolsoeCache: Pair<String, ByteArray>? = null

    /** 담은 길(새 것이 앞) */
    val mokrok: List<JeomGil> get() = ArrayList(mok)

    fun sijak(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        JeomSeol.sijak(c)
        // 백업 제외 — 앱 자료 칸(noBackupFilesDir)에 둠(아이폰 isExcludedFromBackup 과 같음)
        val p = File(c.noBackupFilesDir, "gilnun_naegil.json")
        pail = p
        try {
            if (p.exists()) {
                val a = JSONArray(p.readText())
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { mok.add(JeomGil.batgi(it)) }
            }
        } catch (e: Exception) {
            Girok.namgi("naegil_ilgi_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    private fun jeojang() {
        val a = JSONArray()
        for (g in mok) a.put(g.json())
        val t = a.toString()
        try { pail?.writeText(t) } catch (e: Exception) { Girok.namgi("naegil_jeojang_oryu", mapOf("e" to (e.message ?: ""))) }
    }

    fun chatgi(id: String): JeomGil? = mok.firstOrNull { it.id == id }

    fun damgi(g: JeomGil) {
        val i = mok.indexOfFirst { it.id == g.id }
        if (i >= 0) mok[i] = g else mok.add(0, g)
        jeojang()
    }

    fun jiugi(id: String) {
        mok.removeAll { it.id == id }
        jeojang()
    }

    // 잠금말 — 폰의 열쇠 칸에만. 서버에는 잠금말의 지문(되돌릴 수 없는 값)만 갑니다.
    var jamgeum: String
        get() = KeuJamgeum.pulgi(JeomSeol.geul("naeGilJamgeum")) ?: ""
        set(v) { JeomSeol.geulSseugi("naeGilJamgeum", if (v.isEmpty()) null else KeuJamgeum.jamgeugi(v)) }

    fun jimun(lock: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(("jeomnae|$lock").toByteArray(Charsets.UTF_8))
        return h.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
    }

    /** 웹·아이폰과 같은 방식(PBKDF2 HMAC-SHA256 12만 번, 32바이트) — 화면 줄에서 부르지 말 것(오래 걸림) */
    fun yeolsoe(lock: String): ByteArray {
        val c = yeolsoeCache
        if (c != null && c.first == lock) return c.second
        val k = pbkdf2(lock.toByteArray(Charsets.UTF_8), "jeomnae-salt-2026".toByteArray(Charsets.UTF_8), 120000, 32)
        yeolsoeCache = Pair(lock, k)
        return k
    }

    /** PBKDF2(HMAC-SHA256) 를 손수 셈 — 안드로이드 판마다 다른 글자 바꿈 없이 웹·아이폰과 똑같은 바이트로 */
    private fun pbkdf2(pw: ByteArray, salt: ByteArray, iter: Int, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // 빈 잠금말은 받지 않으므로 열쇠는 늘 한 바이트 넘음(SecretKeySpec 은 빈 열쇠를 받지 않음)
        mac.init(SecretKeySpec(pw, "HmacSHA256"))
        val out = ByteArray(len)
        var block = 1
        var off = 0
        while (off < len) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (n in 1 until iter) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, len - off)
            System.arraycopy(t, 0, out, off, n)
            off += n
            block += 1
        }
        return out
    }

    /** 길을 잠금 — base64(iv).base64(암호문+꼬리표). 화면 줄 밖에서 */
    fun jamgeugi(g: JeomGil, lock: String): String? = try {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.ENCRYPT_MODE, SecretKeySpec(yeolsoe(lock), "AES"), GCMParameterSpec(128, iv))
        val ct = ci.doFinal(g.json().toString().toByteArray(Charsets.UTF_8))   // 안드로이드는 암호문 뒤에 꼬리표 16바이트를 붙여 줌
        Base64.encodeToString(iv, Base64.NO_WRAP) + "." + Base64.encodeToString(ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        Girok.namgi("naegil_jamgeum_oryu", mapOf("e" to (e.message ?: "")))
        null
    }

    /** 맡긴 길을 풂 — 잠금말이 다르면 null. 화면 줄 밖에서 */
    fun pulgi(blob: String, lock: String): JeomGil? {
        val p = blob.split(".")
        if (p.size != 2) return null
        return try {
            val iv = Base64.decode(p[0], Base64.DEFAULT)
            val ct = Base64.decode(p[1], Base64.DEFAULT)
            if (ct.size <= 16 || iv.isEmpty()) return null
            val ci = Cipher.getInstance("AES/GCM/NoPadding")
            ci.init(Cipher.DECRYPT_MODE, SecretKeySpec(yeolsoe(lock), "AES"), GCMParameterSpec(128, iv))
            val o = JSONObject(String(ci.doFinal(ct), Charsets.UTF_8))
            JeomGil.batgi(o).copy(nae = true, matgim = true, ollim = o.optBoolean("ollim", false))
        } catch (e: Exception) {
            null
        }
    }

    /** 맡기기 — 잠가서 jeom.php a=naeput 로. 결과는 화면 줄에서 */
    fun matgigi(g: JeomGil, lock: String, kkeut: (Boolean) -> Unit) {
        il.execute {
            val blob = jamgeugi(g, lock)
            if (blob == null) { main.post { kkeut(false) }; return@execute }
            Jeomjido.postJson("jeom.php", mapOf("a" to "naeput", "k" to jimun(lock)), JSONObject().put("id", g.id).put("blob", blob)) { o ->
                if (o == null || !o.optBoolean("ok", false)) { kkeut(false); return@postJson }
                damgi(g.copy(matgim = true))
                kkeut(true)
            }
        }
    }

    /** 맡겨 둔 길을 찾아옴 — (찾은 수, 풀지 못한 수), 못 받으면 null */
    fun chajaogi(lock: String, kkeut: (Pair<Int, Int>?) -> Unit) {
        Tongsin.json("jeom.php", mapOf("a" to "naelist", "k" to jimun(lock)), 20000) { o ->
            if (o == null) { kkeut(null); return@json }
            val blobs = ArrayList<String>()
            val rows = o.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { blobs.add(Jeomjido.gul(it, "blob")) }
            il.execute {
                val got = ArrayList<JeomGil>()
                var sal = 0
                for (b in blobs) { val g = pulgi(b, lock); if (g != null) got.add(g) else sal += 1 }
                main.post {
                    for (g in got) damgi(g)
                    kkeut(Pair(got.size, sal))
                }
            }
        }
    }

    fun matgimJiugi(id: String) {
        val lock = jamgeum
        jiugi(id)
        if (lock.isEmpty()) return
        Tongsin.json("jeom.php", mapOf("a" to "naedel", "k" to jimun(lock), "id" to id)) { }
    }
}

/** 잠금말을 폰의 열쇠 칸(안드로이드 키스토어 AES 열쇠)으로 잠가 둠 — 키스토어를 못 쓰는 폰은 앱 칸에 그대로(앱만 읽음) */
private object KeuJamgeum {
    private const val IREUM = "gilnun_naegil_jamgeum"

    private fun yeolsoe(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(IREUM, null) as? SecretKey) ?: run {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(KeyGenParameterSpec.Builder(IREUM, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            kg.generateKey()
        }
    } catch (e: Exception) { null }

    fun jamgeugi(t: String): String {
        val k = yeolsoe() ?: return "p:" + t
        return try {
            val ci = Cipher.getInstance("AES/GCM/NoPadding")
            ci.init(Cipher.ENCRYPT_MODE, k)
            "k:" + Base64.encodeToString(ci.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(ci.doFinal(t.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) { "p:" + t }
    }

    fun pulgi(s: String?): String? {
        if (s == null) return null
        if (s.startsWith("p:")) return s.substring(2)
        if (!s.startsWith("k:")) return null
        val p = s.substring(2).split(".")
        if (p.size != 2) return null
        val k = yeolsoe() ?: return null
        return try {
            val ci = Cipher.getInstance("AES/GCM/NoPadding")
            ci.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, Base64.decode(p[0], Base64.DEFAULT)))
            String(ci.doFinal(Base64.decode(p[1], Base64.DEFAULT)), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }
}

// MARK: 나만의 점지도를 그리는 일 — 화면을 떠나도 그리기는 이어짐(아이폰 NaeGeurigi)

object NaeGeurigi {
    var geurineun = false
        private set
    var damgilGeot = false
        private set
    private val pts0 = ArrayList<JeomJeom>()
    private val marks0 = ArrayList<JeomPyo>()
    val pts: List<JeomJeom> get() = ArrayList(pts0)
    val marks: List<JeomPyo> get() = ArrayList(marks0)
    private var t0 = 0L
    private var majimak = 0L
    private var deutgiDoem = false

    val gilLen: Double
        get() {
            var d = 0.0
            for (i in 1 until pts0.size) d += Wichi.geori(pts0[i - 1].lat, pts0[i - 1].lon, pts0[i].lat, pts0[i].lon)
            return d
        }

    private fun batda(w: Jari) {
        if (!geurineun || w.georeumChu) return
        val now = System.currentTimeMillis()
        if (now - majimak < 900) return
        majimak = now
        pts0.add(JeomJeom(w.lat, w.lon, Math.round(w.ochae * 10) / 10.0, if (w.banghyang >= 0) w.banghyang else 0.0,
            Math.round((now - t0) / 1000.0).toDouble()))
    }

    fun sijak() {
        pts0.clear(); marks0.clear(); t0 = System.currentTimeMillis(); majimak = 0L
        if (!deutgiDoem) { deutgiDoem = true; Wichi.deutgi { batda(it) } }
        Wichi.wiseongDolligi()
        geurineun = true
        damgilGeot = false
        Sori.mal("그리기 시작했습니다. 평소대로 걸으십시오.")
    }

    fun kkeut() {
        geurineun = false
        damgilGeot = pts0.size >= 3
        Sori.mal(if (pts0.size < 3) "점이 너무 적어 담을 수 없습니다. 다시 걸어 주십시오."
            else "걷기를 마쳤습니다. ${Jeomjido.bannol(gilLen).toInt()}미터, 표시 ${marks0.size}개입니다. 이 길을 어떻게 할까요. 이름을 적고 나만 쓰기나 모두가 쓰도록 점지도에 올리기를 고르십시오.")
    }

    fun sangtae() {
        val p = pts0.lastOrNull() ?: run { Sori.mal("아직 그리지 않았습니다."); return }
        Sori.mal("지금까지 ${Jeomjido.bannol(gilLen).toInt()}미터, 점 ${pts0.size}개, 표시 ${marks0.size}개입니다. 위치 오차는 ${Jeomjido.bannol(p.acc ?: 0.0).toInt()}미터입니다.")
    }

    fun pyo(nm: String, mal: String = "") {
        val p = pts0.lastOrNull() ?: run { Sori.mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        marks0.add(JeomPyo(p.lat, p.lon, nm, null, null, mal, p.t, p.acc, null))
        Sori.mal(nm + (if (mal.isEmpty()) "" else ", $mal") + " 남겼습니다. 모두 ${marks0.size}개입니다.")
    }

    fun gilMandeulgi(ireum: String): JeomGil {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        val now = Date()
        val id = "nae_" + SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).format(now)
        val nm = if (ireum.isEmpty()) "내 길 " + f.format(now) else ireum
        return JeomGil(id, nm, nm, "", "본인", f.format(now), Jeomjido.bannol(gilLen), pts, marks, nae = true)
    }

    fun biugi() { damgilGeot = false }
}

// MARK: 내 문 — 지금 선 자리를 내 문으로 담아 두면 문까지 안내에서 가장 먼저 씀(폰 안에만)

data class NaeMunHang(
    val id: String,
    val ireum: String,
    val lat: Double,
    val lon: Double,
    val bang: Double?,
    val made: String,
    val geul: List<String>? = null,   // 두 번 찍을 때 카메라가 읽은 문 둘레 글자(호수 등) — 사진은 담지 않음
    val jjak: Boolean? = null         // 두 번 찍어 담은 문
) {
    fun json(): JSONObject {
        val o = JSONObject().put("id", id).put("ireum", ireum).put("lat", lat).put("lon", lon).put("made", made)
        bang?.let { o.put("bang", it) }
        geul?.let { o.put("geul", JSONArray(it)) }
        jjak?.let { o.put("jjak", it) }
        return o
    }

    companion object {
        fun batgi(o: JSONObject): NaeMunHang? {
            val la = Jeomjido.su(o, "lat") ?: return null
            val lo = Jeomjido.su(o, "lon") ?: return null
            val ga = o.optJSONArray("geul")
            val g = if (ga == null) null else (0 until ga.length()).map { ga.optString(it) }.filter { it.isNotEmpty() }
            return NaeMunHang(Jeomjido.gul(o, "id"), Jeomjido.gul(o, "ireum"), la, lo, Jeomjido.su(o, "bang"),
                Jeomjido.gul(o, "made"), g, if (o.has("jjak")) o.optBoolean("jjak") else null)
        }
    }
}

object NaeMun {
    private val mok = ArrayList<NaeMunHang>()
    private var ilgeum = false
    val mokrok: List<NaeMunHang> get() { ilgi(); return ArrayList(mok) }

    private fun ilgi() {
        if (ilgeum) return
        val s = JeomSeol.geul("naeMun") ?: run { ilgeum = true; return }
        ilgeum = true
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { NaeMunHang.batgi(it) }?.let { mok.add(it) }
        } catch (e: Exception) {}
    }

    private fun jeojang() {
        val a = JSONArray()
        for (h in mok) a.put(h.json())
        JeomSeol.geulSseugi("naeMun", a.toString())
    }

    fun damgi(h: NaeMunHang) { ilgi(); mok.add(0, h); jeojang() }

    fun jiugi(id: String) { ilgi(); mok.removeAll { it.id == id }; jeojang() }

    /** 두 번 찍어 담기 — 5미터 안에 이미 담은 문이 있으면 새로 담지 않고 글자와 자리를 보탬. 알릴 말을 돌려줌 */
    fun jjakDamgi(ireum: String, lat: Double, lon: Double, bang: Double?, geul: List<String>): String {
        ilgi()
        val made = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date())
        val i = mok.indexOfFirst { Wichi.geori(it.lat, it.lon, lat, lon) < 5 }
        if (i >= 0) {
            var h = mok[i]
            val g = ArrayList(h.geul ?: emptyList())
            for (x in geul) if (!g.contains(x)) g.add(x)
            h = h.copy(geul = if (g.isEmpty()) null else g.take(4))
            if (h.jjak != true) h = h.copy(lat = lat, lon = lon)
            if (bang != null) h = h.copy(bang = bang)
            h = h.copy(jjak = true)
            if (ireum.isNotEmpty() && (h.ireum == "내 문" || h.ireum == "문")) h = h.copy(ireum = ireum)
            mok[i] = h
            jeojang()
            return "이미 담아 두신 ${h.ireum}입니다. 두 번 찍은 자리" + (if (geul.isEmpty()) "" else "와 문 둘레 글자") + "를 보탰습니다."
        }
        val nm = if (ireum.isNotEmpty()) ireum else (geul.firstOrNull() ?: "내 문")
        damgi(NaeMunHang(UUID.randomUUID().toString(), nm, lat, lon, bang, made, if (geul.isEmpty()) null else geul.take(4), true))
        return "${nm}을 내 문으로 담았습니다." + (if (geul.isEmpty()) "" else " 문 둘레 글자 ${geul.take(2).joinToString(", ")}도 함께 담았습니다.")
    }

    /** 가까운 내 문 가운데 글자를 담아 둔 것(문 찾기가 "찍어 두신 문"인지 가릴 때 씀) */
    fun geulMun(lat: Double, lon: Double, r: Double): NaeMunHang? =
        mokrok.filter { !(it.geul ?: emptyList()).isEmpty() && Wichi.geori(it.lat, it.lon, lat, lon) <= r }
            .minByOrNull { Wichi.geori(it.lat, it.lon, lat, lon) }
}
