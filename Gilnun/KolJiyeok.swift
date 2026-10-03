// 2.39.0 (261003-K1, 이사장님 지시 "지방에서 복지콜·이동약자 차량 부르기 확인") 콜 번호 지역 알아보기
// 예전: 시·도마다 네모 테두리(위도·경도 범위)로 잘라 처음 맞는 시·도를 골랐음 — 김해가 부산으로, 가평이 강원으로, 일산이 서울로 잡히는 등 지방에서 틀림
// 이제: ① 폰의 주소 찾기(CLGeocoder, 무료·열쇠 없음)로 시·도와 시·군·구를 알아냄
//       ② 나스 call.json 2.0 의 "시군"(전국 138개 시·군 센터)에서 그 시·군 센터를 먼저, 다음에 시·도 광역센터, 다음에 전국 번호
//       ③ 통신이 끊겨 주소를 못 찾으면 마지막으로 알아낸 지역, 그것도 없으면 가장 가까운 시·군 센터(15킬로미터 안), 그다음 가장 작은 네모
//       ④ 위치를 한 번도 못 잡았으면 서울 번호를 몰래 내놓지 않고 "위치를 잡는 중"이라고 알림
// 2026년 7월 1일 광주·전남이 전남광주통합특별시가 됨 — 옛 이름과 새 이름을 모두 알아봄(광주 다섯 구는 광주 센터, 나머지는 전남)
import CoreLocation
import Foundation

struct Jiyeok: Codable, Equatable {
    var sido: String          // 정리된 시·도 이름(call.json 지역 이름과 같은 꼴)
    var sigungu: [String]     // 시·군·구 후보(예: 성남시, 분당구)
    var lat: Double
    var lon: Double
    var ttae: Date
    var balmal: String { ([sido] + sigungu.prefix(1)).joined(separator: " ") }
}

final class JiyeokEngine {
    static let shared = JiyeokEngine()
    private let geo = CLGeocoder()
    private var chatneunJung = false
    private let jeojangKi = "kol_jiyeok_majimak"
    private(set) var majimak: Jiyeok?

    static let gwangjuGu = ["동구", "서구", "남구", "북구", "광산구"]
    static let byeolching: [String: String] = [
        "서울": "서울특별시", "서울시": "서울특별시",
        "부산": "부산광역시", "대구": "대구광역시", "인천": "인천광역시", "광주": "광주광역시",
        "대전": "대전광역시", "울산": "울산광역시", "세종": "세종특별자치시", "세종시": "세종특별자치시",
        "경기": "경기도", "강원": "강원특별자치도", "강원도": "강원특별자치도",
        "충북": "충청북도", "충남": "충청남도",
        "전북": "전북특별자치도", "전라북도": "전북특별자치도",
        "전남": "전라남도", "경북": "경상북도", "경남": "경상남도",
        "제주": "제주특별자치도", "제주도": "제주특별자치도",
    ]

    init() {
        if let d = UserDefaults.standard.data(forKey: jeojangKi), let j = try? JSONDecoder().decode(Jiyeok.self, from: d) {
            majimak = j
        }
    }

    /// 시·도 이름 정리(옛 이름·줄인 이름·통합특별시)
    static func sidoJeongni(_ s: String, _ sigungu: [String]) -> String {
        let t = s.trimmingCharacters(in: .whitespaces)
        if t.contains("전남광주") || t.contains("광주전남") {
            return sigungu.contains(where: { gwangjuGu.contains($0) }) ? "광주광역시" : "전라남도"
        }
        return byeolching[t] ?? t
    }

    /// 지금 자리를 넣으면, 지난번과 800미터 넘게 떨어졌거나 10분이 지났을 때만 주소를 다시 찾음
    func gaengsin(_ w: Wichi?) {
        guard let w = w, !chatneunJung else { return }
        if let m = majimak,
           WichiEngine.geori(m.lat, m.lon, w.lat, w.lon) < 800,
           Date().timeIntervalSince(m.ttae) < 600 { return }
        chatneunJung = true
        let loc = CLLocation(latitude: w.lat, longitude: w.lon)
        geo.reverseGeocodeLocation(loc, preferredLocale: Locale(identifier: "ko_KR")) { [weak self] pm, _ in
            guard let self = self else { return }
            defer { self.chatneunJung = false }
            guard let p = pm?.first else { return }
            // 시·도가 비어 오면 특별시·광역시·특별자치시 이름이 담긴 칸에서 찾음
            let sdHubo = [p.administrativeArea, p.locality, p.subAdministrativeArea].compactMap { $0 }
            guard let sd = sdHubo.first(where: { !$0.isEmpty && ($0 == p.administrativeArea || $0.hasSuffix("특별시") || $0.hasSuffix("광역시") || $0.hasSuffix("특별자치시")) }) else { return }
            var sg: [String] = []
            for c in [p.locality, p.subAdministrativeArea, p.subLocality] {
                if let c = c, !c.isEmpty, c != sd, !sg.contains(c) { sg.append(c) }
            }
            let j = Jiyeok(sido: JiyeokEngine.sidoJeongni(sd, sg), sigungu: sg, lat: w.lat, lon: w.lon, ttae: Date())
            DispatchQueue.main.async {
                self.majimak = j
                if let d = try? JSONEncoder().encode(j) { UserDefaults.standard.set(d, forKey: self.jeojangKi) }
                Girok.shared.namgi("kol_jiyeok", ["sido": j.sido, "sg": j.sigungu.joined(separator: ",")])
            }
        }
    }
}

extension MalSajeon {
    /// 콜 번호를 고를 지역 — (시·도, 시·군·구 후보, 어떻게 알았는지)
    func kolJiyeok() -> (sido: String, sigungu: [String], gil: String)? {
        let w = WichiEngine.shared.jigeum
        JiyeokEngine.shared.gaengsin(w)
        if let m = JiyeokEngine.shared.majimak {
            if let w = w, WichiEngine.geori(m.lat, m.lon, w.lat, w.lon) > 3000 {
                // 주소 찾기가 아직 따라오지 못함 — 아래 가까운 센터로 셈
            } else {
                return (m.sido, m.sigungu, "주소")
            }
        }
        guard let w = w else {
            if let m = JiyeokEngine.shared.majimak { return (m.sido, m.sigungu, "마지막") }
            return nil
        }
        // 가장 가까운 시·군 센터(15킬로미터 안)
        if let o = kolJaryo, let sg = o["시군"] as? [[String: Any]] {
            var best: (Double, [String: Any])?
            for g in sg {
                guard let a = Chatgi.su(g["위도"]), let b = Chatgi.su(g["경도"]) else { continue }
                let d = WichiEngine.geori(w.lat, w.lon, a, b)
                if best == nil || d < best!.0 { best = (d, g) }
            }
            if let b = best, b.0 < 15000, let sd = b.1["시도"] as? String, let gu = b.1["시군구"] as? String {
                return (sd, gu.isEmpty ? [] : [gu], "가까운 센터")
            }
        }
        // 가장 작은 네모
        if let o = kolJaryo, let jy = o["지역"] as? [[String: Any]] {
            var best: (Double, String)?
            for g in jy {
                guard let s = g["상자"] as? [Any], s.count == 4,
                      let a0 = Chatgi.su(s[0]), let a1 = Chatgi.su(s[1]), let o0 = Chatgi.su(s[2]), let o1 = Chatgi.su(s[3]),
                      w.lat >= a0 && w.lat <= a1 && w.lon >= o0 && w.lon <= o1,
                      let nm = g["이름"] as? String else { continue }
                let neolbi = (a1 - a0) * (o1 - o0)
                if best == nil || neolbi < best!.0 { best = (neolbi, nm) }
            }
            if let b = best { return (b.1, [], "네모") }
        }
        return nil
    }

    /// 사람에게 들려줄 지역 이름(콜 목록 앞머리)
    func kolJiyeokMal() -> String {
        guard let j = kolJiyeok() else { return "위치를 아직 잡지 못했습니다. 잠시 뒤 다시 열어 주십시오." }
        let gu = j.sigungu.first.map { " " + $0 } ?? ""
        switch j.gil {
        case "마지막": return "위치를 새로 잡지 못해 마지막으로 확인한 \(j.sido)\(gu)의 번호입니다."
        case "가까운 센터": return "주소를 찾지 못해 가장 가까운 \(j.sido)\(gu) 센터 기준 번호입니다."
        case "네모": return "주소를 찾지 못해 대략 \(j.sido) 번호입니다."
        default: return "지금 계신 곳은 \(j.sido)\(gu)입니다."
        }
    }

    /// 지금 계신 지역의 콜 번호 — 시·군 센터 → 시·도 광역센터 → 전국 (같은 번호는 한 번만)
    func kolDeul() -> [KolBeonho] {
        var l: [KolBeonho] = []
        var bon = Set<String>()
        func neoki(_ nm: String, _ tel: String, _ bigo: String) {
            let t = tel.filter { $0.isNumber }
            guard !nm.isEmpty, !t.isEmpty, !bon.contains(t) else { return }
            bon.insert(t)
            l.append(KolBeonho(ireum: nm, jeonhwa: t, bigo: bigo))
        }
        guard let o = kolJaryo else {
            // 번호표를 한 번도 못 받았을 때만 앱 안 기본(서울) — 이름에 서울을 밝힘
            return [KolBeonho(ireum: "서울 복지콜", jeonhwa: "0220920000", bigo: "번호표를 아직 받지 못해 서울 번호입니다"),
                    KolBeonho(ireum: "서울 장애인콜택시", jeonhwa: "15884388", bigo: "서울시설공단"),
                    KolBeonho(ireum: "서울 나비콜(바우처택시)", jeonhwa: "18001133", bigo: "바우처택시 이용등록을 마친 뒤 이용")]
        }
        guard let j = kolJiyeok() else { return [] }
        if let sg = o["시군"] as? [[String: Any]] {
            for g in sg where (g["시도"] as? String) == j.sido {
                guard let gu = g["시군구"] as? String, !gu.isEmpty, j.sigungu.contains(where: { $0 == gu || $0.hasPrefix(gu) }) else { continue }
                let nm = (g["이름"] as? String) ?? (gu + " 교통약자 이동지원센터")
                var bigo = [(g["비고"] as? String) ?? ""]
                if let ap = g["앱"] as? String, !ap.isEmpty { bigo.append("앱 " + ap) }
                neoki(nm, (g["전화"] as? String) ?? "", bigo.filter { !$0.isEmpty }.joined(separator: ", "))
                for t in (g["다른전화"] as? [String]) ?? [] { neoki(nm + " 다른 번호", t, "") }
                break
            }
        }
        if let jy = o["지역"] as? [[String: Any]], let g = jy.first(where: { ($0["이름"] as? String) == j.sido }) {
            for k in (g["콜"] as? [[String: Any]]) ?? [] {
                neoki((k["이름"] as? String) ?? "", (k["전화"] as? String) ?? "", (k["비고"] as? String) ?? "")
            }
        }
        for k in (o["전국"] as? [[String: Any]]) ?? [] {
            neoki((k["이름"] as? String) ?? "", (k["전화"] as? String) ?? "", (k["비고"] as? String) ?? "")
        }
        return l
    }

    /// 콜 한 가지 찾기 — bokji, jangaein, nabi
    /// 복지콜·장애인콜은 지역마다 이름이 달라(두리발·나드리콜·새빛콜…) 이름이 맞는 것이 없으면 그 지역 첫 번호(시·군 센터)를 씀
    func kolChatgi(_ jong: String) -> KolBeonho? {
        let l = kolDeul()
        switch jong {
        case "bokji": return l.first { $0.ireum.contains("복지") } ?? l.first
        case "jangaein": return l.first { $0.ireum.contains("장애인") || $0.ireum.contains("교통약자") || $0.ireum.contains("이동지원") } ?? l.first
        default: return l.first { $0.ireum.contains("나비") || $0.ireum.contains("바우처") }
        }
    }

    /// 말 속에 지역 콜 이름(두리발, 나드리콜, 반디콜 같은)이 들어 있으면 그 번호
    func kolIreumChatgi(_ alts: [String]) -> KolBeonho? {
        let l = kolDeul()
        let mal = alts.map { MalSajeon.ttuk($0) }
        for k in l {
            let tt = k.ireum.replacingOccurrences(of: "(", with: " ").replacingOccurrences(of: ")", with: " ")
            for w in tt.split(separator: " ").map(String.init) where w.count >= 2 {
                if ["교통약자", "이동지원센터", "광역이동지원센터", "다른", "번호", "센터", "광역", "콜택시"].contains(w) { continue }
                if mal.contains(where: { $0.contains(w) }) { return k }
            }
        }
        return nil
    }
}
