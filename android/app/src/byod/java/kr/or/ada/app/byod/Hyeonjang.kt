// BYOD 방송 — 현장 점검 (1.1.5판, 빌드 261009-B7, 방송클, 이사장님 승인 2026-10-09)
// 단추 하나로 방송 전에 살필 것을 한 줄씩 알려 줍니다. 맨 앞 줄은 "몇 가지 가운데 몇 가지 정상"이라는 판단입니다.
// 직원이 이 화면 하나로 준비를 끝낼 수 있게 — 안 되는 줄에는 할 일을 함께 적습니다.
package kr.or.ada.app.byod

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager

object Hyeonjang {

    data class Jul(val doem: Boolean, val mal: String)

    // 스티커와 큐알에 쓴 듣기 주소(접속 도구에서 쓸 때 기억)
    fun stickerJuso(ctx: Context): String = ctx.getSharedPreferences("byod", Context.MODE_PRIVATE).getString("sticker_juso", "") ?: ""
    fun stickerGieok(ctx: Context, juso: String) {
        if (juso.startsWith("http")) ctx.getSharedPreferences("byod", Context.MODE_PRIVATE).edit().putString("sticker_juso", juso).apply()
    }

    // 소리 크기 판단 — 이 판에서는 숫자만 보고 말로 바꿈(dB, 0이 가장 큼)
    fun keugiMal(db: Int): Jul = when {
        db <= -60 -> Jul(false, "소리 크기: 소리가 들어오지 않습니다. 수신기 전원과 볼륨, 수신기 선이 사운드카드 마이크 구멍에 꽂혔는지 확인하십시오.")
        db < -35 -> Jul(false, "소리 크기: 작습니다. 수신기 볼륨을 올려 주십시오.")
        db > -3 -> Jul(false, "소리 크기: 너무 커서 소리가 찢어질 수 있습니다. 수신기 볼륨을 조금 내려 주십시오.")
        else -> Jul(true, "소리 크기: 알맞습니다.")
    }

    fun salpigi(ctx: Context): List<Jul> {
        val l = ArrayList<Jul>()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 1. 방송
        l.add(if (Bang.kyeojim) Jul(true, "방송: 켜져 있습니다. 듣는 분 " + Bang.deutnunSu + "명.")
              else Jul(false, "방송: 꺼져 있습니다. 첫 화면의 BYOD 방송 시작을 누르십시오. 방송을 켜야 소리 크기도 잴 수 있습니다."))
        // 2. 사운드카드
        val ins = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val usbSori = ins.any { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
        val usbSu = runCatching { (ctx.getSystemService(Context.USB_SERVICE) as UsbManager).deviceList.size }.getOrDefault(0)
        l.add(when {
            usbSori -> Jul(true, "사운드카드: 잡혔습니다.")
            usbSu > 0 -> Jul(false, "사운드카드: 허브는 보이지만 사운드카드가 소리 장치로 잡히지 않습니다. 사운드카드를 뺐다가 다시 꽂으십시오. 그래도 안 되면 허브 없이 태블릿에 바로 꽂으십시오.")
            else -> Jul(false, "사운드카드: 보이지 않습니다. 허브를 태블릿에 끝까지 꽂았는지, 사운드카드가 허브에 꽂혔는지 확인하십시오.")
        })
        // 3. 실제로 소리를 받는 곳
        if (Bang.kyeojim) l.add(Jul(Bang.sojae.startsWith("USB 사운드카드"), "소리 받는 곳: " + Bang.sojae + "."))
        // 4. 소리 크기(방송 중, 최근 5초 안에 잰 것만)
        if (Bang.kyeojim && System.currentTimeMillis() - Bang.sorikeugiTtae < 5000) l.add(keugiMal(Bang.sorikeugi))
        // 5. 공유기 연결
        val juso = ByodService.jusoChatgi()
        l.add(if (juso.isEmpty()) Jul(false, "공유기: 붙지 않았습니다. 랜선을 허브에 꽂거나 태블릿을 공유기 와이파이에 붙이십시오.")
              else Jul(true, "공유기: 붙었습니다. 듣기 주소 " + juso))
        // 6. 스티커 주소와 지금 주소
        val st = stickerJuso(ctx)
        l.add(when {
            juso.isEmpty() -> Jul(false, "스티커 주소: 공유기에 붙은 뒤 견줄 수 있습니다.")
            st.isEmpty() -> Jul(false, "스티커 주소: 이 태블릿으로 스티커나 큐알을 만든 적이 없습니다. 더 보기의 접속 도구에서 만드십시오.")
            st == juso -> Jul(true, "스티커 주소: 지금 주소와 같습니다. 스티커와 큐알을 그대로 쓰면 됩니다.")
            else -> Jul(false, "스티커 주소: 지금 주소와 다릅니다. 스티커에는 " + st + ", 지금은 " + juso + " 입니다. 접속 도구에서 스티커와 큐알을 새로 만드십시오.")
        })
        // 7. 배터리
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lv = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
        val chung = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        l.add(when {
            pct < 0 -> Jul(false, "배터리: 알 수 없습니다.")
            chung -> Jul(true, "배터리: " + pct + "퍼센트, 충전 중입니다.")
            pct >= 60 -> Jul(true, "배터리: " + pct + "퍼센트, 충전기는 꽂혀 있지 않습니다.")
            else -> Jul(false, "배터리: " + pct + "퍼센트, 충전기는 꽂혀 있지 않습니다. 허브 충전 단자에 보조배터리를 꽂으십시오.")
        })
        return l
    }

    // 맨 앞 판단 한 줄 + 줄들
    fun geul(ctx: Context): String {
        val l = salpigi(ctx)
        val an = l.filter { !it.doem }
        val sb = StringBuilder()
        sb.append(if (an.isEmpty()) "현장 점검: " + l.size + "가지 모두 정상입니다."
                  else "현장 점검: " + l.size + "가지 가운데 " + (l.size - an.size) + "가지 정상, 확인할 것 " + an.size + "가지.").append("\n")
        for (j in an) sb.append("확인: ").append(j.mal).append("\n")
        for (j in l) if (j.doem) sb.append("정상: ").append(j.mal).append("\n")
        sb.append("BYOD 방송 ").append(Bang.PAN).append("판, 빌드 ").append(Bang.BILD)
        return sb.toString()
    }
}
