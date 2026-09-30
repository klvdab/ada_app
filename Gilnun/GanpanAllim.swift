// 차 안 간판 알림 — 앱 2.25.0 (빌드 261001-2, 대표님 승인 1, 설계도 [덧붙임] 차 안 간판 알림)
// 뜻: 걸을 때는 카메라가 앞장서고, 차 안에서는 지도가 앞장서고 카메라가 거듭니다.
// ① 달릴 때: 카메라 대신 위치로 — 받아 둔 카카오 열쇠(나스 ganpan.php)로 지나는 자리 둘레의 가게·건물을 모아
//    달리는 방향으로 왼쪽 오른쪽을 가려 "오른쪽에 GS25 편의점"처럼 알림. 차 안 안내(차로갈까요)의 지나는 길 안내에 붙음.
// ② 서 있거나 천천히 갈 때: 창밖 간판 읽기를 켜 두셨으면 카메라(즉석 글자 읽기)가 창밖 간판을 읽어 보탬.
// ③ 너무 잦게 떠들지 않게 간격을 두고, 한 번 알린 곳은 30분 안에 되풀이하지 않음. 켜기 끄기는 말하기 설정에.
import Foundation

final class GanpanAllim {
    static let shared = GanpanAllim()

    private var mutneunJung = false
    private var mureunT = Date.distantPast
    private var mureunJari: (Double, Double)? = nil
    private var malT = Date.distantPast
    private var malhan: [String: Date] = [:]      // 한 번 알린 곳(30분)
    private var neurinSijak: Date? = nil
    private var kameraKyeon = false               // 이 부품이 카메라를 켰는가

    /// 우선 차례 — 앞일수록 먼저 알림(식당·카페처럼 흔한 곳은 뒤로)
    private static let chare = ["지하철역", "공공기관", "관광명소", "문화시설", "마트", "은행", "약국", "병원", "주유소", "숙박", "편의점", "카페", "식당"]

    /// 차 안 안내가 자리를 받을 때마다 부름(메인)
    func chaAn(_ w: Wichi) {
        let st = Seoljeong.shared
        let now = Date()
        // ② 서 있거나 천천히 — 창밖 간판 읽기(카메라)
        if st.ganpanKamera && !w.georeumChu && w.sokdo < 3 {
            if neurinSijak == nil { neurinSijak = now }
            if let t = neurinSijak, now.timeIntervalSince(t) >= 3, !kameraKyeon, !GeulIlgi.shared.kyeojim {
                kameraKyeon = true
                GeulIlgi.shared.kyeogiChaAn()
            }
        } else {
            neurinSijak = nil
            if kameraKyeon && w.sokdo >= 5 { kameraKkeugi() }
        }
        // ① 달릴 때 — 위치로
        guard st.ganpanOn, !w.georeumChu, w.sokdo >= 4, w.banghyang >= 0, !mutneunJung else { return }
        let gan = Double(max(15, st.gilGap / 3))           // 지나는 곳 간격의 3분의 1, 적어도 15초
        guard now.timeIntervalSince(malT) >= gan, now.timeIntervalSince(mureunT) >= 8 else { return }
        if let j = mureunJari, GanpanAllim.geori(j.0, j.1, w.lat, w.lon) < 60 { return }
        mutneunJung = true
        mureunT = now
        mureunJari = (w.lat, w.lon)
        let m = w.sokdo > 15 ? "250" : "150"
        let q = ["lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon), "m": m]
        let bang = w.banghyang, sokdo = w.sokdo
        Task {
            let rows = await GanpanAllim.batgi(q)
            await MainActor.run {
                self.mutneunJung = false
                self.goreugi(rows, w.lat, w.lon, bang, sokdo)
            }
        }
    }

    /// 차에서 내리거나 안내가 끝나면
    func kkeut() {
        neurinSijak = nil
        if kameraKyeon { kameraKkeugi() }
    }

    private func kameraKkeugi() {
        kameraKyeon = false
        if GeulIlgi.shared.kyeojim { GeulIlgi.shared.kkeugi(malHagi: false) }
    }

    private static func batgi(_ q: [String: String]) async -> [[String: Any]] {
        guard let d = try? await Tongsin.shared.getSae("ganpan.php", q),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
              (o["ok"] as? Bool) == true else { return [] }
        return (o["rows"] as? [[String: Any]]) ?? []
    }

    /// 앞으로 곧 지날 왼쪽·오른쪽 곳 하나를 골라 알림
    private func goreugi(_ rows: [[String: Any]], _ la: Double, _ lo: Double, _ bang: Double, _ sokdo: Double) {
        let now = Date()
        malhan = malhan.filter { now.timeIntervalSince($0.value) < 1800 }
        var hubo: [(ireum: String, gal: String, pyeon: String, geori: Double, cha: Int)] = []
        for r in rows {
            guard let ir = r["ireum"] as? String, let gal = r["gal"] as? String,
                  let pla = (r["lat"] as? NSNumber)?.doubleValue, let plo = (r["lon"] as? NSNumber)?.doubleValue else { continue }
            let key = ir
            if malhan[key] != nil { continue }
            let g = GanpanAllim.geori(la, lo, pla, plo)
            guard g >= 15 else { continue }
            var gak = GanpanAllim.bangwi(la, lo, pla, plo) - bang
            while gak > 180 { gak -= 360 }
            while gak < -180 { gak += 360 }
            // 앞쪽 비스듬히(20~110도) — 곧 옆을 지날 곳
            let a = abs(gak)
            guard a >= 20 && a <= 110 else { continue }
            let cha = GanpanAllim.chare.firstIndex(of: gal) ?? 99
            hubo.append((ir, gal, gak > 0 ? "오른쪽" : "왼쪽", g, cha))
        }
        guard let ga = hubo.sorted(by: { $0.cha != $1.cha ? $0.cha < $1.cha : $0.geori < $1.geori }).first else { return }
        malhan[ga.ireum] = now
        malT = now
        let ireum = ga.ireum.contains(ga.gal) ? ga.ireum : "\(ga.ireum), \(ga.gal)"
        SoriEngine.shared.mal("\(ga.pyeon)에 \(ireum).", .jeongbo)
        Girok.shared.namgi("ganpan", ["gal": ga.gal, "pyeon": ga.pyeon, "sokdo": Int(sokdo * 3.6)])
    }

    // MARK: 셈

    static func geori(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let r = 6371000.0, p1 = a1 * .pi / 180, p2 = a2 * .pi / 180
        let dp = (a2 - a1) * .pi / 180, dl = (o2 - o1) * .pi / 180
        let h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /// 북쪽 기준 도(0~360)
    static func bangwi(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let p1 = a1 * .pi / 180, p2 = a2 * .pi / 180, dl = (o2 - o1) * .pi / 180
        let y = sin(dl) * cos(p2)
        let x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        let b = atan2(y, x) * 180 / .pi
        return b < 0 ? b + 360 : b
    }
}
