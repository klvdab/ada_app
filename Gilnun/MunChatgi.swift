// 문 찾기(카메라 눈 묶음 1) — 앱 2.15.0 (빌드 260930-1, 이사장님 승인 1)
// 애플이 폰에 열어 둔 "벽·문·창 알아보기"(ARKit 평면 분류, 라이다 폰은 공간 그물 분류까지)로 문을 찾아
// 시계 방향과 걸음 수로 알립니다. 인터넷 없이 폰 안에서만 돌고, 남의 인공지능 모델은 쓰지 않습니다.
// 특허 확인(2026-09-29): 여러 감지기를 합친 실시간 지도·길 고르기, 사람·상황에 따라 기능을 스스로 고르기,
// 지팡이·안경 묶음은 넣지 않음. 문을 알아보는 일은 애플 기능이 맡음.
// 켜지는 때: ① 점지도 문까지 안내에서 문 10미터 안 ② 걸어서 목적지에 닿았을 때 ③ 문 찾기 화면, 말로 "문 찾아 줘"
// 2.16.0 바로잡음(이사장님 지시): 음악 열쇠 제한 없이 모든 폰에서 씀. 기종이 모자라면 그렇다고만 알림.
// 두 번 찍기와 합침(이사장님 2): ① 내 문을 두 번 찍을 때 카메라가 문 둘레 글자를 함께 담음 ② 찾아갈 때 그 글자로
// "찍어 두신 문"인지, 옆 문인지 가림 ③ 문 바로 앞을 알린 뒤 두 걸음 지나시면 손대지 않고 두 번 찍은 것으로 담음.
import SwiftUI
import ARKit
import AVFoundation
import Vision

final class MunChatgi: NSObject, ObservableObject, ARSessionDelegate {
    static let shared = MunChatgi()

    @Published private(set) var kyeojim = false
    @Published private(set) var munBoim = false
    @Published private(set) var sangtae = ""

    /// 이 폰이 문 알아보기를 할 수 있는지(애플 평면 분류)
    static var gigiGaneung: Bool { ARWorldTrackingConfiguration.isSupported && ARPlaneAnchor.isClassificationSupported }
    static var lidar: Bool { ARWorldTrackingConfiguration.supportsSceneReconstruction(.meshWithClassification) }

    private let session = ARSession()
    private let jul = DispatchQueue(label: "gilnun.munchatgi")
    // 아래는 jul 에서만
    private var boT = Date.distantPast
    private var geulT = Date.distantPast
    private var ingneun = false
    // 아래는 메인에서만
    private var sijakT = Date()
    private var malT = Date.distantPast
    private var majimakSi = 0
    private var majimakGeoreum = 0
    private var gakkaum: Float = 99
    private var chatneunMalSu = 0
    private var boinT = Date.distantPast
    private var ireotdaMal = false
    private var ilgeunGeul = Set<String>()
    private var geulSun: [String] = []      // 읽은 차례대로
    private var eodiseo = ""
    private var majatda = false
    /// 찾아가는 문의 이름과 담아 둔 글자 — 있으면 읽은 글자와 견줌
    var gidae: (String, [String])?
    /// 두 번 찍기 중(말 없이 글자만 모음) — jul 에서도 읽음
    private var jjikgiNow = false

    override init() {
        super.init()
        session.delegate = self
        session.delegateQueue = jul
    }

    // MARK: 켜기·끄기

    /// 켜기 — eodiseo: "hwamyeon" 문 찾기 화면, "malhagi" 말로, "munkkaji" 점지도 문까지, "dochak" 걸어서 도착
    func kyeogi(_ eodiseo: String) {
        guard !kyeojim else { return }
        guard MunChatgi.gigiGaneung else {
            mal("이 폰은 카메라로 문을 알아보는 기능을 지원하지 않습니다.")
            return
        }
        AVCaptureDevice.requestAccess(for: .video) { ok in
            DispatchQueue.main.async {
                guard ok else {
                    self.mal("카메라 허락이 꺼져 있어 문을 찾을 수 없습니다. 아이폰 설정의 길눈에서 카메라를 켜 주십시오.")
                    return
                }
                self.sijak(eodiseo)
            }
        }
    }

    private func sijak(_ e: String) {
        eodiseo = e
        GeulIlgi.shared.kkeugi(malHagi: false)   // 2.16.0 카메라는 한 곳만
        GarikiIlgi.shared.kkeugi(malHagi: false)
        SaramGamji.shared.kkeugi(malHagi: false)   // 2.18.0
        SangpumIlgi.shared.kkeugi(malHagi: false)  // 2.20.0
        JipyeSaek.shared.kkeugi(malHagi: false)  // 2.21.0
        BitAlgi.shared.kkeugi(malHagi: false)  // 2.22.0
        Hanmadi.shared.kkeugi(malHagi: false)  // 2.23.0
        TeokAllim.shared.kkeugi(malHagi: false)  // 2.24.0
        QrEngine.shared.kkeugi(malHagi: false)
        jjikgiNow = (e == "jjikgi")
        if gidae == nil && !jjikgiNow, let w = WichiEngine.shared.jigeum, let h = NaeMun.shared.geulMun(w.lat, w.lon, 40) {
            gidae = (h.ireum, h.geul ?? [])
        }
        let c = ARWorldTrackingConfiguration()
        c.planeDetection = [.vertical]
        if MunChatgi.lidar { c.sceneReconstruction = .meshWithClassification }
        session.run(c, options: [.resetTracking, .removeExistingAnchors])
        kyeojim = true
        munBoim = false
        sijakT = Date()
        malT = Date.distantPast
        majimakSi = 0; majimakGeoreum = 0; gakkaum = 99
        chatneunMalSu = 0; ireotdaMal = false; ilgeunGeul = []; geulSun = []; majatda = false
        sangtae = "문을 찾는 중입니다."
        Girok.shared.namgi("munchatgi", ["eodi": e, "lidar": MunChatgi.lidar])
        if jjikgiNow { return }   // 두 번 찍기 — 말 없이 글자만 모음
        let ap = (e == "munkkaji" || e == "dochak") ? "카메라로 문을 찾습니다. " : ""
        mal(ap + "폰을 가슴 앞에 세워 들고 천천히 걸으십시오.")
    }

    func kkeugi(malHagi: Bool = true) {
        guard kyeojim else { return }
        session.pause()
        kyeojim = false
        munBoim = false
        sangtae = ""
        gidae = nil
        jjikgiNow = false
        if malHagi { mal("문 찾기를 마칩니다.") }
    }

    /// 두 번 찍기 — 첫 번째 찍을 때 켜 두었던 카메라가 읽은 글자를 넘기고 끔
    func jjikgiKkeut() -> [String] {
        let g = geulSun
        if kyeojim && eodiseo == "jjikgi" { kkeugi(malHagi: false) }
        return g
    }

    // MARK: 카메라 한 장마다(jul) — 0.5초에 한 번만 살핌

    func session(_ s: ARSession, didUpdate frame: ARFrame) {
        let now = Date()
        guard now.timeIntervalSince(boT) >= 0.5 else { return }
        boT = now
        let cam = frame.camera.transform
        let pos = SIMD3<Float>(cam.columns.3.x, cam.columns.3.y, cam.columns.3.z)
        let ap = -SIMD3<Float>(cam.columns.2.x, cam.columns.2.y, cam.columns.2.z)   // 카메라가 보는 쪽
        var hubo: [SIMD3<Float>] = []
        for a in frame.anchors {
            if let p = a as? ARPlaneAnchor, p.classification == .door {
                let w = p.transform * SIMD4<Float>(p.center.x, p.center.y, p.center.z, 1)
                hubo.append(SIMD3<Float>(w.x, w.y, w.z))
            } else if let m = a as? ARMeshAnchor, let j = MunChatgi.munJungsim(m) {
                hubo.append(j)
            }
        }
        // 가장 가까운 문
        var best: (Float, Double)?
        for h in hubo {
            let dx = h.x - pos.x, dz = h.z - pos.z
            let d = (dx * dx + dz * dz).squareRoot()
            let apH = SIMD2<Float>(ap.x, ap.z), toH = SIMD2<Float>(dx, dz)
            guard simd_length(apH) > 0.1, d > 0.05 else { continue }
            let a1 = simd_normalize(apH), b1 = simd_normalize(toH)
            let cross = a1.x * b1.y - a1.y * b1.x     // 오른쪽이면 양수(위에서 내려다본 x·z)
            let dot = a1.x * b1.x + a1.y * b1.y
            let gak = Double(atan2(cross, dot)) * 180 / .pi
            if best == nil || d < best!.0 { best = (d, gak) }
        }
        // 문 둘레 글자 — 문이 보일 때 3초에 한 번
        var geul: [String] = []
        if best != nil || jjikgiNow, now.timeIntervalSince(geulT) >= (jjikgiNow ? 1.5 : 3), !ingneun {
            geulT = now
            ingneun = true
            geul = MunChatgi.geulIlgi(frame.capturedImage)
            ingneun = false
        }
        DispatchQueue.main.async { self.boda(best, geul) }
    }

    func session(_ s: ARSession, didFailWithError error: Error) {
        DispatchQueue.main.async {
            guard self.kyeojim else { return }
            self.kkeugi(malHagi: false)
            self.mal("카메라를 쓰지 못해 문 찾기를 멈췄습니다.")
        }
    }

    // MARK: 알리기(메인)

    private func boda(_ best: (Float, Double)?, _ geul: [String]) {
        guard kyeojim else { return }
        let now = Date()
        let el = now.timeIntervalSince(sijakT)
        if eodiseo == "jjikgi" {   // 두 번 찍기 — 글자만 모으고 말하지 않음
            for g in geul where !ilgeunGeul.contains(g) { ilgeunGeul.insert(g); geulSun.append(g) }
            if el > 90 { kkeugi(malHagi: false) }
            return
        }
        guard let b = best else {
            if munBoim && now.timeIntervalSince(boinT) > 6 && !ireotdaMal {
                ireotdaMal = true
                munBoim = false
                sangtae = "문을 다시 찾는 중입니다."
                mal("문이 카메라에서 벗어났습니다. 폰을 천천히 좌우로 돌려 주십시오.")
            }
            if !munBoim && ((chatneunMalSu == 0 && el > 10) || (chatneunMalSu == 1 && el > 35)) {
                chatneunMalSu += 1
                mal("문을 찾고 있습니다. 폰을 가슴 앞에 세워 들고 천천히 좌우로 돌려 주십시오.")
            }
            if el > 90 {
                kkeugi(malHagi: false)
                mal("문을 찾지 못해 카메라를 끕니다.")
            }
            return
        }
        let d = b.0, gak = b.1
        if el > 180 {   // 문은 보았으나 3분 넘게 닿지 못함 — 카메라를 오래 켜 두지 않음
            kkeugi(malHagi: false)
            mal("문 찾기를 오래 켜 두어 카메라를 끕니다. 다시 쓰시려면 문 찾아 줘라고 말씀하십시오.")
            return
        }
        boinT = now
        ireotdaMal = false
        let bopok = Seoljeong.shared.bopok > 0.2 ? Seoljeong.shared.bopok : 0.65
        let georeum = max(1, Int((Double(d) / bopok).rounded()))
        let si = MunChatgi.sigye(gak)
        if d <= 0.9 {
            sangtae = "문 바로 앞입니다."
            SoriEngine.shared.sori(.dochak)
            Jindong.dochak()
            mal("문 바로 앞입니다. \(si)시 방향입니다.", .annae, sseuGi: true)
            Girok.shared.namgi("munchatgi_dochak", ["eodi": eodiseo, "cho": Int(el)])
            session.pause()
            kyeojim = false
            munBoim = false
            gidae = nil
            jadongJjikgi(geulSun)
            return
        }
        let cheoeum = !munBoim
        munBoim = true
        if d < gakkaum - 0.5 { gakkaum = d; if !cheoeum { SoriEngine.shared.sori(.hwaksin) } }
        let mm = "\(si)시 방향 문, \(georeum)걸음"
        sangtae = mm
        if cheoeum {
            SoriEngine.shared.sori(.hwaksin)
            mal("문이 보입니다. " + mm + "입니다.")
            malT = now; majimakSi = si; majimakGeoreum = georeum
        } else if now.timeIntervalSince(malT) >= 3 && (si != majimakSi || georeum != majimakGeoreum) {
            mal(mm + ".")
            malT = now; majimakSi = si; majimakGeoreum = georeum
        }
        let sae = geul.filter { !ilgeunGeul.contains($0) }
        if !sae.isEmpty {
            for g in sae { ilgeunGeul.insert(g); geulSun.append(g) }
            geulMalhagi(sae)
        }
    }

    /// 읽은 글자 알리기 — 찍어 두신 문의 글자가 있으면 견주어 맞는 문인지, 옆 문인지
    private func geulMalhagi(_ sae: [String]) {
        guard let gd = gidae, !gd.1.isEmpty, !majatda else {
            mal("문 둘레 글자, " + sae.joined(separator: ", ") + ".", .jeongbo)
            return
        }
        let gz = gd.1.map { MunChatgi.ttuk($0) }.filter { !$0.isEmpty }
        let matda = sae.contains { s in
            let t = MunChatgi.ttuk(s)
            return gz.contains { $0 == t || ($0.count >= 2 && t.count >= 2 && ($0.contains(t) || t.contains($0))) }
        }
        let sutja = { (x: String) in x.range(of: "[0-9]", options: .regularExpression) != nil }
        if matda {
            majatda = true
            SoriEngine.shared.sori(.doraom)
            mal("찍어 두신 문, \(gd.0)입니다.")
        } else if let s = sae.first(where: sutja), gd.1.contains(where: sutja) {
            mal("이 문 글자는 \(s)입니다. 찍어 두신 \(gd.1.first(where: sutja) ?? "")과 다릅니다. 옆 문일 수 있습니다.")
        } else {
            mal("문 둘레 글자, " + sae.joined(separator: ", ") + ".", .jeongbo)
        }
    }

    /// 문 바로 앞을 알린 뒤 두 걸음 지나시면 — 손대지 않고 두 번 찍은 것으로 내 문에 담음(폰 안에만)
    private func jadongJjikgi(_ geul: [String]) {
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
            guard w.ochae <= 20 else {
                self.mal("문을 지나셨습니다. 위성이 흐려 이 문은 내 문에 담지 않았습니다.", .jeongbo, sseuGi: true)
                return
            }
            var lat = w.lat, lon = w.lon
            var bang: Double? = head >= 0 ? head : nil
            if let w2 = WichiEngine.shared.jigeum, w2.ochae <= 20, Date().timeIntervalSince(w2.ttae) < 10 {
                lat = (w.lat + w2.lat) / 2; lon = (w.lon + w2.lon) / 2
                if bang == nil && WichiEngine.geori(w.lat, w.lon, w2.lat, w2.lon) >= 1.5 { bang = WichiEngine.bangwi(w.lat, w.lon, w2.lat, w2.lon) }
            }
            let m = NaeMun.shared.jjakDamgi(ireum: "", lat: lat, lon: lon, bang: bang, geul: geul)
            self.mal("문을 지나셨습니다. " + m, .jeongbo, sseuGi: true)
            Girok.shared.namgi("munchatgi_jjak", ["geul": geul.count])
        }
    }

    static func ttuk(_ s: String) -> String { s.replacingOccurrences(of: " ", with: "").uppercased() }

    private func mal(_ t: String, _ g: MalGeup = .annae, sseuGi: Bool = false) {
        if !Seoljeong.shared.kameraMal && !sseuGi && kyeojim { return }   // 카메라 눈 말소리 끔 — 소리만
        SoriEngine.shared.mal(t, g)
    }

    // MARK: 셈

    /// 몸 기준 도(오른쪽 +) → 1~12시
    static func sigye(_ gak: Double) -> Int {
        var s = Int((gak / 30).rounded())
        s = ((s % 12) + 12) % 12
        return s == 0 ? 12 : s
    }

    /// 라이다 폰 — 공간 그물에서 "문"으로 나뉜 면들의 가운데(세상 좌표). 작으면 버림
    static func munJungsim(_ m: ARMeshAnchor) -> SIMD3<Float>? {
        let g = m.geometry
        guard let cls = g.classification else { return nil }
        let fc = g.faces.count
        guard fc > 0 else { return nil }
        let ipf = g.faces.indexCountPerPrimitive
        let bpi = g.faces.bytesPerIndex
        let ib = g.faces.buffer.contents()
        let vb = g.vertices.buffer.contents()
        let cb = cls.buffer.contents()
        let munBeon = UInt8(ARMeshClassification.door.rawValue)
        var hap = SIMD3<Float>(0, 0, 0)
        var n = 0
        let gan = max(1, fc / 2000)   // 면이 많으면 건너뛰며 셈
        var i = 0
        while i < fc {
            let c = cb.advanced(by: cls.offset + cls.stride * i).assumingMemoryBound(to: UInt8.self).pointee
            if c == munBeon {
                let ip = ib.advanced(by: i * ipf * bpi)
                let vi = bpi == 4 ? Int(ip.assumingMemoryBound(to: UInt32.self).pointee) : Int(ip.assumingMemoryBound(to: UInt16.self).pointee)
                let vp = vb.advanced(by: g.vertices.offset + g.vertices.stride * vi).assumingMemoryBound(to: Float.self)
                let w = m.transform * SIMD4<Float>(vp[0], vp[1], vp[2], 1)
                hap += SIMD3<Float>(w.x, w.y, w.z)
                n += 1
            }
            i += gan
        }
        guard n >= 12 else { return nil }
        return hap / Float(n)
    }

    /// 문 둘레 글자 — 호수·출입구 같은 짧은 글만 두 줄까지(폰 안에서만)
    static func geulIlgi(_ px: CVPixelBuffer) -> [String] {
        let r = VNRecognizeTextRequest()
        r.recognitionLevel = .accurate
        r.recognitionLanguages = ["ko-KR", "en-US"]
        r.usesLanguageCorrection = false
        let h = VNImageRequestHandler(cvPixelBuffer: px, orientation: .right, options: [:])
        try? h.perform([r])
        let obs = r.results ?? []
        var o: [String] = []
        for x in obs {
            guard let c = x.topCandidates(1).first, c.confidence >= 0.5 else { continue }
            let t = c.string.trimmingCharacters(in: .whitespacesAndNewlines)
            guard t.count >= 1, t.count <= 14 else { continue }
            let jeokhap = t.range(of: "[0-9]", options: .regularExpression) != nil
                || ["호", "실", "입구", "출입", "출구", "문", "화장실", "사무", "센터", "PUSH", "PULL", "당기", "미세"].contains { t.contains($0) }
            guard jeokhap, !o.contains(t) else { continue }
            o.append(t)
            if o.count >= 2 { break }
        }
        return o
    }
}

// MARK: 화면 — 길 찾기 탭 그 밖에 펼치기

struct MunChatgiView: View {
    @ObservedObject private var m = MunChatgi.shared
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if m.kyeojim {
                    Button("문 찾기 끄기 — \(m.sangtae)") { m.kkeugi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                } else {
                    Button("문 찾기 켜기 — 폰을 가슴 앞에 세워 들고 걸으십시오") { m.kyeogi("hwamyeon") }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                }
                Toggle(isOn: $s.kameraMal) { Text("카메라 눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("카메라로 앞에 있는 문을 찾아 몇 시 방향, 몇 걸음인지 알려 드립니다. 폰을 가슴 앞에 세워 들거나 목걸이로 걸고 천천히 걸으시면 됩니다. 문이 보이면 확신음과 함께 알려 드리고, 가까워질수록 확신음이 납니다. 3초에 한 번쯤, 방향이나 걸음이 바뀔 때만 말씀드립니다. 문 위에 호수나 출입구 같은 글자가 보이면 한 번 읽어 드립니다. 문 바로 앞에 이르면 도착 소리와 함께 알려 드리고 카메라를 끕니다. 점지도 문까지 안내에서 문 10미터 안에 드시면, 또 걸어서 목적지에 닿으시면 문 찾기가 저절로 켜집니다. 말로 하기에서 문 찾아 줘라고 하셔도 켜집니다. 카메라 눈 말소리를 끄시면 말은 하지 않고 소리로만 알립니다. 인터넷 없이 폰 안에서만 돌며, 라이다가 있는 프로 폰이 가장 정확합니다. 유리문이나 활짝 열린 문은 잘 못 알아볼 수 있으니 지팡이로 꼭 함께 확인하십시오.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("문 찾기")
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true } }
    }
}
