// 자봉 앱 — 표시마다 짧게 말로 남기기(목소리 토막) 2.10.0 (빌드 261007-I1, 2026-10-06 이사장님 확정 방식)
// 표시를 남기고 그 안내 말이 끝나면 폰이 짧게 귀를 엶. 말이 멈추면 저절로 끊고(1.5초 조용하면), 길어도 10초에서 끊음.
// 4초 안에 아무 말이 없으면 토막을 만들지 않음. 토막은 녹음한 시간이 아니라 그 표시의 걸음 자리(st)에 묶임.
// 폰에는 m4a(AAC)로 담고, 올릴 때 서버가 받아쓰기·mp3 바꾸기·말과 표시 견주기를 함(이사장님 원칙: 소리만은 mp3).
// 녹음 전체를 이어 담지 않음. 다른 귀(말로 표시·네 듣기)가 열려 있으면 열지 않음.
import AVFoundation
import Foundation

struct GrTomak: Codable {
    var st: Int            // 묶인 걸음 자리
    var pyosi: String      // 붙은 표시 이름
    var pail: String       // 폰 안 파일 이름(문서/tomak/)
    var cho: Double        // 길이(초)
    var t: Int             // 그리기 시작부터 몇 초째
    var geul: String? = nil   // 서버 받아쓰기 글(올린 뒤 채워짐)
}

final class TomakNokeum: NSObject, AVAudioRecorderDelegate {
    static let shared = TomakNokeum()

    /// 켜고 끄기 — 기본은 켬(교육 약속 일곱). 시끄러운 곳에서 잠시 끌 수 있음
    static var kyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "jb.tomak") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "jb.tomak") }
    }

    static let pyeolDae: URL = {
        let u = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("tomak", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }()

    private var rec: AVAudioRecorder?
    private var sigye: Timer?
    private var sijakTtae = Date()
    private var malSijak: Date?
    private var majimakSori = Date()
    private var kkeut: ((GrTomak?) -> Void)?
    private var jun: (st: Int, pyosi: String, pail: String, t: Int)?
    private(set) var nokeumJung = false

    static let choedae = 10.0      // 길어도 10초
    static let joyong = 1.5        // 이만큼 조용하면 말이 멈춘 것
    static let gidarim = 4.0       // 이 안에 말이 없으면 토막 없음
    static let malSori: Float = -38 // 이보다 크면 말소리(dB)

    /// 짧게 귀를 엶 — 끝나면 토막(없으면 nil)을 돌려줌
    func yeolgi(st: Int, pyosi: String, gilId: String, t: Int, _ kkeut: @escaping (GrTomak?) -> Void) {
        guard TomakNokeum.kyeojim, !nokeumJung, !MalDeutgi.shared.dolgoItda else { kkeut(nil); return }
        guard AVAudioSession.sharedInstance().recordPermission == .granted else {
            AVAudioSession.sharedInstance().requestRecordPermission { _ in }
            kkeut(nil); return
        }
        let pail = "\(gilId)_\(st)_\(Int(Date().timeIntervalSince1970) % 100000).m4a"
        let u = TomakNokeum.pyeolDae.appendingPathComponent(pail)
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth, .duckOthers])
            try s.setActive(true)
            let r = try AVAudioRecorder(url: u, settings: [
                AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 22050, AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue])
            r.isMeteringEnabled = true
            r.delegate = self
            guard r.record() else { kkeut(nil); return }
            rec = r
        } catch {
            Girok.shared.namgi("jb_tomak_oryu", ["code": (error as NSError).code])
            kkeut(nil); return
        }
        nokeumJung = true
        self.kkeut = kkeut
        jun = (st, pyosi, pail, t)
        sijakTtae = Date(); malSijak = nil; majimakSori = Date()
        sigye = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in self?.salpigi() }
    }

    private func salpigi() {
        guard let r = rec else { return }
        r.updateMeters()
        let db = r.averagePower(forChannel: 0)
        let jinan = Date().timeIntervalSince(sijakTtae)
        if db > TomakNokeum.malSori {
            if malSijak == nil { malSijak = Date() }
            majimakSori = Date()
        }
        if malSijak == nil && jinan >= TomakNokeum.gidarim { matchigi(beorim: true); return }
        if let _ = malSijak, Date().timeIntervalSince(majimakSori) >= TomakNokeum.joyong { matchigi(beorim: false); return }
        if jinan >= TomakNokeum.choedae { matchigi(beorim: false) }
    }

    /// 걷기를 멈추거나 다른 표시를 누르면 바로 닫음
    func dakgi() { if nokeumJung { matchigi(beorim: malSijak == nil) } }

    private func matchigi(beorim: Bool) {
        sigye?.invalidate(); sigye = nil
        let cho = rec?.currentTime ?? 0
        rec?.stop(); rec = nil
        nokeumJung = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let f = kkeut; kkeut = nil
        guard let j = jun else { f?(nil); return }
        jun = nil
        let u = TomakNokeum.pyeolDae.appendingPathComponent(j.pail)
        if beorim || cho < 0.8 {
            try? FileManager.default.removeItem(at: u)
            f?(nil); return
        }
        f?(GrTomak(st: j.st, pyosi: j.pyosi, pail: j.pail, cho: (min(cho, TomakNokeum.choedae) * 10).rounded() / 10, t: j.t))
    }

    /// 길을 지우면 그 길의 토막도 지움
    static func gilJiugi(_ gilId: String) {
        let fm = FileManager.default
        for n in (try? fm.contentsOfDirectory(atPath: pyeolDae.path)) ?? [] where n.hasPrefix(gilId + "_") {
            try? fm.removeItem(at: pyeolDae.appendingPathComponent(n))
        }
    }
}
