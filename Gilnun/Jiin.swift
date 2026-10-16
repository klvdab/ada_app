// 가족·지인 명단 — 나스 /eyec/jiin.php(웹 길눈과 같은 명부). 서버에는 이름과 열쇠만, 전화번호는 폰 키체인에만.
// 주인 열쇠는 키체인(Yeolsoe.juin)에 있어 앱을 지웠다 다시 깔아도 명단이 이어집니다.
import Foundation

struct JiinSaram: Identifiable, Hashable {
    let id: String
    let name: String
    let k: String
    let state: String
    var badeum: Bool { state == "받음" }
}

final class Jiin: ObservableObject {
    static let shared = Jiin()
    @Published private(set) var mokrok: [JiinSaram] = []
    @Published private(set) var bulreoom = false
    let owner = Yeolsoe.juin
    private let PHP = "/eyec/jiin.php"

    /// 받는 분께 보낼 초대 주소 — 한 번 열어 "받겠습니다"를 누르시면 끝
    static func chodaeJuso(_ k: String) -> URL {
        URL(string: "https://lvd.ada.or.kr/eyec/badgi.html?k=" + (k.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? k))!
    }

    @discardableResult
    func bureogi() async -> Bool {
        guard let d = try? await Tongsin.shared.getSae(PHP, ["a": "list", "owner": owner]),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any], (o["ok"] as? Bool) == true else {
            return false
        }
        let l: [JiinSaram] = ((o["jiin"] as? [[String: Any]]) ?? []).map {
            JiinSaram(id: ($0["id"] as? String) ?? "", name: ($0["name"] as? String) ?? "",
                      k: ($0["k"] as? String) ?? "", state: ($0["state"] as? String) ?? "기다리는 중")
        }
        await MainActor.run {
            self.mokrok = l
            self.bulreoom = true
        }
        return true
    }

    /// 새 사람 만들기 — (사람, 못 한 까닭)
    func mandeulgi(_ name: String, _ tel: String) async -> (JiinSaram?, String) {
        guard let d = try? await Tongsin.shared.getSae(PHP, ["a": "man", "owner": owner, "name": name]),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
            return (nil, "만들지 못했습니다. 통신이 끊겼을 수 있습니다.")
        }
        guard (o["ok"] as? Bool) == true, let id = o["id"] as? String, let k = o["k"] as? String else {
            return (nil, (o["error"] as? String) ?? "만들지 못했습니다.")
        }
        if !tel.isEmpty { telNoki(id, tel) }
        Girok.shared.namgi("jiin_man", [:])
        await bureogi()
        return (JiinSaram(id: id, name: name, k: k, state: "기다리는 중"), "")
    }

    // MARK: 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 열 분까지·이음 번호 등록 살피기·별명

    /// 가족·지인은 열 분까지
    static let HANDO_SU = 10
    static let gadeukMal = "열 분이 모두 찼습니다. 한 분을 빼셔야 새로 등록됩니다."

    /// 받은 이음 번호와 받은 때 — 명단 화면을 떠나도 30분 동안 등록을 살핌(안드로이드와 같게)
    @Published private(set) var beonho = ""
    @Published private(set) var beonhoTtae = Date.distantPast
    /// 새로 등록하신 분 알림 글
    @Published var deungrokAllim = ""
    private var salpim: Timer?
    private var bonIds: Set<String> = []

    var beonhoSalda: Bool { !beonho.isEmpty && Date().timeIntervalSince(beonhoTtae) < 1800 }

    /// 이음 번호를 받은 뒤 30분 동안 5초마다 명단을 살펴 새로 등록하신 분을 알려 드림
    func beonhoNoki(_ b: String) {
        beonho = b
        beonhoTtae = Date()
        deungrokAllim = ""
        bonIds = Set(mokrok.map { $0.id })
        salpim?.invalidate()
        salpim = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] t in
            guard let self = self else { t.invalidate(); return }
            if Date().timeIntervalSince(self.beonhoTtae) > 1800 { t.invalidate(); self.salpim = nil; return }
            Task {
                guard await self.bureogi() else { return }
                await MainActor.run {
                    let sae = self.mokrok.filter { !self.bonIds.contains($0.id) }
                    for p in sae {
                        SoriEngine.shared.mal("\(p.name) 님이 가족·지인으로 등록하셨습니다.")
                        Girok.shared.namgi("jiin_ieum_deungrok", [:])
                    }
                    if !sae.isEmpty {
                        self.beonho = ""
                        self.salpim?.invalidate()
                        self.salpim = nil
                        self.deungrokAllim = sae.map { "\($0.name) 님이 등록하셨습니다." }.joined(separator: " ")
                    }
                    self.bonIds = Set(self.mokrok.map { $0.id })
                }
            }
        }
    }

    /// 별명 — 폰 안에만(UserDefaults "gn.jiinByeol", id → [별명]). 나스에는 보내지 않음
    @Published private(set) var byeolMokrok: [String: [String]] =
        (UserDefaults.standard.dictionary(forKey: "gn.jiinByeol") as? [String: [String]]) ?? [:]

    func byeol(_ id: String) -> [String] { byeolMokrok[id] ?? [] }

    func byeolNoki(_ id: String, _ l: [String]) {
        let ll = l.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        var m = byeolMokrok
        if ll.isEmpty { m.removeValue(forKey: id) } else { m[id] = ll }
        byeolMokrok = m
        UserDefaults.standard.set(m, forKey: "gn.jiinByeol")
    }

    /// 쉼표로 나눈 별명 글 → 별명들
    static func byeolPulgi(_ t: String) -> [String] {
        t.split(whereSeparator: { $0 == "," || $0 == "，" || $0 == "、" }).map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    /// 목록 줄 이름 — 「이름 (별명: 큰딸, 우리 딸)」
    func julIreum(_ s: JiinSaram) -> String {
        let b = byeol(s.id)
        return b.isEmpty ? s.name : "\(s.name) (별명: \(b.joined(separator: ", ")))"
    }

    /// 말로 찾을 때 뒤에 붙은 높임·토씨 떼기(님, 씨, 에게, 한테, 께, 이, 을, 를)
    static func malDdegi(_ w: String) -> String {
        var t = w
        var bakkwim = true
        while bakkwim {
            bakkwim = false
            for k in ["에게", "한테", "님", "씨", "께", "이", "을", "를"] where t.hasSuffix(k) && t.count > k.count + 1 {
                t = String(t.dropLast(k.count))
                bakkwim = true
                break
            }
        }
        return t
    }

    /// 말(t)에서 명단의 이름·별명을 찾음 — 가장 잘 맞는 분들(같은 점수면 여럿)과 맞은 별명(이름으로 맞았으면 nil), 맞은 말
    /// 들어 있으면 먼저, 없으면 자모가 비슷한 말(두 글자 이상 이름, 자모 네 자에 한 자까지 틀려도)
    func malChatgi(_ t: String) -> [(saram: JiinSaram, byeol: String?, mal: String)] {
        let z = MalSajeon.ttuk(t)
        guard !z.isEmpty else { return [] }
        let tokens: [String] = {
            let l = t.split(separator: " ").map { MalSajeon.ttuk(String($0)) }.filter { !$0.isEmpty }
            var o: [String] = []
            for (i, w) in l.enumerated() {
                o.append(w)
                o.append(Jiin.malDdegi(w))
                if i + 1 < l.count { o.append(w + l[i + 1]); o.append(Jiin.malDdegi(w + l[i + 1])) }
            }
            return o
        }()
        var gyeol: [(saram: JiinSaram, byeol: String?, mal: String, jeom: Int)] = []
        for s in mokrok {
            var choe: (byeol: String?, mal: String, jeom: Int)? = nil
            let ki: [(String, String?)] = [(s.name, nil)] + byeol(s.id).map { ($0, Optional($0)) }
            for (k, b) in ki {
                let kz = Jiin.malDdegi(MalSajeon.ttuk(k))
                guard !kz.isEmpty else { continue }
                var jeom = 0
                if z.contains(kz) {
                    jeom = 1000 + kz.count
                } else if kz.count >= 2 {
                    let kj = MalSajeon.jamo(kz)
                    let heoyong = max(1, kj.count / 4)
                    for tk in tokens where abs(tk.count - kz.count) <= 1 {
                        let d = MalSajeon.geori(MalSajeon.jamo(tk), kj)
                        if d <= heoyong { jeom = max(jeom, 500 - d * 10 + kz.count) }
                    }
                }
                if jeom > 0 && jeom > (choe?.jeom ?? 0) { choe = (byeol: b, mal: k, jeom: jeom) }
            }
            if let c = choe { gyeol.append((saram: s, byeol: c.byeol, mal: c.mal, jeom: c.jeom)) }
        }
        guard let top = gyeol.map({ $0.jeom }).max() else { return [] }
        return gyeol.filter { $0.jeom == top }.map { (saram: $0.saram, byeol: $0.byeol, mal: $0.mal) }
    }

    /// 2.42.0 이음 번호(30분) — (번호, 못 받은 까닭). 2.80.0 (261016-I34, 이사장님 승인 2026-10-16 「1」) 세 자리
    func ieumBeonho() async -> (String?, String) {
        // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 열 분이 차 있으면 번호를 청하지 않음
        if bulreoom && mokrok.count >= Jiin.HANDO_SU { return (nil, Jiin.gadeukMal) }
        let nm = GinGeup.shared.naIrum
        guard let d = try? await Tongsin.shared.getSae(PHP, ["a": "ieum_man", "owner": owner, "who": nm.isEmpty ? "길눈 이용자" : nm]),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
            return (nil, "이음 번호를 받지 못했습니다. 통신이 끊겼을 수 있습니다.")
        }
        guard (o["ok"] as? Bool) == true, let b = o["beonho"] as? String, b.count == 3 else {
            // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 나스가 열 분이 찼다고 하면 같은 말로
            let e = (o["error"] as? String) ?? ""
            if e.contains("10") || e.contains("열 분") || e.contains("가득") || e.contains("찼") { return (nil, Jiin.gadeukMal) }
            return (nil, e.isEmpty ? "이음 번호를 받지 못했습니다." : e)
        }
        Girok.shared.namgi("jiin_ieum_man", [:])
        return (b, "")
    }

    func jiugi(_ s: JiinSaram) async {
        _ = try? await Tongsin.shared.getSae(PHP, ["a": "jiwoo", "owner": owner, "id": s.id])
        telNoki(s.id, "")
        await MainActor.run { self.byeolNoki(s.id, []) }   // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 별명도 함께 지움
        await bureogi()
    }

    // MARK: 전화번호 — 폰 키체인에만

    private func telMokrok() -> [String: String] {
        guard let s = Yeolsoe.ilgi("jiinTel"), let d = s.data(using: .utf8),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: String] else { return [:] }
        return o
    }

    func tel(_ id: String) -> String { telMokrok()[id] ?? "" }

    func telNoki(_ id: String, _ t: String) {
        var m = telMokrok()
        let beon = t.filter { "0123456789+".contains($0) }
        if beon.isEmpty { m.removeValue(forKey: id) } else { m[id] = beon }
        if let d = try? JSONSerialization.data(withJSONObject: m), let s = String(data: d, encoding: .utf8) {
            Yeolsoe.sseugi("jiinTel", s)
        }
    }
}
