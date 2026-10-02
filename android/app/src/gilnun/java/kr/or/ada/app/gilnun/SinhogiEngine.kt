// 안드로이드 길눈 — 음향신호기(2.3.0, 빌드 261002-A6, 대표님 지시: 아이폰 길눈 SinhogiEngine 을 안드로이드에도)
// 경찰청 「시각장애인용 음향신호기 규격서」(2022.4) Ⅶ 부가장치 공용 프로토콜로 폰이 블루투스로 울립니다. 아이폰과 같은 잣대, 같은 시간, 같은 말.
//   · 건널목 앞에서 폰을 꺼내 단추를 찾게 하지 않음 — 길눈이 켜져 있으면 화면이 꺼져도 둘레의 음향신호기를 늘 살피고 스스로 울림(처음부터 켜짐, 설정에서 끔)
//   · 가까이(세기 -80 이상) 잡히면 "위치 안내" 한 번, 그 앞에 4초 넘게 머무르면(-65 이상) "신호 안내" 한 번, 같은 신호기에는 3분에 한 번
//   · 자동으로 울릴 때 길눈은 말하지 않고 짧게 진동만(신호기가 소리를 냄)
//   · 보행신호 음성안내 장치(이름 KPOL01)가 잡히면 "음성안내 장치가 있는 횡단보도 앞입니다"를 5분에 한 번
//   · 손으로 울리기: 길 찾기 첫 화면의 음향신호기 펼치기(위치 안내, 신호 안내)
//   · 음향신호기 찾기: 가까워질수록 확신음이 빨라짐, 바로 앞이면 알리고 위치 안내를 울림
//   · 남산 현장 확인을 위한 기록: 신호기·음성안내 장치가 잡히면 한 기기에 한 번(앱을 켤 때마다) sinhogi_chatgi — 기기 주소는 그대로 남기지 않고 sha256 앞 10자리만
// 규격: 이름 "AHG001", 서비스 0003cdd0-0000-1000-8000-00805f9b0131(특성 cdd1·cdd2), 명령 0x31 0x00 (1 위치안내 / 2 신호안내 / 3 설치 위치 음성안내),
//       답 0x32 0x00 (아래 네 비트가 0 이면 받음). 옛 껍데기 앱의 SignalService.kt 와 규격·잣대가 같음.
// 안드로이드에 맞춘 것:
//   · 살피기는 끊지 않고 이어 훑음(아이폰처럼). 자동일 때는 배터리를 아끼는 보통 빠르기(BALANCED), 손으로 울리기·찾기 동안만 가장 빠르게
//   · 화면이 꺼지면 안드로이드는 거르개 없는 훑기를 막으므로 공용 서비스 번호와 정해진 이름(AHG001, KPOL01)으로 거름(아이폰 잠김과 같은 뜻)
//   · 안드로이드는 화면이 꺼져도 같은 기기를 거듭 알려 주므로 아이폰 2.12.0 의 "잠겨 있으면 한 번 잡혀도 신호 안내" 지름길은 쓰지 않고 4초 머무름으로 가림
//   · 30분 넘게 훑으면 안드로이드가 느리게 바꾸므로 10분마다 다시 열고, 30초에 다섯 번 넘게 새로 열지 않음(안드로이드가 막음)
// 2.5.0(빌드 261002-A8, 대표님 지시) 손으로 울린 결과를 갤럭시 워치에도 알림(WatchLink.sinhogiDap)
// 모든 상태는 화면 줄(main) 하나에서만 만집니다 — 블루투스 답은 Handler 로 넘겨 받음.
package kr.or.ada.app.gilnun

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.UUID

@SuppressLint("MissingPermission")   // 허락은 heorakItda 로 그때그때 확인하고, 막히면 try 로 받음
object SinhogiEngine {
    val SERVICE: UUID = UUID.fromString("0003cdd0-0000-1000-8000-00805f9b0131")
    private val CHAR_A: UUID = UUID.fromString("0003cdd1-0000-1000-8000-00805f9b0131")
    private val CHAR_B: UUID = UUID.fromString("0003cdd2-0000-1000-8000-00805f9b0131")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val AP_MAL = "AHG001"
    const val BOJA_MAL = "KPOL01"

    const val KKEOJIM_MAL = "폰의 블루투스가 꺼져 있습니다. 블루투스를 켜 주십시오."
    val heorakMal: String
        get() = if (Build.VERSION.SDK_INT >= 31) "블루투스 사용을 허락하지 않으셨습니다. 폰 설정의 앱, 길눈, 권한에서 근처 기기와 위치를 허용해 주십시오."
        else "블루투스로 음향신호기를 찾으려면 위치 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오."

    private var ctx: Context? = null
    private val h = Handler(Looper.getMainLooper())
    private var sijakham = false

    private class Chajeun(val dev: BluetoothDevice, val rssi: Int, val ttae: Long)
    private class Pending(val cmd: Int, val jadong: Boolean, val done: (Boolean, String) -> Unit)

    private val chajeun = HashMap<String, Chajeun>()
    private var pending: Pending? = null
    private var gatt: BluetoothGatt? = null
    private var sseulGot: BluetoothGattCharacteristic? = null
    private var bonaem = false
    private var hanRun: Runnable? = null
    private var jadongOn = false
    private var dwi = false
    private val lastWichi = HashMap<String, Long>()
    private val lastSinho = HashMap<String, Long>()
    private val gakkaSince = HashMap<String, Long>()
    private val lastBoja = HashMap<String, Long>()
    private val namgin = HashSet<String>()          // 이번에 기록한 기기(앱을 켤 때마다 새로) — 폰 안에만
    private var kyeojimyeon: (() -> Unit)? = null    // 블루투스가 켜지면 이어 할 일(손으로 울리기)
    private var kyeojimyeonTtae = 0L

    // 찾기(이끌기)
    var chatneunJung = false
        private set
    private var chatgiSijak = 0L
    private var chatgiSori = 0L
    private var eopdaMal = false
    private var chatgiRun: Runnable? = null

    // 훑기
    private var hunneunJung = false
    private var hunneun: String? = null              // 지금 훑는 방식 — 같은 방식이면 다시 열지 않음
    private val yeolinTtae = ArrayList<Long>()       // 훑기를 새로 연 때(30초에 다섯 번 막힘 피하기)
    private var dasiRun: Runnable? = null
    private var gaengsinRun: Runnable? = null

    private val salpim: Boolean get() = jadongOn || chatneunJung || pending != null

    // MARK: 세우기

    fun sijak(c: Context) {
        ctx = c.applicationContext
        if (sijakham) { saerogochim(); return }
        sijakham = true
        try {
            ContextCompat.registerReceiver(c.applicationContext, object : BroadcastReceiver() {
                override fun onReceive(cx: Context?, i: Intent?) {
                    val st = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) ?: -1
                    h.post { blBakkwim(st) }
                }
            }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Girok.namgi("sinhogi_oryu", mapOf("e" to (e.message ?: "")))
        }
        h.post {
            jadongOn = Seoljeong.sinhogiJadong
            dasiSalpim()
        }
    }

    /** 자동으로 잡기 켜기/끄기(설정) */
    fun jadongKyeogi(on: Boolean) {
        h.post {
            jadongOn = on
            dasiSalpim()
            Girok.namgi("sinhogi_jadong", mapOf("on" to on))
        }
    }

    /** 화면이 보이는가(앞) 꺼졌는가(뒤) — 화면이 알려 줌 */
    fun dwiKyeogi(d: Boolean) {
        h.post {
            if (dwi == d) return@post
            dwi = d
            dasiSalpim()
        }
    }

    /** 허락을 받았거나 새로고침 — 살피기를 처음부터 다시 */
    fun saerogochim() {
        h.post {
            jadongOn = Seoljeong.sinhogiJadong
            hunneun = null
            dasiSalpim()
        }
    }

    private val adapter: BluetoothAdapter?
        get() = (ctx?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** 이 폰에 블루투스가 있는가 */
    val giginItda: Boolean get() = adapter != null

    /** 블루투스가 켜져 있는가 */
    val kyeojim: Boolean
        get() = try { adapter?.isEnabled == true } catch (e: Exception) { false }

    /** 블루투스로 살피는 데 필요한 허락 — 안드로이드 12 이상은 근처 기기(훑기·붙기)와 위치, 그 아래는 위치 */
    fun pilyoHeorak(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    val heorakItda: Boolean
        get() {
            val c = ctx ?: return false
            return pilyoHeorak().filter { it != Manifest.permission.ACCESS_COARSE_LOCATION }
                .all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
        }

    /** 블루투스가 60초 안에 켜지면 이어 할 일(손으로 울리려다 꺼져 있던 때) */
    fun kyeojimyeonHagi(f: () -> Unit) {
        h.post { kyeojimyeon = f; kyeojimyeonTtae = System.currentTimeMillis() }
    }

    private fun blBakkwim(st: Int) {
        if (st == BluetoothAdapter.STATE_ON) {
            Girok.namgi("sinhogi_bl", mapOf("on" to true))
            hunneunJung = false; hunneun = null
            dasiSalpim()
            val f = kyeojimyeon
            kyeojimyeon = null
            if (f != null && System.currentTimeMillis() - kyeojimyeonTtae < 60000) {
                Sori.mal("블루투스가 켜졌습니다.")
                h.postDelayed({ f() }, 1500)
            }
        } else if (st == BluetoothAdapter.STATE_OFF) {
            Girok.namgi("sinhogi_bl", mapOf("on" to false))
            hunneunJung = false; hunneun = null
            maechim(false, KKEOJIM_MAL)
            if (chatneunJung) chatgiKkeugiNow(null)
        }
    }

    // MARK: 훑기

    /** 살피기를 지금 형편에 맞춰 다시 — 앞에서는 모든 기기를, 뒤(화면 꺼짐)에서는 공용 번호·정해진 이름만 */
    private fun dasiSalpim() {
        dasiRun?.let { h.removeCallbacks(it) }
        dasiRun = null
        val a = adapter
        if (!salpim || a == null || !kyeojim || !heorakItda) { hunKkeugi(); return }
        val bbareun = chatneunJung || pending != null
        val bang = (if (dwi) "dwi" else "ap") + (if (bbareun) "-bbareum" else "")
        if (hunneunJung && hunneun == bang) return
        val now = System.currentTimeMillis()
        yeolinTtae.removeAll { now - it > 30000 }
        if (yeolinTtae.size >= 4) {
            // 30초에 다섯 번 넘게 새로 열면 안드로이드가 막으므로 — 하던 훑기는 그대로 두고 조금 뒤에 바꿈
            val r = Runnable { dasiSalpim() }
            dasiRun = r
            h.postDelayed(r, 30000 - (now - yeolinTtae[0]) + 300)
            return
        }
        hunKkeugi()
        val sc = try { a.bluetoothLeScanner } catch (e: Exception) { null } ?: return
        val seoljeong = ScanSettings.Builder()
            .setScanMode(if (bbareun) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            .build()
        val georeugae: List<ScanFilter>? = if (dwi) listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build(),
            ScanFilter.Builder().setDeviceName(AP_MAL).build(),
            ScanFilter.Builder().setDeviceName(BOJA_MAL).build()
        ) else null
        try {
            sc.startScan(georeugae, seoljeong, scanCb)
            hunneunJung = true
            hunneun = bang
            yeolinTtae.add(now)
            val r = Runnable { hunneun = null; dasiSalpim() }   // 10분마다 다시 열기(30분 넘으면 안드로이드가 느리게 바꿈)
            gaengsinRun = r
            h.postDelayed(r, 600000)
        } catch (e: Exception) {
            Girok.namgi("sinhogi_hun_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    private fun hunKkeugi() {
        gaengsinRun?.let { h.removeCallbacks(it) }
        gaengsinRun = null
        if (hunneunJung) {
            try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (e: Exception) {}
        }
        hunneunJung = false
        hunneun = null
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) { h.post { batda(r) } }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            val l = ArrayList(results)
            h.post { for (r in l) batda(r) }
        }
        override fun onScanFailed(errorCode: Int) {
            h.post {
                Girok.namgi("sinhogi_hun_silpae", mapOf("code" to errorCode))
                if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) return@post
                gaengsinRun?.let { h.removeCallbacks(it) }
                gaengsinRun = null
                hunneunJung = false
                hunneun = null
                dasiRun?.let { h.removeCallbacks(it) }
                val r = Runnable { dasiSalpim() }
                dasiRun = r
                h.postDelayed(r, 30000)
            }
        }
    }

    private fun batda(r: ScanResult) {
        val rssi = r.rssi
        if (rssi >= 0 || rssi <= -100) return
        val rec = r.scanRecord
        val ireum = rec?.deviceName ?: (try { r.device.name } catch (e: Exception) { null }) ?: ""
        val id = r.device.address ?: return
        val now = System.currentTimeMillis()
        if (ireum.startsWith(BOJA_MAL)) {
            chatgiNamgi("boja", ireum, id, rssi)
            if (jadongOn && rssi >= -80 && now - (lastBoja[id] ?: 0L) > 300000) {
                lastBoja[id] = now
                Sori.mal("음성안내 장치가 있는 횡단보도 앞입니다.")
                Girok.namgi("sinhogi_boja")
            }
            return
        }
        val gongyong = rec?.serviceUuids?.contains(ParcelUuid(SERVICE)) == true
        if (!ireum.startsWith(AP_MAL) && !gongyong) return
        chatgiNamgi("sinhogi", ireum, id, rssi)
        chajeun[id] = Chajeun(r.device, rssi, now)
        if (!jadongOn || pending != null || chatneunJung) return
        // 가장 센 것 하나에만
        val best = chajeun.values.filter { now - it.ttae < 3000 }.maxByOrNull { it.rssi }
        if (best != null && best.dev.address != id) return
        if (rssi >= -65) {
            if (gakkaSince[id] == null) gakkaSince[id] = now
        } else if (rssi < -72) {
            gakkaSince.remove(id)
        }
        if (rssi >= -80 && now - (lastWichi[id] ?: 0L) > 180000) {
            lastWichi[id] = now
            jadongBonaegi(1, r.device)
            return
        }
        val since = gakkaSince[id]
        val meomum = since != null && now - since >= 4000
        if (meomum && now - (lastSinho[id] ?: 0L) > 180000 && now - (lastWichi[id] ?: 0L) > 6000) {
            lastSinho[id] = now
            jadongBonaegi(2, r.device)
        }
    }

    /** 남산 현장 확인 기록 — 한 기기에 한 번. 기기 주소는 sha256 앞 10자리만, 자리는 소수 여섯째 자리까지 */
    private fun chatgiNamgi(jong: String, ireum: String, id: String, rssi: Int) {
        if (!namgin.add(id)) return
        val j = Wichi.jigeum
        Girok.namgi("sinhogi_chatgi", mapOf(
            "jong" to jong,
            "name" to ireum,
            "addr" to hash10(id),
            "rssi" to rssi,
            "lat" to j?.let { Math.round(it.lat * 1e6) / 1e6 },
            "lon" to j?.let { Math.round(it.lon * 1e6) / 1e6 },
            "ochae" to j?.ochae?.toInt()
        ))
    }

    private fun hash10(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it.toInt() and 0xFF) }.take(10)

    /** 이어폰·리모컨 단추를 음향신호기 앞에서 눌렀는가 — 5초 안에 세기 -75 이상으로 잡힌 것이 있으면 참 */
    fun apeIssna(): Boolean {
        val n = System.currentTimeMillis()
        return chajeun.values.any { n - it.ttae < 5000 && it.rssi >= -75 }
    }

    // MARK: 보내기

    /** 손으로 울리기 — 결과를 말하고 진동 */
    fun ulligi(cmd: Int) {
        Girok.namgi("sinhogi_son", mapOf("cmd" to cmd))
        bonaegi(cmd) { ok, mal ->
            Sori.mal(mal)
            jindong(if (ok) "arrive" else "long")
            WatchLink.sinhogiDap(ok, mal)   // 2.5.0 워치에서 울렸을 때 워치도 알게
        }
    }

    private fun bonaegi(cmd: Int, done: (Boolean, String) -> Unit) {
        h.post {
            if (pending != null) { done(false, "앞의 요청을 처리하는 중입니다. 잠시 뒤 다시 해 주십시오."); return@post }
            if (adapter == null) { done(false, "이 기기는 블루투스를 쓸 수 없습니다."); return@post }
            if (!heorakItda) { done(false, heorakMal); return@post }
            if (!kyeojim) { done(false, KKEOJIM_MAL); return@post }
            pending = Pending(cmd, false, done)
            val now = System.currentTimeMillis()
            chajeun.entries.removeAll { now - it.value.ttae >= 3000 }
            dasiSalpim()
            h.postDelayed({ goruGoBuchigi() }, 2500)
            han(9000) { maechim(false, "음향신호기의 답이 없습니다. 가까이 가서 다시 해 주십시오.") }
        }
    }

    private fun jadongBonaegi(cmd: Int, dev: BluetoothDevice) {
        if (pending != null) return
        pending = Pending(cmd, true) { ok, _ -> if (ok) jindong("short") }
        han(8000) { maechim(false, "음향신호기의 답이 없습니다.") }
        Girok.namgi("sinhogi_jadong_bonaem", mapOf("cmd" to cmd))
        buchigi(dev)
    }

    private fun goruGoBuchigi() {
        if (pending == null || gatt != null) return
        val now = System.currentTimeMillis()
        val best = chajeun.values.filter { now - it.ttae < 4000 }.maxByOrNull { it.rssi }
        if (best == null) {
            maechim(false, "가까이에 블루투스 음향신호기가 잡히지 않습니다. 이 횡단보도는 리모컨으로만 울릴 수 있을지 모릅니다.")
            return
        }
        buchigi(best.dev)
    }

    private fun buchigi(dev: BluetoothDevice) {
        val c = ctx ?: run { maechim(false, "음향신호기에 붙지 못했습니다. 다시 해 주십시오."); return }
        bonaem = false
        sseulGot = null
        val g = try {
            if (Build.VERSION.SDK_INT >= 23) dev.connectGatt(c, false, gattCb, BluetoothDevice.TRANSPORT_LE)
            else dev.connectGatt(c, false, gattCb)
        } catch (e: Exception) { null }
        if (g == null) { maechim(false, "음향신호기에 붙지 못했습니다. 다시 해 주십시오."); return }
        gatt = g
    }

    private fun han(ms: Long, f: () -> Unit) {
        hanRun?.let { h.removeCallbacks(it) }
        val r = Runnable { f() }
        hanRun = r
        h.postDelayed(r, ms)
    }

    private fun maechim(ok: Boolean, mal: String) {
        hanRun?.let { h.removeCallbacks(it) }
        hanRun = null
        val g = gatt
        gatt = null
        sseulGot = null
        bonaem = false
        if (g != null) { try { g.disconnect(); g.close() } catch (e: Exception) {} }
        val p = pending ?: return
        pending = null
        dasiSalpim()
        if (!p.jadong) Girok.namgi("sinhogi_gyeolgwa", mapOf("ok" to ok, "cmd" to p.cmd))
        p.done(ok, mal)
    }

    fun boneMal(cmd: Int, hwagin: Boolean): String {
        val what = when (cmd) { 1 -> "위치 안내"; 2 -> "신호 안내"; else -> "설치 위치 안내" }
        return if (hwagin) "음향신호기가 $what 요청을 받았습니다." else "음향신호기에 $what 요청을 보냈습니다."
    }

    private fun myeongryeong(g: BluetoothGatt) {
        if (bonaem) return
        val wc = sseulGot
        val p = pending
        if (wc == null || p == null) { maechim(false, "음향신호기에 명령을 보낼 수 없습니다."); return }
        bonaem = true
        val data = byteArrayOf(0x31, 0x00, (p.cmd and 0x0F).toByte())
        val type = if (wc.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (!sseugi(g, wc, data, type)) { maechim(false, "음향신호기가 명령을 받지 않았습니다. 다시 해 주십시오."); return }
        // 답이 오지 않는 기기도 있어 3초 기다린 뒤에는 보냈다고만 알림
        val c = p.cmd
        han(3000) { maechim(true, boneMal(c, false)) }
    }

    @Suppress("DEPRECATION")
    private fun sseugi(g: BluetoothGatt, wc: BluetoothGattCharacteristic, data: ByteArray, type: Int): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(wc, data, type) == BluetoothStatusCodes.SUCCESS
            } else {
                wc.writeType = type
                wc.value = data
                g.writeCharacteristic(wc)
            }
        } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    private fun alrimKyeogi(g: BluetoothGatt, d: BluetoothGattDescriptor, v: ByteArray): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS
            } else {
                d.value = v
                g.writeDescriptor(d)
            }
        } catch (e: Exception) { false }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            h.post {
                if (g !== gatt) {
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) { try { g.close() } catch (e: Exception) {} }
                    return@post
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val ok = try { g.discoverServices() } catch (e: Exception) { false }
                    if (!ok) maechim(false, "음향신호기에 붙지 못했습니다. 다시 해 주십시오.")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !bonaem) {
                    maechim(false, "음향신호기에 붙지 못했습니다. 다시 해 주십시오.")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            h.post {
                if (g !== gatt) return@post
                val s = g.getService(SERVICE)
                if (s == null) { maechim(false, "이 음향신호기는 공용 방식을 받지 않습니다."); return@post }
                val cs = listOfNotNull(s.getCharacteristic(CHAR_A), s.getCharacteristic(CHAR_B))
                // 쓰기가 되는 특성으로 명령을 보내고, 알림이 되는 특성으로 답을 받음(규격서의 TX·RX 이름은 기기 쪽 기준이라 성질로 가림)
                sseulGot = cs.firstOrNull {
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                }
                if (sseulGot == null) { maechim(false, "음향신호기에 명령을 보낼 수 없습니다."); return@post }
                val nc = cs.firstOrNull {
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                }
                if (nc != null) {
                    try { g.setCharacteristicNotification(nc, true) } catch (e: Exception) {}
                    val d = nc.getDescriptor(CCCD)
                    if (d != null) {
                        val v = if (nc.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (alrimKyeogi(g, d, v)) return@post   // 알림을 켠 뒤(onDescriptorWrite) 명령을 보냄
                    }
                }
                myeongryeong(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            h.post { if (g === gatt) myeongryeong(g) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            h.post {
                if (g === gatt && status != BluetoothGatt.GATT_SUCCESS)
                    maechim(false, "음향신호기가 명령을 받지 않았습니다. 다시 해 주십시오.")
            }
        }

        @Deprecated("안드로이드 13 아래")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            dap(g, c.value?.copyOf())
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            dap(g, value.copyOf())
        }
    }

    private fun dap(g: BluetoothGatt, v: ByteArray?) {
        if (v == null || v.size < 3 || v[0] != 0x32.toByte()) return
        h.post {
            if (g !== gatt) return@post
            val p = pending ?: return@post
            if ((v[2].toInt() and 0x0F) == 0) maechim(true, boneMal(p.cmd, true))
            else maechim(false, "음향신호기가 요청을 거절했습니다. 잠시 뒤 다시 해 주십시오.")
        }
    }

    // MARK: 찾기(이끌기)

    fun chatgiKyeogi() {
        juljul {
            if (chatneunJung) return@juljul
            chatneunJung = true
            chatgiSijak = System.currentTimeMillis()
            chatgiSori = 0L
            eopdaMal = false
            dasiSalpim()
            chatgiRun?.let { h.removeCallbacks(it) }
            val r = object : Runnable {
                override fun run() {
                    if (!chatneunJung) return
                    chatgiBoda()
                    if (chatneunJung) h.postDelayed(this, 200)
                }
            }
            chatgiRun = r
            h.postDelayed(r, 200)
            Girok.namgi("sinhogi_chatgi_kyeogi")
        }
    }

    fun chatgiKkeugi(mal: String? = null) { juljul { chatgiKkeugiNow(mal) } }

    private fun chatgiKkeugiNow(mal: String?) {
        if (!chatneunJung) return
        chatneunJung = false
        chatgiRun?.let { h.removeCallbacks(it) }
        chatgiRun = null
        dasiSalpim()
        if (mal != null) Sori.mal(mal)
    }

    private fun chatgiBoda() {
        val n = System.currentTimeMillis()
        if (n - chatgiSijak > 60000) {
            chatgiKkeugiNow("음향신호기 찾기를 마칩니다.")
            return
        }
        val best = chajeun.values.filter { n - it.ttae < 3000 }.maxByOrNull { it.rssi }
        if (best == null) {
            if (!eopdaMal && n - chatgiSijak > 6000) {
                eopdaMal = true
                Sori.mal("가까이에 음향신호기가 아직 잡히지 않습니다. 천천히 둘러보십시오.")
            }
            return
        }
        if (best.rssi >= -55) {
            chatgiKkeugiNow("음향신호기 바로 앞입니다.")
            h.postDelayed({ ulligi(1) }, 2500)
            return
        }
        val gan = if (best.rssi >= -62) 350L else if (best.rssi >= -70) 700L else if (best.rssi >= -80) 1200L else 2000L
        if (n - chatgiSori >= gan && !Sori.malhaneunJung) {
            chatgiSori = n
            Eum.naegi(EumJong.HWAKSIN)
        }
    }

    // MARK: 진동

    /** short 짧게(자동으로 울렸을 때) · arrive 세 번(받음) · long 길게(안 됨) */
    @Suppress("DEPRECATION")
    private fun jindong(jong: String) {
        val c = ctx ?: return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            else c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val gil = when (jong) {
                "short" -> longArrayOf(0, 70)
                "arrive" -> longArrayOf(0, 90, 90, 90, 90, 200)
                else -> longArrayOf(0, 450)
            }
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(gil, -1)) else v?.vibrate(gil, -1)
        } catch (e: Exception) {}
    }

    /** 화면 줄에서 부르면 곧바로, 아니면 화면 줄로 넘겨서 */
    private fun juljul(f: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) f() else h.post { f() }
    }
}
