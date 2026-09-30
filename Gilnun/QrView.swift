// QR 찾기와 되짚어 나가기 화면 — 앱 2.13.0 (빌드 260929-8, 이사장님 승인 1). 웹 길눈 qr.html · doe.html 과 같은 쓰임.
// QR 찾기: 카메라를 켜 두면 둘레의 QR을 아이폰 자체 판독기로 저절로 찾아 삐 소리와 진동, 무엇인지와 카메라의 어느 쪽에 있는지를 알려 드립니다.
//          카메라 사진은 어디로도 보내지 않습니다. 화면을 떠나면 카메라는 저절로 꺼집니다.
import SwiftUI
import AVFoundation
import UIKit

final class QrEngine: NSObject, ObservableObject, AVCaptureMetadataOutputObjectsDelegate {
    static let shared = QrEngine()

    @Published var kyeojim = false
    @Published var chajeun: String?          // 찾은 QR 글
    @Published var jongryu = ""              // 주소, 전화번호, 글 …
    @Published var seolmyeong = ""           // 무엇으로 가는 QR인지 한 줄
    @Published var jigeumMal = "카메라가 꺼져 있습니다."

    let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.qr")
    private var junbiDoem = false
    private var majimakChajeum = Date()
    private var sigye: Timer?

    func kyeogi() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 QR 찾기를 켜지 못했습니다.") }
                }
            }
        default:
            SoriEngine.shared.mal("카메라 허락이 꺼져 있습니다. 아이폰 설정의 길눈에서 카메라를 켜 주십시오.")
        }
    }

    private func sijak() {
        AnmyeonEngine.shared.kkeugi(malHagi: false)   // 카메라는 한 곳만
        MunChatgi.shared.kkeugi(malHagi: false)       // 2.20.0 다른 카메라 눈도 모두 끔
        GeulIlgi.shared.kkeugi(malHagi: false)
        GarikiIlgi.shared.kkeugi(malHagi: false)
        SaramGamji.shared.kkeugi(malHagi: false)
        SangpumIlgi.shared.kkeugi(malHagi: false)
        JipyeSaek.shared.kkeugi(malHagi: false)  // 2.21.0
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
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.kyeojim = true
                self.chajeun = nil
                self.majimakChajeum = Date()
                self.jigeumMal = "QR을 찾는 중입니다."
                UIApplication.shared.isIdleTimerDisabled = true
                SoriEngine.shared.mal("QR 찾기를 시작했습니다. 폰을 가슴 높이로 세워 들고 천천히 움직여 주십시오. QR이 잡히면 삐 소리가 납니다.")
                Girok.shared.namgi("qr", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.gidarim() }
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        guard kyeojim else { return }
        kyeojim = false
        jigeumMal = "카메라가 꺼져 있습니다."
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("QR 찾기를 그만두고 카메라를 껐습니다.", .jeongbo) }
    }

    /// 찾은 뒤 계속 — 다른 QR 찾기
    func gyesok() {
        chajeun = nil
        majimakChajeum = Date()
        jigeumMal = "QR을 찾는 중입니다."
        SoriEngine.shared.mal("계속 찾습니다.", .jeongbo)
        if !kyeojim { kyeogi() }
    }

    func sangtaeMal() {
        if !kyeojim { SoriEngine.shared.mal("카메라가 꺼져 있습니다. QR 찾기 시작을 누르십시오."); return }
        if let c = chajeun { SoriEngine.shared.mal("찾은 QR — \(seolmyeong) \(c.prefix(60))"); return }
        SoriEngine.shared.mal("QR 찾기 중입니다. 폰의 판독기로 살피고 있습니다.")
    }

    private func gidarim() {
        guard kyeojim, chajeun == nil else { return }
        if Date().timeIntervalSince(majimakChajeum) > 60 {
            majimakChajeum = Date()
            SoriEngine.shared.mal("아직 QR이 잡히지 않았습니다. 찾는 중입니다.", .jeongbo)
        }
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .high
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        let op = AVCaptureMetadataOutput()
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setMetadataObjectsDelegate(self, queue: jul)
        op.metadataObjectTypes = op.availableMetadataObjectTypes.contains(.qr) ? [.qr] : []
        return true
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let q = metadataObjects.compactMap({ $0 as? AVMetadataMachineReadableCodeObject }).first,
              let t = q.stringValue, !t.isEmpty else { return }
        let b = q.bounds   // 카메라 기준(가로로 누운) 0~1 — 폰을 세워 들면 가로·세로가 바뀜
        let x = 1 - b.midY, y = b.midX
        DispatchQueue.main.async {
            guard self.kyeojim, self.chajeun == nil else { return }
            self.chajeun = t
            self.majimakChajeum = Date()
            let (k, s) = QrEngine.jongryuBoda(t)
            self.jongryu = k
            self.seolmyeong = s
            let eodi = QrEngine.eodi(x, y)
            self.jigeumMal = "QR을 찾았습니다. \(s)"
            SoriEngine.shared.sori(.dochak)
            Jindong.hagi("arrive")
            SoriEngine.shared.mal("QR을 찾았습니다. \(eodi) \(s) 내용은 \(t.prefix(80)).")
            Girok.shared.namgi("qr_chajeum", ["jong": k])
        }
    }

    static func eodi(_ x: Double, _ y: Double) -> String {
        var a: [String] = []
        if x < 0.33 { a.append("왼쪽") } else if x > 0.67 { a.append("오른쪽") }
        if y < 0.33 { a.append("위쪽") } else if y > 0.67 { a.append("아래쪽") }
        return a.isEmpty ? "카메라 한가운데에 있습니다." : "카메라의 " + a.joined(separator: " ") + "에 있습니다."
    }

    static func jongryuBoda(_ t: String) -> (String, String) {
        let l = t.lowercased()
        if l.hasPrefix("http://") || l.hasPrefix("https://") {
            let host = URL(string: t)?.host ?? ""
            return ("주소", "\(host) 사이트로 가는 QR입니다.")
        }
        if l.hasPrefix("tel:") { return ("전화번호", "전화번호 \(t.dropFirst(4)) 입니다.") }
        if l.hasPrefix("sms:") || l.hasPrefix("mailto:") { return ("연락처", "연락처 QR입니다.") }
        if l.hasPrefix("wifi:") { return ("와이파이", "와이파이 연결 QR입니다.") }
        return ("글", "글이 담긴 QR입니다.")
    }
}

struct QrView: View {
    @ObservedObject private var q = QrEngine.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let c = q.chajeun {
                    Text(q.jigeumMal + " " + c).font(.title3).accessibilityFocused($chojeom)
                    if q.jongryu == "주소" || q.jongryu == "전화번호" || q.jongryu == "연락처", let u = URL(string: c) {
                        Button(q.jongryu == "주소" ? "이 주소 열기" : (q.jongryu == "전화번호" ? "이 번호로 전화 걸기" : "이 연락처 열기")) {
                            UIApplication.shared.open(u)
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    Button("글 복사하기") {
                        UIPasteboard.general.string = c
                        SoriEngine.shared.mal("복사했습니다.", .jeongbo)
                    }
                    .buttonStyle(KeunDanchu())
                    Button("다른 QR 찾기 — 계속") { q.gyesok() }.buttonStyle(KeunDanchu())
                } else if q.kyeojim {
                    Button("QR 찾는 중 — 지금 상태 알려 주기") { q.sangtaeMal() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                }
                if q.kyeojim {
                    Button("QR 찾기 그만 — 카메라 끄기") { q.kkeugi() }.buttonStyle(KeunDanchu())
                } else {
                    Button("QR 찾기 시작 — 카메라 켜기") { q.kyeogi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                }
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("QR은 보통 1~2미터 안에서 카메라가 그쪽을 향해야 잡힙니다. 폰을 가슴 높이로 세워 들고 천천히 지나가시면 안내판과 벽의 QR이 잡힙니다. 잡히면 삐 소리와 진동이 나고, 무엇이 담겼는지와 카메라의 어느 쪽에 있는지를 알려 드립니다. 카메라 사진은 어디로도 보내지 않고 아이폰 안에서만 읽습니다. 화면을 떠나면 카메라는 저절로 꺼집니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("QR 찾기")
        .onAppear {
            q.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onChange(of: q.chajeun) { c in if c != nil { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { chojeom = true } } }
        .onDisappear { q.kkeugi(malHagi: false) }
    }
}

struct DoeView: View {
    @ObservedObject private var d = DoeEngine.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch d.sangtae {
                case .gieok:
                    Button("나갑니다 — 왔던 길 되짚어 나가기") { d.doejipgi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                    Button(d.jul.isEmpty ? "지금 상태 듣기" : d.jul + " 다시 듣기") { d.jigeumMal() }.buttonStyle(KeunDanchu())
                    Button("기억 그만두기") { d.gieokGeuman() }.buttonStyle(KeunDanchu())
                case .annae:
                    Button("지금 무엇을 할지 다시 듣기") { d.jigeumMal() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                    Button("되짚어 나가기 그만") { d.annaeGeuman() }.buttonStyle(KeunDanchu())
                case .swim:
                    if d.gieokItda {
                        Button("되짚어 나가기 — 기억한 \(d.chongGeoreum)걸음, \(d.kkeokSu)번 꺾은 길로 나가기") { d.doejipgi() }
                            .buttonStyle(KeunDanchu())
                            .accessibilityFocused($chojeom)
                        Button("새로 들어갑니다 — 다시 기억하기") { d.gieokSijak() }.buttonStyle(KeunDanchu())
                        Button("기억한 길 지우기") { d.jiugi() }.buttonStyle(KeunDanchu())
                    } else {
                        Button("들어갑니다 — 지금부터 길을 기억하기") { d.gieokSijak() }
                            .buttonStyle(KeunDanchu())
                            .accessibilityFocused($chojeom)
                    }
                }
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("병원 진료실처럼 건물 안에서 들어온 길로 혼자 나오실 때 씁니다. 들어가시기 전에 들어갑니다를 눌러 두시면 걸음 수와 방향으로 길을 기억합니다. 건물 안에서는 위성이 잡히지 않기 때문입니다. 나오실 때 되짚어 나가기를 누르시면, 먼저 어느 쪽으로 돌아 서실지 알려 드리고 몇 걸음 걸으신 뒤 몇 시 방향으로 꺾으실지 차례로 말씀드립니다. 꺾는 곳 다섯 걸음 앞에서 미리 알려 드리고, 열 걸음마다 남은 걸음을, 방향이 크게 어긋나면 돌아 서실 쪽을 알려 드립니다. 걸음은 폰의 걸음 센서로 세므로 폰을 몸에 지니고 평소처럼 걸으십시오. 그래도 어긋날 수 있으니 벽과 손잡이를 함께 짚어 가십시오. 기억한 길은 이 폰에만 남고 다른 사람에게 가지 않으며, 지우실 때까지 남습니다. 앱이 꺼졌다 켜져도 기억하던 중이면 이어 기억합니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("되짚어 나가기")
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true } }
    }
}
