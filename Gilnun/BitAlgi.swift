// 빛 알아보기(카메라 눈 묶음 7) — 앱 2.22.0 (빌드 260930-9, 대표님 승인 "7번 이어 짓기")
// 불이 켜져 있는지, 창이 어느 쪽인지를 소리 높낮이로 알려 드립니다. 밝을수록 높은 소리가 납니다.
// 폰을 천천히 돌리면 가장 밝은 쪽에서 "이쪽이 가장 밝습니다"라고 알리고, 불이 켜지거나 꺼지면 알립니다.
// 밝기는 카메라가 사진마다 붙여 주는 밝기 값(자동 노출과 상관없는 장면 밝기)으로 셈합니다.
// 폰 안에서만 하며, 사진은 담지도 보내지도 않습니다.
import SwiftUI
import AVFoundation
import ImageIO
import UIKit

final class BitAlgi: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = BitAlgi()

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = ""
    @Published var nopnaji = true   // 높낮이 소리 켜기·끄기

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.bit")
    private var junbiDoem = false
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    // 아래는 메인에서만
    private var girok: [(Date, Double)] = []    // 지난 6초의 밝기
    private var apDangye = ""                    // 앞 장의 밝기 말(두 장 잇달아 같아야 알림)
    private var malDangye = ""
    private var malT = Date.distantPast
    private var gajangT = Date.distantPast
    private var kyeogiT = Date.distantPast       // 불 켜짐·꺼짐 알림
    private var saeT = Date()
    private var jigeum: Double = 0
    private var player: AVAudioPlayer?
    private var sigye: Timer?

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 빛 알아보기를 켜지 못했습니다.") }
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
        Hanmadi.shared.kkeugi(malHagi: false)  // 2.23.0
        TeokAllim.shared.kkeugi(malHagi: false)  // 2.24.0
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
                self.girok = []; self.apDangye = ""; self.malDangye = ""
                self.saeT = Date()
                self.sangtae = "빛을 살피는 중입니다."
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("빛 알아보기를 시작합니다. 밝을수록 높은 소리가 납니다. 폰을 천천히 돌려 보십시오.", sseuGi: true)
                Girok.shared.namgi("bit", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.salpigi() }
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        player?.stop(); player = nil
        guard kyeojim else { return }
        kyeojim = false
        sangtae = ""
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("빛 알아보기를 멈췄습니다.", .jeongbo) }
    }

    func jigeumMal() {
        SoriEngine.shared.mal(kyeojim ? (BitAlgi.dangye(jigeum) + ".") : "빛 알아보기가 꺼져 있습니다.")
    }

    /// 10분 동안 밝기가 바뀌지 않으면 카메라를 끔(배터리)
    private func salpigi() {
        guard kyeojim, Date().timeIntervalSince(saeT) > 600 else { return }
        kkeugi(malHagi: false)
        mal("10분 동안 밝기가 바뀌지 않아 카메라를 껐습니다.", sseuGi: true)
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .medium
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        let op = AVCaptureVideoDataOutput()
        op.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        op.alwaysDiscardsLateVideoFrames = true
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setSampleBufferDelegate(self, queue: jul)
        return true
    }

    // MARK: 한 장마다(jul) — 0.4초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.4 else { return }
        boT = now
        var bv: Double? = nil
        if let att = CMCopyDictionaryOfAttachments(allocator: nil, target: sampleBuffer, attachmentMode: kCMAttachmentMode_ShouldPropagate) as? [String: Any],
           let exif = att[kCGImagePropertyExifDictionary as String] as? [String: Any] {
            bv = (exif[kCGImagePropertyExifBrightnessValue as String] as? NSNumber)?.doubleValue
        }
        // 밝기 값이 없는 폰이면 점들의 평균 밝기로 어림
        if bv == nil, let px = CMSampleBufferGetImageBuffer(sampleBuffer) { bv = BitAlgi.jeomBalgi(px) }
        guard let b = bv else { return }
        DispatchQueue.main.async { self.allida(b) }
    }

    static func jeomBalgi(_ px: CVPixelBuffer) -> Double? {
        guard CVPixelBufferGetPixelFormatType(px) == kCVPixelFormatType_32BGRA else { return nil }
        CVPixelBufferLockBaseAddress(px, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(px, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(px) else { return nil }
        let w = CVPixelBufferGetWidth(px), h = CVPixelBufferGetHeight(px), bpr = CVPixelBufferGetBytesPerRow(px)
        let p = base.assumingMemoryBound(to: UInt8.self)
        var hap = 0.0
        for iy in 0..<16 { for ix in 0..<16 {
            let o = (h * (2 * iy + 1) / 32) * bpr + (w * (2 * ix + 1) / 32) * 4
            hap += (0.114 * Double(p[o]) + 0.587 * Double(p[o + 1]) + 0.299 * Double(p[o + 2])) / 255
        } }
        return hap / 256 * 12 - 4   // 0~1 을 -4~8 으로
    }

    // MARK: 알리기(메인)

    /// 밝기 값(-4 캄캄 ~ 10 햇빛)을 말로
    static func dangye(_ b: Double) -> String {
        if b < -2.5 { return "캄캄합니다" }
        if b < 0.5 { return "어둡습니다" }
        if b < 4 { return "실내 불빛 밝기입니다" }
        if b < 7.5 { return "밝습니다" }
        return "햇빛처럼 아주 밝습니다"
    }

    private func allida(_ b: Double) {
        guard kyeojim else { return }
        let now = Date()
        jigeum = b
        if nopnaji { ttil(b) }
        // 1.5초 전과 견주어 불 켜짐·꺼짐
        let jeon = girok.last(where: { now.timeIntervalSince($0.0) >= 1.2 && now.timeIntervalSince($0.0) <= 2.0 })?.1
        girok.append((now, b))
        girok.removeAll { now.timeIntervalSince($0.0) > 6 }
        let d = BitAlgi.dangye(b)
        sangtae = d + "."
        if let j = jeon, abs(b - j) >= 3, now.timeIntervalSince(kyeogiT) > 4 {
            kyeogiT = now; malT = now; saeT = now
            malDangye = d; apDangye = d
            mal(b > j ? "불이 켜진 듯합니다. \(d)." : "불이 꺼진 듯합니다. \(d).")
            return
        }
        // 폰을 돌리는 동안 가장 밝은 쪽 — 6초 동안 차이가 크고 지금이 가장 밝을 때
        if girok.count >= 8 {
            let bs = girok.map { $0.1 }
            let mx = bs.max() ?? b, mn = bs.min() ?? b
            if mx - mn >= 1.5 && b >= mx - 0.2 && now.timeIntervalSince(gajangT) >= 6 {
                gajangT = now; malT = now; saeT = now
                mal("이쪽이 가장 밝습니다.")
                return
            }
        }
        // 밝기 말은 두 번 잇달아 같고 바뀌었을 때만, 3초에 한 번까지
        defer { apDangye = d }
        guard d == apDangye, d != malDangye, now.timeIntervalSince(malT) >= 3 else { return }
        malT = now; saeT = now
        malDangye = d
        mal(d + ".")
    }

    /// 밝을수록 높은 짧은 소리(220~1760헤르츠)
    private func ttil(_ b: Double) {
        let k = min(max((b + 4) / 14, 0), 1)
        let hz = 220 * pow(2, k * 3)
        player = try? AVAudioPlayer(data: SoriEngine.wav([(hz, 0.06)]))
        player?.volume = 0.6
        player?.play()
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }   // 카메라 눈 말소리 끔 — 높낮이 소리로만
        SoriEngine.shared.mal(t, g)
    }
}

// MARK: 화면 — 둘러보기 탭

struct BitAlgiView: View {
    @ObservedObject private var g = BitAlgi.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 빛을 살피는 중" : "이어 살피기 — 카메라를 켜고 빛 알아보기") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("지금 밝기 듣기") { g.jigeumMal() }
                    .buttonStyle(KeunDanchu())
                Toggle(isOn: $g.nopnaji) { Text("높낮이 소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라가 보는 쪽이 밝을수록 높은 소리가 납니다. 폰을 천천히 돌리시면 가장 밝은 쪽에서 이쪽이 가장 밝습니다라고 알려 드려, 창이나 켜진 불이 어느 쪽인지 찾으실 수 있습니다. 불이 켜지거나 꺼지면 불이 켜진 듯합니다, 불이 꺼진 듯합니다라고 알려 드립니다. 밝기가 바뀌면 캄캄합니다, 어둡습니다, 실내 불빛 밝기입니다, 밝습니다, 햇빛처럼 아주 밝습니다 가운데 하나로 3초에 한 번까지 알려 드립니다. 지금 밝기 듣기를 누르시면 지금 밝기를 다시 들려 드립니다. 높낮이 소리를 끄시면 말로만, 카메라 눈 말소리를 끄시면 높낮이 소리로만 알려 드립니다. 나가기 전에 불을 껐는지, 방에 불이 켜져 있는지 살피실 때 쓰실 수 있습니다. 10분 동안 밝기가 바뀌지 않거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 불 켜져 있어, 빛 알려 줘, 밝은 쪽 찾아 줘라고 하셔도 열립니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("빛 알아보기")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
