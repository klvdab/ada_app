// 음향신호기 울리기 — 안드로이드 (1.1판, 빌드 260927-9, 이사장님 승인 2026-09-27)
// ★1.1 (260927-9) 아이폰 1.1과 같게 — 무조건 자동으로 잡음(처음부터 켜짐, 설정에서 끔): 가까이(-80) 잡히면 위치 안내 한 번,
//   그 앞에 4초 넘게 머무르면(-65) 신호 안내 한 번, 같은 기기는 3분에 한 번. 보행신호 음성안내 장치(KPOL01+)는 5분에 한 번 알림.
// 아이폰 앱(SignalService.swift)과 같은 일을 합니다. 경찰청 「시각장애인용 음향신호기 규격서」(2022.4) 부가장치 공용 프로토콜:
//   · 블루투스를 단 음향신호기 이름은 "AHG001+" 로 시작
//   · UART 서비스 0003cdd0-0000-1000-8000-00805f9b0131 (특성 cdd1·cdd2)
//   · 명령 0x31 0x00 데이터(1 위치안내 "유", 2 신호안내 "신", 3 설치 위치 음성안내), 응답 0x32 0x00 데이터(아래 네 비트 0 이면 받음)
// 둘레를 2.5초 훑어 가장 가까운 음향신호기에 붙어 명령을 보내고, 답을 받거나 3초 지나면 끊습니다.
package kr.or.ada.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

@SuppressLint("MissingPermission")
class SignalService(private val ctx: Context) {

    companion object {
        val SERVICE: UUID = UUID.fromString("0003cdd0-0000-1000-8000-00805f9b0131")
        val CHAR_A: UUID = UUID.fromString("0003cdd1-0000-1000-8000-00805f9b0131")
        val CHAR_B: UUID = UUID.fromString("0003cdd2-0000-1000-8000-00805f9b0131")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val PREFIX = "AHG001"
        const val BOJA = "KPOL01"
        const val JADONG_KEY = "adaSinhogiJadong"

        fun boneMal(cmd: Int, hwakin: Boolean): String {
            val what = when (cmd) { 1 -> "위치 안내"; 2 -> "신호 안내"; else -> "설치 위치 안내" }
            return if (hwakin) "음향신호기가 $what 요청을 받았습니다." else "음향신호기에 $what 요청을 보냈습니다."
        }
    }

    /** 결과: (받았는지, 말) */
    var onDone: ((Boolean, String, Int) -> Unit)? = null
    /** 자동 울리기 결과 */
    var onAuto: ((Boolean, String, Int) -> Unit)? = null
    /** 알려 드릴 말 */
    var onAllim: ((String) -> Unit)? = null
    /** 찾기(이끌기) 중 가장 가까운 음향신호기: (찾았는지, 세기) */
    var onNear: ((Boolean, Int) -> Unit)? = null

    private val h = Handler(Looper.getMainLooper())
    private data class Found(val dev: BluetoothDevice, val rssi: Int, val seen: Long)
    private val found = HashMap<String, Found>()
    private var pendingCmd = 0
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var watching = false
    private var scanning = false
    private var capRun: Runnable? = null
    private var pendingAuto = false
    private var jadongOn = false
    private val lastWichi = HashMap<String, Long>()
    private val lastSinho = HashMap<String, Long>()
    private val gakkaSince = HashMap<String, Long>()
    private val lastBoja = HashMap<String, Long>()
    private val prefs get() = ctx.getSharedPreferences("ada", Context.MODE_PRIVATE)
    val jadongKyeojim: Boolean get() = prefs.getBoolean(JADONG_KEY, true)

    fun jadongSijak() { h.post { if (jadongKyeojim) { jadongOn = true; startScan() } } }
    fun setJadong(on: Boolean) {
        prefs.edit().putBoolean(JADONG_KEY, on).apply()
        h.post { jadongOn = on; if (on) startScan() else if (pendingCmd == 0 && !watching) stopScan() }
    }

    private val adapter get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val name = r.scanRecord?.deviceName ?: ""
            if (r.rssi >= 0 || r.rssi <= -100) return
            if (name.startsWith(BOJA)) {
                h.post {
                    val now = System.currentTimeMillis(); val id = r.device.address
                    if (jadongOn && r.rssi >= -80 && now - (lastBoja[id] ?: 0L) > 300000) {
                        lastBoja[id] = now; onAllim?.invoke("음성안내 장치가 있는 횡단보도 앞입니다.")
                    }
                }
                return
            }
            if (!name.startsWith(PREFIX)) return
            h.post {
                val now = System.currentTimeMillis(); val id = r.device.address
                found[id] = Found(r.device, r.rssi, now)
                if (watching) {
                    val near = found.values.filter { now - it.seen < 3000 }.maxByOrNull { it.rssi }
                    onNear?.invoke(near != null, near?.rssi ?: -100)
                }
                if (!jadongOn || pendingCmd != 0) return@post
                val best = found.values.filter { now - it.seen < 3000 }.maxByOrNull { it.rssi }
                if (best != null && best.dev.address != id) return@post
                if (r.rssi >= -65) { if (gakkaSince[id] == null) gakkaSince[id] = now } else if (r.rssi < -72) gakkaSince.remove(id)
                if (r.rssi >= -80 && now - (lastWichi[id] ?: 0L) > 180000) { lastWichi[id] = now; sendAuto(1, r.device); return@post }
                val since = gakkaSince[id]
                if (since != null && now - since >= 4000 && now - (lastSinho[id] ?: 0L) > 180000 && now - (lastWichi[id] ?: 0L) > 6000) {
                    lastSinho[id] = now; sendAuto(2, r.device)
                }
            }
        }
    }

    private fun startScan(): Boolean {
        val a = adapter ?: return false
        if (!a.isEnabled) return false
        if (scanning) return true
        return try {
            a.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
            scanning = true; true
        } catch (e: Exception) { false }
    }

    private fun stopScan() {
        if (!scanning) return
        try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (e: Exception) {}
        scanning = false
    }

    private fun cap(ms: Long, block: () -> Unit) {
        capRun?.let { h.removeCallbacks(it) }
        val r = Runnable(block); capRun = r; h.postDelayed(r, ms)
    }

    fun send(cmd: Int) {
        h.post {
            if (pendingCmd != 0) { onDone?.invoke(false, "앞의 요청을 처리하는 중입니다. 잠시 뒤 다시 눌러 주십시오.", cmd); return@post }
            val a = adapter
            if (a == null) { onDone?.invoke(false, "이 폰은 블루투스를 쓸 수 없습니다.", cmd); return@post }
            if (!a.isEnabled) { onDone?.invoke(false, "폰의 블루투스가 꺼져 있습니다. 블루투스를 켜 주십시오.", cmd); return@post }
            pendingCmd = cmd
            val now = System.currentTimeMillis()
            found.entries.removeAll { now - it.value.seen > 3000 }
            if (!startScan()) { finish(false, "블루투스로 둘레를 살필 수 없습니다. 주변 기기 허락을 확인해 주십시오."); return@post }
            cap(9000) { finish(false, "음향신호기의 답이 없습니다. 가까이 가서 다시 눌러 주십시오.") }
            h.postDelayed({ pick() }, 2500)
        }
    }

    private fun sendAuto(cmd: Int, dev: BluetoothDevice) {
        if (pendingCmd != 0) return
        pendingCmd = cmd; pendingAuto = true
        cap(8000) { finish(false, "음향신호기의 답이 없습니다.") }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        else dev.connectGatt(ctx, false, gattCb)
    }

    private fun pick() {
        if (pendingCmd == 0) return
        if (!watching && !jadongOn) stopScan()
        val now = System.currentTimeMillis()
        val best = found.values.filter { now - it.seen < 4000 }.maxByOrNull { it.rssi }
        if (best == null) { finish(false, "가까이에 블루투스 음향신호기가 없습니다. 이 횡단보도는 리모컨으로만 울릴 수 있을지 모릅니다."); return }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            best.dev.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        else best.dev.connectGatt(ctx, false, gattCb)
    }

    private fun finish(ok: Boolean, mal: String) {
        capRun?.let { h.removeCallbacks(it) }; capRun = null
        try { gatt?.disconnect(); gatt?.close() } catch (e: Exception) {}
        gatt = null; writeChar = null
        if (!watching && !jadongOn) stopScan()
        val c = pendingCmd
        if (c == 0) return
        pendingCmd = 0
        val au = pendingAuto; pendingAuto = false
        if (au) onAuto?.invoke(ok, mal, c) else onDone?.invoke(ok, mal, c)
    }

    fun watch(on: Boolean) {
        h.post {
            watching = on
            if (on) startScan() else if (pendingCmd == 0 && !jadongOn) stopScan()
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCmd(g: BluetoothGatt) {
        val wc = writeChar ?: run { h.post { finish(false, "음향신호기에 명령을 보낼 수 없습니다.") }; return }
        val data = byteArrayOf(0x31, 0x00, (pendingCmd and 0x0F).toByte())
        val type = if (wc.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(wc, data, type)
        } else {
            wc.writeType = type; wc.value = data; g.writeCharacteristic(wc)
        }
        val c = pendingCmd
        h.post { cap(3000) { finish(true, boneMal(c, false)) } }
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED && pendingCmd != 0 && writeChar == null)
                h.post { finish(false, "음향신호기에 붙지 못했습니다. 다시 눌러 주십시오.") }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val s = g.getService(SERVICE) ?: run { h.post { finish(false, "이 음향신호기는 공용 방식을 받지 않습니다.") }; return }
            val cs = listOfNotNull(s.getCharacteristic(CHAR_A), s.getCharacteristic(CHAR_B))
            writeChar = cs.firstOrNull { it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }
            val nc = cs.firstOrNull { it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 }
            if (nc != null) {
                g.setCharacteristicNotification(nc, true)
                val d = nc.getDescriptor(CCCD)
                if (d != null) {
                    val v = if (nc.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v) else { d.value = v; g.writeDescriptor(d) }
                    return   // 알림을 켠 뒤(onDescriptorWrite) 명령을 보냅니다
                }
            }
            writeCmd(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { writeCmd(g) }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) h.post { finish(false, "음향신호기가 명령을 받지 않았습니다. 다시 눌러 주십시오.") }
        }

        @Deprecated("안드로이드 13 아래")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { dap(c.value) }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { dap(value) }
    }

    private fun dap(v: ByteArray?) {
        if (v == null || v.size < 3 || v[0] != 0x32.toByte()) return
        val c = pendingCmd
        h.post {
            if (c == 0) return@post
            if ((v[2].toInt() and 0x0F) == 0) finish(true, boneMal(c, true))
            else finish(false, "음향신호기가 요청을 거절했습니다. 잠시 뒤 다시 눌러 주십시오.")
        }
    }
}
