// 긴급통화서비스 — 가족·지인(지정한 한 분), 자원봉사자, 현장영상해설사에게 화상통화를 청합니다.
// 웹 길눈 buleugi.html·domum4.html과 같은 길: 나스 /eyec/rel.php 로 부르고(call·jindo·hangup),
// 받으시면 곧장(누르지 않아도) 뒤 카메라와 마이크로 화상통화(WebRTC, 신호는 rel.php sig_put·sig_get, 나스 턴 서버).
// 받는 분 화면(nun4.html·자봉 앱)은 지금 그대로입니다.
// 이사장님 지시: 1분 30초 기다림, 말소리는 요청 시작·받음·연결 실패 때만, 통화 중에는 길눈 말소리를 내지 않음.
// 2.42.0 (261004-G1, 이사장님 승인 2026-10-04) 받는 쪽은 자봉 앱 하나로 — 가족·지인은 이음 번호로 등록한 그 한 분께만 울림.
//   가족·지인이 1분 30초 안에 받지 않으시면 "자원봉사자와 현장영상해설사에게 요청할까요?"를 여쭙고, 예 한 번이면 두 갈래 함께(dowum) 호출.
//   자원봉사자·현장영상해설사는 나스가 처음 15초 최근에 덜 받으신 다섯 분께 먼저, 그다음 모두에게 울림(기회 고르게).
import Foundation
import AVFoundation
import UIKit
import WebRTC

/// 2.43.0 (261004-I1, 이사장님 승인) 영상 다리(턴) 주소 — 나스 설정 쪽지 /eyec/ice.json 에서 읽음.
/// 다리를 리눅스 서버로 옮기는 날 앱을 새로 받지 않고 쪽지 한 줄로 넘어가게. 못 읽으면 마지막으로 받은 것, 그것도 없으면 나스 다리(3478).
/// 길눈과 자봉 앱이 함께 씀(자봉 앱도 Gilnun 폴더를 싣음).
enum IceJuso {
    static let kibon: [RTCIceServer] = [
        RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"]),
        RTCIceServer(urlStrings: ["turn:221.146.173.20:3478?transport=udp", "turn:221.146.173.20:3478?transport=tcp"],
                     username: "gilnun", credential: "gilnun-turn-260911-v8k2q")
    ]

    /// 통화를 청하거나 울릴 때 미리 받아 둠
    static func gaengsin() {
        Task.detached {
            guard let d = try? await Tongsin.shared.getSae("/eyec/ice.json"),
                  let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                  let l = o["ice"] as? [[String: Any]], !l.isEmpty,
                  let dd = try? JSONSerialization.data(withJSONObject: l) else { return }
            UserDefaults.standard.set(dd, forKey: "gn.iceJuso")
        }
    }

    static var servers: [RTCIceServer] {
        if let dd = UserDefaults.standard.data(forKey: "gn.iceJuso"),
           let l = try? JSONSerialization.jsonObject(with: dd) as? [[String: Any]] {
            let s: [RTCIceServer] = l.compactMap { o in
                guard let u = o["urls"] as? [String], !u.isEmpty else { return nil }
                if let un = o["username"] as? String, !un.isEmpty {
                    return RTCIceServer(urlStrings: u, username: un, credential: (o["credential"] as? String) ?? "")
                }
                return RTCIceServer(urlStrings: u)
            }
            if !s.isEmpty { return s }
        }
        return kibon
    }
}

enum GinGeupGalrae: String {
    case jiin, haebong, haeseolsa, dowum
    var ireum: String {
        switch self {
        case .jiin: return "가족·지인"
        case .haebong: return "자원봉사자"
        case .haeseolsa: return "현장영상해설사"
        case .dowum: return "자원봉사자와 현장영상해설사"
        }
    }
}

final class GinGeup: NSObject, ObservableObject, RTCPeerConnectionDelegate {
    static let shared = GinGeup()

    enum Sangtae { case eopseum, yocheong, yeongyeol, tonghwa }

    @Published private(set) var sangtae: Sangtae = .eopseum
    @Published private(set) var geul = ""
    @Published var malHan = ""
    /// 2.42.0 가족·지인이 받지 않으셨을 때 — 자원봉사자와 현장영상해설사에게 넘길지 여쭘
    @Published var neomgilkka = false
    @Published var naIrum: String = UserDefaults.standard.string(forKey: "gn.naIrum") ?? "" {
        didSet { UserDefaults.standard.set(naIrum, forKey: "gn.naIrum") }
    }
    private(set) var galrae: GinGeupGalrae = .jiin
    private(set) var saram: JiinSaram?

    private let REL = "/eyec/rel.php"
    private static let HANDO: Double = 90
    private var room = ""
    private var sijakTtae = Date()
    private var jindoTimer: Timer?
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 받으셨다는 소식 뒤 통화가 이어질 때까지 3초마다 받으신 분이 그대로인지 살핌
    private var badeunJikimTimer: Timer?
    private var badeunJikimJung = false
    private var sigTimer: Timer?
    private var sigN = 0
    private var sigMutneun = false
    private var cheot = true
    private var telMal = false
    private var junbiMal = false
    private var dasiHan = false
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 지인 쪽 신호가 닿지 않는다(targetOn 0)고 처음 들은 때(나스 secs) — 10초 이어지면 넘길지 여쭘
    private var targetEopTtae: Double?
    private var pc: RTCPeerConnection?
    private var capturer: RTCCameraVideoCapturer?

    private static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory(encoderFactory: RTCDefaultVideoEncoderFactory(),
                                        decoderFactory: RTCDefaultVideoDecoderFactory())
    }()

    // MARK: 부르기

    func yocheong(_ g: GinGeupGalrae, _ s: JiinSaram? = nil) {
        guard sangtae == .eopseum else { return }
        IceJuso.gaengsin()
        neomgilkka = false
        galrae = g
        saram = s
        cheot = true
        telMal = false
        junbiMal = false
        dasiHan = false
        targetEopTtae = nil
        room = "j" + String(UUID().uuidString.lowercased().filter { $0.isLetter || $0.isNumber }.prefix(10))
        sijakTtae = Date()
        sangtae = .yocheong
        var mal: String
        if g == .jiin {
            let nm = s?.name ?? "가족·지인"
            mal = "\(nm) 님께 화상통화를 요청합니다."
            if let s = s, !Jiin.shared.tel(s.id).isEmpty { mal += " 전화 걸기 단추도 있습니다." }
        } else {
            mal = "\(g.ireum)에게 호출 신호를 보냅니다."
        }
        geul = mal
        SoriEngine.shared.mal(mal)
        Girok.shared.namgi("gingeup_yocheong", ["g": g.rawValue])

        var where_ = ""
        var jari: [String: String] = [:]
        if let w = WichiEngine.shared.jigeum {
            where_ = String(format: "%.3f,%.3f", w.lat, w.lon)
            jari = ["lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon)]
        }
        let meonjeo = (g == .haeseolsa) ? "haeseolsa" : "jiin"
        let target = (g == .jiin) ? (s?.k ?? "") : ""
        var q: [String: String] = ["a": "call", "room": room, "who": naIrum.isEmpty ? "길눈 이용자" : naIrum,
                                   "where": where_, "gil": malHan, "meonjeo": meonjeo, "galrae": g.rawValue, "target": target]
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 한 폰 표 — 같은 폰의 내 자봉 앱은 울리지 않게(나스가 가림)
        if !HanPon.gd.isEmpty { q["gd"] = HanPon.gd }
        if let y = YeojeongEngine.shared.jigeum {
            q["mok"] = y.mokjeok.ireum
            q["mlat"] = String(y.mokjeok.lat)
            q["mlon"] = String(y.mokjeok.lon)
        }
        var bureum: [String: String] = ["a": "bureum", "room": room, "gil": malHan, "meonjeo": meonjeo, "galrae": g.rawValue, "target": target]
        bureum.merge(jari) { a, _ in a }
        let r = room
        let owner = Jiin.shared.owner
        Task {
            // 나스 통화 중계(턴) 서버가 꺼져 있으면 켬 — 웹 길눈과 같게
            _ = try? await Tongsin.shared.getSae("/jungye/turn_ctl.php", ["k": "turn-260911-p7wq", "a": "kyeogi"])
            _ = try? await Tongsin.shared.getSae("jeom_db.php", bureum)
            // 지인은 먼저 찍어 두고(누구에게 가는지 서버가 알게) 그다음 부름
            if g == .jiin, let s = s {
                _ = try? await Tongsin.shared.getSae("/eyec/jiin.php", ["a": "jjik", "owner": owner, "id": s.id, "room": r])
            }
            let ok = (try? await Tongsin.shared.getSae(self.REL, q)) != nil
            await MainActor.run {
                guard self.sangtae == .yocheong, self.room == r else { return }
                if !ok {
                    self.kkeut("요청을 보내지 못했습니다. 통신을 확인하시고 다시 눌러 주십시오.")
                    return
                }
                self.jindoTimer?.invalidate()
                self.jindoTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in self?.jindo() }
                self.jindo()
            }
        }
    }

    /// 요청 그만두기 / 통화 끊기
    func geumanhagi() {
        guard sangtae != .eopseum else { return }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 끊었다는 신호(bye)를 먼저 보내고 1.5초 뒤에 방을 닫음(a=hangup) —
        //   함께 보내면 방이 먼저 지워져 자봉이 끊긴 줄 모르던 일
        if pc != nil {
            sigPut(["t": "bye"])
            hangup(dwi: 1.5)
        } else {
            hangup()
        }
        kkeut(sangtae == .tonghwa ? "통화를 끊었습니다." : "요청을 그만두었습니다.")
    }

    func jeonhwa() {
        guard let s = saram else { return }
        let t = Jiin.shared.tel(s.id)
        guard !t.isEmpty, let u = URL(string: "tel:" + t) else { return }
        UIApplication.shared.open(u)
    }

    private func jindo() {
        guard sangtae == .yocheong, !room.isEmpty else { return }
        // 2.12.0 나스가 답하지 않아도 폰 시계로 한도를 지킴 — 요청이 끝없이 걸려 있지 않게
        if Date().timeIntervalSince(sijakTtae) >= GinGeup.HANDO + 10 {
            hangup()
            kkeut("연결되지 못했습니다. 통신이 약할 수 있습니다. 다시 요청하시거나 전화를 거실 수 있습니다.")
            return
        }
        let r = room
        Task {
            guard let d = try? await Tongsin.shared.getSae(REL, ["a": "jindo", "room": r]),
                  let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any], (j["ok"] as? Bool) == true else { return }
            await MainActor.run { self.jindoBatda(j, r) }
        }
    }

    private func jindoBatda(_ j: [String: Any], _ r: String) {
        guard sangtae == .yocheong, r == room else { return }
        let s = Chatgi.su(j["secs"]) ?? Date().timeIntervalSince(sijakTtae)
        if let t = j["taken"] as? String, !t.trimmingCharacters(in: .whitespaces).isEmpty {
            jindoTimer?.invalidate()
            jindoTimer = nil
            sangtae = .yeongyeol
            geul = "\(t) 님이 받으셨습니다. 잇는 중입니다."
            SoriEngine.shared.mal("\(t) 님이 받으셨습니다. 카메라와 마이크를 켭니다.")
            Girok.shared.namgi("gingeup_badeum", [:])
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 3초 사이에 그만두고 새로 요청하셨으면 옛 방의 통화를 열지 않음(안드로이드와 같음)
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
                guard let self = self, self.room == r else { return }
                self.tonghwaSijak()
            }
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 받으신 분이 받기를 놓으면(나스 taken 이 비면) 40초를 기다리지 않고 곧바로 마침
            badeunJikimTimer?.invalidate()
            badeunJikimTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in self?.badeunJikim() }
            // 2.12.0 받으신 뒤 40초 안에 통화가 이어지지 않으면 마침
            DispatchQueue.main.asyncAfter(deadline: .now() + 40) {
                guard self.sangtae == .yeongyeol, self.room == r else { return }
                self.hangup()
                self.kkeut("통화를 잇지 못했습니다. 다시 요청해 주십시오.")
            }
            return
        }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 받는 분이 거절하시면(나스 a=geojeol) 1분 30초를 기다리지 않음 —
        //   지인 한 분이면 한 번의 거절로, 여럿에게 갔으면 울린 분(dae) 모두가 거절하셨을 때
        let geojeol = Int(Chatgi.su(j["geojeol"]) ?? 0)
        let daeSu = Int(Chatgi.su(j["dae"]) ?? 0)
        if galrae == .jiin && geojeol >= 1 {
            let nm = saram?.name ?? "가족·지인"
            hangup()
            Girok.shared.namgi("gingeup_geojeol", ["g": galrae.rawValue])
            neomgilkkaMutgi("\(nm) 님이 지금 받기 어렵다고 하셨습니다. 자원봉사자와 현장영상해설사에게 요청할까요?")
            return
        }
        if galrae != .jiin && daeSu > 0 && geojeol >= daeSu {
            hangup()
            kkeut("\(galrae.ireum)가 지금 받기 어렵다고 하셨습니다. 다른 갈래를 고르시거나 잠시 뒤 다시 호출해 주십시오.")
            Girok.shared.namgi("gingeup_geojeol", ["g": galrae.rawValue, "n": geojeol])
            return
        }
        if galrae == .jiin {
            let nm = saram?.name ?? "가족·지인"
            if s >= 15 && !telMal {
                telMal = true
                let tel = saram.map { Jiin.shared.tel($0.id) } ?? ""
                SoriEngine.shared.mal("\(nm) 님이 아직 받지 않으셨습니다. 계속 요청 중입니다." + (tel.isEmpty ? "" : " 전화로 하시려면 전화 걸기를 누르십시오."))
            }
            let targetOn = (j["targetOn"] as? Bool) ?? ((j["targetOn"] as? Int).map { $0 != 0 } ?? true)
            if cheot && !targetOn && !junbiMal {
                junbiMal = true
                SoriEngine.shared.mal("\(nm) 님은 아직 자봉 앱으로 받기를 켜지 않으셨거나 받지 않기로 해 두셔서 신호가 닿지 않을 수 있습니다.")
            }
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 신호가 닿지 않는 지인(받기를 꺼 두셨거나 같은 폰의 내 자봉 앱)이면
            //   1분 30초를 말없이 기다리지 않고 10초 뒤 자원봉사자와 현장영상해설사에게 넘길지 여쭘(받으신 분이 있으면 위에서 이미 이음)
            if targetOn {
                targetEopTtae = nil
            } else if targetEopTtae == nil {
                targetEopTtae = s
            }
            if let t0 = targetEopTtae, s - t0 >= 10 {
                hangup()
                Girok.shared.namgi("gingeup_target_eopseum", [:])
                neomgilkkaMutgi("\(nm) 님께 지금 연결할 수 없습니다. 자원봉사자와 현장영상해설사에게 요청할까요?")
                return
            }
            if s >= GinGeup.HANDO {
                hangup()
                neomgilkkaMutgi("\(nm) 님이 받지 않으십니다. 자원봉사자와 현장영상해설사에게 요청할까요?")
                return
            }
        } else {
            let n = Int(Chatgi.su(j["dae"]) ?? 0)
            geul = n > 0 ? "\(galrae.ireum) \(n)분께 호출 중입니다." : "지금 받으실 수 있는 \(galrae.ireum)가 없습니다."
            if cheot && n == 0 {
                hangup()
                kkeut("지금 받으실 수 있는 \(galrae.ireum)가 없습니다. 다른 갈래를 고르시거나 잠시 뒤 다시 호출해 주십시오.")
                return
            }
            if s >= GinGeup.HANDO {
                hangup()
                kkeut("\(galrae.ireum)와 연결되지 못했습니다. 다른 갈래를 고르시거나 다시 호출해 주십시오.")
                return
            }
        }
        cheot = false
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) dwi 초 뒤에 방을 닫을 수 있게 — 끊었다는 신호(bye)가 먼저 닿도록
    private func hangup(dwi: Double = 0) {
        let r = room
        guard !r.isEmpty else { return }
        let rel = REL
        let bonae: () -> Void = { _ = Task { _ = try? await Tongsin.shared.getSae(rel, ["a": "hangup", "room": r]) } }
        if dwi > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + dwi, execute: bonae)
        } else {
            bonae()
        }
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 가족·지인께 닿지 않았을 때 — 자원봉사자와 현장영상해설사에게 넘길지 여쭙고,
    ///   말로 하기에도 「네·아니요」 물음으로 걸어 둠(다 여쭌 뒤 마이크를 한 번 엶). 단추(예·아니요)는 그대로
    private func neomgilkkaMutgi(_ mal: String) {
        kkeut(mal)
        neomgilkka = true
        MalHagi.shared.neomgilkkaMutgi(mal)
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 받으신 분이 받기를 놓았는지(자봉이 받기를 확인하지 못했거나 기다리는 사이 끊으면
    ///   나스 a=geojeol 로 taken 을 비움), 방이 닫혔는지 살핌 — 통화가 이어지면(tonghwa) 그침
    private func badeunJikim() {
        guard sangtae == .yeongyeol, !room.isEmpty else {
            badeunJikimTimer?.invalidate(); badeunJikimTimer = nil
            return
        }
        guard !badeunJikimJung else { return }
        badeunJikimJung = true
        let r = room
        Task {
            let d = try? await Tongsin.shared.getSae(REL, ["a": "jindo", "room": r])
            await MainActor.run {
                self.badeunJikimJung = false
                guard self.sangtae == .yeongyeol, self.room == r, let d = d,
                      let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any], (j["ok"] as? Bool) == true else { return }
                let sal = (j["sal"] as? Bool) ?? ((j["sal"] as? Int).map { $0 != 0 } ?? true)
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 받은 분이 스스로 놓아 주었을 때(나스 pulrim = geojeol)만 끝냄 —
                //   살아 있음 신호를 보내지 않는 옛 판 자봉은 25초 뒤 나스가 받은 분을 지우므로, 빈 taken 만으로는 끝내지 않음
                let noeum = ((j["pulrim"] as? String) ?? "") == "geojeol" && ((j["taken"] as? String) ?? "").trimmingCharacters(in: .whitespaces).isEmpty
                if !sal || noeum {
                    self.hangup()
                    Girok.shared.namgi("gingeup_badeun_noeum", ["sal": sal])
                    self.kkeut("받으신 분과 연결되지 못했습니다. 다시 요청해 주십시오.")
                }
            }
        }
    }

    private func kkeut(_ mal: String) {
        jindoTimer?.invalidate()
        jindoTimer = nil
        badeunJikimTimer?.invalidate()
        badeunJikimTimer = nil
        sigTimer?.invalidate()
        sigTimer = nil
        capturer?.stopCapture()
        capturer = nil
        pc?.close()
        pc = nil
        room = ""
        sangtae = .eopseum
        SoriEngine.shared.tonghwaJung = false
        geul = mal
        SoriEngine.shared.mal(mal)
        Girok.shared.namgi("gingeup_kkeut", [:])
    }

    // MARK: 화상통화

    private func tonghwaSijak() {
        guard sangtae == .yeongyeol, !room.isEmpty else { return }
        AVCaptureDevice.requestAccess(for: .video) { _ in
            AVAudioSession.sharedInstance().requestRecordPermission { _ in
                DispatchQueue.main.async { self.tonghwaIeum() }
            }
        }
    }

    private func tonghwaIeum() {
        guard sangtae == .yeongyeol else { return }
        SoriEngine.shared.tonghwaJung = true
        let f = GinGeup.factory
        let cfg = RTCConfiguration()
        cfg.iceServers = IceJuso.servers   // 2.43.0 나스 설정 쪽지에서
        cfg.sdpSemantics = .unifiedPlan
        cfg.continualGatheringPolicy = .gatherContinually
        let c = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: ["DtlsSrtpKeyAgreement": "true"])
        let pOpt: RTCPeerConnection? = f.peerConnection(with: cfg, constraints: c, delegate: self)
        guard let p = pOpt else {
            kkeut("통화를 열지 못했습니다. 다시 요청해 주십시오.")
            return
        }
        pc = p
        let aSrc = f.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
        p.add(f.audioTrack(with: aSrc, trackId: "a0"), streamIds: ["s0"])
        let vSrc = f.videoSource()
        p.add(f.videoTrack(with: vSrc, trackId: "v0"), streamIds: ["s0"])
        let cap = RTCCameraVideoCapturer(delegate: vSrc)
        capturer = cap
        let devs = RTCCameraVideoCapturer.captureDevices()
        if let dev = devs.first(where: { $0.position == .back }) ?? devs.first {
            let formats = RTCCameraVideoCapturer.supportedFormats(for: dev)
            let fmt = formats.min { GinGeup.pok($0) < GinGeup.pok($1) }
            if let fmt = fmt {
                let maxFps = fmt.videoSupportedFrameRateRanges.map { $0.maxFrameRate }.max() ?? 24
                cap.startCapture(with: dev, format: fmt, fps: Int(min(24, maxFps)))
            }
        }
        sigN = 0
        sigTimer?.invalidate()
        sigTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.sigPoll() }
        offerBonaegi(iceRestart: false)
        geul = "도와주실 분과 잇는 중입니다."
        Girok.shared.namgi("gingeup_tonghwa_sijak", [:])
    }

    /// 가로 1280에 얼마나 가까운가(작을수록 가까움)
    private static func pok(_ f: AVCaptureDevice.Format) -> Int {
        let w = Int(CMVideoFormatDescriptionGetDimensions(f.formatDescription).width)
        return abs(w - 1280)
    }

    private func offerBonaegi(iceRestart: Bool) {
        guard let p = pc else { return }
        var m = ["OfferToReceiveAudio": "true", "OfferToReceiveVideo": "false"]
        if iceRestart { m["IceRestart"] = "true" }
        p.offer(for: RTCMediaConstraints(mandatoryConstraints: m, optionalConstraints: nil)) { [weak self] sdp, _ in
            guard let self = self, let sdp = sdp else { return }
            p.setLocalDescription(sdp) { _ in
                self.sigPut(["t": "offer", "sdp": sdp.sdp])
            }
        }
    }

    private func sigPut(_ m: [String: Any]) {
        guard !room.isEmpty, let d = try? JSONSerialization.data(withJSONObject: m) else { return }
        let r = room
        Task { _ = await Tongsin.shared.post(REL, ["a": "sig_put", "room": r, "to": "helper"], d) }
    }

    private func sigPoll() {
        guard pc != nil, !sigMutneun, !room.isEmpty else { return }
        sigMutneun = true
        let r = room
        let n = sigN
        Task {
            let d = try? await Tongsin.shared.getSae(REL, ["a": "sig_get", "room": r, "to": "caller", "n": String(n)])
            await MainActor.run {
                self.sigMutneun = false
                guard let d = d, r == self.room,
                      let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any], (j["ok"] as? Bool) == true else { return }
                if let cur = Chatgi.su(j["cur"]) { self.sigN = max(self.sigN, Int(cur)) }
                for x in (j["list"] as? [[String: Any]]) ?? [] {
                    if let m = x["msg"] as? [String: Any] { self.sinhoBatda(m) }
                }
            }
        }
    }

    private func sinhoBatda(_ m: [String: Any]) {
        guard let p = pc else { return }
        switch (m["t"] as? String) ?? "" {
        case "answer":
            guard let sdp = m["sdp"] as? String else { return }
            p.setRemoteDescription(RTCSessionDescription(type: .answer, sdp: sdp)) { _ in }
        case "ice":
            guard let c = m["c"] as? [String: Any], let cand = c["candidate"] as? String else { return }
            let idx = Int32(Chatgi.su(c["sdpMLineIndex"]) ?? 0)
            p.add(RTCIceCandidate(sdp: cand, sdpMLineIndex: idx, sdpMid: c["sdpMid"] as? String)) { _ in }
        case "bye":
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 받는 분이 끊으셨으면 방도 닫음(a=hangup) — 10분 동안 방이 남아 있던 일
            hangup()
            kkeut("도와주시던 분이 끊었습니다.")
        default:
            break
        }
    }

    private func iuGeotda() {
        guard sangtae != .tonghwa else { return }
        sangtae = .tonghwa
        geul = "통화 중입니다."
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        UIAccessibility.post(notification: .announcement, argument: "연결되었습니다. 말씀하십시오.")
        // 이어폰이 없으면 스피커로 — 귀에 대지 않아도 들리게
        let s = AVAudioSession.sharedInstance()
        let georeum = s.currentRoute.outputs.contains { $0.portType == .builtInReceiver }
        if georeum { try? s.overrideOutputAudioPort(.speaker) }
        Girok.shared.namgi("gingeup_yeongyeol", [:])
    }

    // MARK: RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        let c: [String: Any] = ["candidate": candidate.sdp, "sdpMid": candidate.sdpMid ?? "0",
                                "sdpMLineIndex": Int(candidate.sdpMLineIndex)]
        DispatchQueue.main.async { self.sigPut(["t": "ice", "c": c]) }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        DispatchQueue.main.async {
            guard self.pc === peerConnection else { return }
            switch newState {
            case .connected, .completed:
                self.iuGeotda()
            case .failed:
                if !self.dasiHan {
                    self.dasiHan = true
                    self.geul = "연결이 막혀 한 번 더 잇는 중입니다."
                    self.offerBonaegi(iceRestart: true)
                } else {
                    self.hangup()
                    self.kkeut("연결하지 못했습니다. 다시 요청해 주십시오.")
                }
            case .disconnected:
                self.geul = "연결이 잠시 끊겼습니다. 다시 잇는 중입니다."
            default:
                break
            }
        }
    }
}
