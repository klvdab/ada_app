// 긴급통화 통화 — 길눈님(받는 쪽) 화상통화 (자봉 앱 2.1.0, 빌드 261001-9)
// 길손님 길눈 앱이 뒤 카메라 영상과 말소리를 보내고(offer), 길눈님은 말소리로 답함(answer).
// 신호는 나스 rel.php sig_put·sig_get, 길은 나스 턴 서버 — 길눈 앱(GinGeup)과 짝.
// 통화 중에는 길눈 말소리를 내지 않음(대표님 지시). 15초 넘는 통화 끝에 고맙다는 말.
import Foundation
import SwiftUI
import AVFoundation
import WebRTC

final class JabongTonghwa: NSObject, ObservableObject, RTCPeerConnectionDelegate {
    static let shared = JabongTonghwa()
    private let REL = "/eyec/rel.php"

    @Published private(set) var tonghwaJung = false
    @Published private(set) var iEojim = false
    @Published private(set) var geul = ""
    @Published private(set) var mok = ""
    @Published var boim = false                 // 통화 화면 띄우기
    @Published private(set) var yeongsang: RTCVideoTrack?

    private var room = ""
    private var uuid: UUID?
    private var pc: RTCPeerConnection?
    private var sigN = 0
    private var sigMutneun = false
    private var sigTimer: Timer?
    private var sijakT = Date()

    private static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory(encoderFactory: RTCDefaultVideoEncoderFactory(),
                                        decoderFactory: RTCDefaultVideoDecoderFactory())
    }()

    func sijak(room: String, mok: String, uuid: UUID) {
        kkeunki(bonaegi: false)
        self.room = room
        self.mok = mok
        self.uuid = uuid
        tonghwaJung = true
        iEojim = false
        boim = true
        sijakT = Date()
        geul = "길손님과 잇는 중입니다."
        SoriEngine.shared.modu_geodugi()
        SoriEngine.shared.tonghwaJung = true
        RTCAudioSession.sharedInstance().useManualAudio = true
        let cfg = RTCConfiguration()
        cfg.iceServers = IceJuso.servers   // 자봉 2.5.0 나스 설정 쪽지에서(길눈 GinGeup.swift 의 IceJuso)
        cfg.sdpSemantics = .unifiedPlan
        cfg.continualGatheringPolicy = .gatherContinually
        let c = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: ["DtlsSrtpKeyAgreement": "true"])
        guard let p = JabongTonghwa.factory.peerConnection(with: cfg, constraints: c, delegate: self) else {
            geul = "통화를 열지 못했습니다."
            return
        }
        pc = p
        let f = JabongTonghwa.factory
        let aSrc = f.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
        p.add(f.audioTrack(with: aSrc, trackId: "a1"), streamIds: ["s1"])
        sigN = 0
        sigTimer?.invalidate()
        sigTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.sigPoll() }
    }

    /// CallKit 이 소리 자리를 열어 주면 그때 말소리를 켬
    func sorijariYeollim(_ s: AVAudioSession) {
        RTCAudioSession.sharedInstance().audioSessionDidActivate(s)
        RTCAudioSession.sharedInstance().isAudioEnabled = true
        if s.currentRoute.outputs.contains(where: { $0.portType == .builtInReceiver }) { try? s.overrideOutputAudioPort(.speaker) }
    }

    func sorijariDatim(_ s: AVAudioSession) {
        RTCAudioSession.sharedInstance().audioSessionDidDeactivate(s)
        RTCAudioSession.sharedInstance().isAudioEnabled = false
    }

    /// 통화 끊기(길눈님이 누름) — 전화 화면도 닫음
    func kkeutnaegi() {
        if let u = uuid { JabongDaegi.shared.tonghwaKkeut(u) } else { kkeunki(bonaegi: true) }
    }

    /// 통화를 정리. bonaegi 면 길손님 쪽에 끊었다고 알림
    func kkeunki(bonaegi: Bool) {
        guard tonghwaJung else { return }
        if bonaegi { sigPut(["t": "bye"]) }
        let gil = Date().timeIntervalSince(sijakT)
        let eojeotna = iEojim
        sigTimer?.invalidate(); sigTimer = nil
        pc?.close(); pc = nil
        yeongsang = nil
        tonghwaJung = false
        iEojim = false
        boim = false
        SoriEngine.shared.tonghwaJung = false
        RTCAudioSession.sharedInstance().isAudioEnabled = false
        Girok.shared.namgi("jabong_tonghwa_kkeut", ["chou": Int(gil), "ieojim": eojeotna])
        if eojeotna && gil > 15 {
            let b = JabongDaegi.shared.byeol
            SoriEngine.shared.mal("고맙습니다. \(b.isEmpty ? "" : b + " 님, ")오늘 덕분에 한 분이 길을 찾았습니다.")
        }
        room = ""
        uuid = nil
    }

    // MARK: 신호

    private func sigPut(_ m: [String: Any]) {
        guard !room.isEmpty, let d = try? JSONSerialization.data(withJSONObject: m) else { return }
        let r = room
        Task { _ = await Tongsin.shared.post(REL, ["a": "sig_put", "room": r, "to": "caller"], d) }
    }

    private func sigPoll() {
        guard pc != nil, !sigMutneun, !room.isEmpty else { return }
        sigMutneun = true
        let r = room
        let n = sigN
        Task {
            let d = try? await Tongsin.shared.getSae(REL, ["a": "sig_get", "room": r, "to": "helper", "n": String(n)])
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
        case "offer":
            guard let sdp = m["sdp"] as? String else { return }
            p.setRemoteDescription(RTCSessionDescription(type: .offer, sdp: sdp)) { [weak self] e in
                guard e == nil else { return }
                p.answer(for: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)) { a, _ in
                    guard let a = a else { return }
                    p.setLocalDescription(a) { _ in
                        DispatchQueue.main.async { self?.sigPut(["t": "answer", "sdp": a.sdp]) }
                    }
                }
            }
        case "ice":
            guard let c = m["c"] as? [String: Any], let cand = c["candidate"] as? String else { return }
            let idx = Int32(Chatgi.su(c["sdpMLineIndex"]) ?? 0)
            p.add(RTCIceCandidate(sdp: cand, sdpMLineIndex: idx, sdpMid: c["sdpMid"] as? String)) { _ in }
        case "bye":
            geul = "길손님이 통화를 마쳤습니다."
            kkeutnaegi()
        default:
            break
        }
    }

    // MARK: RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        DispatchQueue.main.async { if let v = stream.videoTracks.first { self.yeongsang = v } }
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        DispatchQueue.main.async { if let v = rtpReceiver.track as? RTCVideoTrack { self.yeongsang = v } }
    }

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
                if !self.iEojim {
                    self.iEojim = true
                    self.geul = "길손님과 이어졌습니다."
                    UIAccessibility.post(notification: .announcement, argument: "길손님과 이어졌습니다. 말씀하십시오.")
                }
            case .disconnected:
                self.geul = "연결이 잠시 끊겼습니다. 다시 잇는 중입니다."
            case .failed:
                self.geul = "연결하지 못했습니다."
                self.kkeutnaegi()
            default:
                break
            }
        }
    }
}

// MARK: 통화 화면

struct YeongsangView: UIViewRepresentable {
    let track: RTCVideoTrack?
    func makeUIView(context: Context) -> RTCMTLVideoView {
        let v = RTCMTLVideoView()
        v.videoContentMode = .scaleAspectFit
        return v
    }
    func updateUIView(_ v: RTCMTLVideoView, context: Context) {
        if context.coordinator.track !== track {
            context.coordinator.track?.remove(v)
            track?.add(v)
            context.coordinator.track = track
        }
    }
    func makeCoordinator() -> Coord { Coord() }
    final class Coord { var track: RTCVideoTrack? }
}

struct TonghwaView: View {
    @ObservedObject private var t = JabongTonghwa.shared
    @AccessibilityFocusState private var cheot: Bool
    var body: some View {
        VStack(spacing: 12) {
            Text(t.geul).font(.title3).accessibilityFocused($cheot)
            if !t.mok.isEmpty { Text("길손님의 목적지 — \(t.mok)").font(.body) }
            YeongsangView(track: t.yeongsang)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .accessibilityLabel("길손님 카메라 화면")
            Button("통화 마치기") { t.kkeutnaegi() }.buttonStyle(KeunDanchu())
        }
        .padding()
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheot = true } }
    }
}
