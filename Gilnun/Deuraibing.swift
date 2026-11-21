// 드라이빙 모드 — 2.132.0 (261121-I86, 대클, 이사장님 승인 2026-11-21 「1,2,3」)
// 차를 모는 분을 위한 내비게이션이 아니라, 타고 가시는 분이 갈 길을 미리 알고 확인하시도록.
//   ① 길 미리 듣기 — 「차로 약 25분, 14킬로미터입니다. 동호로, 동호대교, 올림픽대로를 지나 갑니다」
//   ② 다른 길 견주기 — 가장 빠른 길·가장 짧은 길이 추천 길과 다르고 2분 넘게 빠르거나 1킬로미터 넘게 짧을 때만
//   길은 협회 서버 gilmiri.php(카카오 길찾기)에서 받음. 운전을 시키는 말(좌회전하십시오 등)은 하지 않음.
//   설정 탭 말하기 설정의 「드라이빙 모드」를 켜 두시면 차로 가는 길 안내가 시작될 때 한 번 저절로 들려 드림(처음은 꺼짐).
//   말로 하기 「길 미리 들려 줘」, 「다른 길 알려 줘」는 켜고 끔과 상관없이 언제나 됨. 안드로이드 Deuraibing.kt 와 같음.
import Foundation

enum Deuraibing {
    /// 길을 받아 할 말을 만듦 — gyeon 이면 다른 길 견주기만
    static func mal(mokLat: Double, mokLon: Double, mok: String, gyeon: Bool) async -> String {
        guard let w = WichiEngine.shared.jigeum else { return "지금 자리를 알 수 없어 길을 미리 받지 못했습니다." }
        let o = await Chatgi.json("gilmiri.php", ["la1": String(format: "%.6f", w.lat), "lo1": String(format: "%.6f", w.lon),
                                                  "la2": String(format: "%.6f", mokLat), "lo2": String(format: "%.6f", mokLon)])
        guard let o = o, (o["ok"] as? Bool) == true else {
            Girok.shared.namgi("deuraibing_mot", [:])
            return "지금은 \(mok)까지 가는 차 길을 받지 못했습니다. 조금 뒤에 다시 말씀해 주십시오."
        }
        let m = (o["mal"] as? String) ?? "", g = (o["gyeon"] as? String) ?? ""
        let t = gyeon ? (g.isEmpty ? "\(mok)까지는 다른 길도 지금 길과 거의 같습니다. " + m : g) : "\(mok)까지 " + m + (g.isEmpty ? "" : " " + g)
        Girok.shared.namgi("deuraibing_mal", ["mal": t])
        return t
    }

    /// 「길 미리 들려 줘」, 「가는 길 미리 알려 줘」, 「어느 길로 가」
    static func miriMal(_ z: String) -> Bool {
        (z.contains("길") && z.contains("미리") && !z.contains("걸어")) || z.contains("어느길로가") || z.contains("어떤길로가") || z.contains("무슨길로가")
    }
    /// 「다른 길 알려 줘」, 「길 견줘 줘」, 「빠른 길 알려 줘」
    static func gyeonMal(_ z: String) -> Bool {
        (z.contains("다른길") && (z.contains("알려") || z.contains("있어") || z.contains("견줘") || z.contains("비교"))) || z.contains("길견줘") || z.contains("길비교")
            || z.contains("빠른길알려") || z.contains("짧은길알려") || z.contains("돌아가는길")
    }
}
