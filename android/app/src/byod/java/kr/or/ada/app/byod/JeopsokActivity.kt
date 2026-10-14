// BYOD 방송 — 접속 도구: 엔에프시 태그 쓰기와 큐알코드 만들기 (1.1.1판, 빌드 261006-B3, 도서클이 방송클 일을 이어 맡아 만듦)
// 이사장님 지시(2026-10-06): 접수대에서 한 번에 붙게. 안드로이드는 엔에프시 태그(와이파이 붙기), 아이폰은 큐알코드(와이파이 붙기). 두 기능 모두 늘 유지.
// 플린트 2가 오면 붙자마자 듣기 화면이 저절로 열림. 그 전에는 「듣기 주소」 엔에프시 태그나 큐알로 듣기 화면을 엶.
// 1.2.0 (261014-B9, 이사장님 승인 「1」, 방송클) 플린트 2 방식 — 와이파이 태그·큐알 한 장이면 듣기 화면이 저절로 뜸. 듣기 주소 태그·큐알은 예비 방식용으로 펼치기 안에. 와이파이 이름 기억
// 1.1.6 (261012-B8, 이사장님 승인 「1」, 방송클) 스티커를 엔에프시 태그로 부름, 듣기 주소 바로잡기(방송 중 주소 먼저·http와 8080 저절로 채움)와 듣기 주소 확인,
//   엔에프시 태그를 쓴 뒤 다시 읽어 맞는지 확인, A4 인쇄용 한 장(위 와이파이 큐알, 아래 듣기 주소 큐알)
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
             else "방송 와이파이와 듣기 주소를 확인하고 아래에서 고르십시오.")
        ssidCan = kan("방송 와이파이 이름", ssidCheo(flint))   // 1.2.0 한 번 넣은 이름을 기억
        pwCan = kan("와이파이 비밀번호(없으면 비워 두기)", "")
        allim = geul("")
        allim.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        danchu("엔에프시 태그 쓰기: 와이파이 붙기") { ssidGieok(); sseugiJunbi(wifiMessage(), "와이파이 붙기") }
        danchu("엔에프시 태그 쓰기 멈추기") { sseugiMeomchum() }
        danchu("큐알 보기: 와이파이 붙기") { ssidGieok(); qrBoyeogi(wifiQr(), "와이파이붙기") }
        danchu(if (flint) "A4 인쇄용 한 장 만들기: 와이파이 큐알" else "A4 인쇄용 한 장 만들기: 위 와이파이, 아래 듣기 주소") { ssidGieok(); a4Mandeulgi() }   // 1.1.6, 1.2.0
        danchu("큐알 그림 저장(인쇄용)") { qrJeojang() }
        // 듣기 주소 태그·큐알 — 예비 방식(이 기기가 서버)에서 씀. 플린트 2 방식에서는 펼쳐야 보임
        val jusoMuk = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (flint) View.GONE else View.VISIBLE }
        if (flint) danchu("듣기 주소 태그와 큐알 펼치기 (예비 방식에서 씀)") { jusoMuk.visibility = if (jusoMuk.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
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
        qrGeurim = ImageView(this).apply { adjustViewBounds = true; setBackgroundColor(Color.WHITE); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        body.addView(qrGeurim, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
                mal(if (doem) "확인했습니다. 듣기 주소 " + ilkgi + " 에서 BYOD 방송이 답합니다."
                    else "듣기 주소 " + ilkgi + " 에서 답이 없습니다. 방송을 켰는지, 이 태블릿이 방송 공유기에 붙었는지 보십시오. 방송을 켜지 않았으면 그대로 써도 됩니다.")
                f()
            }
        }.start()
    }

    private fun qrMandeul(nae: String, keugi: Int): Bitmap {
        val m = QRCodeWriter().encode(nae, BarcodeFormat.QR_CODE, keugi, keugi, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2))
        val px = IntArray(keugi * keugi)
        for (y in 0 until keugi) for (x in 0 until keugi) px[y * keugi + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, keugi, keugi, Bitmap.Config.RGB_565)
    }

    // 1.1.6 A4 한 장(300dpi, 2480×3508) — 위에 와이파이 큐알, 아래에 듣기 주소 큐알. 폰을 종이 위에서 아래로 조금 내리면 두 번째가 잡힘
    private fun a4Mandeulgi() {
        if (Flint.flintInga(this)) { a4Flint(); return }   // 1.2.0
        val w = wifiQr(); val j = jusoGotchim(jusoCan.text.toString())
        if (w == "WIFI:T:nopass;S:;;") { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
        if (j.isEmpty()) { mal("듣기 주소가 비었습니다."); return }
        jusoCan.setText(j); Hyeonjang.stickerGieok(this, j)
        val b = Bitmap.createBitmap(2480, 3508, Bitmap.Config.RGB_565)
        val c = android.graphics.Canvas(b); c.drawColor(Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 110f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
        val ps = android.graphics.Paint(p).apply { textSize = 70f; isFakeBoldText = false }
        c.drawText("1. 와이파이 붙기 (위)", 1240f, 190f, p)
        c.drawBitmap(qrMandeul(w, 1350), 565f, 240f, null)
        c.drawText("와이파이: " + ssidCan.text.toString().trim(), 1240f, 1680f, ps)
        c.drawLine(200f, 1754f, 2280f, 1754f, ps)
        c.drawText("2. 현장영상해설 듣기 (아래)", 1240f, 1930f, p)
        c.drawBitmap(qrMandeul(j, 1350), 565f, 1980f, null)
        c.drawText(j, 1240f, 3420f, ps)
        qrBitmap = b; qrIreum = "A4한장"
        qrGeurim.setImageBitmap(b)
        qrGeurim.contentDescription = "A4 인쇄용 한 장, 위 와이파이 큐알, 아래 듣기 주소 큐알"
        qrJeojang()
    }

    // 1.2.0 플린트 2 방식 A4 — 와이파이 큐알 하나를 크게(약 17센티미터). 붙으면 듣기 화면이 저절로 뜸
    private fun a4Flint() {
        val w = wifiQr()
        if (w == "WIFI:T:nopass;S:;;") { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
        val b = Bitmap.createBitmap(2480, 3508, Bitmap.Config.RGB_565)
        val c = android.graphics.Canvas(b); c.drawColor(Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 120f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
        val ps = android.graphics.Paint(p).apply { textSize = 80f; isFakeBoldText = false }
        c.drawText("현장영상해설 듣기", 1240f, 300f, p)
        c.drawText("카메라로 비추면 와이파이에 붙고 듣기 화면이 뜹니다", 1240f, 450f, ps)
        c.drawBitmap(qrMandeul(w, 2000), 240f, 600f, null)
        c.drawText("와이파이: " + ssidCan.text.toString().trim(), 1240f, 2800f, ps)
        c.drawText("사단법인 한국시각장애인현장영상해설협회", 1240f, 3200f, ps)
        qrBitmap = b; qrIreum = "A4와이파이"
        qrGeurim.setImageBitmap(b)
        qrGeurim.contentDescription = "A4 인쇄용 한 장, 와이파이 큐알"
        qrJeojang()
    }

    private fun qrBoyeogi(nae: String, ireum: String) {
        if (nae.isEmpty() || nae == "WIFI:T:nopass;S:;;") { mal("내용이 비어 있습니다."); return }
        val m = QRCodeWriter().encode(nae, BarcodeFormat.QR_CODE, 800, 800, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2))
        val b = Bitmap.createBitmap(800, 800, Bitmap.Config.RGB_565)
        for (x in 0 until 800) for (y in 0 until 800) b.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        qrBitmap = b; qrIreum = ireum
        qrGeurim.setImageBitmap(b)
        qrGeurim.contentDescription = if (ireum == "와이파이붙기") "와이파이 붙기 큐알코드" else "듣기 주소 큐알코드"
        mal(qrGeurim.contentDescription.toString() + "를 화면 아래에 띄웠습니다. 인쇄하려면 큐알 그림 저장을 누르십시오.")
    }
    private fun qrJeojang() {
        val b = qrBitmap ?: run { mal("먼저 큐알 보기를 눌러 주십시오."); return }
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "BYOD_" + qrIreum + "_" + System.currentTimeMillis() + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BYOD")
            }
            val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw Exception()
            contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            mal("사진 앱의 BYOD 폴더에 저장했습니다. 그 그림을 인쇄하시면 됩니다.")
        } catch (e: Exception) { mal("저장하지 못했습니다.") }
    }
}
