// 상품 바코드 읽기(카메라 눈 묶음 5) — 앱 2.20.0 (빌드 260930-7, 이사장님 승인 "5번부터 차례대로")
// 상품을 카메라 앞에서 천천히 돌리면 바코드를 찾아 "딸깍" 한 번 울리고 상품 이름을 읽어 드립니다.
// 바코드 알아보기는 애플이 폰에 열어 둔 기술(Vision)로 폰 안에서 합니다. 사진은 담지도 보내지도 않습니다.
// 상품 이름은 바코드 번호만 무료 공개 상품 자료(오픈푸드팩츠, 열쇠 없음)에 물어 찾고,
// 못 찾으면 어느 나라 상품인지와 바코드 번호를 읽어 드립니다.
import SwiftUI
import AVFoundation
import Vision
import UIKit

final class SangpumIlgi: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = SangpumIlgi()

    @Published private(set) var kyeojim = false
    @Published private(set) var sangtae = ""
    @Published private(set) var majimak = ""        // 방금 읽은 상품(다시 듣기용)
    @Published private(set) var jinan: [String] = [] // 이번에 읽은 상품들(최근 것이 위)

    private let sesyeon = AVCaptureSession()
    private let jul = DispatchQueue(label: "gilnun.sangpum")
    private var junbiDoem = false
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var ingneun = false
    // 아래는 메인에서만
    private var beonhoT: [String: Date] = [:]   // 같은 바코드 10초 안 되풀이 않음
    private var chatneun = false                // 이름을 찾는 중
    private var saeT = Date()                   // 마지막으로 바코드를 찾은 때
    private var dowumT = Date()                 // 마지막 찾는 법 알림
    private var sigye: Timer?

    // MARK: 켜기·끄기

    func kyeogi() {
        guard !kyeojim else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: sijak()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.sijak() } else { SoriEngine.shared.mal("카메라를 쓸 수 없어 상품 바코드 읽기를 켜지 못했습니다.") }
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
        JipyeSaek.shared.kkeugi(malHagi: false)  // 2.21.0
        BitAlgi.shared.kkeugi(malHagi: false)  // 2.22.0
        Hanmadi.shared.kkeugi(malHagi: false)  // 2.23.0
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
                self.chatneun = false
                self.saeT = Date(); self.dowumT = Date()
                self.sangtae = "바코드를 찾는 중입니다."
                UIApplication.shared.isIdleTimerDisabled = true
                self.mal("상품 바코드 읽기를 시작합니다. 폰에서 한 뼘쯤 떨어뜨려 상품을 천천히 돌려 주십시오.", sseuGi: true)
                Girok.shared.namgi("sangpum", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.salpigi() }
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        guard kyeojim else { return }
        kyeojim = false
        chatneun = false
        sangtae = ""
        UIApplication.shared.isIdleTimerDisabled = false
        if malHagi { SoriEngine.shared.mal("상품 바코드 읽기를 멈췄습니다.", .jeongbo) }
    }

    func dasiDeutgi() {
        if !majimak.isEmpty { SoriEngine.shared.mal(majimak) }
        else { SoriEngine.shared.mal(kyeojim ? "아직 읽은 상품이 없습니다. 바코드를 찾는 중입니다." : "아직 읽은 상품이 없습니다.") }
    }

    /// 20초마다 찾는 법 한 번, 10분 동안 바코드를 못 찾으면 카메라를 끔(배터리)
    private func salpigi() {
        guard kyeojim else { return }
        let now = Date()
        if now.timeIntervalSince(saeT) > 600 {
            kkeugi(malHagi: false)
            mal("10분 동안 바코드를 찾지 못해 카메라를 껐습니다.", sseuGi: true)
            return
        }
        if !chatneun && now.timeIntervalSince(saeT) > 20 && now.timeIntervalSince(dowumT) > 20 {
            dowumT = now
            mal("바코드는 보통 상품 뒷면이나 옆면 아래쪽에 있습니다. 천천히 돌려 주십시오.", .jeongbo)
        }
    }

    private func junbi() -> Bool {
        sesyeon.beginConfiguration()
        defer { sesyeon.commitConfiguration() }
        sesyeon.sessionPreset = .high
        guard let k = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let ip = try? AVCaptureDeviceInput(device: k), sesyeon.canAddInput(ip) else { return false }
        sesyeon.addInput(ip)
        // 가까운 상품에 초점이 잘 맞도록
        if (try? k.lockForConfiguration()) != nil {
            if k.isFocusModeSupported(.continuousAutoFocus) { k.focusMode = .continuousAutoFocus }
            if k.isAutoFocusRangeRestrictionSupported { k.autoFocusRangeRestriction = .near }
            k.unlockForConfiguration()
        }
        let op = AVCaptureVideoDataOutput()
        op.alwaysDiscardsLateVideoFrames = true
        guard sesyeon.canAddOutput(op) else { return false }
        sesyeon.addOutput(op)
        op.setSampleBufferDelegate(self, queue: jul)
        return true
    }

    // MARK: 한 장마다(jul) — 0.3초에 한 번

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.3, !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        let r = VNDetectBarcodesRequest()
        r.symbologies = [.ean13, .ean8, .upce, .code128]
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: .right, options: [:])
        try? h.perform([r])
        guard let o = (r.results ?? []).first(where: { ($0.payloadStringValue ?? "").count >= 6 }),
              let beonho = o.payloadStringValue else { return }
        DispatchQueue.main.async { self.chajeum(beonho) }
    }

    // MARK: 찾음(메인)

    private func chajeum(_ beonho: String) {
        guard kyeojim, !chatneun else { return }
        let now = Date()
        if let t = beonhoT[beonho], now.timeIntervalSince(t) < 10 { return }
        beonhoT[beonho] = now
        saeT = now
        chatneun = true
        SoriEngine.shared.sori(.hwaksin)
        sangtae = "바코드를 찾았습니다. 상품 이름을 찾는 중입니다."
        Girok.shared.namgi("sangpum", ["chajeum": true])
        Task { [weak self] in
            let ireum = await SangpumIlgi.ireumChatgi(beonho)
            await MainActor.run { self?.allida(beonho, ireum) }
        }
    }

    private func allida(_ beonho: String, _ ireum: String?) {
        chatneun = false
        guard kyeojim else { return }
        let m: String
        if let ir = ireum, !ir.isEmpty {
            m = ir + "."
        } else {
            m = SangpumIlgi.nara(beonho) + " 상품 이름은 찾지 못했습니다. 바코드 번호는 " + SangpumIlgi.suja(beonho) + "입니다."
        }
        majimak = m
        jinan.insert(m, at: 0)
        if jinan.count > 20 { jinan.removeLast() }
        sangtae = m
        // 상품 이름은 사람이 기다리는 대답이므로 카메라 눈 말소리를 꺼도 읽어 드림
        SoriEngine.shared.mal(m, .annae)
    }

    // MARK: 상품 이름 찾기 — 바코드 번호만 보냄

    static func ireumChatgi(_ beonho: String) async -> String? {
        guard beonho.allSatisfy({ $0.isNumber }),
              let u = URL(string: "https://world.openfoodfacts.org/api/v2/product/\(beonho).json?fields=product_name_ko,product_name,brands,quantity") else { return nil }
        var rq = URLRequest(url: u, timeoutInterval: 6)
        rq.setValue("Gilnun/2.20 (kr.or.ada.app)", forHTTPHeaderField: "User-Agent")
        guard let dr = try? await URLSession.shared.data(for: rq),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200,
              let j = try? JSONSerialization.jsonObject(with: dr.0) as? [String: Any],
              let p = j["product"] as? [String: Any] else { return nil }
        func g(_ k: String) -> String { ((p[k] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
        let ireum = g("product_name_ko").isEmpty ? g("product_name") : g("product_name_ko")
        guard !ireum.isEmpty else { return nil }
        var s = ""
        let br = g("brands").components(separatedBy: ",").first?.trimmingCharacters(in: .whitespaces) ?? ""
        if !br.isEmpty && !ireum.contains(br) { s += br + ", " }
        s += ireum
        let yang = g("quantity")
        if !yang.isEmpty { s += ", " + yang }
        return s
    }

    /// 바코드 앞자리로 어느 나라에서 번호를 받은 상품인지
    static func nara(_ b: String) -> String {
        guard b.count == 13, let a3 = Int(b.prefix(3)) else { return "" }
        switch a3 {
        case 880: return "우리나라 상품입니다."
        case 450...459, 490...499: return "일본 상품입니다."
        case 690...699: return "중국 상품입니다."
        case 0...139: return "미국이나 캐나다 상품입니다."
        case 400...440: return "독일 상품입니다."
        case 300...379: return "프랑스 상품입니다."
        case 471: return "대만 상품입니다."
        case 885: return "태국 상품입니다."
        case 893: return "베트남 상품입니다."
        default: return ""
        }
    }

    /// 번호를 한 자씩 또박또박(영은 공으로)
    static func suja(_ b: String) -> String {
        let ir: [Character: String] = ["0": "공", "1": "일", "2": "이", "3": "삼", "4": "사",
                                       "5": "오", "6": "육", "7": "칠", "8": "팔", "9": "구"]
        return b.map { ir[$0] ?? String($0) }.joined(separator: " ")
    }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi { return }   // 카메라 눈 말소리 끔 — 찾는 법 알림은 말하지 않음
        SoriEngine.shared.mal(t, g)
    }
}

// MARK: 화면 — 둘러보기 탭

struct SangpumIlgiView: View {
    @ObservedObject private var g = SangpumIlgi.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(g.kyeojim ? "멈추기 — 바코드를 찾는 중" : "이어 찾기 — 카메라를 켜고 바코드 읽기") {
                    if g.kyeojim { g.kkeugi() } else { g.kyeogi() }
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
                Button("방금 상품 다시 듣기") { g.dasiDeutgi() }
                    .buttonStyle(KeunDanchu())
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                if !g.jinan.isEmpty {
                    DisclosureGroup("이번에 읽은 상품 펼치기") {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(Array(g.jinan.enumerated()), id: \.offset) { _, t in
                                Text(t).font(.body)
                            }
                        }
                    }
                    .font(.title3)
                }
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("화면을 여시면 바로 시작합니다. 폰을 한 손에 들고, 상품을 폰 뒤쪽 카메라에서 한 뼘쯤 떨어뜨려 천천히 돌려 주십시오. 바코드를 찾으면 확신음이 한 번 울리고 상품 이름을 읽어 드립니다. 같은 상품은 10초 안에 되풀이해 읽지 않습니다. 이름을 찾지 못하면 어느 나라 상품인지와 바코드 번호를 읽어 드립니다. 바코드는 보통 상품 뒷면이나 옆면 아래쪽에 있습니다. 20초 동안 찾지 못하면 찾는 법을 한 번 알려 드립니다. 카메라 눈 말소리를 끄시면 찾는 법 알림은 하지 않고, 상품 이름만 읽어 드립니다. 방금 상품 다시 듣기를 누르시면 마지막으로 읽은 상품을 다시 들려 드리고, 이번에 읽은 상품 펼치기에서 앞서 읽은 것들을 보실 수 있습니다. 10분 동안 바코드를 찾지 못하거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 바코드 읽어 줘, 이 상품 뭐야라고 하셔도 열립니다. 바코드 알아보기는 폰 안에서 하고 사진은 담지도 보내지도 않습니다. 상품 이름을 찾을 때만 바코드 번호를 무료 공개 상품 자료에 물어봅니다. 공개 자료에 없는 우리나라 상품은 이름이 나오지 않을 수 있습니다.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("상품 바코드 읽기")
        .onAppear {
            g.kyeogi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
        .onDisappear { g.kkeugi(malHagi: false) }
    }
}
