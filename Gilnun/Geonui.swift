// 건의와 소감 말하기 — 길눈 2.141.0 (빌드 261126-I95, 대장클, 이사장님 승인 2026-11-26 「이번 판에 꼭 넣어서 통과 받고 앱 공개하자」)
// 시각장애인은 게시판에 글을 쓰기 어려우니, 말로 하기에서 소감을 말하듯 그냥 말씀하시면 길눈이 받아 적어 협회로 보냄.
// 「길눈아, 건의할게」·「소감 말할게」·「길눈에 바라는 것」 → 「네, 말씀하십시오. 다 하시면 끝이라고 해 주십시오」 →
//   말씀 도중 쉬셔도 이어 받아 적고(2분까지) → 「이렇게 받아 적었습니다 … 보낼까요?」 → 네면 보냄, 다시면 처음부터, 아니요면 버림.
// 누가(설정의 내 이름·폰 번호표), 어디서(시·구까지만), 어떤 폰·어떤 판, 언제, 어떤 상황(하던 일·바로 앞 기록)을 함께 남김.
// 처음 한 번만 이름과 사는 구를 함께 보내도 되는지 여쭘(아니요면 이름과 구 없이 보냄). 보내는 길은 앱 기록(applog)과 같아 통신이 끊겨도 쌓였다 감.
import Foundation
import UIKit

final class GeonuiEngine {
    static let shared = GeonuiEngine()

    enum Dangye { case eopseum, dongui, batneun, hwagin }
    private(set) var dangye: Dangye = .eopseum
    private var jul: [String] = []
    private var sijakTtae = Date.distantPast
    private var majimakTtae = Date.distantPast
    private var sanghwang: [String: Any] = [:]

    private static let DONGUI_KI = "gn.geonuiDongui"
    private var dongui: Bool? {
        get { UserDefaults.standard.object(forKey: GeonuiEngine.DONGUI_KI) as? Bool }
        set { UserDefaults.standard.set(newValue, forKey: GeonuiEngine.DONGUI_KI) }
    }

    /// 지금 건의를 주고받는 중인가(3분 넘게 말씀이 없으면 저절로 풂)
    var jinhaengJung: Bool {
        if dangye != .eopseum && Date().timeIntervalSince(majimakTtae) > 180 { chogihwa() }
        return dangye != .eopseum
    }

    /// 건의를 시작하는 말인가 — 짧은 말만(긴 말 속의 「건의」는 내용으로 봄)
    static func sijakMal(_ z: String) -> Bool {
        guard z.count <= 24 else { return false }
        let k = ["건의할게", "건의할께", "건의하고싶", "건의사항", "건의좀", "건의할래", "건의해도", "건의하자", "건의하기",
                 "소감말할", "소감을말", "소감말하", "소감할게", "후기말할", "후기남길", "사용후기", "평가할게", "의견말할", "의견드릴",
                 "길눈에바라", "길눈한테바라", "고쳐달라고말", "고칠것말할", "하고싶은말있"]
        return k.contains { z.contains($0) }
    }

    /// 건의를 시작할 때 — 대답할 말과 이어 들을지
    func sijak(_ z: String) -> (String, Bool) {
        chogihwa()
        sanghwang = GeonuiEngine.sanghwangMoeugi()
        majimakTtae = Date()
        Girok.shared.namgi("geonui_sijak", [:])
        if dongui == nil {
            dangye = .dongui
            return ("건의를 보내실 때 설정에 적어 두신 내 이름, 사시는 구, 폰 기종을 함께 보냅니다. 괜찮으시면 네, 이름 없이 보내시려면 아니요라고 말씀해 주십시오.", true)
        }
        dangye = .batneun
        sijakTtae = Date()
        return ("네, 말씀하십시오. 다 하시면 끝이라고 해 주십시오.", true)
    }

    enum Dap { case malHago(String, Bool), deutgi }

    /// 건의 중에 들린 말 — 대답할 말(이어 들을지) 또는 소리 없이 다시 듣기
    func deureum(_ alts: [String]) -> Dap {
        majimakTtae = Date()
        let t = (alts.first ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let z = MalSajeon.ttuk(t)
        switch dangye {
        case .eopseum:
            return .malHago("", false)
        case .dongui:
            if GeonuiEngine.ne(z) { dongui = true }
            else if GeonuiEngine.aniyo(z) { dongui = false }
            else { return .malHago("네 또는 아니요로 말씀해 주십시오. 이름과 사시는 구를 함께 보내도 될까요?", true) }
            dangye = .batneun
            sijakTtae = Date()
            return .malHago((dongui == true ? "고맙습니다. " : "이름 없이 보내겠습니다. ") + "말씀하십시오. 다 하시면 끝이라고 해 주십시오.", true)
        case .batneun:
            if GeonuiEngine.kkeutMal(z) {
                return jul.isEmpty ? chwiso("말씀하신 것이 없어 건의를 마칩니다.") : hwaginYeojum()
            }
            if !t.isEmpty { jul.append(t) }
            if Date().timeIntervalSince(sijakTtae) > 120 { return hwaginYeojum() }
            return .deutgi
        case .hwagin:
            if GeonuiEngine.dasiMal(z) {
                jul = []
                dangye = .batneun
                sijakTtae = Date()
                return .malHago("처음부터 다시 받아 적겠습니다. 말씀하십시오. 다 하시면 끝이라고 해 주십시오.", true)
            }
            if GeonuiEngine.aniyo(z) || z.contains("취소") || z.contains("보내지마") {
                return chwiso("보내지 않고 지웠습니다.")
            }
            if GeonuiEngine.ne(z) || z.contains("보내") {
                bonaegi()
                return .malHago("보냈습니다. 협회에서 잘 살펴 길눈을 고쳐 가겠습니다. 고맙습니다.", false)
            }
            if z.contains("더") && z.count <= 8 {
                dangye = .batneun
                return .malHago("네, 이어서 말씀하십시오. 다 하시면 끝이라고 해 주십시오.", true)
            }
            return .malHago("보내시려면 네, 처음부터 다시 하시려면 다시, 보내지 않으시려면 아니요라고 말씀해 주십시오.", true)
        }
    }

    /// 듣는 동안 말씀이 끊겼을 때(조용히 지나감)
    func chimmuk() -> Dap {
        majimakTtae = Date()
        switch dangye {
        case .batneun:
            return jul.isEmpty ? chwiso("말씀이 들리지 않아 건의를 마칩니다. 다시 하시려면 길눈아, 건의할게라고 불러 주십시오.") : hwaginYeojum()
        case .dongui, .hwagin:
            return chwiso("대답이 없어 건의를 보내지 않았습니다. 다시 하시려면 길눈아, 건의할게라고 불러 주십시오.")
        case .eopseum:
            return .malHago("", false)
        }
    }

    private func hwaginYeojum() -> Dap {
        dangye = .hwagin
        let geul = jul.joined(separator: " ")
        return .malHago("이렇게 받아 적었습니다. \(geul). 보낼까요? 네, 다시, 아니요 가운데 말씀해 주십시오. 덧붙이시려면 더라고 하십시오.", true)
    }

    private func chwiso(_ m: String) -> Dap {
        Girok.shared.namgi("geonui_chwiso", ["jul": jul.count])
        chogihwa()
        return .malHago(m, false)
    }

    private func chogihwa() {
        dangye = .eopseum
        jul = []
    }

    private func bonaegi() {
        var d = sanghwang
        d["mal"] = String(jul.joined(separator: " ").prefix(2000))
        d["cho"] = Int(Date().timeIntervalSince(sijakTtae))
        d["dongui"] = dongui ?? false
        if dongui == true {
            d["ireum"] = GinGeup.shared.naIrum
            d["hoching"] = Seoljeong.shared.hoching
            d["gu"] = JiyeokEngine.shared.majimak?.balmal ?? ""   // 시·도와 시·군·구까지만(폰의 주소 찾기)
        }
        Girok.shared.namgi("geonui", d)
        Girok.shared.bonaegi()
        chogihwa()
    }

    /// 건의를 시작한 때의 상황 — 하던 일, 폰, 판, 바로 앞 기록
    private static func sanghwangMoeugi() -> [String: Any] {
        var d: [String: Any] = ["gijong": GeonuiEngine.gijong(), "ios_pan": UIDevice.current.systemVersion, "app_pan": Pan.pan]
        if let y = YeojeongEngine.shared.jigeum { d["yeojeong"] = y.mokjeok.ireum + " / " + y.danggye.rawValue }
        if MalgilEngine.shared.geotneun { d["malgil"] = MalgilEngine.shared.jemok }
        if BangsongEngine.shared.itda { d["bangsong"] = true }
        d["apgirok"] = Girok.shared.choegeun()
        return d
    }

    private static func gijong() -> String {
        var s = utsname()
        uname(&s)
        let m = Mirror(reflecting: s.machine).children.compactMap { $0.value as? Int8 }.filter { $0 != 0 }.map { Character(UnicodeScalar(UInt8($0))) }
        return String(m)
    }

    static func ne(_ z: String) -> Bool { ["네", "예", "응", "좋아", "괜찮아", "그래", "네네", "보내", "보내줘", "네보내", "그래요", "좋아요", "괜찮아요"].contains(z) || z.hasPrefix("네") || z.hasPrefix("예") }
    static func aniyo(_ z: String) -> Bool { z.hasPrefix("아니") || z.hasPrefix("싫") || z == "안돼" || z == "아뇨" }
    static func dasiMal(_ z: String) -> Bool { z == "다시" || z.hasPrefix("다시할") || z.hasPrefix("다시말") || z == "처음부터" }
    static func kkeutMal(_ z: String) -> Bool {
        ["끝", "끝이야", "끝이에요", "끝입니다", "끝났어", "다했어", "다했어요", "이상이야", "이상입니다", "그게다야", "여기까지", "끝끝"].contains(z)
            || (z.count <= 6 && z.hasSuffix("끝"))
    }
}
