// 듣기 예약 알람 — 길눈이 꺼져 있어도 정한 시각에 울리는 시계 알람(애플 AlarmKit, iOS 26 이상)
// 2.134.0 (빌드 261123-I88, 대클, 이사장님 승인 2026-11-23 「그래 수정하자」 — 「길눈이 켜져 있지 않아도 예약이 되게」)
// ① iOS 26 이상이고 알람을 허락하셨으면, 예약마다 시계 알람을 걺 — 길눈이 꺼져 있어도, 무음으로 해 두셔도 그 시각에 울림
// ② 알람 화면의 「라디오 틀기」를 누르시면 길눈이 열리며 그 예약 방송을 바로 틂, 「끄기」는 알람만 끔
// ③ 길눈이 켜져 있어 지킴이가 먼저 틀었으면 알람은 곧바로 거둠(라디오 위에 알람 소리가 겹치지 않게)
// ④ iOS 26 아래이거나 알람을 허락하지 않으셨으면 예전처럼 알림(누르시면 틂)으로 — 허락이 꺼져 있으면 설정 열기 링크를 띄움
import Foundation
import SwiftUI
import UIKit
import AppIntents
#if canImport(AlarmKit)
import AlarmKit
#endif

enum DeutgiAllam {
    private static let idKi = "gn.deutgiAllamIds"        // 건 알람 UUID 들(다시 걸 때 모두 거둠)
    private static let jjakKi = "gn.deutgiAllamJjak"     // 예약 id → 알람 UUID 들

    /// 이 폰에서 시계 알람을 쓸 수 있는지(iOS 26 이상)
    static var ssulSuItda: Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) { return true }
        #endif
        return false
    }

    /// 알람을 허락하셨는지 — 아직 여쭙지 않았으면 nil
    static var heorak: Bool? {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            switch AlarmManager.shared.authorizationState {
            case .authorized: return true
            case .denied: return false
            default: return nil
            }
        }
        #endif
        return false
    }

    /// 허락 여쭙기(처음 한 번) — 끝나면 허락 여부를 넘김
    static func heorakYeojjugi(_ kkeut: @escaping (Bool) -> Void) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            Task {
                let st = (try? await AlarmManager.shared.requestAuthorization()) ?? .denied
                await MainActor.run { kkeut(st == .authorized) }
            }
            return
        }
        #endif
        kkeut(false)
    }

    /// 예약 목록대로 알람을 모두 다시 걺. 걸었으면 true(그러면 알림은 걸지 않음)
    @discardableResult
    static func dasiGeolgi(_ mok: [DeutgiYeyakHang]) -> Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            moduGeodugi()
            guard heorak == true else { return false }
            var ids: [String] = []
            var jjak: [String: [String]] = [:]
            let now = Date()
            for h in mok where h.kyeojim {
                guard let sk = seukejul(h, now) else { continue }
                let uid = UUID()
                let alert = AlarmPresentation.Alert(
                    title: LocalizedStringResource(stringLiteral: "길눈 듣기 예약 \(JeonggiHochul.sigakMal(h.si, h.bun)) \(h.chaeneolIreum)"),
                    stopButton: AlarmButton(text: "끄기", textColor: .white, systemImageName: "stop.circle"),
                    secondaryButton: AlarmButton(text: "라디오 틀기", textColor: .white, systemImageName: "radio"),
                    secondaryButtonBehavior: .custom)
                let attr = AlarmAttributes<DeutgiAllamMeta>(presentation: AlarmPresentation(alert: alert),
                                                            metadata: DeutgiAllamMeta(yeyakId: h.id),
                                                            tintColor: .blue)
                let cfg = AlarmManager.AlarmConfiguration<DeutgiAllamMeta>(
                    countdownDuration: nil, schedule: sk, attributes: attr,
                    stopIntent: nil, secondaryIntent: DeutgiAllamTeulgiIntent(yeyakId: h.id), sound: .default)
                let hid = h.id
                Task {
                    do {
                        _ = try await AlarmManager.shared.schedule(id: uid, configuration: cfg)
                    } catch {
                        Girok.shared.namgi("deutgi_allam_oryu", ["e": String(String(describing: error).prefix(80)), "id": hid])
                    }
                }
                ids.append(uid.uuidString)
                jjak[h.id, default: []].append(uid.uuidString)
            }
            UserDefaults.standard.set(ids, forKey: idKi)
            UserDefaults.standard.set(jjak, forKey: jjakKi)
            Girok.shared.namgi("deutgi_allam_geolgi", ["n": ids.count])
            return true
        }
        #endif
        return false
    }

    /// 건 알람 모두 거두기
    static func moduGeodugi() {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            for s in UserDefaults.standard.stringArray(forKey: idKi) ?? [] {
                if let u = UUID(uuidString: s) { try? AlarmManager.shared.cancel(id: u) }
            }
            UserDefaults.standard.removeObject(forKey: idKi)
            UserDefaults.standard.removeObject(forKey: jjakKi)
        }
        #endif
    }

    /// 지킴이가 먼저 틀었을 때 — 울리는 알람을 멈춤(되풀이 예약은 다음 날 그대로 울림)
    static func meomchugi(_ yeyakId: String) {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let jjak = UserDefaults.standard.dictionary(forKey: jjakKi) as? [String: [String]] ?? [:]
            for s in jjak[yeyakId] ?? [] {
                if let u = UUID(uuidString: s) { try? AlarmManager.shared.stop(id: u) }
            }
        }
        #endif
    }

    /// 길눈이 켜져 있을 때 — 알람이 울리기 시작하면 곧바로 그 예약을 틀고 알람은 거둠(지킴이 15초를 기다리지 않게)
    private static var jikimiDolgo = false
    static func jikimi() {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            guard !jikimiDolgo else { return }
            jikimiDolgo = true
            Task {
                for await alarms in AlarmManager.shared.alarmUpdates {
                    let jjak = UserDefaults.standard.dictionary(forKey: jjakKi) as? [String: [String]] ?? [:]
                    for a in alarms where a.state == .alerting {
                        let u = a.id.uuidString
                        guard let yid = jjak.first(where: { $0.value.contains(u) })?.key else { continue }
                        await MainActor.run {
                            guard UIApplication.shared.applicationState == .active || BangsongEngine.shared.itda,
                                  let h = DeutgiYeyak.shared.mok.first(where: { $0.id == yid }) else { return }
                            DeutgiYeyak.shared.teulgi(h, t: DeutgiYeyak.oneulTtae(h, Date()) ?? Date(), jadong: true)
                        }
                    }
                }
            }
        }
        #endif
    }

    #if canImport(AlarmKit)
    @available(iOS 26.0, *)
    private static func seukejul(_ h: DeutgiYeyakHang, _ now: Date) -> Alarm.Schedule? {
        let si = Alarm.Schedule.Relative.Time(hour: h.si, minute: h.bun)
        switch h.banbok {
        case "hanbeon":
            guard h.nal.count == 8, let y = Int(h.nal.prefix(4)), let m = Int(h.nal.dropFirst(4).prefix(2)), let d = Int(h.nal.suffix(2)) else { return nil }
            var dc = DateComponents()
            dc.year = y; dc.month = m; dc.day = d; dc.hour = h.si; dc.minute = h.bun
            guard let t = Calendar.current.date(from: dc), t > now else { return nil }
            return .fixed(t)
        case "pyeongil":
            return .relative(.init(time: si, repeats: .weekly([.monday, .tuesday, .wednesday, .thursday, .friday])))
        case "jumal":
            return .relative(.init(time: si, repeats: .weekly([.saturday, .sunday])))
        default:
            return .relative(.init(time: si, repeats: .weekly([.monday, .tuesday, .wednesday, .thursday, .friday, .saturday, .sunday])))
        }
    }
    #endif

    /// 알람이 꺼져 있을 때 알리는 말과 설정 열기
    static let kkeojimMal = "아이폰 설정에서 길눈 알람 허락이 꺼져 있어, 길눈이 꺼져 있을 때는 알림만 울립니다. 알람을 켜시면 길눈이 꺼져 있어도 시계 알람처럼 울리고, 라디오 틀기를 누르시면 바로 틉니다. 아래 설정 열기를 누르시면 길눈 설정으로 갑니다."

    static func seoljeongYeolgi() {
        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
    }
}

#if canImport(AlarmKit)
@available(iOS 26.0, *)
struct DeutgiAllamMeta: AlarmMetadata {
    var yeyakId: String
}

/// 알람 화면의 「라디오 틀기」 — 길눈을 열며 그 예약 방송을 틂
@available(iOS 26.0, *)
struct DeutgiAllamTeulgiIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "길눈 예약 라디오 틀기"
    static var description = IntentDescription("듣기 예약 알람에서 그 예약 방송을 길눈으로 틉니다.")
    static var openAppWhenRun: Bool = true
    static var isDiscoverable: Bool = false

    @Parameter(title: "예약")
    var yeyakId: String

    init() { yeyakId = "" }
    init(yeyakId: String) { self.yeyakId = yeyakId }

    func perform() async throws -> some IntentResult {
        let id = yeyakId
        await MainActor.run {
            Bonche.shared.sijak()
            DeutgiYeyak.shared.allamNureum(id)
        }
        return .result()
    }
}
#endif
