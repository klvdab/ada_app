// 지폐와 색깔 알아보기(카메라 눈 묶음 6) — 앱 2.21.0 (빌드 260930-8, 대표님 승인 "6번 이어 짓기")
// 카메라에 댄 것의 가운데 색을 사람들이 늘 쓰는 색 이름으로 알리고(진한 남색, 연한 하늘색, 밝은 빨강),
// 무늬가 있으면 바탕색과 무늬 색을 나누어 알립니다(흰 바탕에 검정 무늬).
// 우리나라 지폐(천 원, 오천 원, 만 원, 오만 원)는 지폐에 적힌 숫자와 글자를 폰 안에서 읽어 알아봅니다.
// 모두 애플이 폰에 열어 둔 기술(Vision)과 카메라 점들의 색 셈으로 폰 안에서만 하며, 사진은 담지도 보내지도 않습니다.
import SwiftUI
import AVFoundation
import Vision
import UIKit

final class JipyeSaek: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = JipyeSaek()

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = ""
    @Published private(set) var majimak = ""   // 방금 알린 것(다시 듣기용)

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.jipyesaek")
    private var junbiDoem = false
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var ingneun = false
    private var beon = 0
    private var apDon = ""                      // 앞 장에서 읽은 지폐(두 장 잇달아 같아야 알림)
    // 아래는 메인에서만
    private var apSaek = ""                     // 앞 장의 색(두 장 잇달아 같아야 알림)
    private var malSaek = ""                    // 마지막으로 말한 색
    private var saekT = Date.distantPast
    private var donT: [String: Date] = [:]      // 같은 지폐 10초 안 되풀이 않음
    private var donMalT = Date.distantPast       // 지폐를 말한 뒤 3초는 색을 말하지 않음
    private var eodumT = Date.distantPast
    private var saeT = Date()
    private var sigye: Timer?

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 지폐와 색깔 알아보기를 켜지 못했습니다.") }
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
        BitAlgi.shared.kkeugi(malHagi: false)  // 2.22.0
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
            self.apDon = ""
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.kyeojim = true
                self.apSaek = ""; self.malSaek = ""
                self.saeT = Date()
                self.sangtae = "살피는 중입니다."
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("지폐와 색깔 알아보기를 시작합니다. 폰 뒤쪽 카메라를 옷이나 물건, 지폐에 한 뼘쯤 떨어뜨려 대 주십시오.", sseuGi: true)
                Girok.shared.namgi("jipyesaek", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.salpigi() }
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        guard kyeojim else { return }
        kyeojim = false
        sangtae = ""
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("지폐와 색깔 알아보기를 멈췄습니다.", .jeongbo) }
    }

    func dasiDeutgi() {
        if !majimak.isEmpty { SoriEngine.shared.mal(majimak) }
        else { SoriEngine.shared.mal(kyeojim ? "살피는 중입니다." : "지폐와 색깔 알아보기가 꺼져 있습니다.") }
    }

    /// 5분 동안 새로 알린 것이 없으면 카메라를 끔(배터리)
    private func salpigi() {
        guard kyeojim, Date().timeIntervalSince(saeT) > 300 else { return }
        kkeugi(malHagi: false)
        mal("5분 동안 새로 알려 드릴 것이 없어 카메라를 껐습니다.", sseuGi: true)
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .high
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        if (try? k.lockForConfiguration()) != nil {
            if k.isFocusModeSupported(.continuousAutoFocus) { k.focusMode = .continuousAutoFocus }
            if k.isAutoFocusRangeRestrictionSupported { k.autoFocusRangeRestriction = .near }
            k.unlockForConfiguration()
        }
        let op = AVCaptureVideoDataOutput()
        op.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]   // 색 셈을 위해
        op.alwaysDiscardsLateVideoFrames = true
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setSampleBufferDelegate(self, queue: jul)
        return true
    }

    // MARK: 한 장마다(jul) — 0.5초에 한 번 색, 1초에 한 번 지폐

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.5, !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        beon += 1
        let saek = JipyeSaek.saekSem(px)
        var don: String? = nil
        if beon % 2 == 0 {
            let (juldeul, _) = GeulIlgi.ilgi(px, .right)
            let d = JipyeSaek.jipyeGalla(juldeul)
            // 두 번 잇달아 같은 지폐로 읽혀야 알림(가격표 숫자 헛읽기 줄이기)
            if let d = d, d == apDon { don = d }
            apDon = d ?? ""
        }
        DispatchQueue.main.async { self.allida(saek, don) }
    }

    // MARK: 알리기(메인)

    private func allida(_ saek: (String, Bool)?, _ don: String?) {
        guard kyeojim else { return }
        let now = Date()
        if let d = don {
            if let t = donT[d], now.timeIntervalSince(t) < 10 { } else {
                donT[d] = now
                donMalT = now
                saeT = now
                SoriEngine.shared.sori(.hwaksin)
                let m = d + " 지폐입니다."
                majimak = m; sangtae = m
                SoriEngine.shared.mal(m, .annae)   // 기다리는 대답이므로 말소리를 꺼도 알림
                Girok.shared.namgi("jipyesaek", ["jipye": d])
                return
            }
        }
        guard let sk = saek else { return }
        let ireum = sk.0, eodum = sk.1
        if eodum {
            apSaek = ""
            if now.timeIntervalSince(eodumT) > 10 {
                eodumT = now
                sangtae = "너무 어둡습니다."
                mal("너무 어둡습니다. 조금 더 밝은 곳에서 대 주십시오.", .jeongbo, sseuGi: true)
            }
            return
        }
        // 두 장 잇달아 같고, 바뀌었을 때만, 3초에 한 번까지. 지폐를 말한 뒤 3초는 쉼
        defer { apSaek = ireum }
        guard ireum == apSaek, ireum != malSaek,
              now.timeIntervalSince(saekT) >= 3, now.timeIntervalSince(donMalT) >= 3 else { return }
        saekT = now
        malSaek = ireum
        saeT = now
        let m = ireum + "."
        majimak = m; sangtae = m
        SoriEngine.shared.mal(m, .annae)
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }
        SoriEngine.shared.mal(t, g)
    }

    // MARK: 지폐 가르기 — 지폐에 적힌 숫자·글자로(오만 원을 만 원보다, 오천 원을 천 원보다 먼저 봄)

    static func jipyeGalla(_ juldeul: [String]) -> String? {
        let t = juldeul.joined(separator: " ")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: ",", with: "")
            .replacingOccurrences(of: ".", with: "")
        if t.contains("50000") || t.contains("오만원") { return "오만 원" }
        if t.contains("10000") || t.contains("만원") { return "만 원" }
        if t.contains("5000") || t.contains("오천원") { return "오천 원" }
        if t.contains("1000") || t.contains("천원") { return "천 원" }
        return nil
    }

    // MARK: 색 셈 — 가운데 점들을 모아 바탕색과 무늬 색을 가름

    /// (색 이름, 너무 어두움)
    static func saekSem(_ px: CVPixelBuffer) -> (String, Bool)? {
        guard CVPixelBufferGetPixelFormatType(px) == kCVPixelFormatType_32BGRA else { return nil }
        CVPixelBufferLockBaseAddress(px, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(px, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(px) else { return nil }
        let w = CVPixelBufferGetWidth(px), h = CVPixelBufferGetHeight(px)
        let bpr = CVPixelBufferGetBytesPerRow(px)
        let p = base.assumingMemoryBound(to: UInt8.self)
        // 가운데 가로·세로 40% 안을 24×24 점으로
        var jeom: [(Double, Double, Double)] = []
        let n = 24
        for iy in 0..<n {
            for ix in 0..<n {
                let x = Int(Double(w) * (0.3 + 0.4 * (Double(ix) + 0.5) / Double(n)))
                let y = Int(Double(h) * (0.3 + 0.4 * (Double(iy) + 0.5) / Double(n)))
                let o = y * bpr + x * 4
                jeom.append((Double(p[o + 2]) / 255, Double(p[o + 1]) / 255, Double(p[o]) / 255))
            }
        }
        let balgi = jeom.reduce(0.0) { $0 + max($1.0, $1.1, $1.2) } / Double(jeom.count)
        if balgi < 0.12 { return ("", true) }
        // 두 무리로 나누기(k-평균 두 개, 여섯 번)
        let sorted = jeom.sorted { ($0.0 + $0.1 + $0.2) < ($1.0 + $1.1 + $1.2) }
        var c1 = sorted[sorted.count / 4], c2 = sorted[sorted.count * 3 / 4]
        var gat = [Int](repeating: 0, count: jeom.count)
        func geori(_ a: (Double, Double, Double), _ b: (Double, Double, Double)) -> Double {
            let d0 = a.0 - b.0, d1 = a.1 - b.1, d2 = a.2 - b.2
            return (d0 * d0 + d1 * d1 + d2 * d2).squareRoot()
        }
        for _ in 0..<6 {
            var s1 = (0.0, 0.0, 0.0), s2 = (0.0, 0.0, 0.0), n1 = 0, n2 = 0
            for (i, q) in jeom.enumerated() {
                if geori(q, c1) <= geori(q, c2) { gat[i] = 0; s1.0 += q.0; s1.1 += q.1; s1.2 += q.2; n1 += 1 }
                else { gat[i] = 1; s2.0 += q.0; s2.1 += q.1; s2.2 += q.2; n2 += 1 }
            }
            if n1 > 0 { c1 = (s1.0 / Double(n1), s1.1 / Double(n1), s1.2 / Double(n1)) }
            if n2 > 0 { c2 = (s2.0 / Double(n2), s2.1 / Double(n2), s2.2 / Double(n2)) }
        }
        let n2 = gat.filter { $0 == 1 }.count, n1 = jeom.count - n2
        let (badak, mu, muBi) = n1 >= n2 ? (c1, c2, Double(n2) / Double(jeom.count)) : (c2, c1, Double(n1) / Double(jeom.count))
        let badakIreum = saekIreum(badak)
        if muBi >= 0.15 && geori(badak, mu) > 0.25 {
            let muIreum = saekIreum(mu)
            if muIreum != badakIreum { return ("\(badakIreum) 바탕에 \(muIreum) 무늬", false) }
        }
        return (badakIreum, false)
    }

    /// 사람들이 늘 쓰는 색 이름
    static func saekIreum(_ c: (Double, Double, Double)) -> String {
        let r = c.0, g = c.1, b = c.2
        let mx = max(r, g, b), mn = min(r, g, b)
        let v = mx, s = mx > 0 ? (mx - mn) / mx : 0
        var hu = 0.0
        if mx != mn {
            if mx == r { hu = 60 * ((g - b) / (mx - mn)) }
            else if mx == g { hu = 60 * ((b - r) / (mx - mn) + 2) }
            else { hu = 60 * ((r - g) / (mx - mn) + 4) }
            if hu < 0 { hu += 360 }
        }
        // 무채색
        if v < 0.18 { return "검정" }
        if s < 0.13 {
            if v > 0.85 { return "흰색" }
            if v > 0.62 { return "연한 회색" }
            if v > 0.38 { return "회색" }
            return "진한 회색"
        }
        var ireum: String
        switch hu {
        case ..<12, 345...: ireum = (s < 0.5 && v > 0.7) ? "분홍" : "빨강"
        case 12..<40: ireum = v < 0.55 ? "갈색" : ((s < 0.45 && v > 0.7) ? "살구색" : "주황")
        case 40..<66: ireum = v < 0.55 ? "황토색" : ((s < 0.35) ? "베이지" : "노랑")
        case 66..<95: ireum = v < 0.5 ? "올리브색" : "연두"
        case 95..<165: ireum = "초록"
        case 165..<195: ireum = "청록"
        case 195..<232: ireum = (s < 0.5 && v > 0.65) ? "하늘색" : "파랑"
        case 232..<258: ireum = v < 0.55 ? "남색" : "파랑"
        case 258..<292: ireum = "보라"
        default: ireum = (s < 0.5 && v > 0.7) ? "분홍" : "자주"
        }
        // 짙고 옅음
        let bunhong = ["분홍", "하늘색", "살구색", "베이지"].contains(ireum)
        if v < 0.4 && !["갈색", "올리브색", "황토색"].contains(ireum) { return "진한 " + ireum }
        if ireum == "갈색" && v < 0.3 { return "진한 갈색" }
        if !bunhong && s < 0.4 && v > 0.75 { return "연한 " + ireum }
        if s > 0.75 && v > 0.85 { return "밝은 " + ireum }
        return ireum
    }
}

// MARK: 화면 — 둘러보기 탭

struct JipyeSaekView: View {
    @ObservedObject private var g = JipyeSaek.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 지폐와 색깔을 살피는 중" : "이어 살피기 — 카메라를 켜고 지폐와 색깔 알아보기") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("방금 것 다시 듣기") { g.dasiDeutgi() }
                    .buttonStyle(KeunDanchu())
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 옷이나 물건에 한 뼘쯤 떨어뜨려 대시면, 가운데 색을 진한 남색, 연한 하늘색, 밝은 빨강처럼 늘 쓰는 색 이름으로 알려 드립니다. 무늬가 있으면 흰색 바탕에 검정 무늬처럼 바탕색과 무늬 색을 나누어 알려 드립니다. 색은 바뀔 때만, 3초에 한 번까지 말씀드립니다. 지폐를 대시면 숫자가 보이는 쪽을 카메라로 향해 주십시오. 천 원, 오천 원, 만 원, 오만 원을 확신음 한 번과 함께 알려 드리고, 같은 지폐는 10초 안에 되풀이하지 않습니다. 너무 어두우면 조금 더 밝은 곳에서 대 주십시오라고 먼저 알려 드립니다. 색은 불빛에 따라 달라 보여 노란 전등 아래에서는 조금 다르게 들릴 수 있습니다. 옷 맞춰 입기, 양말 짝 맞추기, 과일 익은 정도 보기에 쓰실 수 있습니다. 방금 것 다시 듣기를 누르시면 마지막으로 알린 것을 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 무슨 색이야, 지폐 알려 줘, 얼마짜리야라고 하셔도 열립니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("지폐와 색깔 알아보기")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
