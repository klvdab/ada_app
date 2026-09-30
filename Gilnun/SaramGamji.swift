// 사람 감지(카메라 눈 묶음 4) — 앱 2.18.0 (빌드 260930-4, 이사장님 승인 "만들어야 해")
// 걸을 때 앞에 사람이 있으면 "1시 방향 사람, 3걸음"처럼 알리고, 가까워질수록 확신음이 빨라집니다.
// 애플이 폰에 열어 둔 사람 알아보기(Vision)만 쓰고, 거리는 카메라 한 대로 사람 키(1.7미터)를 기준 삼아 어림합니다.
// 얼굴을 알아보거나 사진을 담지 않고, 인터넷 없이 폰 안에서만 돕니다.
// 특허 확인(2026-09-30): 애플 EP4189528(카메라 화면 위에 거리 표시)은 우리나라에 없고 우리는 화면을 띄우지 않음.
// 삼성생명공익재단 10-2291179(보정 영상을 화면에 띄움)·아이신스 10-2615844(카메라 두 대로 거리)와 겹치지 않게
// 화면 영상 없이, 카메라 한 대로만.
import SwiftUI
import AVFoundation
import Vision
import UIKit

final class SaramGamji: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = SaramGamji()

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = ""

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.saram")
    private var junbiDoem = false
    private var sijya: Double = 63     // 카메라 가로(누운 쪽) 시야 도
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var ingneun = false
    // 아래는 메인에서만
    private var malT = Date.distantPast
    private var majimakMal = ""
    private var boim = false
    private var eopT = Date()
    private var eopMal = true
    private var saeT = Date()
    private var georiNow: Double = 99
    private var sigye: Timer?
    private var ttak: Timer?
    private var ttakGan: Double = 0

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 사람 감지를 켜지 못했습니다.") }
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
        SangpumIlgi.shared.kkeugi(malHagi: false)  // 2.20.0
        jul.async {
            if !self.junbiDoem {
                guard self.junbi() else {
                    DispatchQueue.main.async { SoriEngine.shared.mal("카메라를 열지 못했습니다.") }
                    return
                }
                self.junbiDoem = true
            }
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.kyeojim = true
                self.boim = false; self.eopMal = true; self.majimakMal = ""; self.georiNow = 99
                self.saeT = Date()
                self.sangtae = "사람을 살피는 중입니다."
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("사람 감지를 시작합니다. 폰을 가슴 앞에 세워 들고 걸으십시오.", sseuGi: true)
                Girok.shared.namgi("saram", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.salpigi() }
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        ttakMeomchum()
        guard kyeojim else { return }
        kyeojim = false
        sangtae = ""
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("사람 감지를 멈췄습니다.", .jeongbo) }
    }

    func jigeumMal() {
        SoriEngine.shared.mal(kyeojim ? (sangtae.isEmpty ? "사람을 살피는 중입니다." : sangtae) : "사람 감지가 꺼져 있습니다.")
    }

    /// 10분 동안 사람을 한 번도 못 보면 카메라를 끔(배터리)
    private func salpigi() {
        guard kyeojim, Date().timeIntervalSince(saeT) > 600 else { return }
        kkeugi(malHagi: false)
        mal("10분 동안 사람이 보이지 않아 카메라를 껐습니다.", sseuGi: true)
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .high
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        sijya = Double(k.activeFormat.videoFieldOfView)
        let op = AVCaptureVideoDataOutput()
        op.alwaysDiscardsLateVideoFrames = true
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setSampleBufferDelegate(self, queue: jul)
        return true
    }

    // MARK: 한 장마다(jul) — 0.4초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.4, !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        let r = VNDetectHumanRectanglesRequest()
        r.upperBodyOnly = false
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: .right, options: [:])
        try? h.perform([r])
        // 세운 폰 기준 시야 — 세로 시야는 카메라 가로 시야, 가로 시야는 비율로 좁힘
        let bw = Double(CVPixelBufferGetWidth(px)), bh = Double(CVPixelBufferGetHeight(px))
        let seroRad = sijya * .pi / 180
        let garoRad = 2 * atan(tan(seroRad / 2) * (bh / max(1, bw)))
        var saram: [(Double, Double)] = []   // (거리 미터, 몸 기준 도 — 오른쪽 +)
        for o in r.results ?? [] where o.confidence > 0.5 {
            let b = o.boundingBox
            let nopi = Double(b.height)
            guard nopi > 0.05 else { continue }
            var d = 1.7 / (2 * tan(seroRad / 2) * nopi)
            if b.minY < 0.02 || b.maxY > 0.98 { d = min(d, 1.2) }   // 몸이 화면을 넘치면 아주 가까움
            let gak = (Double(b.midX) - 0.5) * garoRad * 180 / .pi
            saram.append((d, gak))
        }
        saram.sort { $0.0 < $1.0 }
        DispatchQueue.main.async { self.allida(saram) }
    }

    // MARK: 알리기(메인)

    private func allida(_ saram: [(Double, Double)]) {
        guard kyeojim else { return }
        let now = Date()
        guard let ga = saram.first, ga.0 <= 8 else {
            if boim && now.timeIntervalSince(eopT) > 2.5 {
                boim = false
                ttakMeomchum()
                georiNow = 99
                sangtae = "앞에 사람이 없습니다."
                if !eopMal { eopMal = true; mal("앞에 사람이 없습니다.", .jeongbo) }
            }
            return
        }
        eopT = now
        saeT = now
        let bopok = Seoljeong.shared.bopok > 0.2 ? Seoljeong.shared.bopok : 0.65
        let georeum = max(1, Int((ga.0 / bopok).rounded()))
        let si = MunChatgi.sigye(ga.1)
        let su = saram.filter { $0.0 <= 8 }.count
        let m = (su > 1 ? "앞에 사람 \(su)명, 가장 가까운 사람 " : "") + "\(si)시 방향 사람, \(georeum)걸음"
        sangtae = m
        georiNow = ga.0
        ttakMatchum(ga.0)
        let cheoeum = !boim
        boim = true
        eopMal = false
        if cheoeum || (now.timeIntervalSince(malT) >= 3 && m != majimakMal) {
            malT = now
            majimakMal = m
            mal(m + ".")
        }
    }

    /// 가까워질수록 빨라지는 확신음 — 1.5미터 안 0.5초, 3미터 안 1초, 그 밖엔 울리지 않음
    private func ttakMatchum(_ d: Double) {
        let gan: Double = d < 1.5 ? 0.5 : (d < 3 ? 1.0 : 0)
        guard gan != ttakGan else { return }
        ttakGan = gan
        ttak?.invalidate(); ttak = nil
        guard gan > 0 else { return }
        ttak = Timer.scheduledTimer(withTimeInterval: gan, repeats: true) { _ in SoriEngine.shared.sori(.hwaksin) }
    }

    private func ttakMeomchum() {
        ttak?.invalidate(); ttak = nil; ttakGan = 0
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }   // 카메라 눈 말소리 끔 — 확신음으로만
        SoriEngine.shared.mal(t, g)
    }
}

// MARK: 화면 — 둘러보기 탭

struct SaramGamjiView: View {
    @ObservedObject private var g = SaramGamji.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 사람을 살피는 중" : "이어 살피기 — 카메라를 켜고 사람 감지") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("지금 앞 다시 듣기") { g.jigeumMal() }
                    .buttonStyle(KeunDanchu())
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("걸으실 때 앞에 사람이 있으면 몇 시 방향, 몇 걸음인지 알려 드립니다. 여러 사람이면 몇 명인지와 가장 가까운 사람을 알려 드립니다. 3미터 안으로 가까워지면 확신음이 1초마다, 1.5미터 안이면 0.5초마다 울립니다. 말은 3초에 한 번쯤 바뀔 때만 합니다. 사람이 사라지면 앞에 사람이 없습니다라고 한 번 알립니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 세워 들거나 목걸이로 걸고 걸으십시오. 거리는 사람 키를 기준으로 어림한 것이라 앉은 사람이나 아이는 실제보다 멀게 들릴 수 있습니다. 카메라 눈 말소리를 끄시면 말 없이 확신음으로만 알립니다. 10분 동안 사람이 보이지 않거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 사람 있어라고 하셔도 열립니다. 얼굴을 알아보거나 사진을 담지 않으며, 인터넷 없이 폰 안에서만 돕니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("사람 감지")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
