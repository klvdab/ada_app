// 긴급통화 받기 — 자봉 앱을 길눈님 전화기로 (자봉 앱 2.1.0, 빌드 261001-9, 대표님 승인 "1")
// ① "함께하겠습니다" 한 번에: 나스 rel.php 에 대기 등록(늘 켜 둠) + 카메라·마이크 허락 + 폰 알림 주소를 나스 apns.php 에 맡김
// ② 길손님이 도움을 청하면 나스가 애플 알림(VoIP)을 보내고, 폰은 일반 전화처럼 "길손님이 도움을 청합니다"를 띄워 울림(CallKit)
//    잠겨 있어도, 다른 앱을 쓰고 있어도. 받으시면 곧바로 통화(JabongTonghwa)
// ③ 다른 길눈님이 먼저 받으면 벨을 멈추고 "다른 분께 연결되었습니다. 감사합니다"
// 2.4.0 (261004-G1, 이사장님 승인 2026-10-04) 긴급통화는 자봉 앱 하나로 받습니다.
//   가족·지인으로 받기 — 길눈님이 불러 주신 이음 번호 여섯 자리와 부르실 내 이름을 넣으면 그 길눈님 명단에 등록(나스 jiin.php a=ieum).
//     이때 이 폰의 대기 열쇠(k)가 지인 열쇠가 되어, 그 길눈님이 나를 고르시면 이 폰만 "○○ 님이 화상통화를 요청합니다"로 울림.
//   긴급통화 받지 않기 — 점지도만 그려 주시고 통화는 원치 않는 분. 대기를 끄고 알림 주소도 지움. 언제든 다시 켤 수 있음.
// 대표님 약속: 별명만(실명·전화번호는 화면에 없음), 자원봉사자는 수료 번호로, 해설사는 협회에 등록한 전화번호로 확인만.
import Foundation
import SwiftUI
import UIKit
import PushKit
import CallKit
import AVFoundation

final class JabongDaegi: NSObject, ObservableObject, PKPushRegistryDelegate, CXProviderDelegate {
    static let shared = JabongDaegi()
    private let d = UserDefaults.standard
    private let REL = "/eyec/rel.php"

    /// 받을 갈래 — 자원봉사자(haebong), 현장영상해설사(haeseolsa)
    @Published private(set) var kind: String
    @Published private(set) var byeol: String
    @Published private(set) var kyeojim: Bool          // 긴급통화 받기를 켜 둠
    @Published private(set) var sangtae = ""
    /// 2.4.0 가족·지인으로 등록된 길눈님 이름들과, 그분들이 부르실 내 이름
    @Published private(set) var gajok: [String]
    @Published private(set) var gajokIreum: String
    /// 2.4.0 긴급통화 받지 않기를 고르심
    @Published private(set) var geobu: Bool

    /// 이 폰의 대기 열쇠(나스 rel.php 의 k) — 처음 한 번 만들어 둠
    let k: String

    private var registry: PKPushRegistry?
    private let provider: CXProvider
    private let callCtrl = CXCallController()
    /// 지금 울리는 부름
    private var ulim: (uuid: UUID, room: String, mok: String)?
    private var salpimTimer: Timer?
    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 울리기 시작한 때 — 안드로이드와 같이 100초가 넘으면 스스로 멈춤
    private var ulimTtae = Date()
    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기를 누르고 내 것인지 확인하는 중인 전화(그동안은 통화 중과 같게 봄)와 그 받기 동작.
    ///   batgiChwiso 는 기다리는 사이 끊으셨거나 전화 화면이 시간을 넘긴 것 — 결과가 와도 통화를 열지 않음
    private var batgiUuid: UUID?
    private var batgiRoom = ""
    private var batgiAction: CXAnswerCallAction?
    private var batgiChwiso = false
    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 확인에 쓰는 시간 한도(초) — 전화 화면이 기다려 주는 동안 끝나게
    private static let BATGI_HANDO: TimeInterval = 8

    private override init() {
        kind = d.string(forKey: "jb.daegiKind") ?? ""
        byeol = d.string(forKey: "jb.daegiByeol") ?? ""
        kyeojim = d.bool(forKey: "jb.daegiOn")
        gajok = d.stringArray(forKey: "jb.gajok") ?? []
        gajokIreum = d.string(forKey: "jb.gajokIreum") ?? ""
        geobu = d.bool(forKey: "jb.geobu")
        if let kk = d.string(forKey: "jb.daegiK"), !kk.isEmpty {
            k = kk
        } else {
            let s = "abcdefghijkmnpqrstuvwxyz23456789"
            let kk = "jb" + String((0..<20).map { _ in s.randomElement()! })
            d.set(kk, forKey: "jb.daegiK")
            k = kk
        }
        let cfg = CXProviderConfiguration()
        cfg.supportsVideo = true
        cfg.maximumCallGroups = 1
        cfg.maximumCallsPerCallGroup = 1
        cfg.supportedHandleTypes = [.generic]
        cfg.includesCallsInRecents = false
        provider = CXProvider(configuration: cfg)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    /// 앱을 켤 때 — 애플 알림(VoIP) 받을 준비. 꺼진 앱도 이것으로 깨어남
    func sijak() {
        let r = PKPushRegistry(queue: .main)
        r.delegate = self
        r.desiredPushTypes = [.voIP]
        registry = r
        if kyeojim { Task { await self.daegiAllim(on: true) } }   // 켤 때마다 대기를 새로 알림
        IceJuso.gaengsin()
    }

    var galraeIreum: String { kind.isEmpty ? "가족·지인" : (kind == "haeseolsa" ? "현장영상해설사" : "자원봉사자") }
    /// 나스 대기에 올릴 갈래와 이름 — 봉사 역할이 없으면 가족·지인(jiin)으로
    private var daegiKind: String { kind.isEmpty ? (gajok.isEmpty ? "" : "jiin") : kind }
    private var daegiWho: String { byeol.isEmpty ? gajokIreum : byeol }
    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기·거절 때 나스에 알릴 내 이름 — 가족·지인만 등록하신 분은 별명이 비어 있어
    /// 길눈이 받았다는 것(taken)을 못 보고 1분 30초를 기다렸음. 안드로이드와 같게 daegiWho, 그것도 없으면 "자봉"
    private var batneunIreum: String { daegiWho.isEmpty ? "자봉" : daegiWho }

    // MARK: 2.4.0 가족·지인으로 받기 / 긴급통화 받지 않기

    /// 이음 번호로 가족·지인 등록 — 성공하면 nil, 안 되면 까닭
    func gajokDeungrok(beonho: String, ireum: String) async -> String? {
        let b = beonho.filter { $0.isNumber }
        let nm = ireum.trimmingCharacters(in: .whitespaces)
        guard b.count == 6 else { return "이음 번호 여섯 자리를 넣어 주십시오." }
        guard !nm.isEmpty else { return "길눈님이 부르실 내 이름을 적어 주십시오." }
        _ = await AVCaptureDevice.requestAccess(for: .video)
        _ = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            AVAudioSession.sharedInstance().requestRecordPermission { c.resume(returning: $0) }
        }
        guard let dt = try? await Tongsin.shared.getSae("/eyec/jiin.php", ["a": "ieum", "beonho": b, "name": nm, "k": k]),
              let j = try? JSONSerialization.jsonObject(with: dt) as? [String: Any] else {
            return "통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."
        }
        guard (j["ok"] as? Bool) == true else { return (j["error"] as? String) ?? "등록을 마치지 못했습니다." }
        let who = (j["who"] as? String) ?? "길눈님"
        await MainActor.run {
            if !self.gajok.contains(who) { self.gajok.append(who) }
            self.gajokIreum = nm
            self.kyeojim = true
            self.geobu = false
            self.d.set(self.gajok, forKey: "jb.gajok"); self.d.set(nm, forKey: "jb.gajokIreum")
            self.d.set(true, forKey: "jb.daegiOn"); self.d.set(false, forKey: "jb.geobu")
        }
        await daegiAllim(on: true)
        tokenMatgigi()
        Girok.shared.namgi("jabong_gajok", [:])
        return nil
    }

    /// 긴급통화 받지 않기 — 대기를 끄고 알림 주소도 지움
    func geobuhagi() async {
        await daegiAllim(on: false)
        if let bd = try? JSONSerialization.data(withJSONObject: ["who": k]) { _ = await Tongsin.shared.post("/eyec/apns.php", ["a": "jiugi"], bd) }
        await MainActor.run {
            self.kyeojim = false; self.geobu = true
            self.d.set(false, forKey: "jb.daegiOn"); self.d.set(true, forKey: "jb.geobu")
        }
        Girok.shared.namgi("jabong_geobu", [:])
    }

    /// 다시 받기로 마음을 바꾸심 — 받지 않기만 풀고, 역할이 있으면 다시 켬
    func geobuPulgi() async {
        await MainActor.run { self.geobu = false; self.d.set(false, forKey: "jb.geobu") }
        if !daegiKind.isEmpty { await swigi(false) }
    }

    // MARK: 함께하겠습니다 / 잠시 쉬기

    /// 함께하겠습니다 — 성공하면 nil, 안 되면 까닭
    func hamkke(kind: String, byeol: String, hwagin: String) async -> String? {
        let b = byeol.trimmingCharacters(in: .whitespaces)
        guard !b.isEmpty else { return "길눈님 별명을 적어 주십시오." }
        // 카메라·마이크 허락을 지금 받아 둠 — 통화 때 허용 창을 찾는 일이 없게
        _ = await AVCaptureDevice.requestAccess(for: .video)
        _ = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            AVAudioSession.sharedInstance().requestRecordPermission { c.resume(returning: $0) }
        }
        var q = ["a": "daegi", "k": k, "on": "1", "kind": kind, "who": b, "hangsang": "1"]
        if !HanPon.gd.isEmpty { q["gd"] = HanPon.gd }   // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 한 폰 표 — 같은 폰의 내 길눈 요청은 울리지 않게
        if kind == "haeseolsa" { q["tel"] = hwagin } else { q["surye"] = hwagin }
        guard let dt = try? await Tongsin.shared.getSae(REL, q),
              let j = try? JSONSerialization.jsonObject(with: dt) as? [String: Any] else {
            return "통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."
        }
        guard (j["ok"] as? Bool) == true else { return (j["msg"] as? String) ?? "함께하기를 마치지 못했습니다." }
        await MainActor.run {
            self.kind = kind; self.byeol = b; self.kyeojim = true; self.geobu = false
            self.d.set(kind, forKey: "jb.daegiKind"); self.d.set(b, forKey: "jb.daegiByeol"); self.d.set(true, forKey: "jb.daegiOn"); self.d.set(false, forKey: "jb.geobu")
        }
        tokenMatgigi()
        Girok.shared.namgi("jabong_hamkke", ["kind": kind])
        return nil
    }

    /// 잠시 쉬기 / 다시 함께하기
    func swigi(_ swim: Bool) async {
        await daegiAllim(on: !swim)
        await MainActor.run {
            self.kyeojim = !swim
            self.d.set(!swim, forKey: "jb.daegiOn")
        }
        if swim, let bd = try? JSONSerialization.data(withJSONObject: ["who": k]) { _ = await Tongsin.shared.post("/eyec/apns.php", ["a": "jiugi"], bd) }
        if !swim { tokenMatgigi() }
    }

    private func daegiAllim(on: Bool) async {
        let kd = daegiKind, w = daegiWho
        guard !kd.isEmpty, !w.isEmpty else { return }
        var q = ["a": "daegi", "k": k, "on": on ? "1" : "0", "kind": kd, "who": w, "hangsang": "1"]
        if !HanPon.gd.isEmpty { q["gd"] = HanPon.gd }   // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 한 폰 표
        _ = try? await Tongsin.shared.getSae(REL, q)
    }

    /// 폰 알림 주소를 나스에 맡김
    private func tokenMatgigi() {
        guard kyeojim, let t = registry?.pushToken(for: .voIP) else { return }
        let hex = t.map { String(format: "%02x", $0) }.joined()
        let bon: [String: Any] = ["who": k, "voip": hex, "app": "jabong", "kind": daegiKind]
        guard let bd = try? JSONSerialization.data(withJSONObject: bon) else { return }
        Task { _ = await Tongsin.shared.post("/eyec/apns.php", ["a": "deung"], bd) }
    }

    // MARK: PKPushRegistryDelegate

    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        tokenMatgigi()
    }

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        let p = payload.dictionaryPayload
        let room = (p["room"] as? String) ?? ""
        let mok = (p["mok"] as? String) ?? ""
        let gal = (p["galrae"] as? String) ?? ""
        let buleun = (p["byeol"] as? String) ?? ""
        // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 같은 폰의 내 길눈이 청한 것 — 애플 약속대로 전화 화면은 알리되 곧바로 말없이 닫음
        let gd = (p["gd"] as? String) ?? ""
        let naPon = !gd.isEmpty && gd == HanPon.gd
        IceJuso.gaengsin()   // 2.5.0 받기 전에 영상 다리 주소를 새로
        let uuid = UUID()
        // 애플 약속: 이 알림을 받으면 반드시 전화 화면을 띄워야 함
        let u = CXCallUpdate()
        u.remoteHandle = CXHandle(type: .generic, value: "길손님")
        if gal == "jiin" {
            u.localizedCallerName = buleun.isEmpty ? "길눈님이 화상통화를 요청합니다" : buleun + " 님이 화상통화를 요청합니다"
        } else {
            u.localizedCallerName = gal == "haeseolsa" ? "길손님이 현장영상해설사를 청합니다" : "길손님이 도움을 청합니다"
        }
        u.hasVideo = true
        u.supportsHolding = false
        u.supportsGrouping = false
        u.supportsUngrouping = false
        u.supportsDTMF = false
        provider.reportNewIncomingCall(with: uuid, update: u) { [weak self] e in
            completion()
            guard let self = self else { return }
            if naPon {
                self.provider.reportCall(with: uuid, endedAt: nil, reason: .remoteEnded)
                Girok.shared.namgi("jabong_napon", [:])
                return
            }
            // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 통화 중(받기 확인 중 포함)에 온 부름은 말없이 버리지 않고 나스에 거절로 알림 —
            //   길눈님이 1분 30초를 기다리지 않고 곧바로 다른 분께 넘기시게
            let bappeum = JabongTonghwa.shared.tonghwaJung || self.batgiUuid != nil
            if e != nil || room.isEmpty || bappeum {
                self.provider.reportCall(with: uuid, endedAt: nil, reason: .failed)
                // 지금 내가 받은 그 방이면(알림이 겹쳐 온 것) 거절을 보내지 않음 — 내 받기가 풀리지 않게
                let naBang = room == JabongTonghwa.shared.room || room == self.batgiRoom
                if bappeum && !room.isEmpty && !naBang {
                    self.geojeolAllim(room)
                    Girok.shared.namgi("jabong_bappeum", [:])
                }
                return
            }
            self.ulim = (uuid, room, mok)
            self.ulimTtae = Date()
            Girok.shared.namgi("jabong_ulim", ["gal": gal])
            self.salpigi()
        }
    }

    /// 울리는 동안 2초마다: 다른 분이 받았는지, 길손님이 그만두었는지
    private func salpigi() {
        salpimTimer?.invalidate()
        salpimTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] t in
            guard let self = self, let u = self.ulim else { t.invalidate(); return }
            // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 100초 넘게 울리면 받지 않은 전화로 닫고 벨을 멈춤(안드로이드와 같음)
            if Date().timeIntervalSince(self.ulimTtae) > 100 {
                self.ulimKkeut(.unanswered)
                Girok.shared.namgi("jabong_ulim_hando", [:])
                return
            }
            Task {
                guard let dt = try? await Tongsin.shared.getSae(self.REL, ["a": "jindo", "room": u.room]),
                      let j = try? JSONSerialization.jsonObject(with: dt) as? [String: Any] else { return }
                let sal = (j["sal"] as? Bool) ?? true
                let takenK = (j["takenK"] as? String) ?? ""
                let gd = (j["gd"] as? String) ?? ""
                await MainActor.run {
                    guard let cur = self.ulim, cur.uuid == u.uuid else { return }
                    if !gd.isEmpty && gd == HanPon.gd {
                        // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 나스가 알려 준 요청 폰이 이 폰이면 말없이 닫음
                        self.ulimKkeut(.remoteEnded)
                    } else if !takenK.isEmpty && takenK != self.k {
                        self.ulimKkeut(.answeredElsewhere)
                        SoriEngine.shared.mal("다른 분께 연결되었습니다. 감사합니다.")
                    } else if !sal {
                        self.ulimKkeut(.remoteEnded)
                    }
                }
            }
        }
    }

    private func ulimKkeut(_ why: CXCallEndedReason) {
        salpimTimer?.invalidate(); salpimTimer = nil
        if let u = ulim { provider.reportCall(with: u.uuid, endedAt: nil, reason: why) }
        ulim = nil
    }

    /// 통화를 마칠 때 전화 화면도 닫음
    func tonghwaKkeut(_ uuid: UUID) {
        // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 전화 화면 닫기가 안 되면(이미 닫힌 전화 등) 통화 화면을 직접 정리 — 화면이 남지 않게
        callCtrl.request(CXTransaction(action: CXEndCallAction(call: uuid))) { e in
            guard e != nil else { return }
            DispatchQueue.main.async {
                Girok.shared.namgi("jabong_kkeut_oryu", [:])
                JabongTonghwa.shared.kkeunki(bonaegi: true)
            }
        }
    }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 전화 화면의 이 전화가 아직 살아 있는지(끊기지 않았는지)
    private func jeonhwaSalainna(_ uuid: UUID) -> Bool {
        guard let c = callCtrl.callObserver.calls.first(where: { $0.uuid == uuid }) else { return false }
        return !c.hasEnded
    }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 확인을 그만둠 — 받기 동작은 실패로 돌려주고, 결과가 오면 열지 않음
    private func batgiGeumanduki(_ why: String) {
        guard batgiUuid != nil else { return }
        batgiChwiso = true
        batgiAction?.fail()
        batgiAction = nil
        Girok.shared.namgi("jabong_batgi_chwiso", ["why": why])
    }

    // MARK: CXProviderDelegate

    func providerDidReset(_ provider: CXProvider) {
        batgiGeumanduki("reset")   // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09)
        JabongTonghwa.shared.kkeunki(bonaegi: true)
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        guard let u = ulim, u.uuid == action.callUUID else { action.fail(); return }
        salpimTimer?.invalidate(); salpimTimer = nil
        ulim = nil
        // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 확인 중 표시 — 8초 안에 끝냄. 그사이 끊으시거나 전화 화면이 시간을 넘기면 그만둠
        batgiUuid = u.uuid
        batgiRoom = u.room
        batgiAction = action
        batgiChwiso = false
        Task {
            // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 다툼 — 내 것임이 확인될 때만 통화를 엶.
            //   진 쪽이 통화를 열었다가 닫으며 보낸 끊기 신호가 진짜 통화를 끊던 일. 지면 전화 화면도 닫음
            let g = await self.batgiDatum(u.room)
            await MainActor.run {
                let chwiso = self.batgiChwiso || self.batgiUuid != u.uuid
                if self.batgiUuid == u.uuid {
                    self.batgiUuid = nil; self.batgiRoom = ""; self.batgiAction = nil; self.batgiChwiso = false
                }
                if chwiso {
                    // 기다리는 사이 끊으셨거나 전화 화면이 시간을 넘김 — 내 받기가 되었을 수 있으면 나스에 놓아 줌(a=geojeol)
                    if g != .nam { self.geojeolAllim(u.room) }
                    Girok.shared.namgi("jabong_batgi_jim", ["g": "chwiso"])
                    return
                }
                if g == .nae && self.jeonhwaSalainna(u.uuid) {
                    action.fulfill()
                    JabongTonghwa.shared.sijak(room: u.room, mok: u.mok, uuid: u.uuid)
                    Girok.shared.namgi("jabong_batum", [:])
                    return
                }
                // 확인하지 못했거나(받기 답을 잃었을 수 있음), 내 것인데 전화가 이미 닫혔으면 받기를 놓아 줌 — 길눈님이 40초를 기다리지 않게
                if g != .nam { self.geojeolAllim(u.room) }
                action.fail()
                self.provider.reportCall(with: u.uuid, endedAt: nil, reason: g == .nam ? .answeredElsewhere : .failed)
                SoriEngine.shared.mal(g == .nam ? "다른 분이 먼저 받으셨습니다." : "연결되지 않았습니다.")
                Girok.shared.namgi("jabong_batgi_jim", ["g": g == .nam ? "nam" : (g == .nae ? "datim" : "motham")])
            }
        }
    }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 전화 화면이 받기를 기다리다 시간을 넘김 — 받기를 실패로 돌리고 전화를 닫음
    func provider(_ provider: CXProvider, timedOutPerforming action: CXAction) {
        if let a = action as? CXAnswerCallAction, batgiUuid == a.callUUID {
            batgiGeumanduki("sigan")
            provider.reportCall(with: a.callUUID, endedAt: nil, reason: .failed)
        }
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 확인을 기다리는 사이 끊으심 — 확인 결과가 와도 열지 않음(받기가 되었으면 그때 놓아 줌)
        if let b = batgiUuid, b == action.callUUID {
            batgiGeumanduki("kkeum")
            action.fulfill()
            return
        }
        if let u = ulim, u.uuid == action.callUUID {
            salpimTimer?.invalidate(); salpimTimer = nil
            ulim = nil
            // 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 전에 거절하심 — 나스에 알려 길눈님이 1분 30초를 기다리지 않게
            geojeolAllim(u.room)
        } else {
            JabongTonghwa.shared.kkeunki(bonaegi: true)
        }
        action.fulfill()
    }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 다툼의 결과 — 내 것, 다른 분 것, 확인하지 못함(통신·방 없음)
    enum BatgiGyeolgwa { case nae, nam, motham }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) a=take 를 보내고(답이 없으면 한 번 더), 답으로 내 것임이 분명하지 않으면
    ///   a=jindo(&k=내 열쇠)로 takenK 가 내 열쇠인지 확인함(두 번까지). 안드로이드 JabongDaegi.batgiDatum 과 같음
    ///   2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 모두 합쳐 8초 안에(한 번 묻기는 3초까지) — 전화 화면이 받기를 기다려 주는 동안 끝나게
    private func batgiDatum(_ room: String) async -> BatgiGyeolgwa {
        let handoTtae = Date().addingTimeInterval(JabongDaegi.BATGI_HANDO)
        let q = ["a": "take", "room": room, "who": batneunIreum, "k": k]
        var j = await relJson(q, handoTtae)
        if j == nil { j = await relJson(q, handoTtae) }
        if let j = j {
            if (j["ok"] as? Bool) == false { return .nam }
            let tk = ((j["takenK"] as? String) ?? (j["taken_k"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            if !tk.isEmpty { return tk == k ? .nae : .nam }
        }
        for _ in 0..<2 {
            guard let jj = await relJson(["a": "jindo", "room": room, "k": k], handoTtae) else { continue }
            let tk = ((jj["takenK"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            if tk == k { return .nae }
            return tk.isEmpty ? .motham : .nam
        }
        return .motham
    }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 받기 확인용 물음 — 한도 시각(handoTtae)을 넘기지 않게 한 번에 3초까지만 기다림
    private func relJson(_ q: [String: String], _ handoTtae: Date) async -> [String: Any]? {
        let namun = handoTtae.timeIntervalSinceNow
        guard namun > 0.3, var c = URLComponents(string: "https://lvd.ada.or.kr" + REL) else { return nil }
        c.queryItems = q.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let u = c.url else { return nil }
        let r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: min(3, namun))
        guard let dap = try? await URLSession.shared.data(for: r),
              (dap.1 as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSONSerialization.jsonObject(with: dap.0) as? [String: Any]
    }

    /// 2.17.0 (261009-I11, 이사장님 승인 2026-10-09) 거절을 나스에 알림(a=geojeol) — 길눈은 jindo 의 geojeol 수로 알아챔
    private func geojeolAllim(_ room: String) {
        guard !room.isEmpty else { return }
        let q = ["a": "geojeol", "room": room, "k": k, "who": batneunIreum]
        Task { _ = try? await Tongsin.shared.getSae(self.REL, q) }
        Girok.shared.namgi("jabong_geojeol", [:])
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        JabongTonghwa.shared.sorijariYeollim(audioSession)
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        JabongTonghwa.shared.sorijariDatim(audioSession)
    }
}

// MARK: 화면 — 봉사 탭의 "긴급통화 받기"
// 2.4.0 역할 셋(자원봉사자, 현장영상해설사, 가족·지인)과 "긴급통화 받지 않기"

struct JbHamkkeView: View {
    @ObservedObject private var g = JabongDaegi.shared
    @State private var kind = "haebong"
    @State private var byeol = ""
    @State private var hwagin = ""
    @State private var beonho = ""
    @State private var gajokIreum = ""
    @State private var allim = ""
    @State private var hal = false
    @State private var gochim = false
    @AccessibilityFocusState private var allimChojeom: Bool

    private var badeumMal: String {
        var m = "긴급통화를 받고 있습니다."
        if !g.kind.isEmpty { m += " \(g.kind == "haeseolsa" ? "현장영상해설사" : "자원봉사자"), 별명 \(g.byeol)." }
        if !g.gajok.isEmpty { m += " 가족·지인으로 등록된 곳은 " + g.gajok.map { $0 + " 님" }.joined(separator: ", ") + "입니다." }
        return m
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if !allim.isEmpty { Text(allim).font(.title3).accessibilityFocused($allimChojeom) }
                if g.geobu && !gochim {
                    Text("긴급통화를 받지 않기로 하셨습니다. 어떤 요청도 울리지 않습니다. 점지도 그리기는 그대로 쓰실 수 있습니다.").font(.title3)
                    Button("마음이 바뀌면 — 긴급통화 받기 시작") { geobuPulgi() }.buttonStyle(KeunDanchu()).disabled(hal)
                } else if g.kyeojim && !gochim {
                    Text(badeumMal).font(.title3)
                    Text("요청이 오면 일반 전화처럼 울립니다. 받으시면 곧바로 길눈님 카메라 화면과 말소리가 이어집니다.").font(.body)
                    Button("잠시 쉬기 — 긴급통화를 받지 않음") { swigi(true) }.buttonStyle(KeunDanchu()).disabled(hal)
                    DisclosureGroup("더 보기 펼치기") {
                        Button("가족·지인으로 받기 — 이음 번호 넣기") { kind = "gajok"; beonho = ""; gajokIreum = g.gajokIreum; gochim = true }.buttonStyle(KeunDanchu())
                        Button("역할이나 별명 고치기") { kind = g.kind.isEmpty ? "haebong" : g.kind; byeol = g.byeol; gochim = true }.buttonStyle(KeunDanchu())
                        Button("긴급통화 받지 않기 — 점지도만 그립니다") { geobu() }.buttonStyle(KeunDanchu()).disabled(hal)
                    }.font(.title3)
                } else if (!g.kind.isEmpty || !g.gajok.isEmpty) && !g.kyeojim && !gochim {
                    Text("지금은 쉬는 중입니다. 긴급통화가 울리지 않습니다.").font(.title3)
                    Button("다시 함께하기 — 긴급통화 받기") { swigi(false) }.buttonStyle(KeunDanchu()).disabled(hal)
                    Button("가족·지인으로 받기 — 이음 번호 넣기") { kind = "gajok"; beonho = ""; gajokIreum = g.gajokIreum; gochim = true }.buttonStyle(KeunDanchu())
                    Button("역할이나 별명 고치기") { kind = g.kind.isEmpty ? "haebong" : g.kind; byeol = g.byeol; gochim = true }.buttonStyle(KeunDanchu())
                    Button("긴급통화 받지 않기 — 점지도만 그립니다") { geobu() }.buttonStyle(KeunDanchu()).disabled(hal)
                } else {
                    Text("길눈님이 도움을 청할 때 받으실 역할을 고르십시오. 실명과 전화번호는 화면에 나오지 않습니다.").font(.body)
                    Button((kind == "haebong" ? "고름 — " : "") + "자원봉사자로 받기 — 교육을 마치신 분") { kind = "haebong"; hwagin = "" }.buttonStyle(KeunDanchu())
                    Button((kind == "haeseolsa" ? "고름 — " : "") + "현장영상해설사로 받기 — 협회 해설사") { kind = "haeseolsa"; hwagin = "" }.buttonStyle(KeunDanchu())
                    Button((kind == "gajok" ? "고름 — " : "") + "가족·지인으로 받기 — 길눈님께 이음 번호를 받으신 분") { kind = "gajok" }.buttonStyle(KeunDanchu())
                    if kind == "gajok" {
                        TextField("이음 번호 여섯 자리 — 길눈님께 받으신 번호", text: $beonho).keyboardType(.numberPad).textFieldStyle(.roundedBorder).font(.title3)
                        TextField("길눈님이 부르실 내 이름 — 보기: 큰딸, 김철수", text: $gajokIreum).textFieldStyle(.roundedBorder).font(.title3)
                        Button("가족·지인으로 등록하기") { gajokDeungrok() }.buttonStyle(KeunDanchu()).disabled(hal)
                    } else {
                        TextField("길눈님 별명", text: $byeol).textFieldStyle(.roundedBorder).font(.title3)
                        if kind == "haeseolsa" {
                            TextField("협회에 등록하신 전화번호 — 확인에만 씁니다", text: $hwagin).keyboardType(.phonePad).textFieldStyle(.roundedBorder).font(.title3)
                        } else {
                            TextField("수료 번호 — 협회에서 받으신 번호", text: $hwagin).textInputAutocapitalization(.characters).autocorrectionDisabled().textFieldStyle(.roundedBorder).font(.title3)
                        }
                        Button("함께하겠습니다") { hamkke() }.buttonStyle(KeunDanchu()).disabled(hal)
                    }
                    if gochim {
                        Button("그만두기") { gochim = false; allim = "" }.buttonStyle(KeunDanchu())
                    } else {
                        Button("긴급통화는 받지 않겠습니다 — 점지도만 그립니다") { geobu() }.buttonStyle(KeunDanchu()).disabled(hal)
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("긴급통화 받기")
    }

    private func allyeo(_ t: String) {
        allim = t
        allimChojeom = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }

    private func hamkke() {
        hal = true
        allyeo("잠시만요, 자리를 마련하고 있습니다.")
        Task {
            let t = await g.hamkke(kind: kind, byeol: byeol, hwagin: hwagin)
            await MainActor.run {
                hal = false
                if let t = t { allyeo(t) } else { gochim = false; allyeo("고맙습니다. 이제 함께하는 눈이 되셨습니다. 길눈님이 도움을 청하면 전화처럼 울립니다.") }
            }
        }
    }

    private func gajokDeungrok() {
        hal = true
        allyeo("잠시만요, 등록하고 있습니다.")
        Task {
            let t = await g.gajokDeungrok(beonho: beonho, ireum: gajokIreum)
            await MainActor.run {
                hal = false
                if let t = t { allyeo(t) } else {
                    gochim = false
                    allyeo("등록되었습니다. " + (g.gajok.last ?? "길눈") + " 님이 화상통화를 요청하시면 이 폰이 전화처럼 울립니다.")
                }
            }
        }
    }

    private func swigi(_ s: Bool) {
        hal = true
        Task {
            await g.swigi(s)
            await MainActor.run { hal = false; allyeo(s ? "잠시 쉽니다. 긴급통화가 울리지 않습니다." : "다시 함께합니다. 긴급통화가 울립니다.") }
        }
    }

    private func geobu() {
        hal = true
        Task {
            await g.geobuhagi()
            await MainActor.run { hal = false; gochim = false; allyeo("긴급통화를 받지 않습니다. 점지도 그리기는 그대로 쓰실 수 있습니다.") }
        }
    }

    private func geobuPulgi() {
        hal = true
        Task {
            await g.geobuPulgi()
            await MainActor.run {
                hal = false
                if g.kyeojim { allyeo("다시 긴급통화를 받습니다.") } else { gochim = true; allyeo("받으실 역할을 고르십시오.") }
            }
        }
    }
}
