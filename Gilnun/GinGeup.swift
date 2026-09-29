// 긴급통화서비스 — 가족·지인(지정한 한 분), 자원봉사자, 현장영상해설사에게 화상통화를 청합니다.
// 웹 길눈 buleugi.html·domum4.html과 같은 길: 나스 /eyec/rel.php 로 부르고(call·jindo·hangup),
// 받으시면 곧장(누르지 않아도) 뒤 카메라와 마이크로 화상통화(WebRTC, 신호는 rel.php sig_put·sig_get, 나스 턴 서버).
// 받는 분 화면(nun4.html·자봉 앱)은 지금 그대로입니다.
// 이사장님 지시: 1분 30초 기다림, 말소리는 요청 시작·받음·연결 실패 때만, 통화 중에는 길눈 말소리를 내지 않음.
import Foundation
import AVFoundation
import UIKit
import WebRTC

enum GinGeupGalrae: String {
    case jiin, haebong, haeseolsa
    var ireum: String {
        switch self {
        case .jiin: return "가족·지인"
        case .haebong: return "자원봉사자"
        case .haeseolsa: return "현장영상해설사"
        }
    }
}

final class GinGeup: NSObject, ObservableObject, RTCPeerConnectionDelegate {
    static let shared = GinGeup()

    enum Sangtae { case eopseum, yocheong, yeongyeol, tonghwa }

    @Published private(set) var sangtae: Sangtae = .eopseum
    @Published private(set) var geul = ""
    @Published var malHan = ""
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
    private var sigTimer: Timer?
    private var sigN = 0
    private var sigMutneun = false
    private var cheot = true
    private var telMal = false
    private var junbiMal = false
    private var dasiHan = false
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
        galrae = g
        saram = s
        cheot = true
        telMal = false
        junbiMal = false
        dasiHan = false
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
        if pc != nil { sigPut(["t": "bye"]) }
        hangup()
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
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { self.tonghwaSijak() }
            // 2.12.0 받으신 뒤 40초 안에 통화가 이어지지 않으면 마침
            DispatchQueue.main.asyncAfter(deadline: .now() + 40) {
                guard self.sangtae == .yeongyeol, self.room == r else { return }
                self.hangup()
                self.kkeut("통화를 잇지 못했습니다. 다시 요청해 주십시오.")
            }
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
                SoriEngine.shared.mal("\(nm) 님은 아직 초대 주소를 열어 받겠습니다를 누르지 않으셔서 신호가 닿지 않을 수 있습니다.")
            }
            if s >= GinGeup.HANDO {
                hangup()
                kkeut("\(nm) 님과 연결되지 못했습니다. 다른 분께 요청하시거나 전화를 거실 수 있습니다.")
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

    private func hangup() {
        let r = room
        guard !r.isEmpty else { return }
        Task { _ = try? await Tongsin.shared.getSae(REL, ["a": "hangup", "room": r]) }
    }

    private func kkeut(_ mal: String) {
        jindoTimer?.invalidate()
        jindoTimer = nil
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
        cfg.iceServers = [
            RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"]),
            RTCIceServer(urlStrings: ["turn:221.146.173.20:3478?transport=udp", "turn:221.146.173.20:3478?transport=tcp"],
                         username: "gilnun", credential: "gilnun-turn-260911-v8k2q")
        ]
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
