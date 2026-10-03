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

    /// 2.42.0 이음 번호(여섯 자리, 30분) — (번호, 못 받은 까닭)
    func ieumBeonho() async -> (String?, String) {
        let nm = GinGeup.shared.naIrum
        guard let d = try? await Tongsin.shared.getSae(PHP, ["a": "ieum_man", "owner": owner, "who": nm.isEmpty ? "길눈 이용자" : nm]),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
            return (nil, "이음 번호를 받지 못했습니다. 통신이 끊겼을 수 있습니다.")
        }
        guard (o["ok"] as? Bool) == true, let b = o["beonho"] as? String, b.count == 6 else {
            return (nil, (o["error"] as? String) ?? "이음 번호를 받지 못했습니다.")
        }
        Girok.shared.namgi("jiin_ieum_man", [:])
        return (b, "")
    }

    func jiugi(_ s: JiinSaram) async {
        _ = try? await Tongsin.shared.getSae(PHP, ["a": "jiwoo", "owner": owner, "id": s.id])
        telNoki(s.id, "")
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
