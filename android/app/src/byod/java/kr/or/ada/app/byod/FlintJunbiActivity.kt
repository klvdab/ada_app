// BYOD 방송 — 플린트 2 준비하기 (1.2.0판, 빌드 261014-B9, 이사장님 승인 「1」, 방송클)
// 새 플린트 2 공유기에 방송 서버를 넣는 화면. 사무실에서 공유기마다 처음 한 번만 합니다(방송 서버를 새 판으로 바꿀 때도 같은 단추).
// 앱이 공유기 관리 주소(192.168.8.1)에 관리자 비밀번호로 들어가(SSH), 앱 안에 든 두 파일을 올리고 준비 파일을 돌립니다.
// 준비 파일이 「BYOD:」로 알리는 말을 한 줄씩 화면에 적고 읽어 드립니다. 비밀번호는 기기에 남기지 않습니다.
package kr.or.ada.app.byod

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class FlintJunbiActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var pw: EditText
    private lateinit var allim: TextView
    private lateinit var girok: TextView
    private lateinit var sijakDanchu: Button
    @Volatile private var dolgo = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sv = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        sv.addView(body); setContentView(sv)
        title = "플린트 2 준비하기"
        danchu("뒤로") { finish() }
        geul("새 플린트 2 공유기에 방송 서버를 넣습니다. 공유기마다 처음 한 번만 하면 됩니다.")
        geul("준비 1. 공유기의 인터넷 단자(WAN)에 사무실 인터넷 선을 꽂고 전원을 켭니다.")
        geul("준비 2. 공유기를 처음 켰다면, 관리자 비밀번호를 정하는 화면을 앞을 보는 분이 한 번 마쳐 주십시오.")
        geul("준비 3. 이 기기를 공유기 와이파이에 붙입니다. 처음 이름은 GL-MT6000 으로 시작하고, 비밀번호는 공유기 바닥 스티커에 있습니다. 이미 준비한 공유기면 " + Flint.SSID + " 에 붙입니다.")
        pw = EditText(this).apply {
            textSize = 20f; hint = "공유기 관리자 비밀번호"; contentDescription = "공유기 관리자 비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        body.addView(pw)
        sijakDanchu = danchu("플린트 2 준비 시작") { sijak() }
        danchu("공유기 방송 서버 확인하기") { hwagin() }
        allim = geul("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        girok = geul("").apply { textSize = 16f }
        danchu("뒤로") { finish() }
    }

    private fun geul(t: String): TextView { val v = TextView(this).apply { text = t; textSize = 20f; setPadding(0, 14, 0, 14) }; body.addView(v); return v }
    private fun danchu(t: String, f: () -> Unit): Button { val b = Button(this).apply { text = t; textSize = 20f; isAllCaps = false; setOnClickListener { f() } }; body.addView(b); return b }
    private fun mal(t: String) { runOnUiThread { allim.text = t; allim.announceForAccessibility(t); girok.append(t + "\n") } }

    private fun hwagin() {
        mal("공유기 방송 서버에 묻고 있습니다.")
        Thread {
            val t = Flint.mutgi(this, "/status")
            if (t == null) mal("공유기 방송 서버가 답하지 않습니다. 이 기기가 " + Flint.SSID + " 와이파이에 붙었는지 보시고, 처음 쓰는 공유기면 플린트 2 준비 시작을 누르십시오.")
            else try {
                val j = org.json.JSONObject(t)
                mal("공유기 방송 서버가 답합니다. " + j.optString("pan") + "판, 듣는 분 " + j.optInt("n") + "명, " +
                    (if (j.optBoolean("mp3")) "화면을 잠가도 이어지는 소리를 쓸 수 있습니다." else "늦음이 가장 적은 소리만 씁니다."))
            } catch (_: Exception) { mal("공유기가 답했지만 내용을 읽지 못했습니다.") }
        }.start()
    }

    // 공유기 쪽 망으로만 잇는 소켓(공유기 와이파이에는 인터넷이 없어 안드로이드가 모바일 데이터로 보내려 하므로)
    private inner class MangSocket : com.jcraft.jsch.SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val n = Flint.mang(this@FlintJunbiActivity)
            val s = n?.socketFactory?.createSocket() ?: Socket()
            s.connect(InetSocketAddress(host, port), 8000)
            return s
        }
        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }

    private fun olligi(ses: Session, asset: String, gil: String) {
        val b = assets.open("flint/$asset").use { it.readBytes() }
        val ch = ses.openChannel("exec") as ChannelExec
        ch.setCommand("cat > $gil && wc -c < $gil")
        val o = ch.outputStream
        val i = ch.inputStream
        ch.connect(10000)
        o.write(b); o.flush(); o.close()
        val dap = i.bufferedReader().readText().trim()
        ch.disconnect()
        if (dap != b.size.toString()) throw IllegalStateException("올린 크기가 다릅니다($asset)")
    }

    private fun sijak() {
        if (dolgo) { mal("준비하고 있습니다. 끝날 때까지 기다려 주십시오."); return }
        val p = pw.text.toString()
        if (p.isEmpty()) { mal("공유기 관리자 비밀번호를 넣어 주십시오."); return }
        if (Flint.mang(this) == null) { mal("이 기기가 공유기에 붙지 않았습니다. 준비 3을 먼저 해 주십시오."); return }
        dolgo = true
        girok.text = ""
        mal("공유기에 들어가고 있습니다.")
        Thread {
            var ses: Session? = null
            try {
                val js = JSch()
                ses = js.getSession("root", Flint.GWANRI, 22)
                ses.setPassword(p)
                ses.setConfig("StrictHostKeyChecking", "no")
                ses.setSocketFactory(MangSocket())
                ses.connect(10000)
                mal("공유기에 들어갔습니다. 방송 서버 파일을 올립니다.")
                olligi(ses, "byod_flint.py", "/tmp/byod_flint.py")
                olligi(ses, "byod_seolchi.sh", "/tmp/byod_seolchi.sh")
                val ch = ses.openChannel("exec") as ChannelExec
                ch.setCommand("sh /tmp/byod_seolchi.sh 2>&1")
                val i = ch.inputStream
                ch.connect(10000)
                var kkeut = false
                i.bufferedReader().forEachLine { l ->
                    if (l.startsWith("BYOD: ")) mal(l.removePrefix("BYOD: "))
                    if (l.trim() == "BYOD-KKEUT") kkeut = true
                }
                ch.disconnect()
                if (kkeut) {
                    Flint.bangsikBakkugi(this, "flint")
                    mal("플린트 2 준비를 마쳤습니다. 1분쯤 뒤 와이파이 " + Flint.SSID + " 가 뜨면 이 기기를 거기에 붙이고, 첫 화면에서 LVD 송출 시작을 누르십시오.")
                } else mal("준비를 끝까지 마치지 못했습니다. 위의 마지막 안내를 보시고 다시 눌러 주십시오.")
            } catch (e: Exception) {
                val m = e.message ?: ""
                mal(when {
                    m.contains("Auth", true) -> "비밀번호가 맞지 않습니다. 공유기 관리자 비밀번호를 다시 넣어 주십시오."
                    m.contains("timeout", true) || m.contains("connect", true) -> "공유기에 닿지 않습니다. 이 기기가 공유기 와이파이에 붙었는지 보십시오."
                    else -> "준비하지 못했습니다. " + m
                })
            } finally {
                try { ses?.disconnect() } catch (_: Exception) { }
                dolgo = false
            }
        }.start()
    }
}
