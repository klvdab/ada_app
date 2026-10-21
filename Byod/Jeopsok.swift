// BYOD 방송 아이폰 — 접속 도구: 엔에프시 태그 쓰기와 큐알코드 만들기 (1.1.5판, 빌드 261010-BI1, 방송클)
// 이사장님 지시(2026-10-06): 접수대에서 한 번에 붙게. 안드로이드 듣는 분은 엔에프시 태그(와이파이 붙기), 아이폰 듣는 분은 큐알코드(와이파이 붙기).
// 두 기능 모두 늘 유지 — 안드로이드판 접속 도구(JeopsokActivity)와 같은 내용을 씁니다.
// 아이폰은 지금 붙은 와이파이 이름을 앱이 알아낼 수 없어, 와이파이 이름은 한 번 넣어 두면 기억합니다.
// 1.2.0 (261014-BI3, 이사장님 승인 「1」, 방송클) 플린트 2 방식 — 와이파이 태그·큐알 한 장이면 듣기 화면이 저절로 뜸. 듣기 주소 태그·큐알은 예비 방식용으로 펼치기 안에. 와이파이 이름은 방식마다 따로 기억
// 1.1.6 (261012-BI2, 이사장님 승인 「1」, 방송클) 스티커를 엔에프시 태그로 부름, 듣기 주소 바로잡기와 확인, 엔에프시 태그를 쓴 뒤 다시 읽어 확인, A4 인쇄용 한 장 — 안드로이드 1.1.6과 같음
// 1.7.0 (261021-BI9, 이사장님 승인) 「예비 방식」을 「아이피타임 방식」으로. 큐알이 커서 카메라에 다 들어오지 않는다 하여 큐알을 작게 —
//   인쇄하면 한 변 약 5센티미터(300dpi 590점), 화면에서는 한 변 약 4센티미터(250포인트 칸 안). 둘레 흰 여백은 표준대로 4칸.
//   큐알 그림 저장도 A4 한 장 안에 5센티미터로 넣어 저장. 아이피타임 A4는 가운데 자르는 선(두 큐알이 한 카메라에 함께 잡히지 않게) — 안드로이드 1.7.0과 같음
import SwiftUI
import UIKit
import CoreNFC
import CoreImage
import CoreImage.CIFilterBuiltins

// MARK: 와이파이·주소 내용 만들기

enum Jeopsok {
    /// 와이파이 정보(Wi-Fi Simple Config) — 안드로이드폰이 엔에프시 태그를 읽으면 「연결할까요」를 띄움
    static func wifiPayload(ssid: String, pw: String) -> Data {
        func tlv(_ o: inout Data, _ t: Int, _ v: Data) {
            o.append(UInt8((t >> 8) & 255)); o.append(UInt8(t & 255))
            o.append(UInt8((v.count >> 8) & 255)); o.append(UInt8(v.count & 255))
            o.append(v)
        }
        var cred = Data()
        tlv(&cred, 0x1026, Data([1]))
        tlv(&cred, 0x1045, Data(ssid.utf8))
        tlv(&cred, 0x1003, pw.isEmpty ? Data([0, 1]) : Data([0, 0x20]))
        tlv(&cred, 0x100F, pw.isEmpty ? Data([0, 1]) : Data([0, 8]))
        tlv(&cred, 0x1027, Data(pw.utf8))
        tlv(&cred, 0x1020, Data([0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF]))
        var all = Data()
        tlv(&all, 0x104A, Data([0x10]))
        tlv(&all, 0x100E, cred)
        return all
    }

    static func wifiMesiji(ssid: String, pw: String) -> NFCNDEFMessage {
        let r = NFCNDEFPayload(format: .media, type: Data("application/vnd.wfa.wsc".utf8), identifier: Data(), payload: wifiPayload(ssid: ssid, pw: pw))
        return NFCNDEFMessage(records: [r])
    }

    static func jusoMesiji(_ juso: String) -> NFCNDEFMessage? {
        guard let u = URL(string: juso), let r = NFCNDEFPayload.wellKnownTypeURIPayload(url: u) else { return nil }
        return NFCNDEFMessage(records: [r])
    }

    private static func gamssagi(_ t: String) -> String {
        t.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: ",", with: "\\,").replacingOccurrences(of: ":", with: "\\:")
    }

    static func wifiQr(ssid: String, pw: String) -> String {
        pw.isEmpty ? "WIFI:T:nopass;S:\(gamssagi(ssid));;" : "WIFI:T:WPA;S:\(gamssagi(ssid));P:\(gamssagi(pw));;"
    }

    /// 1.1.6 듣기 주소 바로잡기 — http:// 와 :8080 과 끝의 / 를 저절로 채움
    static func gotchim(_ t0: String) -> String {
        var t = t0.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: " ", with: "")
        if t.isEmpty { return "" }
        if !t.hasPrefix("http://") && !t.hasPrefix("https://") { t = "http://" + t }
        let dwi = t.components(separatedBy: "://").last ?? ""
        let host = dwi.components(separatedBy: "/").first ?? ""
        if !host.contains(":"), let r = t.range(of: host) { t.replaceSubrange(r, with: host + ":\(Bang.PORT)") }
        let dwi2 = t.components(separatedBy: "://").last ?? ""
        if !dwi2.contains("/") { t += "/" }
        return t
    }

    /// 1.1.6 듣기 주소 확인 — 그 주소의 /status 에 물어 BYOD 방송이 답하는지 봄(3초)
    static func hwagin(_ juso: String, _ kkeut: @escaping (Bool) -> Void) {
        guard let u = URL(string: juso + "status") else { kkeut(false); return }
        var q = URLRequest(url: u); q.timeoutInterval = 3; q.cachePolicy = .reloadIgnoringLocalCacheData
        URLSession.shared.dataTask(with: q) { d, r, _ in
            let ok = (r as? HTTPURLResponse)?.statusCode == 200 && (d.flatMap { String(data: $0, encoding: .utf8) } ?? "").contains("\"byod\"")
            DispatchQueue.main.async { kkeut(ok) }
        }.resume()
    }

    // 1.7.0 인쇄 크기 — A4(300dpi, 2480×3508)에서 큐알 한 변 590점 = 약 5센티미터
    static let A4W: CGFloat = 2480, A4H: CGFloat = 3508, QK: CGFloat = 590

    private static func a4Geurigi(_ geurim: (UIGraphicsImageRendererContext, (String, CGFloat, CGFloat, Bool) -> Void) -> Void) -> UIImage {
        let fm = UIGraphicsImageRendererFormat(); fm.scale = 1; fm.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: A4W, height: A4H), format: fm).image { c in
            UIColor.white.setFill(); c.fill(CGRect(x: 0, y: 0, width: A4W, height: A4H))
            c.cgContext.interpolationQuality = .none   // 큐알 칸 가장자리가 흐려지지 않게
            let gaunde = NSMutableParagraphStyle(); gaunde.alignment = .center
            func sseugi(_ t: String, _ y: CGFloat, _ k: CGFloat, _ gulgeum: Bool) {
                let a: [NSAttributedString.Key: Any] = [.font: gulgeum ? UIFont.boldSystemFont(ofSize: k) : UIFont.systemFont(ofSize: k), .foregroundColor: UIColor.black, .paragraphStyle: gaunde]
                (t as NSString).draw(in: CGRect(x: 0, y: y, width: A4W, height: k * 1.5), withAttributes: a)
            }
            geurim(c, sseugi)
        }
    }

    private static func jareunSeon(_ c: UIGraphicsImageRendererContext, _ y: CGFloat, _ sseugi: (String, CGFloat, CGFloat, Bool) -> Void) {
        UIColor.black.setFill()
        var x: CGFloat = 120
        while x < A4W - 120 { c.fill(CGRect(x: x, y: y - 2, width: 40, height: 4)); x += 70 }
        sseugi("자르는 선", y - 90, 50, false)
    }

    /// 1.1.6 A4 한 장 — 위에 와이파이 큐알, 아래에 듣기 주소 큐알. 1.7.0 큐알을 약 5센티미터로 작게, 가운데 자르는 선(두 장으로 잘라 씀)
    static func a4(wifi: String, ssid: String, juso: String) -> UIImage? {
        guard let q1 = qrGeurim(wifi), let q2 = qrGeurim(juso) else { return nil }
        return a4Geurigi { c, sseugi in
            sseugi("첫째. 와이파이 붙기", 300, 100, true)
            sseugi("아이폰 카메라로 비추십시오", 460, 70, false)
            q1.draw(in: CGRect(x: (A4W - QK) / 2, y: 700, width: QK, height: QK))
            sseugi("와이파이: " + ssid, 1370, 70, false)
            jareunSeon(c, 1754, sseugi)
            sseugi("둘째. 현장영상해설 듣기", 2054, 100, true)
            sseugi("와이파이에 붙은 뒤 카메라로 비추십시오", 2214, 70, false)
            q2.draw(in: CGRect(x: (A4W - QK) / 2, y: 2454, width: QK, height: QK))
            sseugi(juso, 3124, 70, false)
            sseugi("사단법인 한국시각장애인현장영상해설협회", 3330, 55, false)
        }
    }

    /// 1.2.0 플린트 2 방식 A4 — 와이파이 큐알 하나. 1.7.0 약 5센티미터로 작게
    static func a4Flint(wifi: String, ssid: String) -> UIImage? {
        guard let q = qrGeurim(wifi) else { return nil }
        return a4Geurigi { _, sseugi in
            sseugi("현장영상해설 듣기", 300, 110, true)
            sseugi("카메라로 비추면 와이파이에 붙고 듣기 화면이 뜹니다", 470, 70, false)
            q.draw(in: CGRect(x: (A4W - QK) / 2, y: 720, width: QK, height: QK))
            sseugi("와이파이: " + ssid, 1390, 70, false)
            sseugi("사단법인 한국시각장애인현장영상해설협회", 1540, 55, false)
        }
    }

    /// 1.7.0 큐알 하나를 A4 한 장 위쪽에 약 5센티미터로(사진 앱에서 쪽에 맞추어 인쇄해도 커지지 않게)
    static func a4Hana(nae: String, wifi: Bool, ssid: String) -> UIImage? {
        guard let q = qrGeurim(nae) else { return nil }
        return a4Geurigi { _, sseugi in
            sseugi(wifi ? "와이파이 붙기" : "현장영상해설 듣기", 300, 100, true)
            sseugi(wifi ? "아이폰 카메라로 비추십시오" : "와이파이에 붙은 뒤 카메라로 비추십시오", 460, 70, false)
            q.draw(in: CGRect(x: (A4W - QK) / 2, y: 700, width: QK, height: QK))
            sseugi(wifi ? "와이파이: " + ssid : nae, 1370, 70, false)
            sseugi("사단법인 한국시각장애인현장영상해설협회", 1520, 55, false)
        }
    }

    /// 큐알 그림 — 1.7.0 둘레 흰 여백 4칸(표준), 칸 하나 12점
    static func qrGeurim(_ t: String) -> UIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(t.utf8)
        f.correctionLevel = "M"
        guard let o = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 12, y: 12)) else { return nil }
        guard let cg = CIContext().createCGImage(o, from: o.extent) else { return nil }
        // CIFilter 큐알은 둘레에 1칸 여백이 붙어 나오므로 3칸을 더 둘러 모두 4칸
        let w = o.extent.width, teduri: CGFloat = 12 * 3
        let fm = UIGraphicsImageRendererFormat()
        fm.scale = 1
        fm.opaque = true
        let r = UIGraphicsImageRenderer(size: CGSize(width: w + teduri * 2, height: w + teduri * 2), format: fm)
        return r.image { c in
            UIColor.white.setFill()
            c.fill(CGRect(x: 0, y: 0, width: w + teduri * 2, height: w + teduri * 2))
            c.cgContext.interpolationQuality = .none
            UIImage(cgImage: cg).draw(in: CGRect(x: teduri, y: teduri, width: w, height: w))
        }
    }
}

// MARK: 엔에프시 태그 쓰기

final class NfcSseugi: NSObject, NFCNDEFReaderSessionDelegate {
    static let shared = NfcSseugi()
    private var session: NFCNDEFReaderSession?
    private var mesiji: NFCNDEFMessage?
    private var ireum = ""
    private var su = 0
    var allim: (String) -> Void = { _ in }

    func sijak(_ m: NFCNDEFMessage, ireum: String) {
        guard NFCNDEFReaderSession.readingAvailable else { allim("이 아이폰은 엔에프시 태그를 쓸 수 없습니다."); return }
        if let s0 = session { session = nil; s0.invalidate() }   // 앞 쓰기가 돌고 있으면 먼저 닫음
        mesiji = m
        self.ireum = ireum
        su = 0
        let s = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: false)
        s.alertMessage = "\(ireum) 엔에프시 태그를 아이폰 뒷면 윗부분에 대 주십시오. 여러 장을 차례로 대면 계속 씁니다."
        session = s
        s.begin()
        allim("\(ireum) 엔에프시 태그 쓰기를 준비했습니다. 아이폰 뒷면 윗부분, 카메라 옆에 엔에프시 태그를 대 주십시오. 1분이 지나면 저절로 멈춥니다.")
    }

    func meomchugi() {
        guard let s = session else { allim("지금은 엔에프시 태그를 쓰고 있지 않습니다."); return }
        s.invalidate()
    }

    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        guard session === self.session else { return }   // 이미 새 쓰기로 바뀐 옛 것은 건드리지 않음
        let n = su
        self.session = nil
        mesiji = nil
        DispatchQueue.main.async { self.allim("엔에프시 태그 쓰기를 멈췄습니다. 모두 \(n)장 썼습니다.") }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) { }

    /// 1.1.6 두 엔디프 묶음이 같은가(종류와 내용)
    static func gatda(_ a: NFCNDEFMessage, _ b: NFCNDEFMessage) -> Bool {
        guard a.records.count == b.records.count else { return false }
        for (x, y) in zip(a.records, b.records) where x.type != y.type || x.payload != y.payload { return false }
        return true
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetect tags: [NFCNDEFTag]) {
        guard let tag = tags.first, let m = mesiji else { session.restartPolling(); return }
        func dasi(_ t: String) {
            session.alertMessage = t
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { session.restartPolling() }
        }
        session.connect(to: tag) { e in
            if e != nil { dasi("엔에프시 태그를 움직이지 말고 2초쯤 다시 대 주십시오."); return }
            tag.queryNDEFStatus { st, keugi, e2 in
                if e2 != nil { dasi("엔에프시 태그를 읽지 못했습니다. 다시 대 주십시오."); return }
                switch st {
                case .notSupported: dasi("이 엔에프시 태그는 쓸 수 없는 종류입니다. 엔태그 213, 215, 216 엔에프시 태그를 쓰십시오.")
                case .readOnly: dasi("이 엔에프시 태그는 잠겨 있어 쓸 수 없습니다. 다른 엔에프시 태그를 대 주십시오.")
                case .readWrite:
                    if keugi < m.length { dasi("이 엔에프시 태그는 너무 작습니다. 엔태그 215 이상을 쓰십시오."); return }
                    tag.writeNDEF(m) { e3 in
                        if e3 != nil { dasi("쓰지 못했습니다. 엔에프시 태그를 움직이지 말고 2초쯤 대 주십시오."); return }
                        // 1.1.6 쓴 뒤 다시 읽어 견줌 — 다르면 다시 대 달라고 알림
                        tag.readNDEF { dm, e4 in
                            if e4 == nil, let dm = dm, !NfcSseugi.gatda(dm, m) { dasi("썼지만 다시 읽은 내용이 다릅니다. 엔에프시 태그를 움직이지 말고 한 번 더 대 주십시오."); return }
                            self.su += 1
                            let n = self.su
                            let ir = self.ireum
                            DispatchQueue.main.async { self.allim("\(ir) 엔에프시 태그 \(n)장째를 다 썼습니다. 다음 엔에프시 태그를 대 주십시오.") }
                            dasi("\(n)장째를 다 썼습니다. 다음 엔에프시 태그를 대 주십시오.")
                        }
                    }
                @unknown default: dasi("다시 대 주십시오.")
                }
            }
        }
    }
}

// MARK: 큐알 그림을 사진 앱에 저장

final class SajinJeojang: NSObject {
    static let shared = SajinJeojang()
    private var kkeut: ((Bool) -> Void)?
    func jeojang(_ i: UIImage, _ k: @escaping (Bool) -> Void) {
        kkeut = k
        UIImageWriteToSavedPhotosAlbum(i, self, #selector(dwi(_:didFinishSavingWithError:contextInfo:)), nil)
    }
    @objc private func dwi(_ image: UIImage, didFinishSavingWithError error: Error?, contextInfo: UnsafeRawPointer) {
        let ok = error == nil
        DispatchQueue.main.async { self.kkeut?(ok); self.kkeut = nil }
    }
}

// MARK: 화면

struct JeopsokView: View {
    let dwiro: () -> Void
    @AppStorage("byod_ssid") private var ssidT = ""
    @AppStorage("byod_ssid_flint") private var ssidF = ""   // 1.2.0 플린트 2 방식 와이파이 이름은 따로 기억
    @State private var jusoPyeolchim = false
    private let flint = Flint.flintInga
    private var ssid: String { flint ? ssidF : ssidT }
    private var ssidB: Binding<String> { flint ? $ssidF : $ssidT }
    @AppStorage("byod_pw") private var pw = ""
    @State private var juso = Jeopsok.gotchim(Bang.shared.juso.isEmpty ? (Flint.flintInga ? Flint.JUSO : Juso.deutgiJuso()) : Bang.shared.juso)   // 1.1.6 방송 중이면 방송 주소를 먼저
    @State private var allim = ""
    @State private var qr: UIImage?
    @State private var qrIreum = ""
    @State private var qrNae = ""   // 1.7.0 화면에 띄운 큐알 하나의 내용(A4 두 장짜리를 띄웠으면 빈 글)
    @AccessibilityFocusState private var wiChojeom: Bool
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Danchu(geul: "뒤로") { dwiro() }.accessibilityFocused($wiChojeom)
                Geul(flint ? "플린트 2 방식입니다. 듣는 분은 와이파이 태그 한 번(안드로이드)이나 와이파이 큐알 한 번(아이폰)이면 듣기 화면이 저절로 뜹니다. 듣기 주소 태그는 필요 없습니다."
                           : "아이피타임 방식입니다. 듣는 분은 두 번 합니다. 첫째, 와이파이에 붙습니다(안드로이드는 와이파이 태그, 아이폰은 와이파이 큐알). 둘째, 듣기 주소 태그나 듣기 주소 큐알로 듣기 화면을 엽니다(안드로이드와 아이폰 모두). 방송 와이파이 이름은 아이피타임 공유기 와이파이 이름 그대로 넣으십시오. 한 번 넣어 두면 기억합니다.")
                Kan(ireum: "방송 와이파이 이름", gap: ssidB)
                Kan(ireum: "와이파이 비밀번호(없으면 비워 두기)", gap: $pw)
                if !allim.isEmpty {
                    Text(allim).font(.title3.weight(.bold)).foregroundStyle(Saek.norang)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityFocused($allimChojeom)
                }
                Danchu(geul: "엔에프시 태그 쓰기: 와이파이 붙기") {
                    let s = ssid.trimmingCharacters(in: .whitespaces)
                    if s.isEmpty { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
                    NfcSseugi.shared.sijak(Jeopsok.wifiMesiji(ssid: s, pw: pw), ireum: "와이파이 붙기")
                }
                Danchu(geul: "엔에프시 태그 쓰기 멈추기") { NfcSseugi.shared.meomchugi() }
                Geul("큐알코드(아이폰은 카메라로 비추면 와이파이에 붙음)", gulgeum: true).accessibilityAddTraits(.isHeader)
                Danchu(geul: flint ? "A4 인쇄용 한 장 만들기: 와이파이 큐알" : "A4 인쇄용 한 장 만들기: 위 와이파이, 아래 듣기 주소, 가운데를 잘라 두 장") {   // 1.1.6, 1.2.0, 1.7.0
                    let s = ssid.trimmingCharacters(in: .whitespaces)
                    if s.isEmpty { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
                    if flint {   // 1.2.0
                        guard let a = Jeopsok.a4Flint(wifi: Jeopsok.wifiQr(ssid: s, pw: pw), ssid: s) else { mal("그림을 만들지 못했습니다."); return }
                        qr = a; qrIreum = "A4 인쇄용 한 장, 와이파이"; qrNae = ""
                        SajinJeojang.shared.jeojang(a) { ok in
                            mal(ok ? "A4 인쇄용 한 장을 사진 앱에 저장했습니다. 와이파이 큐알 하나이며 한 변 약 5센티미터입니다. 붙으면 듣기 화면이 저절로 뜹니다." : "저장하지 못했습니다. 아이폰 설정에서 LVD 송출의 사진 추가를 허용해 주십시오.")
                        }
                        return
                    }
                    let j = Jeopsok.gotchim(juso); juso = j
                    if j.isEmpty { mal("듣기 주소가 비었습니다."); return }
                    Hyeonjang.stickerGieok(j)
                    guard let a = Jeopsok.a4(wifi: Jeopsok.wifiQr(ssid: s, pw: pw), ssid: s, juso: j) else { mal("그림을 만들지 못했습니다."); return }
                    qr = a; qrIreum = "A4 인쇄용 한 장, 위 와이파이, 아래 듣기 주소, 가운데 자르는 선"; qrNae = ""
                    SajinJeojang.shared.jeojang(a) { ok in
                        mal(ok ? "A4 인쇄용 한 장을 사진 앱에 저장했습니다. 큐알은 한 변 약 5센티미터입니다. 인쇄한 뒤 가운데 자르는 선을 따라 잘라 위는 와이파이, 아래는 듣기로 쓰십시오." : "저장하지 못했습니다. 아이폰 설정에서 LVD 송출의 사진 추가를 허용해 주십시오.")
                    }
                }
                Danchu(geul: "큐알 보기: 와이파이 붙기") {
                    let s = ssid.trimmingCharacters(in: .whitespaces)
                    if s.isEmpty { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
                    qrBoyeogi(Jeopsok.wifiQr(ssid: s, pw: pw), "와이파이 붙기")
                }
                Danchu(geul: "큐알 그림 저장(인쇄용)") {
                    guard let q0 = qr else { mal("먼저 큐알 보기를 눌러 주십시오."); return }
                    // 1.7.0 큐알 하나는 A4 한 장 안에 약 5센티미터로 넣어 저장
                    let q = qrNae.isEmpty ? q0 : (Jeopsok.a4Hana(nae: qrNae, wifi: qrIreum == "와이파이 붙기", ssid: ssid.trimmingCharacters(in: .whitespaces)) ?? q0)
                    SajinJeojang.shared.jeojang(q) { ok in
                        mal(ok ? "A4 한 장으로 사진 앱에 저장했습니다. 큐알은 한 변 약 5센티미터입니다. 그 그림을 인쇄하시면 됩니다." : "저장하지 못했습니다. 아이폰 설정에서 LVD 송출의 사진 추가를 허용해 주십시오.")
                    }
                }
                // 듣기 주소 태그·큐알 — 아이피타임 방식(이 아이폰이 서버)에서 씀. 플린트 2 방식에서는 펼쳐야 보임
                if flint {
                    Danchu(geul: jusoPyeolchim ? "듣기 주소 태그와 큐알 접기" : "듣기 주소 태그와 큐알 펼치기 (아이피타임 방식에서 씀)") { jusoPyeolchim.toggle() }
                }
                if !flint || jusoPyeolchim {
                    Kan(ireum: "듣기 주소", gap: $juso)
                    Danchu(geul: "듣기 주소 확인하기") { jusoHwagin {} }   // 1.1.6
                    Danchu(geul: "엔에프시 태그 쓰기: 듣기 주소 열기") {
                        let j = Jeopsok.gotchim(juso); juso = j   // 1.1.6 바로잡고 먼저 확인한 뒤 씀
                        guard let m = Jeopsok.jusoMesiji(j), j.hasPrefix("http") else { mal("듣기 주소가 비었거나 틀렸습니다. 공유기에 붙은 뒤 다시 여십시오."); return }
                        Hyeonjang.stickerGieok(j)   // 쓴 주소를 기억해 현장 점검에서 견줌
                        jusoHwagin { NfcSseugi.shared.sijak(m, ireum: "듣기 주소") }
                    }
                    Danchu(geul: "큐알 보기: 듣기 주소 열기") {
                        let j = Jeopsok.gotchim(juso); juso = j
                        if !j.hasPrefix("http") { mal("듣기 주소가 비었거나 틀렸습니다. 공유기에 붙은 뒤 다시 여십시오."); return }
                        Hyeonjang.stickerGieok(j)
                        qrBoyeogi(j, "듣기 주소")
                    }
                }
                if let q = qr {
                    // 1.7.0 화면 큐알은 250포인트 칸 안에 작게(아이폰에서 한 변 약 4센티미터), 가운데에
                    Image(uiImage: q).resizable().interpolation(.none).scaledToFit()
                        .frame(width: 250, height: 250)
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel(qrIreum + " 큐알코드")
                }
                Danchu(geul: "뒤로") { dwiro() }
            }
            .padding(20)
        }
        .background(Saek.bada.ignoresSafeArea())
        .accessibilityAction(.escape) { dwiro() }
        .onAppear {
            NfcSseugi.shared.allim = { t in mal(t) }
            if juso.isEmpty { juso = flint ? Flint.JUSO : Juso.deutgiJuso() }
            if flint && ssidF.isEmpty { ssidF = Flint.SSID }   // 1.2.0
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { wiChojeom = true }
        }
        .onDisappear { NfcSseugi.shared.allim = { _ in } }
    }

    private func mal(_ t: String) {
        allim = t
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
        if Malsori.shared.kyeojim { Malsori.shared.mal(t) }
    }

    /// 1.1.6 듣기 주소 확인 — 됐든 안 됐든 다음 일(f)은 이어 함
    private func jusoHwagin(_ f: @escaping () -> Void) {
        let j = Jeopsok.gotchim(juso); juso = j
        if j.isEmpty { mal("듣기 주소가 비었습니다."); return }
        let ip = (j.components(separatedBy: "://").last ?? "").components(separatedBy: ":").first ?? ""
        let ilkgi = ip.replacingOccurrences(of: ".", with: " 점 ")
        mal("듣기 주소 \(ilkgi) 를 확인하고 있습니다.")
        Jeopsok.hwagin(j) { ok in
            mal(ok ? "확인했습니다. 듣기 주소 \(ilkgi) 에서 LVD 송출이 답합니다."
                   : "듣기 주소 \(ilkgi) 에서 답이 없습니다. 방송을 켰는지, 이 아이폰이 아이피타임 공유기에 붙었는지 보십시오. 방송을 켜지 않았으면 그대로 써도 됩니다.")
            f()
        }
    }

    private func qrBoyeogi(_ nae: String, _ ireum: String) {
        guard let i = Jeopsok.qrGeurim(nae) else { mal("큐알을 만들지 못했습니다."); return }
        qr = i
        qrIreum = ireum
        qrNae = nae   // 1.7.0 저장할 때 A4 한 장 안에 다시 그림
        mal("\(ireum) 큐알코드를 화면 아래에 띄웠습니다. 한 변 약 4센티미터입니다. 인쇄하려면 큐알 그림 저장을 누르십시오.")
    }
}
