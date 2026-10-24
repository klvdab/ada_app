// 1.9.0 (261024-BI11) 무대 영상 녹화 — LVD 송출 아이폰 (이사장님 지시: BYOD 방송도 교육 녹화처럼 영상으로 남길 것)
// 첫 화면의 「무대 영상 녹화」를 켜 두면, BYOD 방송을 시작할 때 뒤 카메라로 무대를 함께 녹화하고 방송을 멈추면 함께 멈춥니다.
//   1. 영상만 찍습니다(소리 없음) — 소리는 방송이 이미 사운드카드·해설 소리로 받고 있어, 서버가 행사 녹음과 합칩니다.
//      카메라가 방송의 소리 설정(AVAudioSession)을 건드리지 않게 automaticallyConfiguresApplicationAudioSession = false.
//   2. 720p, 1분 조각(v0001.mov …)으로 행사 폴더(아이피타임 방식은 이 아이폰 행사 녹음과 같은 폴더)에 씁니다.
//      조각마다 시작·끝 때(1970년부터 밀리초)를 yeongsang.json 에 남깁니다(전기가 끊겨도 앞 조각은 남음).
//   3. 화면이 잠기거나 앱이 뒤로 가면 아이폰이 카메라를 막으므로 깨끗이 멈추고, 화면이 돌아오면 새 조각으로 다시 잇습니다.
//   4. 카메라가 안 되어도 방송은 절대 멈추지 않습니다. 문제는 한 번만 알립니다.
//   5. 올리기(YeongsangOlligi)는 소리 다음에, 따로 한 줄기에서 — 소리 올리기를 붙잡지 않습니다.
import Foundation
import AVFoundation
import UIKit

final class YeongsangNokhwa: NSObject, AVCaptureFileOutputRecordingDelegate {
    static let shared = YeongsangNokhwa()
    static let KI = "yeongsang_nokhwa"   // 첫 화면 켜고 끄기(@AppStorage 와 같은 열쇠)
    static var kyeojim: Bool { UserDefaults.standard.bool(forKey: KI) }

    private let sq = DispatchQueue(label: "byod.yeongsang")
    private var session: AVCaptureSession?
    private var output: AVCaptureMovieFileOutput?
    private var teo: URL?
    private var g: [String: Any] = [:]
    private var hwalseong = false        // 방송과 함께 녹화하기로 한 동안
    private var yeoneunJung = false      // 새 조각을 여는 중(두 번 열지 않게)
    private var jigeumN = 0              // 지금 쓰는 조각 번호(1부터)
    private var bomyeon: [NSObjectProtocol] = []          // 화면 줄기에서만 만짐
    private var sesyeonBomyeon: [NSObjectProtocol] = []   // sq 줄기에서만 만짐
    private var mainKyeojim = false      // 화면 줄기: kyeogi 를 부른 뒤 kkeugi 전까지
    private var dakneunJung = false      // 멈춘 뒤 닫는 중(그사이 다시 켜면 기다렸다 엶)
    private var dwie = false             // sq: 앱이 뒤로 가 있음(화면 꺼짐) — 이때는 카메라를 다시 열지 않음
    private var hwamyeonMeomchum = false // sq: 화면이 꺼져 멈춘 적이 있음(돌아오면 「다시 이었습니다」 한 번)

    // 화면이 보는 값 — 잠금으로 지킴
    private let jamgeum = NSLock()
    private var _sid = ""
    private var _mal = ""                // 문제나 멈춤 사정(없으면 빈 글)
    private var _hapMs: Double = 0       // 다 쓴 조각의 길이 합
    private var _jogakSijakMs: Double = 0 // 지금 쓰는 조각이 시작된 때(안 쓰면 0)
    private var _jogakSu = 0
    private var _majimakAllim = ""
    private var _banghyang: AVCaptureVideoOrientation = .landscapeRight

    var sid: String { jamgeum.lock(); defer { jamgeum.unlock() }; return _sid }

    /// 첫 화면·현장 점검의 한 줄 — 녹화하지 않고 문제도 없으면 빈 글
    var sangtaeMal: String {
        jamgeum.lock(); defer { jamgeum.unlock() }
        let now = Date().timeIntervalSince1970 * 1000
        let ms = _hapMs + (_jogakSijakMs > 0 ? max(0, now - _jogakSijakMs) : 0)
        let bun = Int(ms / 60000)
        if !_sid.isEmpty {
            if !_mal.isEmpty { return "무대 영상: " + _mal + " 지금까지 \(bun)분." }
            if _jogakSijakMs > 0 { return "무대 영상: 녹화 중, 지금까지 \(bun)분" }
            return "무대 영상: 카메라를 여는 중입니다."
        }
        return _mal.isEmpty ? "" : "무대 영상: " + _mal
    }

    private func malNeogi(_ t: String, allil: Bool) {
        jamgeum.lock()
        _mal = t
        let saeroum = allil && !t.isEmpty && t != _majimakAllim
        if saeroum { _majimakAllim = t }
        jamgeum.unlock()
        if saeroum {
            let m = "무대 영상: " + t
            DispatchQueue.main.async { if Malsori.shared.kyeojim { Malsori.shared.mal(m) } else { allyeo(m) } }
        }
    }

    /// 카메라 허락(처음 한 번 묻기) — 첫 화면에서 켤 때 미리 물어 둠
    static func heorakMutgi(_ kkeut: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: kkeut(true)
        case .notDetermined: AVCaptureDevice.requestAccess(for: .video) { ok in DispatchQueue.main.async { kkeut(ok) } }
        default: kkeut(false)
        }
    }

    // MARK: 켜고 끄기 — 화면 줄기(BangIl)에서 부름

    /// 방송이 시작된 뒤 부름. sid0: 아이피타임 방식 행사 녹음의 이름(없으면 이 아이폰이 새로 지음)
    func kyeogi(sid0: String, bangsik: String, jemok: String, haeseolsa: String) {
        if mainKyeojim { return }
        mainKyeojim = true
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
        banghyangGochigi()
        if bomyeon.isEmpty {
            let nc = NotificationCenter.default
            bomyeon.append(nc.addObserver(forName: UIDevice.orientationDidChangeNotification, object: nil, queue: .main) { [weak self] _ in self?.banghyangGochigi() })
            bomyeon.append(nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in self?.dwiroGam() })
            bomyeon.append(nc.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) { self?.apeuroOm() }
            })
        }
        UIApplication.shared.isIdleTimerDisabled = true   // 무대 녹화 동안 화면이 꺼지지 않게(꺼지면 카메라가 멈춤)
        YeongsangNokhwa.heorakMutgi { [weak self] ok in
            guard let self = self, self.mainKyeojim else { return }
            if !ok {
                self.malNeogi("카메라 허락이 없어 녹화하지 못합니다. 아이폰 설정, LVD 송출, 카메라를 켜 주십시오. BYOD 방송은 그대로 이어집니다.", allil: true)
                UIApplication.shared.isIdleTimerDisabled = false
                return
            }
            self.sq.async { self.yeolgi(sid0: sid0, bangsik: bangsik, jemok: jemok, haeseolsa: haeseolsa) }
        }
    }

    /// 방송을 멈출 때 부름
    func kkeugi() {
        if !mainKyeojim { return }
        mainKyeojim = false
        UIApplication.shared.isIdleTimerDisabled = false
        for o in bomyeon { NotificationCenter.default.removeObserver(o) }
        bomyeon.removeAll()
        UIDevice.current.endGeneratingDeviceOrientationNotifications()
        sq.async {
            guard self.hwalseong else { return }
            self.hwalseong = false
            self.dakneunJung = true
            if let o = self.output, o.isRecording { o.stopRecording() }   // 끝 때는 didFinish 에서 적음
            // 조각 마무리를 조금 기다린 뒤 닫음
            self.sq.asyncAfter(deadline: .now() + 2) { self.dakgi() }
        }
    }

    // MARK: 속 — 모두 sq 줄기에서

    private func yeolgi(sid0: String, bangsik: String, jemok: String, haeseolsa: String) {
        if hwalseong { return }
        if dakneunJung {   // 앞 녹화를 닫는 중 — 닫힌 뒤 엶
            sq.asyncAfter(deadline: .now() + 2) { [weak self] in self?.yeolgi(sid0: sid0, bangsik: bangsik, jemok: jemok, haeseolsa: haeseolsa) }
            return
        }
        let now = Date().timeIntervalSince1970
        var s = sid0
        if s.isEmpty {
            var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
            let d = c.dateComponents([.year, .month, .day, .hour, .minute, .second], from: Date(timeIntervalSince1970: now))
            s = String(format: "byod%02d%02d%02d%02d%02d%02d", (d.year ?? 0) % 100, d.month ?? 0, d.day ?? 0, d.hour ?? 0, d.minute ?? 0, d.second ?? 0)
        }
        let d = HaengsaOlligi.teo.appendingPathComponent(s)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        teo = d
        g = ["sid": s, "bangsik": bangsik, "jemok": jemok, "haeseolsa": haeseolsa, "sijak": now, "kkeut": 0,
             "sangtae": "jinhaeng", "jogak": [[String: Any]](), "kkeunkim": [[Any]](),
             "pan": "LVD 송출 아이폰 " + Bang.PAN, "hwajil": "720p", "sori": "eopseum"]
        var hap: Double = 0
        // 같은 방송 중에 껐다 다시 켰으면(같은 행사 폴더) 앞의 기록에 이어 씀 — 앞 조각이 목록에서 빠지지 않게
        if let ob = try? Data(contentsOf: d.appendingPathComponent("yeongsang.json")),
           var og = (try? JSONSerialization.jsonObject(with: ob)) as? [String: Any] {
            og["sangtae"] = "jinhaeng"; og["kkeut"] = 0
            var kk = og["kkeunkim"] as? [[Any]] ?? []
            kk.append([now * 1000, "dasi_kyeom"]); og["kkeunkim"] = kk
            for j in og["jogak"] as? [[String: Any]] ?? [] {
                let js = j["sijak"] as? Double ?? 0, jk = j["kkeut"] as? Double ?? 0
                if js > 0 && jk > js { hap += jk - js }
            }
            g = og
        }
        jamgeum.lock(); _sid = s; _hapMs = hap; _jogakSijakMs = 0; _jogakSu = (g["jogak"] as? [[String: Any]] ?? []).count; _mal = ""; _majimakAllim = ""; jamgeum.unlock()
        jeojang()
        hwalseong = true
        guard sesyeonMandeulgi() else { return }
        session?.startRunning()
        saeJogak()
    }

    /// 뒤 카메라, 720p, 영상만. 실패하면 문제를 알리고 false(방송은 그대로)
    private func sesyeonMandeulgi() -> Bool {
        if session != nil { return true }
        let ss = AVCaptureSession()
        ss.automaticallyConfiguresApplicationAudioSession = false   // 방송의 소리 설정을 건드리지 않음
        ss.beginConfiguration()
        if ss.canSetSessionPreset(.hd1280x720) { ss.sessionPreset = .hd1280x720 }
        guard let cam = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let inp = try? AVCaptureDeviceInput(device: cam), ss.canAddInput(inp) else {
            ss.commitConfiguration()
            malNeogi("뒤 카메라를 열지 못했습니다. BYOD 방송은 그대로 이어집니다.", allil: true)
            return false
        }
        ss.addInput(inp)
        let out = AVCaptureMovieFileOutput()
        out.maxRecordedDuration = CMTime(seconds: 60, preferredTimescale: 600)   // 1분 조각
        guard ss.canAddOutput(out) else {
            ss.commitConfiguration()
            malNeogi("영상 녹화 장치를 붙이지 못했습니다. BYOD 방송은 그대로 이어집니다.", allil: true)
            return false
        }
        ss.addOutput(out)
        // H.264, 초당 약 2메가비트(1분 조각 약 15메가바이트) — 올리기 쉽게
        if let c = out.connection(with: .video) {
            if out.availableVideoCodecTypes.contains(.h264) {
                var st: [String: Any] = [AVVideoCodecKey: AVVideoCodecType.h264]
                if out.supportedOutputSettingsKeys(for: c).contains(AVVideoCompressionPropertiesKey) {
                    st[AVVideoCompressionPropertiesKey] = [AVVideoAverageBitRateKey: 2_000_000]
                }
                out.setOutputSettings(st, for: c)
            }
            if c.isVideoStabilizationSupported { c.preferredVideoStabilizationMode = .standard }
        }
        ss.commitConfiguration()
        let nc = NotificationCenter.default
        sesyeonBomyeon.append(nc.addObserver(forName: .AVCaptureSessionRuntimeError, object: ss, queue: nil) { [weak self] _ in
            self?.sq.asyncAfter(deadline: .now() + 2) { self?.dasiHaebogi() }
        })
        sesyeonBomyeon.append(nc.addObserver(forName: .AVCaptureSessionInterruptionEnded, object: ss, queue: nil) { [weak self] _ in
            self?.sq.asyncAfter(deadline: .now() + 0.5) { self?.dasiHaebogi() }
        })
        session = ss; output = out
        return true
    }

    /// 새 조각 열기 — 화면이 앞에 있고 세션이 돌 때만
    private func saeJogak() {
        guard hwalseong, !yeoneunJung, let ss = session, let out = output, let d = teo, !out.isRecording else { return }
        guard ss.isRunning, !ss.isInterrupted else { return }
        let fm = FileManager.default
        var n = (g["jogak"] as? [[String: Any]] ?? []).count + 1
        while fm.fileExists(atPath: d.appendingPathComponent(String(format: "v%04d.mov", n)).path)
                || fm.fileExists(atPath: d.appendingPathComponent(String(format: "v%04d.mp4", n)).path) { n += 1 }
        let u = d.appendingPathComponent(String(format: "v%04d.mov", n))
        if let c = out.connection(with: .video), c.isVideoOrientationSupported {
            jamgeum.lock(); let o = _banghyang; jamgeum.unlock()
            c.videoOrientation = o
        }
        jigeumN = n
        yeoneunJung = true
        out.startRecording(to: u, recordingDelegate: self)
    }

    /// 화면이 잠기거나 앱이 뒤로 갈 때 — 깨끗이 멈춤(아이폰이 뒤에서는 카메라를 막음)
    private func dwiroGam() {
        sq.async {
            self.dwie = true
            guard self.hwalseong else { return }
            self.hwamyeonMeomchum = true
            self.kkeunkimNeogi("hwamyeon_kkeojim")
            if let o = self.output, o.isRecording { o.stopRecording() }
            self.sq.asyncAfter(deadline: .now() + 1) { if !self.hwalseong { return }; self.session?.stopRunning() }
            self.malNeogi("화면이 꺼져 녹화를 잠시 멈췄습니다. 화면을 켜면 다시 잇습니다. 무대 녹화 동안에는 화면을 켜 두십시오.", allil: false)
        }
    }

    /// 화면이 돌아올 때 — 다시 엶
    private func apeuroOm() {
        sq.async {
            self.dwie = false
            guard self.hwalseong else { return }
            self.dasiHaebogi()
        }
    }

    private func dasiHaebogi() {
        guard hwalseong, !dwie, let ss = session else { return }
        if !ss.isRunning { ss.startRunning() }
        if ss.isRunning && !ss.isInterrupted && !(output?.isRecording ?? false) && !yeoneunJung {
            saeJogak()
            if hwamyeonMeomchum {
                hwamyeonMeomchum = false
                kkeunkimNeogi("dasi")
                malNeogi("", allil: false)
                DispatchQueue.main.async { allyeo("무대 영상 녹화를 다시 이었습니다.") }
            }
        }
    }

    private func dakgi() {
        for o in sesyeonBomyeon { NotificationCenter.default.removeObserver(o) }
        sesyeonBomyeon.removeAll()
        session?.stopRunning()
        session = nil; output = nil
        dakneunJung = false
        g["kkeut"] = Date().timeIntervalSince1970
        g["sangtae"] = "kkeut"
        jeojang()
        jamgeum.lock()
        let bun = Int(_hapMs / 60000), su = _jogakSu
        _sid = ""; _jogakSijakMs = 0
        _mal = "녹화를 마쳤습니다. \(bun)분, 조각 \(su)개. 소리를 올린 다음에 다시 듣기에 올립니다."
        jamgeum.unlock()
        teo = nil
        HaengsaOlligi.shared.sijak(maegam: false)   // 소리가 먼저 올라가고, 그다음 영상(YeongsangOlligi)
        YeongsangOlligi.shared.sijak()             // 소리가 아직이면 이번에는 건너뛰고 1분마다 다시
    }

    private func kkeunkimNeogi(_ mal: String) {
        var kk = g["kkeunkim"] as? [[Any]] ?? []
        kk.append([Date().timeIntervalSince1970 * 1000, mal]); g["kkeunkim"] = kk
        jeojang()
    }

    private func jeojang() {
        guard let d = teo, JSONSerialization.isValidJSONObject(g), let b = try? JSONSerialization.data(withJSONObject: g) else { return }
        try? b.write(to: d.appendingPathComponent("yeongsang.json"), options: .atomic)
    }

    private func banghyangGochigi() {
        let o: AVCaptureVideoOrientation?
        switch UIDevice.current.orientation {
        case .landscapeLeft: o = .landscapeRight
        case .landscapeRight: o = .landscapeLeft
        case .portrait: o = .portrait
        case .portraitUpsideDown: o = .portraitUpsideDown
        default: o = nil   // 눕혀 놓음·모름 — 앞의 값을 그대로
        }
        if let o = o { jamgeum.lock(); _banghyang = o; jamgeum.unlock() }
    }

    // MARK: 녹화 일꾼의 소식(아무 줄기에서나 옴 — sq 로 넘김)

    func fileOutput(_ output: AVCaptureFileOutput, didStartRecordingTo fileURL: URL, from connections: [AVCaptureConnection]) {
        let ms = Date().timeIntervalSince1970 * 1000
        sq.async {
            self.yeoneunJung = false
            var l = self.g["jogak"] as? [[String: Any]] ?? []
            l.append(["n": self.jigeumN, "pail": fileURL.lastPathComponent, "sijak": ms, "kkeut": 0])
            self.g["jogak"] = l
            self.jeojang()
            self.jamgeum.lock(); self._jogakSijakMs = ms; self._jogakSu = l.count; self._mal = ""; self.jamgeum.unlock()   // 다시 써지면 앞의 문제 글을 지움
        }
    }

    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {
        let ms = Date().timeIntervalSince1970 * 1000
        var doem = error == nil
        if let e = error as NSError? { doem = (e.userInfo[AVErrorRecordingSuccessfullyFinishedKey] as? Bool) ?? false }
        sq.async {
            self.yeoneunJung = false
            var l = self.g["jogak"] as? [[String: Any]] ?? []
            if let i = l.lastIndex(where: { ($0["pail"] as? String) == outputFileURL.lastPathComponent }) {
                l[i]["kkeut"] = ms
                if !doem { l[i]["kkeunkim"] = 1 }
                self.g["jogak"] = l
                self.jamgeum.lock()
                if self._jogakSijakMs > 0 { self._hapMs += max(0, ms - self._jogakSijakMs) }
                self._jogakSijakMs = 0
                self.jamgeum.unlock()
            }
            self.jeojang()
            guard self.hwalseong else { return }
            if doem { self.saeJogak(); return }   // 1분이 차서 끝났으면 곧바로 다음 조각
            // 잘못 끝남 — 화면이 꺼져 막힌 것이 아니면 문제로 한 번 알리고, 5초 뒤 다시 해 봄(쉼 없이 되풀이하지 않게)
            if let ss = self.session, ss.isRunning, !ss.isInterrupted {
                self.malNeogi("녹화 조각을 쓰지 못했습니다. 저장 공간을 확인하십시오. BYOD 방송은 그대로 이어집니다.", allil: true)
            }
            self.sq.asyncAfter(deadline: .now() + 5) { self.dasiHaebogi() }
        }
    }
}

/// 1.9.0 (261024-BI11) 무대 영상 녹화 — 영상 조각을 LVD 방송 다시 듣기(나스 byod_olligi.php)에 올림.
/// 같은 행사의 소리(녹음·결과보고·기록)가 다 올라간 뒤에만, 따로 한 줄기에서 올립니다(소리를 붙잡지 않음). 못 올리면 1분마다 다시.
/// 보내는 것: step=yeongsang&n=조각번호(1부터)&ext=mp4|mov&sijak=밀리초&gil=바이트 (몸통 = 조각 파일 하나 통째로),
///           다 올린 뒤 step=yeongsang_girok (몸통 = yeongsang.json). 자세한 것은 /home/claude/gochim/byod_ios_yeongsang_NOTE.txt
final class YeongsangOlligi {
    static let shared = YeongsangOlligi()
    private let q = DispatchQueue(label: "byod.yeongsang.olligi")
    private var dolgo = false
    private let jamgeum = NSLock()
    private var allin = Set<String>()   // q 줄기: 「아직 못 올림」을 이미 알린 행사(1분마다 되풀이해 떠들지 않게)

    /// 아직 다 올리지 않은 영상 행사가 있는가(1분마다 다시 올리기에 씀)
    static var nameum: Bool {
        let fm = FileManager.default
        for nm in (try? fm.contentsOfDirectory(atPath: HaengsaOlligi.teo.path)) ?? [] {
            let d = HaengsaOlligi.teo.appendingPathComponent(nm)
            if fm.fileExists(atPath: d.appendingPathComponent("yeongsang.json").path) && !fm.fileExists(atPath: d.appendingPathComponent("yeongsang_olim").path) { return true }
        }
        return false
    }

    func sijak() {
        jamgeum.lock()
        if dolgo { jamgeum.unlock(); return }
        dolgo = true
        jamgeum.unlock()
        q.async {
            self.dolgi()
            self.jamgeum.lock(); self.dolgo = false; self.jamgeum.unlock()
        }
    }

    private func mal(_ t: String) { DispatchQueue.main.async { if Malsori.shared.kyeojim { Malsori.shared.mal(t) } else { allyeo(t) } } }

    private func bonae(_ q: String, _ u: URL?, _ mom: Data?) -> Bool {
        guard let url = URL(string: q) else { return false }
        var rq = URLRequest(url: url); rq.httpMethod = "POST"; rq.timeoutInterval = 600
        let s = DispatchSemaphore(value: 0)
        var ok = false
        if let u = u {
            URLSession.shared.uploadTask(with: rq, fromFile: u) { d, _, _ in
                ok = (d.flatMap { String(data: $0, encoding: .utf8) } ?? "").contains("\"ok\":true")
                s.signal()
            }.resume()
        } else {
            URLSession.shared.uploadTask(with: rq, from: mom ?? Data()) { d, _, _ in
                ok = (d.flatMap { String(data: $0, encoding: .utf8) } ?? "").contains("\"ok\":true")
                s.signal()
            }.resume()
        }
        _ = s.wait(timeout: .now() + 610)
        return ok
    }

    /// .mov 를 다시 누르지 않고 그릇만 .mp4 로 옮김(가벼움). 안 되면 nil — 그때는 .mov 를 그대로 올림
    private func mp4Mandeulgi(_ mov: URL) -> URL? {
        let mp4 = mov.deletingPathExtension().appendingPathExtension("mp4")
        if FileManager.default.fileExists(atPath: mp4.path) { return mp4 }
        guard let ex = AVAssetExportSession(asset: AVURLAsset(url: mov), presetName: AVAssetExportPresetPassthrough),
              ex.supportedFileTypes.contains(.mp4) else { return nil }
        let tmp = mov.deletingPathExtension().appendingPathExtension("mp4.tmp")
        try? FileManager.default.removeItem(at: tmp)
        ex.outputURL = tmp
        ex.outputFileType = .mp4
        let s = DispatchSemaphore(value: 0)
        ex.exportAsynchronously { s.signal() }
        _ = s.wait(timeout: .now() + 120)
        let keugi = ((try? FileManager.default.attributesOfItem(atPath: tmp.path))?[.size] as? NSNumber)?.intValue ?? 0
        guard ex.status == .completed, keugi > 0,
              (try? FileManager.default.moveItem(at: tmp, to: mp4)) != nil else {
            try? FileManager.default.removeItem(at: tmp)
            return nil
        }
        return mp4
    }

    /// 어느 행사(sid)로 올릴지 — 소리가 다 올라간 행사만. 아직 못 정하면 nil(다음에 다시)
    private func gyeoljeong(_ nm: String, _ y: [String: Any]) -> String? {
        let fm = FileManager.default
        let d = HaengsaOlligi.teo.appendingPathComponent(nm)
        if fm.fileExists(atPath: d.appendingPathComponent("girok.json").path) {   // 아이피타임 방식 — 같은 폴더의 녹음
            return fm.fileExists(atPath: d.appendingPathComponent("olim").path) ? nm : nil
        }
        if fm.fileExists(atPath: d.appendingPathComponent("girok_jinhaeng.json").path) { return nil }   // 녹음 마감을 기다림
        // 플린트 2 방식 — 공유기가 녹음한 행사 가운데, 영상 시작 때가 그 행사 안(앞 5분·뒤 2분 여유)에 드는 것
        let ys = y["sijak"] as? Double ?? 0
        if let h = HaengsaOlligi.mokrok().first(where: { $0.olim && ys - $0.sijak.timeIntervalSince1970 > -300 && ys - $0.sijak.timeIntervalSince1970 < $0.gilee + 120 }) { return h.sid }
        // 공유기 시각이 어긋나 못 맞추면, 6시간 뒤 이 아이폰이 지은 이름으로 올림(서버가 sijak 으로 맞춤)
        if Date().timeIntervalSince1970 - ys > 6 * 3600 && !HaengsaOlligi.nameum { return nm }
        return nil
    }

    private func dolgi() {
        let fm = FileManager.default
        for nm in ((try? fm.contentsOfDirectory(atPath: HaengsaOlligi.teo.path)) ?? []).sorted() {
            if Bang.shared.keu { return }   // 방송 중에는 올리지 않음(인터넷 방송과 다투지 않게)
            let d = HaengsaOlligi.teo.appendingPathComponent(nm)
            let yu = d.appendingPathComponent("yeongsang.json")
            guard nm != YeongsangNokhwa.shared.sid, fm.fileExists(atPath: yu.path),
                  !fm.fileExists(atPath: d.appendingPathComponent("yeongsang_olim").path),
                  let yb = try? Data(contentsOf: yu), let y = (try? JSONSerialization.jsonObject(with: yb)) as? [String: Any],
                  let sid = gyeoljeong(nm, y) else { continue }
            let q0 = "\(HaengsaOlligi.NAS)?k=\(HaengsaOlligi.YEOLSOE)&sid=\(sid)&sid_ap=\(nm)"
            var da = true
            for j in y["jogak"] as? [[String: Any]] ?? [] {
                if Bang.shared.keu { return }
                guard let pail = j["pail"] as? String, let n = (j["n"] as? Int) ?? (j["n"] as? NSNumber)?.intValue else { continue }
                let mov = d.appendingPathComponent(pail)
                let pyosi = d.appendingPathComponent(String(format: "v%04d.olim", n))
                if fm.fileExists(atPath: pyosi.path) { continue }
                let mp4 = d.appendingPathComponent(String(format: "v%04d.mp4", n))
                var u: URL? = fm.fileExists(atPath: mp4.path) ? mp4 : nil
                if u == nil && fm.fileExists(atPath: mov.path) { u = mp4Mandeulgi(mov) ?? mov }
                guard let up = u else { continue }
                let gil = ((try? fm.attributesOfItem(atPath: up.path))?[.size] as? NSNumber)?.intValue ?? 0
                if gil <= 0 { continue }   // 빈 조각은 건너뜀
                let sj = Int64(j["sijak"] as? Double ?? 0)
                if bonae("\(q0)&step=yeongsang&n=\(n)&ext=\(up.pathExtension)&sijak=\(sj)&gil=\(gil)", up, nil) {
                    try? Data("1".utf8).write(to: pyosi)
                    if up.pathExtension == "mp4" && fm.fileExists(atPath: mov.path) { try? fm.removeItem(at: mov) }   // 같은 영상의 옛 그릇 — 나스에 올라간 뒤에만 지움(mp4 는 남김)
                } else { da = false; break }
            }
            if da && bonae("\(q0)&step=yeongsang_girok", nil, yb) {
                try? Data("1".utf8).write(to: d.appendingPathComponent("yeongsang_olim"))
                mal("\(y["jemok"] as? String ?? "") 무대 영상을 LVD 방송 다시 듣기에 올렸습니다.")
            } else {
                if !allin.contains(nm) { allin.insert(nm); mal("무대 영상을 아직 올리지 못했습니다. 인터넷이 되면 저절로 다시 올립니다.") }
                return
            }
        }
    }
}
