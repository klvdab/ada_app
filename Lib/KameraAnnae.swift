// AI점자도서관 — 0.8.7 (261121-L26) 소리로 이끄는 카메라 (도클, 이사장님 결정 2026-11-21)
// 1. 비추는 것이 무엇인지 소리로 읽어 줌(글자는 읽고, 글자가 없으면 무엇처럼 보이는지)
// 2. 복지카드가 화면 가운데 오도록 시계 방향으로 이끔(「9시 방향으로 조금」, 「폰을 조금 내려 주세요」)
// 3. 카드가 가운데 오면 저절로 찍고, 폰 안에서 글자를 읽어 「복지카드·시각장애·회원 이름」을 살핌.
//    셋이 모두 맞으면 서버가 바로 통과시키고, 아니면 협회가 사진을 보고 확인함.
//    읽은 글자(주민번호 등)는 폰 밖으로 보내지 않고 맞다·아니다만 보냄.
import SwiftUI
import AVFoundation
import Vision

/// 찍은 사진과 폰 안에서 살핀 결과
struct KameraGyeolgwa {
    let img: UIImage
    let bokji: Bool   // 복지카드·장애인등록증·장애인증명서 글자가 보임
    let sigak: Bool   // 「시각」 글자가 보임
    let ireum: Bool   // 회원 이름이 보임
    var modu: Bool { bokji && sigak && ireum }
}

struct KameraAnnaeView: UIViewControllerRepresentable {
    let ireum: String
    let ggeut: (KameraGyeolgwa?) -> Void
    func makeUIViewController(context: Context) -> KameraAnnaeVC { let v = KameraAnnaeVC(); v.ireum = ireum; v.ggeut = ggeut; return v }
    func updateUIViewController(_ vc: KameraAnnaeVC, context: Context) {}
}

final class KameraAnnaeVC: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate, AVCapturePhotoCaptureDelegate {
    var ireum = ""
    var ggeut: ((KameraGyeolgwa?) -> Void)?
    private let session = AVCaptureSession()
    private let videoOut = AVCaptureVideoDataOutput()
    private let photoOut = AVCapturePhotoOutput()
    private let q = DispatchQueue(label: "kr.or.ada.lib.kamera")
    private var preview: AVCaptureVideoPreviewLayer?
    private var lastBun: TimeInterval = 0        // 마지막으로 살핀 때
    private var lastMal = ""; private var lastMalT: TimeInterval = 0
    private var majeum = 0                       // 가운데에 연달아 들어온 횟수
    private var jjikneunJung = false
    private var bogiMode = false                 // 「보이는 것 읽어 주기」를 눌렀을 때 한 번
    private let tts = AVSpeechSynthesizer()
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        guard let dev = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: dev) else { mal("카메라를 열지 못했습니다.", sege: true); return }
        session.beginConfiguration()
        session.sessionPreset = .photo
        if session.canAddInput(input) { session.addInput(input) }
        videoOut.alwaysDiscardsLateVideoFrames = true
        videoOut.setSampleBufferDelegate(self, queue: q)
        if session.canAddOutput(videoOut) { session.addOutput(videoOut) }
        if session.canAddOutput(photoOut) { session.addOutput(photoOut) }
        session.commitConfiguration()
        let pl = AVCaptureVideoPreviewLayer(session: session); pl.videoGravity = .resizeAspectFill; view.layer.addSublayer(pl); preview = pl
        // 단추 셋 — 보이는 것 읽기, 지금 찍기, 닫기(낭독기로 찾기 쉽게 화면 위·가운데·아래)
        let st = UIStackView(); st.axis = .vertical; st.spacing = 14; st.translatesAutoresizingMaskIntoConstraints = false
        for (t, a) in [("보이는 것 읽어 주기", #selector(bogi)), ("지금 바로 찍기", #selector(jigeumJjik)), ("닫기", #selector(datgi))] {
            let b = UIButton(type: .system); b.setTitle(t, for: .normal); b.titleLabel?.font = .boldSystemFont(ofSize: 22)
            b.backgroundColor = UIColor(white: 0, alpha: 0.6); b.setTitleColor(.white, for: .normal); b.layer.cornerRadius = 12
            b.contentEdgeInsets = UIEdgeInsets(top: 14, left: 18, bottom: 14, right: 18); b.addTarget(self, action: a, for: .touchUpInside)
            st.addArrangedSubview(b)
        }
        view.addSubview(st)
        NSLayoutConstraint.activate([st.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
                                     st.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
                                     st.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24)])
        accessibilityViewIsModal = true
        q.async { [weak self] in self?.session.startRunning() }
        mal("카메라가 열렸습니다. 복지카드를 평평한 곳에 놓고, 폰을 카드 위 한 뼘 높이에서 들어 주십시오. 카드가 가운데로 오도록 소리로 알려 드리고, 가운데 오면 저절로 찍습니다.", sege: true)
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); preview?.frame = view.bounds }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); q.async { [weak self] in self?.session.stopRunning() }; tts.stopSpeaking(at: .immediate) }
    override func accessibilityPerformEscape() -> Bool { datgi(); return true }

    @objc private func datgi() { q.async { [weak self] in self?.session.stopRunning() }; ggeut?(nil) }
    @objc private func bogi() { bogiMode = true; lastBun = 0 }
    @objc private func jigeumJjik() { jjikgi() }

    /// 안내 말 — 보이스오버가 켜져 있으면 알림으로, 꺼져 있으면 폰 목소리로. 같은 말은 3초 안에 되풀이하지 않음
    private func mal(_ s: String, sege: Bool = false) {
        let now = Date().timeIntervalSince1970
        if !sege && s == lastMal && now - lastMalT < 3 { return }
        lastMal = s; lastMalT = now
        DispatchQueue.main.async {
            if UIAccessibility.isVoiceOverRunning { UIAccessibility.post(notification: .announcement, argument: s) }
            else { self.tts.stopSpeaking(at: .immediate); let u = AVSpeechUtterance(string: s); u.voice = AVSpeechSynthesisVoice(language: "ko-KR"); self.tts.speak(u) }
        }
    }

    // 0.7초에 한 번 살핌
    func captureOutput(_ output: AVCaptureOutput, didOutput sb: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date().timeIntervalSince1970
        guard !jjikneunJung, now - lastBun > 0.7, let pb = CMSampleBufferGetImageBuffer(sb) else { return }
        lastBun = now
        let h = VNImageRequestHandler(cvPixelBuffer: pb, orientation: .right, options: [:])   // 폰을 세워 든 모양으로
        let rect = VNDetectRectanglesRequest(); rect.minimumAspectRatio = 0.4; rect.maximumAspectRatio = 1.0; rect.minimumSize = 0.12; rect.maximumObservations = 1; rect.minimumConfidence = 0.6
        let geul = VNRecognizeTextRequest(); geul.recognitionLevel = .fast; geul.recognitionLanguages = ["ko-KR", "en-US"]; geul.usesLanguageCorrection = false
        try? h.perform([rect, geul])
        let lines = (geul.results ?? []).compactMap { $0.topCandidates(1).first?.string }
        let all = lines.joined(separator: " ")
        if bogiMode { bogiMode = false; boineunGeot(h: h, lines: lines); return }
        guard let r = rect.results?.first else {
            majeum = 0
            if all.contains("복지") || all.contains("장애") { mal("카드 글자가 보입니다. 폰을 조금 더 들어 올려 카드 전체가 보이게 해 주십시오.") }
            else { mal("카드가 보이지 않습니다. 폰을 카드 바로 위, 한 뼘 높이로 옮겨 주십시오.") }
            return
        }
        // Vision 좌표: 왼쪽 아래가 0. 카드 가운데가 화면 가운데에서 얼마나 벗어났나
        let bb = r.boundingBox
        let dx = bb.midX - 0.5, dy = bb.midY - 0.5, kugi = bb.width * bb.height
        var hal: [String] = []
        func bang(_ d: CGFloat, _ plus: String, _ minus: String) { let a = abs(d); if a < 0.1 { return }; hal.append((d > 0 ? plus : minus) + (a > 0.25 ? " 방향으로 많이" : " 방향으로 조금")) }
        bang(dx, "3시", "9시")     // 카드가 화면 오른쪽에 있으면 폰을 3시 쪽(오른쪽)으로
        bang(dy, "12시", "6시")    // 카드가 화면 위쪽에 있으면 폰을 12시 쪽(위)으로
        if !hal.isEmpty { majeum = 0; mal("폰을 " + hal.joined(separator: ", ") + " 옮겨 주십시오."); return }
        if kugi < 0.15 { majeum = 0; mal("가운데입니다. 폰을 카드 쪽으로 조금 내려 주십시오."); return }
        if kugi > 0.8 { majeum = 0; mal("너무 가깝습니다. 폰을 조금 들어 올려 주십시오."); return }
        majeum += 1
        DispatchQueue.main.async { self.haptic.impactOccurred() }
        if majeum == 1 { mal("좋습니다. 그대로 멈춰 주십시오.", sege: true) }
        if majeum >= 2 { jjikgi() }   // 두 번 연달아 가운데면 저절로 찍음
    }

    /// 「보이는 것 읽어 주기」 — 글자가 있으면 읽고, 없으면 무엇처럼 보이는지
    private func boineunGeot(h: VNImageRequestHandler, lines: [String]) {
        if !lines.isEmpty { mal("보이는 글자입니다. " + lines.prefix(4).joined(separator: ". "), sege: true); return }
        let c = VNClassifyImageRequest(); try? h.perform([c])
        let top = (c.results ?? []).filter { $0.confidence > 0.3 }.prefix(2).map { KameraAnnaeVC.uri($0.identifier) }
        mal(top.isEmpty ? "무엇인지 알아보기 어렵습니다. 조금 더 가까이 비추어 주십시오." : "이것처럼 보입니다. " + top.joined(separator: ", "), sege: true)
    }
    /// 자주 나오는 이름은 우리말로
    static func uri(_ id: String) -> String {
        let d: [String: String] = ["document": "문서", "paper": "종이", "card": "카드", "text": "글자", "person": "사람", "people": "사람", "table": "탁자", "desk": "책상", "hand": "손", "book": "책", "phone": "전화기", "cup": "컵", "bottle": "병", "food": "음식", "dog": "개", "cat": "고양이", "chair": "의자", "door": "문", "window": "창문", "car": "자동차", "computer": "컴퓨터", "keyboard": "자판", "screen": "화면", "wall": "벽", "floor": "바닥", "sky": "하늘", "tree": "나무", "plant": "식물", "money": "돈", "wallet": "지갑", "bag": "가방", "clothing": "옷", "shoe": "신발", "cable": "선", "light": "불빛", "box": "상자"]
        let k = id.lowercased().components(separatedBy: CharacterSet(charactersIn: "_ ,")).first ?? id
        return d[k] ?? id.replacingOccurrences(of: "_", with: " ")
    }

    private func jjikgi() {
        guard !jjikneunJung else { return }
        jjikneunJung = true
        mal("찍습니다.", sege: true)
        DispatchQueue.main.async { let s = AVCapturePhotoSettings(); self.photoOut.capturePhoto(with: s, delegate: self) }
    }
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let d = photo.fileDataRepresentation(), let img = UIImage(data: d) else { jjikneunJung = false; majeum = 0; mal("찍지 못했습니다. 다시 맞춰 주십시오.", sege: true); return }
        mal("찍었습니다. 글자를 살피는 중입니다.", sege: true)
        q.async { [weak self] in
            guard let self, let cg = img.cgImage else { return }
            let geul = VNRecognizeTextRequest(); geul.recognitionLevel = .accurate; geul.recognitionLanguages = ["ko-KR", "en-US"]; geul.usesLanguageCorrection = false
            try? VNImageRequestHandler(cgImage: cg, orientation: KameraAnnaeVC.cgOri(img.imageOrientation), options: [:]).perform([geul])
            let t = (geul.results ?? []).compactMap { $0.topCandidates(1).first?.string }.joined(separator: " ")
            let tb = t.replacingOccurrences(of: " ", with: "")
            let ir = self.ireum.replacingOccurrences(of: " ", with: "")
            let g = KameraGyeolgwa(img: img,
                                   bokji: tb.contains("복지카드") || tb.contains("복지") || tb.contains("장애인등록") || tb.contains("장애인증명"),
                                   sigak: tb.contains("시각"),
                                   ireum: ir.count >= 2 && tb.contains(ir))
            DispatchQueue.main.async { self.session.stopRunning(); self.ggeut?(g) }   // 읽은 글자 t 는 여기서 버림(폰 밖으로 보내지 않음)
        }
    }
    static func cgOri(_ o: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch o { case .up: return .up; case .down: return .down; case .left: return .left; case .right: return .right; case .upMirrored: return .upMirrored; case .downMirrored: return .downMirrored; case .leftMirrored: return .leftMirrored; case .rightMirrored: return .rightMirrored; @unknown default: return .up }
    }
}
