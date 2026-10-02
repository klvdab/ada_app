// 안드로이드 길눈 — QR 찾기(묶음 b3_kamera, 아이폰 QrView.swift QrEngine 2.13.0 을 같은 말로 옮김)
// 카메라를 켜 두면 둘레의 QR을 폰 안의 판독기(ML Kit 바코드, 앱 안에 실림)로 저절로 찾아 삐 소리와 진동,
// 무엇인지와 카메라의 어느 쪽에 있는지를 알려 드립니다. 카메라 사진은 어디로도 보내지 않습니다. 화면을 떠나면 카메라는 저절로 꺼집니다.
package kr.or.ada.app.gilnun

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.Locale

object QrChatgi : KameraNunBupum {
    override val ireum = "QR 찾기"
    override var kyeojim = false
        private set
    /** 찾은 QR 글 */
    var chajeun: String? = null
        private set
    /** 주소, 전화번호, 연락처, 와이파이, 글 */
    var jongryu = ""
        private set
    /** 무엇으로 가는 QR인지 한 줄 */
    var seolmyeong = ""
        private set
    var jigeumMal = "카메라가 꺼져 있습니다."
        private set
    /** 켜짐·찾음이 바뀌면(화면이 채움) */
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var majimakChajeum = 0L
    private var pandok: BarcodeScanner? = null
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            gidarim()
            main.postDelayed(this, 5000)
        }
    }

    init { Kamera.deungrok(this) }

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        Kamera.heorak(t, this, "QR 찾기를") { sijak(t, bogi) }
    }

    private fun sijak(t: GilnunActivity, bogi: PreviewView?) {
        val p = pandok ?: BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        ).also { pandok = it }
        Kamera.sijak(t, this, bogi, 250L, { jang -> bunseok(p, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                chajeun = null
                majimakChajeum = System.currentTimeMillis()
                jigeumMal = "QR을 찾는 중입니다."
                Sori.mal("QR 찾기를 시작했습니다. 폰을 가슴 높이로 세워 들고 천천히 움직여 주십시오. QR이 잡히면 삐 소리가 납니다.")
                Girok.namgi("qr", mapOf("kyeogi" to true))
                main.removeCallbacks(sigye)
                main.postDelayed(sigye, 5000)
                byeonhwa?.invoke()
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        main.removeCallbacks(sigye)
        if (!kyeojim) return
        kyeojim = false
        jigeumMal = "카메라가 꺼져 있습니다."
        if (malHagi) Sori.mal("QR 찾기를 그만두고 카메라를 껐습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    /** 찾은 뒤 계속 — 다른 QR 찾기 */
    fun gyesok(a: GilnunActivity? = null, bogi: PreviewView? = null) {
        chajeun = null
        majimakChajeum = System.currentTimeMillis()
        jigeumMal = "QR을 찾는 중입니다."
        Sori.mal("계속 찾습니다.", MalGeup.JEONGBO)
        if (!kyeojim) kyeogi(a, bogi) else byeonhwa?.invoke()
    }

    fun sangtaeMal() {
        if (!kyeojim) { Sori.mal("카메라가 꺼져 있습니다. QR 찾기 시작을 누르십시오."); return }
        val c = chajeun
        if (c != null) { Sori.mal("찾은 QR — $seolmyeong ${c.take(60)}"); return }
        Sori.mal("QR 찾기 중입니다. 폰의 판독기로 살피고 있습니다.")
    }

    private fun gidarim() {
        if (!kyeojim || chajeun != null) return
        if (System.currentTimeMillis() - majimakChajeum > 60000) {
            majimakChajeum = System.currentTimeMillis()
            Sori.mal("아직 QR이 잡히지 않았습니다. 찾는 중입니다.", MalGeup.JEONGBO)
        }
    }

    /** 분석 줄 — 한 장을 판독기에. 결과는 화면 줄 */
    private fun bunseok(p: BarcodeScanner, jang: KameraJang) {
        val w = jang.garo.toDouble()
        val h = jang.sero.toDouble()
        p.process(jang.inputImage())
            .addOnSuccessListener { l ->
                if (!kyeojim || chajeun != null) return@addOnSuccessListener
                val q = l.firstOrNull { !it.rawValue.isNullOrEmpty() } ?: return@addOnSuccessListener
                val t = q.rawValue ?: return@addOnSuccessListener
                val b = q.boundingBox
                val x = if (b != null && w > 0) b.exactCenterX() / w else 0.5
                val y = if (b != null && h > 0) b.exactCenterY() / h else 0.5
                chajeun = t
                majimakChajeum = System.currentTimeMillis()
                val (k, s) = jongryuBoda(t)
                jongryu = k
                seolmyeong = s
                val eodi = eodi(x, y)
                jigeumMal = "QR을 찾았습니다. $s"
                Eum.naegi(EumJong.DOCHAK)
                KameraNun.jindong("arrive")
                Sori.mal("QR을 찾았습니다. $eodi $s 내용은 ${t.take(80)}.")
                Girok.namgi("qr_chajeum", mapOf("jong" to k))
                byeonhwa?.invoke()
            }
            .addOnCompleteListener { jang.kkeut() }
    }

    fun eodi(x: Double, y: Double): String {
        val a = ArrayList<String>()
        if (x < 0.33) a.add("왼쪽") else if (x > 0.67) a.add("오른쪽")
        if (y < 0.33) a.add("위쪽") else if (y > 0.67) a.add("아래쪽")
        return if (a.isEmpty()) "카메라 한가운데에 있습니다." else "카메라의 " + a.joinToString(" ") + "에 있습니다."
    }

    fun jongryuBoda(t: String): Pair<String, String> {
        val l = t.lowercase(Locale.ROOT)
        if (l.startsWith("http://") || l.startsWith("https://")) {
            val host = try { Uri.parse(t).host ?: "" } catch (e: Exception) { "" }
            return "주소" to "$host 사이트로 가는 QR입니다."
        }
        if (l.startsWith("tel:")) return "전화번호" to "전화번호 ${t.drop(4)} 입니다."
        if (l.startsWith("sms:") || l.startsWith("mailto:")) return "연락처" to "연락처 QR입니다."
        if (l.startsWith("wifi:")) return "와이파이" to "와이파이 연결 QR입니다."
        return "글" to "글이 담긴 QR입니다."
    }
}

/** QR 찾기 화면 — 길 찾기 탭 그 밖에 펼치기 */
class QrChatgiHwamyeon : KameraHwamyeon("QR 찾기") {
    override val bupum: KameraNunBupum get() = QrChatgi
    override fun kyeogi(t: GilnunActivity) = QrChatgi.kyeogi(t, bogi)

    override fun juljul(t: GilnunActivity): View {
        val q = QrChatgi
        q.byeonhwa = { dasiGeurigoChojeom(t) }
        var cheot: View? = null
        val c = q.chajeun
        if (c != null) {
            cheot = t.geul(q.jigeumMal + " " + c, true)
            if (q.jongryu == "주소" || q.jongryu == "전화번호" || q.jongryu == "연락처") {
                val ir = when (q.jongryu) {
                    "주소" -> "이 주소 열기"
                    "전화번호" -> "이 번호로 전화 걸기"
                    else -> "이 연락처 열기"
                }
                t.danchu(ir) {
                    try {
                        val u = Uri.parse(c)
                        val i = if (q.jongryu == "전화번호") Intent(Intent.ACTION_DIAL, u) else Intent(Intent.ACTION_VIEW, u)
                        t.startActivity(i)
                    } catch (e: Exception) {
                        Sori.mal("열 수 있는 앱이 없습니다.")
                    }
                }
            }
            t.danchu("글 복사하기") {
                try {
                    val cm = t.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("QR", c))
                    Sori.mal("복사했습니다.", MalGeup.JEONGBO)
                } catch (e: Exception) {
                    Sori.mal("복사하지 못했습니다.")
                }
            }
            t.danchu("다른 QR 찾기 — 계속") { q.gyesok(t, bogi) }
        } else if (q.kyeojim) {
            cheot = t.danchu("QR 찾는 중 — 지금 상태 알려 주기") { q.sangtaeMal() }
        }
        val kb = if (q.kyeojim) t.danchu("QR 찾기 그만 — 카메라 끄기") { q.kkeugi(true) }
                 else t.danchu("QR 찾기 시작 — 카메라 켜기") { q.kyeogi(t, bogi) }
        bogiNeoki(t, kb)
        alaDuSil(t, "QR은 보통 1~2미터 안에서 카메라가 그쪽을 향해야 잡힙니다. 폰을 가슴 높이로 세워 들고 천천히 지나가시면 안내판과 벽의 QR이 잡힙니다. 잡히면 삐 소리와 진동이 나고, 무엇이 담겼는지와 카메라의 어느 쪽에 있는지를 알려 드립니다. 어두우면 이 안의 손전등을 켜십시오. 카메라 사진은 어디로도 보내지 않고 폰 안에서만 읽습니다. 화면을 떠나면 카메라는 저절로 꺼집니다.") {
            bulbitDanchu(t)
        }
        return cheot ?: kb
    }
}
