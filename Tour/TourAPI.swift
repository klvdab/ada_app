// LVD투어스튜디오 앱 — 나스 창구 부르기 (판 1.2.0, 빌드 261017-T4 — 최고관리자가 「누구의 자리를 볼지」 고르면 모든 부름에 as 를 붙임(웹과 같음). 1.0.0 261012-T1)
// 웹 투어스튜디오와 같은 창구(lvdts_api.php)를 그대로 씁니다. 서버는 고치지 않습니다.
// 들어오기 표(통행증)는 폰 안에만 두고, 비밀번호는 앱이 간직하지 않습니다.
import Foundation

enum TourAPI {
    static let base = "https://lvd.ada.or.kr/lvdts/lvdts_api.php"
    static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) LVDTourStudio/1.0"

    /// 최고관리자가 보고 있는 남의 자리(아이디). 비어 있으면 내 자리 — 웹 asWho 와 같음
    static var asWho = ""

    static var token: String {
        get { UserDefaults.standard.string(forKey: "lvdtsToken") ?? "" }
        set { UserDefaults.standard.set(newValue, forKey: "lvdtsToken") }
    }

    /// 창구 한 번 부르기 — 늘 사전(딕셔너리)으로 돌려줌. 닿지 못하면 ok=false 와 까닭
    static func call(_ action: String, _ body: [String: Any] = [:], timeout: TimeInterval = 40, tk: String? = nil) async -> [String: Any] {
        var b = body
        b["action"] = action
        if !asWho.isEmpty && b["as"] == nil && action != "login" && action != "logout" { b["as"] = asWho }
        guard let u = URL(string: base), let data = try? JSONSerialization.data(withJSONObject: b) else {
            return ["ok": false, "error": "보낼 자료를 만들지 못했습니다."]
        }
        var r = URLRequest(url: u)
        r.httpMethod = "POST"
        r.httpBody = data
        r.timeoutInterval = timeout
        r.cachePolicy = .reloadIgnoringLocalCacheData
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        let pyo = tk ?? token
        if !pyo.isEmpty { r.setValue(pyo, forHTTPHeaderField: "X-LVDTS-Token") }
        do {
            let (d, _) = try await URLSession.shared.data(for: r)
            guard let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
                let t = String(data: d.prefix(120), encoding: .utf8) ?? ""
                return ["ok": false, "error": "서버가 이상한 대답을 했습니다: " + t]
            }
            if (j["needLogin"] as? Bool) == true && action != "logout" {
                await MainActor.run { TourStore.shared.naga(mal: "들어오기 표가 끝났습니다. 다시 들어와 주십시오.") }
            }
            return j
        } catch {
            return ["ok": false, "error": "나스에 닿지 못했습니다. 인터넷을 확인해 주십시오.", "offline": true]
        }
    }

    static func ok(_ j: [String: Any]) -> Bool { (j["ok"] as? Bool) == true }
    static func err(_ j: [String: Any]) -> String { geulja(j["error"]).isEmpty ? "잘 되지 않았습니다." : geulja(j["error"]) }

    // 사진·소리 보기 주소는 표(make_ticket)로 엶 — 웹과 같음
    static var ticket = ""
    static var ticketAt = Date.distantPast
    static func needTicket() async -> String {
        if !ticket.isEmpty && Date().timeIntervalSince(ticketAt) < 5400 { return ticket }
        let j = await call("make_ticket")
        if ok(j) { ticket = geulja(j["tk"]); ticketAt = Date() }
        return ticket
    }
    static func fileURL(_ kind: String, course: String, f: String, tk: String) -> URL? {
        var c = URLComponents(string: base)
        c?.queryItems = [URLQueryItem(name: "action", value: kind), URLQueryItem(name: "c", value: course),
                         URLQueryItem(name: "f", value: f), URLQueryItem(name: "tk", value: tk)]
        return c?.url
    }
}
