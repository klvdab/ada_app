// 문까지 이끌기 (2.66.0, 빌드 261013-I20, 이사장님 승인 2026-10-13 「그래」)
// 10월 12일 LVD서울센터와 법인 본점에 걸어서 닿은 뒤 카메라 문 찾기가 두 번 모두 30초 만에 실패한 일 —
// 이사장님 아이폰 12에는 거리를 재는 센서(라이다)가 없어 카메라만으로는 문을 잘 못 잡음.
// 그래서 도착하면 카메라보다 먼저, 담아 둔 문 자리(내 문 → 문 창고 mun.php)로 「문은 1시 방향, 약 15걸음입니다」처럼
// 시계 방향과 걸음 수로 이끌고, 문 앞 5미터 안에 들면 카메라(문까지 모드)가 마지막 몇 걸음을 거듦.
// 담아 둔 문이 없으면 예전처럼 카메라 문 찾기를 바로 켬.
import Foundation

final class MunIkkeulgi {
    static let shared = MunIkkeulgi()

    private struct Mun { let lat: Double; let lon: Double; let ireum: String; let saengMal: String }
    private var mun: Mun?
    private var sigye: Timer?
    private var sijakT = Date()
    private var majimakMalT = Date.distantPast
    private var majimakD: Double = -1
    private var majimakSigye = -1
    private var ochaeMal = false
    private var beon = 0
    private(set) var ikkeuneunJung = false

    /// 걸어서 목적지에 닿았을 때 — 담아 둔 문을 찾아 이끌고, 없으면 카메라 문 찾기
    func sijak(lat: Double, lon: Double, kamera: Bool) {
        meomchugi()
        beon += 1
        let b0 = beon
        // 내가 담은 문이 먼저
        let nae = NaeMun.shared.mokrok
            .map { ($0, WichiEngine.geori(lat, lon, $0.lat, $0.lon)) }
            .filter { $0.1 <= 60 }
            .sorted { $0.1 < $1.1 }
            .first
        if let n = nae {
            let m = n.0
            dolligi(Mun(lat: m.lat, lon: m.lon, ireum: m.ireum.isEmpty ? "담아 두신 문" : m.ireum, saengMal: ""), b0, kamera, "nae")
            return
        }
        Task {
            let o = await Nas.get("mun.php", [("a", "near")] + Jeomjido.jari(lat, lon) + [("r", "60")])
            let rows = (o?["list"] as? [[String: Any]]) ?? []
            // 대문(doo)으로 표시된 문이 먼저, 그다음 목적지에 가까운 문
            var hubo: [(Mun, Int, Double)] = []
            for x in rows {
                guard let la = Chatgi.su(x["lat"]), let lo = Chatgi.su(x["lon"]), la != 0 else { continue }
                let nm = Nas.gul(x["ireum"]).trimmingCharacters(in: .whitespaces)
                let doo = (Chatgi.su(x["doo"]) ?? 0) > 0 ? 0 : 1
                hubo.append((Mun(lat: la, lon: lo, ireum: nm.isEmpty ? "문" : nm, saengMal: Nas.gul(x["saengMal"])), doo,
                             WichiEngine.geori(lat, lon, la, lo)))
            }
            let gol = hubo.sorted { ($0.1, $0.2) < ($1.1, $1.2) }.first?.0
            await MainActor.run {
                guard b0 == self.beon else { return }
                if let g = gol {
                    self.dolligi(g, b0, kamera, "changgo")
                } else {
                    Girok.shared.namgi("mun_ikkeul", ["mun": "eopseum"])
                    if kamera { MunChatgi.shared.kyeogi("dochak") }
                }
            }
        }
    }

    func meomchugi() {
        sigye?.invalidate()
        sigye = nil
        mun = nil
        ikkeuneunJung = false
    }

    private func dolligi(_ m: Mun, _ b0: Int, _ kamera: Bool, _ eodi: String) {
        mun = m
        sijakT = Date()
        majimakMalT = .distantPast
        majimakD = -1
        majimakSigye = -1
        ochaeMal = false
        ikkeuneunJung = true
        Girok.shared.namgi("mun_ikkeul", ["mun": eodi])
        sigye = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            self?.salpigi(b0, kamera)
        }
        salpigi(b0, kamera)
    }

    private func salpigi(_ b0: Int, _ kamera: Bool) {
        guard b0 == beon, let m = mun else { return }
        let now = Date()
        // 3분 넘게 닿지 못하면 그만 — 카메라로 넘김
        if now.timeIntervalSince(sijakT) > 180 {
            Girok.shared.namgi("mun_ikkeul_mot", [:])
            meomchugi()
            SoriEngine.shared.mal("문 안내를 마칩니다. 다시 찾으시려면 문 찾아 줘라고 말씀하십시오.")
            return
        }
        guard let w = WichiEngine.shared.jigeum else { return }
        let d = WichiEngine.geori(w.lat, w.lon, m.lat, m.lon)
        if d <= 5 {
            Girok.shared.namgi("mun_ikkeul_dochak", ["cho": Int(now.timeIntervalSince(sijakT))])
            meomchugi()
            SoriEngine.shared.mal("문 앞 가까이에 닿았습니다. \(m.ireum)입니다." + (m.saengMal.isEmpty ? "" : " \(m.saengMal)"))
            if kamera { DispatchQueue.main.asyncAfter(deadline: .now() + 3) { MunChatgi.shared.kyeogi("munkkaji") } }
            return
        }
        let bang = WichiEngine.bangwi(w.lat, w.lon, m.lat, m.lon)
        let apjjok = (w.banghyang >= 0 && w.sokdo > 0.8) ? w.banghyang : WichiEngine.shared.nachimban
        let s = apjjok >= 0 ? WichiEngine.sigyeBanghyang(jeongmyeon: apjjok, mokpyo: bang) : 0
        // 처음, 거리가 3미터 넘게 바뀌었거나 방향이 바뀌었을 때, 아니면 12초마다
        let bakkwim = majimakD < 0 || abs(d - majimakD) >= 3 || (s != 0 && s != majimakSigye)
        guard bakkwim || now.timeIntervalSince(majimakMalT) >= 12 else { return }
        guard now.timeIntervalSince(majimakMalT) >= 4 else { return }   // 너무 잦지 않게
        majimakMalT = now
        majimakD = d
        majimakSigye = s
        var t = s == 0
            ? "문까지 \(AnnaeEngine.shared.gm(d))입니다. 한두 걸음 걸으시면 방향을 알려 드립니다."
            : "문은 \(s)시 방향, \(AnnaeEngine.shared.gm(d))입니다."
        if !ochaeMal && w.ochae > 15 {
            ochaeMal = true
            t += " 위성 오차가 커서 몇 걸음 어긋날 수 있습니다."
        }
        SoriEngine.shared.mal(t)
    }
}
