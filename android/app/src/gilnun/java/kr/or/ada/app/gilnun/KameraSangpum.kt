// 안드로이드 길눈 — 상품 바코드 읽기(묶음 b4_dulreo, 아이폰 SangpumIlgi.swift 2.20.0 을 같은 말로 옮김)
// 상품을 카메라 앞에서 천천히 돌리면 바코드를 찾아 확신음 한 번 울리고 상품 이름을 읽어 드립니다.
// 바코드 알아보기는 폰 안의 판독기(ML Kit 바코드, 앱 안에 실림 — b3 QR 찾기와 같은 묶음)로 합니다. 사진은 담지도 보내지도 않습니다.
// 상품 이름은 바코드 번호만 무료 공개 상품 자료(오픈푸드팩츠, 열쇠 없음)에 물어 찾고,
// 못 찾으면 어느 나라 상품인지와 바코드 번호를 읽어 드립니다.
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.json.JSONObject

object SangpumIlgi : KameraNunBupum {
    override val ireum = "상품 바코드 읽기"
    override var kyeojim = false
        private set
    var sangtae = ""
        private set
    /** 방금 읽은 상품(다시 듣기용) */
    var majimak = ""
        private set
    /** 이번에 읽은 상품들(최근 것이 위) */
    val jinan = ArrayList<String>()
    /** 켜짐·읽은 상품이 바뀌면(화면이 글자만 바꿈) */
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var pandok: BarcodeScanner? = null
    // 아래는 화면 줄에서만
    private val beonhoT = HashMap<String, Long>()   // 같은 바코드 10초 안 되풀이 않음
    private var chatneun = false                     // 이름을 찾는 중
    private var saeT = 0L                            // 마지막으로 바코드를 찾은 때
    private var dowumT = 0L                          // 마지막 찾는 법 알림
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            salpigi()
            if (kyeojim) main.postDelayed(this, 5000)
        }
    }

    init { Kamera.deungrok(this) }

    // MARK: 켜기·끄기

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        Kamera.heorak(t, this, "상품 바코드 읽기를") { sijak(t, bogi) }
    }

    private fun sijak(t: GilnunActivity, bogi: PreviewView?) {
        val p = pandok ?: BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_E, Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128)
                .build()
        ).also { pandok = it }
        Kamera.sijak(t, this, bogi, 300L, { jang -> bunseok(p, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                chatneun = false
                saeT = System.currentTimeMillis()
                dowumT = saeT
                sangtae = "바코드를 찾는 중입니다."
                mal("상품 바코드 읽기를 시작합니다. 폰에서 한 뼘쯤 떨어뜨려 상품을 천천히 돌려 주십시오.", sseuGi = true)
                Girok.namgi("sangpum", mapOf("kyeogi" to true))
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
        chatneun = false
        sangtae = ""
        if (malHagi) Sori.mal("상품 바코드 읽기를 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun dasiDeutgi() {
        if (majimak.isNotEmpty()) Sori.mal(majimak)
        else Sori.mal(if (kyeojim) "아직 읽은 상품이 없습니다. 바코드를 찾는 중입니다." else "아직 읽은 상품이 없습니다.")
    }

    /** 20초마다 찾는 법 한 번, 10분 동안 바코드를 못 찾으면 카메라를 끔(배터리) */
    private fun salpigi() {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        if (now - saeT > 600000) {
            kkeugi(false)
            mal("10분 동안 바코드를 찾지 못해 카메라를 껐습니다.", sseuGi = true)
            return
        }
        if (!chatneun && now - saeT > 20000 && now - dowumT > 20000) {
            dowumT = now
            mal("바코드는 보통 상품 뒷면이나 옆면 아래쪽에 있습니다. 천천히 돌려 주십시오.", MalGeup.JEONGBO)
        }
    }

    // MARK: 한 장마다 — 0.3초에 한 번(분석 줄 → 결과는 화면 줄)

    private fun bunseok(p: BarcodeScanner, jang: KameraJang) {
        p.process(jang.inputImage())
            .addOnSuccessListener { l ->
                val b = l.firstOrNull { (it.rawValue ?: "").length >= 6 } ?: return@addOnSuccessListener
                var beonho = b.rawValue ?: return@addOnSuccessListener
                // 아이폰은 UPC-A 를 앞에 0 을 붙인 EAN-13 으로 읽음 — 같게 맞춤
                if (b.format == Barcode.FORMAT_UPC_A && beonho.length == 12) beonho = "0$beonho"
                chajeum(beonho)
            }
            .addOnCompleteListener { jang.kkeut() }
    }

    // MARK: 찾음(화면 줄)

    private fun chajeum(beonho: String) {
        if (!kyeojim || chatneun) return
        val now = System.currentTimeMillis()
        val t = beonhoT[beonho]
        if (t != null && now - t < 10000) return
        beonhoT[beonho] = now
        saeT = now
        chatneun = true
        Eum.naegi(EumJong.HWAKSIN)
        sangtae = "바코드를 찾았습니다. 상품 이름을 찾는 중입니다."
        Girok.namgi("sangpum", mapOf("chajeum" to true))
        ireumChatgi(beonho) { ir -> allida(beonho, ir) }
    }

    private fun allida(beonho: String, ireum: String?) {
        chatneun = false
        if (!kyeojim) return
        val m = if (!ireum.isNullOrEmpty()) "$ireum."
                else nara(beonho) + " 상품 이름은 찾지 못했습니다. 바코드 번호는 " + suja(beonho) + "입니다."
        majimak = m
        jinan.add(0, m)
        while (jinan.size > 20) jinan.removeAt(jinan.size - 1)
        sangtae = m
        // 상품 이름은 사람이 기다리는 대답이므로 카메라 눈 말소리를 꺼도 읽어 드림
        Sori.mal(m, MalGeup.ANNAE)
        byeonhwa?.invoke()
    }

    // MARK: 상품 이름 찾기 — 바코드 번호만 보냄

    fun ireumChatgi(beonho: String, kkeut: (String?) -> Unit) {
        if (beonho.isEmpty() || !beonho.all { it.isDigit() }) { kkeut(null); return }
        val u = "https://world.openfoodfacts.org/api/v2/product/$beonho.json?fields=product_name_ko,product_name,brands,quantity"
        DrNet.geul(u, "Gilnun-Android/2.7 (kr.or.ada.app)", 6000) { s ->
            if (s == null) { kkeut(null); return@geul }
            val p = try { JSONObject(s).optJSONObject("product") } catch (e: Exception) { null }
            if (p == null) { kkeut(null); return@geul }
            fun g(k: String): String = ((p.opt(k) as? String) ?: "").trim()
            val ireum = if (g("product_name_ko").isEmpty()) g("product_name") else g("product_name_ko")
            if (ireum.isEmpty()) { kkeut(null); return@geul }
            var r = ""
            val br = g("brands").split(",").first().trim()
            if (br.isNotEmpty() && !ireum.contains(br)) r += "$br, "
            r += ireum
            val yang = g("quantity")
            if (yang.isNotEmpty()) r += ", $yang"
            kkeut(r)
        }
    }

    /** 바코드 앞자리로 어느 나라에서 번호를 받은 상품인지 */
    fun nara(b: String): String {
        if (b.length != 13) return ""
        val a3 = b.take(3).toIntOrNull() ?: return ""
        return when (a3) {
            880 -> "우리나라 상품입니다."
            in 450..459, in 490..499 -> "일본 상품입니다."
            in 690..699 -> "중국 상품입니다."
            in 0..139 -> "미국이나 캐나다 상품입니다."
            in 400..440 -> "독일 상품입니다."
            in 300..379 -> "프랑스 상품입니다."
            471 -> "대만 상품입니다."
            885 -> "태국 상품입니다."
            893 -> "베트남 상품입니다."
            else -> ""
        }
    }

    /** 번호를 한 자씩 또박또박(영은 공으로) */
    fun suja(b: String): String {
        val ir = mapOf('0' to "공", '1' to "일", '2' to "이", '3' to "삼", '4' to "사",
                       '5' to "오", '6' to "육", '7' to "칠", '8' to "팔", '9' to "구")
        return b.map { ir[it] ?: it.toString() }.joinToString(" ")
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)
}

/** 상품 바코드 읽기 화면 — 둘러보기 탭 */
class SangpumHwamyeon : KameraHwamyeon("상품 바코드 읽기") {
    override val bupum: KameraNunBupum get() = SangpumIlgi
    override fun kyeogi(t: GilnunActivity) = SangpumIlgi.kyeogi(t, bogi)

    private var jinanPyeol = false
    private var jinanMok: List<String> = emptyList()   // 펼칠 때의 목록(펼친 동안 줄이 흔들리지 않게)
    private val m5 = DrMok5()

    override fun juljul(t: GilnunActivity): View {
        val g = SangpumIlgi
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t, bogi) }
        val dd = t.danchu("방금 상품 다시 듣기") { g.dasiDeutgi() }
        bogiNeoki(t, dd)
        malsoriDanchu(t)
        var jb: Button? = null
        if (g.jinan.isNotEmpty() || jinanPyeol) {
            jb = t.danchu(if (jinanPyeol) "이번에 읽은 상품 접기" else "이번에 읽은 상품 펼치기") {
                jinanPyeol = !jinanPyeol
                if (jinanPyeol) { jinanMok = ArrayList(g.jinan); m5.saeMok() }
                t.dasiGeurigi()
            }
            if (jinanPyeol) m5.geurigi(t, jinanMok) { s -> t.geul(s) }
        }
        val itda = g.jinan.isNotEmpty()
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
                // 처음 읽은 상품이 생기면 펼치기 줄이 나와야 하므로 한 번만 다시 그림(커서는 그대로 첫 줄)
                if (!itda && g.jinan.isNotEmpty() && jb == null) dasiGeurigoChojeom(t)
            }
        }
        alaDuSil(t, "화면을 여시면 바로 시작합니다. 폰을 한 손에 들고, 상품을 폰 뒤쪽 카메라에서 한 뼘쯤 떨어뜨려 천천히 돌려 주십시오. 바코드를 찾으면 확신음이 한 번 울리고 상품 이름을 읽어 드립니다. 같은 상품은 10초 안에 되풀이해 읽지 않습니다. 이름을 찾지 못하면 어느 나라 상품인지와 바코드 번호를 읽어 드립니다. 바코드는 보통 상품 뒷면이나 옆면 아래쪽에 있습니다. 20초 동안 찾지 못하면 찾는 법을 한 번 알려 드립니다. 어두우면 이 안의 손전등을 켜십시오. 카메라 눈 말소리를 끄시면 찾는 법 알림은 하지 않고, 상품 이름만 읽어 드립니다. 방금 상품 다시 듣기를 누르시면 마지막으로 읽은 상품을 다시 들려 드리고, 이번에 읽은 상품 펼치기에서 앞서 읽은 것들을 다섯 개씩 보실 수 있습니다. 10분 동안 바코드를 찾지 못하거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 바코드 읽어 줘, 이 상품 뭐야라고 하셔도 열립니다. 바코드 알아보기는 폰 안에서 하고 사진은 담지도 보내지도 않습니다. 상품 이름을 찾을 때만 바코드 번호를 무료 공개 상품 자료에 물어봅니다. 공개 자료에 없는 우리나라 상품은 이름이 나오지 않을 수 있습니다.") {
            bulbitDanchu(t)
        }
        return kb
    }

    private fun kGeul() = if (SangpumIlgi.kyeojim) "멈추기 — 바코드를 찾는 중" else "이어 찾기 — 카메라를 켜고 바코드 읽기"
}
