// 자봉 등록 정보 — 웹 자봉과 같은 나스 창고(/jabong/deung.php)에 모임. 폰에는 자봉 번호·이름·지역·1365 아이디만 기억
import Foundation

final class JabongNae: ObservableObject {
    static let shared = JabongNae()
    private let d = UserDefaults.standard

    @Published private(set) var beonho: String
    @Published private(set) var ireum: String
    @Published private(set) var jiyeok: String
    @Published private(set) var id1365: String
    @Published private(set) var geurinSu: Int     // 그려 준 길 수(내 발자취)
    @Published private(set) var gyoyukPan: Int    // 2.10.0 마친 교육 판(2 = 일곱 가지 약속)
    var gyoyukDoem: Bool { gyoyukPan >= JbGyoyuk.pan }

    var deungrokham: Bool { !beonho.isEmpty }
    @Published private(set) var hwanyeong = false   // 2.6.0 막 등록을 마쳐 환영 화면을 보이는 중
    func hwanyeongKkeut() { hwanyeong = false }

    private init() {
        beonho = d.string(forKey: "jb.beonho") ?? ""
        ireum = d.string(forKey: "jb.ireum") ?? ""
        jiyeok = d.string(forKey: "jb.jiyeok") ?? ""
        id1365 = d.string(forKey: "jb.id1365") ?? ""
        geurinSu = d.integer(forKey: "jb.geurinSu")
        gyoyukPan = d.integer(forKey: "jb.gyoyukPan")
    }

    /// 2.10.0 교육을 마침 — 폰에 적고, 나스 등록 창고에 날짜·판을 남김(안 닿으면 다음에 다시 보냄)
    func gyoyukMachim() async {
        await MainActor.run { self.gyoyukPan = JbGyoyuk.pan; self.d.set(JbGyoyuk.pan, forKey: "jb.gyoyukPan"); self.d.set(true, forKey: "jb.gyoyukMotBonaem") }
        await gyoyukBonaegi()
    }

    /// 교육 기록 보내기 — 앱을 열 때도 못 보낸 것이 있으면 다시
    func gyoyukBonaegi() async {
        guard deungrokham, d.bool(forKey: "jb.gyoyukMotBonaem"), let jam = Yeolsoe.ilgi("jbJam") else { return }
        let nal = ISO8601DateFormatter().string(from: Date())
        if let j = await JabongNae.mutgi("gyoyuk", ["beonho": beonho, "jam": jam, "pan": JbGyoyuk.pan, "nal": nal, "munje": JbGyoyuk.munje.count]),
           (j["ok"] as? Bool) == true {
            d.set(false, forKey: "jb.gyoyukMotBonaem")
        }
        Girok.shared.namgi("jabong_gyoyuk", ["beonho": beonho, "pan": "\(JbGyoyuk.pan)"])
    }

    private func dameum(_ b: String, _ i: String, _ j: String, _ id: String) {
        beonho = b; ireum = i; jiyeok = j; id1365 = id
        d.set(b, forKey: "jb.beonho"); d.set(i, forKey: "jb.ireum"); d.set(j, forKey: "jb.jiyeok"); d.set(id, forKey: "jb.id1365")
    }

    /// 나스 등록 창고에 물음
    private static func mutgi(_ a: String, _ bon: [String: Any]) async -> [String: Any]? {
        guard let u = URL(string: "https://lvd.ada.or.kr/jabong/deung.php?a=" + a) else { return nil }
        var r = URLRequest(url: u, timeoutInterval: 15)
        r.httpMethod = "POST"
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.httpBody = try? JSONSerialization.data(withJSONObject: bon)
        guard let dr = try? await URLSession.shared.data(for: r),
              let j = try? JSONSerialization.jsonObject(with: dr.0) as? [String: Any] else { return nil }
        return j
    }

    /// 처음 등록 — 성공하면 nil, 안 되면 까닭
    func deungrok(ireum: String, yeonrak: String, jiyeok: String, id1365: String, jam: String) async -> String? {
        guard let j = await JabongNae.mutgi("sin", ["ireum": ireum, "yeonrak": yeonrak, "jiyeok": jiyeok, "id1365": id1365, "jam": jam, "gyoyuk": true, "gyoyukPan": JbGyoyuk.pan]) else {
            return "통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."
        }
        guard (j["ok"] as? Bool) == true, let b = j["beonho"] as? String, !b.isEmpty else {
            return (j["msg"] as? String) ?? "등록하지 못했습니다."
        }
        Yeolsoe.sseugi("jbJam", jam)   // 새로고침 때 쓰려고 네 자리 숫자는 열쇠 곳간에
        await MainActor.run {
            self.gyoyukPan = JbGyoyuk.pan; self.d.set(JbGyoyuk.pan, forKey: "jb.gyoyukPan")   // 2.10.0 등록 때 새 교육을 마침
            self.hwanyeong = true; self.dameum(b, ireum, jiyeok, id1365)
        }
        Girok.shared.namgi("jabong_deungrok", ["beonho": b])
        return nil
    }

    /// 웹에서 이미 등록하신 분 — 자봉 번호와 네 자리 숫자로 이어서 씀
    func ieoSseugi(beonho: String, jam: String) async -> String? {
        let b = beonho.uppercased().trimmingCharacters(in: .whitespaces)
        guard let j = await JabongNae.mutgi("nae", ["beonho": b, "jam": jam]) else {
            return "통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."
        }
        guard (j["ok"] as? Bool) == true else { return (j["msg"] as? String) ?? "찾지 못했습니다." }
        let su = (j["girok"] as? [Any])?.count ?? 0
        Yeolsoe.sseugi("jbJam", jam)
        await MainActor.run {
            self.dameum(b, (j["ireum"] as? String) ?? "", (j["jiyeok"] as? String) ?? "", (j["id1365"] as? String) ?? "")
            self.geurinSu = su
            self.d.set(su, forKey: "jb.geurinSu")
        }
        return nil
    }

    /// 등록 정보를 나스에서 다시 받아 봄(새로고침)
    func dasiBatgi() async {
        guard deungrokham, let jam = Yeolsoe.ilgi("jbJam"),
              let j = await JabongNae.mutgi("nae", ["beonho": beonho, "jam": jam]), (j["ok"] as? Bool) == true else { return }
        let su = (j["girok"] as? [Any])?.count ?? geurinSu
        await MainActor.run {
            self.dameum(self.beonho, (j["ireum"] as? String) ?? self.ireum, (j["jiyeok"] as? String) ?? self.jiyeok, (j["id1365"] as? String) ?? self.id1365)
            self.geurinSu = su
            self.d.set(su, forKey: "jb.geurinSu")
        }
    }

    /// 이 폰에서 등록을 지움(나스 기록은 그대로)
    func ijeugi() {
        dameum("", "", "", "")
    }
}
