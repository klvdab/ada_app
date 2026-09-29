// 음악·방송 엔진 — 앱 2.8.0 (빌드 260928-10), 이사장님 승인(가1·나1).
// 길 위의 음악(나스 음악·누구나 음악), 라디오, TV 소리, 지금 세상 이야기(기사 읽기)를 한 곳에서 틉니다.
// ① 화면을 옮겨도, 폰을 잠가도, 길 찾기 탭에서 안내를 받아도 끊기지 않음(앱 자체 재생기)
// ② 길눈 안내 말이 나오면 방송 소리를 작게 줄였다가 끝나면 되돌림, 경고만 멈췄다가 말하고 다시 틂(가1)
// ③ 말로 하기가 명령을 듣는 동안과 긴급통화 동안에는 잠시 멈춤
// ④ 끊기거나 16초 넘게 제자리면 저절로 다시 이음(라디오·TV는 새 주소를 받아)
// ⑤ 여정이 아닐 때 이어폰 단추가 방송을 다룸(나1 — RemoteDanchu가 나눠 줌)
// 나스 쪽은 손대지 않고 웹 길눈과 같은 자료 창고(eumak.php, nuguna, sori.php, sesang.php, gojang.php)를 씁니다.
import Foundation
import AVFoundation
import MediaPlayer
import UIKit
import Combine

enum BangsongJong: String {
    case eopseum, eumak, nuguna, radio, tv, gisa
}

struct EumakGok: Identifiable, Hashable {
    let f: String
    let ireum: String
    var id: String { f }
}

struct NugunaGok: Identifiable, Hashable {
    let sn: String
    let ireum: String
    let jakgok: String
    let jeojakja: String
    let jogeon: String
    let bun: String
    let mp3: String
    var id: String { sn + mp3 }
    var pyosi: String {
        "저작자 \(jeojakja)" + (jakgok.isEmpty ? "" : ", 원곡 \(jakgok)") + ", 출처 공유마당(한국저작권위원회), 이용 조건 \(jogeon)"
    }
}

struct Chaeneol: Identifiable, Hashable {
    let id: String
    let name: String
    let freq: String
    let kind: String   // radio, tv
    var julMal: String { freq.isEmpty ? name : "\(name) \(freq)" }
}

struct Gisa: Identifiable, Hashable {
    let saem: String
    let jemok: String
    let yoyak: String
    let juso: String
    let ttaeMal: String
    let sokbo: Bool
    var id: String { juso + jemok }
    var julMal: String { jemok + (ttaeMal.isEmpty ? "" : ", \(saem) \(ttaeMal)") }
}

final class BangsongEngine: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = BangsongEngine()

    // MARK: 화면이 보는 것
    @Published private(set) var jong: BangsongJong = .eopseum
    @Published private(set) var jemok = ""            // 지금 나오는 곡·채널·기사 이름
    @Published private(set) var sangtaeMal = ""       // 한 번만 알릴 상태(연결 중, 다시 잇는 중)
    @Published private(set) var meomchum = false      // 이용자가 멈춤
    @Published private(set) var eumakDeureom = false  // 나스 음악 열쇠로 들어옴
    @Published private(set) var cheoumIra = false     // 나스 음악 열쇠가 아직 정해지지 않음
    @Published private(set) var galraeDeul: [String] = []
    @Published private(set) var gokMok: [EumakGok] = []
    @Published private(set) var gokI = -1
    @Published private(set) var jadoKyeojim = false
    @Published private(set) var jadoJul = ""
    @Published private(set) var nugunaPyosi = ""
    @Published private(set) var chaeneolDeul: [Chaeneol] = []
    @Published private(set) var chaeneolNote = ""
    @Published private(set) var gisaMok: [Gisa] = []
    @Published private(set) var gisaI = -1
    @Published private(set) var gisaMeori = ""
    @Published private(set) var gisaBon: [String] = []
    @Published private(set) var gisaIlkneun = false
    @Published var yeongsangKkeum: Bool {
        didSet {
            UserDefaults.standard.set(yeongsangKkeum, forKey: "gn.yeongsangKkeum")
            hwajilMatchugi()
        }
    }

    let player = AVPlayer()
    private let gisaSynth = AVSpeechSynthesizer()
    private var gisaMajimak: AVSpeechUtterance?
    private var naebuMeomchum = Set<String>()   // gyeonggo, deutgi, tonghwa
    private var malJung = false
    private var jigeumChaeneol: Chaeneol?
    private var nuguna: [NugunaGok] = []
    private var nugunaJul: [NugunaGok] = []
    private var nugunaI = -1
    private var kkeutGwanchal: Any?
    private var sigye: Timer?
    private var jadoSigye: Timer?
    private var jadoKey = ""
    private var majimakJari = -1.0
    private var majimakUmjik = Date()
    private var ieumSu = 0
    private var meomchunTtae: Date?
    private var ssak = Set<AnyCancellable>()
    private var sttGwanchal: NSKeyValueObservation?
    private var tk: String { Yeolsoe.ilgi("eumakTk") ?? "" }

    static let ppuri = "https://lvd.ada.or.kr"

    override init() {
        yeongsangKkeum = (UserDefaults.standard.object(forKey: "gn.yeongsangKkeum") as? Bool) ?? false
        super.init()
        gisaSynth.delegate = self
        player.automaticallyWaitsToMinimizeStalling = true
    }

    func sijak() {
        // 긴급통화 동안에는 잠시 멈춤
        GinGeup.shared.$sangtae
            .map { $0 != .eopseum }
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] t in self?.naebu("tonghwa", t) }
            .store(in: &ssak)
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 4, repeats: true) { [weak self] _ in self?.jikigi() }
    }

    /// 지금 소리가 나고 있거나 멈춰 둔 방송이 있음
    var itda: Bool { jong != .eopseum }
    /// 지금 실제로 소리를 내는 중
    var naneunJung: Bool { jong != .eopseum && !meomchum && naebuMeomchum.isEmpty }

    // MARK: 소리 자리

    /// 방송이 쓸 소리 자리 — 재생 전용, 다른 앱과 섞지 않음(이어폰 단추가 길눈으로 오게)
    func sesyeonJapgi() {
        let s = AVAudioSession.sharedInstance()
        if MalDeutgi.shared.dolgoItda && s.category == .playAndRecord { try? s.setActive(true); return }   // 말로 하기 마이크가 열려 있음(2.12.1 이어폰 단추보다 먼저)
        if RemoteDanchu.shared.kyeojim { RemoteDanchu.shared.sesyeonJapgi(); return }
        do {
            if s.category != .playback { try s.setCategory(.playback, mode: .default, options: []) }
            try s.setActive(true)
        } catch {
            Girok.shared.namgi("bangsong_oryu", ["dan": "sesyeon", "code": (error as NSError).code])
        }
    }

    // MARK: 길눈 말과 함께(가1)

    /// 길눈이 말을 시작함 — 보통 말은 소리를 작게, 경고는 멈춤
    func malSijak(_ geup: MalGeup) {
        guard itda else { return }
        malJung = true
        if geup == .gyeonggo {
            naebu("gyeonggo", true)
        } else if jong == .gisa {
            if gisaSynth.isSpeaking && !gisaSynth.isPaused { gisaSynth.pauseSpeaking(at: .word) }
        } else {
            player.volume = 0.2
        }
    }

    /// 길눈이 말을 마침 — 되돌림
    func malKkeut() {
        guard malJung else { return }
        malJung = false
        naebu("gyeonggo", false)
        player.volume = 1.0
        if jong == .gisa && gisaIlkneun && !meomchum && naebuMeomchum.isEmpty && gisaSynth.isPaused {
            gisaSynth.continueSpeaking()
        }
    }

    /// 말로 하기가 명령을 듣는 동안 멈춤
    func deutgiMeomchum(_ t: Bool) { naebu("deutgi", t) }
    /// 2.12.2 "하이 길눈"을 들은 때부터 명령을 마칠 때까지 멈춤
    func bureumMeomchum(_ t: Bool) { naebu("bureum", t) }

    private func naebu(_ k: String, _ t: Bool) {
        let jeon = naebuMeomchum.isEmpty
        if t { naebuMeomchum.insert(k) } else { naebuMeomchum.remove(k) }
        let hu = naebuMeomchum.isEmpty
        guard itda, !meomchum, jeon != hu else { return }
        if !hu {
            meomchunTtae = Date()
            if jong == .gisa { if gisaSynth.isSpeaking { gisaSynth.pauseSpeaking(at: .immediate) } } else { player.pause() }
        } else {
            dasiTeulgi()
        }
    }

    /// 멈췄던 것을 다시 — 생방송을 1분 넘게 멈췄으면 새로 이음
    private func dasiTeulgi() {
        let oraeMeomchum = Date().timeIntervalSince(meomchunTtae ?? Date()) > 60
        meomchunTtae = nil
        sesyeonJapgi()
        switch jong {
        case .gisa:
            if gisaSynth.isPaused { gisaSynth.continueSpeaking() }
        case .radio, .tv:
            if oraeMeomchum, let c = jigeumChaeneol { chaeneolTeulgi(c, malHagi: false) } else { player.play() }
        case .eumak, .nuguna:
            player.play()
        case .eopseum:
            break
        }
        pyosiGaengsin()
    }

    // MARK: 이용자 단추

    /// 멈춤과 다시 틀기
    func meomchumTogeul() {
        guard itda else { return }
        if meomchum {
            meomchum = false
            if naebuMeomchum.isEmpty { dasiTeulgi() }
        } else {
            meomchum = true
            meomchunTtae = Date()
            if jong == .gisa { gisaSynth.pauseSpeaking(at: .word) } else { player.pause() }
            pyosiGaengsin()
        }
    }

    /// 다음 곡·다음 채널·다음 기사
    func daeum() {
        switch jong {
        case .eumak: gokTeulgi(gokI + 1)
        case .nuguna: nugunaDaeum()
        case .radio, .tv: chaeneolBakkugi(1)
        case .gisa: gisaYeolgi(gisaI + 1)
        case .eopseum: SoriEngine.shared.mal("지금 틀고 있는 것이 없습니다.", .jeongbo)
        }
    }

    /// 이전 곡·앞 채널·기사 처음부터
    func ijeon() {
        switch jong {
        case .eumak: gokTeulgi(max(0, gokI - 1))
        case .nuguna: nugunaI = max(0, nugunaI - 2); nugunaDaeum()
        case .radio, .tv: chaeneolBakkugi(-1)
        case .gisa: gisaCheoeumButeo()
        case .eopseum: break
        }
    }

    /// 모두 그만
    func geuman(malHagi: Bool = true) {
        guard itda else { return }
        let jeon = jong
        jadoKkeugi(malHagi: false)
        player.pause()
        player.replaceCurrentItem(with: nil)
        if gisaSynth.isSpeaking { gisaSynth.stopSpeaking(at: .immediate) }
        gisaIlkneun = false
        jong = .eopseum
        jemok = ""
        sangtaeMal = ""
        meomchum = false
        nugunaPyosi = ""
        kkeutGwanchalChiugi()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        RemoteDanchu.shared.matchugi()
        if malHagi {
            let m: String
            switch jeon {
            case .radio: m = "라디오를 껐습니다."
            case .tv: m = "TV를 껐습니다."
            case .gisa: m = "기사 읽기를 그만둡니다."
            default: m = "음악을 껐습니다."
            }
            SoriEngine.shared.mal(m, .jeongbo)
        }
        Girok.shared.namgi("bangsong_geuman", ["jong": jeon.rawValue])
    }

    /// 지금 무엇이 나오는가
    var jigeumMal: String {
        switch jong {
        case .eopseum: return "지금 틀고 있는 것이 없습니다."
        case .eumak, .nuguna: return "지금 곡은 \(jemok)입니다."
        case .radio: return "지금 \(jemok)을 듣고 계십니다."
        case .tv: return "지금 \(jemok)을 틀어 두셨습니다."
        case .gisa: return "지금 읽는 기사는 \(jemok)입니다."
        }
    }

    // MARK: 재생 속

    private func teulgi(_ url: URL, _ j: BangsongJong, _ ireum: String, seek: Double = 0) {
        if jong == .gisa && j != .gisa {
            gisaSynth.stopSpeaking(at: .immediate)
            gisaIlkneun = false
        }
        sesyeonJapgi()
        kkeutGwanchalChiugi()
        let item = AVPlayerItem(url: url)
        item.preferredForwardBufferDuration = 20
        player.replaceCurrentItem(with: item)
        jong = j
        jemok = ireum
        meomchum = false
        naebuMeomchum.remove("gyeonggo")
        hwajilMatchugi()
        majimakJari = -1
        majimakUmjik = Date()
        kkeutGwanchal = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.kkeunnam()
        }
        sttGwanchal = item.observe(\.status, options: [.new]) { [weak self] it, _ in
            DispatchQueue.main.async {
                guard let s = self, s.player.currentItem === it else { return }
                if it.status == .readyToPlay {
                    if seek > 1 { s.player.seek(to: CMTime(seconds: seek, preferredTimescale: 600)) }
                    s.sangtaeMal = ""
                } else if it.status == .failed {
                    Girok.shared.namgi("bangsong_oryu", ["dan": "item", "jong": j.rawValue])
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) { s.dasiIeum() }
                }
            }
        }
        if naebuMeomchum.isEmpty { player.play() }
        player.volume = malJung ? 0.2 : 1.0
        pyosiGaengsin()
        RemoteDanchu.shared.matchugi()
    }

    private func kkeutGwanchalChiugi() {
        if let k = kkeutGwanchal { NotificationCenter.default.removeObserver(k) }
        kkeutGwanchal = nil
        sttGwanchal?.invalidate()
        sttGwanchal = nil
    }

    private func kkeunnam() {
        switch jong {
        case .eumak:
            if gokI + 1 < gokMok.count {
                gokTeulgi(gokI + 1, malHagi: false)
            } else {
                SoriEngine.shared.mal("목록의 마지막 곡까지 들으셨습니다.", .jeongbo)
                geuman(malHagi: false)
            }
        case .nuguna:
            nugunaDaeum(malHagi: false)
        case .tv, .radio:
            // MBC 듣는방송24처럼 프로그램 단위로 나오는 채널은 끝나면 다음 프로그램으로
            if let c = jigeumChaeneol {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self.chaeneolTeulgi(c, malHagi: false) }
            }
        default:
            break
        }
    }

    /// 4초마다 — 멈춤 지킴이
    private func jikigi() {
        guard jong != .eopseum, jong != .gisa, !meomchum, naebuMeomchum.isEmpty else {
            majimakUmjik = Date()
            return
        }
        let t = player.currentTime().seconds
        if t.isFinite && t > max(majimakJari, 0) + 0.5 {   // 2.12.0 새로 이은 흐름이 막 시작한 것은 움직임으로 치지 않음
            majimakJari = t
            majimakUmjik = Date()
            ieumSu = 0
            return
        }
        if Date().timeIntervalSince(majimakUmjik) > 16 { dasiIeum() }
    }

    /// 다시 잇기 — 음악은 멈춘 자리부터, 라디오·TV는 새 주소로
    private func dasiIeum() {
        guard jong != .eopseum, jong != .gisa else { return }
        ieumSu += 1
        majimakUmjik = Date()
        Girok.shared.namgi("bangsong_ieum", ["jong": jong.rawValue, "su": ieumSu])
        if ieumSu == 2 {
            sangtaeMal = "끊겨서 다시 잇는 중입니다."
            SoriEngine.shared.mal("끊겨서 다시 잇는 중입니다.", .jeongbo)
        }
        switch jong {
        case .radio, .tv:
            if let c = jigeumChaeneol { chaeneolTeulgi(c, malHagi: false) }
        case .eumak:
            let jari = max(0, majimakJari)
            if gokI >= 0 && gokI < gokMok.count, let u = gokJuso(gokMok[gokI]) {
                teulgi(u, .eumak, gokMok[gokI].ireum, seek: jari)
            }
        case .nuguna:
            let jari = max(0, majimakJari)
            if nugunaI >= 0 && nugunaI < nugunaJul.count, let u = URL(string: BangsongEngine.ppuri + "/jeom/nuguna/" + nugunaJul[nugunaI].mp3) {
                teulgi(u, .nuguna, nugunaJul[nugunaI].ireum, seek: jari)
            }
        default:
            break
        }
    }

    /// TV 영상 끄기 — 가장 낮은 화질 단계를 골라 데이터를 아낌(영상 자체는 막지 않음)
    private func hwajilMatchugi() {
        guard let it = player.currentItem else { return }
        it.preferredPeakBitRate = (jong == .tv && yeongsangKkeum) ? 1 : 0
    }

    // MARK: 잠금 화면에 보이는 것

    func pyosiGaengsin() {
        guard jong != .eopseum else { return }
        if RemoteDanchu.shared.kyeojim { return }   // 여정 중에는 길눈 안내가 그 자리를 씀
        let bu: String
        switch jong {
        case .radio: bu = "길눈 — 라디오 듣기"
        case .tv: bu = "길눈 — TV 보기"
        case .gisa: bu = "길눈 — 지금 세상 이야기"
        default: bu = "길눈 — 길 위의 음악"
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: jemok,
            MPMediaItemPropertyArtist: bu,
            MPNowPlayingInfoPropertyPlaybackRate: (meomchum ? 0.0 : 1.0),
            MPNowPlayingInfoPropertyIsLiveStream: (jong == .radio || jong == .tv)
        ]
    }

    // MARK: 나스에 묻기

    /// 주소 — 값은 모두 글자·숫자 밖을 퍼센트로(파일 이름의 +·& 도 안전하게)
    static func juso(_ pail: String, _ q: [(String, String)]) -> URL? {
        var hm = CharacterSet.alphanumerics
        hm.insert(charactersIn: "-._~")
        let s = q.map { "\($0.0)=\($0.1.addingPercentEncoding(withAllowedCharacters: hm) ?? "")" }.joined(separator: "&")
        return URL(string: BangsongEngine.ppuri + pail + (s.isEmpty ? "" : "?" + s))
    }

    private static func mutgi(_ pail: String, _ q: [(String, String)]) async -> [String: Any]? {
        guard let u = juso(pail, q + [("_", String(Int(Date().timeIntervalSince1970 * 1000)))]) else { return nil }
        var r = URLRequest(url: u)
        r.cachePolicy = .reloadIgnoringLocalCacheData
        r.timeoutInterval = 20
        guard let dr = try? await URLSession.shared.data(for: r),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        let d = dr.0
        return (try? JSONSerialization.jsonObject(with: d)) as? [String: Any]
    }

    private func eumakMutgi(_ a: String, _ q: [(String, String)] = []) async -> [String: Any]? {
        var qq: [(String, String)] = [("a", a)]
        if !tk.isEmpty { qq.append(("tk", tk)) }
        return await BangsongEngine.mutgi("/jeom/eumak.php", qq + q)
    }

    private static func gokDeul(_ o: [String: Any]?) -> [EumakGok] {
        ((o?["rows"] as? [[String: Any]]) ?? []).compactMap { r in
            guard let f = r["f"] as? String, !f.isEmpty else { return nil }
            return EumakGok(f: f, ireum: (r["ireum"] as? String) ?? f)
        }
    }

    // MARK: 길 위의 음악 — 나스 음악(열쇠)

    /// 화면이 열릴 때 — 열쇠가 있으면 갈래를 받아 둠
    func eumakJunbi() async {
        let s = await eumakMutgi("sangtae")
        let cheoum = !((s?["jeonghaessna"] as? Bool) ?? true)
        await MainActor.run { self.cheoumIra = cheoum }
        guard !tk.isEmpty else { await MainActor.run { self.eumakDeureom = false }; return }
        let g = await eumakMutgi("galrae")
        let ok = (g?["ok"] as? Bool) == true
        await MainActor.run {
            self.eumakDeureom = ok
            if ok { self.galraeDeul = (g?["rows"] as? [String]) ?? [] }
        }
    }

    /// 열쇠 넣기 — 처음이면 그 열쇠로 정함
    func yeolsoeNeoki(_ pw: String) async -> String? {
        let s = await BangsongEngine.mutgi("/jeom/eumak.php", [("a", "sangtae")])
        guard s != nil else { return "자료 창고에 닿지 못했습니다. 통신을 확인해 주십시오." }
        let a = ((s?["jeonghaessna"] as? Bool) ?? false) ? "deulgi" : "pwset"
        let k = await BangsongEngine.mutgi("/jeom/eumak.php", [("a", a), ("pw", pw)])
        guard (k?["ok"] as? Bool) == true, let t = k?["tk"] as? String, !t.isEmpty else {
            return (k?["error"] as? String) ?? "들어가지 못했습니다. 열쇠를 다시 넣어 주십시오."
        }
        Yeolsoe.sseugi("eumakTk", t)
        await eumakJunbi()
        return nil
    }

    func temaDeul(_ g: String) async -> [String] {
        let j = await eumakMutgi("tema", [("g", g)])
        return (j?["rows"] as? [String]) ?? []
    }

    /// 갈래·테마로 이어서 틀기
    func galraeTeulgi(_ g: String, _ t: String) async {
        await MainActor.run { SoriEngine.shared.mal("노래를 고르고 있습니다.", .jeongbo) }
        var q: [(String, String)] = [("g", g)]
        if !t.isEmpty { q.append(("t", t)) }
        let j = await eumakMutgi("gok", q)
        await MainActor.run { self.mokBadeum(j, apMal: "") }
    }

    /// 고장 이름으로 찾기
    func gojangChatgi(_ q: String) async {
        await MainActor.run { SoriEngine.shared.mal("\(q) 노래를 찾고 있습니다.", .jeongbo) }
        let j = await eumakMutgi("gojang", [("q", q)])
        await MainActor.run {
            if BangsongEngine.gokDeul(j).isEmpty {
                SoriEngine.shared.mal("\(q)\(MalHagi.i(q)) 든 노래를 찾지 못했습니다.")
            } else {
                self.mokBadeum(j, apMal: "\(q) 노래 \(BangsongEngine.gokDeul(j).count)곡을 찾았습니다. ")
            }
        }
    }

    /// 말로 찾기 — 가수·곡 이름·주제(웹 길눈과 같은 서버 찾기)
    func malChatgi(_ q: String) async -> String {
        let j = await eumakMutgi("chatgi", [("q", q)])
        if (j?["gallim"] as? Bool) == true { return (j?["mal"] as? String) ?? "어느 쪽으로 틀까요? 다시 말씀해 주십시오." }
        let ls = BangsongEngine.gokDeul(j)
        guard !ls.isEmpty else { return "찾는 곡이 없습니다. 가수나 곡 이름을 다시 말씀해 주십시오." }
        let n = (j?["su"] as? Int) ?? ls.count
        let ir = (j?["ireum"] as? String) ?? q
        let kind = (j?["kind"] as? String) ?? ""
        var m: String
        switch kind {
        case "주제": m = ir + (ir.hasSuffix("음악") ? "" : " 음악") + " \(n)곡을 찾았습니다. 섞어서 틉니다."
        case "가수": m = "\(ir) 노래 \(n)곡을 찾았습니다. 섞어서 틉니다."
        case "작곡가": m = "\(ir) 작품 \(n)곡을 찾았습니다. 섞어서 틉니다."
        case "제목": m = "\(ir), \(n)곡을 찾았습니다. 같은 제목의 곡부터 틉니다."
        default: m = "\(ir), \(n)곡을 찾았습니다. 섞어서 틉니다."
        }
        m += " 첫 곡은 \(ls[0].ireum)입니다."
        await MainActor.run {
            self.gokMok = ls
            self.gokTeulgi(0, malHagi: false)
        }
        return m
    }

    private func mokBadeum(_ j: [String: Any]?, apMal: String) {
        if (j?["ok"] as? Bool) != true {
            let e = (j?["error"] as? String) ?? "불러오지 못했습니다. 통신을 확인해 주십시오."
            if (j?["error"] as? String)?.contains("열쇠") == true { Yeolsoe.sseugi("eumakTk", ""); eumakDeureom = false }
            SoriEngine.shared.mal(e)
            return
        }
        let ls = BangsongEngine.gokDeul(j)
        guard !ls.isEmpty else { SoriEngine.shared.mal("그 자리에 노래가 없습니다."); return }
        gokMok = ls
        gokTeulgi(0, malHagi: false)
        SoriEngine.shared.mal(apMal + "첫 곡은 \(ls[0].ireum)입니다.", .jeongbo)
    }

    private func gokJuso(_ g: EumakGok) -> URL? {
        if let p = BangsongEngine.batadun(g.f) { return p }
        return BangsongEngine.juso("/jeom/eumak.php", [("a", "teul"), ("tk", tk), ("f", g.f), ("q", "g")])
    }

    func gokTeulgi(_ i: Int, malHagi: Bool = true) {
        guard i >= 0, i < gokMok.count else {
            SoriEngine.shared.mal("마지막 곡입니다. 목록의 끝입니다.", .jeongbo)
            return
        }
        guard let u = gokJuso(gokMok[i]) else { return }
        gokI = i
        teulgi(u, .eumak, gokMok[i].ireum)
        if malHagi { SoriEngine.shared.mal(gokMok[i].ireum, .jeongbo) }
        // 다음 곡을 나스가 줄여 두게 하고, 폰에도 받아 둠(굴속에서도 이어지게)
        let nx = i + 1
        if nx < gokMok.count {
            let g = gokMok[nx]
            let tk0 = tk
            Task.detached {
                if let ju = BangsongEngine.juso("/jeom/eumak.php", [("a", "junbi"), ("tk", tk0), ("f", g.f)]) {
                    _ = try? await URLSession.shared.data(from: ju)
                }
                await BangsongEngine.badaduki(g.f, tk0)
            }
        }
    }

    // 폰에 받아 둔 곡 — 세 곡까지만
    private static var batadunGot: URL {
        let u = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("gilnun_eumak", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    private static func batadunIreum(_ f: String) -> String {
        String(f.unicodeScalars.map { CharacterSet.alphanumerics.contains($0) ? Character($0) : "_" }.suffix(80)) + ".mp3"
    }

    static func batadun(_ f: String) -> URL? {
        let u = batadunGot.appendingPathComponent(batadunIreum(f))
        return FileManager.default.fileExists(atPath: u.path) ? u : nil
    }

    static func badaduki(_ f: String, _ tk: String) async {
        guard batadun(f) == nil, !tk.isEmpty,
              let u = juso("/jeom/eumak.php", [("a", "teul"), ("tk", tk), ("f", f), ("q", "g")]),
              let dr = try? await URLSession.shared.download(from: u),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200 else { return }
        let tmp = dr.0
        let fm = FileManager.default
        let mok = batadunGot.appendingPathComponent(batadunIreum(f))
        try? fm.moveItem(at: tmp, to: mok)
        // 오래된 것부터 지워 세 곡만 남김
        if let ls = try? fm.contentsOfDirectory(at: batadunGot, includingPropertiesForKeys: [.contentModificationDateKey]) {
            let jeongryeol = ls.sorted {
                let a = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let b = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                return a > b
            }
            for x in jeongryeol.dropFirst(3) { try? fm.removeItem(at: x) }
        }
    }

    // MARK: 지나는 고장 노래 저절로(1분마다)

    func jadoKyeogi() {
        guard eumakDeureom else { SoriEngine.shared.mal("나스 음악 열쇠를 먼저 넣어 주십시오."); return }
        jadoKyeojim = true
        jadoKey = ""
        SoriEngine.shared.mal("지나는 고장 노래를 저절로 틀어 드립니다.")
        jadoBoda()
        jadoSigye?.invalidate()
        jadoSigye = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in self?.jadoBoda() }
    }

    /// 2.12.7 차에 타면 지나는 고장 노래를 저절로(설정에서 끔) — 라디오·TV·기사를 듣고 계시면 건드리지 않음(이사장님 승인 1)
    private var jadoChaRo = false
    func chaTamGojangNorae() {
        guard Seoljeong.shared.gojangNorae, !jadoKyeojim, !tk.isEmpty else { return }
        guard jong == .eopseum || jong == .eumak else { return }
        Task {
            if !self.eumakDeureom { await self.eumakJunbi() }
            await MainActor.run {
                guard self.eumakDeureom, !self.jadoKyeojim, self.jong == .eopseum || self.jong == .eumak else { return }
                self.jadoChaRo = true
                self.jadoKyeogi()
                Girok.shared.namgi("gojang_norae_cha", [:])
            }
        }
    }
    /// 차에서 내리시거나 여정을 마치시면 고장 따라 바꾸기만 멈춤(듣던 노래는 그대로)
    func chaNaerimGojangNorae() {
        guard jadoKyeojim, jadoChaRo else { return }
        jadoChaRo = false
        jadoKkeugi(malHagi: false)
    }

    func jadoKkeugi(malHagi: Bool = true) {
        guard jadoKyeojim else { return }
        jadoChaRo = false
        jadoKyeojim = false
        jadoSigye?.invalidate()
        jadoSigye = nil
        jadoJul = ""
        if malHagi { SoriEngine.shared.mal("저절로 틀기를 그만둡니다. 듣던 노래는 그대로 이어집니다.") }
    }

    private func jadoBoda() {
        guard jadoKyeojim, let w = WichiEngine.shared.jigeum else { return }
        Task {
            guard let j = await BangsongEngine.mutgi("/jeom/gojang.php", [("lat", String(format: "%.6f", w.lat)), ("lon", String(format: "%.6f", w.lon))]),
                  (j["ok"] as? Bool) == true, let key = j["key"] as? String else { return }
            let gonna = await MainActor.run { () -> Bool in
                if key == self.jadoKey { return false }
                self.jadoKey = key
                return true
            }
            guard gonna else { return }
            let si0 = (j["si"] as? String) ?? ""
            let gu0 = (j["gu"] as? String) ?? ""
            let gj = (j["gojang"] as? String) ?? ""
            // 시·군 이름을 먼저, 구 이름은 쓰지 않음(웹 길눈 260911 고침과 같게)
            var nm = ""
            if let r = gu0.range(of: "^[^\\s]+?(시|군)(\\s|$)", options: .regularExpression) {
                nm = String(gu0[r]).trimmingCharacters(in: .whitespaces)
                if nm.hasSuffix("시") || nm.hasSuffix("군") { nm = String(nm.dropLast()) }
            } else {
                nm = si0.replacingOccurrences(of: "(특별시|광역시|특별자치시|특별자치도|통합특별시|도)$", with: "", options: .regularExpression)
            }
            guard !nm.isEmpty else { return }
            await MainActor.run { self.jadoJul = "지금 \(gj) — \(nm) 노래를 찾습니다." }
            var k = await self.eumakMutgi("gojang", [("q", nm)])
            var ireum = nm
            if BangsongEngine.gokDeul(k).isEmpty {
                let si = si0.replacingOccurrences(of: "(특별시|광역시|특별자치시|특별자치도|도)$", with: "", options: .regularExpression)
                if !si.isEmpty && si != nm {
                    k = await self.eumakMutgi("gojang", [("q", si)])
                    ireum = si
                }
            }
            let ls = BangsongEngine.gokDeul(k)
            await MainActor.run {
                guard self.jadoKyeojim else { return }
                if ls.isEmpty {
                    self.jadoJul = "지금 \(gj) — 이 고장 노래를 찾지 못해 앞서 듣던 것을 이어 갑니다."
                    return
                }
                self.gokMok = ls
                self.gokTeulgi(0, malHagi: false)
                self.jadoJul = "지금 \(gj) — \(ireum) 노래 \(ls.count)곡"
                SoriEngine.shared.mal("\(ireum) 노래 \(ls.count)곡으로 바꿉니다.", .jeongbo)
            }
        }
    }

    // MARK: 누구나 음악(열쇠 없이, 공유마당 자유이용 곡)

    func nugunaTeulgi(_ bun: String, _ mal: String) async {
        if nuguna.isEmpty {
            if let u = URL(string: BangsongEngine.ppuri + "/jeom/nuguna/mok.json?_=\(Int(Date().timeIntervalSince1970))"),
               let dr = try? await URLSession.shared.data(from: u),
               let a = (try? JSONSerialization.jsonObject(with: dr.0)) as? [[String: Any]] {
                let ls = a.compactMap { r -> NugunaGok? in
                    guard let mp3 = r["mp3"] as? String, !mp3.isEmpty else { return nil }
                    let sn = (r["sn"] as? Int).map { String($0) } ?? ((r["sn"] as? String) ?? mp3)
                    return NugunaGok(sn: sn, ireum: (r["ireum"] as? String) ?? "", jakgok: (r["jakgok"] as? String) ?? "",
                                     jeojakja: (r["jeojakja"] as? String) ?? "", jogeon: (r["jogeon"] as? String) ?? "",
                                     bun: (r["bun"] as? String) ?? "", mp3: mp3)
                }
                await MainActor.run { self.nuguna = ls }
            }
        }
        await MainActor.run {
            let jul = self.nuguna.filter { bun.isEmpty || $0.bun == bun }.shuffled()
            guard !jul.isEmpty else { SoriEngine.shared.mal("누구나 음악 목록을 받지 못했습니다. 잠시 뒤 다시 해 주십시오."); return }
            self.nugunaJul = jul
            self.nugunaI = -1
            self.nugunaDaeum(malHagi: false)
            SoriEngine.shared.mal(mal + " 첫 곡은 \(jul[0].ireum)입니다.", .jeongbo)
        }
    }

    private func nugunaDaeum(malHagi: Bool = true) {
        guard !nugunaJul.isEmpty else { return }
        nugunaI = (nugunaI + 1) % nugunaJul.count
        let g = nugunaJul[nugunaI]
        guard let u = URL(string: BangsongEngine.ppuri + "/jeom/nuguna/" + g.mp3) else { return }
        teulgi(u, .nuguna, g.ireum)
        nugunaPyosi = g.pyosi
        if malHagi { SoriEngine.shared.mal(g.ireum, .jeongbo) }
    }

    // MARK: 라디오·TV

    func chaeneolBatgi() async {
        guard let j = await BangsongEngine.mutgi("/bfblive/sori.php", [("a", "list")]) else { return }
        let ls = ((j["list"] as? [[String: Any]]) ?? []).compactMap { r -> Chaeneol? in
            guard let id = r["id"] as? String, let nm = r["name"] as? String else { return nil }
            return Chaeneol(id: id, name: nm, freq: (r["freq"] as? String) ?? "", kind: (r["kind"] as? String) ?? "radio")
        }
        await MainActor.run { if !ls.isEmpty { self.chaeneolDeul = ls } }
    }

    func kindChaeneol(_ kind: String) -> [Chaeneol] { chaeneolDeul.filter { $0.kind == kind } }

    func chaeneolTeulgi(_ c: Chaeneol, malHagi: Bool = true) {
        jigeumChaeneol = c
        if malHagi {
            sangtaeMal = "\(c.name)에 잇는 중입니다."
            SoriEngine.shared.mal("\(c.name)에 잇는 중입니다.", .jeongbo)
        }
        if c.kind == "radio" { UserDefaults.standard.set(c.id, forKey: "gn.majimakRadio") } else { UserDefaults.standard.set(c.id, forKey: "gn.majimakTv") }
        Girok.shared.namgi("bangsong", ["id": c.id])
        Task {
            let j = await BangsongEngine.mutgi("/bfblive/sori.php", [("a", "url"), ("id", c.id)])
            await MainActor.run {
                guard self.jigeumChaeneol?.id == c.id else { return }
                guard (j?["ok"] as? Bool) == true, let s = j?["url"] as? String, !s.isEmpty else {
                    self.sangtaeMal = (j?["msg"] as? String) ?? "\(c.name)에 잇지 못했습니다."
                    SoriEngine.shared.mal(self.sangtaeMal)
                    return
                }
                let url: URL? = s.hasPrefix("http") ? URL(string: s) : URL(string: BangsongEngine.ppuri + "/bfblive/" + s)
                guard let u = url else { return }
                let seek = (j?["seek"] as? Double) ?? Double((j?["seek"] as? Int) ?? 0)
                self.chaeneolNote = (j?["note"] as? String) ?? ""
                self.teulgi(u, c.kind == "tv" ? .tv : .radio, c.name, seek: seek)
            }
        }
    }

    /// 2.12.7 방송 이름을 한 가지 꼴로 — 영문·한글·띄어쓰기·말 순서가 달라도 같게(MBC·엠비씨·엠비시 → mbc)
    static func bangsongPyojun(_ t: String) -> String {
        var s = MalSajeon.ttuk(t).lowercased()
        let bakkum: [(String, String)] = [
            ("엠비씨", "mbc"), ("엠비시", "mbc"), ("앰비씨", "mbc"), ("앰비시", "mbc"), ("엠비", "mbc"), ("문화방송", "mbc"),
            ("케이비에스", "kbs"), ("케이비애스", "kbs"), ("캐이비에스", "kbs"), ("한국방송", "kbs"),
            ("이비에스", "ebs"), ("이비애스", "ebs"), ("교육방송", "ebs"),
            ("오비에스", "obs"), ("티비에스", "tbs"), ("비비에스", "bbs"), ("불교방송", "bbs"), ("아리랑", "arirang"),
            ("에프엠", "fm"), ("애프엠", "fm"), ("포유", "4u"),
            ("티브이", "tv"), ("티비", "tv"), ("텔레비전", "tv"),
            ("일라디오", "1라디오"), ("원라디오", "1라디오"), ("삼라디오", "3라디오"),
            ("일tv", "1tv"), ("이tv", "2tv"), ("원tv", "1tv"), ("투tv", "2tv")
        ]
        for (a, b) in bakkum { s = s.replacingOccurrences(of: a, with: b) }
        return s
    }

    /// 말 속의 방송사
    private static func bangsongsa(_ s: String) -> String? {
        for b in ["kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang"] where s.contains(b) { return b }
        return nil
    }

    /// 말로 — "MBC 라디오 틀어 줘", "라디오 엠비씨", "표준FM", "라디오 틀어 줘"(지난번 채널)
    /// 돌려주는 것: (할 말, 되묻는 말인지). 모르는 이름이면 아무것도 틀지 않고 여쭘(2.12.7 이사장님 승인 1)
    func chaeneolMalro(_ z: String, kind: String) async -> (String, Bool) {
        if chaeneolDeul.isEmpty { await chaeneolBatgi() }
        let ls = kindChaeneol(kind)
        guard !ls.isEmpty else { return ("채널 목록을 받지 못했습니다. 통신을 확인해 주십시오.", false) }
        let zz = BangsongEngine.bangsongPyojun(z)
        // 채널 이름 풀기: 방송사와 나머지(표준fm, 1라디오 따위)
        let pul: [(Chaeneol, String?, String)] = ls.map { c in
            let nm = BangsongEngine.bangsongPyojun(c.name)
            let sa = BangsongEngine.bangsongsa(nm)
            var bu = nm
            if let sa = sa { bu = bu.replacingOccurrences(of: sa, with: "") }
            bu = bu.replacingOccurrences(of: "(화면해설)", with: "")
            return (c, sa, bu)
        }
        let sa = BangsongEngine.bangsongsa(zz)
        let hubo = sa == nil ? pul : pul.filter { $0.1 == sa }
        // 1) 이름 통째로
        var gorun = hubo.first { !$0.2.isEmpty && zz.contains(($0.1 ?? "") + $0.2) }?.0
        // 2) 방송사를 뺀 나머지(표준fm, fm4u, 1라디오, 해피fm …)
        let heunhan: Set<String> = ["tv", "fm", "라디오", "방송"]
        if gorun == nil { gorun = hubo.first { $0.2.count >= 2 && !heunhan.contains($0.2) && zz.contains($0.2) }?.0 }
        // 3) 줄여 부른 말
        if gorun == nil {
            let jjal: [(String, String)] = [("표준", "표준fm"), ("4u", "fm4u"), ("해피", "해피fm"), ("쿨", "쿨fm"), ("클래식", "클래식fm"),
                                            ("사랑의소리", "3라디오"), ("3라디오", "3라디오"), ("1라디오", "1라디오"), ("한민족", "한민족"),
                                            ("듣는방송", "듣는방송"), ("화면해설", "듣는방송"), ("1tv", "1tv"), ("2tv", "2tv"), ("플러스", "플러스")]
            for (m, k) in jjal where zz.contains(m) {
                gorun = hubo.first { $0.2.contains(k) }?.0
                if gorun != nil { break }
            }
        }
        // 4) 방송사만 말씀하시면 그 방송사의 으뜸 채널(MBC → MBC 표준FM, KBS → KBS 1라디오)
        if gorun == nil, sa != nil, !hubo.isEmpty {
            let eutteum: [String: String] = kind == "radio"
                ? ["mbc": "mbcsfm", "kbs": "kbs1r", "ebs": "ebsfm"]
                : ["mbc": "mbcdeut", "kbs": "kbs1tv", "ebs": "ebs1tv"]
            gorun = hubo.first { $0.0.id == eutteum[sa!] }?.0 ?? hubo.first?.0
        }
        // 5) 아무 이름도 없이 "라디오 틀어 줘"면 지난번 채널
        if gorun == nil {
            var namun = zz
            for w in ["라디오", "tv", "fm", "방송", "채널", "틀어", "틀자", "켜", "들려", "듣자", "들을래", "보자", "볼래", "주세요", "줘", "좀", "다시", "지금", "을", "를"] {
                namun = namun.replacingOccurrences(of: w, with: "")
            }
            if namun.isEmpty {
                let id = UserDefaults.standard.string(forKey: kind == "radio" ? "gn.majimakRadio" : "gn.majimakTv") ?? ""
                gorun = ls.first { $0.id == id } ?? ls.first
            }
        }
        guard let c = gorun else {
            Girok.shared.namgi("bangsong_moreum", ["mal": String(z.prefix(40))])
            let ireum = ls.prefix(8).map { $0.name }.joined(separator: ", ")
            return ("어느 방송을 \(kind == "radio" ? "들으실까요" : "보실까요")? \(ireum) 가운데 말씀해 주십시오.", true)
        }
        await MainActor.run { self.chaeneolTeulgi(c, malHagi: false) }
        return ("\(c.name)을 틉니다.", false)
    }

    private func chaeneolBakkugi(_ d: Int) {
        guard let c = jigeumChaeneol else { return }
        let ls = kindChaeneol(c.kind)
        guard let i = ls.firstIndex(of: c), !ls.isEmpty else { return }
        let n = ls[(i + d + ls.count) % ls.count]
        chaeneolTeulgi(n)
    }

    // MARK: 지금 세상 이야기

    static let garae: [(String, String)] = [("sokbo", "속보·특보"), ("all", "두루 소식"), ("jangae", "장애·복지"), ("jeongchi", "정치"),
                                             ("gyeongje", "경제"), ("gukje", "국제"), ("sahoe", "사회"), ("spo", "스포츠·연예")]

    private static func gisaDeul(_ j: [String: Any]?) -> [Gisa] {
        ((j?["rows"] as? [[String: Any]]) ?? []).compactMap { r in
            guard let jm = r["jemok"] as? String, let ju = r["juso"] as? String else { return nil }
            return Gisa(saem: (r["saem"] as? String) ?? "", jemok: jm, yoyak: (r["yoyak"] as? String) ?? "", juso: ju,
                        ttaeMal: (r["ttaeMal"] as? String) ?? "", sokbo: (r["sokbo"] as? Bool) ?? false)
        }
    }

    /// 갈래의 기사 — nil 은 받지 못함
    func gisaBatgi(_ g: String, sae: Bool = false) async -> [Gisa]? {
        var q: [(String, String)] = [("garae", g)]
        if sae { q.append(("sae", "1")) }
        guard let j = await BangsongEngine.mutgi("/jeom/sesang.php", q), (j["ok"] as? Bool) == true else { return nil }
        let ls = BangsongEngine.gisaDeul(j)
        await MainActor.run { self.gisaMok = ls }
        return ls
    }

    func gisaChatgi(_ q: String) async -> [Gisa]? {
        guard let j = await BangsongEngine.mutgi("/jeom/sesang.php", [("a", "chatgi"), ("q", q)]), (j["ok"] as? Bool) == true else { return nil }
        let ls = BangsongEngine.gisaDeul(j)
        await MainActor.run { self.gisaMok = ls }
        return ls
    }

    /// 기사 열기 — 원문 전체를 받아 읽음(말소리를 꺼 두셨으면 화면에만)
    func gisaYeolgi(_ i: Int) {
        guard i >= 0, i < gisaMok.count else { SoriEngine.shared.mal("더 들려 드릴 기사가 없습니다."); return }
        let r = gisaMok[i]
        player.pause()
        player.replaceCurrentItem(with: nil)
        kkeutGwanchalChiugi()
        jadoKkeugi(malHagi: false)
        if gisaSynth.isSpeaking { gisaSynth.stopSpeaking(at: .immediate) }
        gisaI = i
        jong = .gisa
        jemok = r.jemok
        meomchum = false
        gisaBon = []
        gisaMeori = r.saem + (r.ttaeMal.isEmpty ? "" : " · " + r.ttaeMal) + " · 원문을 여는 중입니다"
        pyosiGaengsin()
        RemoteDanchu.shared.matchugi()
        Task {
            let j = await BangsongEngine.mutgi("/jeom/sesang.php", [("a", "bonmun"), ("u", r.juso)])
            await MainActor.run {
                guard self.gisaI == i, self.jong == .gisa else { return }
                var mundan: [String]
                var meori: String
                if (j?["ok"] as? Bool) == true {
                    mundan = (j?["mundan"] as? [String]) ?? []
                    let tm = (j?["ttaeMal"] as? String) ?? r.ttaeMal
                    let gija = (j?["gija"] as? String) ?? ""
                    meori = ((j?["saem"] as? String) ?? r.saem) + (tm.isEmpty ? "" : " · " + tm) + (gija.isEmpty ? "" : " · " + gija) + " · 원문 전체"
                } else {
                    let msg = (j?["msg"] as? String) ?? "원문을 열지 못했습니다."
                    mundan = (!r.yoyak.isEmpty && !msg.contains("속보는")) ? [r.yoyak] : []
                    meori = r.saem + (r.ttaeMal.isEmpty ? "" : " · " + r.ttaeMal) + " · " + msg + (mundan.isEmpty ? "" : " 요약을 읽어 드립니다.")
                }
                self.gisaMeori = meori
                self.gisaBon = mundan
                self.gisaIlkgi()
            }
        }
    }

    /// 제목부터 끝까지 읽기
    private func gisaIlkgi() {
        guard jong == .gisa, gisaI >= 0, gisaI < gisaMok.count else { return }
        if gisaSynth.isSpeaking { gisaSynth.stopSpeaking(at: .immediate) }
        guard Seoljeong.shared.malKyeojim else { gisaIlkneun = false; return }   // 보이스오버로 들으시는 분은 화면 글로
        sesyeonJapgi()
        let ilk = [gisaMok[gisaI].jemok + ".", gisaMeori.replacingOccurrences(of: " · ", with: ", ") + "."] + gisaBon
            + ["기사 끝입니다. 다음 기사를 들으시려면 다음 기사를 누르십시오."]
        let v = BangsongEngine.gisaMoksori()
        let rate = Seoljeong.bbareugiGap[max(0, min(4, Seoljeong.shared.gisaBbareugiDan))]
        var majimak: AVSpeechUtterance?
        for s in ilk where !s.trimmingCharacters(in: .whitespaces).isEmpty {
            let u = AVSpeechUtterance(string: s)
            u.voice = v
            u.rate = rate
            u.postUtteranceDelay = 0.25
            gisaSynth.speak(u)
            majimak = u
        }
        gisaMajimak = majimak
        gisaIlkneun = true
        if meomchum || !naebuMeomchum.isEmpty || malJung { gisaSynth.pauseSpeaking(at: .immediate) }
    }

    func gisaCheoeumButeo() {
        guard jong == .gisa else { return }
        meomchum = false
        gisaIlkgi()
    }

    static func gisaMoksori() -> AVSpeechSynthesisVoice? {
        let id = Seoljeong.shared.gisaMoksoriId
        if !id.isEmpty, let v = AVSpeechSynthesisVoice(identifier: id) { return v }
        return SoriEngine.shared.moksori()
    }

    /// 목소리 들어 보기
    func moksoriDeureoboki() {
        if jong == .gisa && gisaSynth.isSpeaking { return }
        let u = AVSpeechUtterance(string: "이 목소리로 기사를 읽어 드립니다.")
        u.voice = BangsongEngine.gisaMoksori()
        u.rate = Seoljeong.bbareugiGap[max(0, min(4, Seoljeong.shared.gisaBbareugiDan))]
        sesyeonJapgi()
        gisaSynth.speak(u)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            guard utterance === self.gisaMajimak else { return }
            self.gisaMajimak = nil
            self.gisaIlkneun = false
        }
    }
}
