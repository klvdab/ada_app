// BYOD 방송 아이폰 — 접속 도구: 엔에프시 스티커 쓰기와 큐알코드 만들기 (1.1.5판, 빌드 261010-BI1, 방송클)
// 이사장님 지시(2026-10-06): 접수대에서 한 번에 붙게. 안드로이드 듣는 분은 엔에프시 스티커(와이파이 붙기), 아이폰 듣는 분은 큐알코드(와이파이 붙기).
// 두 기능 모두 늘 유지 — 안드로이드판 접속 도구(JeopsokActivity)와 같은 내용을 씁니다.
// 아이폰은 지금 붙은 와이파이 이름을 앱이 알아낼 수 없어, 와이파이 이름은 한 번 넣어 두면 기억합니다.
import SwiftUI
import UIKit
import CoreNFC
import CoreImage
import CoreImage.CIFilterBuiltins

// MARK: 와이파이·주소 내용 만들기

enum Jeopsok {
    /// 와이파이 정보(Wi-Fi Simple Config) — 안드로이드폰이 스티커를 읽으면 「연결할까요」를 띄움
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

    /// 큐알 그림 — 흰 테두리를 둘러 인쇄해도 잘 읽히게
    static func qrGeurim(_ t: String) -> UIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(t.utf8)
        f.correctionLevel = "M"
        guard let o = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 12, y: 12)) else { return nil }
        guard let cg = CIContext().createCGImage(o, from: o.extent) else { return nil }
        let w = o.extent.width, teduri: CGFloat = 48
        let fm = UIGraphicsImageRendererFormat()
        fm.scale = 1
        fm.opaque = true
        let r = UIGraphicsImageRenderer(size: CGSize(width: w + teduri * 2, height: w + teduri * 2), format: fm)
        return r.image { c in
            UIColor.white.setFill()
            c.fill(CGRect(x: 0, y: 0, width: w + teduri * 2, height: w + teduri * 2))
            UIImage(cgImage: cg).draw(in: CGRect(x: teduri, y: teduri, width: w, height: w))
        }
    }
}

// MARK: 엔에프시 스티커 쓰기

final class NfcSseugi: NSObject, NFCNDEFReaderSessionDelegate {
    static let shared = NfcSseugi()
    private var session: NFCNDEFReaderSession?
    private var mesiji: NFCNDEFMessage?
    private var ireum = ""
    private var su = 0
    var allim: (String) -> Void = { _ in }

    func sijak(_ m: NFCNDEFMessage, ireum: String) {
        guard NFCNDEFReaderSession.readingAvailable else { allim("이 아이폰은 엔에프시 스티커를 쓸 수 없습니다."); return }
        if let s0 = session { session = nil; s0.invalidate() }   // 앞 쓰기가 돌고 있으면 먼저 닫음
        mesiji = m
        self.ireum = ireum
        su = 0
        let s = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: false)
        s.alertMessage = "\(ireum) 스티커를 아이폰 뒷면 윗부분에 대 주십시오. 여러 장을 차례로 대면 계속 씁니다."
        session = s
        s.begin()
        allim("\(ireum) 스티커 쓰기를 준비했습니다. 아이폰 뒷면 윗부분, 카메라 옆에 스티커를 대 주십시오. 1분이 지나면 저절로 멈춥니다.")
    }

    func meomchugi() {
        guard let s = session else { allim("지금은 스티커를 쓰고 있지 않습니다."); return }
        s.invalidate()
    }

    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        guard session === self.session else { return }   // 이미 새 쓰기로 바뀐 옛 것은 건드리지 않음
        let n = su
        self.session = nil
        mesiji = nil
        DispatchQueue.main.async { self.allim("스티커 쓰기를 멈췄습니다. 모두 \(n)장 썼습니다.") }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) { }

    func readerSession(_ session: NFCNDEFReaderSession, didDetect tags: [NFCNDEFTag]) {
        guard let tag = tags.first, let m = mesiji else { session.restartPolling(); return }
        func dasi(_ t: String) {
            session.alertMessage = t
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { session.restartPolling() }
        }
        session.connect(to: tag) { e in
            if e != nil { dasi("스티커를 움직이지 말고 2초쯤 다시 대 주십시오."); return }
            tag.queryNDEFStatus { st, keugi, e2 in
                if e2 != nil { dasi("스티커를 읽지 못했습니다. 다시 대 주십시오."); return }
                switch st {
                case .notSupported: dasi("이 스티커는 쓸 수 없는 종류입니다. 엔태그 213, 215, 216 스티커를 쓰십시오.")
                case .readOnly: dasi("이 스티커는 잠겨 있어 쓸 수 없습니다. 다른 스티커를 대 주십시오.")
                case .readWrite:
                    if keugi < m.length { dasi("이 스티커는 너무 작습니다. 엔태그 215 이상을 쓰십시오."); return }
                    tag.writeNDEF(m) { e3 in
                        if e3 != nil { dasi("쓰지 못했습니다. 스티커를 움직이지 말고 2초쯤 대 주십시오."); return }
                        self.su += 1
                        let n = self.su
                        let ir = self.ireum
                        DispatchQueue.main.async { self.allim("\(ir) 스티커 \(n)장째를 다 썼습니다. 다음 스티커를 대 주십시오.") }
                        dasi("\(n)장째를 다 썼습니다. 다음 스티커를 대 주십시오.")
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
    @AppStorage("byod_ssid") private var ssid = ""
    @AppStorage("byod_pw") private var pw = ""
    @State private var juso = Juso.deutgiJuso()
    @State private var allim = ""
    @State private var qr: UIImage?
    @State private var qrIreum = ""
    @AccessibilityFocusState private var wiChojeom: Bool
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Danchu(geul: "뒤로") { dwiro() }.accessibilityFocused($wiChojeom)
                Geul("방송 와이파이와 듣기 주소를 확인하고 아래에서 고르십시오. 와이파이 이름은 한 번 넣어 두면 기억합니다.")
                Kan(ireum: "방송 와이파이 이름", gap: $ssid)
                Kan(ireum: "와이파이 비밀번호(없으면 비워 두기)", gap: $pw)
                Kan(ireum: "듣기 주소", gap: $juso)
                if !allim.isEmpty {
                    Text(allim).font(.title3.weight(.bold)).foregroundStyle(Saek.norang)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityFocused($allimChojeom)
                }
                Geul("엔에프시 스티커(안드로이드폰용)", gulgeum: true).accessibilityAddTraits(.isHeader)
                Danchu(geul: "스티커 쓰기: 와이파이 붙기") {
                    let s = ssid.trimmingCharacters(in: .whitespaces)
                    if s.isEmpty { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
                    NfcSseugi.shared.sijak(Jeopsok.wifiMesiji(ssid: s, pw: pw), ireum: "와이파이 붙기")
                }
                Danchu(geul: "스티커 쓰기: 듣기 주소 열기") {
                    let j = juso.trimmingCharacters(in: .whitespaces)
                    guard let m = Jeopsok.jusoMesiji(j), j.hasPrefix("http") else { mal("듣기 주소가 비었거나 틀렸습니다. 공유기에 붙은 뒤 다시 여십시오."); return }
                    Hyeonjang.stickerGieok(j)   // 쓴 주소를 기억해 현장 점검에서 견줌
                    NfcSseugi.shared.sijak(m, ireum: "듣기 주소")
                }
                Danchu(geul: "스티커 쓰기 멈추기") { NfcSseugi.shared.meomchugi() }
                Geul("큐알코드(아이폰은 카메라로 비추면 와이파이에 붙음)", gulgeum: true).accessibilityAddTraits(.isHeader)
                Danchu(geul: "큐알 보기: 와이파이 붙기") {
                    let s = ssid.trimmingCharacters(in: .whitespaces)
                    if s.isEmpty { mal("방송 와이파이 이름을 먼저 넣어 주십시오."); return }
                    qrBoyeogi(Jeopsok.wifiQr(ssid: s, pw: pw), "와이파이 붙기")
                }
                Danchu(geul: "큐알 보기: 듣기 주소 열기") {
                    let j = juso.trimmingCharacters(in: .whitespaces)
                    if !j.hasPrefix("http") { mal("듣기 주소가 비었거나 틀렸습니다. 공유기에 붙은 뒤 다시 여십시오."); return }
                    Hyeonjang.stickerGieok(j)
                    qrBoyeogi(j, "듣기 주소")
                }
                Danchu(geul: "큐알 그림 저장(인쇄용)") {
                    guard let q = qr else { mal("먼저 큐알 보기를 눌러 주십시오."); return }
                    SajinJeojang.shared.jeojang(q) { ok in
                        mal(ok ? "사진 앱에 저장했습니다. 그 그림을 인쇄하시면 됩니다." : "저장하지 못했습니다. 아이폰 설정에서 BYOD 방송의 사진 추가를 허용해 주십시오.")
                    }
                }
                if let q = qr {
                    Image(uiImage: q).resizable().interpolation(.none).scaledToFit()
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
            if juso.isEmpty { juso = Juso.deutgiJuso() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { wiChojeom = true }
        }
        .onDisappear { NfcSseugi.shared.allim = { _ in } }
    }

    private func mal(_ t: String) {
        allim = t
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
        if Malsori.shared.kyeojim { Malsori.shared.mal(t) }
    }

    private func qrBoyeogi(_ nae: String, _ ireum: String) {
        guard let i = Jeopsok.qrGeurim(nae) else { mal("큐알을 만들지 못했습니다."); return }
        qr = i
        qrIreum = ireum
        mal("\(ireum) 큐알코드를 화면 아래에 띄웠습니다. 인쇄하려면 큐알 그림 저장을 누르십시오.")
    }
}
