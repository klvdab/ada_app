// 안드로이드 길눈 — 안면인식(묶음 b4_dulreo, 아이폰 AnmyeonView.swift 2.7.0 을 같은 말로 옮김)
// 폰 안의 얼굴 찾기(ML Kit 얼굴, 앱 안 모델)만 씁니다. 사진은 어디로도 보내지 않습니다.
// 몇 명이 있는지, 몇 시 방향인지, 대략 몇 미터인지, 이쪽을 보는지, 웃는 듯한지(조심스럽게 짐작)를 알려 드립니다.
// 웃음은 아이폰이 입꼬리 자리로 짐작하는 것을 안드로이드는 ML Kit 의 웃는 정도(0~1)로 짐작합니다.
// ★아는 사람 등록 — 아이폰은 애플 사진 특징값(VNFeaturePrint)으로 견줌. 안드로이드에는 같은 것이 없어
//   얼굴 생김새(두 눈 사이를 1로 놓은 코·입·볼 자리)와 얼굴 밝기 무늬(24×24 흑백, 고르게 맞춤)를 함께 견주는 어림셈으로 대신합니다.
//   불빛·각도·안경에 쉽게 흔들려 아이폰보다 훨씬 자주 틀리거나 못 알아봅니다 — 화면과 도움말에 그렇게 밝힘.
//   등록한 얼굴 값은 폰 안(백업 안 하는 칸, noBackupFilesDir)에만 둡니다. 알려 드리는 말은 바뀔 때만, 3초에 한 번까지.
package kr.or.ada.app.gilnun

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.camera.view.PreviewView
import androidx.core.widget.doAfterTextChanged
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 보이는 사람 하나(아이폰 AnmyeonSaram) */
class AnmyeonSaram(
    val sigak: Int,          // 시계 방향 11·12·1 등
    val miteo: Double,       // 대략 거리
    val boneunJung: Boolean, // 이쪽을 보는지
    val unneunDeut: Boolean, // 웃는 듯한지(짐작)
    var ireum: String? = null // 등록한 사람이면 이름(짐작)
)

/** 얼굴 어림 값 하나 — 생김새(12)와 밝기 무늬(576) */
class AnmyeonTeukjing(val g: DoubleArray, val a: DoubleArray) {
    fun json(): JSONObject {
        val o = JSONObject()
        o.put("g", JSONArray().also { j -> g.forEach { j.put(it) } })
        o.put("a", JSONArray().also { j -> a.forEach { j.put(((it * 1000).roundToInt()) / 1000.0) } })
        return o
    }

    companion object {
        fun batgi(o: JSONObject?): AnmyeonTeukjing? {
            if (o == null) return null
            val ga = o.optJSONArray("g") ?: return null
            val aa = o.optJSONArray("a") ?: return null
            if (ga.length() != 12 || aa.length() != 576) return null
            return AnmyeonTeukjing(DoubleArray(12) { ga.optDouble(it, 0.0) }, DoubleArray(576) { aa.optDouble(it, 0.0) })
        }
    }
}

object AnmyeonEngine : KameraNunBupum {
    override val ireum = "안면인식"
    override var kyeojim = false
        private set
    var jigeumMal = "카메라가 꺼져 있습니다."
        private set
    var ireumDeul: List<String> = emptyList()
        private set
    var deungrokJung = false
        private set
    /** 켜짐·지금 보이는 것·등록한 분이 바뀌면(화면이 글자만 바꿈) */
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var chatgi: FaceDetector? = null
    private var majimakMalSigan = 0L
    private var majimakMal = ""
    private var pail: File? = null

    // 아는 사람 — 이름마다 얼굴 어림 값 여러 장
    private val aneun = LinkedHashMap<String, ArrayList<AnmyeonTeukjing>>()
    private var deungrokIreum = ""
    private val deungrokMoeum = ArrayList<AnmyeonTeukjing>()

    init { Kamera.deungrok(this) }

    fun sijak(c: Context) {
        if (pail != null) return
        pail = File(c.applicationContext.noBackupFilesDir, "gilnun_anmyeon.json")
        bureogi()
    }

    // MARK: 켜고 끄기

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        sijak(t)
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        Kamera.heorak(t, this, "안면인식을") { kyeogiSok(t, bogi) }
    }

    private fun kyeogiSok(t: GilnunActivity, bogi: PreviewView?) {
        val d = chatgi ?: FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.05f)
                .build()
        ).also { chatgi = it }
        Kamera.sijak(t, this, bogi, 500L, { jang -> bunseok(d, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                majimakMal = ""
                majimakMalSigan = 0L
                Sori.mal("안면인식을 켰습니다. 폰 뒤쪽 카메라를 앞으로 향해 들어 주십시오.")
                Girok.namgi("anmyeon", mapOf("kyeogi" to true))
                byeonhwa?.invoke()
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        if (!kyeojim) return
        kyeojim = false
        deungrokJung = false
        jigeumMal = "카메라가 꺼져 있습니다."
        if (malHagi) Sori.mal("안면인식을 껐습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    // MARK: 얼굴 보기 — 0.5초에 한 번(결과는 화면 줄)

    private fun bunseok(d: FaceDetector, jang: KameraJang) {
        d.process(jang.inputImage())
            .addOnSuccessListener { l -> boda(jang, l) }
            .addOnFailureListener { boda(jang, emptyList()) }
            .addOnCompleteListener { jang.kkeut() }
    }

    private fun boda(jang: KameraJang, l: List<Face>) {
        if (!kyeojim) return
        val w = jang.garo.toDouble()
        val h = jang.sero.toDouble()
        if (w <= 0 || h <= 0) return
        val eolguldeul = l.sortedBy { it.boundingBox.exactCenterX() }
        val saram = ArrayList<AnmyeonSaram>()
        for (f in eolguldeul) {
            val b = f.boundingBox
            val x = b.exactCenterX() / w
            val sigak = if (x < 0.2) 10 else if (x < 0.4) 11 else if (x <= 0.6) 12 else if (x <= 0.8) 1 else 2
            val miteo = min(15.0, max(0.3, 0.19 / max(b.height() / h, 0.01)))
            val boneun = abs(f.headEulerAngleY) < 20f          // 아이폰 |yaw| < 0.35 라디안(약 20도)
            val unneun = (f.smilingProbability ?: 0f) > 0.7f   // 조심스러운 짐작
            saram.add(AnmyeonSaram(sigak, miteo, boneun, unneun))
        }
        // 아는 사람 찾기 / 등록 — 얼굴 부분만 어림 값으로
        if ((aneun.isNotEmpty() || deungrokJung) && eolguldeul.isNotEmpty()) {
            val baro = try { jang.baroBitmap() } catch (e: Exception) { null }
            if (baro != null) {
                for ((i, f) in eolguldeul.withIndex()) {
                    val fp = teukjing(baro, f) ?: continue
                    if (deungrokJung && eolguldeul.size == 1) {
                        deungrokMoeum.add(fp)
                        jigeumMal = "${deungrokIreum}님 얼굴을 담는 중입니다. ${deungrokMoeum.size}장째."
                        byeonhwa?.invoke()
                        if (deungrokMoeum.size >= 5) deungrokKkeut()
                    } else if (aneun.isNotEmpty()) {
                        saram[i].ireum = chatgiIreum(fp)
                    }
                }
            }
        }
        if (deungrokJung && eolguldeul.size != 1) {
            val m = if (eolguldeul.isEmpty()) "등록할 얼굴이 보이지 않습니다." else "한 사람만 보이게 해 주십시오."
            if (m != jigeumMal) { jigeumMal = m; byeonhwa?.invoke() }
        }
        allimgi(saram)
    }

    private fun allimgi(saram: List<AnmyeonSaram>) {
        val mal = malMandeulgi(saram)
        if (!kyeojim) return
        if (!deungrokJung && mal != jigeumMal) { jigeumMal = mal; byeonhwa?.invoke() }
        val now = System.currentTimeMillis()
        if (mal == majimakMal || now - majimakMalSigan < 3000 || deungrokJung) return
        majimakMal = mal
        majimakMalSigan = now
        Sori.mal(mal, MalGeup.JEONGBO)
    }

    fun malMandeulgi(saram: List<AnmyeonSaram>): String {
        if (saram.isEmpty()) return "사람 얼굴이 보이지 않습니다."
        val su = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
        val n = saram.size
        var t = (if (n < su.size) su[n] else "$n") + " 명."
        for (s in saram.take(4)) {
            var m = "${s.sigak}시 방향"
            s.ireum?.let { m += " ${it}님인 듯," }
            m += " 약 ${miteoMal(s.miteo)}"
            if (s.boneunJung) m += ", 이쪽을 봅니다"
            if (s.unneunDeut) m += ", 웃는 듯합니다"
            t += " $m."
        }
        if (n > 4) t += " 그 밖에 ${n - 4}명."
        return t
    }

    fun miteoMal(m: Double): String {
        if (m < 1) return "1미터 안"
        if (m < 3) return String.format(Locale.US, "%.1f미터", Jeomjido.bannol(m * 2) / 2).replace(".0", "")
        return "${Jeomjido.bannol(m).toInt()}미터"
    }

    // MARK: 얼굴 어림 값(아이폰 특징값 대신) — 생김새 12 + 밝기 무늬 576

    private fun jeom(f: Face, k: Int): PointF? = f.getLandmark(k)?.position

    /** 얼굴 하나의 어림 값 — 이목구비가 다 잡히고 얼굴이 너무 돌아가지 않았을 때만 */
    fun teukjing(baro: Bitmap, f: Face): AnmyeonTeukjing? {
        if (abs(f.headEulerAngleY) > 25f || abs(f.headEulerAngleX) > 25f) return null
        val le = jeom(f, FaceLandmark.LEFT_EYE) ?: return null
        val re = jeom(f, FaceLandmark.RIGHT_EYE) ?: return null
        val ko = jeom(f, FaceLandmark.NOSE_BASE) ?: return null
        val il = jeom(f, FaceLandmark.MOUTH_LEFT) ?: return null
        val ir = jeom(f, FaceLandmark.MOUTH_RIGHT) ?: return null
        val ib = jeom(f, FaceLandmark.MOUTH_BOTTOM) ?: return null
        val lc = jeom(f, FaceLandmark.LEFT_CHEEK) ?: return null
        val rc = jeom(f, FaceLandmark.RIGHT_CHEEK) ?: return null
        // 두 눈 가운데를 0, 두 눈 사이를 1, 두 눈을 가로로 눕혀 맞춤
        val mx = (le.x + re.x) / 2.0
        val my = (le.y + re.y) / 2.0
        val ex = (re.x - le.x).toDouble()
        val ey = (re.y - le.y).toDouble()
        val eye = sqrt(ex * ex + ey * ey)
        if (eye < 12) return null   // 얼굴이 너무 작음
        val gak = atan2(ey, ex)
        val co = cos(-gak)
        val si = sin(-gak)
        val g = DoubleArray(12)
        var k = 0
        for (p in listOf(ko, il, ir, ib, lc, rc)) {
            val dx = (p.x - mx) / eye
            val dy = (p.y - my) / eye
            g[k++] = dx * co - dy * si
            g[k++] = dx * si + dy * co
        }
        // 밝기 무늬 — 얼굴 상자를 조금 넓혀 24×24 흑백으로, 평균 0·고르기 1로 맞춤(불빛 차이를 줄임)
        val b = f.boundingBox
        val pw = (b.width() * 0.1).toInt()
        val ph = (b.height() * 0.1).toInt()
        val r = Rect(max(0, b.left - pw), max(0, b.top - ph), min(baro.width, b.right + pw), min(baro.height, b.bottom + ph))
        if (r.width() < 24 || r.height() < 24) return null
        val a = DoubleArray(576)
        try {
            val jogak = Bitmap.createBitmap(baro, r.left, r.top, r.width(), r.height())
            val jak = Bitmap.createScaledBitmap(jogak, 24, 24, true)
            for (y in 0 until 24) for (x in 0 until 24) {
                val c = jak.getPixel(x, y)
                a[y * 24 + x] = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
            }
        } catch (e: Exception) {
            return null
        }
        val pyeong = a.average()
        var bun = 0.0
        for (v in a) bun += (v - pyeong) * (v - pyeong)
        val pyo = sqrt(bun / a.size)
        if (pyo < 1e-3) return null
        for (i in a.indices) a[i] = (a[i] - pyeong) / pyo
        return AnmyeonTeukjing(g, a)
    }

    /** 두 어림 값의 거리 — 생김새 거리 + (1 − 밝기 무늬 닮음). 작을수록 닮음 */
    private fun georiSem(x: AnmyeonTeukjing, y: AnmyeonTeukjing): Double {
        var gd = 0.0
        for (i in 0 until 12) gd += (x.g[i] - y.g[i]) * (x.g[i] - y.g[i])
        gd = sqrt(gd)
        var dot = 0.0
        for (i in 0 until 576) dot += x.a[i] * y.a[i]
        val dalm = dot / 576.0   // 고르게 맞춘 값끼리라 −1~1
        return gd + (1 - dalm)
    }

    /** 가장 가까운 사람 — 잣대 안이면 이름(짐작) */
    private fun chatgiIreum(fp: AnmyeonTeukjing): String? {
        var jal: String? = null
        var jalG = Double.MAX_VALUE
        for ((ireum, deul) in aneun) for (d in deul) {
            val g = georiSem(fp, d)
            if (g < jalG) { jalG = g; jal = ireum }
        }
        // 잣대(어림) — 생김새가 거의 같고(0.15 안팎) 밝기 무늬가 꽤 닮을 때(0.75 넘게)쯤
        return if (jal != null && jalG < 0.45) jal else null
    }

    // MARK: 아는 사람 등록 — 폰 안에만

    fun deungrokSijak(ireum: String) {
        val i = ireum.trim()
        if (i.isEmpty()) { Sori.mal("등록할 분의 이름을 먼저 적어 주십시오."); return }
        if (!kyeojim) { Sori.mal("먼저 안면인식을 켜 주십시오."); return }
        deungrokIreum = i
        deungrokMoeum.clear()
        deungrokJung = true
        jigeumMal = "${i}님 얼굴을 담는 중입니다."
        Sori.mal("${i}님 얼굴을 담습니다. 그분 얼굴이 카메라에 보이게 몇 초 들고 계십시오. 얼굴을 정면으로 비춰 주십시오.")
        byeonhwa?.invoke()
    }

    private fun deungrokKkeut() {
        val i = deungrokIreum
        val l = aneun.getOrPut(i) { ArrayList() }
        l.addAll(deungrokMoeum)
        while (l.size > 15) l.removeAt(0)
        deungrokMoeum.clear()
        deungrokJung = false
        ireumDeul = aneun.keys.sorted()
        Sori.mal("${i}님을 등록했습니다. 폰 안에만 보관합니다.")
        Girok.namgi("anmyeon", mapOf("deungrok" to true))
        jeojang()
        byeonhwa?.invoke()
    }

    fun jiugi(ireum: String) {
        aneun.remove(ireum)
        ireumDeul = aneun.keys.sorted()
        jeojang()
        Sori.mal("${ireum}님을 지웠습니다.")
        byeonhwa?.invoke()
    }

    private fun jeojang() {
        val f = pail ?: return
        val o = JSONObject()
        for ((k, v) in aneun) o.put(k, JSONArray().also { j -> v.forEach { j.put(it.json()) } })
        val s = o.toString()
        DrNet.dwiSil {
            try {
                val im = File(f.parentFile, f.name + ".part")
                im.writeText(s, Charsets.UTF_8)
                if (!im.renameTo(f)) { f.writeText(s, Charsets.UTF_8); im.delete() }
            } catch (e: Exception) {}
        }
    }

    private fun bureogi() {
        val f = pail ?: return
        try {
            if (!f.exists()) return
            val o = JSONObject(f.readText(Charsets.UTF_8))
            val ks = o.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val a = o.optJSONArray(k) ?: continue
                val l = ArrayList<AnmyeonTeukjing>()
                for (i in 0 until a.length()) AnmyeonTeukjing.batgi(a.optJSONObject(i))?.let { l.add(it) }
                if (l.isNotEmpty()) aneun[k] = l
            }
            ireumDeul = aneun.keys.sorted()
        } catch (e: Exception) {}
    }
}

/** 안면인식 화면 — 둘러보기 탭(아이폰처럼 켜기 단추로 켬) */
class AnmyeonHwamyeon : KameraHwamyeon("안면인식") {
    override val bupum: KameraNunBupum get() = AnmyeonEngine
    override val jadong: Boolean = false
    override fun kyeogi(t: GilnunActivity) = AnmyeonEngine.kyeogi(t, bogi)

    private var deungPyeol = false
    private var ireum = ""
    private val m5 = DrMok5()

    override fun juljul(t: GilnunActivity): View {
        val e = AnmyeonEngine
        e.sijak(t)
        val kb = t.danchu(kGeul()) { if (e.kyeojim) e.kkeugi(true) else e.kyeogi(t, bogi) }
        val jm = t.geul(e.jigeumMal, true)
        jm.contentDescription = "지금 보이는 것. ${e.jigeumMal}"
        t.geul("폰을 세워 들고 뒤쪽 카메라를 앞으로 향하십시오. 바뀔 때만 3초에 한 번까지 말씀드립니다. 거리와 웃음은 짐작입니다.")
        bogiNeoki(t, jm)
        t.pyeolchigi("아는 사람 등록 — 폰 안에만 보관,", deungPyeol) { deungPyeol = !deungPyeol }
        var db: android.widget.Button? = null
        if (deungPyeol) {
            val ip: EditText = t.ipryeok("등록할 분의 이름", false)
            ip.setText(ireum)
            ip.doAfterTextChanged { ireum = it?.toString() ?: "" }
            db = t.danchu(dGeul()) {
                if (e.deungrokJung) return@danchu
                e.deungrokSijak(ireum)
            }
            if (e.ireumDeul.isEmpty()) {
                t.geul("등록한 분이 없습니다.")
            } else {
                m5.geurigi(t, e.ireumDeul) { i ->
                    val v = t.geul(i, true)
                    v.contentDescription = "$i. 등록한 분"
                    drDongjak(t, v, listOf("지우기" to { e.jiugi(i) }))
                    v
                }
                t.geul("지우실 때는 이름을 길게 누르시거나 톡백 동작 메뉴에서 지우기를 고르십시오.")
            }
            t.geul("안드로이드에서는 얼굴 생김새(눈, 코, 입 자리)와 얼굴 밝기 무늬로 어림하는 것이라 아이폰보다 훨씬 자주 틀리거나 못 알아봅니다. 불빛과 각도, 안경에 따라 달라지니 이름은 참고로만 들으십시오. 등록한 얼굴 값은 폰 안에만 보관하고 폰 백업에도 넣지 않습니다.")
        }
        var ireumSu = e.ireumDeul.size
        e.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
                if (jm.text.toString() != e.jigeumMal) {
                    jm.text = e.jigeumMal
                    jm.contentDescription = "지금 보이는 것. ${e.jigeumMal}"
                }
                val d = dGeul()
                db?.let { if (it.text.toString() != d) it.text = d }
                if (deungPyeol && e.ireumDeul.size != ireumSu) {
                    ireumSu = e.ireumDeul.size
                    t.dasiGeurigi()   // 등록한 분 줄이 바뀜(말은 엔진이 이미 함)
                }
            }
        }
        return kb
    }

    /** 말로 하기 "안면인식" — 화면을 연 뒤 켬(아이폰 1.5초 뒤 kyeogi) */
    fun malroKyeogi(t: GilnunActivity) = AnmyeonEngine.kyeogi(t, bogi)

    private fun kGeul() = if (AnmyeonEngine.kyeojim) "안면인식 끄기" else "안면인식 켜기"
    private fun dGeul() = if (AnmyeonEngine.deungrokJung) "얼굴을 담는 중입니다" else "지금 앞의 얼굴로 등록"
}
