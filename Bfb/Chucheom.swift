// 2.8.0 덧붙임(빌드 261028-LI11, 새방클, 이사장님 승인 2026-10-28 「1」) 이름 추첨 — 당첨자 이름을 듣는 분 모두에게 한 번, 내가 당첨이면 이름도 함께.
// LVD 방송 아이폰(듣는 앱) — 입장 번호와 행운권 추첨 당첨 알림 (2.8.0판, 빌드 261028-LI11, 새방클, 이사장님 승인 2026-10-28 「1」)
// 안드로이드 LVD 방송 2.8.0(Chucheom.kt)과 같은 일. 노트북판 현장 해설방송 듣기 화면(deut.html)이 하던 일을 앱에도 넣음.
// 1. 입장 번호: 첫 화면 「내 입장 번호」에 넣거나, 큐알·태그(lvdbangsong://deut?…&n=12&g=가)로 받음. 폰에 기억.
// 2. 행사장(BYOD 방송)에서 듣는 동안 30초마다 그 번호를 방송 서버에 알림(/beonho?n=&g=&i=) — 피디 화면 「참석과 추첨」의 「지금 듣는 번호」에 잡힘.
// 3. 3초마다 /status 의 당첨 번호(dang)와 추첨 차례(dseq)를 봄. 새 추첨이 내 번호면 진동 세 번 + 알림 소리 + 알림(허락했으면) + 말 한 번.
//    같은 추첨을 두 번 알리지 않게 행사장 자리와 차례를 기억함. 내 번호가 아니면 아무 말도 하지 않음.
import Foundation
import AudioToolbox
import UserNotifications
import UIKit

enum Chucheom {
    private static let D = UserDefaults.standard
    static var beonho: String { D.string(forKey: "ipjang_beonho") ?? "" }
    static var guyeok: String { D.string(forKey: "ipjang_guyeok") ?? "" }

    /// 번호 다듬기 — 숫자 1~4자리가 아니면 nil, 빈 글이면 빈 글(지우기)
    static func dadeum(_ t: String) -> String? {
        var s = t.filter { !$0.isWhitespace }
        if s.hasSuffix("번") { s.removeLast() }
        if s.isEmpty { return "" }
        guard s.count <= 4, s.allSatisfy({ $0.isASCII && $0.isNumber }) else { return nil }
        let x = String(s.drop(while: { $0 == "0" }))
        return x.isEmpty ? "0" : x
    }

    static func dugi(_ n: String, _ g: String = "") {
        D.set(n, forKey: "ipjang_beonho")
        D.set(n.isEmpty ? "" : String(g.trimmingCharacters(in: .whitespaces).prefix(10)), forKey: "ipjang_guyeok")
        bonaenTtae = .distantPast
        BfbGirok.shared.namgi("ipjang", ["issum": n.isEmpty ? "false" : "true"])   // 번호 자체는 남기지 않음
        if !n.isEmpty { UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in } }
    }

    static var danchuMal: String {
        let n = beonho, g = guyeok
        if n.isEmpty { return "내 입장 번호: 없음. 행운권 추첨이 있으면 누르고 번호를 넣습니다" }
        return "내 입장 번호: " + (g.isEmpty ? "" : g + " 구역 ") + n + "번. 누르면 바꿉니다"
    }

    private static var jid: String {
        if let j = D.string(forKey: "jid"), j.count >= 8 { return j }
        let a = Array("0123456789abcdefghijklmnopqrstuvwxyz")
        let j = String((0..<16).map { _ in a[Int.random(in: 0..<a.count)] })
        D.set(j, forKey: "jid")
        return j
    }

    private static var bonaenTtae = Date.distantPast
    private static var bonTtae = Date.distantPast
    private static var mutneunJung = false

    private static var bonJari = ""
    /// 듣기가 행사장에 붙어 있는 동안 1초마다 부름 — 때가 된 일만 함.
    /// 2.8.0 덧붙임 이름 추첨 — 입장 번호가 없어도 당첨 보기는 하고, 당첨자 이름(dnm)이 오면 듣는 분 모두에게 한 번 알림
    static func salpigi() {
        let n = beonho
        let now = Date()
        if !n.isEmpty && now.timeIntervalSince(bonaenTtae) >= 30 {
            bonaenTtae = now
            let g = guyeok
            var gil = "/beonho?n=\(n)"
            if !g.isEmpty, let e = g.addingPercentEncoding(withAllowedCharacters: .alphanumerics) { gil += "&g=" + e }
            gil += "&i=" + jid
            Hyeonjang.mutgi(gil, ttae: 2.5) { d in if d == nil { DispatchQueue.main.async { bonaenTtae = Date().addingTimeInterval(-25) } } }
        }
        guard now.timeIntervalSince(bonTtae) >= 3, !mutneunJung else { return }
        bonTtae = now
        mutneunJung = true
        let jari = Hyeonjang.jari
        Hyeonjang.mutgi("/status", ttae: 2.5) { d in
            DispatchQueue.main.async {
                mutneunJung = false
                guard let d = d, let j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] else { return }
                let seq = (j["dseq"] as? Int) ?? Int("\(j["dseq"] ?? 0)") ?? 0
                let dang = (j["dang"] as? String) ?? ""
                let dnm = (j["dnm"] as? String) ?? ""
                guard seq > 0, !(dang.isEmpty && dnm.isEmpty) else { return }
                let ki = jari + "#" + String(seq) + "#" + dang + "#" + dnm
                let cheot = bonJari != jari   // 이 행사장에서 처음 본 상태 — 지난 추첨은 알리지 않음
                bonJari = jari
                if D.string(forKey: "dang_bon") == ki { return }
                D.set(ki, forKey: "dang_bon")
                let nae = beonho
                if !nae.isEmpty && dang == nae { allimHagi(dang, dnm) }
                else if !cheot && !dnm.isEmpty { Deut.shared.dangcheomMal("행운권 추첨 당첨: " + dnm + " 님입니다.") }
            }
        }
    }

    private static func allimHagi(_ n: String, _ nm: String = "") {
        let g = guyeok
        let mal = "축하합니다. " + (nm.isEmpty ? "" : nm + " 님, ") + (g.isEmpty ? "" : g + " 구역 ") + n + "번, 당첨되셨습니다. 진행요원이 찾아갑니다."
        BfbGirok.shared.namgi("dangcheom", ["ok": "1"])
        for k in 0..<3 {   // 길게 세 번
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(k) * 0.7) { AudioServicesPlayAlertSound(SystemSoundID(kSystemSoundID_Vibrate)) }
        }
        AudioServicesPlaySystemSound(1005)
        let c = UNMutableNotificationContent()
        c.title = "행운권 당첨"
        c.body = mal
        c.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "lvd-dangcheom", content: c, trigger: nil)) { _ in }
        Deut.shared.dangcheomMal(mal)
    }
}
