// 발 앞 계단·턱 알림(카메라 눈 묶음 9) — 앱 2.24.0 (빌드 261001-1, 대표님 승인 1) — ★관리자 시험 중
// 라이다가 있는 프로 아이폰에서, 발 앞 바닥 높이가 갑자기 꺼지거나 솟는 곳을 "두 걸음 앞, 내려가는 턱, 약 15센티미터"처럼 알립니다.
// 안전 경고라 말소리를 꺼도 늘 알립니다. 오래 시험한 뒤 대표님 승인으로 모든 분께 엽니다(지금은 나스 음악 열쇠가 있는 폰에서만 보임).
// 특허 확인(2026-10-01): 에스알포스트 10-2496113(학습된 모델로 계단을 알아보고, 물체를 세 갈래로 나눠 위험 수준을 매기며,
// 영상 회색조 경계선 수로 계단 단수와 높이를 셈)의 요소를 하나도 쓰지 않음 — 여기서는 라이다 깊이로 바닥 높이의 끊김만 셈하고,
// 계단 단수를 세지 않으며, 물체를 가르지 않고, 영상을 쓰지 않음. 10-2291296(듀얼 카메라 시차·시맨틱 분할·학습 서버·발열 발향 배터리)과도 무관.
// 폰 안에서만 하며, 사진이나 깊이 자료를 담지도 보내지도 않습니다.
import SwiftUI
import ARKit
import AVFoundation
import UIKit

final class TeokAllim: NSObject, ObservableObject, ARSessionDelegate {
    static let shared = TeokAllim()

    /// 라이다 깊이를 쓸 수 있는 폰인가
    static var ganeung: Bool { ARWorldTrackingConfiguration.supportsFrameSemantics(.sceneDepth) }
    /// 관리자 시험 중 — 나스 음악 열쇠가 있는 폰에서만 보임
    static var boim: Bool { !(Yeolsoe.ilgi("eumakTk") ?? "").isEmpty }

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = ""

    private let session = ARSession()
    private let jul = DispatchQueue(label: "gilnun.teok")
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var apGeot = ""          // 앞 장에서 찾은 것(세 장 잇달아 같아야 알림)
    private var gatSu = 0
    // 아래는 메인에서만
    private var malGeot = ""
    private var malT = Date.distantPast
    private var malGeoreum = 99
    private var gyeolT = Date.distantPast
    private var saeT = Date()
    private var sigye: Timer?

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        guard TeokAllim.ganeung else {
            SoriEngine.shared.mal("이 폰에는 거리를 재는 라이다가 없어 발 앞 계단·턱 알림을 쓸 수 없습니다. 프로 모델 아이폰에서 됩니다.")
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 발 앞 계단·턱 알림을 켜지 못했습니다.") }
                }
            }
        default:
            SoriEngine.shared.mal("카메라 허락이 꺼져 있습니다. 아이폰 설정의 길눈에서 카메라를 켜 주십시오.")
        }
    }

    private func sijak() {
        // 카메라는 한 곳만
        AnmyeonEngine.shared.kkeugi(malHagi: false)
        QrEngine.shared.kkeugi(malHagi: false)
        MunChatgi.shared.kkeugi(malHagi: false)
        GeulIlgi.shared.kkeugi(malHagi: false)
        GarikiIlgi.shared.kkeugi(malHagi: false)
        SaramGamji.shared.kkeugi(malHagi: false)
        SangpumIlgi.shared.kkeugi(malHagi: false)
        JipyeSaek.shared.kkeugi(malHagi: false)
        BitAlgi.shared.kkeugi(malHagi: false)
        Hanmadi.shared.kkeugi(malHagi: false)
        session.delegate = self
        session.delegateQueue = jul
        let c = ARWorldTrackingConfiguration()
        c.worldAlignment = .gravity
        c.frameSemantics = [.sceneDepth]
        session.run(c, options: [.resetTracking, .removeExistingAnchors])
        jul.async { self.apGeot = ""; self.gatSu = 0 }
        kyeojim = true
        malGeot = ""; malGeoreum = 99
        saeT = Date(); gyeolT = Date()
        sangtae = "발 앞을 살피는 중입니다."
        UIApplication.shared.isIdleTimerDisabled = true
        SoriEngine.shared.mal("발 앞 계단·턱 알림을 시작합니다. 폰을 가슴 앞에 들고 카메라가 앞쪽 바닥을 보도록 조금 숙여 주십시오.")
        Girok.shared.namgi("teok", ["kyeogi": true])
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.salpigi() }
    }

    func kkeugi(malHagi: Bool = true) {
        sigye?.invalidate(); sigye = nil
        guard kyeojim else { return }
        session.pause()
        kyeojim = false
        sangtae = ""
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("발 앞 계단·턱 알림을 멈췄습니다.", .jeongbo) }
    }

    func jigeumMal() {
        SoriEngine.shared.mal(kyeojim ? (sangtae.isEmpty ? "발 앞을 살피는 중입니다." : sangtae) : "발 앞 계단·턱 알림이 꺼져 있습니다.")
    }

    /// 바닥이 15초 넘게 안 보이면 숙이기를 청하고, 20분 동안 쓰지 않으면 끔
    private func salpigi() {
        guard kyeojim else { return }
        let now = Date()
        if now.timeIntervalSince(saeT) > 1200 {
            kkeugi(malHagi: false)
            SoriEngine.shared.mal("20분이 지나 발 앞 계단·턱 알림을 껐습니다.")
            return
        }
        if now.timeIntervalSince(gyeolT) > 15 {
            gyeolT = now
            SoriEngine.shared.mal("앞쪽 바닥이 잘 보이지 않습니다. 폰을 조금 더 아래로 숙여 주십시오.", .jeongbo)
        }
    }

    // MARK: 한 장마다(jul) — 0.2초에 한 번

    func session(_ s: ARSession, didUpdate frame: ARFrame) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.2 else { return }
        boT = now
        guard case .normal = frame.camera.trackingState, let dp = frame.sceneDepth else { return }
        let p = TeokAllim.bunseok(frame, dp)
        let geot = p.map { "\($0.naeryeo)|\(Int(($0.nopi * 20).rounded()))" } ?? ""
        if geot == apGeot { gatSu += 1 } else { apGeot = geot; gatSu = 1 }
        let hwakjeong = gatSu >= 3   // 세 장(0.6초) 잇달아 같아야
        let badak = p != nil || TeokAllim.badakBoim(frame, dp)
        DispatchQueue.main.async { self.allida(hwakjeong ? p : nil, hwakjeong && p == nil, badak) }
    }

    struct Teok { let georiM: Double; let nopi: Double; let naeryeo: Bool; let keun: Bool }

    /// 발 앞 폭 70센티미터 띠의 바닥 높이를 10센티미터 칸마다 모아, 끊겨 꺼지거나 솟는 첫 곳을 찾음
    static func bunseok(_ frame: ARFrame, _ dp: ARDepthData) -> Teok? {
        guard let pum = jeomdeul(frame, dp) else { return nil }
        // 칸: 앞 0.3미터~3.0미터, 10센티미터씩
        var kan = [[Float]](repeating: [], count: 27)
        for q in pum { let i = Int((q.0 - 0.3) / 0.1); if i >= 0 && i < 27 { kan[i].append(q.1) } }
        let nopi: [Float?] = kan.map { k in
            guard k.count >= 4 else { return nil }
            let s = k.sorted(); return s[s.count / 2]
        }
        // 이어지는 두 칸(사이 빈칸 50센티미터까지)의 높이가 6센티미터 넘게 끊기면 턱
        var ap: (Int, Float)? = nil
        for i in 0..<27 {
            guard let h = nopi[i] else { continue }
            if let a = ap, i - a.0 <= 6 {
                let j = a.0, hj = a.1
                let cha = h - hj
                if abs(cha) >= 0.06 {
                    // 뒤 칸 하나 더로 다짐(잡티 거르기)
                    let dwi = ((i + 1)..<min(27, i + 4)).compactMap { nopi[$0] }.first ?? h
                    if abs(dwi - hj) >= 0.05 && (dwi - hj) * cha > 0 {
                        let geori = 0.3 + Double(j) * 0.1 + 0.05 + Double(i - j) * 0.05
                        let n = Double(abs(cha))
                        return Teok(georiM: geori, nopi: n, naeryeo: cha < 0, keun: n >= 0.4)
                    }
                }
            }
            ap = (i, h)
        }
        return nil
    }

    /// 바닥 쪽 점이 넉넉히 보이는가(숙이기 안내용)
    static func badakBoim(_ frame: ARFrame, _ dp: ARDepthData) -> Bool {
        guard let pum = jeomdeul(frame, dp) else { return false }
        return pum.filter { $0.0 < 2.0 }.count >= 60
    }

    /// 깊이 점들을 세상 좌표로 옮겨, 몸 앞 띠 안의 (앞으로 거리, 높이)만 모음
    static func jeomdeul(_ frame: ARFrame, _ dp: ARDepthData) -> [(Float, Float)]? {
        let dm = dp.depthMap
        CVPixelBufferLockBaseAddress(dm, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(dm, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(dm) else { return nil }
        let w = CVPixelBufferGetWidth(dm), h = CVPixelBufferGetHeight(dm), bpr = CVPixelBufferGetBytesPerRow(dm)
        var sinroe: UnsafeMutableRawPointer? = nil
        var sbpr = 0
        if let cm = dp.confidenceMap {
            CVPixelBufferLockBaseAddress(cm, .readOnly)
            sinroe = CVPixelBufferGetBaseAddress(cm); sbpr = CVPixelBufferGetBytesPerRow(cm)
        }
        defer { if let cm = dp.confidenceMap { CVPixelBufferUnlockBaseAddress(cm, .readOnly) } }
        let img = frame.camera.imageResolution
        let sx = Float(w) / Float(img.width), sy = Float(h) / Float(img.height)
        let K = frame.camera.intrinsics
        let fx = K[0][0] * sx, fy = K[1][1] * sy, cx = K[2][0] * sx, cy = K[2][1] * sy
        let T = frame.camera.transform
        let kam = SIMD3<Float>(T.columns.3.x, T.columns.3.y, T.columns.3.z)
        // 카메라가 보는 쪽(-z)을 수평으로 눕힌 앞 방향과 오른쪽
        var ap = SIMD3<Float>(-T.columns.2.x, 0, -T.columns.2.z)
        let gil = (ap.x * ap.x + ap.z * ap.z).squareRoot()
        guard gil > 0.2 else { return nil }   // 폰이 너무 바닥만 보거나 하늘만 보면 쉼
        ap /= gil
        let oreun = SIMD3<Float>(-ap.z, 0, ap.x)
        var pum: [(Float, Float)] = []
        pum.reserveCapacity(1200)
        var v = 2
        while v < h {
            var u = 2
            while u < w {
                if let sp = sinroe, sp.load(fromByteOffset: v * sbpr + u, as: UInt8.self) < 1 { u += 6; continue }   // 믿음이 낮은 점은 뺌
                let d = base.load(fromByteOffset: v * bpr + u * 4, as: Float32.self)
                if d > 0.2 && d < 4.0 {
                    let xc = (Float(u) - cx) * d / fx, yc = (Float(v) - cy) * d / fy
                    let pc = SIMD4<Float>(xc, -yc, -d, 1)
                    let pw = T * pc
                    let rel = SIMD3<Float>(pw.x, pw.y, pw.z) - kam
                    let apGeori = rel.x * ap.x + rel.z * ap.z
                    let yeop = rel.x * oreun.x + rel.z * oreun.z
                    if abs(yeop) < 0.35 && apGeori > 0.3 && apGeori < 3.0 { pum.append((apGeori, pw.y)) }
                }
                u += 6
            }
            v += 6
        }
        return pum
    }

    // MARK: 알리기(메인) — 안전 경고라 말소리를 꺼도 알림

    private func allida(_ t: Teok?, _ eopseum: Bool, _ badak: Bool) {
        guard kyeojim else { return }
        let now = Date()
        if badak { gyeolT = now }
        guard let t = t else {
            if eopseum && !malGeot.isEmpty { malGeot = ""; malGeoreum = 99; sangtae = "발 앞이 고릅니다." }
            return
        }
        saeT = now
        let bopok = Seoljeong.shared.bopok > 0.2 ? Seoljeong.shared.bopok : 0.65
        let georeum = max(1, Int((t.georiM / bopok).rounded()))
        let cm = Int((t.nopi * 100 / 5).rounded()) * 5
        let jong: String
        if t.keun { jong = t.naeryeo ? "크게 꺼진 곳" : "앞을 막는 높은 것" }
        else { jong = t.naeryeo ? "내려가는 턱" : "올라가는 턱" }
        let ap = t.georiM < 0.55 ? "바로 발 앞" : "\(GeoreumSu.su(georeum)) 걸음 앞"
        let m = t.keun ? "\(ap), \(jong)" : "\(ap), \(jong), 약 \(cm)센티미터"
        let geot = "\(jong)|\(cm)"
        sangtae = m + "."
        // 새 것이거나, 같은 것이라도 한 걸음 넘게 가까워졌고 2초 지났을 때만
        let sae = geot != malGeot
        let gakka = georeum < malGeoreum && now.timeIntervalSince(malT) >= 2
        guard sae || gakka else { return }
        if !sae && now.timeIntervalSince(malT) < 2 { return }
        malGeot = geot; malGeoreum = georeum; malT = now
        SoriEngine.shared.sori(.gyeonggo)
        SoriEngine.shared.mal(m + ".", .gyeonggo)
        Girok.shared.namgi("teok", ["jong": jong, "cm": cm, "georeum": georeum])
    }
}

/// 걸음 수를 우리말 수로(한 걸음, 두 걸음 …)
enum GeoreumSu {
    static func su(_ n: Int) -> String {
        let ir = ["", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열"]
        return n >= 1 && n <= 10 ? ir[n] : "\(n)"
    }
}

// MARK: 화면 — 설정 탭(관리자 시험 중)

struct TeokAllimView: View {
    @ObservedObject private var g = TeokAllim.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 발 앞을 살피는 중" : "이어 살피기 — 발 앞 계단·턱 알림 켜기") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("지금 발 앞 다시 듣기") { g.jigeumMal() }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("관리자 시험 중인 기능입니다. 라이다가 있는 프로 모델 아이폰에서만 됩니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 들고 카메라가 앞쪽 바닥을 보도록 조금 숙여 걸으시면, 발 앞 바닥이 갑자기 꺼지거나 솟는 곳을 두 걸음 앞, 내려가는 턱, 약 15센티미터처럼 경고음과 함께 알려 드립니다. 40센티미터가 넘으면 크게 꺼진 곳, 앞을 막는 높은 것으로 알려 드립니다. 같은 턱은 한 걸음 넘게 가까워질 때만 다시 알립니다. 안전 경고라 말소리를 꺼도 늘 알립니다. 계단이 몇 단인지는 세지 않습니다. 앞쪽 바닥이 15초 넘게 안 보이면 폰을 더 숙여 달라고 말씀드립니다. 20분이 지나거나 화면을 떠나시면 끕니다. 지팡이를 대신하지 않으며 지팡이와 함께 쓰십시오. 폰 안에서만 살피며 사진이나 깊이 자료를 담지도 보내지도 않습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("발 앞 계단·턱 알림")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
