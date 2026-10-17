// LVD투어스튜디오 앱 — 1.2.0판 화면 ③ 카메라 (빌드 261017-T4, 방클, 웹 startCam·camTick·shoot 과 같은 잣대)
// 몸을 돌리면 저절로 찍기(나침반으로 12시·3시·6시·9시, 12도 안에 들면 담음), 폰 기울기 알림, 담을 때 소리,
// 손으로 한 장 찍기(나침반이 잡히지 않으면 차례대로), 폰에 담긴 사진 넣기
import SwiftUI
import UIKit
import AVFoundation
import CoreLocation
import CoreMotion
import PhotosUI

/// 담을 때 소리(삐) — 웹 SND.beep 과 같음. 아이폰 셔터 소리는 나라 규정으로 늘 납니다
enum Sori {
    static var beep: Bool {
        get { UserDefaults.standard.object(forKey: "damSori") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "damSori") }
    }
}

final class JeoljeoroGi: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate, CLLocationManagerDelegate {
    let session = AVCaptureSession()
    private let out = AVCapturePhotoOutput()
    private let lm = CLLocationManager()
    private let mm = CMMotionManager()
    @Published var heading: Double? = nil
    @Published var jul = "카메라를 여는 중입니다."
    @Published var got: Set<Int> = []
    var auto = true
    var base: Double? = nil
    var busy = false
    var guide = false
    var idx = 0
    private var gz: Double? = nil
    private var lastBk: Int? = nil
    private var lastTl = ""
    private var saidAt = Date.distantPast
    private var pending: Int = 0
    var say: (String) -> Void = { _ in }
    var damgi: (UIImage, Int, Double?) async -> Void = { _, _, _ in }
    static let DIRS: [(k: Int, a: Double)] = [(12, 0), (3, 90), (6, 180), (9, 270)]

    func yeolgi() {
        AVCaptureDevice.requestAccess(for: .video) { ok in
            guard ok else { DispatchQueue.main.async { self.say("카메라 쓰기를 허락하지 않으셨습니다. 폰 설정의 투어스튜디오에서 카메라를 켜 주십시오.") }; return }
            DispatchQueue.global(qos: .userInitiated).async {
                self.session.beginConfiguration()
                self.session.sessionPreset = .photo
                if let d = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back), let inp = try? AVCaptureDeviceInput(device: d), self.session.canAddInput(inp) { self.session.addInput(inp) }
                if self.session.canAddOutput(self.out) { self.session.addOutput(self.out) }
                self.session.commitConfiguration()
                self.session.startRunning()
            }
        }
        lm.delegate = self
        lm.headingOrientation = .portrait
        if lm.authorizationStatus == .notDetermined { lm.requestWhenInUseAuthorization() }
        if CLLocationManager.headingAvailable() { lm.headingFilter = 1; lm.startUpdatingHeading() }
        if mm.isDeviceMotionAvailable {
            mm.deviceMotionUpdateInterval = 0.2
            mm.startDeviceMotionUpdates(to: .main) { [weak self] m, _ in if let m { self?.gz = m.gravity.z; self?.tick() } }
        }
        got = []; base = nil; idx = 0; busy = false
        jul = "카메라가 열려 있습니다. 방위를 읽는 중입니다."
        say("카메라를 열었습니다. " + (auto ? "앞을 향하고 잠시 서 계시면 12시부터 담고, 시계 방향으로 천천히 도시면 3시·6시·9시가 차례로 담깁니다." : "방향을 잡고 한 장 찍기를 누르십시오."))
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.5) { [weak self] in
            guard let self else { return }
            if self.heading == nil { self.guide = true
                let nx = [12, 3, 6, 9][self.idx % 4]
                self.jul = "나침반이 잡히지 않아 차례대로 담습니다. 다음은 \(nx)시 방향입니다."
                self.say("나침반이 잡히지 않습니다. 몸을 시계 방향으로 돌리신 뒤 한 장 찍기를 누르시면 \(nx)시 방향으로 담습니다.")
            }
        }
        if auto { DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { [weak self] in if let self, self.base == nil, self.session.isRunning { self.jjikgi(12) } } }
    }
    func datgi() {
        lm.stopUpdatingHeading(); mm.stopDeviceMotionUpdates()
        DispatchQueue.global().async { self.session.stopRunning() }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateHeading h: CLHeading) {
        let v = h.trueHeading >= 0 ? h.trueHeading : h.magneticHeading
        DispatchQueue.main.async { self.heading = v; self.tick() }
    }
    func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool { false }
    private func angDiff(_ a: Double, _ b: Double) -> Double { let d = abs(a - b).truncatingRemainder(dividingBy: 360); return d > 180 ? 360 - d : d }
    private func tick() {
        let now = Date()
        guard let h = heading else { return }
        let bk = base == nil ? 12 : [12, 3, 6, 9][Int((((h - base!) + 360).truncatingRemainder(dividingBy: 360) / 90).rounded()) % 4]
        let t = "\(bk)시 방향으로 잡습니다."
        if bk != lastBk { if lastBk != nil && !auto && now.timeIntervalSince(saidAt) > 1.5 { say(t); saidAt = now }; lastBk = bk }
        if jul != t && !busy { jul = t }
        if let z = gz {
            let tl = z > 0.26 ? "down" : (z < -0.5 ? "up" : "ok")
            if tl != lastTl { if tl != "ok" && now.timeIntervalSince(saidAt) > 3 { say(tl == "down" ? "폰을 조금 내려 주십시오." : "폰을 조금 올려 주십시오."); saidAt = now }; lastTl = tl }
        }
        if auto, let b = base, !busy {
            let rel = ((h - b) + 360).truncatingRemainder(dividingBy: 360)
            for d in JeoljeoroGi.DIRS where !got.contains(d.k) && angDiff(rel, d.a) < 12 { jjikgi(d.k); break }
        }
    }
    /// 한 장 찍기 — clock 0 이면 지금 잡힌 방향(나침반이 없으면 차례대로)
    func jjikgi(_ clock0: Int) {
        if busy { return }
        guard session.isRunning else { say("카메라가 아직 열리지 않았습니다. 잠시 뒤 다시 눌러 주십시오."); return }
        var clock = clock0
        if base == nil, let h = heading { base = h }
        if clock == 0 {
            if heading == nil { clock = [12, 3, 6, 9][idx % 4]; idx += 1 }
            else if let h = heading, let b = base { clock = [12, 3, 6, 9][Int((((h - b) + 360).truncatingRemainder(dividingBy: 360) / 90).rounded()) % 4] }
            else { clock = 12 }
        } else if clock == 12 && heading == nil { idx = 1 }
        busy = true; pending = clock
        if Sori.beep { Gwan.ppi() }
        let st = AVCapturePhotoSettings()
        out.capturePhoto(with: st, delegate: self)
    }
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let clock = pending, hd = heading
        guard let d = photo.fileDataRepresentation(), let im = UIImage(data: d) else { DispatchQueue.main.async { self.busy = false; self.say("사진을 담지 못했습니다. 다시 찍어 주십시오.") }; return }
        Task { @MainActor in
            await self.damgi(im, clock, hd)
            self.got.insert(clock)
            self.busy = false
            let left = JeoljeoroGi.DIRS.filter { !self.got.contains($0.k) }.map { "\($0.k)시" }
            let m = left.isEmpty ? "네 방향을 다 담았습니다. 다음 지점으로 옮기셔도 됩니다." : "다음은 " + left[0] + " 방향입니다." + (self.auto && !self.guide ? " 시계 방향으로 천천히 도십시오." : " 몸을 시계 방향으로 돌리신 뒤 한 장 찍기를 누르십시오.")
            self.jul = m
            self.say(m)
        }
    }
}

struct KameraMiri: UIViewRepresentable {
    let session: AVCaptureSession
    final class V: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var pl: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
    func makeUIView(context: Context) -> V { let v = V(); v.pl.session = session; v.pl.videoGravity = .resizeAspect; v.isAccessibilityElement = false; return v }
    func updateUIView(_ uiView: V, context: Context) {}
}

/// 저절로 찍기 화면 — 위에 지금 방향 한 줄과 네 방향 표시, 아래에 한 장 찍기와 닫기
struct JeoljeoroView: View {
    @EnvironmentObject var store: TourStore
    @Environment(\.dismiss) private var dismiss
    let auto: Bool
    @StateObject private var g = JeoljeoroGi()
    @AccessibilityFocusState private var f: Bool
    private var damEun: String {
        let a = [12, 3, 6, 9].filter { g.got.contains($0) }.map { "\($0)시" }
        return "담은 방향 " + (a.isEmpty ? "없음" : a.joined(separator: ", "))
    }
    var body: some View {
        VStack(spacing: 10) {
            Text(g.jul).font(.title3.bold()).multilineTextAlignment(.center).padding(.top, 12).accessibilityFocused($f)
            Text([12, 3, 6, 9].map { (g.got.contains($0) ? "● " : "○ ") + "\($0)시" }.joined(separator: "  "))
                .font(.headline).accessibilityLabel(damEun)
            KameraMiri(session: g.session).frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityHidden(true)
            HStack(spacing: 16) {
                Button("한 장 찍기") { g.jjikgi(0) }.font(.title2.bold()).buttonStyle(.borderedProminent)
                Button("닫기") { g.datgi(); dismiss() }.font(.title3).buttonStyle(.bordered)
            }.padding(.bottom, 20)
        }
        .background(Color.black.opacity(0.9))
        .foregroundStyle(.white)
        .onAppear {
            g.auto = auto
            g.say = { t in store.say(t) }
            g.damgi = { im, clock, hd in
                var ex: [String: Any] = ["auto": auto]
                if let hd { ex["head"] = Int(hd.rounded()) } else { ex["head"] = NSNull() }
                await store.photo(im, dir: "\(clock)시 방향", extra: ex)
            }
            g.yeolgi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { f = true }
        }
        .onDisappear { g.datgi() }
        .accessibilityAction(.escape) { g.datgi(); dismiss() }
    }
}

/// 답사 탭에 붙는 1.2.0 카메라 묶음 — 찍는 방식, 담는 모양, 담을 때 소리, 폰에 담긴 사진 넣기, 코스 벗어남 알림
struct Kamera120Section: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    @ObservedObject private var bs = Beoseonam.shared
    @State private var yeolgi = false
    @State private var auto = UserDefaults.standard.object(forKey: "camAuto") as? Bool ?? false
    @State private var moyang = Wonchik.moyang
    @State private var beep = Sori.beep
    @State private var goreun: PhotosPickerItem? = nil
    var body: some View {
        Section("카메라 — 몸을 돌리면 저절로 찍기·폰 사진 넣기") {
            Picker("찍는 방식", selection: $auto) {
                Text("손으로 한 장씩 찍기").tag(false)
                Text("몸을 돌리면 저절로 찍기 (12시·3시·6시·9시)").tag(true)
            }.onChange(of: auto) { _, v in UserDefaults.standard.set(v, forKey: "camAuto") }
            Button(auto ? "저절로 찍기 카메라 열기" : "나침반 카메라 열기 (한 장씩)") {
                if store.cur == nil { store.say("먼저 지점을 골라 주십시오."); return }
                yeolgi = true
            }.font(.headline)
            Picker("담는 모양", selection: $moyang) { ForEach(Wonchik.MOYANG, id: \.0) { Text($0.1).tag($0.0) } }
                .onChange(of: moyang) { _, v in UserDefaults.standard.set(v, forKey: "damneunMoyang"); store.say("담는 모양을 " + (Wonchik.MOYANG.first { $0.0 == v }?.1 ?? "") + "(으)로 정했습니다.") }
            Toggle("담을 때 삐 소리", isOn: $beep).onChange(of: beep) { _, v in Sori.beep = v }
            PhotosPicker(selection: $goreun, matching: .images) { Text("폰에 담긴 사진 넣기 (방위 모름으로 담김)") }
                .onChange(of: goreun) { _, it in
                    guard let it else { return }
                    Task {
                        if let d = try? await it.loadTransferable(type: Data.self), let im = UIImage(data: d) { await store.photo(im, dir: "방위 모름", extra: ["from": "file"]) }
                        else { store.say("사진을 읽지 못했습니다.") }
                        goreun = nil
                    }
                }
        }
        Section("코스 벗어남 알림") {
            Toggle("코스 벗어남 알림", isOn: Binding(get: { g.offOn }, set: { g.offOn = $0; if $0 { bs.sijak() } else { bs.meomchum() }; store.say($0 ? "코스 벗어남 알림을 켰습니다." : "코스 벗어남 알림을 껐습니다.") }))
            Stepper(value: Binding(get: { g.offM }, set: { g.offM = $0 }), in: 10...300, step: 10) { Text("벗어남 기준 \(g.offM)미터") }
            Stepper(value: Binding(get: { g.offS }, set: { g.offS = $0 }), in: 5...120, step: 5) { Text("기다리는 시간 \(g.offS)초") }
            if !bs.tti.isEmpty { Text(bs.tti).font(.headline) }
        }
        .onAppear { bs.sijak() }
        .fullScreenCover(isPresented: $yeolgi) { JeoljeoroView(auto: auto).environmentObject(store) }
    }
}
