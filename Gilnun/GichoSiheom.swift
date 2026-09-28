// 기초 시험 — 30분 동안 1분마다 지금 상태를 말하고 나스에 남깁니다.
// 폰을 잠근 채, 주머니 속에서, 통신을 끊은 채, 음악을 튼 채, 보이스오버를 켠 채 안내가 이어지는지 봅니다.
// 기록에는 그때 앱이 뒤에 있었는지, 폰이 잠겼는지, 음악이 나오고 있었는지를 함께 남겨 클이 실물로 확인합니다.
import SwiftUI
import Combine
import AVFoundation
import UIKit

final class GichoSiheom: ObservableObject {
    static let shared = GichoSiheom()
    let chongBun = 30

    @Published private(set) var doneunJung = false
    @Published private(set) var bun = 0
    @Published private(set) var majimakGyeolgwa = ""

    private var sigye: Timer?
    private var gamsi: Timer?
    private var ssak = Set<AnyCancellable>()
    private var wichiGyeonggoTtae: Date?
    private var sijakBatun = 0
    private var sijakMal = 0
    private var sijakGeoreum = 0
    private var sijakKkeunkim = 0

    func sijak() {
        guard !doneunJung else { return }
        let w = WichiEngine.shared
        w.sijak()
        doneunJung = true
        bun = 0
        sijakBatun = w.batunSu
        sijakMal = SoriEngine.shared.malHaneunSu
        sijakGeoreum = w.oneulGeoreum
        sijakKkeunkim = Tongsin.shared.kkeunkimSu
        SoriEngine.shared.mal("기초 시험을 시작합니다. 30분 동안 1분마다 말씀드립니다. 화면을 잠그고 주머니에 넣으셔도 됩니다.")
        Girok.shared.namgi("gicho_sijak", sangtae())
        sigye = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in self?.hanBun() }
        gamsi = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.salpigi() }
        Tongsin.shared.yeongyeolBakkwim
            .sink { [weak self] ok in
                guard let self = self, self.doneunJung else { return }
                SoriEngine.shared.mal(ok ? "통신이 다시 이어졌습니다. 쌓아 둔 기록을 보냅니다."
                                         : "통신이 끊겼습니다. 기록은 폰에 쌓아 두었다가 이어지면 보냅니다.")
            }
            .store(in: &ssak)
    }

    private func hanBun() {
        bun += 1
        let w = WichiEngine.shared
        var mal = "기초 시험 \(bun)분째. "
        if let j = w.jigeum {
            mal += j.georeumChu ? "위성이 흐려 걸음으로 이어 셈하는 중입니다. "
                                : "위성 오차 \(Int(j.ochae.rounded()))미터. "
        } else {
            mal += "아직 위치를 받지 못했습니다. "
        }
        mal += "오늘 걸음 \(w.oneulGeoreum). "
        mal += Tongsin.shared.yeongyeol ? "통신 됨." : "통신 끊김."
        SoriEngine.shared.mal(mal)
        var d = sangtae()
        d["bun"] = bun
        Girok.shared.namgi("gicho_bun", d)
        if bun >= chongBun { machim(jungdan: false) }
    }

    /// 10초마다 — 위치가 1분 넘게 안 들어오면 경고(5분에 한 번)
    private func salpigi() {
        let cho = WichiEngine.shared.majimakWiseong.map { Date().timeIntervalSince($0) } ?? 999
        guard cho > 60 else { return }
        if wichiGyeonggoTtae.map({ Date().timeIntervalSince($0) > 300 }) ?? true {
            wichiGyeonggoTtae = Date()
            SoriEngine.shared.mal("위치가 1분 넘게 들어오지 않습니다.", .gyeonggo)
            Girok.shared.namgi("gicho_wichi_meomchum", ["cho": Int(cho)])
        }
    }

    func machim(jungdan: Bool) {
        guard doneunJung else { return }
        sigye?.invalidate()
        gamsi?.invalidate()
        sigye = nil
        gamsi = nil
        ssak.removeAll()
        doneunJung = false
        let w = WichiEngine.shared
        let batun = w.batunSu - sijakBatun
        let mal = SoriEngine.shared.malHaneunSu - sijakMal
        let georeum = max(0, w.oneulGeoreum - sijakGeoreum)
        let kk = Tongsin.shared.kkeunkimSu - sijakKkeunkim
        majimakGyeolgwa = "\(jungdan ? "중간에 멈춘" : "마친") 기초 시험 \(bun)분. 위치 받은 횟수 \(batun), 길눈이 말한 횟수 \(mal), 걸음 \(georeum), 통신 끊김 \(kk)번."
        SoriEngine.shared.mal(majimakGyeolgwa)
        var d = sangtae()
        d["bun"] = bun
        d["jungdan"] = jungdan
        d["batunSu"] = batun
        d["malSu"] = mal
        d["kkeunkimSu"] = kk
        Girok.shared.namgi("gicho_kkeut", d)
        Girok.shared.jeojang()
        Girok.shared.bonaegi()
    }

    private func sangtae() -> [String: Any] {
        let app = UIApplication.shared
        let w = WichiEngine.shared
        var d: [String: Any] = [
            "dwi": app.applicationState == .background,
            "jamgim": !app.isProtectedDataAvailable,
            "vo": UIAccessibility.isVoiceOverRunning,
            "tongsin": Tongsin.shared.yeongyeol,
            "heorak": w.heorak,
            "georeum": w.oneulGeoreum,
            "batun": w.batunSu,
            "eumak": AVAudioSession.sharedInstance().isOtherAudioPlaying,
            "ssain": Girok.shared.ssainSu
        ]
        if let j = w.jigeum {
            d["ochae"] = Int(j.ochae)
            d["chu"] = j.georeumChu
            d["wichiCho"] = Int(Date().timeIntervalSince(j.ttae))
        }
        return d
    }
}

struct GichoSiheomView: View {
    @ObservedObject private var s = GichoSiheom.shared
    @ObservedObject private var w = WichiEngine.shared
    @ObservedObject private var t = Tongsin.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 긴급한 것부터 — 위치 허락이 모자라면 그것을 맨 위에
                if w.geojeoldoem {
                    Button("위치가 꺼져 있습니다 — 아이폰 설정 열기. 길눈, 위치, 항상을 골라 주십시오") {
                        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
                    }
                    .buttonStyle(KeunDanchu())
                } else if !w.hangsang {
                    Button("위치를 항상 허락하기 — 폰이 잠겨도 안내가 이어지게 합니다") {
                        w.hangsangHeorakCheong()
                    }
                    .buttonStyle(KeunDanchu())
                }
                if s.doneunJung {
                    Button("기초 시험 중 — 그만하기") { s.machim(jungdan: true) }
                        .buttonStyle(KeunDanchu())
                } else {
                    Button("기초 시험 시작 — 30분 동안 1분마다 말씀드립니다") { s.sijak() }
                        .buttonStyle(KeunDanchu())
                    if !s.majimakGyeolgwa.isEmpty {
                        Text("지난 결과 — " + s.majimakGyeolgwa)
                    }
                }
                DisclosureGroup("시험하는 법 펼치기") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("시작을 누르신 뒤 아래 다섯 가지를 차례로 해 보십시오. 1분마다 길눈이 말하면 이어지고 있는 것입니다.")
                        Text("화면을 잠그십시오.")
                        Text("폰을 주머니에 넣으십시오.")
                        Text("음악이나 라디오를 트십시오. 길눈이 말할 때만 음악 소리가 잠시 작아집니다.")
                        Text("비행기 모드를 켜서 통신을 끊어 보십시오. 위치는 계속 받고, 기록은 폰에 쌓였다가 통신이 이어지면 나스로 갑니다.")
                        Text("보이스오버를 켠 채로 해 보십시오.")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .font(.title3)
                DisclosureGroup("지금 상태 펼치기") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("시험 \(s.bun)분째")
                        Text("위치 허락 — \(w.heorak)")
                        if let j = w.jigeum {
                            Text(j.georeumChu ? "걸음으로 이어 셈하는 중" : "위성 오차 \(Int(j.ochae.rounded()))미터")
                        } else {
                            Text("아직 위치를 받지 못했습니다")
                        }
                        Text("오늘 걸음 \(w.oneulGeoreum)")
                        Text(t.yeongyeol ? "통신 됨" : "통신 끊김")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("기초 시험")
    }
}
