// LVD투어스튜디오 앱 — 녹음·mp3·받아쓰기·소리 듣기·못 올린 것 (판 1.1.0, 빌드 261017-T3, 방클, 이사장님 승인 「첫단계부터 시작해」)
// 웹 투어스튜디오와 같은 일을 앱에서:
//   현장 메모 말로 적기(memo) · 지점별 읽으며 녹음(prac) — 담은 소리는 이 폰 안에서 mp3 로 바꿔 올림(이사장님 원칙 「영상 없는 녹음은 mp3」, 웹 /gongtong/mp3.js 와 같은 lamejs)
//   mp3 로 못 바꾸면 담긴 그대로(wav) 올림 — 웹과 같음
//   받아쓰기는 아이폰 받아쓰기(SFSpeechRecognizer). 안 되면 소리로만 남김 — 웹과 같은 안내
//   못 올린 사진·소리는 폰에 남겨 두었다가 다시 올림(웹의 「못 올린 것 — 지금 올려 보기」와 같음)
import Foundation
import AVFoundation
import Speech
import WebKit
import UIKit

// MARK: - 녹음기 (16kHz 한 줄 소리, wav)
@MainActor
final class Nokeumgi: NSObject, ObservableObject, AVAudioRecorderDelegate {
    static let shared = Nokeumgi()
    @Published private(set) var jung = false          // 녹음 중
    @Published private(set) var galle = ""            // "memo" / "prac"
    @Published private(set) var spotId = ""
    @Published private(set) var where_ = ""
    private var rec: AVAudioRecorder?
    private var sijak = Date()
    private var url: URL?
    static let RATE = 16000.0

    /// 녹음 시작 — 마이크를 허락하지 않으셨으면 nil 까닭을 돌려줌
    func start(galle: String, spotId: String, where_: String) async -> String? {
        if jung { return "이미 녹음 중입니다. 먼저 끝내 주십시오." }
        Deutgi.shared.meomchum()
        let ok: Bool = await withCheckedContinuation { c in AVAudioApplication.requestRecordPermission { c.resume(returning: $0) } }
        if !ok { return "마이크를 허락하지 않으셔서 녹음할 수 없습니다. 아이폰 설정의 투어스튜디오에서 마이크를 켜 주십시오." }
        let u = FileManager.default.temporaryDirectory.appendingPathComponent("nokeum_\(Int(Date().timeIntervalSince1970)).wav")
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try s.setActive(true)
            let r = try AVAudioRecorder(url: u, settings: [
                AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: Nokeumgi.RATE, AVNumberOfChannelsKey: 1,
                AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false])
            r.delegate = self
            guard r.record() else { return "녹음을 시작하지 못했습니다." }
            rec = r; url = u; sijak = Date()
            jung = true; self.galle = galle; self.spotId = spotId; self.where_ = where_
            return nil
        } catch {
            return "녹음을 시작하지 못했습니다."
        }
    }
    /// 녹음 끝 — (파일, 초)
    func stop() -> (URL, Int)? {
        guard jung, let r = rec, let u = url else { return nil }
        r.stop()
        rec = nil; jung = false
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        let sec = Int(Date().timeIntervalSince(sijak).rounded())
        return (u, sec)
    }
}

// MARK: - mp3 바꾸기 (화면에 보이지 않는 웹 칸에서 lamejs)
@MainActor
final class Mp3Gi: NSObject, WKNavigationDelegate {
    static let shared = Mp3Gi()
    private var web: WKWebView?
    private var junbi: CheckedContinuation<Bool, Never>?
    private var doem = false

    private func ready() async -> Bool {
        if doem, web != nil { return true }
        guard let u = Bundle.main.url(forResource: "mp3enc", withExtension: "js"), let js = try? String(contentsOf: u, encoding: .utf8) else { return false }
        let c = WKWebViewConfiguration()
        c.userContentController.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        let w = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: c)
        w.navigationDelegate = self
        web = w
        let ok: Bool = await withCheckedContinuation { c in
            junbi = c
            w.loadHTMLString("<!doctype html><html><head><meta charset='utf-8'></head><body></body></html>", baseURL: nil)
        }
        doem = ok
        return ok
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        junbi?.resume(returning: true); junbi = nil
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        junbi?.resume(returning: false); junbi = nil
    }

    /// wav 파일을 mp3 로 — 못 바꾸면 nil
    func bakkugi(_ wav: URL) async -> Data? {
        guard let d = try? Data(contentsOf: wav), let pcm = Mp3Gi.pcm(d), pcm.count > 3200 else { return nil }
        guard await ready(), let w = web else { return nil }
        do {
            _ = try await w.evaluateJavaScript("mpStart(\(Int(Nokeumgi.RATE)))")
            let CH = 96000
            var i = 0
            while i < pcm.count {
                let p = pcm.subdata(in: i..<min(pcm.count, i + CH))
                _ = try await w.evaluateJavaScript("mpPush('" + p.base64EncodedString() + "')")
                i += CH
            }
            let r = try await w.evaluateJavaScript("mpEnd()")
            guard let b = r as? String, let mp3 = Data(base64Encoded: b), mp3.count > 100 else { return nil }
            return mp3
        } catch { return nil }
    }
    /// wav 안의 소리 덩이(data)만 — 짝수 길이로
    static func pcm(_ d: Data) -> Data? {
        let b = [UInt8](d)
        var i = 12
        while i + 8 <= b.count {
            let id = String(bytes: b[i..<i + 4], encoding: .ascii) ?? ""
            let n = Int(b[i + 4]) | Int(b[i + 5]) << 8 | Int(b[i + 6]) << 16 | Int(b[i + 7]) << 24
            if id == "data" {
                let s = i + 8, e = min(b.count, s + n)
                let len = (e - s) & ~1
                return len > 0 ? d.subdata(in: s..<s + len) : nil
            }
            i += 8 + n + (n & 1)
        }
        return nil
    }
}

// MARK: - 받아쓰기
enum Badasseugi {
    /// 소리 파일을 글로 — 안 되면 빈 글
    static func geullo(_ u: URL) async -> String {
        let st: SFSpeechRecognizerAuthorizationStatus = await withCheckedContinuation { c in SFSpeechRecognizer.requestAuthorization { c.resume(returning: $0) } }
        guard st == .authorized, let r = SFSpeechRecognizer(locale: Locale(identifier: "ko-KR")), r.isAvailable else { return "" }
        let q = SFSpeechURLRecognitionRequest(url: u)
        q.shouldReportPartialResults = false
        if r.supportsOnDeviceRecognition { q.requiresOnDeviceRecognition = false }
        return await withCheckedContinuation { c in
            var kkeut = false
            let task = r.recognitionTask(with: q) { res, err in
                if kkeut { return }
                if let res, res.isFinal { kkeut = true; c.resume(returning: res.bestTranscription.formattedString) }
                else if err != nil { kkeut = true; c.resume(returning: "") }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 60) {
                if !kkeut { kkeut = true; task.cancel(); c.resume(returning: "") }
            }
        }
    }
}

// MARK: - 소리 듣기 (서버에 담긴 메모·연습 녹음)
@MainActor
final class Deutgi: ObservableObject {
    static let shared = Deutgi()
    @Published private(set) var jigeum = ""     // 듣는 파일 이름
    private var p: AVPlayer?
    private var kkeutGwan: NSObjectProtocol?
    func deutgi(kosId: String, f: String) async {
        if jigeum == f { meomchum(); return }
        meomchum()
        let tk = await TourAPI.needTicket()
        guard let u = TourAPI.fileURL("audio", course: kosId, f: f, tk: tk) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        let it = AVPlayerItem(url: u)
        p = AVPlayer(playerItem: it)
        kkeutGwan = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: it, queue: .main) { _ in
            Task { @MainActor in Deutgi.shared.jigeum = "" }
        }
        jigeum = f
        p?.play()
    }
    func meomchum() {
        p?.pause(); p = nil; jigeum = ""
        if let k = kkeutGwan { NotificationCenter.default.removeObserver(k); kkeutGwan = nil }
    }
}

// MARK: - 못 올린 것 (폰에 남겨 두기)
struct DaegiGeot: Codable {
    var ref: String        // 코스 안에 qref 로 적힌 표
    var jong: String       // "photo" / "audio"
    var c: String          // 코스 번호
    var s: String          // 지점 번호
    var ext: String
    var kind: String       // 소리일 때 memo / prac
    var at: String
}

enum Daegi {
    static var teo: URL {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("daegi", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }
    static func neoki(_ g: DaegiGeot, _ data: Data) {
        try? data.write(to: teo.appendingPathComponent(g.ref + ".bin"), options: .atomic)
        if let j = try? JSONEncoder().encode(g) { try? j.write(to: teo.appendingPathComponent(g.ref + ".json"), options: .atomic) }
    }
    static func mokrok() -> [DaegiGeot] {
        let fs = (try? FileManager.default.contentsOfDirectory(at: teo, includingPropertiesForKeys: nil)) ?? []
        return fs.filter { $0.pathExtension == "json" }.compactMap { u in
            guard let d = try? Data(contentsOf: u) else { return nil }
            return try? JSONDecoder().decode(DaegiGeot.self, from: d)
        }.sorted { $0.at < $1.at }
    }
    static func data(_ g: DaegiGeot) -> Data? { try? Data(contentsOf: teo.appendingPathComponent(g.ref + ".bin")) }
    static func ppaegi(_ ref: String) {
        try? FileManager.default.removeItem(at: teo.appendingPathComponent(ref + ".bin"))
        try? FileManager.default.removeItem(at: teo.appendingPathComponent(ref + ".json"))
    }
    static var su: Int { mokrok().count }
    static func saeRef(_ jong: String) -> String { (jong == "photo" ? "p" : "a") + String(Int(Date().timeIntervalSince1970 * 1000)) }
}

// MARK: - 연습 되돌아보기 (웹 pracReview·pracSay 와 같음)
enum Yeonseup {
    /// 웹 BANNED 와 같은 시각 전제 표현(14번)
    static let BANNED = ["보인다", "보임", "보이는", "보여", "보시면", "보세요", "바라본", "바라보", "내려다본", "보는 바와 같이", "눈에 들어오", "눈앞에", "한눈에", "보입니다", "보이고", "보이며", "보였", "보이지"]
    static func geumji(_ t: String) -> [String] {
        let s0 = t.replacingOccurrences(of: "만져\\s*보(시면|세요|십시오|십시요|시고|시길|실|셔서)", with: "만져[촉각]", options: .regularExpression)
        return BANNED.filter { s0.contains($0) }
    }
    static func review(_ k: Kos, _ s: Spot, sec: Int, heard: String) -> [String: Any] {
        let chars = k.spotText(s).filter { !$0.isWhitespace }.count
        let cpm = sec > 0 ? Int((Double(chars) / (Double(sec) / 60)).rounded()) : 0
        let room = Int(k.stayOf(s) * 60)
        let over = room > 0 ? sec - room : 0
        let at = ISO8601DateFormatter().string(from: Date())
        return ["sec": sec, "cpm": cpm, "over": over, "heard": heard, "ban": heard.isEmpty ? [String]() : geumji(heard), "at": at]
    }
    static func mal(_ k: Kos, _ s: Spot) -> String {
        guard let p = s.d["prac"] as? [String: Any] else {
            let r = Int(s.n("real") ?? 0)
            return r > 0 ? "\(r)초 걸렸습니다." : ""
        }
        let base = Int(sutja(k.d["cps"]) ?? 330)
        let sec = Int(sutja(p["sec"]) ?? 0), over = Int(sutja(p["over"]) ?? 0), cpm = Int(sutja(p["cpm"]) ?? 0)
        let heard = geulja(p["heard"]), ban = (p["ban"] as? [String]) ?? []
        var m = "\(sec)초 걸렸습니다."
        if over > 0 { m += " 머무는 시간보다 \(over)초 깁니다." } else if k.stayOf(s) > 0 { m += " 머무는 시간 안에 들어갑니다." }
        if cpm > 0 {
            m += " 1분에 \(cpm)자 빠르기로, 기준 \(base)자보다 "
            m += Double(cpm) > Double(base) * 1.15 ? "빠릅니다. 조금 천천히 읽으셔도 됩니다." : (Double(cpm) < Double(base) * 0.8 ? "느립니다." : "알맞습니다.")
        }
        if !heard.isEmpty {
            m += ban.isEmpty ? " 말씀에서 시각 전제 표현은 들리지 않았습니다(참고)." : " 말씀 가운데 시각 전제 표현이 들렸습니다(참고): " + ban.joined(separator: ", ") + "."
        } else { m += " 이 폰은 받아쓰기가 안 되어 말한 낱말은 살피지 못했습니다." }
        return m + " 녹음도 남겨 두었습니다."
    }
}

/// 메모를 담을 칸 — 웹 memoWhere 와 같은 넷
enum MemoKan {
    static let mok: [(String, String)] = [("access", "이동 구간 안내"), ("touch", "만져 볼 것"), ("safe", "조심할 것"), ("text", "현장영상해설 밑글")]
    static func ireum(_ k: String) -> String { mok.first { $0.0 == k }?.1 ?? k }
}
