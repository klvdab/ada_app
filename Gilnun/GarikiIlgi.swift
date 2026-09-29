// 가리키고 말하기(카메라 눈 묶음 3) — 앱 2.17.0 (빌드 260930-3, 이사장님 승인 1)
// 전자레인지·세탁기·엘리베이터·키오스크처럼 단추가 많은 곳에서, 검지로 가리킨 곳의 글자만 골라 읽어 드립니다.
// 애플이 폰에 열어 둔 손 모양 알아보기와 글자 알아보기(Vision)만 쓰며, 라이다가 없는 폰에서도 됩니다.
// 특허 확인(2026-09-30): 오캠 우리나라 특허는 모두 죽은 권리. 삼성 10-2157313(착용 기기가 가리킨 것을 서버로 보내 받은 정보를 띄움)과
// 겹치지 않게 손에 들고 쓰고 서버로 보내지 않으며 폰 안에서 읽어 말로만 알림. 삼성 10-2642668("이거" 같은 말을 사진으로 풀기)은 쓰지 않음.
// 모든 폰에서 씀.
import SwiftUI
import AVFoundation
import Vision
import UIKit

final class GarikiIlgi: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = GarikiIlgi()

    @Published private(set) var kyeojim = false
    @Published private(set) var majimak = ""
    @Published private(set) var ilgeun: [GeulJul] = []

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.gariki")
    private var junbiDoem = false
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var ingneun = false
    private var apGeul = ""            // 바로 앞 장에서 가리킨 글(두 번 잇달아 같아야 읽음)
    // 아래는 메인에서만
    private var malHanGeul = ""        // 마지막으로 읽은 글
    private var sonBoim = false
    private var sonEopT = Date()
    private var sonEopMalSu = 0
    private var sonEopMalT = Date.distantPast
    private var geulEopMal = false
    private var sonT = Date.distantPast
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
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 가리키고 말하기를 켜지 못했습니다.") }
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
        SaramGamji.shared.kkeugi(malHagi: false)   // 2.18.0
        jul.async {
            if !self.junbiDoem {
                guard self.junbi() else {
                    DispatchQueue.main.async { SoriEngine.shared.mal("카메라를 열지 못했습니다.") }
                    return
                }
                self.junbiDoem = true
            }
            self.apGeul = ""
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.kyeojim = true
                self.malHanGeul = ""; self.sonBoim = false; self.sonEopT = Date(); self.sonEopMalSu = 0; self.geulEopMal = false
                self.saeT = Date()
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("가리키고 말하기를 시작합니다. 한 손으로 폰을 단추판 쪽으로 들고, 다른 손 검지로 단추를 가리키십시오.", sseuGi: true)
                Girok.shared.namgi("gariki", ["kyeogi": true])
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
        if malHagi { SoriEngine.shared.mal("가리키고 말하기를 멈췄습니다.", .jeongbo) }
    }

    func dasiDeutgi() {
        SoriEngine.shared.mal(majimak.isEmpty ? "아직 읽은 글자가 없습니다." : majimak)
    }

    /// 3분 동안 새로 읽은 글이 없으면 카메라를 끔
    private func salpigi() {
        guard kyeojim, Date().timeIntervalSince(saeT) > 180 else { return }
        kkeugi(malHagi: false)
        mal("3분 동안 새로 읽은 글이 없어 카메라를 껐습니다. 다시 쓰시려면 이어 읽기를 누르십시오.", sseuGi: true)
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

    // MARK: 한 장마다(jul) — 0.5초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.5, !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        let (son, geul) = GarikiIlgi.boda(px)
        var hwakjeong: String?
        if let g = geul {
            if g == apGeul { hwakjeong = g }   // 두 장 잇달아 같은 글이면 읽음(손떨림 줄이기)
            apGeul = g
        } else {
            apGeul = ""
        }
        DispatchQueue.main.async { self.allida(son, geul != nil, hwakjeong) }
    }

    /// 손끝과, 손끝이 가리키는 쪽의 가장 가까운 낱말(폰 안에서만)
    static func boda(_ px: CVPixelBuffer) -> (Bool, String?) {
        let son = VNDetectHumanHandPoseRequest()
        son.maximumHandCount = 2
        let geul = VNRecognizeTextRequest()
        geul.recognitionLevel = .accurate
        geul.recognitionLanguages = ["ko-KR", "en-US"]
        geul.usesLanguageCorrection = false
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: .right, options: [:])
        try? h.perform([son, geul])
        // 검지 끝 — 믿음이 가장 큰 손
        var kkeut: CGPoint?
        var bang = CGVector(dx: 0, dy: 1)
        var mideum: Float = 0
        for o in son.results ?? [] {
            guard let tip = try? o.recognizedPoint(.indexTip), tip.confidence > 0.5, tip.confidence > mideum else { continue }
            mideum = tip.confidence
            kkeut = tip.location
            if let dip = try? o.recognizedPoint(.indexDIP), dip.confidence > 0.3 {
                let dx = tip.location.x - dip.location.x, dy = tip.location.y - dip.location.y
                let l = max(0.0001, (dx * dx + dy * dy).squareRoot())
                bang = CGVector(dx: dx / l, dy: dy / l)
            }
        }
        guard let t = kkeut else { return (false, nil) }
        // 가리키는 곳 — 손끝에서 가리키는 쪽으로 조금 앞
        let p = CGPoint(x: t.x + bang.dx * 0.03, y: t.y + bang.dy * 0.03)
        var best: (String, CGFloat)?
        for o in geul.results ?? [] {
            guard let c = o.topCandidates(1).first, c.confidence >= 0.4 else { continue }
            let s = c.string
            // 낱말마다 자리를 받아 가장 가까운 낱말을 고름
            var i = s.startIndex
            while i < s.endIndex {
                while i < s.endIndex && s[i] == " " { i = s.index(after: i) }
                guard i < s.endIndex else { break }
                var j = i
                while j < s.endIndex && s[j] != " " { j = s.index(after: j) }
                let w = String(s[i..<j]).trimmingCharacters(in: .punctuationCharacters)
                if !w.isEmpty, let b = try? c.boundingBox(for: i..<j)?.boundingBox {
                    let cx = b.midX, cy = b.midY
                    // 가리키는 쪽 뒤(손 쪽)에 있는 글은 버림
                    let ap = (cx - t.x) * bang.dx + (cy - t.y) * bang.dy
                    if ap > -0.01 {
                        let d = b.contains(p) ? 0 : ((cx - p.x) * (cx - p.x) + (cy - p.y) * (cy - p.y)).squareRoot()
                        if d < 0.12 && (best == nil || d < best!.1) { best = (w, d) }
                    }
                }
                i = j
            }
        }
        return (true, best?.0)
    }

    // MARK: 알리기(메인)

    private func allida(_ son: Bool, _ geulItda: Bool, _ geul: String?) {
        guard kyeojim else { return }
        let now = Date()
        if !son {
            if sonBoim && now.timeIntervalSince(sonT) > 1.5 { sonBoim = false; sonEopT = now; sonEopMalSu = 0 }
            if !sonBoim && now.timeIntervalSince(sonEopT) > 3 && sonEopMalSu < 3 && now.timeIntervalSince(sonEopMalT) >= 3 {
                sonEopMalSu += 1
                sonEopMalT = now
                mal("손가락이 보이지 않습니다. 폰을 조금 뒤로 빼 주십시오.", .jeongbo)
            }
            return
        }
        sonT = now
        if !sonBoim {
            sonBoim = true
            sonEopMalSu = 0
            SoriEngine.shared.sori(.hwaksin)
            mal("손가락이 보입니다.", .jeongbo)
        }
        if let g = geul, g != malHanGeul {
            malHanGeul = g
            majimak = g
            saeT = now
            geulEopMal = false
            beon += 1
            ilgeun.insert(GeulJul(id: beon, mal: g), at: 0)
            if ilgeun.count > 100 { ilgeun = Array(ilgeun.prefix(100)) }
            mal(g)
        } else if !geulItda && !geulEopMal && now.timeIntervalSince(saeT) > 6 {
            geulEopMal = true
            mal("손끝 가까이에 글자가 없습니다.", .jeongbo)
        }
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }   // 카메라 눈 말소리 끔 — 화면 글자로만
        SoriEngine.shared.mal(t, g)
    }
}

// MARK: 화면 — 둘러보기 탭

struct GarikiIlgiView: View {
    @ObservedObject private var g = GarikiIlgi.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 가리킨 글을 읽는 중" : "이어 읽기 — 카메라를 켜고 가리킨 글 읽기") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("방금 읽은 것 다시 듣기") { g.dasiDeutgi() }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("읽은 글 펼치기 — \(g.ilgeun.count)개") {
                    Mokrok5(g.ilgeun) { j in Text(j.mal).font(.title3) }
                }
                .font(.title3)
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("전자레인지, 세탁기, 엘리베이터, 키오스크처럼 단추가 많은 곳에서 손가락으로 가리킨 곳의 글자만 골라 읽어 드립니다. 화면을 여시면 바로 시작합니다. 한 손으로 폰을 단추판 쪽으로 들고, 다른 손 검지로 단추를 가리키십시오. 손끝을 찾으면 손가락이 보입니다라고 한 번 알리고, 손끝이 가리키는 쪽 가장 가까운 낱말을 읽어 드립니다. 손가락을 옮기시면 새 글에 닿을 때마다 읽고, 같은 글에 머무시면 되풀이하지 않습니다. 손가락이 화면 밖으로 나가면 폰을 조금 뒤로 빼시라고 알려 드립니다. 카메라 눈 말소리를 끄시면 화면 글자로만 남습니다. 3분 동안 새로 읽은 글이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 가리키는 거 읽어 줘라고 하셔도 열립니다. 사진과 글은 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("가리키고 말하기")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
