// 즉석 글자 읽기(카메라 눈 묶음 2) — 앱 2.16.0 (빌드 260930-2, 이사장님 승인 1)
// 사진을 찍지 않아도 카메라를 대고 있으면 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다.
// 애플이 폰에 열어 둔 글자 알아보기(Vision)만 쓰며, 사진과 글은 어디로도 보내지 않습니다(인터넷 없이 폰 안에서만).
// 두 번 잇달아 보인 줄만 읽어 헛글을 줄이고, 한 번 읽은 줄은 30초 안에 되풀이하지 않습니다.
// 글자가 화면 끝에 걸려 잘리면 어느 쪽으로 옮기실지 알려 드립니다(3초에 한 번까지).
// 모든 폰에서 씀(이사장님 지시 — 음악 열쇠와 묶지 않음). 뒤에 차 안 간판 알림이 이 엔진을 다시 씁니다.
import SwiftUI
import AVFoundation
import Vision
import UIKit

struct GeulJul: Identifiable, Hashable {
    let id: Int
    let mal: String
}

final class GeulIlgi: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = GeulIlgi()

    @Published private(set) var kyeojim = false
    @Published private(set) var ilgeun: [GeulJul] = []    // 읽은 줄(새것이 위)
    @Published private(set) var majimak = ""

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.geulilgi")
    private var junbiDoem = false
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var ingneun = false
    private var apJul = Set<String>()          // 바로 앞 장에서 본 줄
    // 아래는 메인에서만
    private var ilgeunT: [String: Date] = [:]  // 줄 → 읽은 때
    private var gyeonuT = Date.distantPast
    private var saeT = Date()
    private var beon = 0
    private var sigye: Timer?

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 글자 읽기를 켜지 못했습니다.") }
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
        GarikiIlgi.shared.kkeugi(malHagi: false)   // 2.17.0
        SaramGamji.shared.kkeugi(malHagi: false)   // 2.18.0
        SangpumIlgi.shared.kkeugi(malHagi: false)  // 2.20.0
        JipyeSaek.shared.kkeugi(malHagi: false)  // 2.21.0
        jul.async {
            if !self.junbiDoem {
                guard self.junbi() else {
                    DispatchQueue.main.async { SoriEngine.shared.mal("카메라를 열지 못했습니다.") }
                    return
                }
                self.junbiDoem = true
            }
            self.apJul = []
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.kyeojim = true
                self.saeT = Date()
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("글자 읽기를 시작합니다. 폰을 글자 쪽으로 세워 들고 천천히 움직여 주십시오.", sseuGi: true)
                Girok.shared.namgi("geulilgi", ["kyeogi": true])
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
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("글자 읽기를 멈췄습니다.", .jeongbo) }
    }

    func dasiDeutgi() {
        SoriEngine.shared.mal(majimak.isEmpty ? "아직 읽은 글자가 없습니다." : majimak)
    }

    /// 3분 동안 새 글이 없으면 카메라를 끔
    private func salpigi() {
        guard kyeojim, Date().timeIntervalSince(saeT) > 180 else { return }
        kkeugi(malHagi: false)
        mal("3분 동안 새 글자가 없어 카메라를 껐습니다. 다시 읽으시려면 이어 읽기를 누르십시오.", sseuGi: true)
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .high
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        let op = AVCaptureVideoDataOutput()
        op.alwaysDiscardsLateVideoFrames = true
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setSampleBufferDelegate(self, queue: jul)
        return true
    }

    // MARK: 한 장마다(jul) — 1초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 1, !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        let (juldeul, jallim) = GeulIlgi.ilgi(px, .right)
        // 두 장 잇달아 보인 줄만(헛글 줄이기)
        let keys = juldeul.map { GeulIlgi.ttuk($0) }
        let hwakjeong = zip(juldeul, keys).filter { apJul.contains($0.1) }.map { $0.0 }
        apJul = Set(keys)
        DispatchQueue.main.async { self.boda(hwakjeong, jallim) }
    }

    // MARK: 알리기(메인)

    private func boda(_ juldeul: [String], _ jallim: String?) {
        guard kyeojim else { return }
        let now = Date()
        var sae: [String] = []
        for j in juldeul {
            let k = GeulIlgi.ttuk(j)
            if let t = ilgeunT[k], now.timeIntervalSince(t) < 30 { ilgeunT[k] = now; continue }
            ilgeunT[k] = now
            sae.append(j)
            if sae.count >= 8 { break }
        }
        if ilgeunT.count > 400 { ilgeunT = ilgeunT.filter { now.timeIntervalSince($0.value) < 60 } }
        if !sae.isEmpty {
            saeT = now
            let m = sae.joined(separator: ". ")
            majimak = m
            for s in sae { beon += 1; ilgeun.insert(GeulJul(id: beon, mal: s), at: 0) }
            if ilgeun.count > 100 { ilgeun = Array(ilgeun.prefix(100)) }
            mal(m)
            return
        }
        if let j = jallim, now.timeIntervalSince(gyeonuT) >= 3 {
            gyeonuT = now
            mal("글자가 \(j)으로 잘렸습니다. 폰을 조금 \(j)으로 옮기십시오.", .jeongbo)
        }
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }   // 카메라 눈 말소리 끔 — 화면 글자로만
        SoriEngine.shared.mal(t, g)
    }

    // MARK: 글자 알아보기(폰 안에서만) — 차 안 간판 알림도 씀

    /// 위에서 아래, 왼쪽에서 오른쪽 차례의 줄과, 화면 끝에 걸린 글이 있으면 그쪽(왼쪽·오른쪽·위쪽·아래쪽)
    static func ilgi(_ px: CVPixelBuffer, _ bang: CGImagePropertyOrientation) -> ([String], String?) {
        let r = VNRecognizeTextRequest()
        r.recognitionLevel = .accurate
        r.recognitionLanguages = ["ko-KR", "en-US"]
        r.usesLanguageCorrection = true
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: bang, options: [:])
        try? h.perform([r])
        var hang: [(String, CGRect)] = []
        for x in r.results ?? [] {
            guard let c = x.topCandidates(1).first, c.confidence >= 0.4 else { continue }
            let t = c.string.trimmingCharacters(in: .whitespacesAndNewlines)
            guard t.count >= 2 else { continue }
            hang.append((t, x.boundingBox))
        }
        // 위(1 - maxY)부터, 같은 높이면 왼쪽부터
        hang.sort { a, b in
            let ya = 1 - a.1.maxY, yb = 1 - b.1.maxY
            if abs(ya - yb) > 0.03 { return ya < yb }
            return a.1.minX < b.1.minX
        }
        var jallim: String?
        for (_, b) in hang {
            if b.minX < 0.015 { jallim = "왼쪽"; break }
            if b.maxX > 0.985 { jallim = "오른쪽"; break }
            if b.maxY > 0.985 { jallim = "위쪽"; break }
            if b.minY < 0.015 { jallim = "아래쪽"; break }
        }
        return (hang.map { $0.0 }, jallim)
    }

    static func ttuk(_ s: String) -> String { s.replacingOccurrences(of: " ", with: "").uppercased() }
}

// MARK: 화면 — 둘러보기 탭

struct GeulIlgiView: View {
    @ObservedObject private var g = GeulIlgi.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 글자를 읽는 중" : "이어 읽기 — 카메라를 켜고 글자 읽기") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("방금 읽은 것 다시 듣기") { g.dasiDeutgi() }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("읽은 글 펼치기 — \(g.ilgeun.count)줄") {
                    Mokrok5(g.ilgeun) { j in Text(j.mal).font(.title3) }
                }
                .font(.title3)
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("사진을 찍지 않아도 카메라를 대고 있으면 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다. 화면을 여시면 바로 읽기 시작합니다. 폰을 글자 쪽으로 세워 들고 천천히 움직이십시오. 한 번 읽은 줄은 30초 안에 되풀이하지 않고 새로 보인 글만 읽습니다. 글자가 화면 끝에 걸려 잘리면 폰을 어느 쪽으로 옮기실지 알려 드립니다. 읽은 글은 읽은 글 펼치기에 다섯 줄씩 남습니다. 카메라 눈 말소리를 끄시면 말 없이 화면 글자로만 남습니다. 3분 동안 새 글이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 글자 읽어 줘라고 하셔도 열립니다. 사진과 글은 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("즉석 글자 읽기")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
