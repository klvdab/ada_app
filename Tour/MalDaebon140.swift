// LVD투어스튜디오 앱 — 1.4.0 앱에만 있는 것 (판 1.4.0, 빌드 261018-T6, 방클, 이사장님 승인 「앱만 할 수 있는 것」 3번 → 다음 차례 「1」)
// 말로 대본 쓰기 — 받아쓰기를 바로 대본으로
//   지점 한 화면에서 말하는 대로 글이 되어 고른 칸(대본·이동 구간 안내·만져 볼 것·조심할 것·현장영상해설사 위치) 끝에 붙음
//   「마침표」「쉼표」「물음표」「느낌표」「줄 바꿔」는 부호로, 「열두 시 방향」은 「12시 방향」으로 적음
//   이어폰 단추 한 번 누름을 「대본 받아쓰기」로 고르면 화면을 안 보고도 씀
//   받아쓰는 동안 30초마다 폰에 남겨 두어, 앱이 꺼져도 다음에 넣을 수 있음
import Foundation
import AVFoundation
import Speech
import SwiftUI

enum MalDaebon {
    static let KAN: [(String, String)] = [("text", "대본"), ("access", "이동 구간 안내"), ("touch", "만져 볼 것"), ("safe", "조심할 것"), ("gpos", "현장영상해설사 위치")]
    static func ireum(_ k: String) -> String { KAN.first { $0.0 == k }?.1 ?? "대본" }
    /// 말로 하는 숫자 → 시계 숫자 (긴 말부터)
    static let SU: [(String, Int)] = [("열두", 12), ("열하나", 11), ("열한", 11), ("열", 10), ("아홉", 9), ("여덟", 8), ("일곱", 7), ("여섯", 6),
                                      ("다섯", 5), ("네", 4), ("넷", 4), ("세", 3), ("셋", 3), ("두", 2), ("둘", 2), ("한", 1), ("하나", 1)]
    static let BUHO: [(String, String)] = [("줄 바꿔", "\n"), ("줄바꿔", "\n"), ("줄 바꾸기", "\n"), ("줄바꾸기", "\n"), ("줄바꿈", "\n"), ("다음 줄", "\n"),
                                           ("마침표", "."), ("쉼표", ","), ("물음표", "?"), ("느낌표", "!")]

    static func bakkum(_ t: String, _ pat: String, _ to: String) -> String {
        guard let r = try? NSRegularExpression(pattern: pat) else { return t }
        return r.stringByReplacingMatches(in: t, range: NSRange(t.startIndex..., in: t), withTemplate: to)
    }
    /// 말한 글 다듬기 — 부호 말을 부호로, 시계 방향을 숫자로
    static func dadeum(_ t0: String) -> String {
        var t = t0
        for (w, n) in SU { t = bakkum(t, "(^|[^가-힣])" + w + " ?시 ?(방향|쪽)", "$1\(n)시 $2") }
        t = bakkum(t, "(\\d{1,2}) *시 *(방향|쪽)", "$1시 $2")
        for (w, b) in BUHO { t = t.replacingOccurrences(of: w, with: b) }
        t = bakkum(t, " +([.,?!])", "$1")
        t = bakkum(t, "([.,?!])([^ \\n.,?!0-9])", "$1 $2")
        t = bakkum(t, "[ \\t]*\\n[ \\t]*", "\n")
        t = bakkum(t, " {2,}", " ")
        return t.trimmingCharacters(in: .whitespaces)
    }
    /// 왼쪽·오른쪽 말이 들어 있나 (시계 방향으로 고치시라고 알림)
    static func jwau(_ t: String) -> Bool { t.range(of: "왼쪽|오른쪽|왼편|오른편", options: .regularExpression) != nil }
    /// 있던 글 끝에 이어 붙이기
    static func ipgi(_ o: String, _ t: String) -> String {
        if o.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return t }
        return o + ((o.hasSuffix(" ") || o.hasSuffix("\n")) ? "" : " ") + t
    }
}

/// 받아쓰는 소리를 알아듣기 칸으로 넘기는 상자 (소리 줄은 다른 줄에서 옴)
final class MalSangja: @unchecked Sendable {
    private let lock = NSLock()
    private var r: SFSpeechAudioBufferRecognitionRequest?
    func dagi(_ x: SFSpeechAudioBufferRecognitionRequest?) { lock.lock(); r = x; lock.unlock() }
    func neoki(_ b: AVAudioPCMBuffer) { lock.lock(); let x = r; lock.unlock(); x?.append(b) }
}

@MainActor
final class MalSseugi: ObservableObject {
    static let shared = MalSseugi()
    @Published private(set) var jung = false
    @Published private(set) var dan = ""        // 끝난 토막들
    @Published private(set) var jigeum = ""     // 지금 듣는 토막
    @Published private(set) var spotId = ""
    @Published var kan: String = UserDefaults.standard.string(forKey: "malKan") ?? "text" {
        didSet { UserDefaults.standard.set(kan, forKey: "malKan") }
    }
    @Published private(set) var nameun: [String: String]? = UserDefaults.standard.dictionary(forKey: "malNameun") as? [String: String]
    private let eng = AVAudioEngine()
    private let sangja = MalSangja()
    private var task: SFSpeechRecognitionTask?
    private var rz: SFSpeechRecognizer?
    private var sedae = 0
    private var heotdoem = 0
    private var namgiTask: Task<Void, Never>? = nil

    var modu: String { MalDaebon.dadeum(dan + " " + jigeum) }

    /// 받아쓰기 시작 — 안 되면 까닭을 돌려줌
    func sijak(_ s: Spot) async -> String? {
        if jung { return "이미 받아쓰는 중입니다." }
        if Nokeumgi.shared.jung { return "녹음 중입니다. 먼저 끝내 주십시오." }
        guard let k = TourStore.shared.kos, !k.isGyobon else { return "먼저 내 코스를 열어 주십시오." }
        Deutgi.shared.meomchum()
        let mic: Bool = await withCheckedContinuation { c in AVAudioApplication.requestRecordPermission { c.resume(returning: $0) } }
        if !mic { return "마이크를 허락하지 않으셔서 받아쓸 수 없습니다. 아이폰 설정의 투어스튜디오에서 마이크를 켜 주십시오." }
        let au: SFSpeechRecognizerAuthorizationStatus = await withCheckedContinuation { c in SFSpeechRecognizer.requestAuthorization { c.resume(returning: $0) } }
        if au != .authorized { return "받아쓰기를 허락하지 않으셔서 쓸 수 없습니다. 아이폰 설정의 투어스튜디오에서 받아쓰기(음성 인식)를 켜 주십시오." }
        guard let r = SFSpeechRecognizer(locale: Locale(identifier: "ko-KR")), r.isAvailable else { return "지금 이 폰에서는 받아쓰기를 쓸 수 없습니다. 통신을 확인하시거나 현장 메모 녹음을 쓰십시오." }
        rz = r
        do {
            let ss = AVAudioSession.sharedInstance()
            try ss.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try ss.setActive(true)
            let inp = eng.inputNode
            let fmt = inp.outputFormat(forBus: 0)
            if fmt.sampleRate <= 0 || fmt.channelCount == 0 { return "마이크를 열지 못했습니다." }
            inp.removeTap(onBus: 0)
            MalSseugi.tapGeolgi(inp, fmt, sangja)
            eng.prepare()
            try eng.start()
        } catch {
            eng.inputNode.removeTap(onBus: 0)
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            return "마이크를 열지 못했습니다."
        }
        dan = ""; jigeum = ""; spotId = s.id; heotdoem = 0
        jung = true
        tomak()
        namgiTask?.cancel()
        namgiTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                if Task.isCancelled || !MalSseugi.shared.jung { break }
                MalSseugi.shared.namgigi()
            }
        }
        return nil
    }

    /// 새 토막 — 받아쓰기는 한 번에 1분쯤이라, 끝나면 이어서 새로 엶
    private func tomak() {
        guard jung, let r = rz else { return }
        sedae += 1
        let my = sedae
        let q = SFSpeechAudioBufferRecognitionRequest()
        q.shouldReportPartialResults = true
        q.taskHint = .dictation
        if r.supportsOnDeviceRecognition { q.requiresOnDeviceRecognition = true }
        q.addsPunctuation = false
        sangja.dagi(q)
        task = MalSseugi.alabdeutgi(r, q, my)
    }

    nonisolated static func tapGeolgi(_ inp: AVAudioInputNode, _ fmt: AVAudioFormat, _ sj: MalSangja) {
        inp.installTap(onBus: 0, bufferSize: 2048, format: fmt) { b, _ in sj.neoki(b) }
    }
    nonisolated static func alabdeutgi(_ r: SFSpeechRecognizer, _ q: SFSpeechAudioBufferRecognitionRequest, _ my: Int) -> SFSpeechRecognitionTask {
        r.recognitionTask(with: q) { res, err in
            let t = res?.bestTranscription.formattedString
            let fin = res?.isFinal ?? false
            let bad = err != nil
            Task { @MainActor in MalSseugi.shared.batgi(my, t, fin, bad) }
        }
    }

    private func batgi(_ my: Int, _ t: String?, _ fin: Bool, _ bad: Bool) {
        guard jung, my == sedae else { return }
        if let t { jigeum = t }
        guard fin || bad else { return }
        let had = !jigeum.trimmingCharacters(in: .whitespaces).isEmpty
        if had { dan = (dan.isEmpty ? "" : dan + " ") + jigeum; heotdoem = 0 } else { heotdoem += 1 }
        jigeum = ""
        task = nil
        if heotdoem >= 8 {
            kkeut(neoki: true, kkalm: "한동안 알아들은 말이 없어 받아쓰기를 멈추었습니다.")
            return
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000)
            MalSseugi.shared.tomak()
        }
    }

    /// 받아쓰던 글을 폰에 남겨 둠 (앱이 꺼져도 다음에 넣게)
    private func namgigi() {
        let t = modu
        guard !t.isEmpty, let kid = TourStore.shared.kos?.id else { return }
        let x = ["kos": kid, "spot": spotId, "kan": kan, "t": t]
        UserDefaults.standard.set(x, forKey: "malNameun")
    }
    func nameunJiugi() { UserDefaults.standard.removeObject(forKey: "malNameun"); nameun = nil }
    /// 지난번 끊긴 글 넣기
    func nameunNeoki() {
        guard let n = nameun, let t = n["t"], let k = TourStore.shared.kos, k.id == n["kos"], let s = k.spot(n["spot"] ?? "") else { return }
        neoki(k, s, n["kan"] ?? "text", t)
        nameunJiugi()
    }

    /// 끝 — neoki 면 고른 칸 끝에 붙임
    func kkeut(neoki nk: Bool, kkalm: String = "") {
        guard jung else { return }
        let t = modu.trimmingCharacters(in: .whitespacesAndNewlines)
        jung = false
        sedae += 1
        namgiTask?.cancel(); namgiTask = nil
        task?.cancel(); task = nil
        sangja.dagi(nil)
        eng.stop()
        eng.inputNode.removeTap(onBus: 0)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        dan = ""; jigeum = ""
        UserDefaults.standard.removeObject(forKey: "malNameun"); nameun = nil
        Danchu.shared.buchigi()
        let st = TourStore.shared
        guard nk else { Danchu.shared.malhae("받아쓰기를 그만두었습니다. 넣지 않았습니다."); return }
        guard let k = st.kos, let s = k.spot(spotId) else { return }
        if t.isEmpty { Danchu.shared.malhae((kkalm.isEmpty ? "" : kkalm + " ") + "알아들은 말이 없어 넣지 않았습니다."); return }
        neoki(k, s, kan, t, kkalm)
    }

    private func neoki(_ k: Kos, _ s: Spot, _ kn: String, _ t: String, _ kkalm: String = "") {
        let st = TourStore.shared
        if kn == "text" { k.setSpotText(s, MalDaebon.ipgi(k.spotText(s), t)) }
        else { s.d[kn] = MalDaebon.ipgi(s.s(kn), t) }
        st.bakkwim()
        var m = (kkalm.isEmpty ? "" : kkalm + " ") + k.spotLab(s) + " " + MalDaebon.ireum(kn) + " 칸에 넣었습니다."
        if MalDaebon.jwau(t) { m += " 왼쪽이나 오른쪽이라는 말이 들어 있습니다. 시계 방향으로 고쳐 주십시오." }
        Danchu.shared.malhae(m)
        st.allim = m + " 넣은 글: " + t
        Task { @MainActor in _ = await TourStore.shared.save(quiet: true) }
    }
}

/// 지점 한 화면에 붙는 1.4.0 묶음 — 말로 대본 쓰기
struct MalDaebonSection: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var m = MalSseugi.shared
    let s: Spot
    var body: some View {
        Section("말로 대본 쓰기 — 말하는 대로 고른 칸 끝에") {
            Picker("넣을 칸", selection: $m.kan) {
                ForEach(MalDaebon.KAN.map { $0.0 }, id: \.self) { key in Text(MalDaebon.ireum(key)).tag(key) }
            }
            if m.jung && m.spotId == s.id {
                Text(m.modu.isEmpty ? "듣는 중입니다. 말씀하십시오." : m.modu).font(.title3).fixedSize(horizontal: false, vertical: true)
                Button("끝 — " + MalDaebon.ireum(m.kan) + " 칸에 넣기") { m.kkeut(neoki: true) }.font(.headline)
                Button("그만두기 (넣지 않음)") { m.kkeut(neoki: false) }
            } else if m.jung {
                Text("다른 지점에서 받아쓰는 중입니다.")
                Button("그 받아쓰기 끝내고 넣기") { m.kkeut(neoki: true) }
            } else {
                Button("말로 쓰기 시작") {
                    Task { @MainActor in
                        if let e = await m.sijak(s) { store.say(e) }
                        else { store.allim = "받아쓰는 중입니다. 다 말씀하시면 끝 단추를 누르십시오." }
                    }
                }.font(.headline)
            }
            if !m.jung, let n = m.nameun, n["kos"] == store.kos?.id {
                Text("지난번 받아쓰다 끊긴 글이 있습니다 (" + MalDaebon.ireum(n["kan"] ?? "text") + " 칸): " + (n["t"] ?? "")).fixedSize(horizontal: false, vertical: true)
                Button("끊긴 글 넣기") { m.nameunNeoki() }
                Button("끊긴 글 버리기") { m.nameunJiugi(); store.say("끊긴 글을 버렸습니다.") }
            }
            Text("말하는 대로 글이 되어 고른 칸 끝에 붙습니다. 「마침표」, 「쉼표」, 「물음표」, 「느낌표」라고 말하면 부호가, 「줄 바꿔」라고 말하면 새 줄이 들어갑니다. 「열두 시 방향」이라고 말하면 「12시 방향」으로 적습니다. 오래 말해도 이어서 받아쓰고, 30초마다 폰에 남겨 두어 앱이 꺼져도 다음에 넣을 수 있습니다. 넣은 글은 바로 위 칸에서 고칠 수 있습니다.")
                .font(.footnote)
        }
    }
}
