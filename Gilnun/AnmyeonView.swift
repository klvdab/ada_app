// 안면인식 — 앱 2.7.0 (빌드 260928-9), 이사장님 승인(1번).
// 아이폰 자체 얼굴 찾기(Vision)를 폰 안에서만 씁니다. 사진은 어디로도 보내지 않습니다.
// 몇 명이 있는지, 몇 시 방향인지, 대략 몇 미터인지, 이쪽을 보는지, 웃는 듯한지(조심스럽게 짐작)를 알려 드립니다.
// 아는 사람 등록은 폰 안에만 보관(아이클라우드 백업에서도 뺌). 알려 드리는 말은 바뀔 때만, 3초에 한 번까지.
import SwiftUI
import AVFoundation
import Vision
import UIKit

struct AnmyeonSaram: Equatable {
    let sigak: Int        // 시계 방향 11·12·1 등
    let miteo: Double     // 대략 거리
    let boneunJung: Bool  // 이쪽을 보는지
    let unneunDeut: Bool  // 웃는 듯한지(짐작)
    var ireum: String?    // 등록한 사람이면 이름(짐작)
}

final class AnmyeonEngine: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = AnmyeonEngine()

    @Published var kyeojim = false
    @Published var jigeumMal = "카메라가 꺼져 있습니다."
    @Published var ireumDeul: [String] = []
    @Published var deungrokJung = false

    let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.anmyeon")
    private var junbiDoem = false
    private var majimakBoda = Date.distantPast
    private var majimakMalSigan = Date.distantPast
    private var majimakMal = ""
    private var bonSaram: [AnmyeonSaram] = []

    // 아는 사람 — 이름마다 얼굴 특징 여러 장
    private var aneun: [String: [VNFeaturePrintObservation]] = [:]
    private var deungrokIreum = ""
    private var deungrokMoeum: [VNFeaturePrintObservation] = []

    override init() {
        super.init()
        bureogi()
    }

    // MARK: 켜고 끄기

    func kyeogi() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() }
                    else { SoriEngine.shared.mal("카메라를 쓸 수 없어 안면인식을 켜지 못했습니다.") }
                }
            }
        default:
            SoriEngine.shared.mal("카메라 허락이 꺼져 있습니다. 아이폰 설정의 길눈에서 카메라를 켜 주십시오.")
        }
    }

    private func sijak() {
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
                self.majimakMal = ""
                self.majimakMalSigan = .distantPast
                UIApplication.shared.isIdleTimerDisabled = true
                SoriEngine.shared.mal("안면인식을 켰습니다. 폰 뒤쪽 카메라를 앞으로 향해 들어 주십시오.")
                Girok.shared.namgi("anmyeon", ["kyeogi": true])
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async {
            if self.sesyeon.isRunning { self.sesyeon.stopRunning() }
        }
        guard kyeojim else { return }
        kyeojim = false
        deungrokJung = false
        jigeumMal = "카메라가 꺼져 있습니다."
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("안면인식을 껐습니다.", .jeongbo) }
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .vga640x480
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        let op = AVCaptureVideoDataOutput()
        op.alwaysDiscardsLateVideoFrames = true
        op.setSampleBufferDelegate(self, queue: jul)
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        return true
    }

    // MARK: 얼굴 보기 — 0.5초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(majimakBoda) > 0.5,
              let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        majimakBoda = now
        // 폰을 세워 든 채 뒤쪽 카메라 — 화면 기준 바로 세움
        let handler = VNImageRequestHandler(cvPixelBuffer: pb, orientation: .right, options: [:])
        // 얼굴 자리와 고개 돌림(yaw)은 얼굴 찾기에서, 입 모양은 이목구비 찾기에서
        let eolgul = VNDetectFaceRectanglesRequest()
        do { try handler.perform([eolgul]) } catch { return }
        let eolguldeul = (eolgul.results ?? []).sorted { $0.boundingBox.midX < $1.boundingBox.midX }
        var ipDeul: [VNFaceObservation] = []
        if !eolguldeul.isEmpty {
            let imok = VNDetectFaceLandmarksRequest()
            imok.inputFaceObservations = eolguldeul
            if (try? handler.perform([imok])) != nil { ipDeul = imok.results ?? [] }
        }

        var saram: [AnmyeonSaram] = []
        for f in eolguldeul {
            let b = f.boundingBox
            let x = b.midX
            let sigak: Int = x < 0.2 ? 10 : (x < 0.4 ? 11 : (x <= 0.6 ? 12 : (x <= 0.8 ? 1 : 2)))
            let miteo = min(15, max(0.3, 0.19 / max(b.height, 0.01)))
            var boneun = false
            if let yaw = f.yaw?.doubleValue { boneun = abs(yaw) < 0.35 }
            var unneun = false
            if let l = ipDeul.min(by: { AnmyeonEngine.tteoreojim($0.boundingBox, b) < AnmyeonEngine.tteoreojim($1.boundingBox, b) }),
               AnmyeonEngine.tteoreojim(l.boundingBox, b) < 0.05 {
                unneun = AnmyeonEngine.unneunDeut(l)
            }
            saram.append(AnmyeonSaram(sigak: sigak, miteo: miteo, boneunJung: boneun,
                                      unneunDeut: unneun, ireum: nil))
        }

        // 아는 사람 찾기 / 등록 — 얼굴 부분만 특징으로
        if (!aneun.isEmpty || deungrokJung) && !eolguldeul.isEmpty {
            for (i, f) in eolguldeul.enumerated() {
                guard let fp = AnmyeonEngine.teukjing(handler, f.boundingBox) else { continue }
                if deungrokJung && eolguldeul.count == 1 {
                    deungrokMoeum.append(fp)
                    if deungrokMoeum.count >= 5 { deungrokKkeut() }
                } else if !aneun.isEmpty {
                    saram[i].ireum = chajgi(fp)
                }
            }
        }
        if deungrokJung && eolguldeul.count != 1 {
            DispatchQueue.main.async {
                self.jigeumMal = eolguldeul.isEmpty ? "등록할 얼굴이 보이지 않습니다." : "한 사람만 보이게 해 주십시오."
            }
        }
        allimgi(saram)
    }

    private func allimgi(_ saram: [AnmyeonSaram]) {
        let mal = AnmyeonEngine.malMandeulgi(saram)
        DispatchQueue.main.async {
            guard self.kyeojim else { return }
            if !self.deungrokJung { self.jigeumMal = mal }
            let now = Date()
            guard mal != self.majimakMal, now.timeIntervalSince(self.majimakMalSigan) >= 3, !self.deungrokJung else { return }
            self.majimakMal = mal
            self.majimakMalSigan = now
            SoriEngine.shared.mal(mal, .jeongbo)
        }
    }

    static func malMandeulgi(_ saram: [AnmyeonSaram]) -> String {
        guard !saram.isEmpty else { return "사람 얼굴이 보이지 않습니다." }
        let su = ["", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열"]
        let n = saram.count
        var t = (n < su.count ? su[n] : "\(n)") + " 명."
        for s in saram.prefix(4) {
            var m = "\(s.sigak)시 방향"
            if let i = s.ireum { m += " \(i)님인 듯," }
            m += " 약 \(miteoMal(s.miteo))"
            if s.boneunJung { m += ", 이쪽을 봅니다" }
            if s.unneunDeut { m += ", 웃는 듯합니다" }
            t += " " + m + "."
        }
        if n > 4 { t += " 그 밖에 \(n - 4)명." }
        return t
    }

    static func miteoMal(_ m: Double) -> String {
        if m < 1 { return "1미터 안" }
        if m < 3 { return String(format: "%.1f미터", (m * 2).rounded() / 2).replacingOccurrences(of: ".0", with: "") }
        return "\(Int(m.rounded()))미터"
    }

    static func tteoreojim(_ a: CGRect, _ b: CGRect) -> CGFloat {
        abs(a.midX - b.midX) + abs(a.midY - b.midY)
    }

    /// 입꼬리가 입 가운데보다 올라가고 입이 넓으면 "웃는 듯" — 조심스러운 짐작
    static func unneunDeut(_ f: VNFaceObservation) -> Bool {
        guard let ip = f.landmarks?.outerLips, ip.pointCount >= 6 else { return false }
        let p = ip.normalizedPoints
        guard let oen = p.min(by: { $0.x < $1.x }), let oreun = p.max(by: { $0.x < $1.x }) else { return false }
        let pok = oreun.x - oen.x
        let gaunde = p.reduce(CGFloat(0)) { $0 + $1.y } / CGFloat(p.count)
        let kkori = (oen.y + oreun.y) / 2
        return pok > 0.42 && kkori > gaunde + 0.015
    }

    static func teukjing(_ h: VNImageRequestHandler, _ b: CGRect) -> VNFeaturePrintObservation? {
        let r = VNGenerateImageFeaturePrintRequest()
        let k = b.insetBy(dx: -b.width * 0.1, dy: -b.height * 0.1)
            .intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        guard !k.isNull, k.width > 0.05 else { return nil }
        r.regionOfInterest = k
        do { try h.perform([r]) } catch { return nil }
        return r.results?.first as? VNFeaturePrintObservation
    }

    /// 가장 가까운 사람. 특징 판마다 거리 잣대가 달라 두 가지로 봄.
    private func chajgi(_ fp: VNFeaturePrintObservation) -> String? {
        var jal: (String, Float)?
        for (ireum, deul) in aneun {
            for d in deul {
                var g: Float = 0
                guard (try? fp.computeDistance(&g, to: d)) != nil else { continue }
                if jal == nil || g < jal!.1 { jal = (ireum, g) }
            }
        }
        guard let j = jal else { return nil }
        let jatdae: Float = fp.requestRevision >= 2 ? 0.55 : 12
        return j.1 < jatdae ? j.0 : nil
    }

    // MARK: 아는 사람 등록 — 폰 안에만

    func deungrokSijak(_ ireum: String) {
        let i = ireum.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !i.isEmpty else { SoriEngine.shared.mal("등록할 분의 이름을 먼저 적어 주십시오."); return }
        guard kyeojim else { SoriEngine.shared.mal("먼저 안면인식을 켜 주십시오."); return }
        jul.async {
            self.deungrokIreum = i
            self.deungrokMoeum = []
        }
        deungrokJung = true
        jigeumMal = "\(i)님 얼굴을 담는 중입니다."
        SoriEngine.shared.mal("\(i)님 얼굴을 담습니다. 그분 얼굴이 카메라에 보이게 몇 초 들고 계십시오.")
    }

    private func deungrokKkeut() {
        let i = deungrokIreum
        aneun[i, default: []].append(contentsOf: deungrokMoeum)
        if aneun[i]!.count > 15 { aneun[i] = Array(aneun[i]!.suffix(15)) }
        deungrokMoeum = []
        let deul = aneun
        DispatchQueue.main.async {
            self.deungrokJung = false
            self.ireumDeul = deul.keys.sorted()
            SoriEngine.shared.mal("\(i)님을 등록했습니다. 폰 안에만 보관합니다.")
            Girok.shared.namgi("anmyeon", ["deungrok": true])
        }
        jeojang(deul)
    }

    func jiugi(_ ireum: String) {
        jul.async {
            self.aneun[ireum] = nil
            let deul = self.aneun
            self.jeojang(deul)
            DispatchQueue.main.async {
                self.ireumDeul = deul.keys.sorted()
                SoriEngine.shared.mal("\(ireum)님을 지웠습니다.")
            }
        }
    }

    private static var pail: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun_anmyeon.plist")
    }

    private func jeojang(_ deul: [String: [VNFeaturePrintObservation]]) {
        var d: [String: [Data]] = [:]
        for (k, v) in deul {
            d[k] = v.compactMap { try? NSKeyedArchiver.archivedData(withRootObject: $0, requiringSecureCoding: true) }
        }
        var u = AnmyeonEngine.pail
        try? FileManager.default.createDirectory(at: u.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let data = try? PropertyListEncoder().encode(d) else { return }
        try? data.write(to: u, options: [.atomic, .completeFileProtection])
        var rv = URLResourceValues()
        rv.isExcludedFromBackup = true
        try? u.setResourceValues(rv)
    }

    private func bureogi() {
        guard let data = try? Data(contentsOf: AnmyeonEngine.pail),
              let d = try? PropertyListDecoder().decode([String: [Data]].self, from: data) else { return }
        var deul: [String: [VNFeaturePrintObservation]] = [:]
        for (k, v) in d {
            deul[k] = v.compactMap { try? NSKeyedUnarchiver.unarchivedObject(ofClass: VNFeaturePrintObservation.self, from: $0) }
        }
        aneun = deul
        ireumDeul = deul.keys.sorted()
    }
}

struct AnmyeonView: View {
    @ObservedObject private var e = AnmyeonEngine.shared
    @State private var ireum = ""
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(e.kyeojim ? "안면인식 끄기" : "안면인식 켜기") {
                    if e.kyeojim { e.kkeugi() } else { e.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Text(e.jigeumMal)
                    .font(.title3)
                    .accessibilityLabel("지금 보이는 것. \(e.jigeumMal)")
                Text("폰을 세워 들고 뒤쪽 카메라를 앞으로 향하십시오. 바뀔 때만 3초에 한 번까지 말씀드립니다. 거리와 웃음은 짐작입니다.")
                    .font(.footnote)
                DisclosureGroup("아는 사람 등록 — 폰 안에만 보관") {
                    VStack(alignment: .leading, spacing: 10) {
                        TextField("등록할 분의 이름", text: $ireum)
                            .textFieldStyle(.roundedBorder)
                        Button(e.deungrokJung ? "얼굴을 담는 중입니다" : "지금 앞의 얼굴로 등록") {
                            guard !e.deungrokJung else { return }
                            e.deungrokSijak(ireum)
                        }
                        .buttonStyle(KeunDanchu())
                        if e.ireumDeul.isEmpty {
                            Text("등록한 분이 없습니다.")
                        } else {
                            ForEach(e.ireumDeul, id: \.self) { i in
                                Text(i)
                                    .accessibilityLabel("\(i). 등록한 분")
                                    .accessibilityAction(named: "지우기") { e.jiugi(i) }
                            }
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("안면인식")
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }
        .onDisappear { e.kkeugi(malHagi: false) }
    }
}
