// BYOD 방송 — 현장 점검 (1.1.5판, 빌드 261009-B7, 방송클, 이사장님 승인 2026-10-09)
// 단추 하나로 방송 전에 살필 것을 한 줄씩 알려 줍니다. 맨 앞 줄은 "몇 가지 가운데 몇 가지 정상"이라는 판단입니다.
// 직원이 이 화면 하나로 준비를 끝낼 수 있게 — 안 되는 줄에는 할 일을 함께 적습니다.
// 1.2.0 (261014-B9) 플린트 2 방식에서는 공유기·공유기 방송 서버·해설 소리 보내기·잠가도 이어지는 소리를 살핌
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

    // 엔에프시 태그와 큐알에 쓴 듣기 주소(접속 도구에서 쓸 때 기억)
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
        // 1.5.0 함께 나가는 인터넷 방송
        l.add(when {
            !Inteonet.hamkke(ctx) -> Jul(true, "인터넷 방송: 꺼 두셨습니다. BYOD 방송만 나갑니다.")
            Inteonet.jeonhwa(ctx).length < 9 -> Jul(false, "인터넷 방송: 현장영상해설사 전화번호가 없어 함께 나가지 않습니다. 첫 화면의 전화번호 칸에 협회 명단의 번호를 넣으십시오.")
            Bang.kyeojim -> Jul(Inteonet.jul.contains("함께 나가고"), Inteonet.jul.ifEmpty { "인터넷 방송: 준비 중입니다." })
            else -> Jul(true, "인터넷 방송: BYOD 방송을 켜면 함께 나갑니다.")
        })
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
        // 1.2.0 플린트 2 방식 — 공유기 안 방송 서버(망에 묻지 않고, 방송 중 3초마다 물은 결과로 판단)
        if (Flint.flintInga(ctx)) {
            val mang = Flint.mang(ctx) != null
            l.add(if (mang) Jul(true, "공유기: 플린트 2에 붙었습니다.") else Jul(false, "공유기: 플린트 2 와이파이(" + Flint.SSID + ")나 랜선에 붙지 않았습니다. 이 기기를 공유기에 붙이십시오."))
            if (Bang.kyeojim) {
                val dap = System.currentTimeMillis() - Flint.dapTtae < 10_000
                l.add(if (dap) Jul(true, "BYOD 방송 서버: 답합니다. " + Flint.seobeoPan + "판.")
                      else Jul(false, "BYOD 방송 서버: 답하지 않습니다. 처음 쓰는 공유기면 더 보기의 플린트 2 준비하기를 하십시오."))
                l.add(if (Flint.bonaenda) Jul(true, "해설 소리: 공유기로 보내고 있습니다.") else Jul(false, "해설 소리: 공유기로 보내지 못하고 있습니다. 공유기 전원과 와이파이를 확인하십시오."))
                if (dap) l.add(if (Flint.mp3) Jul(true, "화면을 잠가도 이어지는 소리: 쓸 수 있습니다.") else Jul(false, "화면을 잠가도 이어지는 소리: 공유기에 없습니다. 사무실에서 플린트 2 준비하기를 다시 하십시오."))
            }
        } else {
            // 5. 공유기 연결
            val juso = ByodService.jusoChatgi()
            // 1.7.0 (261021-B15) 아이피타임 방식 — 공유기 이름을 밝히고, 이 기기가 녹음하고 있는지 살핌
            l.add(if (juso.isEmpty()) Jul(false, "공유기(아이피타임): 붙지 않았습니다. 아이피타임 공유기의 랜 구멍과 허브 랜 구멍을 랜선으로 잇거나, 이 기기를 아이피타임 와이파이에 붙이십시오.")
                  else Jul(true, "공유기(아이피타임): 붙었습니다. 듣기 주소 " + juso))
            // 1.8.0 (261023-B16) 듣는 앱(LVD 방송 2.4.0부터)이 이 기기를 찾을 수 있는지 — 방송 중 10초마다 스스로 물어 본 결과
            if (Bang.kyeojim) l.add(if (System.currentTimeMillis() - Bang.mundapTtae < 25_000)
                    Jul(true, "듣는 앱이 찾을 자리: 답합니다. 같은 와이파이의 LVD 방송 앱이 이 기기를 저절로 찾습니다.")
                else Jul(false, "듣는 앱이 찾을 자리: 답하지 않습니다. 이 기기가 아이피타임 공유기에 붙었는지, 듣는 폰이 같은 아이피타임 와이파이에 붙었는지 확인하십시오."))
            if (Bang.kyeojim) l.add(if (GigiNokeum.sid.isNotEmpty() && GigiNokeum.sseunBaiteu > 0)
                    Jul(true, "행사 녹음: 이 기기가 녹음하고 있습니다. 지금까지 " + (GigiNokeum.sseunBaiteu / 16000 / 60) + "분. 방송을 멈추면 다시 듣기에 올립니다.")
                else Jul(false, "행사 녹음: 아직 녹음이 쌓이지 않습니다. 해설 소리가 들어오는지 확인하십시오."))
            // 1.9.0 (261024-B17) 현장 기능 — 피디·해설 화면 파일, 피디 번호, 자물쇠 문(무대 카메라·관계자 무전에 필요)
            val pdF = Jaryo.pail(ctx, "pd.html") != null && Jaryo.pail(ctx, "bang.html") != null
            l.add(if (pdF) Jul(true, "피디 화면과 해설 화면: 담아 두었습니다." + (if (Bang.kyeojim && Bang.pdKey.isNotEmpty()) " 피디 번호 " + Bang.pdKey + ". 첫 화면의 피디 화면 열기로 엽니다." else ""))
                  else Jul(false, "피디 화면과 해설 화면: 아직 받지 못했습니다. 인터넷이 되는 곳에서 더 보기의 새로고침을 누르십시오."))
            if (Bang.kyeojim) {
                val tm = HyeonjangSeobeo.tlsMal()
                val tp = HyeonjangSeobeo.seobeo?.tport ?: 0
                l.add(if (tp > 0) Jul(true, "자물쇠 문: " + tp + "번이 열렸습니다. 관계자 폰에서 무대 카메라와 관계자 무전을 쓸 수 있습니다. 처음 열 때 안전하지 않다는 경고가 나오면 고급, 계속을 누르십시오.")
                      else Jul(false, "자물쇠 문: 열지 못했습니다(" + tm + "). 방송은 되지만 관계자 폰의 무대 카메라와 관계자 무전은 쓸 수 없습니다. 방송을 멈췄다가 다시 시작해 보십시오."))
            }
            // 6. 엔에프시 태그 주소와 지금 주소
            val st = stickerJuso(ctx)
            l.add(when {
                juso.isEmpty() -> Jul(false, "엔에프시 태그 주소: 공유기에 붙은 뒤 견줄 수 있습니다.")
                st.isEmpty() -> Jul(false, "엔에프시 태그 주소: 이 태블릿으로 엔에프시 태그나 큐알을 만든 적이 없습니다. 더 보기의 접속 도구에서 만드십시오.")
                st == juso -> Jul(true, "엔에프시 태그 주소: 지금 주소와 같습니다. 엔에프시 태그와 큐알을 그대로 쓰면 됩니다.")
                else -> Jul(false, "엔에프시 태그 주소: 지금 주소와 다릅니다. 엔에프시 태그에는 " + st + ", 지금은 " + juso + " 입니다. 접속 도구에서 엔에프시 태그와 큐알을 새로 만드십시오.")
            })
        }
        // 1.9.0 (261024-B17) 무대 영상 녹화 — 켜 두었는지, 카메라 허락, 녹화 중이면 몇 분째인지나 문제
        run {
            val on = MudaeNokhwa.kyeojim(ctx)
            val yj = MudaeNokhwa.jul(ctx)
            val bin = MudaeNokhwa.teo(ctx).usableSpace / (1024L * 1024 * 1024)
            l.add(when {
                !on && yj.isEmpty() -> Jul(true, "무대 영상 녹화: 꺼 두셨습니다. 소리만 녹음합니다. 첫 화면 방송 시작 바로 위 단추로 켭니다.")
                MudaeNokhwa.munje.isNotEmpty() -> Jul(false, yj)
                Bang.kyeojim && MudaeNokhwa.nokhwaJung -> Jul(true, yj + ". 저장 공간은 약 " + bin + "기가바이트 남았습니다.")
                Bang.kyeojim && yj.isNotEmpty() -> Jul(true, yj)
                !MudaeNokhwa.heorak(ctx) -> Jul(false, "무대 영상 녹화: 켜 두셨지만 카메라 허락이 없습니다. 방송을 시작할 때 묻는 창에서 허용을 고르십시오.")
                bin < 2 -> Jul(false, "무대 영상 녹화: 켜 두셨지만 저장 공간이 약 " + bin + "기가바이트뿐입니다. 한 시간에 약 1기가바이트를 씁니다. 빈자리를 만들어 주십시오.")
                Bang.kyeojim -> Jul(false, "무대 영상 녹화: 켜 두셨지만 이번 방송에는 녹화하지 않고 있습니다. 단추를 껐다가 다시 켜 보십시오.")
                else -> Jul(true, "무대 영상 녹화: 켜 두셨습니다. 방송을 시작하면 뒤 카메라로 무대를 녹화합니다. 저장 공간은 약 " + bin + "기가바이트 남았습니다.")
            })
        }
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
        sb.append("LVD 송출 ").append(Bang.PAN).append("판, 빌드 ").append(Bang.BILD)
        return sb.toString()
    }
}
