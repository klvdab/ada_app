// 듣기 예약 — 정한 시각에 라디오 틀기, 정한 때에 끄기
// 2.115.0 (빌드 261027-I69, 대클, 이사장님 승인 2026-10-27 「일단 길눈에서 할 수 있는 최대를 해 놓자」 — 「매일 오전 7시에 MBC 표준FM 틀어 줘」)
// ① 길눈이 켜져 있으면(화면이 꺼져 길 안내·방송 중이어도) 정한 시각에 저절로 틂
// ② 아이폰은 앱이 스스로 깨어날 수 없으므로, 그 시각에 알림을 울리고 알림을 누르시면 틂(앱 규칙상 손이 한 번 감)
// ③ 손대지 않고 틀고 싶으시면 단축어 「개인 자동화」에 「길눈 라디오 틀기」를 넣으시면 됨(RadioTeulgiIntent)
// ④ 끄기 예약 — 몇 분 뒤 끄기, 예약마다 「틀고 나서 몇 분 뒤 끄기」
// ⑤ 통화·긴급통화 중에는 방송 엔진이 멈춤을 맡음(통화가 걸리면 하던 소리는 멈춘다는 원칙 그대로)
import Foundation
import SwiftUI
import UIKit
import UserNotifications
import AppIntents

struct DeutgiYeyakHang: Codable, Identifiable, Hashable {
    var id: String
    var si: Int
    var bun: Int
    var banbok: String        // maeil 날마다, pyeongil 평일, jumal 주말, hanbeon 한 번만
    var nal: String           // hanbeon 일 때 yyyyMMdd
    var chaeneolId: String
    var chaeneolIreum: String
    var kkeulBun: Int         // 틀고 나서 몇 분 뒤 끌지(0 이면 그대로 둠)
    var kyeojim: Bool

    var banbokMal: String {
        switch banbok {
        case "pyeongil": return "평일마다"
        case "jumal": return "주말마다"
        case "hanbeon": return DeutgiYeyak.nalMalYmd(nal) + " 한 번"
        default: return "날마다"
        }
    }

    var julMal: String {
        var t = "\(banbokMal) \(JeonggiHochul.sigakMal(si, bun)), \(chaeneolIreum)"
        if kkeulBun > 0 { t += ", \(DeutgiYeyak.bunMal(kkeulBun)) 뒤 끔" }
        if !kyeojim { t += ", 꺼 둠" }
        return t
    }
}

final class DeutgiYeyak: ObservableObject {
    static let shared = DeutgiYeyak()

    @Published private(set) var mok: [DeutgiYeyakHang] = []
    /// 끄기 예약 — 이때 방송을 끔
    @Published private(set) var kkeulTtae: Date?

    private let ki = "gn.deutgiYeyak"
    private let hanKi = "gn.deutgiYeyakHan"
    private var sigye: Timer?
    private var dolgo = false
    /// 알림·자동으로 튼 때 — 같은 때를 두 번 틀지 않게
    private var han: [String] = []

    init() {
        let d = UserDefaults.standard
        if let x = d.data(forKey: ki), let v = try? JSONDecoder().decode([DeutgiYeyakHang].self, from: x) { mok = v }
        han = d.stringArray(forKey: hanKi) ?? []
    }

    /// 앱이 켜질 때 — 지킴이를 세우고 알림을 다시 걺
    func sijak() {
        guard !dolgo else { return }
        dolgo = true
        sigye = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in self?.salpigi() }
        if let s = sigye { RunLoop.main.add(s, forMode: .common) }
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.salpigi()
        }
        jaeAllim()
        salpigi()
    }

    // MARK: 정하기

    func deohagi(_ h: DeutgiYeyakHang) {
        mok.append(h)
        jeojang()
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in
            DispatchQueue.main.async { self.jaeAllim() }
        }
        Girok.shared.namgi("deutgi_yeyak", ["si": h.si, "bun": h.bun, "banbok": h.banbok, "ch": h.chaeneolId, "kkeul": h.kkeulBun])
    }

    func jiugi(_ id: String) {
        mok.removeAll { $0.id == id }
        jeojang()
        jaeAllim()
        Girok.shared.namgi("deutgi_yeyak_jium", [:])
    }

    func kyeogiKkeugi(_ id: String) {
        guard let i = mok.firstIndex(where: { $0.id == id }) else { return }
        mok[i].kyeojim.toggle()
        jeojang()
        jaeAllim()
    }

    func moduJiugi() {
        mok = []
        jeojang()
        jaeAllim()
    }

    private func jeojang() {
        if let d = try? JSONEncoder().encode(mok) { UserDefaults.standard.set(d, forKey: ki) }
    }

    // MARK: 끄기 예약

    func kkeugiYeyak(_ bun: Int) {
        kkeulTtae = Date().addingTimeInterval(Double(bun) * 60)
        Girok.shared.namgi("deutgi_kkeugi_yeyak", ["bun": bun])
    }

    func kkeugiChwiso() { kkeulTtae = nil }

    var kkeugiMal: String {
        guard let t = kkeulTtae else { return "" }
        let n = max(1, Int((t.timeIntervalSinceNow / 60).rounded(.up)))
        return "\(DeutgiYeyak.bunMal(n)) 뒤에 방송을 끕니다."
    }

    // MARK: 지킴이 — 15초마다

    func salpigi() {
        let now = Date()
        if let t = kkeulTtae, now >= t {
            kkeulTtae = nil
            if BangsongEngine.shared.itda {
                SoriEngine.shared.mal("예약하신 대로 방송을 끕니다.", .annae)
                BangsongEngine.shared.geuman(malHagi: false)
                Girok.shared.namgi("deutgi_kkeugi", [:])
            }
        }
        for h in mok where h.kyeojim {
            guard let t = DeutgiYeyak.oneulTtae(h, now), now >= t, now.timeIntervalSince(t) < 120 else { continue }
            let k = h.id + "|" + DeutgiYeyak.ymdHm(t)
            if han.contains(k) { continue }
            teulgi(h, t: t, jadong: true)
        }
    }

    /// 오늘 이 예약이 울릴 때(오늘이 그 날이 아니면 없음)
    static func oneulTtae(_ h: DeutgiYeyakHang, _ now: Date) -> Date? {
        let cal = Calendar.current
        let w = cal.component(.weekday, from: now)
        switch h.banbok {
        case "pyeongil": if w == 1 || w == 7 { return nil }
        case "jumal": if w != 1 && w != 7 { return nil }
        case "hanbeon": if h.nal != JeonggiHochul.ymd(now) { return nil }
        default: break
        }
        return cal.date(bySettingHour: h.si, minute: h.bun, second: 0, of: now)
    }

    /// 틀기 — jadong: 지킴이가 저절로, 아니면 알림을 누르셔서
    func teulgi(_ h: DeutgiYeyakHang, t: Date, jadong: Bool) {
        // 2.116.0 (261027-I70) 아침 브리핑을 읽는 중이면 기다림 — 브리핑을 마친 뒤 브리핑이 이 예약을 이어서 틂(말 위에 라디오가 겹치지 않게)
        if jadong && Iljeong.shared.beuripingJung { return }
        let k = h.id + "|" + DeutgiYeyak.ymdHm(t)
        let beolsseo = han.contains(k)
        if !beolsseo {
            han.append(k)
            if han.count > 60 { han.removeFirst(han.count - 60) }
            UserDefaults.standard.set(han, forKey: hanKi)
        }
        if h.banbok == "hanbeon", let i = mok.firstIndex(where: { $0.id == h.id }) {
            mok[i].kyeojim = false
            jeojang()
        }
        let b = BangsongEngine.shared
        // 이미 그 채널이 나오고 있으면 그대로(알림을 늦게 누르셔도 두 번 틀지 않음)
        if b.jong == .radio && b.jigeumChaeneolId == h.chaeneolId && !b.meomchum {
            if !jadong { SoriEngine.shared.mal("\(h.chaeneolIreum)이 이미 나오고 있습니다.", .jeongbo) }
            return
        }
        // 통화 중이면 틀지 않음 — 통화가 끝난 뒤 알림을 누르시면 틂
        if JeonhwaGamsi.shared.jeonhwaJung || GinGeup.shared.sangtae != .eopseum {
            Girok.shared.namgi("deutgi_yeyak_tonghwa", ["ch": h.chaeneolId])
            return
        }
        SoriEngine.shared.mal("\(JeonggiHochul.sigakMal(h.si, h.bun)) \(h.chaeneolIreum) 예약입니다. 틉니다.", .jeongbo)
        Girok.shared.namgi("deutgi_yeyak_teulgi", ["ch": h.chaeneolId, "jadong": jadong])
        Task {
            if b.chaeneolDeul.isEmpty { await b.chaeneolBatgi() }
            await MainActor.run {
                guard let c = b.chaeneolDeul.first(where: { $0.id == h.chaeneolId }) else {
                    SoriEngine.shared.mal("\(h.chaeneolIreum) 채널을 찾지 못했습니다. 통신을 확인해 주십시오.")
                    return
                }
                b.chaeneolTeulgi(c, malHagi: false)
                if h.kkeulBun > 0 { self.kkeugiYeyak(h.kkeulBun) }
            }
        }
    }

    // MARK: 알림 — 앱이 꺼져 있거나 잠들어 있을 때

    func jaeAllim() {
        let c = UNUserNotificationCenter.current()
        c.getPendingNotificationRequests { rs in
            let yet = rs.map { $0.identifier }.filter { $0.hasPrefix("deutgi-") }
            c.removePendingNotificationRequests(withIdentifiers: yet)
            DispatchQueue.main.async { self.allimGeolgi() }
        }
    }

    private func allimGeolgi() {
        let c = UNUserNotificationCenter.current()
        let now = Date()
        for h in mok where h.kyeojim {
            let ct = UNMutableNotificationContent()
            ct.title = "길눈 듣기 예약"
            ct.body = "\(JeonggiHochul.sigakMal(h.si, h.bun)) \(h.chaeneolIreum) 예약입니다. 누르시면 틉니다."
            ct.sound = .default
            ct.userInfo = ["deutgi": h.id]
            var nalDeul: [DateComponents] = []
            switch h.banbok {
            case "hanbeon":
                guard h.nal.count == 8, let y = Int(h.nal.prefix(4)), let m = Int(h.nal.dropFirst(4).prefix(2)), let d = Int(h.nal.suffix(2)) else { continue }
                var dc = DateComponents()
                dc.year = y; dc.month = m; dc.day = d; dc.hour = h.si; dc.minute = h.bun
                if let t = Calendar.current.date(from: dc), t > now {
                    c.add(UNNotificationRequest(identifier: "deutgi-\(h.id)-1", content: ct,
                                                trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: false)), withCompletionHandler: nil)
                }
                continue
            case "pyeongil": nalDeul = (2...6).map { (w: Int) -> DateComponents in var dc = DateComponents(); dc.weekday = w; return dc }
            case "jumal": nalDeul = [1, 7].map { (w: Int) -> DateComponents in var dc = DateComponents(); dc.weekday = w; return dc }
            default: nalDeul = [DateComponents()]
            }
            for (i, dc0) in nalDeul.enumerated() {
                var dc = dc0
                dc.hour = h.si
                dc.minute = h.bun
                c.add(UNNotificationRequest(identifier: "deutgi-\(h.id)-\(i)", content: ct,
                                            trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: true)), withCompletionHandler: nil)
            }
        }
    }

    /// 알림이 왔을 때(앱이 켜져 있으면 띄우지 않고 바로 틂) — JeonggiHochul 의 알림 대리인이 넘겨 줌
    func allimOm(_ n: UNNotification) {
        guard let id = n.request.content.userInfo["deutgi"] as? String, let h = mok.first(where: { $0.id == id }) else { return }
        teulgi(h, t: DeutgiYeyak.allimTtae(h, n.date), jadong: true)
    }

    /// 알림을 누르셨을 때
    func allimNureum(_ n: UNNotification) {
        guard let id = n.request.content.userInfo["deutgi"] as? String, let h = mok.first(where: { $0.id == id }) else { return }
        teulgi(h, t: DeutgiYeyak.allimTtae(h, n.date), jadong: false)
    }

    /// 알림이 온 날의 예약 시각(같은 때를 두 번 틀지 않게 묶는 열쇠)
    static func allimTtae(_ h: DeutgiYeyakHang, _ d: Date) -> Date {
        Calendar.current.date(bySettingHour: h.si, minute: h.bun, second: 0, of: d) ?? d
    }

    // MARK: 다음 예약

    var daeumMal: String {
        let cal = Calendar.current
        let now = Date()
        var best: (Date, DeutgiYeyakHang)?
        for h in mok where h.kyeojim {
            for d in 0..<8 {
                guard let nal = cal.date(byAdding: .day, value: d, to: now), let t = DeutgiYeyak.oneulTtae(h, nal), t > now else { continue }
                if best == nil || t < best!.0 { best = (t, h) }
                break
            }
        }
        guard let b = best else { return mok.isEmpty ? "듣기 예약이 없습니다." : "켜 둔 예약이 없습니다." }
        let oneul = cal.isDateInToday(b.0) ? "오늘" : (cal.isDateInTomorrow(b.0) ? "내일" : JeonggiHochul.nalMal(b.0))
        return "다음은 \(oneul) \(JeonggiHochul.sigakMal(b.1.si, b.1.bun)) \(b.1.chaeneolIreum)입니다."
    }

    // MARK: 도움 셈

    static func ymdHm(_ t: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMddHHmm"
        return f.string(from: t)
    }

    static func nalMalYmd(_ s: String) -> String {
        guard s.count == 8, let m = Int(s.dropFirst(4).prefix(2)), let d = Int(s.suffix(2)) else { return s }
        return "\(m)월 \(d)일"
    }

    static func bunMal(_ n: Int) -> String {
        if n >= 60 && n % 60 == 0 { return "\(n / 60)시간" }
        if n > 60 { return "\(n / 60)시간 \(n % 60)분" }
        return "\(n)분"
    }

    static func saeId() -> String { String(Int(Date().timeIntervalSince1970 * 1000)) }

    // MARK: 말로 하기 — 「매일 7시에 MBC 표준FM 틀어 줘」, 「평일 아침 6시 반 KBS 1라디오 예약」, 「내일 6시에 라디오 틀어 줘」,
    //   「30분 뒤에 라디오 꺼 줘」, 「한 시간 뒤에 꺼」, 「듣기 예약 알려 줘」, 「듣기 예약 지워 줘」, 「끄기 예약 취소」

    func malCheori(_ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let zl = z.lowercased()
        let pj = BangsongEngine.bangsongPyojun(z)
        let bangsongMal = z.contains("라디오") || zl.contains("fm") || z.contains("에프엠") || z.contains("방송") ||
            ["kbs", "mbc", "ebs", "obs", "tbs", "bbs", "표준fm", "fm4u", "해피fm", "쿨fm", "클래식fm", "사랑의소리", "한민족",
             "sbs", "cbs", "ytn", "와이티엔", "tbn", "교통방송", "국악방송", "극동방송", "불교방송", "국방fm", "경인방송", "ifm", "파워fm", "러브fm", "음악fm", "고릴라디오", "조이포유", "반디"].contains { pj.contains($0) }   // 2.122.0 새 라디오
        let yeyakMal = z.contains("예약")
        // 1. 예약 듣기·지우기
        if z.contains("듣기예약") || (yeyakMal && bangsongMal && !z.contains("시")) {
            if z.contains("지워") || z.contains("취소") || z.contains("없애") {
                if z.contains("끄기") {
                    kkeugiChwiso()
                    dap("끄기 예약을 거두었습니다.", false)
                    return true
                }
                let n = mok.count
                moduJiugi()
                dap(n == 0 ? "지울 듣기 예약이 없습니다." : "듣기 예약 \(n)개를 모두 지웠습니다.", false)
                return true
            }
            if z.contains("알려") || z.contains("뭐") || z.contains("무슨") || z.contains("있어") || z.contains("들려") {
                if mok.isEmpty { dap("듣기 예약이 없습니다.", false); return true }
                dap("듣기 예약 \(mok.count)개입니다. " + mok.map { $0.julMal }.joined(separator: ". ") + ". " + daeumMal + (kkeugiMal.isEmpty ? "" : " " + kkeugiMal), false)
                return true
            }
        }
        // 2. 끄기 예약 — 「30분 뒤에 꺼」, 「한 시간 있다가 라디오 꺼 줘」
        let kkeugi = ["꺼", "끄", "그만", "멈춰"].contains { z.contains($0) }
        if kkeugi, let n = DeutgiYeyak.bunDwi(z) {
            if z.contains("취소") { kkeugiChwiso(); dap("끄기 예약을 거두었습니다.", false); return true }
            kkeugiYeyak(n)
            dap("\(DeutgiYeyak.bunMal(n)) 뒤에 방송을 끄겠습니다." + (BangsongEngine.shared.itda ? "" : " 지금은 틀고 있는 방송이 없습니다."), false)
            return true
        }
        if z.contains("끄기예약") && (z.contains("취소") || z.contains("지워") || z.contains("없애")) {
            kkeugiChwiso()
            dap("끄기 예약을 거두었습니다.", false)
            return true
        }
        // 3. 틀기 예약 — 시각과 방송이 함께 있고, 틀어·켜·예약 같은 말이 있을 때만
        guard bangsongMal, (z.contains("틀어") || z.contains("켜") || yeyakMal || z.contains("들려")), !kkeugi else { return false }
        let banbok: String
        if z.contains("평일") || z.contains("주중") { banbok = "pyeongil" }
        else if z.contains("주말") { banbok = "jumal" }
        else if z.contains("매일") || z.contains("날마다") || z.contains("매일아침") || z.contains("아침마다") || z.contains("저녁마다") { banbok = "maeil" }
        else { banbok = "hanbeon" }
        guard banbok != "hanbeon" || z.hasPrefix("오늘") || z.hasPrefix("내일") || z.hasPrefix("모레") || yeyakMal else { return false }
        guard var t = JeonggiHochul.sigakDeul(z).first else {
            if yeyakMal || banbok != "hanbeon" {
                dap("몇 시에 틀지 함께 말씀해 주십시오. 예를 들면 매일 아침 7시에 MBC 표준FM 틀어 줘라고 하시면 됩니다.", false)
                return true
            }
            return false
        }
        if (z.contains("아침") || z.contains("오전") || z.contains("새벽")) && t.0 == 12 { t.0 = 0 }
        let cal = Calendar.current
        var nal = ""
        if banbok == "hanbeon" {
            var d = cal.startOfDay(for: Date())
            if z.hasPrefix("내일") { d = cal.date(byAdding: .day, value: 1, to: d) ?? d }
            else if z.hasPrefix("모레") { d = cal.date(byAdding: .day, value: 2, to: d) ?? d }
            else if !z.hasPrefix("오늘") {
                // 「7시에 라디오 예약」 — 오늘 그 시각이 지났으면 내일
                if let tt = cal.date(bySettingHour: t.0, minute: t.1, second: 0, of: Date()), tt <= Date() { d = cal.date(byAdding: .day, value: 1, to: d) ?? d }
            }
            nal = JeonggiHochul.ymd(d)
        }
        let kkeulBun = DeutgiYeyak.bunDwi(z.components(separatedBy: "틀어").dropFirst().joined()) ?? 0
        Task {
            let b = BangsongEngine.shared
            if b.chaeneolDeul.isEmpty { await b.chaeneolBatgi() }
            let c = await MainActor.run { DeutgiYeyak.chaeneolChatgi(z) }
            await MainActor.run {
                guard let c = c else {
                    let ireum = b.kindChaeneol("radio").prefix(6).map { $0.name }.joined(separator: ", ")
                    dap("어느 방송을 틀지 알아듣지 못했습니다. \(ireum) 가운데 하나를 넣어 다시 말씀해 주십시오.", false)
                    return
                }
                let h = DeutgiYeyakHang(id: DeutgiYeyak.saeId(), si: t.0, bun: t.1, banbok: banbok, nal: nal,
                                        chaeneolId: c.id, chaeneolIreum: c.name, kkeulBun: kkeulBun, kyeojim: true)
                self.deohagi(h)
                dap("\(h.julMal)로 예약했습니다. 그 시각에 길눈이 켜져 있으면 저절로 틀고, 꺼져 있으면 알림을 울립니다. 알림을 누르시면 틉니다.", false)
            }
        }
        return true
    }

    /// 「30분 뒤」, 「1시간 뒤」, 「한 시간 있다가」, 「1시간 반 뒤」 — 분으로
    static func bunDwi(_ z: String) -> Int? {
        var s = z
        for (a, b) in [("한시간", "1시간"), ("두시간", "2시간"), ("세시간", "3시간"), ("반시간", "30분"), ("십분", "10분"), ("이십분", "20분"), ("삼십분", "30분")] {
            s = s.replacingOccurrences(of: a, with: b)
        }
        guard s.contains("뒤") || s.contains("후") || s.contains("있다가") || s.contains("지나") else { return nil }
        var hap = 0
        let ch = Array(s)
        var i = 0
        var chatum = false
        while i < ch.count {
            if ch[i].isNumber {
                var su = ""
                var j = i
                while j < ch.count, ch[j].isNumber { su.append(ch[j]); j += 1 }
                if let n = Int(su) {
                    if s[s.index(s.startIndex, offsetBy: j)...].hasPrefix("시간") {
                        hap += n * 60; chatum = true; j += 2
                        if j < ch.count, ch[j] == "반" { hap += 30; j += 1 }
                    } else if j < ch.count, ch[j] == "분" {
                        hap += n; chatum = true; j += 1
                    }
                }
                i = j
                continue
            }
            i += 1
        }
        return chatum && hap > 0 && hap <= 24 * 60 ? hap : nil
    }

    /// 말 속의 라디오 채널(방송 엔진의 이름 풀기와 같은 셈) — 이름이 없으면 지난번 라디오
    @MainActor static func chaeneolChatgi(_ z: String) -> Chaeneol? {
        let b = BangsongEngine.shared
        let ls = b.kindChaeneol("radio")
        guard !ls.isEmpty else { return nil }
        let zz = BangsongEngine.bangsongPyojun(z)
        let sae = ["kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang"]
        func sa(_ s: String) -> String? { sae.first { s.contains($0) } }
        let pul: [(Chaeneol, String?, String)] = ls.map { c in
            let nm = BangsongEngine.bangsongPyojun(c.name)
            let s0 = sa(nm)
            var bu = nm
            if let s0 = s0 { bu = bu.replacingOccurrences(of: s0, with: "") }
            return (c, s0, bu)
        }
        let s1 = sa(zz)
        let hubo = s1 == nil ? pul : pul.filter { $0.1 == s1 }
        if let c = hubo.first(where: { !$0.2.isEmpty && zz.contains(($0.1 ?? "") + $0.2) })?.0 { return c }
        let heunhan: Set<String> = ["tv", "fm", "라디오", "방송"]
        if let c = hubo.first(where: { $0.2.count >= 2 && !heunhan.contains($0.2) && zz.contains($0.2) })?.0 { return c }
        let jjal: [(String, String)] = [("표준", "표준fm"), ("4u", "fm4u"), ("해피", "해피fm"), ("쿨", "쿨fm"), ("클래식", "클래식fm"),
                                        ("사랑의소리", "3라디오"), ("3라디오", "3라디오"), ("1라디오", "1라디오"), ("한민족", "한민족")]
        for (m, k) in jjal where zz.contains(m) {
            if let c = hubo.first(where: { $0.2.contains(k) })?.0 { return c }
        }
        if let s1 = s1 {
            let eutteum = ["mbc": "mbcsfm", "kbs": "kbs1r", "ebs": "ebsfm"]
            return hubo.first { $0.0.id == eutteum[s1] }?.0 ?? hubo.first?.0
        }
        let id = UserDefaults.standard.string(forKey: "gn.majimakRadio") ?? ""
        return ls.first { $0.id == id } ?? ls.first
    }
}

// MARK: - 화면

struct DeutgiYeyakView: View {
    @ObservedObject private var y = DeutgiYeyak.shared
    @ObservedObject private var b = BangsongEngine.shared
    @State private var saeYeyak = false
    @State private var goreun: DeutgiYeyakHang?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(y.daeumMal).font(.title3)
                if y.kkeulTtae != nil {
                    Text(y.kkeugiMal).font(.title3)
                    Button("끄기 예약 거두기") { y.kkeugiChwiso(); SoriEngine.shared.mal("끄기 예약을 거두었습니다.") }.buttonStyle(KeunDanchu())
                }
                Button("새 듣기 예약 만들기") { saeYeyak = true }.buttonStyle(KeunDanchu())
                if !y.mok.isEmpty {
                    Text("정해 둔 예약 \(y.mok.count)개").font(.title3.weight(.semibold))
                    Mokrok5(y.mok) { h in
                        Button(h.julMal) { goreun = h }.buttonStyle(KeunDanchu())
                    }
                }
                Text("끄기 예약 — 지금 나오는 방송을 정한 때에 끕니다").font(.title3.weight(.semibold))
                ForEach([15, 30, 60, 90], id: \.self) { n in
                    Button("\(DeutgiYeyak.bunMal(n)) 뒤에 끄기") {
                        y.kkeugiYeyak(n)
                        SoriEngine.shared.mal("\(DeutgiYeyak.bunMal(n)) 뒤에 방송을 끕니다." + (b.itda ? "" : " 지금은 틀고 있는 방송이 없습니다."))
                    }.buttonStyle(KeunDanchu())
                }
                DisclosureGroup("손대지 않고 틀기 — 아이폰 단축어 자동화") {
                    Text(DeutgiYeyakView.danchugeoMal).font(.body).fixedSize(horizontal: false, vertical: true)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("듣기 예약")
        .onAppear { if b.chaeneolDeul.isEmpty { Task { await b.chaeneolBatgi() } } }
        .sheet(isPresented: $saeYeyak) { DeutgiYeyakSaeView() }
        .sheet(item: $goreun) { h in DeutgiYeyakHangView(h: h) }
    }

    static let danchugeoMal = "아이폰은 앱이 정한 시각에 스스로 깨어날 수 없어서, 길눈이 꺼져 있으면 알림을 한 번 누르셔야 라디오가 나옵니다. 손대지 않고 틀고 싶으시면 아이폰의 단축어 앱에서 한 번만 정해 두시면 됩니다. 단축어 앱을 열고 아래쪽 자동화 탭에서 새로운 자동화를 고르고, 특정 시간을 골라 시각과 매일을 정한 뒤, 즉시 실행을 고르고 다음을 누릅니다. 동작 찾기 칸에 길눈을 넣어 길눈 라디오 틀기를 고르고, 채널 칸에 MBC 표준FM처럼 듣고 싶은 방송 이름을 넣은 뒤 완료를 누르시면 됩니다. 그 뒤로는 정한 시각에 손대지 않아도 길눈이 라디오를 틉니다. 끌 때는 같은 방법으로 길눈 라디오 끄기를 넣으시면 됩니다."
}

/// 새 예약 — 시각, 되풀이, 채널, 끌 때
struct DeutgiYeyakSaeView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var b = BangsongEngine.shared
    @State private var sigak: Date = Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var banbok = "maeil"
    @State private var nalSeontaek: Date = Date()
    @State private var chaeneolId = UserDefaults.standard.string(forKey: "gn.majimakRadio") ?? "mbcsfm"
    @State private var kkeulBun = 0
    @AccessibilityFocusState private var cheot: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("틀 시각", selection: $sigak, displayedComponents: .hourAndMinute)
                        .font(.title3)
                        .accessibilityFocused($cheot)
                    Picker("되풀이", selection: $banbok) {
                        Text("날마다").tag("maeil")
                        Text("평일만 — 월요일부터 금요일").tag("pyeongil")
                        Text("주말만 — 토요일과 일요일").tag("jumal")
                        Text("한 번만").tag("hanbeon")
                    }
                    .font(.title3)
                    if banbok == "hanbeon" {
                        DatePicker("날짜", selection: $nalSeontaek, in: Calendar.current.startOfDay(for: Date())..., displayedComponents: .date)
                            .font(.title3)
                    }
                    Picker("방송", selection: $chaeneolId) {
                        ForEach(b.kindChaeneol("radio")) { c in Text(c.julMal).tag(c.id) }
                    }
                    .font(.title3)
                    Picker("끌 때", selection: $kkeulBun) {
                        Text("끄지 않고 그대로").tag(0)
                        Text("30분 뒤 끄기").tag(30)
                        Text("1시간 뒤 끄기").tag(60)
                        Text("2시간 뒤 끄기").tag(120)
                    }
                    .font(.title3)
                }
                Section {
                    Button("이대로 예약하기") { jeojang() }.font(.title3.weight(.semibold))
                    Button("그만두기") { dismiss() }.font(.title3)
                }
            }
            .navigationTitle("새 듣기 예약")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if b.chaeneolDeul.isEmpty { Task { await b.chaeneolBatgi() } }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheot = true }
            }
        }
    }

    private func jeojang() {
        let cal = Calendar.current
        let si = cal.component(.hour, from: sigak)
        let bun = cal.component(.minute, from: sigak)
        guard let c = b.kindChaeneol("radio").first(where: { $0.id == chaeneolId }) ?? b.kindChaeneol("radio").first else {
            SoriEngine.shared.mal("채널 목록을 받지 못했습니다. 통신을 확인하신 뒤 다시 눌러 주십시오.")
            return
        }
        var nal = ""
        if banbok == "hanbeon" {
            nal = JeonggiHochul.ymd(nalSeontaek)
            if let t = cal.date(bySettingHour: si, minute: bun, second: 0, of: nalSeontaek), t <= Date() {
                SoriEngine.shared.mal("이미 지난 시각입니다. 날짜나 시각을 다시 골라 주십시오.")
                return
            }
        }
        let h = DeutgiYeyakHang(id: DeutgiYeyak.saeId(), si: si, bun: bun, banbok: banbok, nal: nal,
                                chaeneolId: c.id, chaeneolIreum: c.name, kkeulBun: kkeulBun, kyeojim: true)
        DeutgiYeyak.shared.deohagi(h)
        SoriEngine.shared.mal("\(h.julMal)로 예약했습니다.")
        dismiss()
    }
}

/// 예약 하나 — 켜고 끄기, 지우기, 지금 틀어 보기
struct DeutgiYeyakHangView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var y = DeutgiYeyak.shared
    let h: DeutgiYeyakHang
    @AccessibilityFocusState private var cheot: Bool

    var body: some View {
        let jigeum = y.mok.first { $0.id == h.id } ?? h
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(jigeum.julMal).font(.title3).accessibilityFocused($cheot)
                    Button(jigeum.kyeojim ? "이 예약 잠시 끄기" : "이 예약 다시 켜기") {
                        y.kyeogiKkeugi(h.id)
                        SoriEngine.shared.mal(jigeum.kyeojim ? "이 예약을 꺼 두었습니다." : "이 예약을 다시 켰습니다.")
                    }.buttonStyle(KeunDanchu())
                    Button("지금 틀어 보기") {
                        if let c = BangsongEngine.shared.chaeneolDeul.first(where: { $0.id == h.chaeneolId }) {
                            BangsongEngine.shared.chaeneolTeulgi(c)
                        }
                    }.buttonStyle(KeunDanchu())
                    Button("이 예약 지우기") {
                        y.jiugi(h.id)
                        SoriEngine.shared.mal("예약을 지웠습니다.")
                        dismiss()
                    }.buttonStyle(KeunDanchu())
                    Button("닫기") { dismiss() }.buttonStyle(KeunDanchu())
                }
                .padding()
            }
            .navigationTitle("듣기 예약")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheot = true } }
        }
    }
}

// MARK: - 단축어 — 정한 시각에 손대지 않고 틀기(아이폰 단축어 「개인 자동화」에 넣어 씀)

@available(iOS 17.0, *)
struct RadioTeulgiIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource = "길눈 라디오 틀기"
    static var description = IntentDescription("정한 라디오 방송을 길눈으로 틉니다. 단축어 자동화에 넣으면 정한 시각에 손대지 않아도 나옵니다.")
    static var openAppWhenRun: Bool = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "방송", default: "MBC 표준FM")
    var chaeneol: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        await MainActor.run { Bonche.shared.sijak() }
        let (m, _) = await BangsongEngine.shared.chaeneolMalro(chaeneol + " 라디오", kind: "radio")
        Girok.shared.namgi("deutgi_danchugeo", ["mal": String(chaeneol.prefix(30))])
        return .result(dialog: IntentDialog(stringLiteral: m))
    }
}

struct RadioKkeugiIntent: AppIntent {
    static var title: LocalizedStringResource = "길눈 라디오 끄기"
    static var description = IntentDescription("길눈에서 나오는 방송을 끕니다.")
    static var openAppWhenRun: Bool = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    func perform() async throws -> some IntentResult & ProvidesDialog {
        await MainActor.run { BangsongEngine.shared.geuman(malHagi: false) }
        return .result(dialog: "방송을 껐습니다.")
    }
}
