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

    // MARK: 2.128.0 찾는 글자로 길잡기 (261109-I82, 이사장님 승인 2026-11-09 「1」)
    // 「105호 찾아 줘」처럼 찾는 글자를 정하면 보이는 글 가운데 그 글자만 찾아 시계 방향으로 알리고,
    // 가운데(12시)에 오면 떨림과 확신음으로 알림. 문패·호수·화장실 표지로 문을 찾는 셈이라 거리 센서(라이다) 없는 폰에서도 됨.
    // 폰 안에서만 돌고 사진은 어디로도 보내지 않음.

    /// 찾는 글자(화면·말에 쓰는 이름) — nil 이면 보이는 글자를 모두 읽음
    @Published private(set) var chatneun: String? = nil
    /// 켤 때 바로 찾기로 들어갈 것(말로 하기·문 찾기에서 넘김)
    private var daeumChat: (String, [String], Bool)? = nil
    private var chatKeys: [String] = []       // 맞출 낱말(ttuk), jul 에서도 읽음 — 바꿀 때는 jul.sync
    private var munEseo = false               // 문 찾기·도착에서 넘어온 찾기면 정면 가까이 닿은 뒤 내 문에 글자를 담음
    private var chatSijakT = Date()
    private var chatChajeumT = Date.distantPast   // 처음 찾은 때
    private var chatBoinT = Date.distantPast      // 마지막으로 본 때
    private var chatMalT = Date.distantPast
    private var chatJeongmyeonT = Date.distantPast
    private var chatAnnaeSu = 0
    private var chatJigeumSi = 0
    private var chatNochim = false
    private var chatDamgiHam = false
    private var chatApBoim = false            // jul 에서만 — 바로 앞 장에서도 보였는가

    /// 말로 하기·문 찾기에서 — 찾는 글자로 켬(이미 켜져 있으면 바로 바꿈)
    func chatgiSijak(_ ireum: String, _ keys: [String], munEseo me: Bool = false) {
        let ks = keys.map { GeulIlgi.ttuk($0) }.filter { !$0.isEmpty }
        guard !ks.isEmpty else { return }
        if kyeojim { chatgiNeoki(ireum, ks, me); return }
        daeumChat = (ireum, ks, me)
        kyeogi()
    }

    /// 찾기를 그만두고 보이는 글자를 모두 읽는 처음 방식으로
    func chatgiKkeugi(malHagi: Bool = true) {
        guard chatneun != nil else { return }
        let ir = chatneun ?? ""
        Girok.shared.namgi("geul_chatgi_kkeut", ["mok": ir, "chajeum": chatChajeumT != Date.distantPast, "cho": Int(Date().timeIntervalSince(chatSijakT))])
        jul.sync { self.chatKeys = []; self.chatApBoim = false }
        chatneun = nil
        if malHagi { mal("\(ir) 찾기를 그만두고 보이는 글자를 모두 읽습니다.", .jeongbo, sseuGi: true) }
    }

    private func chatgiNeoki(_ ireum: String, _ ks: [String], _ me: Bool) {
        jul.sync { self.chatKeys = ks; self.chatApBoim = false }
        chatneun = ireum
        munEseo = me
        chatSijakT = Date(); chatChajeumT = .distantPast; chatBoinT = .distantPast; chatMalT = .distantPast
        chatJeongmyeonT = .distantPast; chatAnnaeSu = 0; chatJigeumSi = 0; chatNochim = false; chatDamgiHam = false
        saeT = Date()
        mal("\(ireum)을 찾습니다. 폰을 가슴 앞에 세워 들고 천천히 9시에서 3시 쪽으로 비춰 주십시오. 보이면 시계 방향으로 알려 드립니다.", sseuGi: true)
        Girok.shared.namgi("geul_chatgi", ["mok": ireum, "munEseo": me])
    }

    /// 찾는 글자를 본 장마다(메인) — si: 시계 방향(10~2), keugi: 글 높이(화면 비율)
    private func chatBoda(_ si: Int?, _ keugi: Double, _ boinGeul: String) {
        guard kyeojim, let ir = chatneun else { return }
        let now = Date()
        guard let si = si else {
            // 못 봄 — 찾은 뒤 8초 동안 안 보이면 한 번 알림, 아직 못 찾았으면 15초마다 한 번
            if chatChajeumT != .distantPast {
                if !chatNochim && now.timeIntervalSince(chatBoinT) > 8 {
                    chatNochim = true
                    mal("\(ir)이 카메라에서 벗어났습니다. 폰을 천천히 9시에서 3시 쪽으로 다시 비춰 주십시오.")
                }
            } else if now.timeIntervalSince(chatSijakT) > Double(15 * (chatAnnaeSu + 1)) {
                chatAnnaeSu += 1
                mal("아직 \(ir)을 찾는 중입니다. 폰을 조금 높이거나 낮추어 천천히 비춰 주십시오.")
            }
            return
        }
        saeT = now
        chatBoinT = now
        let cheoeum = chatChajeumT == .distantPast
        let gakkai = keugi > 0.12 ? " 아주 가까이" : (keugi > 0.06 ? " 가까이" : "")
        if cheoeum {
            chatChajeumT = now
            SoriEngine.shared.sori(.hwaksin)
            Jindong.hagi("short")
            mal("찾았습니다. \(si)시 방향\(gakkai)에 \(boinGeul).", sseuGi: true)
            Girok.shared.namgi("geul_chatgi_chajeum", ["mok": ir, "cho": Int(now.timeIntervalSince(chatSijakT))])
            chatMalT = now; chatJigeumSi = si; chatNochim = false
            return
        }
        if chatNochim { chatNochim = false; mal("다시 보입니다. \(si)시 방향\(gakkai)."); chatMalT = now; chatJigeumSi = si; return }
        if si == 12 {
            if now.timeIntervalSince(chatJeongmyeonT) >= 3 {
                chatJeongmyeonT = now
                Jindong.hagi("short")
                SoriEngine.shared.sori(.hwaksin)
                if chatJigeumSi != 12 || now.timeIntervalSince(chatMalT) >= 6 { mal("정면\(gakkai)."); chatMalT = now }
            }
            chatJigeumSi = 12
            // 문 찾기에서 넘어온 찾기 — 정면 아주 가까이면 두 걸음 지난 뒤 내 문에 이 글자를 담음
            if munEseo && keugi > 0.12 && !chatDamgiHam { chatDamgiHam = true; munDamgi(boinGeul) }
            return
        }
        if (si != chatJigeumSi && now.timeIntervalSince(chatMalT) >= 2) || now.timeIntervalSince(chatMalT) >= 6 {
            mal("\(si)시 방향\(gakkai).")
            chatMalT = now; chatJigeumSi = si
        }
    }

    /// 문패 글자로 문 앞에 닿은 뒤 두 걸음 지나시면 그 자리를 내 문에 담음(문 찾기의 손대지 않고 두 번 찍기와 같은 방식)
    private func munDamgi(_ geul: String) {
        guard let w = WichiEngine.shared.jigeum, Date().timeIntervalSince(w.ttae) < 30 else { return }
        let head = WichiEngine.shared.nachimban
        let gijun = WichiEngine.shared.georeumSu
        var n = 0
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] t in
            n += 1
            guard let self = self else { t.invalidate(); return }
            if n > 25 { t.invalidate(); return }
            guard WichiEngine.shared.georeumSu - gijun >= 2 else { return }
            t.invalidate()
            guard w.ochae <= 20 else { return }
            let m = NaeMun.shared.jjakDamgi(ireum: "", lat: w.lat, lon: w.lon, bang: head >= 0 ? head : nil, geul: [geul])
            self.mal("문을 지나셨습니다. " + m, .jeongbo, sseuGi: true)
            Girok.shared.namgi("geul_chatgi_mundamgi", ["mok": geul])
        }
    }

    /// 말에서 찾는 글자 뽑기 — z 는 띄어쓰기를 뺀 말. (이름, 맞출 낱말들) 또는 nil
    /// 「105호 찾아 줘」「B105호 어디야」「7번 출구 표지 찾아 줘」「화장실 표지 찾아 줘」「카메라로 엘리베이터 찾아 줘」「약국 글자 찾아 줘」
    static func chatneunMal(_ z: String) -> (String, [String])? {
        let u = z.uppercased()
        guard u.contains("찾") || u.contains("어디") else { return nil }
        let kamera = u.contains("표지") || u.contains("카메라") || u.contains("글자") || u.contains("글씨") || u.contains("간판") || u.contains("문패")
        // 호수 — 「105호」「B105호」「지하105호」
        if let r = u.range(of: "(B|지하)?[0-9]{1,4}호", options: .regularExpression), !u.contains("역"), !u.contains("호선"), !u.contains("호차"), !u.contains("호기") {
            var s = String(u[r]).replacingOccurrences(of: "호", with: "")
            if s.hasPrefix("지하") { s = "B" + String(s.dropFirst(2)) }
            let su = s.replacingOccurrences(of: "B", with: "")
            return ("\(s)호", [s + "호", s, su + "호"].filter { $0.count >= 2 })
        }
        guard kamera else { return nil }
        if let r = u.range(of: "[0-9]{1,2}번출구", options: .regularExpression), !u.contains("역") {
            let n = String(u[r]).replacingOccurrences(of: "번출구", with: "")
            return ("\(n)번 출구 표지", ["\(n)번출구", "출구\(n)", "EXIT\(n)", "\(n)EXIT", "\(n)번"])
        }
        if u.contains("화장실") { return ("화장실 표지", ["화장실", "TOILET", "RESTROOM", "WC"]) }
        if u.contains("엘리베이터") || u.contains("승강기") { return ("엘리베이터 표지", ["엘리베이터", "승강기", "ELEVATOR", "LIFT"]) }
        if u.contains("비상구") { return ("비상구 표지", ["비상구", "EXIT"]) }
        if u.contains("계단") { return ("계단 표지", ["계단", "STAIRS"]) }
        // 「약국 글자 찾아 줘」 — 앞말을 그대로
        for kk in ["글자찾", "글씨찾", "간판찾", "표지찾", "문패찾"] {
            if let r = u.range(of: kk) {
                var ap = String(u[..<r.lowerBound])
                for x in ["카메라로", "카메라", "이라는", "라는", "란", "이", "가"] where ap.hasSuffix(x) && ap.count > x.count { ap = String(ap.dropLast(x.count)) }
                ap = ap.replacingOccurrences(of: "카메라로", with: "")
                if ap.count >= 1 && ap.count <= 12 { return (ap, [ap]) }
            }
        }
        return nil
    }

    // MARK: 켜기·끄기

    /// 2.25.0 차 안 간판 알림이 켤 때 — 시작 말 없이 창밖 간판을 읽음
    private var chaAnJung = false
    func kyeogiChaAn() {
        guard !kyeojim else { return }
        // 2.104.0 (261024-I58) 긴급통화가 카메라를 쓰는 동안에는 켜지 않음(통화 영상을 빼앗지 않게, 안드로이드와 같음)
        if GinGeup.shared.kameraSseuneunJung { SoriEngine.shared.mal("긴급통화 중에는 카메라 눈을 쓸 수 없습니다."); return }
        chaAnJung = true
        kyeogi()
    }

    private var sijakJung = false   // 2.128.0 켜는 중에 화면이 또 켜지 않게
    func kyeogi() {
        guard !kyeojim, !sijakJung else { return }
        // 2.104.0 (261024-I58) 긴급통화가 카메라를 쓰는 동안에는 켜지 않음(통화 영상을 빼앗지 않게, 안드로이드와 같음)
        if GinGeup.shared.kameraSseuneunJung { SoriEngine.shared.mal("긴급통화 중에는 카메라 눈을 쓸 수 없습니다."); return }
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
        BitAlgi.shared.kkeugi(malHagi: false)  // 2.22.0
        Hanmadi.shared.kkeugi(malHagi: false)  // 2.23.0
        TeokAllim.shared.kkeugi(malHagi: false)  // 2.24.0
        sijakJung = true
        jul.async {
            if !self.junbiDoem {
                guard self.junbi() else {
                    DispatchQueue.main.async { self.sijakJung = false; SoriEngine.shared.mal("카메라를 열지 못했습니다.") }
                    return
                }
                self.junbiDoem = true
            }
            self.apJul = []
            if !self.sesyeon.isRunning { self.sesyeon.startRunning() }
            DispatchQueue.main.async {
                self.sijakJung = false
                self.kyeojim = true
                self.saeT = Date()
                UIApplication.shared.isIdleTimerDisabled = true
                if !self.chaAnJung && self.daeumChat == nil { self.mal("글자 읽기를 시작합니다. 폰을 글자 쪽으로 세워 들고 천천히 움직여 주십시오.", sseuGi: true) }
                self.chaAnJung = false
                Girok.shared.namgi("geulilgi", ["kyeogi": true])
                self.sigye?.invalidate()
                self.sigye = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.salpigi() }
                if let dc = self.daeumChat { self.daeumChat = nil; self.chatgiNeoki(dc.0, dc.1, dc.2) }   // 2.128.0 찾는 글자로 바로
            }
        }
    }

    func kkeugi(malHagi: Bool = true) {
        jul.async { if self.sesyeon.isRunning { self.sesyeon.stopRunning() } }
        sigye?.invalidate(); sigye = nil
        guard kyeojim else { return }
        if chatneun != nil { chatgiKkeugi(malHagi: false) }   // 2.128.0
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
        let ir = chatneun
        kkeugi(malHagi: false)
        if let ir = ir { mal("3분 동안 \(ir)을 찾지 못해 카메라를 껐습니다.", sseuGi: true); return }   // 2.128.0
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
        // 2.128.0 찾는 글자가 있으면 0.5초마다, 그 글자만
        let ks = chatKeys
        guard now.timeIntervalSince(boT) >= (ks.isEmpty ? 1 : 0.5), !ingneun, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        boT = now
        ingneun = true
        defer { ingneun = false }
        if !ks.isEmpty {
            let hang = GeulIlgi.ilgiCheol(px, .right, ks.contains { $0.range(of: "[0-9]", options: .regularExpression) != nil } ? .accurate : .fast)
            let maj = hang.filter { h in let t = GeulIlgi.ttuk(h.0); return ks.contains { t.contains($0) } }
                          .max { $0.1.height < $1.1.height }
            if let m = maj, chatApBoim {
                let x = m.1.midX
                let si = x < 0.2 ? 10 : (x < 0.4 ? 11 : (x < 0.6 ? 12 : (x < 0.8 ? 1 : 2)))
                let k = Double(m.1.height), g = m.0
                DispatchQueue.main.async { self.chatBoda(si, k, g) }
            } else if maj == nil {
                DispatchQueue.main.async { self.chatBoda(nil, 0, "") }
            }
            chatApBoim = (maj != nil)
            return
        }
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
            if b.minX < 0.015 { jallim = "9시 쪽"; break }   // 2.53.0 시계 방향으로
            if b.maxX > 0.985 { jallim = "3시 쪽"; break }
            if b.maxY > 0.985 { jallim = "12시 쪽"; break }
            if b.minY < 0.015 { jallim = "6시 쪽"; break }
        }
        return (hang.map { $0.0 }, jallim)
    }

    static func ttuk(_ s: String) -> String { s.replacingOccurrences(of: " ", with: "").uppercased() }

    /// 2.128.0 찾는 글자용 — 줄과 자리(화면 비율, 왼쪽 아래가 0)
    static func ilgiCheol(_ px: CVPixelBuffer, _ bang: CGImagePropertyOrientation, _ level: VNRequestTextRecognitionLevel) -> [(String, CGRect)] {
        let r = VNRecognizeTextRequest()
        r.recognitionLevel = level
        r.recognitionLanguages = ["ko-KR", "en-US"]
        r.usesLanguageCorrection = false
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: bang, options: [:])
        try? h.perform([r])
        var hang: [(String, CGRect)] = []
        for x in r.results ?? [] {
            guard let c = x.topCandidates(1).first, c.confidence >= 0.3 else { continue }
            let t = c.string.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !t.isEmpty else { continue }
            hang.append((t, x.boundingBox))
        }
        return hang
    }
}

// MARK: 화면 — 둘러보기 탭

struct GeulIlgiView: View {
    @ObservedObject private var g = GeulIlgi.shared
    @ObservedObject private var s = Seoljeong.shared
    @State private var mokPyeol = false   // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16)
    @AccessibilityFocusState private var chojeom: Bool
    @State private var chatGeul = ""   // 2.128.0

    /// 2.128.0 칸에 적은 글자로 찾기 — 「105호」면 105호·105 둘 다로
    private func chatgi() {
        let t = chatGeul.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { SoriEngine.shared.mal("찾을 글자를 먼저 적어 주십시오."); return }
        if let r = GeulIlgi.chatneunMal(t.replacingOccurrences(of: " ", with: "") + "찾"), !r.1.isEmpty { g.chatgiSijak(r.0, r.1) }
        else { g.chatgiSijak(t, [t]) }
        chatGeul = ""
    }

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
                // 2.128.0 찾는 글자로 길잡기
                if let c = g.chatneun {
                    Button("\(c) 찾기 그만 — 보이는 글자 모두 읽기로") { g.chatgiKkeugi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    TextField("찾을 글자(보기: 105호, 약국)", text: $chatGeul)
                        .font(.title3)
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.search)
                        .onSubmit { chatgi() }
                    Button("이 글자 찾기") { chatgi() }
                        .buttonStyle(KeunDanchu())
                    Button("화장실 표지 찾기") { g.chatgiSijak("화장실 표지", ["화장실", "TOILET", "RESTROOM", "WC"]) }
                        .buttonStyle(KeunDanchu())
                    Button("엘리베이터 표지 찾기") { g.chatgiSijak("엘리베이터 표지", ["엘리베이터", "승강기", "ELEVATOR", "LIFT"]) }
                        .buttonStyle(KeunDanchu())
                }
                // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 펼치기 이름은 「읽은 글 펼치기」로 고정(읽는 동안 줄 수가 바뀌어 단추 이름이 흔들리던 것) — 펼칠 때 줄 수를 한 번 알림
                DisclosureGroup("읽은 글 펼치기", isExpanded: $mokPyeol) {
                    Mokrok5(g.ilgeun) { j in Text(j.mal).font(.title3) }
                }
                .font(.title3)
                .onChange(of: mokPyeol) { p in
                    if p { SoriEngine.shared.mal(g.ilgeun.isEmpty ? "아직 읽은 글이 없습니다." : "읽은 글 \(g.ilgeun.count)줄입니다.") }
                }
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("사진을 찍지 않아도 카메라를 대고 있으면 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다. 화면을 여시면 바로 읽기 시작합니다. 폰을 글자 쪽으로 세워 들고 천천히 움직이십시오. 한 번 읽은 줄은 30초 안에 되풀이하지 않고 새로 보인 글만 읽습니다. 글자가 화면 끝에 걸려 잘리면 폰을 어느 쪽으로 옮기실지 알려 드립니다. 읽은 글은 읽은 글 펼치기에 다섯 줄씩 남습니다. 카메라 눈 말소리를 끄시면 말 없이 화면 글자로만 남습니다. 3분 동안 새 글이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 글자 읽어 줘라고 하셔도 열립니다. 찾을 글자 칸에 105호나 약국처럼 적고 이 글자 찾기를 누르시거나, 말로 하기에서 105호 찾아 줘, 화장실 표지 찾아 줘, 7번 출구 표지 찾아 줘라고 하시면 그 글자만 찾습니다. 보이면 1시 방향처럼 시계 방향으로 알리고, 가운데에 오면 떨림과 확신음으로 정면이라고 알립니다. 글자가 크게 보일수록 가까이라고 덧붙입니다. 문패 글자로 문 앞에 닿으신 뒤 두 걸음 지나시면 그 자리를 내 문에 담아 다음에는 찍어 두신 문인지 가려 드립니다. 사진과 글은 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.")
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
