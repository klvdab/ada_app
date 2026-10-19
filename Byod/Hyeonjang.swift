// BYOD 방송 아이폰 — 현장 점검 (1.1.5판, 빌드 261010-BI1, 방송클 — 안드로이드 1.1.5 현장 점검과 같은 잣대)
// 단추 하나로 방송 전에 살필 것을 한 줄씩 알려 줍니다. 맨 앞 줄은 "몇 가지 가운데 몇 가지 정상"이라는 판단입니다.
// 직원이 이 화면 하나로 준비를 끝낼 수 있게 — 안 되는 줄에는 할 일을 함께 적습니다.
// 1.2.0 (261014-BI3) 플린트 2 방식에서는 공유기 방송 서버·해설 소리 보내기·잠가도 이어지는 소리를 살핌(안드로이드 1.2.0과 같음)
import Foundation
import UIKit

enum Hyeonjang {
    struct Jul { let doem: Bool; let mal: String }

    // 엔에프시 태그와 큐알에 쓴 듣기 주소(접속 도구에서 쓸 때 기억)
    static var stickerJuso: String { UserDefaults.standard.string(forKey: "sticker_juso") ?? "" }
    static func stickerGieok(_ juso: String) {
        if juso.hasPrefix("http") { UserDefaults.standard.set(juso, forKey: "sticker_juso") }
    }

    // 소리 크기 판단 — dB, 0이 가장 큼
    static func keugiMal(_ db: Int) -> Jul {
        if db <= -60 { return Jul(doem: false, mal: "소리 크기: 소리가 들어오지 않습니다. 수신기 전원과 볼륨, 수신기 선이 사운드카드 마이크 구멍에 꽂혔는지 확인하십시오.") }
        if db < -35 { return Jul(doem: false, mal: "소리 크기: 작습니다. 수신기 볼륨을 올려 주십시오.") }
        if db > -3 { return Jul(doem: false, mal: "소리 크기: 너무 커서 소리가 찢어질 수 있습니다. 수신기 볼륨을 조금 내려 주십시오.") }
        return Jul(doem: true, mal: "소리 크기: 알맞습니다.")
    }

    static func salpigi() -> [Jul] {
        let b = Bang.shared
        var l: [Jul] = []
        // 1. 방송
        l.append(b.kyeojim ? Jul(doem: true, mal: "방송: 켜져 있습니다. 듣는 분 \(b.deutnunSu)명.")
                           : Jul(doem: false, mal: "방송: 꺼져 있습니다. 첫 화면의 BYOD 방송 시작을 누르십시오. 방송을 켜야 소리 크기도 잴 수 있습니다."))
        // 1.5.0 함께 나가는 인터넷 방송
        if !InteonetBonaegi.hamkke { l.append(Jul(doem: true, mal: "인터넷 방송: 꺼 두셨습니다. BYOD 방송만 나갑니다.")) }
        else if InteonetBonaegi.jeonhwa.count < 9 { l.append(Jul(doem: false, mal: "인터넷 방송: 현장영상해설사 전화번호가 없어 함께 나가지 않습니다. 첫 화면의 전화번호 칸에 협회 명단의 번호를 넣으십시오.")) }
        else if b.kyeojim { l.append(Jul(doem: b.inteonet.contains("함께 나가고"), mal: b.inteonet.isEmpty ? "인터넷 방송: 준비 중입니다." : b.inteonet)) }
        else { l.append(Jul(doem: true, mal: "인터넷 방송: BYOD 방송을 켜면 함께 나갑니다.")) }
        // 2. 사운드카드
        l.append(Sori.usbInneunga() ? Jul(doem: true, mal: "사운드카드: 잡혔습니다.")
                 : Jul(doem: false, mal: "사운드카드: 보이지 않습니다. 카메라 어댑터를 아이폰에 끝까지 꽂았는지, 사운드카드가 어댑터에 꽂혔는지, 어댑터 충전 구멍에 충전기가 꽂혔는지 확인하십시오."))
        // 3. 실제로 소리를 받는 곳
        if b.kyeojim { l.append(Jul(doem: b.sojae.hasPrefix("USB 사운드카드"), mal: "소리 받는 곳: \(b.sojae).")) }
        // 4. 소리 크기(방송 중, 최근 5초 안에 잰 것만)
        let (db, ttae) = b.sorikeugiBogi()
        if b.kyeojim && Date().timeIntervalSince(ttae) < 5 { l.append(keugiMal(db)) }
        if Flint.flintInga {   // 1.2.0
            let f = FlintBonaegi.shared
            let butum = !Juso.deutgiJuso().isEmpty
            l.append(butum ? Jul(doem: true, mal: "공유기: 붙었습니다.") : Jul(doem: false, mal: "공유기: 붙지 않았습니다. 이 아이폰을 와이파이 \(Flint.SSID) 에 붙이십시오."))
            if b.kyeojim {
                let dap = Date().timeIntervalSince(f.dapTtae) < 10
                l.append(dap ? Jul(doem: true, mal: "BYOD 방송 서버: 답합니다. \(f.seobeoPan)판.")
                             : Jul(doem: false, mal: "BYOD 방송 서버: 답하지 않습니다. 새 공유기는 안드로이드 LVD 송출 앱의 플린트 2 준비하기로 먼저 준비하십시오."))
                l.append(f.bonaenda ? Jul(doem: true, mal: "해설 소리: 공유기로 보내고 있습니다.") : Jul(doem: false, mal: "해설 소리: 공유기로 보내지 못하고 있습니다. 공유기 전원과 와이파이를 확인하십시오."))
                if dap { l.append(f.mp3 ? Jul(doem: true, mal: "화면을 잠가도 이어지는 소리: 쓸 수 있습니다.") : Jul(doem: false, mal: "화면을 잠가도 이어지는 소리: 공유기에 없습니다. 사무실에서 플린트 2 준비하기를 다시 하십시오.")) }
            }
        } else {
        // 5. 공유기 연결
        let juso = Juso.deutgiJuso()
        l.append(juso.isEmpty ? Jul(doem: false, mal: "공유기: 붙지 않았습니다. 아이폰을 방송 공유기 와이파이에 붙이거나, 어댑터에 랜선을 꽂으십시오.")
                              : Jul(doem: true, mal: "공유기: 붙었습니다. 듣기 주소 \(juso)"))
        // 6. 엔에프시 태그 주소와 지금 주소
        let st = stickerJuso
        if juso.isEmpty { l.append(Jul(doem: false, mal: "엔에프시 태그 주소: 공유기에 붙은 뒤 견줄 수 있습니다.")) }
        else if st.isEmpty { l.append(Jul(doem: false, mal: "엔에프시 태그 주소: 이 아이폰으로 엔에프시 태그나 큐알을 만든 적이 없습니다. 더 보기의 접속 도구에서 만드십시오.")) }
        else if st == juso { l.append(Jul(doem: true, mal: "엔에프시 태그 주소: 지금 주소와 같습니다. 엔에프시 태그와 큐알을 그대로 쓰면 됩니다.")) }
        else { l.append(Jul(doem: false, mal: "엔에프시 태그 주소: 지금 주소와 다릅니다. 엔에프시 태그에는 \(st), 지금은 \(juso) 입니다. 접속 도구에서 엔에프시 태그와 큐알을 새로 만드십시오.")) }
        }
        // 7. 배터리
        let d = UIDevice.current
        d.isBatteryMonitoringEnabled = true
        let pct = d.batteryLevel < 0 ? -1 : Int((d.batteryLevel * 100).rounded())
        let chung = d.batteryState == .charging || d.batteryState == .full
        if pct < 0 { l.append(Jul(doem: false, mal: "배터리: 알 수 없습니다.")) }
        else if chung { l.append(Jul(doem: true, mal: "배터리: \(pct)퍼센트, 충전 중입니다.")) }
        else if pct >= 60 { l.append(Jul(doem: true, mal: "배터리: \(pct)퍼센트, 충전기는 꽂혀 있지 않습니다.")) }
        else { l.append(Jul(doem: false, mal: "배터리: \(pct)퍼센트, 충전기는 꽂혀 있지 않습니다. 어댑터 충전 구멍에 보조배터리를 꽂으십시오.")) }
        return l
    }

    /// 맨 앞 판단 한 줄 + 줄들
    static func geul() -> String {
        let l = salpigi()
        let an = l.filter { !$0.doem }
        var t = an.isEmpty ? "현장 점검: \(l.count)가지 모두 정상입니다.\n"
            : "현장 점검: \(l.count)가지 가운데 \(l.count - an.count)가지 정상, 확인할 것 \(an.count)가지.\n"
        for j in an { t += "확인: \(j.mal)\n" }
        for j in l where j.doem { t += "정상: \(j.mal)\n" }
        t += "LVD 송출 아이폰 \(Bang.PAN)판, 빌드 \(Bang.BILD)"
        return t
    }
}
