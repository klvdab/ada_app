// BYOD 방송 — 접속 도구: 엔에프시 태그 쓰기와 큐알코드 만들기 (1.1.1판, 빌드 261006-B3, 도서클이 방송클 일을 이어 맡아 만듦)
// 이사장님 지시(2026-10-06): 접수대에서 한 번에 붙게. 안드로이드는 엔에프시 태그(와이파이 붙기), 아이폰은 큐알코드(와이파이 붙기). 두 기능 모두 늘 유지.
// 플린트 2가 오면 붙자마자 듣기 화면이 저절로 열림. 그 전에는 「듣기 주소」 엔에프시 태그나 큐알로 듣기 화면을 엶.
// 1.2.0 (261014-B9, 이사장님 승인 「1」, 방송클) 플린트 2 방식 — 와이파이 태그·큐알 한 장이면 듣기 화면이 저절로 뜸. 듣기 주소 태그·큐알은 예비 방식용으로 펼치기 안에. 와이파이 이름 기억
// 1.1.6 (261012-B8, 이사장님 승인 「1」, 방송클) 스티커를 엔에프시 태그로 부름, 듣기 주소 바로잡기(방송 중 주소 먼저·http와 8080 저절로 채움)와 듣기 주소 확인,
//   엔에프시 태그를 쓴 뒤 다시 읽어 맞는지 확인, A4 인쇄용 한 장(위 와이파이 큐알, 아래 듣기 주소 큐알)
// 1.7.0 (261021-B15, 이사장님 승인) 「예비 방식」을 「아이피타임 방식」으로. 큐알이 커서 카메라에 다 들어오지 않는다 하여 큐알을 작게 —
//   인쇄하면 한 변 약 5센티미터(300dpi 590점), 화면에서는 한 변 약 4센티미터(250dp). 큐알 둘레 흰 여백은 표준대로 4칸.
//   큐알 그림 저장도 큐알만 따로 저장하지 않고 A4 한 장 안에 5센티미터로 넣어 저장(사진 앱에서 인쇄하면 쪽에 맞춰 커지던 것을 막음).
//   A4 한 장(아이피타임 방식)은 가운데 자르는 선을 두어, 위·아래 큐알이 한 카메라에 함께 잡히지 않게 두 장으로 잘라 쓰게 함
package kr.or.ada.app.byod

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

class JeopsokActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var ssidCan: EditText
    private lateinit var pwCan: EditText
    private lateinit var jusoCan: EditText
    private lateinit var allim: TextView
    private lateinit var qrGeurim: ImageView
    private var qrBitmap: Bitmap? = null
    private var qrIreum = ""
    private var sseulGeot: NdefMessage? = null
    private var sseulIreum = ""
    private var sseunSu = 0

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sv = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        sv.addView(body); setContentView(sv)
        title = "접속 도구 — 엔에프시와 큐알"
        danchu("뒤로") { finish() }
        val flint = Flint.flintInga(this)   // 1.2.0
        geul(if (flint) "플린트 2 방식입니다. 듣는 분은 와이파이 태그 한 번(안드로이드)이나 와이파이 큐알 한 번(아이폰)이면 듣기 화면이 저절로 뜹니다. 듣기 주소 태그는 필요 없습니다."
             else "아이피타임 방식입니다. 듣는 분은 두 번 합니다. 첫째, 와이파이에 붙습니다(안드로이드는 와이파이 태그, 아이폰은 와이파이 큐알). 둘째, 듣기 주소 태그나 듣기 주소 큐알로 듣기 화면을 엽니다(안드로이드와 아이폰 모두). 방송 와이파이 이름은 아이피타임 공유기 와이파이 이름 그대로 넣으십시오.")
        ssidCan = kan("방송 와이파이 이름", ssidCheo(flint))   // 1.2.0 한 번 넣은 이름을 기억
        pwCan = kan("와이파이 비밀번호(없으면 비워 두기)", "")
        allim = geul("")
        allim.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        // 1.9.1 (261025-B18, 새방클, 이사장님 승인 2026-10-25) 한 번에 듣기 — LVD 방송 앱(2.6.0부터)이 저절로 열려 이 와이파이에 붙고 곧바로 들림
        geul("한 번에 듣기: 듣는 분 폰에 LVD 방송 앱이 깔려 있으면, 안드로이드는 엔에프시 태그를 대고 아이폰은 큐알을 비추기만 하면 앱이 열려 이 와이파이에 붙고 곧바로 들립니다. 와이파이만 붙기 태그와 큐알은 아래에 예비로 둡니다.")
        danchu("엔에프시 태그 쓰기: 한 번에 듣기 (LVD 방송 앱)") { ssidGieok(); hanbeonJuso()?.let { sseugiJunbi(NdefMessage(arrayOf(NdefRecord.createUri(it))), "한 번에 듣기") } }
        danchu("큐알 보기: 한 번에 듣기 (LVD 방송 앱)") { ssidGieok(); hanbeonJuso()?.let { qrBoyeogi(it, "한번에듣기") } }
        danchu("엔에프시 태그 쓰기: 와이파이 붙기") { ssidGieok(); sseugiJunbi(wifiMessage(), "와이파이 붙기") }
        danchu("엔에프시 태그 쓰기 멈추기") { sseugiMeomchum() }
        danchu("큐알 보기: 와이파이 붙기") { ssidGieok(); qrBoyeogi(wifiQr(), "와이파이붙기") }
        danchu(if (flint) "A4 인쇄용 한 장 만들기: 와이파이 큐알" else "A4 인쇄용 한 장 만들기: 위 와이파이, 아래 듣기 주소, 가운데를 잘라 두 장") { ssidGieok(); a4Mandeulgi() }   // 1.1.6, 1.2.0, 1.7.0
        danchu("큐알 그림 저장(인쇄용)") { qrJeojang() }
        // 듣기 주소 태그·큐알 — 아이피타임 방식(이 기기가 서버)에서 씀. 플린트 2 방식에서는 펼쳐야 보임
        val jusoMuk = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (flint) View.GONE else View.VISIBLE }
        if (flint) danchu("듣기 주소 태그와 큐알 펼치기 (아이피타임 방식에서 씀)") { jusoMuk.visibility = if (jusoMuk.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        val bon = body
        body = jusoMuk
        jusoCan = kan("듣기 주소", jusoGotchim(if (Bang.juso.isNotEmpty()) Bang.juso else if (flint) Flint.JUSO else ByodService.jusoChatgi()))   // 1.1.6 방송 중이면 방송 주소를 먼저
        danchu("듣기 주소 확인하기") { jusoHwagin {} }   // 1.1.6
        geul("듣기 주소 열기 태그는 안드로이드폰과 아이폰 XS 이후 기종 모두 폰 윗부분을 대면 열림")
        danchu("엔에프시 태그 쓰기: 듣기 주소 열기") {   // 1.1.6 주소를 바로잡고 먼저 확인한 뒤 씀
            val j = jusoGotchim(jusoCan.text.toString()); jusoCan.setText(j)
            if (j.isEmpty()) { mal("듣기 주소가 비었습니다. 공유기에 붙은 뒤 다시 여십시오."); return@danchu }
            Hyeonjang.stickerGieok(this, j)
            jusoHwagin { sseugiJunbi(NdefMessage(arrayOf(NdefRecord.createUri(j))), "듣기 주소") }
        }
        danchu("큐알 보기: 듣기 주소 열기") { val j = jusoGotchim(jusoCan.text.toString()); jusoCan.setText(j); Hyeonjang.stickerGieok(this, j); qrBoyeogi(j, "듣기주소") }   // 1.1.5
        body = bon
        body.addView(jusoMuk)
        // 1.7.0 화면 큐알은 한 변 약 4센티미터(250dp)로 작게, 가운데에 — 화면을 다른 폰 카메라에 비출 때 다 들어오게
        val qk = (250 * resources.displayMetrics.density).toInt()
        qrGeurim = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.WHITE); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        body.addView(qrGeurim, LinearLayout.LayoutParams(qk, qk).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; topMargin = 16; bottomMargin = 16 })
        danchu("뒤로") { finish() }
    }

    override fun onPause() { super.onPause(); sseugiMeomchum(false) }

    private fun geul(t: String): TextView { val v = TextView(this).apply { text = t; textSize = 20f; setPadding(0, 16, 0, 16) }; body.addView(v); return v }
    private fun danchu(t: String, f: () -> Unit): Button { val b = Button(this).apply { text = t; textSize = 20f; isAllCaps = false; setOnClickListener { f() } }; body.addView(b); return b }
    private fun kan(ireum: String, cheo: String): EditText {
        geul(ireum)
        val e = EditText(this).apply { setText(cheo); textSize = 20f; contentDescription = ireum; inputType = InputType.TYPE_CLASS_TEXT }
        body.addView(e); return e
    }
    private fun mal(t: String) { allim.text = t; allim.announceForAccessibility(t) }

    // 1.2.0 와이파이 이름 — 안드로이드는 위치 허락 없이는 지금 와이파이 이름을 알려 주지 않아 늘 비어 나왔음. 한 번 넣은 이름을 기억
    private fun ssidCheo(flint: Boolean): String {
        val g = getSharedPreferences("byod", Context.MODE_PRIVATE).getString(if (flint) "ssid_flint" else "ssid", "") ?: ""
        return g.ifEmpty { jigeumSsid().ifEmpty { if (flint) Flint.SSID else "" } }
    }
    private fun ssidGieok() {
        val t = ssidCan.text.toString().trim()
        if (t.isNotEmpty()) getSharedPreferences("byod", Context.MODE_PRIVATE).edit().putString(if (Flint.flintInga(this)) "ssid_flint" else "ssid", t).apply()
    }

    @Suppress("DEPRECATION")
    private fun jigeumSsid(): String = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val s = wm.connectionInfo?.ssid ?: ""
        val t = s.trim('"')
        if (t.isEmpty() || t.contains("unknown")) "" else t
    } catch (e: Exception) { "" }

    /** 1.9.1 한 번에 듣기 주소 — lvdbangsong://deut?w=와이파이이름&p=비밀번호 (LVD 방송 앱이 받음) */
    private fun hanbeonJuso(): String? {
        val ssid = ssidCan.text.toString().trim()
        if (ssid.isEmpty()) { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return null }
        val pw = pwCan.text.toString()
        val b = android.net.Uri.Builder().scheme("lvdbangsong").authority("deut").appendQueryParameter("w", ssid)
        if (pw.isNotEmpty()) b.appendQueryParameter("p", pw)
        return b.build().toString()
    }

    // 와이파이 정보(Wi-Fi Simple Config) — 안드로이드가 엔에프시 태그를 읽으면 「연결할까요」를 띄움
    private fun wifiMessage(): NdefMessage? {
        val ssid = ssidCan.text.toString().trim()
        if (ssid.isEmpty()) { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return null }
        val pw = pwCan.text.toString()
        val cred = ByteArrayOutputStream()
        fun tlv(o: ByteArrayOutputStream, t: Int, v: ByteArray) { o.write(t shr 8); o.write(t and 255); o.write(v.size shr 8); o.write(v.size and 255); o.write(v) }
        tlv(cred, 0x1026, byteArrayOf(1))
        tlv(cred, 0x1045, ssid.toByteArray(Charsets.UTF_8))
        tlv(cred, 0x1003, if (pw.isEmpty()) byteArrayOf(0, 1) else byteArrayOf(0, 0x20))
        tlv(cred, 0x100F, if (pw.isEmpty()) byteArrayOf(0, 1) else byteArrayOf(0, 8))
        tlv(cred, 0x1027, pw.toByteArray(Charsets.UTF_8))
        tlv(cred, 0x1020, byteArrayOf(-1, -1, -1, -1, -1, -1))
        val all = ByteArrayOutputStream()
        tlv(all, 0x104A, byteArrayOf(0x10))
        tlv(all, 0x100E, cred.toByteArray())
        return NdefMessage(arrayOf(NdefRecord.createMime("application/vnd.wfa.wsc", all.toByteArray())))
    }
    private fun wifiQr(): String {
        val ssid = ssidCan.text.toString().trim().replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
        val pw = pwCan.text.toString().replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
        return if (pw.isEmpty()) "WIFI:T:nopass;S:" + ssid + ";;" else "WIFI:T:WPA;S:" + ssid + ";P:" + pw + ";;"
    }

    private fun sseugiJunbi(m: NdefMessage?, ireum: String) {
        if (m == null) return
        val na = NfcAdapter.getDefaultAdapter(this)
        if (na == null) { mal("이 기기에는 엔에프시가 없습니다."); return }
        if (!na.isEnabled) { mal("엔에프시가 꺼져 있습니다. 설정에서 엔에프시를 켜 주십시오."); return }
        sseulGeot = m; sseulIreum = ireum; sseunSu = 0
        na.enableReaderMode(this, { tag -> sseugi(tag) }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
        mal(ireum + " 엔에프시 태그 쓰기를 준비했습니다. 기기 뒷면 가운데에 엔에프시 태그를 대 주십시오. 여러 장을 차례로 대면 계속 씁니다.")
    }
    // 1.1.1 — 엔에프시 태그 쓰기 고침(10월 6일 직원 시험에서 「쓸 수 없습니다」): 엔디프 확인을 건너뛰던 설정을 빼고, 그래도 안 되면 엔태그(NTAG) 칩에 칸마다 직접 씀(엔에프시 툴스 앱과 같은 방식)
    private fun sseugi(tag: Tag) {
        val m = sseulGeot ?: return
        val r = try { sseugiNdef(tag, m) } catch (e: Exception) { try { sseugiJikjeop(tag, m) } catch (e2: Exception) { "쓰지 못했습니다. 엔에프시 태그를 움직이지 말고 2초쯤 대 주십시오." } }
        if (r.isEmpty()) sseunSu++
        runOnUiThread { mal(if (r.isEmpty()) sseulIreum + " 엔에프시 태그 " + sseunSu + "장째를 다 썼습니다. 다음 엔에프시 태그를 대 주십시오." else r) }
    }
    private fun sseugiNdef(tag: Tag, m: NdefMessage): String {
        val nd = Ndef.get(tag)
        if (nd != null) {
            nd.connect()
            try {
                if (!nd.isWritable) return "이 엔에프시 태그는 잠겨 있어 쓸 수 없습니다."
                if (nd.maxSize < m.toByteArray().size) return "이 엔에프시 태그는 너무 작습니다. 엔태그 215 이상을 쓰십시오."
                nd.writeNdefMessage(m)
                // 1.1.6 쓴 뒤 다시 읽어 견줌 — 다르면 다시 대 달라고 알림
                val dasi = try { nd.ndefMessage?.toByteArray() } catch (e: Exception) { null }
                if (dasi != null && !dasi.contentEquals(m.toByteArray())) return "썼지만 다시 읽은 내용이 다릅니다. 엔에프시 태그를 움직이지 말고 한 번 더 대 주십시오."
                return ""
            } finally { try { nd.close() } catch (e: Exception) {} }
        }
        val f = NdefFormatable.get(tag)
        if (f != null) {
            f.connect()
            try { f.format(m); return "" } finally { try { f.close() } catch (e: Exception) {} }
        }
        return sseugiJikjeop(tag, m)
    }
    // 엔태그 21x(울트라라이트 계열) 칩에 엔디프 묶음을 4쪽(page 4)부터 칸마다 직접 씀
    private fun sseugiJikjeop(tag: Tag, m: NdefMessage): String {
        val mu = android.nfc.tech.MifareUltralight.get(tag) ?: return "이 엔에프시 태그는 지원하지 않는 종류입니다. 엔태그 213, 215, 216 엔에프시 태그를 쓰십시오."
        val msg = m.toByteArray()
        val o = java.io.ByteArrayOutputStream()
        o.write(0x03)
        if (msg.size < 255) o.write(msg.size) else { o.write(0xFF); o.write(msg.size shr 8); o.write(msg.size and 255) }
        o.write(msg); o.write(0xFE)
        while (o.size() % 4 != 0) o.write(0)
        val b = o.toByteArray()
        mu.connect()
        try {
            for (i in b.indices step 4) mu.writePage(4 + i / 4, b.copyOfRange(i, i + 4))
        } finally { try { mu.close() } catch (e: Exception) {} }
        return ""
    }
    private fun sseugiMeomchum(malhae: Boolean = true) {
        try { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) } catch (e: Exception) {}
        if (malhae && sseulGeot != null) mal("엔에프시 태그 쓰기를 멈췄습니다. 모두 " + sseunSu + "장 썼습니다.")
        sseulGeot = null
    }

    // 1.1.6 듣기 주소 바로잡기 — http:// 와 :8080 과 끝의 / 를 저절로 채움
    private fun jusoGotchim(t0: String): String {
        var t = t0.trim().replace(" ", "")
        if (t.isEmpty()) return ""
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "http://" + t
        val host = t.substringAfter("://").substringBefore("/")
        if (!host.contains(":")) t = t.replaceFirst(host, host + ":" + Bang.PORT)
        if (t.substringAfter("://").indexOf('/') < 0) t += "/"
        return t
    }

    // 1.1.6 듣기 주소 확인 — 그 주소로 방송 서버에 물어 답이 오는지 봄. 됐든 안 됐든 다음 일(f)은 이어 함
    private fun jusoHwagin(f: () -> Unit) {
        val j = jusoGotchim(jusoCan.text.toString()); jusoCan.setText(j)
        if (j.isEmpty()) { mal("듣기 주소가 비었습니다."); return }
        val ip = j.substringAfter("://").substringBefore(":").substringBefore("/")
        val ilkgi = ip.replace(".", " 점 ")
        mal("듣기 주소 " + ilkgi + " 를 확인하고 있습니다.")
        Thread {
            val doem = try {
                val c = java.net.URL(j + "status").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 3000; c.readTimeout = 3000; c.useCaches = false
                val ok = c.responseCode == 200 && c.inputStream.bufferedReader().readText().contains("\"byod\"")
                c.disconnect(); ok
            } catch (e: Exception) { false }
            runOnUiThread {
                mal(if (doem) "확인했습니다. 듣기 주소 " + ilkgi + " 에서 LVD 송출이 답합니다."
                    else "듣기 주소 " + ilkgi + " 에서 답이 없습니다. 방송을 켰는지, 이 기기가 아이피타임 공유기에 랜선으로 붙었는지 보십시오. 방송을 켜지 않았으면 그대로 써도 됩니다.")
                f()
            }
        }.start()
    }

    // 1.7.0 큐알 그림 — 둘레 흰 여백 4칸(표준), 칸 하나가 정수 점이 되게 만들어 인쇄해도 가장자리가 흐리지 않음
    private fun qrMandeul(nae: String, keugi: Int): Bitmap {
        val m = QRCodeWriter().encode(nae, BarcodeFormat.QR_CODE, keugi, keugi,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M))
        val px = IntArray(keugi * keugi)
        for (y in 0 until keugi) for (x in 0 until keugi) px[y * keugi + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, keugi, keugi, Bitmap.Config.RGB_565)
    }

    // 1.7.0 인쇄 크기 — A4(300dpi, 2480×3508)에서 큐알 한 변 590점 = 약 5센티미터
    private val A4W = 2480; private val A4H = 3508; private val QK = 590

    private fun a4Bang(): Pair<Bitmap, android.graphics.Canvas> {
        val b = Bitmap.createBitmap(A4W, A4H, Bitmap.Config.RGB_565)
        val c = android.graphics.Canvas(b); c.drawColor(Color.WHITE)
        return Pair(b, c)
    }
    private fun pen(k: Float, gulgeum: Boolean) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = k; isFakeBoldText = gulgeum; textAlign = android.graphics.Paint.Align.CENTER
    }
    private fun jareunSeon(c: android.graphics.Canvas, y: Float) {
        val p = android.graphics.Paint().apply { color = Color.BLACK; strokeWidth = 4f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(40f, 30f), 0f) }
        c.drawLine(120f, y, (A4W - 120).toFloat(), y, p)
        c.drawText("자르는 선", (A4W / 2).toFloat(), y - 50f, pen(50f, false))
    }

    // 1.1.6 A4 한 장 — 위에 와이파이 큐알, 아래에 듣기 주소 큐알. 1.7.0 큐알을 약 5센티미터로 작게, 가운데 자르는 선(두 장으로 잘라 씀)
    private fun a4Mandeulgi() {
        if (Flint.flintInga(this)) { a4Flint(); return }   // 1.2.0
        val w = wifiQr(); val j = jusoGotchim(jusoCan.text.toString())
        if (w == "WIFI:T:nopass;S:;;") { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
        if (j.isEmpty()) { mal("듣기 주소가 비었습니다."); return }
        jusoCan.setText(j); Hyeonjang.stickerGieok(this, j)
        val (b, c) = a4Bang()
        val x = ((A4W - QK) / 2).toFloat(); val cx = (A4W / 2).toFloat()
        // 위 반쪽(0~1754): 첫째 와이파이 붙기
        c.drawText("첫째. 와이파이 붙기", cx, 420f, pen(100f, true))
        c.drawText("아이폰 카메라로 비추십시오", cx, 560f, pen(70f, false))
        c.drawBitmap(qrMandeul(w, QK), x, 700f, null)
        c.drawText("와이파이: " + ssidCan.text.toString().trim(), cx, 1430f, pen(70f, false))
        jareunSeon(c, 1754f)
        // 아래 반쪽(1754~3508): 둘째 듣기 화면 열기
        c.drawText("둘째. 현장영상해설 듣기", cx, 2174f, pen(100f, true))
        c.drawText("와이파이에 붙은 뒤 카메라로 비추십시오", cx, 2314f, pen(70f, false))
        c.drawBitmap(qrMandeul(j, QK), x, 2454f, null)
        c.drawText(j, cx, 3184f, pen(70f, false))
        c.drawText("사단법인 한국시각장애인현장영상해설협회", cx, 3380f, pen(55f, false))
        qrBitmap = b; qrIreum = "A4두장"
        qrGeurim.setImageBitmap(b)
        qrGeurim.contentDescription = "A4 인쇄용 한 장, 위 와이파이 큐알, 아래 듣기 주소 큐알, 가운데 자르는 선"
        qrJeojang("A4 한 장을 사진 앱의 BYOD 폴더에 저장했습니다. 큐알은 한 변 약 5센티미터입니다. 인쇄한 뒤 가운데 자르는 선을 따라 잘라 위는 와이파이, 아래는 듣기로 쓰십시오.")
    }

    // 1.2.0 플린트 2 방식 A4 — 와이파이 큐알 하나. 1.7.0 약 5센티미터로 작게
    private fun a4Flint() {
        val w = wifiQr()
        if (w == "WIFI:T:nopass;S:;;") { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
        val (b, c) = a4Bang()
        val cx = (A4W / 2).toFloat()
        c.drawText("현장영상해설 듣기", cx, 420f, pen(110f, true))
        c.drawText("카메라로 비추면 와이파이에 붙고 듣기 화면이 뜹니다", cx, 570f, pen(70f, false))
        c.drawBitmap(qrMandeul(w, QK), ((A4W - QK) / 2).toFloat(), 720f, null)
        c.drawText("와이파이: " + ssidCan.text.toString().trim(), cx, 1450f, pen(70f, false))
        c.drawText("사단법인 한국시각장애인현장영상해설협회", cx, 1600f, pen(55f, false))
        qrBitmap = b; qrIreum = "A4와이파이"
        qrGeurim.setImageBitmap(b)
        qrGeurim.contentDescription = "A4 인쇄용 한 장, 와이파이 큐알"
        qrJeojang("A4 한 장을 사진 앱의 BYOD 폴더에 저장했습니다. 큐알은 한 변 약 5센티미터입니다.")
    }

    // 화면에 큐알 하나 — 1.7.0 화면 칸이 한 변 약 4센티미터라 작게 보임. 저장하면 A4 한 장 안에 약 5센티미터로 넣음
    private var qrNae = ""
    private fun qrBoyeogi(nae: String, ireum: String) {
        if (nae.isEmpty() || nae == "WIFI:T:nopass;S:;;") { mal("내용이 비어 있습니다."); return }
        qrBitmap = qrMandeul(nae, 600); qrIreum = ireum; qrNae = nae
        qrGeurim.setImageBitmap(qrBitmap)
        qrGeurim.contentDescription = if (ireum == "와이파이붙기") "와이파이 붙기 큐알코드" else "듣기 주소 큐알코드"
        mal(qrGeurim.contentDescription.toString() + "를 화면 아래에 띄웠습니다. 한 변 약 4센티미터입니다. 인쇄하려면 큐알 그림 저장을 누르십시오.")
    }

    // 1.7.0 큐알 하나를 A4 한 장 위쪽에 약 5센티미터로
    private fun a4Hana(): Bitmap? {
        if (qrNae.isEmpty()) return null
        val (b, c) = a4Bang()
        val cx = (A4W / 2).toFloat()
        val wifi = qrIreum == "와이파이붙기"
        c.drawText(if (wifi) "와이파이 붙기" else "현장영상해설 듣기", cx, 420f, pen(100f, true))
        c.drawText(if (wifi) "아이폰 카메라로 비추십시오" else "와이파이에 붙은 뒤 카메라로 비추십시오", cx, 560f, pen(70f, false))
        c.drawBitmap(qrMandeul(qrNae, QK), ((A4W - QK) / 2).toFloat(), 700f, null)
        c.drawText(if (wifi) "와이파이: " + ssidCan.text.toString().trim() else qrNae, cx, 1430f, pen(70f, false))
        c.drawText("사단법인 한국시각장애인현장영상해설협회", cx, 1580f, pen(55f, false))
        return b
    }

    private fun qrJeojang(doemMal: String = "") {
        val b0 = qrBitmap ?: run { mal("먼저 큐알 보기를 눌러 주십시오."); return }
        val hana = qrIreum == "와이파이붙기" || qrIreum == "듣기주소"
        val b = if (hana) (a4Hana() ?: b0) else b0   // 1.7.0 큐알 하나도 A4 한 장 안에 약 5센티미터로
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "BYOD_" + qrIreum + "_" + System.currentTimeMillis() + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BYOD")
            }
            val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw Exception()
            contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            mal(doemMal.ifEmpty { "A4 한 장으로 사진 앱의 BYOD 폴더에 저장했습니다. 큐알은 한 변 약 5센티미터입니다. 그 그림을 인쇄하시면 됩니다." })
        } catch (e: Exception) { mal("저장하지 못했습니다.") }
    }
}
