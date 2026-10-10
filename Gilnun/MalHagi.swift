// 말로 하기 — 길눈을 말로 시킵니다(이사장님 발안 2026-09-17, 앱 2.5.0 빌드 260928-7).
// 2.12.1 (빌드 260929-1) 하이 길눈 — 길눈이 말할 때 마이크를 닫지 않고 알아듣기만 쉼(잠긴 폰에서도 이어지게)
// 2.12.2 (빌드 260929-2) 걸러 듣기로 방송·안내 말 중에도 부름을 듣고, 부름을 들으면 길눈 소리를 멈추고 다른 소리를 낮춤
// 2.12.3 (빌드 260929-3) 걸러 듣기·소리 낮추기를 뺌. 명령을 기다리는 중 "하이 길눈"만 들리면 "네" 하고 다시 기다림
// 2.12.4 (빌드 260929-4) 멈춤 풀기(25초 넘게 한 자리에 멈추면 처음으로, 30초 넘게 부름을 못 기다리면 다시 엶)와 걸음마다 기록
// 2.12.5 (빌드 260929-5, 이사장님 승인 1) 역 이름 찾기 — 한글 번호(오 번→5번), 줄인 역 이름(제기역→제기동역), 가까운 역·즐겨찾기를 받아쓰기에 미리 알림,
//   되묻고 기다리는 시간 10초, 길거리 음악
// 웹 길눈에서 이사장님이 정하신 것을 앱의 알맹이로 옮겼습니다.
//   부르기: 말로 하기 단추, 보이스오버 두 손가락 두 번 두드리기, "하이 길눈"(설정에서 켬), 화면이 잠긴 채 "시리야, 길눈"
//   알아듣기: 아이폰 자체 받아쓰기(폰 안, 무료) + 웹과 같은 나스 알아듣기 사전
//   명령 먼저: 묻는 중이라도 네·아니오가 아니면 새 명령을 따름
//   목적지를 알아들으면 되묻지 않고 곧바로 안내(가자는 말 없이 이름만 들리면 한 번 여쭘)
//   "그만"은 어디서나, 급한 순서는 긴급통화 > 경고 > 길 안내
//   대답을 마치면 땡 소리, 묻는 말에는 마이크를 한 번만 저절로 엶
//   모르는 말은 사과하고 기록해 두었다가 사전을 키움
// 2.30.0 (빌드 261001-10, 대표님 승인 1) 딩동 대신 또렷한 목소리 「네」, 땡 대신 「잠깐만 기다려 주세요」(말소리를 끈 분께는 딩동·땡 그대로).
//   하이 길눈을 알아들은 귀가 그대로 명령을 들음(귀를 하나로). 못 들으면 「다시 한번 말씀해 주세요」 한 번만, 그래도 못 들으면 조용히 물러남.
//   명령 글자가 0이면 마이크 막힘(maik_makhim)을 따로 남김.
import Foundation
import UIKit
import AVFoundation
import Combine
import SwiftUI   // 2.7.0 둘러보기 화면 길(NavigationPath)

final class MalHagi: ObservableObject {
    static let shared = MalHagi()

    enum Sangtae { case swim, deutneun, araboneun }
    @Published private(set) var sangtae: Sangtae = .swim { didSet { sangtaeTtae = Date() } }
    // 2.12.4 멈춤 풀기
    private var sangtaeTtae = Date()
    private var bureumEopseumTtae: Date?
    private var gamsiSigye: Timer?
    @Published private(set) var deureunMal = ""
    @Published private(set) var dapMal = ""

    // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) neomgilkka — 긴급통화 「자원봉사자와 현장영상해설사에게 요청할까요?」
    private enum Mureum { case eopseum, mokjeok, bangsik, chaYocheong, kol, galrae, hoching, hubo, jeom, eumakBiseut, kolHwagin, neomgilkka }
    /// 2.60.0 "○○에 전화할까요?"라고 여쭌 콜
    private var kolDaegi: KolBeonho?
    private var mureum: Mureum = .eopseum
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 새로 여쭐 때마다 차 부르기의 묵은 물음(다음 수단에 걸까요·복지카드 메일)을 거둠
    ///   — 10분 전 「다음 수단에 걸까요」가 뒤에 여쭌 「목적지를 변경하실 건가요?」의 「네」를 가로채 다른 콜에 걸던 일
    private var mureumTtae = Date.distantPast {
        didSet { if mureumTtae > oldValue { ChaBureugi.shared.mureumBiugi() } }
    }
    /// 2.61.0 (261009-I15) 말로 하기가 마지막으로 여쭌 때 — 차 부르기는 이보다 뒤에 여쭌 물음일 때만 네·아니오를 씀
    var majimakMureumTtae: Date { mureumTtae }
    private var mok: Jangso?
    private var talgeotDaegi: Talgeot?
    private var hubo: [Jangso] = []
    private var huboI = 0
    private var huboTalgeot: Talgeot?
    private var huboHwagin = false
    private var motBeon = 0
    private var motTtae = Date.distantPast
    private var jadongYeolim = 0
    // 2.49.0 대화 이어 가기(이사장님 승인 2026-10-06) — 대답 뒤 하이 길눈 없이 이어 듣기, 같은 말 세 번이면 부드럽게 끊음
    private var ieoSu = 0
    private var ieoGeumman = false
    private var watchMal = false
    private var choegeunMal: [String] = []
    private var daehwaGirok: [[String]] = []
    private var bureumDolgo = false
    private var bureumYeyak = false
    private var bureumSilpae = 0   // 2.12.0 뒤에서 부름 기다리기를 거듭 못 열면 앱으로 돌아올 때까지 쉼
    private var ijeonMal = ""
    /// 2.30.0 「다시 한번 말씀해 주세요」를 이미 했음(한 번만)
    private var dasiHanbeon = false
    private var ssak = Set<AnyCancellable>()
    private var sijakham = false
    // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 명령 차례 번호 — 전화가 오거나 편집을 시작하면 하나 올려 늦게 온 대답을 버림
    private var myeongBeon = 0
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「알아보는 중입니다」를 기다리는 명령 번호(대답이 나오면 0)
    private var araboGidarim = 0
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 한 마디(맞장구)에 마지막으로 대답한 때 — 20초 안에 또 오면 조용히 물러남
    private var geunyangTtae = Date.distantPast
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 기억해 둔 가실 곳(mok)을 정한 때 — 여정이 없으면 3분 안의 것만 씀
    private var mokTtae = Date.distantPast

    private var sajeon: MalSajeon { MalSajeon.shared }
    private var ho: String { Seoljeong.shared.ho }

    // MARK: 세우기

    func sijak() {
        guard !sijakham else { return }
        sijakham = true
        sajeon.bureogi()
        SoriEngine.shared.malSijakHook = { [weak self] in self?.malSijakham() }
        gamsiSigye = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.gamsi() }
        Seoljeong.shared.$haiGilnun
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] on in self?.haiGilnunBakkwim(on) }
            .store(in: &ssak)
        // 2.12.2 "방송 중에도 하이 길눈 듣기"를 바꾸시면 마이크를 새로 엶
        Seoljeong.shared.$haiBangsongDeutgi
            .dropFirst()
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self, self.sangtae == .swim, MalDeutgi.shared.dolgoItda else { return }
                MalDeutgi.shared.meomchugi()
                self.bureumDolgo = false
                self.bureumDasi(0.5)
            }
            .store(in: &ssak)
        // 2.59.0 폰 전화 중에도 마이크를 통화에 내어 줌(통화 말을 명령으로 알아듣지 않게)
        JeonhwaGamsi.shared.$jeonhwaJung
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] j in
                guard let self = self else { return }
                if j { self.jeonhwaOm() } else { self.bureumDasi(1.5) }
            }
            .store(in: &ssak)
        // 긴급통화 중에는 마이크를 통화에 내어 줌
        GinGeup.shared.$sangtae
            .receive(on: DispatchQueue.main)
            .sink { [weak self] s in
                guard let self = self else { return }
                if s != .eopseum { self.moduMeomchum() } else { self.bureumDasi(1.5) }
            }
            .store(in: &ssak)
        let nc = NotificationCenter.default
        nc.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] n in
            guard let self = self,
                  let v = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let ty = AVAudioSession.InterruptionType(rawValue: v) else { return }
            if ty == .began { self.moduMeomchum() } else { self.bureumDasi(1.0) }
        }
        nc.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] n in
            guard let self = self, self.bureumDolgo,
                  let v = n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                  let r = AVAudioSession.RouteChangeReason(rawValue: v),
                  r == .newDeviceAvailable || r == .oldDeviceUnavailable else { return }
            // 이어폰을 꽂거나 빼면 그 마이크로 다시 엶
            MalDeutgi.shared.meomchugi()
            self.bureumDolgo = false
            self.bureumDasi(0.8)
        }
        nc.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.bureumSilpae = 0
            self?.bureumDasi(1.0)
        }
        // 2.12.6 폰 잠금을 풀면 듣기를 포기했던 것을 다시 셈하고 엶
        nc.addObserver(forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main) { [weak self] _ in
            self?.bureumSilpae = 0
            self?.bureumDasi(1.0)
        }
    }

    // MARK: 부르기

    /// 말로 하기 단추·보이스오버 두 손가락 두 번 두드리기·시리로 앱을 열 때
    func dudeurim() {
        DispatchQueue.main.async {
            self.sijak()
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 전화 중이면 단추를 누르셔도 아무 말 없이 마이크를 열지 않음(통화 말을 명령으로 듣지 않게)
            if JeonhwaGamsi.shared.jeonhwaJung {
                Girok.shared.namgi("malhagi_jeonhwa", ["dan": "dudeurim"])
                return
            }
            if self.sangtae == .deutneun {
                self.myeongryeongChwiso()
                SoriEngine.shared.sori(.ttaeng)
                self.bureumDasi(0.8)
                return
            }
            self.jadongYeolim = 0
            self.dasiHanbeon = false
            self.ijeonMal = SoriEngine.shared.majimak
            self.myeongryeongYeolgi(sori: true)
        }
    }

    /// 2.60.0 (261009-I14, 이사장님 승인 2026-10-09 남산) 길 찾기 편집창에 글자를 넣으시는 중 — 마이크 듣기를 쉼
    ///   (말을 잘못 알아들어 목적지가 정해졌다 풀렸다 하며 편집창이 사라졌다 나타나 커서가 옆으로 밀리던 일)
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 편집을 시작한 때 — 안드로이드와 같이 5분이 지나면 저절로 풂
    private var pyeonjipTtae: Date?
    var pyeonjipJung: Bool {
        guard let t = pyeonjipTtae else { return false }
        return Date().timeIntervalSince(t) < 300
    }

    func pyeonjipSijak() {
        guard !pyeonjipJung else { return }
        pyeonjipTtae = Date()
        if sangtae == .deutneun { myeongryeongChwiso() }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 알아보던 명령의 대답이 편집을 시작한 뒤에 오면 조용히 버림
        myeongBeon += 1
        araboGidarim = 0
        if sangtae == .araboneun { sangtae = .swim }
        moduMeomchum()
        Girok.shared.namgi("pyeonjip", ["on": true])
    }

    func pyeonjipKkeut() {
        guard pyeonjipTtae != nil else { return }
        pyeonjipTtae = nil
        Girok.shared.namgi("pyeonjip", ["on": false])
        bureumDasi(1.0)
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 전화가 옴 — 마이크를 닫고, 알아보던 명령과 묻던 말(목적지 바꾸기·콜 걸기)을 모두 거둠
    private func jeonhwaOm() {
        moduMeomchum()
        myeongBeon += 1
        araboGidarim = 0
        malbeotGidarim = 0
        jadongYeolim = 0
        ieoSu = 0
        dasiHanbeon = false
        if sangtae != .swim { sangtae = .swim }
        hwaginBiugi()
        Girok.shared.namgi("malhagi_jeonhwa", ["dan": "om"])
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 긴급통화가 가족·지인께 닿지 않아 「자원봉사자와 현장영상해설사에게 요청할까요?」를 여쭘(GinGeup 이 말함) —
    ///   네·아니오 물음으로 걸어 두고(마지막 물음 하나, 2분), 그 말을 다 한 뒤 마이크를 한 번 엶. 전화 중·편집 중·마이크 허락이 없으면 열지 않음(단추는 그대로)
    func neomgilkkaMutgi(_ q: String) {
        hwaginBiugi()
        mureum = .neomgilkka
        mureumTtae = Date()
        Girok.shared.namgi("malhagi_neomgilkka", [:])
        let yeolgi = { [weak self] in
            guard let self = self, self.sijakham, self.mureum == .neomgilkka, self.sangtae == .swim,
                  GinGeup.shared.neomgilkka, GinGeup.shared.sangtae == .eopseum,
                  !self.pyeonjipJung, !JeonhwaGamsi.shared.jeonhwaJung, MalDeutgi.heorakItda else { return }
            self.dasiHanbeon = false
            self.jadongYeolim = 1   // 저절로 연 마이크 — 딩동, 10초 기다리고 말씀이 없으면 조용히 닫음
            self.myeongryeongYeolgi(sori: true)
        }
        if Seoljeong.shared.malKyeojim {
            SoriEngine.shared.kkeutnamyeon { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: yeolgi) }
        } else {
            // 말소리를 꺼 두셨으면 보이스오버가 단추를 읽을 만큼 기다림
            DispatchQueue.main.asyncAfter(deadline: .now() + min(8, 1.2 + Double(q.count) * 0.09), execute: yeolgi)
        }
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 네·아니오를 기다리던 물음(목적지 바꾸기·콜 걸기·후보)을 비움
    private func hwaginBiugi() {
        mureum = .eopseum
        ChaBureugi.shared.mureumBiugi()   // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 차 부르기의 물음(다음 수단·복지카드 메일)도 거둠
        kolDaegi = nil
        hubo = []
        huboI = 0
        huboHwagin = false
    }

    private func myeongryeongYeolgi(sori: Bool) {
        guard GinGeup.shared.sangtae == .eopseum else { return }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 전화 중이면 어떤 길로도 마이크를 열지 않음(이어 듣기·하이 길눈·지킴이·단추 모두)
        if JeonhwaGamsi.shared.jeonhwaJung {
            if sangtae == .deutneun { myeongryeongChwiso() }
            jadongYeolim = 0
            Girok.shared.namgi("malhagi_jeonhwa", ["dan": "yeolgi"])
            return
        }
        if !sori && pyeonjipJung { return }   // 2.60.0 편집 중에는 저절로 이어 듣지 않음
        if !MalDeutgi.heorakItda {
            MalDeutgi.heorak { [weak self] ok in
                guard let self = self else { return }
                if ok {
                    self.myeongryeongYeolgi(sori: sori)
                } else {
                    self.malHam("말로 하기에는 마이크와 음성 인식 허락이 필요합니다. 아이폰 설정의 길눈에서 마이크와 음성 인식을 켜 주십시오.") {}
                }
            }
            return
        }
        // 2.30.0 하이 길눈을 알아들은 귀가 이어 들을 때는 끄지 않음(귀를 하나로)
        if !MalDeutgi.shared.saeIeum { MalDeutgi.shared.swigi() }   // 2.12.1 마이크는 열어 둔 채 알아듣기만 바꿈
        bureumDolgo = false
        SoriEngine.shared.modu_geodugi()   // 명령 먼저 — 하던 말을 멈춤(경고는 남김)
        sangtae = .deutneun
        SoriEngine.shared.myeongryeongDeutneunJung = true   // 2.12.0 마이크를 여는 틈에 다른 말이 끼어 닫지 않게
        let yeolgi = { [weak self] in
            guard let self = self, self.sangtae == .deutneun else { return }
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「네」를 말하는 사이에 전화가 왔으면 열지 않음
            if JeonhwaGamsi.shared.jeonhwaJung { self.myeongryeongChwiso(); return }
            SoriEngine.shared.myeongryeongDeutneunJung = true
            MalDeutgi.shared.doumMal = self.doumMal()   // 2.12.5 가까운 역·즐겨찾기
            let gidarim: Double = self.jadongYeolim > 0 ? 10 : 6   // 2.12.5 되물은 뒤에는 10초 기다림
            let ok = MalDeutgi.shared.myeongryeong(gidarim: gidarim) { [weak self] alts in self?.deureum(alts) }
            Girok.shared.namgi("myeong_yeolgi", ["ok": ok])
            if !ok {
                SoriEngine.shared.myeongryeongDeutneunJung = false
                self.sangtae = .swim
                self.malHam("지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 해 주십시오.") { [weak self] in self?.bureumDasi(1.0) }
            }
        }
        // 2.62.0 (261010-I16, 이사장님 승인 2026-10-10) 대답 뒤 이어 들을 때도 딩동 대신 「네」(10월 1일 결정 — 딩동 자리는 「네」, 말소리를 끈 분만 딩동)
        if sori && Seoljeong.shared.malKyeojim {
            // 2.30.0 딩동 대신 또렷한 「네」 — 말하는 동안 귀를 닫고, 다 말한 뒤 곧바로 엶
            MalDeutgi.shared.gwiDatgi(true)
            Girok.shared.namgi("ne", ["gwi": MalDeutgi.shared.saeIeum ? "hana" : "sae"])
            SoriEngine.shared.daehwaMal("네") {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.12, execute: yeolgi)
            }
        } else if sori {
            SoriEngine.shared.sori(.dingdong)   // 2.26.0 딩동 — 이제 말씀하십시오(말소리를 끈 분, 되물은 뒤)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.55, execute: yeolgi)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15, execute: yeolgi)
        }
    }

    private func myeongryeongChwiso() {
        MalDeutgi.shared.swigi()
        SoriEngine.shared.myeongryeongDeutneunJung = false
        sangtae = .swim
    }

    /// 명령을 다 들었음
    private func deureum(_ alts0: [String]) {
        SoriEngine.shared.myeongryeongDeutneunJung = false
        let beopeo = MalDeutgi.shared.majimakBeopeo
        Girok.shared.namgi("myeong_deureum", ["su": alts0.count, "sangtae": sangtae == .deutneun, "beopeo": beopeo,
                                              "sae": MalDeutgi.shared.saeSseumJung])
        guard sangtae == .deutneun else { return }
        let alts = alts0.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        if alts.isEmpty {
            // 2.30.0 명령 글자가 0 — 마이크 막힘을 따로 남김(beopeo 0이면 마이크에 소리가 안 들어옴, 아니면 알아듣기가 못 적음)
            Girok.shared.namgi("maik_makhim", ["dan": "myeongryeong", "beopeo": beopeo, "dasi": dasiHanbeon,
                                               "sae": MalDeutgi.shared.saeSseumJung])
            sangtae = .swim
            if jadongYeolim > 0 {
                // 저절로 연 마이크에 말씀이 없으면 조용히 닫음
                jadongYeolim = 0
                MalDeutgi.shared.swigi()
                if !Seoljeong.shared.malKyeojim { SoriEngine.shared.sori(.ttaeng) }
                bureumDasi(0.8)
                return
            }
            if !dasiHanbeon && GinGeup.shared.sangtae == .eopseum {
                // 2.30.0 한 번만 「다시 한번 말씀해 주세요」 하고 같은 귀로 한 번 더 들음
                dasiHanbeon = true
                if Seoljeong.shared.malKyeojim {
                    sangtae = .deutneun
                    SoriEngine.shared.myeongryeongDeutneunJung = true
                    MalDeutgi.shared.gwiDatgi(true)
                    SoriEngine.shared.daehwaMal("다시 한번 말씀해 주세요") { [weak self] in
                        guard let self = self, self.sangtae == .deutneun else { return }
                        self.myeongryeongYeolgi(sori: false)
                    }
                } else {
                    myeongryeongYeolgi(sori: true)   // 말소리를 끄셨으면 딩동으로 다시 말씀하실 차례를 알림
                }
                return
            }
            // 그래도 못 들으면 조용히 물러남
            dasiHanbeon = false
            MalDeutgi.shared.swigi()
            if !Seoljeong.shared.malKyeojim { SoriEngine.shared.sori(.ttaeng) }
            Girok.shared.namgi("dap_kkeut", ["mureoNam": true])
            bureumDasi(0.8)
            return
        }
        dasiHanbeon = false
        // 2.12.3 "하이 길눈"만 들렸으면(대답을 못 들어 한 번 더 부르심) 명령으로 치지 않고 "네" 하고 다시 기다림
        if alts.contains(where: { MalDeutgi.bureumMal($0) && MalSajeon.ttuk($0).count <= 6 }) {
            Girok.shared.namgi("hai_dasi", [:])
            sangtae = .swim
            bureumDeureum()
            return
        }
        // 2.49.0 말씀이 있으면 이어 듣기를 새로 셈, 「됐어」면 그침, 같은 말 세 번이면 부드럽게 끊음
        let zz = MalSajeon.ttuk(alts[0])
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 한 마디(맞장구·잡소리)는 새 대화로 치지 않아 이어 듣기 셈을 새로 하지 않음
        if !MalHagi.geunyangMal(zz) { ieoSu = 0 }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「배차됐어」「배차 됐어」「배차되었어」는 대화 끝이 아니라 배차 대답으로(차 부르기가 먼저 받음)
        if !MalHagi.baechaDapMal(alts) && ["됐어", "됐어요", "됐습니다", "고마워", "고마워요", "고맙습니다", "알았어", "알겠어", "이제됐어", "충분해"].contains(zz) {
            ieoGeumman = true
            sangtae = .swim
            hwaginBiugi()   // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 대화를 마치면 묻던 목적지·콜 확인도 비움
            dapHagi("네, 필요하시면 하이 길눈이라고 불러 주십시오.", false)
            return
        }
        choegeunMal.append(zz)
        if choegeunMal.count > 3 { choegeunMal.removeFirst() }
        if choegeunMal.count == 3 && Set(choegeunMal).count == 1 && zz.count > 1 {
            choegeunMal = []
            ieoGeumman = true
            sangtae = .swim
            dapHagi("같은 말씀을 여러 번 하셨습니다. 제가 잘 돕지 못했다면 다른 말로 여쭤 봐 주시거나, 긴급통화로 현장영상해설사를 부르실 수 있습니다.", false)
            return
        }
        deureunMal = alts[0]
        sangtae = .araboneun
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 명령 차례 번호 — 전화·편집으로 거둔 뒤 늦게 온 대답은 조용히 버림.
        //   말씀을 마치신 때부터 3초가 지나도 대답이 없으면 「알아보는 중입니다」를 한 번(어떤 대답이든 나오면 그만)
        myeongBeon += 1
        let mb = myeongBeon
        araboGidarim = mb
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
            guard let self = self, self.araboGidarim == mb, self.myeongBeon == mb else { return }
            self.araboGidarim = 0
            self.malHamMan("알아보는 중입니다.")
        }
        if Seoljeong.shared.malKyeojim {
            // 2.30.0 (대표님 지시) 땡 대신 「잠깐만 기다려 주세요」 — 귀는 이미 닫음
            // 2.31.0 (대표님 승인) 결과가 1초 안에 나오면 곧바로 말씀드리고, 1초 넘게 걸릴 때만 「잠깐만 기다려 주세요」
            var malSijak = false   // 잠깐만을 말하기 시작했나
            var malKkeut = false
            var dap: (String, Bool)?
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                guard dap == nil, self?.myeongBeon == mb else { return }
                malSijak = true
                SoriEngine.shared.daehwaMal("잠깐만 기다려 주세요") {
                    malKkeut = true
                    if let d = dap, self?.myeongBeon == mb { self?.dapHagi(d.0, d.1) }
                }
            }
            cheori(alts) { [weak self] t, mutneun in
                DispatchQueue.main.async {
                    guard dap == nil else { return }
                    dap = (t, mutneun)
                    guard let self = self, self.myeongBeon == mb else { return }   // 2.61.0 거둔 명령의 대답은 버림
                    self.araboGidarim = 0
                    if !malSijak || malKkeut { self.dapHagi(t, mutneun) }
                }
            }
            return
        }
        // 2.26.0 (대표님 지시) 말씀을 다 들으면 땡 — 그때서야 길눈 목소리로 결과를 말함(말소리를 끄신 분)
        SoriEngine.shared.sori(.ttaeng)
        let ttaengT = Date()
        cheori(alts) { [weak self] t, mutneun in
            DispatchQueue.main.async {
                guard let self = self, self.myeongBeon == mb else { return }   // 2.61.0 거둔 명령의 대답은 버림
                self.araboGidarim = 0
                let nameun = max(0, 0.35 - Date().timeIntervalSince(ttaengT))
                DispatchQueue.main.asyncAfter(deadline: .now() + nameun) { [weak self] in
                    guard let self = self, self.myeongBeon == mb else { return }
                    self.dapHagi(t, mutneun)
                }
            }
        }
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 기다리게 하는 짧은 알림 — 말소리를 꺼 두셨으면 보이스오버로(안드로이드는 톡백)
    private func malHamMan(_ t: String) {
        if Seoljeong.shared.malKyeojim {
            SoriEngine.shared.mal(t, .jeongbo)
        } else {
            UIAccessibility.post(notification: .announcement, argument: t)
        }
    }

    /// 대답하고, 묻는 말이면 마이크를 한 번만 저절로 엶. 아니면 땡 소리로 마침
    private func dapHagi(_ t: String, _ mutneun: Bool) {
        sangtae = .swim
        if !t.isEmpty { dapMal = t }
        malHam(t) { [weak self] in
            guard let self = self else { return }
            // 2.49.0 묻는 말이든 아니든 대답 뒤에는 하이 길눈 없이 10초 이어 들음(딩동으로 알림). 말씀이 없으면 조용히 닫음
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 편집 중이거나 전화 중이면 이어 듣지 않음(안드로이드와 같이 이 자리에서 바로 봄)
            if GinGeup.shared.sangtae == .eopseum && !self.ieoGeumman && !self.watchMal && self.ieoSu < 20
                && !self.pyeonjipJung && !JeonhwaGamsi.shared.jeonhwaJung {
                self.ieoSu += 1
                self.jadongYeolim += 1
                self.myeongryeongYeolgi(sori: true)
            } else {
                self.ieoGeumman = false
                self.watchMal = false
                self.ieoSu = 0
                self.jadongYeolim = 0
                Girok.shared.namgi("dap_kkeut", [:])
                self.bureumDasi(0.8)   // 2.26.0 땡은 대답 앞에서 이미 울림
            }
        }
    }

    /// 길눈이 말하기 — 말소리를 꺼 두셨으면 보이스오버로
    private func malHam(_ t: String, _ ihu: @escaping () -> Void) {
        if t.isEmpty {
            SoriEngine.shared.kkeutnamyeon(ihu)
            return
        }
        if Seoljeong.shared.malKyeojim {
            SoriEngine.shared.mal(t, .annae)
            SoriEngine.shared.kkeutnamyeon(ihu)
        } else {
            UIAccessibility.post(notification: .announcement, argument: t)
            DispatchQueue.main.asyncAfter(deadline: .now() + min(8, 0.8 + Double(t.count) * 0.09), execute: ihu)
        }
    }

    // MARK: 하이 길눈

    private func haiGilnunBakkwim(_ on: Bool) {
        if on {
            MalDeutgi.heorak { [weak self] ok in
                guard let self = self else { return }
                if ok {
                    self.malHam("이제 하이 길눈이라고 부르시면 대답합니다.") { [weak self] in self?.bureumDasi(0.5) }
                } else {
                    Seoljeong.shared.haiGilnun = false
                    self.malHam("하이 길눈에는 마이크와 음성 인식 허락이 필요합니다. 아이폰 설정의 길눈에서 켜 주십시오.") {}
                }
            }
        } else {
            if sangtae != .deutneun { MalDeutgi.shared.meomchugi() }   // 2.12.1 마이크를 아주 닫음
            bureumDolgo = false
            SoriEngine.shared.deutgiKyeojim = false
        }
    }

    /// 부름 기다리기를 다시 엶 — 길눈이 말하는 중이면 다 말한 뒤에
    private func bureumDasi(_ dwi: Double) {
        if sangtae == .swim { bureumSoriDollim() }   // 2.12.2 명령을 마쳤으면 멈췄던 소리를 되돌림
        guard !bureumYeyak else { return }
        bureumYeyak = true
        DispatchQueue.main.asyncAfter(deadline: .now() + dwi) { [weak self] in
            guard let self = self else { return }
            self.bureumYeyak = false
            guard Seoljeong.shared.haiGilnun, self.sangtae == .swim, !self.bureumDolgo, !self.pyeonjipJung,
                  GinGeup.shared.sangtae == .eopseum, !JeonhwaGamsi.shared.jeonhwaJung, MalDeutgi.heorakItda else {
                if !Seoljeong.shared.haiGilnun {
                    SoriEngine.shared.deutgiKyeojim = false
                    // 2.12.1 하이 길눈을 꺼 두셨으면 말로 하기를 마친 뒤 마이크를 닫음
                    if self.sangtae == .swim && MalDeutgi.shared.dolgoItda { MalDeutgi.shared.meomchugi() }
                }
                return
            }
            if SoriEngine.shared.bappeum {
                SoriEngine.shared.kkeutnamyeon { [weak self] in self?.bureumDasi(0.6) }
                return
            }
            SoriEngine.shared.deutgiKyeojim = true
            let ok = MalDeutgi.shared.bureum(deureum: { [weak self] in
                self?.bureumDeureum()
            }, kkeunkim: { [weak self] in
                self?.bureumDolgo = false
                self?.bureumDasi(0.3)
            })
            self.bureumDolgo = ok
            Girok.shared.namgi("bureum_yeolgi", ["ok": ok])
            if ok { self.bureumSilpae = 0; return }
            self.bureumSilpae += 1
            if UIApplication.shared.applicationState == .background && self.bureumSilpae >= 3 {
                Girok.shared.namgi("bureum_swim", [:])   // 앱으로 돌아오시면 다시 엶
                return
            }
            self.bureumDasi(10)
        }
    }

    /// 2.12.2 "하이 길눈"을 들은 동안 멈추고 낮췄던 소리를 되돌림
    private var bureumSoriJurim = false
    private func bureumSoriDollim() {
        MalDeutgi.shared.moduSoriMeomchum(false)   // 2.26.0 멈췄던 다른 앱 소리도 되돌림
        guard bureumSoriJurim else { return }
        bureumSoriJurim = false
        BangsongEngine.shared.bureumMeomchum(false)
        MalDeutgi.shared.dareunSori(jurim: false)
    }

    /// 길눈이 막 말하려 함 — 부름 기다리기를 잠시 닫고 다 말한 뒤 다시 엶. 명령 듣는 중 경고가 오면 경고부터
    private func malSijakham() {
        if sangtae == .deutneun {
            myeongryeongChwiso()
            return
        }
        // 2.12.2 걸러 듣기 중이면 길눈이 말하는 동안에도 부름을 들음(말에 "길눈"이 들면 그때만 쉼)
        if bureumDolgo && MalDeutgi.shared.georeunda && !SoriEngine.shared.hanunMal.contains("길눈") { return }
        if bureumDolgo {
            MalDeutgi.shared.swigi()   // 2.12.1 마이크는 열어 둔 채 쉼 — 잠긴 폰에서도 다시 듣게
            bureumDolgo = false
            bureumDasi(0.6)
        }
    }

    /// "하이 길눈"을 들음 — "네" 하고 명령을 들음
    private func bureumDeureum() {
        bureumDolgo = false
        guard sangtae == .swim, GinGeup.shared.sangtae == .eopseum else { return }
        guard !JeonhwaGamsi.shared.jeonhwaJung else { return }   // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 전화 중에 들린 부름은 버림
        jadongYeolim = 0
        dasiHanbeon = false
        ijeonMal = SoriEngine.shared.majimak
        gakkaunYeokGaengsin()   // 2.12.5
        // 2.12.2 부름을 들으면 곧바로 길눈 방송을 멈추고 다른 앱 소리를 크게 낮춤(명령을 마치면 되돌림)
        bureumSoriJurim = true
        BangsongEngine.shared.bureumMeomchum(true)
        MalDeutgi.shared.dareunSori(jurim: true)
        SoriEngine.shared.modu_geodugi()
        // 2.26.0 (대표님 지시) "하이 길눈"하고 끊으시면 곧바로 — 폰의 모든 소리를 멈추고 다음 말씀을 기다림
        // 2.30.0 말소리를 켜 두셨으면 딩동 대신 또렷한 「네」(myeongryeongYeolgi 에서)
        MalDeutgi.shared.moduSoriMeomchum(true)
        if !Seoljeong.shared.malKyeojim { Girok.shared.namgi("dingdong", ["bappeum": SoriEngine.shared.bappeum]) }
        myeongryeongYeolgi(sori: true)
    }

    // MARK: 2.12.4 멈춤 풀기 — 어디서 막히든 하이 길눈이 영영 먹통이 되지 않게

    private func gamsi() {
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 전화 중에는 지킴이도 마이크를 다시 열지 않음
        guard Seoljeong.shared.haiGilnun, GinGeup.shared.sangtae == .eopseum, !JeonhwaGamsi.shared.jeonhwaJung else { bureumEopseumTtae = nil; return }
        let now = Date()
        if sangtae != .swim {
            bureumEopseumTtae = nil
            guard now.timeIntervalSince(sangtaeTtae) > 25 else { return }
            Girok.shared.namgi("malhagi_pulgi", ["sangtae": sangtae == .deutneun ? "deutneun" : "araboneun",
                                                 "bappeum": SoriEngine.shared.bappeum])
            MalDeutgi.shared.swigi()
            SoriEngine.shared.myeongryeongDeutneunJung = false
            jadongYeolim = 0
            sangtae = .swim
            bureumDolgo = false
            bureumDasiGangje()
            return
        }
        if bureumDolgo || bureumYeyak { bureumEopseumTtae = nil; return }
        if UIApplication.shared.applicationState == .background && bureumSilpae >= 3 { return }
        guard let t0 = bureumEopseumTtae else { bureumEopseumTtae = now; return }
        guard now.timeIntervalSince(t0) > 30 else { return }
        Girok.shared.namgi("bureum_gangje", ["bappeum": SoriEngine.shared.bappeum])
        bureumEopseumTtae = nil
        bureumDasiGangje()
    }

    /// 길눈이 말을 마치기를 기다리지 않고 곧바로 부름 기다리기를 엶
    private func bureumDasiGangje() {
        bureumYeyak = false
        bureumSoriDollim()
        guard Seoljeong.shared.haiGilnun, sangtae == .swim, !bureumDolgo, !pyeonjipJung, MalDeutgi.heorakItda else { return }   // 2.60.0 편집 중 쉼, 2.52.1 하이 길눈이 꺼져 있으면(자봉 앱 포함) 열지 않음
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 전화 중·긴급통화 중이면 열지 않음
        guard !JeonhwaGamsi.shared.jeonhwaJung, GinGeup.shared.sangtae == .eopseum else { return }
        SoriEngine.shared.deutgiKyeojim = true
        let ok = MalDeutgi.shared.bureum(deureum: { [weak self] in
            self?.bureumDeureum()
        }, kkeunkim: { [weak self] in
            self?.bureumDolgo = false
            self?.bureumDasi(0.3)
        })
        bureumDolgo = ok
        Girok.shared.namgi("bureum_yeolgi", ["ok": ok, "gangje": true])
    }

    private func moduMeomchum() {
        SoriEngine.shared.daehwaGeuman()   // 2.30.0
        bureumSoriDollim()
        MalDeutgi.shared.meomchugi()
        bureumDolgo = false
        SoriEngine.shared.myeongryeongDeutneunJung = false
        SoriEngine.shared.deutgiKyeojim = false
        if sangtae == .deutneun { sangtae = .swim }
    }

    // MARK: 시리

    /// 시리로 받은 말 — 길눈의 대답을 시리가 읽어 줌
    func siriCheori(_ t: String) async -> String {
        await withCheckedContinuation { (c: CheckedContinuation<String, Never>) in
            DispatchQueue.main.async {
                self.sijak()
                var kkeut = false
                self.deureunMal = t
                self.cheori([t]) { d, _ in
                    guard !kkeut else { return }
                    kkeut = true
                    if !d.isEmpty { self.dapMal = d }
                    c.resume(returning: d.isEmpty ? "알겠습니다." : d)
                }
            }
        }
    }

    // MARK: 워치에서 온 말 (2.6.0)

    /// 손목(워치)에 말씀하신 글 — 폰 길눈이 알아듣고 폰에서 대답을 말하며, 대답 글을 워치에 돌려줌
    func bakkatCheori(_ t: String, _ kkeut: @escaping (String) -> Void) {
        DispatchQueue.main.async {
            self.sijak()
            let s = t.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !s.isEmpty else { kkeut("말씀이 들리지 않았습니다."); return }
            if self.sangtae == .deutneun { self.myeongryeongChwiso() }
            MalDeutgi.shared.swigi()
            self.bureumDolgo = false
            self.deureunMal = s
            self.jadongYeolim = 1   // 워치에서 온 말에는 폰 마이크를 저절로 열지 않음
            self.watchMal = true   // 2.49.0 이어 듣기도 하지 않음
            self.sangtae = .araboneun
            var han = false
            self.cheori([s]) { d, m in
                guard !han else { return }
                han = true
                self.dapHagi(d, m)
                kkeut(d.isEmpty ? "알겠습니다." : d)
            }
        }
    }

    // MARK: 알아듣고 하기

    /// 알아들은 말들로 할 일을 하고, 대답(dap)을 꼭 한 번 부름 — (할 말, 묻는 말인가)
    func cheori(_ alts0: [String], _ dap: @escaping (String, Bool) -> Void) {
        let alts = alts0.map { MalHagi.beonhoSutja($0.trimmingCharacters(in: .whitespacesAndNewlines)) }.filter { !$0.isEmpty }
        guard let t = alts.first else { dap("말씀이 들리지 않았습니다.", false); return }
        Girok.shared.namgi("malhagi", ["mal": String(t.prefix(60))])
        let z = MalSajeon.ttuk(t)
        let y = YeojeongEngine.shared.jigeum
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 묻던 말을 잊는 때를 안드로이드와 맞춤 — 점지도로 걸을까요·호칭은 1분, 그 밖(목적지·콜 확인 등)은 2분
        let sumyeong: TimeInterval = (mureum == .jeom || mureum == .hoching) ? 60 : 120
        if Date().timeIntervalSince(mureumTtae) > sumyeong { mureum = .eopseum; kolDaegi = nil }

        // 0. 2.12.6 하던 일 멈추기 — 안내·따라 걷기·묻던 말을 모두 멈춤(음악·방송은 그대로)
        if sajeon.itda(alts, "hadeon_meomchum") && z.count <= 10 {
            dap("", false)
            AnnaeEngine.shared.haneunIlMeomchum()
            return
        }
        // 0-0. 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 콜 취소 — "콜 취소", "콜 취소했어", "택시 취소"(아래 「그만」의 취소보다 먼저)
        if MalHagi.kolChwisoMal(z) {
            hwaginBiugi()
            ChaBureugi.shared.kolPulgi("malro")
            dap("알겠습니다. 콜을 취소하신 것으로 남겼습니다.", false)
            return
        }
        // 0-1. 2.58.0 차 부르기 — 배차 대답, 다음 수단, 복지카드 메일, 성적표, 이용 조건, 정기 호출(이사장님 승인 2026-10-09)
        if ChaBureugi.shared.malCheori(alts, z, dap) { return }
        // 1. 여정 끝내기
        if sajeon.itda(alts, "yeojeong_kkeut") {
            mureum = .eopseum
            if y != nil {
                dap("", false)
                AnnaeEngine.shared.kkeut()
            } else {
                dap("지금 가시는 여정이 없습니다.", false)
            }
            return
        }
        // 1-2. 2.12.7 도착 — "도착", "도착했어", "다 왔어"를 목적지 이름으로 찾지 않고 여정 끝내기로(이사장님 승인 1)
        let dochakMal = ["도착", "도착했어", "도착했다", "도착했어요", "도착했습니다", "도착이야", "도착했네", "다왔어", "다왔다", "다왔어요", "다왔습니다", "다왔네", "도착완료", "여기도착"]
        if dochakMal.contains(z) || (z.hasPrefix("도착") && z.count <= 6 && !z.contains("까지") && !z.contains("시간")) {
            mureum = .eopseum
            if y != nil {
                dap("도착하셨습니다. 여정을 마칩니다.", false)
                AnnaeEngine.shared.kkeut()
            } else {
                dap("지금 가시는 여정이 없습니다. 가실 곳을 말씀하시려면 어디로 가자라고 해 주십시오.", false)
            }
            return
        }
        // 1-3. 2.44.0 (261004-I2, 2026-10-04 KTX 부산행, 이사장님 승인) 탈것 바로잡는 말 — "기차 탔어", "기차야", "케이티엑스", "고속버스야", "택시 탔어"
        //      어떤 상태(지하철로 잘못 알고 있을 때 포함)에서도 받음. 장소 찾기("기차역으로 가자")와 섞이지 않게 가자·가줘·까지·으로가 든 말은 뺌
        if y != nil && z.count <= 14 && !z.contains("가자") && !z.contains("가줘") && !z.contains("까지") && !z.contains("으로") {
            let lz = z.lowercased()
            var tk: Talgeot? = nil
            if lz.contains("ktx") || z.contains("케이티엑스") || lz.contains("srt") || z.contains("에스알티")
                || z.contains("기차탔") || z.contains("기차야") || z.contains("기차예요") || z.contains("기차에요") || z.contains("기차입니다") || z.contains("기차타고") {
                tk = .gicha
            } else if z.contains("고속버스탔") || z.contains("고속버스야") || z.contains("고속버스예요") || z.contains("고속버스에요") || z.contains("고속버스입니다") || z.contains("고속버스타고") {
                tk = .gosokbeoseu
            } else if z.contains("택시탔") || z.contains("택시야") || z.contains("택시예요") || z.contains("택시에요") || z.contains("택시입니다") || z.contains("택시타고") {
                tk = .cha
            }
            if let tk = tk {
                mureum = .eopseum
                dap("", false)
                AnnaeEngine.shared.talgeotBarojapgi(tk)
                return
            }
        }
        // 2. 긴급통화 중 그만·끊어
        if GinGeup.shared.sangtae != .eopseum && (sajeon.itda(alts, "geuman") || z.contains("끊어")) {
            dap("", false)
            GinGeup.shared.geumanhagi()
            return
        }
        // 2.10.0 점지도 — 점지도로 걸을까요의 대답, 따라 걷는 중의 명령("그만 걷기"는 아래 "그만"보다 먼저)
        let jm = JeomEngine.shared
        if jm.muleum != nil {
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 네·아니오는 말 전체로만(응급실·예술의전당을 네로 듣지 않게)
            let hd0 = MalHagi.hwaginDap(alts)
            let ye0 = hd0 == .ye, ani0 = hd0 == .ani
            if z.contains("위성") || (mureum == .jeom && ani0 && !ye0 && z.count <= 10) {
                mureum = .eopseum
                dap("위성으로 걷습니다.", false)
                jm.muleumDap(jeom: false)
                return
            }
            if z.contains("점지도") || (mureum == .jeom && ye0 && !ani0 && z.count <= 10) {
                mureum = .eopseum
                dap("점지도로 걷습니다.", false)
                jm.muleumDap(jeom: true)
                return
            }
        }
        if jm.gil != nil {
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 점지도의 탈것 구간을 지나는 중 「내렸어」 — 내림 자리부터 다시 걸음 안내
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「밖으로 나왔어」「나왔어」도 내림으로
            //   2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「나왔어」는 말 전체이거나 짧은 말의 끝일 때만, 「안 나왔어」는 받지 않음
            if jm.tagoGaneunJung && (z.contains("내렸") || z.contains("하차") || MalSajeon.naoatdaMal(alts)) {
                dap("", false)
                jm.naeryeotda()
                return
            }
            if z.contains("그만걷") || z.contains("따라걷기그만") || z.contains("따라걷기끝") || z.contains("걷기그만") {
                dap("", false)
                jm.geuman()
                return
            }
            if z.contains("다음에무엇") || z.contains("다음에뭐") || z.contains("다음은뭐") || z.contains("다음표시") || z.contains("앞에뭐") {
                dap(jm.daeumMuotMal(), false)
                return
            }
            if jm.dochakHam && z.contains("되돌아") {
                dap("", false)
                jm.doedoragagi()
                return
            }
            if z.contains("문제있") || z.contains("여기문제") {
                dap("무슨 문제인지 고르시는 화면을 엽니다.", false)
                TabGil.shared.tab = 0
                GilGil.shared.cheotHwamyeon()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.munje) }
                return
            }
            if z.contains("길목") {
                dap("", false)
                jm.gilmok()
                return
            }
            if z.contains("정류장") && !z.contains("까지") {
                dap("", false)
                jm.beoseuJeongryujang()
                return
            }
            if z.contains("다른문") {
                dap("", false)
                jm.dareunMun()
                return
            }
            if z.contains("여기걸렸") {
                dap("", false)
                jm.geollimNamgigi()
                return
            }
        }
        if z.contains("점지도") && (z.contains("가까운") || z.contains("근처") || z.contains("목록") || z.contains("찾아")) {
            dap("가까운 점지도를 엽니다. 골라서 누르시면 따라 걷습니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.jeomMok) }
            return
        }
        // 3. 그만 — 어디서나
        if sajeon.itda(alts, "geuman") && z.count <= 8 {
            hwaginBiugi()   // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 묻던 목적지·콜 확인도 비움
            SinhogiEngine.shared.chatgiKkeugi()
            SoriEngine.shared.modu_geodugi()
            if BangsongEngine.shared.naneunJung { BangsongEngine.shared.meomchumTogeul() }   // 2.8.0 방송도 멈춤(이어서 틀어로 다시)
            MalgilEngine.shared.geuman()   // 2.14.0 말로 그린 길 안내도 그만
            MunChatgi.shared.kkeugi()      // 2.15.0 문 찾기도 그만
            GeulIlgi.shared.kkeugi()       // 2.16.0 즉석 글자 읽기도 그만
            GarikiIlgi.shared.kkeugi()     // 2.17.0 가리키고 말하기도 그만
            SaramGamji.shared.kkeugi()     // 2.18.0 사람 감지도 그만
            SangpumIlgi.shared.kkeugi()    // 2.20.0 상품 바코드 읽기도 그만
            JipyeSaek.shared.kkeugi()      // 2.21.0 지폐와 색깔 알아보기도 그만
            BitAlgi.shared.kkeugi()        // 2.22.0 빛 알아보기도 그만
            Hanmadi.shared.kkeugi()        // 2.23.0 한마디 설명도 그만
            TeokAllim.shared.kkeugi()      // 2.24.0 발 앞 계단·턱 알림도 그만
            dap("", false)
            return
        }

        // 4. 묻던 말의 대답
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 네·아니오는 말 전체가 대답일 때만(앞에 붙은 「응」「네」로 응급실·예술의전당·길 안내해 줘를 네로 듣지 않게)
        let hd = MalHagi.hwaginDap(alts)
        let ye = hd == .ye
        let ani = hd == .ani
        let jjalbeun = z.count <= 10
        switch mureum {
        case .hoching:
            mureum = .eopseum
            var h = t
            for k in ["이라고 불러 줘", "라고 불러 줘", "이라고 불러줘", "라고 불러줘", "이라고 불러", "라고 불러",
                      "이라고 해 줘", "라고 해 줘", "이라고", "라고", "으로 불러 줘", "로 불러 줘", "으로 불러", "로 불러"] where h.hasSuffix(k) {
                h = String(h.dropLast(k.count))
                break
            }
            h = h.trimmingCharacters(in: .whitespaces)
            if h.isEmpty || (ani && jjalbeun) {
                dap("알겠습니다. 호칭은 그대로 \(ho)입니다.", false)
                return
            }
            Seoljeong.shared.hoching = h
            dap("알겠습니다. 이제 \(h)\(MalHagi.irago(h)) 부르겠습니다.", false)
            return
        case .galrae:
            if gingeupJikjeop(z, dap) { return }
            if z.contains("가족") || z.contains("지인") {
                gingeup(t, dap)
                return
            }
        case .kol:
            if let jong = kolJong(alts) {
                mureum = .eopseum
                kolGeolgi(jong, dap)
                return
            }
            if let k = sajeon.kolIreumChatgi(alts) {
                mureum = .eopseum
                kolGeolgiK(k, dap)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                dap("알겠습니다. 차에 타시면 차에 탔어라고 말씀해 주십시오.", false)
                return
            }
        case .chaYocheong:
            if let jong = kolJong(alts) {
                mureum = .eopseum
                kolGeolgi(jong, dap)
                return
            }
            if let k = sajeon.kolIreumChatgi(alts) {
                mureum = .eopseum
                kolGeolgiK(k, dap)
                return
            }
            if ye && !ani && jjalbeun {
                mureum = .kol
                mureumTtae = Date()
                dap(kolMutgi(), true)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                dap("알겠습니다. 차에 타시면 차에 탔어라고 말씀해 주십시오.", false)
                return
            }
        case .bangsik:
            if let m = mok, let tg = talgeotChatgi(alts), z.count <= 10 {
                mureum = .eopseum
                gagi(m, tg, dap)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                dap("알겠습니다.", false)
                return
            }
        case .hubo:
            if huboHwagin && ye && !ani && jjalbeun && huboI < hubo.count {
                huboHwagin = false
                mureum = .eopseum
                let h = hubo[huboI]
                gagi(h, huboTalgeot, dap, apMal: "\(h.ireum)\(MalHagi.ro(h.ireum)) 안내합니다. ")
                return
            }
            if z.contains("다른곳") || z.contains("다른데") {
                daeumHubo(dap)
                return
            }
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 가시는 중에 「목적지를 ○○로 변경하실 건가요?」에 아니라고 하시면
            //   다음 후보로 넘기지 않고 지금 목적지로 계속 안내함(다른 곳·다른 데라고 하실 때만 다음 후보)
            if ani && jjalbeun, huboHwagin, let yy = y {
                hwaginBiugi()
                dap("알겠습니다. \(yy.mokjeok.ireum)\(MalHagi.ro(yy.mokjeok.ireum)) 계속 안내합니다.", false)
                return
            }
            if ani && jjalbeun {
                daeumHubo(dap)
                return
            }
        case .kolHwagin:
            // 2.60.0 (261009-I14, 이사장님 승인) "○○에 전화할까요?"의 대답 — 네일 때만 겁니다
            if ye && !ani && jjalbeun, let k = kolDaegi {
                mureum = .eopseum
                kolDaegi = nil
                kolGeolgiK(k, dap)
                return
            }
            if ani && jjalbeun {
                mureum = .eopseum
                kolDaegi = nil
                dap("알겠습니다. 걸지 않겠습니다.", false)
                return
            }
        case .neomgilkka:
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「자원봉사자와 현장영상해설사에게 요청할까요?」의 대답 — 네면 예 단추와 같이 두 갈래 함께 호출
            let g = GinGeup.shared
            if !g.neomgilkka || g.sangtae != .eopseum {
                mureum = .eopseum   // 단추로 이미 고르셨음 — 아래로 흘려 보통 말로 받음
            } else if ye && !ani && jjalbeun {
                mureum = .eopseum
                dap("", false)
                g.yocheong(.dowum)
                return
            } else if ani && jjalbeun {
                mureum = .eopseum
                g.neomgilkka = false
                dap("알겠습니다.", false)
                return
            } else {
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 딱 떨어지는 네·아니오가 아니면 물음을 거두고 보통 말로 받음 —
                //   2분 동안 남겨 두면 나중에 다른 뜻으로 한 「네」가 긴급통화를 걸 수 있음(안드로이드와 같음)
                mureum = .eopseum
            }
        case .eumakBiseut:
            // 2.46.0 "비슷한 제목으로 옛사랑이 있습니다. 틀까요?" — 네면 틂, 아니면 그만
            let b = BangsongEngine.shared
            mureum = .eopseum
            if ye && !ani && jjalbeun, let bq = b.biseutQ {
                b.biseutQ = nil
                Task {
                    let m = await b.malChatgi(bq)
                    DispatchQueue.main.async { dap(m, false) }
                }
                return
            }
            b.biseutQ = nil
            if ani && jjalbeun { dap("알겠습니다.", false); return }
        case .mokjeok, .eopseum, .jeom:
            break
        }

        // 4-1. 2.60.0 (261009-I14, 이사장님 승인 2026-10-09 남산) 한 마디 대답("그럼", "그래", "응" 등)은 곳 이름으로 찾지 않음
        //      — "그럼"을 곳 이름으로 찾아 상봉역 길을, "그래"에 비자변경 학원 길을 잡던 일
        if MalHagi.geunyangMal(z) {
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 잡소리가 되풀이되어 헛돌지 않게 — 한 번만 대답하고, 20초 안에 또 오면 조용히 듣기를 그침
            if Date().timeIntervalSince(geunyangTtae) < 20 {
                Girok.shared.namgi("geunyang_dasi", [:])
                ieoGeumman = true
                dap("", false)
                return
            }
            geunyangTtae = Date()
            dap(mureum == .mokjeok ? "가실 곳의 이름을 말씀해 주십시오." : "네, 말씀하십시오. 가실 곳이나 하실 일을 말씀해 주십시오.", true)
            return
        }
        // 4-2. 2.60.0 "변경", "목적지 변경", "목적지 바꿔" — 곳 이름이 아니라 목적지를 바꾸자는 명령
        if MalHagi.byeongyeongMal(z) {
            mureum = .mokjeok
            mureumTtae = Date()
            if let yy = y {
                dap("지금 \(yy.mokjeok.ireum)\(MalHagi.ro(yy.mokjeok.ireum)) 가시는 중입니다. 어디로 바꿀까요?", true)
            } else {
                dap("\(ho), 어디로 가실까요?", true)
            }
            return
        }

        // 5. 긴급통화 — 가장 급한 일
        if sajeon.itda(alts, "doum") || z.contains("화상통화") || z.contains("영상통화") || z.contains("긴급통화") {
            if gingeupJikjeop(z, dap) { return }
            gingeup(t, dap)
            return
        }

        // 6. 목적지 말을 떼어 보고, 가자는 말이 없으면 명령부터 살핌
        let (q0, gagiMal, roTtem) = sajeon.mokjeokMal(t)
        let tg = talgeotChatgi(alts)
        var q = MalHagi.talgeotTteokgi(q0)
        // "서울역까지 버스로 가자"처럼 한 번 더 뗌(두 자 넘는 것만)
        let (q2, g2, _) = sajeon.mokjeokMal(q)
        if g2 && MalSajeon.ttuk(q2).count >= 2 { q = q2 }
        // "지금 가는 길 알려 줘"처럼 떼고 나서 남는 것이 없으면 명령으로 봄
        if (!gagiMal || MalSajeon.ttuk(q).count <= 2) && myeongryeong(alts, z, tg, dap) { return }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) "그럼 가자", "어 가자" — 가자를 떼고 남은 것이 한 마디(맞장구)이거나 없으면 찾지 않고 이름을 여쭘
        let qz0 = MalSajeon.ttuk(q)
        if gagiMal && tg == nil && (qz0.isEmpty || MalHagi.geunyangMal(qz0)) {
            mureum = .mokjeok
            mureumTtae = Date()
            dap("가실 곳의 이름을 말씀해 주십시오.", true)
            return
        }

        // 7. 목적지 — 이름 끝의 로(종로·을지로)는 되살려 한 번 더 찾아봄
        var qB: String? = roTtem ? q + "로" : nil
        for p in ["으로", "까지", "에게", "한테", "로", "에"] where q.hasSuffix(p) && q.count > p.count + 1 {
            if p == "로" { qB = q }
            q = String(q.dropLast(p.count)).trimmingCharacters(in: .whitespaces)
            break
        }
        let mureunJung = (mureum == .mokjeok)
        if q.isEmpty {
            mureum = .mokjeok
            mureumTtae = Date()
            if let tg = tg { talgeotDaegi = tg }
            dap("\(ho), 어디로 가실까요?", true)
            return
        }
        // 2.60.0 (이사장님 승인 2026-10-09) 목적지가 정해진 뒤에는 어떤 말이 들려도 "목적지를 변경하실 건가요?"라고 먼저 여쭘.
        //   목적지가 없을 때도 "가자"라는 말 없이 이름만 들렸으면(대답으로 말씀하신 이름 포함) 한 번 여쭘
        let hwagin = !(gagiMal || tg != nil) || y != nil
        let malbeotGa = !(gagiMal || mureunJung || tg != nil)
        let taltgeot = tg ?? talgeotDaegi
        // 즐겨찾기 먼저
        let favs = Jeulgyeo.shared.mokrok.sorted { $0.ireum.count > $1.ireum.count }
        for a in alts {
            let az = MalSajeon.ttuk(a)
            if let f = favs.first(where: { let fz = MalSajeon.ttuk($0.ireum); return !fz.isEmpty && az.contains(fz) }) {
                hubo = [f.jangso]
                huboI = 0
                huboTalgeot = taltgeot
                huboAnnae(dap, hwagin: hwagin)
                return
            }
        }
        let qA = q
        let qB0 = qB
        Task {
            // 2.12.5 "제기역 2번 출구", "제기 전철역" → 지하철역 목록의 바른 이름(제기동역)으로
            let yk = await MalHagi.yeokBarojapgi(qA)
            let res: (String, [Jangso]?)
            if let y = yk { res = await MalHagi.jangsoChatgi(y, nil) } else { res = await MalHagi.jangsoChatgi(qA, qB0) }
            let (q, r) = res
            // 2.26.0 (대표님 지시) "약수역 5번 출구"를 못 찾으면 그 역의 가까운 다른 출구를 권함
            var daean: Jangso? = nil
            var rMatjum = r
            if let y = yk, let bn = MalHagi.chulguBeon(y) {
                if let i = (r ?? []).prefix(5).firstIndex(where: { MalHagi.chulguBeon($0.ireum) == bn }), var rr0 = r {
                    let a = rr0.remove(at: i); rr0.insert(a, at: 0); rMatjum = rr0   // 맞는 출구를 맨 앞으로
                } else {
                    let yeok = y.components(separatedBy: " ").first ?? y
                    let w = await MainActor.run { WichiEngine.shared.jigeum }
                    if let rs = await Chatgi.jangso(yeok + " 출구") {
                        let chulgu = rs.filter { MalHagi.chulguBeon($0.ireum) != nil && MalHagi.chulguBeon($0.ireum) != bn }
                        if let w = w {
                            daean = chulgu.min { WichiEngine.geori(w.lat, w.lon, $0.lat, $0.lon) < WichiEngine.geori(w.lat, w.lon, $1.lat, $1.lon) }
                        } else {
                            daean = chulgu.first
                        }
                    }
                }
            }
            let rFinal = rMatjum
            let daeanFinal = daean
            DispatchQueue.main.async {
                if let dn = daeanFinal, let y = yk, let bn = MalHagi.chulguBeon(dn.ireum) {
                    self.hubo = [dn]
                    self.huboI = 0
                    self.huboTalgeot = taltgeot
                    self.huboHwagin = true
                    self.mureum = .hubo
                    self.mureumTtae = Date()
                    Girok.shared.namgi("chulgu_daean", ["mal": String(y.prefix(30)), "daean": bn])
                    dap("네, \(y)\(MalHagi.eul(y)) 찾지 못했습니다. 가까운 \(bn)번 출구로 안내해 드릴까요?", true)
                    return
                }
                guard let r = rFinal else {
                    dap("찾는 중에 연결이 끊겼습니다. 통신을 확인하시고 다시 말씀해 주십시오.", false)
                    return
                }
                if r.isEmpty {
                    // 2.29.0 사전으로 못 알아들은 말은 폰 안 인공지능에게 한 번 풀어 달라고 함(8초 안에 거듭 묻지 않음)
                    if MalAI.sseulSuItda && Date().timeIntervalSince(self.aiTtae) > 8 {
                        self.aiTtae = Date()
                        Task {
                            let s = await MalAI.puri(t)
                            await MainActor.run {
                                if let s = s, MalSajeon.ttuk(s) != MalSajeon.ttuk(t) {
                                    Girok.shared.namgi("mal_ai", ["jeon": String(t.prefix(40)), "hu": String(s.prefix(40))])
                                    self.cheori([s], dap)
                                } else {
                                    Girok.shared.namgi("mal_ai", ["jeon": String(t.prefix(40)), "hu": ""])
                                    self.motChatgiDap(t, q, malbeotGa, dap)
                                }
                            }
                        }
                        return
                    }
                    self.motChatgiDap(t, q, malbeotGa, dap)
                    return
                }
                // 2.12.7 주소로 말씀하시면(동호로 7길 14) 그 건물 안 가게 이름(주전) 대신 주소를 이름으로(이사장님 승인 1)
                var rr = Array(r.prefix(3))
                if MalHagi.jusoMalinga(qA), let a = rr.first {
                    let jj = a.juso.isEmpty ? qA : a.juso
                    rr = [Jangso(ireum: MalHagi.jusoIreum(jj), juso: jj, lat: a.lat, lon: a.lon)]
                    Girok.shared.namgi("juso_mokjeok", ["mal": String(qA.prefix(30)), "ireum": String(a.ireum.prefix(20))])
                }
                self.hubo = rr
                self.huboI = 0
                self.huboTalgeot = taltgeot
                self.huboAnnae(dap, hwagin: hwagin)
            }
        }
    }

    /// 2.12.7 도로명 주소나 지번 주소로 말씀하셨는가 — 동호로 7길 14, 동호로7길 14번지, 신당동 432-1
    static func jusoMalinga(_ q: String) -> Bool {
        let t = q.trimmingCharacters(in: .whitespaces)
        return t.range(of: "[가-힣0-9]+(로|길)\\s*[0-9]+(\\s*(번?길|가길))?\\s*[0-9-]*\\s*(번지|호)?\\s*$", options: .regularExpression) != nil
            && t.range(of: "[0-9]", options: .regularExpression) != nil
            || t.range(of: "[가-힣]+(동|리|가)\\s*[0-9]+(-[0-9]+)?\\s*(번지)?\\s*$", options: .regularExpression) != nil
    }

    /// 주소를 부를 이름으로 — "서울 중구 동호로7길 14" → "동호로7길 14"
    static func jusoIreum(_ juso: String) -> String {
        let t = juso.trimmingCharacters(in: .whitespaces)
        if let r = t.range(of: "[가-힣0-9]+(로|길)[0-9가-힣]*\\s*[0-9-]+.*$", options: .regularExpression) { return String(t[r]) }
        return t
    }

    /// 명령 — 하면 참
    private func myeongryeong(_ alts: [String], _ z: String, _ tg: Talgeot?, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let s = sajeon
        let y = YeojeongEngine.shared.jigeum
        // 2.6.0 음향신호기 — "신호기 울려 줘", "신호 알려 줘", "신호기 어디", "신호기 찾아 줘"
        if z.contains("신호기") || z.contains("신호알려") || z.contains("신호안내") || z.contains("신호등") {
            // 2.45.0 "주변에 신호기 있어?", "신호기 살펴 줘", "블루투스 신호기 있나" — 있는지와 블루투스로 울릴 수 있는지(이사장님 승인)
            if !(z.contains("어디") || z.contains("위치")) && (z.contains("있") || z.contains("주변") || z.contains("살펴") || z.contains("둘레") || z.contains("블루투스")) {
                dap("", false)
                SinhogiEngine.shared.juByeonSalpigi()
                return true
            }
            if z.contains("찾") {
                dap("음향신호기 찾기를 켭니다. 가까워질수록 소리가 빨라집니다. 그만이라고 하시면 멈춥니다.", false)
                SinhogiEngine.shared.chatgiKyeogi()
            } else {
                let cmd: UInt8 = (z.contains("어디") || z.contains("위치")) ? 1 : 2
                dap("", false)
                SinhogiEngine.shared.ulligi(cmd)
            }
            return true
        }
        // 2.13.0 기분과 날씨에 맞춰 음악 — "기분이 꿀꿀해", "잔잔한 음악 틀어 줘", "날씨에 맞게 틀어 줘"(이사장님 지시)
        if let g = BangsongEngine.gibunChatgi(z),
           g == .nalssi || ["음악", "노래", "틀어", "틀자", "들려", "추천", "곡", "기분"].contains(where: { z.contains($0) }),
           !["그만", "꺼", "끄기", "멈춰", "중지"].contains(where: { z.contains($0) }) {
            Task {
                let m = await BangsongEngine.shared.gibunTeulgi(g)
                DispatchQueue.main.async { dap(m, false) }
            }
            return true
        }
        // 2.13.0 날씨 — "날씨 어때", "오늘 날씨"(웹 길눈과 같은 자료)
        if z.contains("날씨") || z.contains("미세먼지") {
            Task {
                let n = await Nalssi.shared.mal()
                DispatchQueue.main.async { dap(n.isEmpty ? "날씨를 받아 오지 못했습니다. 통신과 위치를 확인해 주십시오." : "날씨는 " + n + ".", false) }
            }
            return true
        }
        // 2.13.0 되짚어 나가기 — "길 기억해 줘"(들어갈 때), "되짚어 나가자"(나올 때)
        if z.contains("되짚") || z.contains("왔던길로나가") || z.contains("들어온길로나가") {
            let d = DoeEngine.shared
            if d.sangtae == .annae { dap("", false); d.jigeumMal() }
            else if d.gieokItda || d.sangtae == .gieok { dap("", false); d.doejipgi() }
            else { dap("기억해 둔 길이 없습니다. 들어가실 때 길 기억해 줘라고 말씀해 주십시오.", false) }
            return true
        }
        if z.contains("길기억") || z.contains("길을기억") || z.contains("길좀기억") {
            if z.contains("그만") || z.contains("멈춰") || z.contains("꺼") { dap("", false); DoeEngine.shared.gieokGeuman(); return true }
            dap("", false)
            DoeEngine.shared.gieokSijak()
            return true
        }
        // 2.22.0 빛 알아보기(카메라 눈) — "불 켜져 있어", "빛 알려 줘", "밝은 쪽 찾아 줘"
        if z.contains("불켜") || z.contains("불꺼") || z.contains("빛알") || z.contains("빛찾") || z.contains("밝은쪽") || z.contains("창문어느") {
            dap("빛 알아보기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.bit) }
            return true
        }
        // 2.21.0 지폐와 색깔 알아보기(카메라 눈) — "무슨 색이야", "색깔 알려 줘", "지폐 알려 줘", "얼마짜리야"
        if z.contains("무슨색") || z.contains("색깔") || z.contains("색알려") || z.contains("색이뭐") || z.contains("지폐") || z.contains("얼마짜리") {
            dap("지폐와 색깔 알아보기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.jipye) }
            return true
        }
        // 2.20.0 상품 바코드 읽기(카메라 눈) — "바코드 읽어 줘", "이 상품 뭐야", "무슨 상품이야"
        if z.contains("바코드") || z.contains("상품뭐") || z.contains("무슨상품") || z.contains("상품이름") || z.contains("상품읽") {
            dap("상품 바코드 읽기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.sangpum) }
            return true
        }
        // 2.23.0 인터넷 없이 한마디 설명(카메라 눈) — "이게 뭐야", "뭐가 보여", "한마디 설명"
        if z.contains("이게뭐") || z.contains("이거뭐") || z.contains("뭐가보여") || z.contains("무엇이보여") || z.contains("한마디설명") {
            dap("한마디 설명을 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.hanmadi) }
            return true
        }
        // 2.18.0 사람 감지(카메라 눈) — "사람 있어", "사람 감지", "앞에 사람"
        if z.contains("사람있") || z.contains("사람감지") || z.contains("앞에사람") || z.contains("사람찾") {
            dap("사람 감지를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.saram) }
            return true
        }
        // 2.17.0 가리키고 말하기(카메라 눈) — "가리키는 거 읽어 줘", "가리킨 글자"
        if z.contains("가리키") || z.contains("가리킨") {
            dap("가리키고 말하기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.gariki) }
            return true
        }
        // 2.16.0 즉석 글자 읽기(카메라 눈)
        if z.contains("글자읽") || z.contains("글읽어") || z.contains("글씨읽") {
            dap("즉석 글자 읽기를 엽니다.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.geulIlgi) }
            return true
        }
        // 2.15.0 문 찾기(카메라 눈)
        if z.contains("문찾") || z.contains("문어디") {
            dap("카메라로 문을 찾습니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.munChatgi) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { MunChatgi.shared.kyeogi("malhagi") }
            return true
        }
        // 2.14.0 말로 그린 길, 음성유도기와 승강기, 현장영상해설 받기
        if z.contains("말로그린") {
            dap("말로 그린 길을 엽니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.malgil) }
            return true
        }
        if z.contains("음성유도기") || z.contains("유도기") || z.contains("승강기") || (z.contains("엘리베이터") && (z.contains("역") || z.contains("어디"))) {
            dap("가까운 역의 음성유도기와 승강기를 찾습니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.yudo) }
            return true
        }
        if z.contains("현장영상해설") || z.contains("현장해설") || z.contains("해설받") || z.contains("해설코스") || z.contains("파견신청") {
            dap("현장영상해설 받기를 엽니다. 현장영상해설사 화상통화, 현장영상해설 코스, 파견 신청 가운데 고르십시오.", false)
            TabGil.shared.tab = 1
            DulreoGil.shared.path = NavigationPath()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(DulreoHwamyeon.haeseol) }
            return true
        }
        // 2.13.0 QR 찾기 — "QR 찾아 줘", "큐알"
        if z.lowercased().contains("qr") || z.contains("큐알") || z.contains("큐아르") {
            dap("QR 찾기를 엽니다.", false)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.qr) }
            return true
        }
        // 2.13.0 배리어프리방송국은 앞으로 따로 앱으로 키우기로 함(이사장님 결정) — 길눈에서는 모르는 척하지 않고 그렇다고 말씀드림
        if z.contains("배프") || z.contains("배리어프리") {
            dap("죄송합니다. 배리어프리방송국은 앞으로 따로 앱으로 만들 예정이라 길눈에는 들어 있지 않습니다.", false)
            return true
        }
        if s.itda(alts, "doumal") {
            dap(MalHagi.doumalMal, false)
            return true
        }
        if s.itda(alts, "dasi") && z.count <= 10 && MalgilEngine.shared.geotneun {   // 2.14.0 말로 그린 길을 걷는 중이면 그 안내를
            dap("", false)
            MalgilEngine.shared.dasiDeutgi()
            return true
        }
        if s.itda(alts, "dasi") && z.count <= 10 {
            dap(ijeonMal.isEmpty ? "다시 들려 드릴 말이 없습니다." : ijeonMal, false)
            return true
        }
        if s.itda(alts, "cheoeum") && z.count <= 8 {
            mok = nil
            talgeotDaegi = nil
            mureum = .mokjeok
            mureumTtae = Date()
            dap("처음부터 하겠습니다. \(ho), 어디로 가실까요?", true)
            return true
        }
        if s.itda(alts, "kol_beonho") {
            let l = s.kolDeul().map { "\($0.ireum) \(MalSajeon.beonhoMal($0.jeonhwa))" }
            if l.isEmpty { dap(s.kolJiyeokMal(), false); return true }
            dap(s.kolJiyeokMal() + " 콜 번호입니다. " + l.joined(separator: ". ") + ".", false)
            return true
        }
        if let jong = kolJong(alts) {
            // 2.60.0 (261009-I14, 이사장님 승인 2026-10-09 남산) "복지 콜 기다리는 중"을 부르라는 말로 알아듣고 한 번 더 걸던 일 —
            //   부르는 말(전화·불러·걸어·연결·호출)이 있을 때만 여쭙고, 없으면 형편만 알려 드림. 걸기 전에는 늘 한 번 여쭘
            kolHwaginMutgi(jong: jong, k: nil, bureum: MalHagi.kolBureumMal(alts), dap)
            return true
        }
        // 2.39.0 지역 콜 이름(두리발, 나드리콜, 새빛콜 등)으로 부르기 — 목적지 이름과 헷갈리지 않게 콜·전화·불러가 함께 있을 때만
        if alts.contains(where: { $0.contains("콜") || $0.contains("전화") || $0.contains("불러") }), let k = s.kolIreumChatgi(alts) {
            kolHwaginMutgi(jong: nil, k: k, bureum: MalHagi.kolBureumMal(alts), dap)
            return true
        }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「나왔어」 갈래는 말 전체이거나 짧은 말의 끝일 때만 내림(「소리가 안 나왔어」는 내림 아님)
        if s.itda(alts, "naerim") || MalSajeon.naoatdaMal(alts) {
            if let yy = y, yy.danggye == .taneunJung || yy.danggye == .taneunGotKkaji {
                dap("", false)
                AnnaeEngine.shared.naeryeotda()
                GilGil.shared.cheotHwamyeon()
            } else {
                dap("지금 타고 가시는 여정이 없습니다. 가실 곳을 말씀해 주시면 안내하겠습니다.", false)
            }
            return true
        }
        if s.itda(alts, "tatda") {
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「차에 탔어」「택시 탔어」는 여정에 지하철 길이 있어도 늘 차 안 안내로.
            //   지하철 역 알림은 「지하철 탔어」「열차 탔어」「전철 탔어」라고 하실 때만
            let jihaMal = z.contains("지하철") || z.contains("열차") || z.contains("전철")
            let beoseuMal = !jihaMal && z.contains("버스") && !z.contains("고속버스")
            let chaMal = !jihaMal && !beoseuMal && (z.contains("차") || z.contains("택시"))
            if let yy = y {
                dap("", false)
                if jihaMal {
                    if yy.jiha != nil { JihacheolEngine.shared.tatda(jadong: false) } else { AnnaeEngine.shared.talgeotBarojapgi(.jihacheol) }
                } else if beoseuMal || (!chaMal && yy.beoseu != nil) {
                    if yy.beoseu != nil { AnnaeEngine.shared.beoseuTatda() } else { AnnaeEngine.shared.talgeotBarojapgi(.beoseu) }
                } else {
                    AnnaeEngine.shared.chaTatda()
                }
                GilGil.shared.cheotHwamyeon()
            } else if let m = mokSaengsaeng {   // 2.61.0 여정이 없으면 3분 안에 정한 곳만(끝난 여정의 옛 목적지로 가지 않게)
                dap("", false)
                AnnaeEngine.shared.chaTagi(m)
                GilGil.shared.cheotHwamyeon()
            } else {
                talgeotDaegi = .cha
                mureum = .mokjeok
                mureumTtae = Date()
                dap("차 안 안내를 하겠습니다. \(ho), 어디로 가십니까?", true)
            }
            return true
        }
        if s.itda(alts, "sigan") {
            if y == nil {
                mureum = .mokjeok
                mureumTtae = Date()
                dap("아직 가시는 곳이 없습니다. \(ho), 어디로 가실까요?", true)
            } else {
                dap(geollineunMal(), false)
            }
            return true
        }
        if s.itda(alts, "jigeum_gil") || s.itda(alts, "charye") {
            if y != nil {
                dap("", false)
                AnnaeEngine.shared.hyeonhwang()
            } else {
                mureum = .mokjeok
                mureumTtae = Date()
                dap("지금 가시는 길이 없습니다. \(ho), 어디로 가실까요?", true)
            }
            return true
        }
        if s.itda(alts, "eodi") {
            dap("", false)
            AnnaeEngine.shared.jigeumJari()
            return true
        }
        if s.itda(alts, "sigan_now") {
            dap(MalHagi.jigeumSigak(), false)
            return true
        }
        // 2.56.0 차 안 안내 정도(웹 길눈 0.84.0과 같은 말 「자세히·간단히·보통으로 안내해」)
        if s.itda(alts, "cha_jasehi") || s.itda(alts, "cha_gandan") || s.itda(alts, "cha_botong") {
            let v = s.itda(alts, "cha_jasehi") ? 3 : (s.itda(alts, "cha_gandan") ? 1 : 2)
            ChaMat.shared.jeongdo = v
            dap(v == 1 ? "차 안 안내를 간단히 합니다. 남은 거리, 길에서 벗어났을 때, 내리는 곳만 말씀드립니다."
                : (v == 2 ? "차 안 안내를 보통으로 합니다." : "차 안 안내를 자세히 합니다. 꺾는 곳마다 미리 말씀드립니다."), false)
            return true
        }
        if s.itda(alts, "bareuge") {
            let d = min(Seoljeong.bbareugiIreum.count - 1, Seoljeong.shared.bbareugiDan + 1)
            Seoljeong.shared.bbareugiDan = d
            dap("\(Seoljeong.bbareugiIreum[d]) 말씀드립니다.", false)
            return true
        }
        if s.itda(alts, "neurige") {
            let d = max(0, Seoljeong.shared.bbareugiDan - 1)
            Seoljeong.shared.bbareugiDan = d
            dap("\(Seoljeong.bbareugiIreum[d]) 말씀드립니다.", false)
            return true
        }
        if s.itda(alts, "annae_kkeum") {
            Seoljeong.shared.malKyeojim = false
            dap("길눈 말소리를 껐습니다. 경고는 그대로 말씀드리고, 말로 하기의 대답은 보이스오버로 드립니다.", false)
            return true
        }
        if s.itda(alts, "annae_kyeom") {
            Seoljeong.shared.malKyeojim = true
            dap("길눈 말소리를 켰습니다.", false)
            return true
        }
        if s.itda(alts, "mok_bakkum") {
            let l = SoriEngine.hangukMoksori()
            if l.count < 2 {
                dap("이 폰에는 한국어 목소리가 하나뿐입니다. 아이폰 설정의 손쉬운 사용, 읽기 및 말하기, 음성에서 한국어 목소리를 더 받으시면 바꿀 수 있습니다.", false)
            } else {
                let jigeum = SoriEngine.shared.moksori()?.identifier ?? ""
                let i = l.firstIndex { $0.identifier == jigeum } ?? -1
                Seoljeong.shared.moksoriId = l[(i + 1) % l.count].identifier
                dap("이 목소리로 말씀드립니다.", false)
            }
            return true
        }
        if s.itda(alts, "hoching") {
            mureum = .hoching
            mureumTtae = Date()
            dap("지금은 \(ho)\(MalHagi.irago(ho)) 부릅니다. 뭐라고 불러 드릴까요?", true)
            return true
        }
        if s.itda(alts, "saerogochim") {
            Tongsin.shared.gamchumBiugi()
            s.bureogi()
            dap("새로고침을 마쳤습니다. 나스에서 새 자료를 받습니다.", false)
            return true
        }
        if s.itda(alts, "jeulgyeo_mok") {
            let l = Jeulgyeo.shared.mokrok.prefix(5).map { $0.ireum }
            if l.isEmpty {
                dap("즐겨찾기가 비어 있습니다. 가시는 곳에서 즐겨찾기에 담아 줘라고 말씀하시면 담아 둡니다.", false)
            } else {
                mureum = .mokjeok
                mureumTtae = Date()
                dap("즐겨찾기에 담긴 곳은 \(l.joined(separator: ", "))입니다. \(ho), 어디로 가실까요?", true)
            }
            return true
        }
        if s.itda(alts, "jeulgyeo_dam") {
            var j: Jangso?
            if let yy = y {
                j = Jangso(ireum: yy.mokjeok.ireum, juso: yy.mokjeok.juso, lat: yy.mokjeok.lat, lon: yy.mokjeok.lon)
            } else {
                j = mok
            }
            if let j = j {
                if Jeulgyeo.shared.damgi(j) {
                    dap("\(j.ireum)\(MalHagi.eul(j.ireum)) 즐겨찾기에 담았습니다.", false)
                } else {
                    dap("\(j.ireum)\(MalHagi.eun(j.ireum)) 이미 즐겨찾기에 있습니다.", false)
                }
            } else {
                dap("담을 곳이 없습니다. 먼저 가실 곳을 말씀해 주십시오.", false)
            }
            return true
        }
        // 2.7.0 둘러보기 — 고장 이야기, 마실, 사진 읽어 주기, 안면인식, 축제, 둘레 찾기
        if s.itda(alts, "gojang") && !(z.contains("노래") || z.contains("음악")) {   // 2.12.7 고장 노래는 음악으로
            Task {
                let r = await Dulreo.gojang()
                DispatchQueue.main.async {
                    guard let r = r, !r.0.isEmpty else { dap("이 고장 이야기를 받지 못했습니다. 통신과 위치를 확인해 주십시오.", false); return }
                    dap(r.0, false)
                }
            }
            return true
        }
        if s.itda(alts, "masil") { dulreoYeolgi(.masil, "마실을 엽니다. 떠나실 고장을 고르십시오.", dap); return true }
        if s.itda(alts, "sajin") { dulreoYeolgi(.sajin, "사진 읽어 주기를 엽니다. 사진 찍어 읽어 주기 단추를 두드리십시오.", dap); return true }
        if z.contains("안면") || z.contains("얼굴인식") || z.contains("누가있") || z.contains("사람있") {
            dulreoYeolgi(.anmyeon, "안면인식을 엽니다.", dap)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { AnmyeonEngine.shared.kyeogi() }
            return true
        }
        if z.contains("축제") {
            Task {
                let r = await Dulreo.gabol("chukje")
                DispatchQueue.main.async {
                    guard let r = r else { dap("축제 소식을 받지 못했습니다. 통신과 위치를 확인해 주십시오.", false); return }
                    if r.isEmpty { dap("10킬로미터 안에 지금 알려진 축제가 없습니다.", false); return }
                    dap("가까운 축제 \(min(3, r.count))곳입니다. " + r.prefix(3).map { $0.julMal }.joined(separator: " ") + " 더 들으시려면 둘러보기 탭의 가는 김에에서 축제를 여십시오.", false)
                }
            }
            return true
        }
        if let jong = DulleJong.malEseo(z),
           ["근처", "가까운", "가까이", "주변", "제일가까", "찾아", "어디"].contains(where: { z.contains($0) }) || z.count <= 6 {
            Task {
                var r = await Dulreo.dulle(jong)
                if jong.id == "sikdang" && (r ?? []).isEmpty {
                    r = await Dulreo.dulle(DulleJong(id: "", ireum: "식당", natmal: "음식점", geot: true))
                }
                DispatchQueue.main.async {
                    let nm = jong.ireum.components(separatedBy: " — ").first ?? jong.ireum
                    guard let r = r else { dap("\(nm)\(MalHagi.eul(nm)) 찾지 못했습니다. 통신과 위치를 확인해 주십시오.", false); return }
                    if r.isEmpty { dap("1킬로미터 안에 \(nm)\(MalHagi.i(nm)) 없습니다.", false); return }
                    self.hubo = r.prefix(3).map { $0.jangso }
                    self.huboI = 0
                    self.huboTalgeot = tg
                    self.huboAnnae(dap, hwagin: true)
                }
            }
            return true
        }
        // 2.8.0 음악·방송 — 다음 곡, 무슨 곡이야, 음악 틀어 줘, 트롯 틀어 줘, 라디오 틀어 줘, 뉴스 들려줘, 음악 꺼
        if bangsongMyeongryeong(alts, alts.first ?? "", z, dap) { return true }
        // 아직 앱에 넣지 못한 기능 — 모르는 척하지 않고, 기록해 두었다가 그 기능을 넣을 때 말로도 되게
        let aJik: [(String, String)] = [("mun_namgigi", "문 남기기"),
                                       ("hwaksin_kkeum", "확신음 켜고 끄기"), ("hwaksin_kyeom", "확신음 켜고 끄기"),
                                       ("nopge", "목소리 높낮이"), ("natge", "목소리 높낮이")]
        for (k, nm) in aJik where s.itda(alts, k) {
            Girok.shared.namgi("malhagi_eopneun", ["k": k, "mal": String((alts.first ?? "").prefix(60))])
            dap("죄송합니다. \(nm)\(MalHagi.eun(nm)) 아직 앱에 넣지 못했습니다. 그 기능을 넣을 때 말로도 되게 하겠습니다. 이 말씀은 기록해 두었습니다.", false)
            return true
        }
        // 탈것만 말씀하심 — "걸어가자", "지하철로 가자"
        if let tg = tg, z.count <= 8 {
            if let yy = y {
                let j = Jangso(ireum: yy.mokjeok.ireum, juso: yy.mokjeok.juso, lat: yy.mokjeok.lat, lon: yy.mokjeok.lon)
                if tg == .georeum {
                    dap("", false)
                    if yy.danggye == .taneunJung { AnnaeEngine.shared.naeryeotda() } else { AnnaeEngine.shared.georeoGagi() }
                    GilGil.shared.cheotHwamyeon()
                } else {
                    gagi(j, tg, dap)
                }
            } else if let m = mokSaengsaeng {   // 2.61.0 여정이 없으면 3분 안에 정한 곳만(끝난 여정의 옛 목적지로 가지 않게)
                gagi(m, tg, dap)
            } else {
                talgeotDaegi = tg
                mureum = .mokjeok
                mureumTtae = Date()
                dap("\(ho), 어디로 가실지 먼저 말씀해 주십시오.", true)
            }
            return true
        }
        // 네·아니오만 — 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 말 전체가 대답일 때만
        let hdM = MalHagi.hwaginDap(alts)
        if hdM == .ye {
            mureum = .mokjeok
            mureumTtae = Date()
            dap("네, \(ho). 어디로 가실까요?", true)
            return true
        }
        if hdM == .ani {
            dap("알겠습니다.", false)
            return true
        }
        return false
    }

    /// 2.8.0 음악·방송 명령 — 하면 참
    private func bangsongMyeongryeong(_ alts: [String], _ t: String, _ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let s = sajeon
        let b = BangsongEngine.shared
        let zl = z.lowercased()
        if s.itda(alts, "gok_daeum") {
            if b.itda { dap("", false); b.daeum() } else { dap("지금 틀고 있는 것이 없습니다.", false) }
            return true
        }
        if s.itda(alts, "gok_ijeon") {
            if b.itda { dap("", false); b.ijeon() } else { dap("지금 틀고 있는 것이 없습니다.", false) }
            return true
        }
        if s.itda(alts, "musun_gok") {
            dap(b.jigeumMal, false)
            return true
        }
        if s.itda(alts, "dasiteul") && b.itda && b.meomchum {
            dap("", false)
            b.meomchumTogeul()
            return true
        }
        let kkeugi = ["그만", "꺼", "끄기", "끄자", "멈춰", "중지"].contains { z.contains($0) }
        // 2.12.7 방송사 이름만 말씀하셔도(MBC 틀어 줘, 엠비시) 라디오로 — 음악 찾기로 새지 않게
        let pj = BangsongEngine.bangsongPyojun(z)
        let bangsongsaMal = ["kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang", "표준fm", "fm4u", "4u", "해피fm", "쿨fm", "클래식fm", "사랑의소리", "한민족"].contains { pj.contains($0) }
        let radio = z.contains("라디오") || zl.contains("fm") || z.contains("에프엠") || bangsongsaMal
        let tv = zl.contains("tv") || z.contains("티비") || z.contains("티브이") || z.contains("텔레비전") || z.contains("듣는방송")
        let nyuseu = s.itda(alts, "nyuseu") || s.itda(alts, "jangae") || z.contains("뉴스") || z.contains("세상이야기") || z.contains("속보") || z.contains("기사읽") || z.contains("기사들려")
        let eumak = s.itda(alts, "eumak") || z.contains("노래") || z.contains("음악") || z.contains("틀어")
        // 2.12.7 지나는 고장 노래 — "고장 노래 틀어 줘", "지나는 고장 노래 꺼"(이사장님 승인 1)
        if z.contains("고장노래") || z.contains("고장음악") || (z.contains("지나는고장") && (z.contains("노래") || z.contains("음악"))) {
            if kkeugi {
                if b.jadoKyeojim { dap("", false); b.jadoKkeugi() } else { dap("지나는 고장 노래는 켜져 있지 않습니다.", false) }
                return true
            }
            if b.jadoKyeojim { dap("지나는 고장 노래가 이미 켜져 있습니다.", false); return true }
            dap("", false)   // 2.19.0 고장 노래는 열쇠 없이 모든 분께
            b.jadoKyeogi()
            return true
        }
        if kkeugi {
            guard b.itda, radio || tv || nyuseu || eumak || z.contains("방송") else { return false }
            dap("", false)
            b.geuman()
            return true
        }
        if nyuseu {
            var g = "all"
            if z.contains("속보") || z.contains("특보") { g = "sokbo" }
            else if z.contains("장애") || z.contains("복지") { g = "jangae" }
            else if z.contains("정치") { g = "jeongchi" }
            else if z.contains("경제") { g = "gyeongje" }
            else if z.contains("국제") || z.contains("세계") { g = "gukje" }
            else if z.contains("사회") { g = "sahoe" }
            else if z.contains("스포츠") || z.contains("연예") { g = "spo" }
            let nm = BangsongEngine.garae.first { $0.0 == g }?.1 ?? "두루 소식"
            Task {
                let ls = await b.gisaBatgi(g)
                DispatchQueue.main.async {
                    guard let ls = ls else { dap("소식을 받아 오지 못했습니다. 통신을 확인해 주십시오.", false); return }
                    if ls.isEmpty { dap("지금 \(nm)에는 새 기사가 없습니다.", false); return }
                    b.gisaYeolgi(0)
                    TabGil.shared.tab = 2
                    BangsongGil.shared.path = NavigationPath()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        BangsongGil.shared.path.append(BangsongHwamyeon.sesang)
                        BangsongGil.shared.path.append(BangsongHwamyeon.gisa)
                    }
                    dap("\(nm), 최신 기사부터 읽어 드립니다. 다음 기사라고 하시면 넘어갑니다.", false)
                }
            }
            return true
        }
        if tv || radio {
            Task {
                let (m, mutneun) = await b.chaeneolMalro(z, kind: tv ? "tv" : "radio")
                DispatchQueue.main.async { dap(m, mutneun) }
            }
            return true
        }
        if eumak {
            var q = t
            for w in ["길 위의", "길위의", "길 위", "길거리", "틀어 주세요", "틀어 줘", "틀어줘", "틀어 봐", "틀어봐", "틀어", "들려 줘", "들려줘", "듣고 싶어", "듣자", "들을래",
                      "음악", "노래", "좀", "곡", "줘"] {
                q = q.replacingOccurrences(of: w, with: " ")
            }
            q = q.trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
            for p in ["을", "를"] where q.hasSuffix(p) && q.count > 2 { q = String(q.dropLast()).trimmingCharacters(in: .whitespaces) }
            if (Yeolsoe.ilgi("eumakTk") ?? "").isEmpty {   // 2.19.0 음악 전체는 다시 열쇠(이사장님 지시 2)
                Task { await b.nugunaTeulgi("", "나스 음악 열쇠를 아직 넣지 않으셔서 누구나 음악을 틉니다. 지나는 고장 노래는 열쇠 없이 고장 노래 틀어 줘라고 하시면 됩니다.") }
                dap("", false)
                return true
            }
            if q.isEmpty {
                if b.itda && b.meomchum { dap("", false); b.meomchumTogeul(); return true }
                Task { await b.galraeTeulgi("가요", "") }
                dap("", false)
                return true
            }
            b.biseutQ = nil
            Task {
                let m = await b.malChatgi(q)
                DispatchQueue.main.async {
                    // 2.46.0 비슷한 곡을 여쭈었으면 "네"를 기다림
                    if b.biseutQ != nil { self.mureum = .eumakBiseut; self.mureumTtae = Date(); dap(m, true) } else { dap(m, false) }
                }
            }
            return true
        }
        return false
    }

    /// 2.7.0 둘러보기 탭의 화면을 열어 드림
    private func dulreoYeolgi(_ h: DulreoHwamyeon, _ mal: String, _ dap: @escaping (String, Bool) -> Void) {
        TabGil.shared.tab = 1
        DulreoGil.shared.path = NavigationPath()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { DulreoGil.shared.path.append(h) }
        dap(mal, false)
    }

    // MARK: 가기

    /// 찾은 곳을 알려 드리고 곧바로 안내 — 가자는 말 없이 이름만 들렸으면 한 번 여쭘
    private func huboAnnae(_ dap: @escaping (String, Bool) -> Void, hwagin: Bool) {
        guard huboI < hubo.count else { return }
        let h = hubo[huboI]
        // 2.26.0 (대표님 지시) "네, 약수역 5번 출구는 1시 방향, 120미터에 있습니다."
        var m = "네, " + h.ireum
        if let w = WichiEngine.shared.jigeum {
            let d = WichiEngine.geori(w.lat, w.lon, h.lat, h.lon)
            if d < 30 {
                m += MalHagi.eun(h.ireum) + " 지금 계신 곳 바로 가까이에 있습니다."
            } else {
                let bang = w.banghyang >= 0 ? "\(MunChatgi.sigye(GanpanAllim.bangwi(w.lat, w.lon, h.lat, h.lon) - w.banghyang))시 방향, " : ""
                m += MalHagi.eun(h.ireum) + " \(bang)\(Annae.geoMal(d))에 있습니다."
            }
        } else {
            m += "."
        }
        mureum = .hubo
        mureumTtae = Date()
        huboHwagin = hwagin
        if hwagin {
            // 2.60.0 (이사장님 승인 2026-10-09) 이미 가시는 곳이 있으면 바꿀지를 분명히 여쭘
            if let yy = YeojeongEngine.shared.jigeum, MalSajeon.ttuk(yy.mokjeok.ireum) != MalSajeon.ttuk(h.ireum) {
                dap(m + " 지금은 \(yy.mokjeok.ireum)\(MalHagi.ro(yy.mokjeok.ireum)) 가시는 중입니다. 목적지를 \(h.ireum)\(MalHagi.ro(h.ireum)) 변경하실 건가요?", true)
            } else {
                dap(m + " 여기로 안내할까요?", true)
            }
            return
        }
        let dareun = hubo.count > huboI + 1 ? " 다른 곳이면 불러서 다른 곳이라고 하십시오." : ""
        gagi(h, huboTalgeot, dap, apMal: m + " 여기로 안내합니다." + dareun + " ")
    }

    private func daeumHubo(_ dap: @escaping (String, Bool) -> Void) {
        huboI += 1
        if huboI < hubo.count {
            huboAnnae(dap, hwagin: huboHwagin)
        } else {
            mureum = .mokjeok
            mureumTtae = Date()
            dap("다른 곳을 말씀해 주십시오. \(ho), 어디로 가실까요?", true)
        }
    }

    /// 탈것에 맞춰 안내 시작 — 대답을 먼저 하고 안내 엔진을 움직임(말 차례가 맞게)
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 기억해 둔 가실 곳 — 여정이 있거나, 이번 물음 흐름에서 3분 안에 정한 것만
    ///   (여정이 끝난 뒤 「택시 탔어」「걸어가자」로 옛 목적지를 향해 안내를 시작하던 일)
    private var mokSaengsaeng: Jangso? {
        guard let m = mok else { return nil }
        if YeojeongEngine.shared.jigeum != nil || Date().timeIntervalSince(mokTtae) < 180 { return m }
        return nil
    }

    private func gagi(_ j: Jangso, _ tg0: Talgeot?, _ dap: @escaping (String, Bool) -> Void, apMal: String = "") {
        mok = j
        mokTtae = Date()
        Jeulgyeo.shared.sseum(j)
        let d: Double? = WichiEngine.shared.jigeum.map { WichiEngine.geori($0.lat, $0.lon, j.lat, j.lon) }
        let yj = YeojeongEngine.shared
        yj.talgeotSaeroBogi()   // 2.60.0 묵은 탈것 판단을 지우고 새로 봄(이사장님 승인 2026-10-09)
        let taneunJung = yj.sokdoChujeong != .georeum || (yj.jigeum?.danggye == .taneunJung && yj.jigeum?.jiha == nil && yj.choegeunTalgeotUmjigim)
        var tg = tg0 ?? talgeotDaegi
        talgeotDaegi = nil
        if tg == nil {
            if taneunJung {
                tg = yj.sokdoChujeong == .jihacheol ? .jihacheol : .cha   // 2.40.0 땅속에서 타고 가는 중이면 지하철로
            } else if d == nil || (d ?? 0) <= 2000 {
                tg = .georeum
            }
        }
        let mk = Mokjeok(ireum: j.ireum, lat: j.lat, lon: j.lon, juso: j.juso)
        guard let g = tg else {
            yj.jeonghagi(mk)
            GilGil.shared.cheotHwamyeon()
            mureum = .bangsik
            mureumTtae = Date()
            dap(apMal + "걸어가시기에는 먼 곳입니다. \(ho), 차로, 지하철로, 버스로 가운데 어떻게 가실까요?", true)
            return
        }
        switch g {
        case .georeum:
            // 2.10.0 맞는 점지도가 있으면 "점지도로 걸을까요, 위성으로 걸을까요" 한 번 여쭘(네 하시면 점지도)
            GilGil.shared.cheotHwamyeon()
            JeomEngine.shared.georeoGagiBoda(j) { [weak self] q in
                if let q = q {
                    self?.mureum = .jeom
                    self?.mureumTtae = Date()
                    dap(apMal + q, true)
                } else {
                    dap(apMal, false)
                }
            }
        case .cha, .gicha, .gosokbeoseu:
            if taneunJung {
                dap(apMal + "타고 가시는 중이니 차 안 안내로 잇습니다.", false)
                AnnaeEngine.shared.chaTagi(j)
            } else {
                yj.jeonghagi(mk)
                mureum = .chaYocheong
                mureumTtae = Date()
                dap(apMal + "차를 요청할까요? 이미 차가 있으시면 타신 뒤 차에 탔어라고 말씀해 주십시오.", true)
            }
            GilGil.shared.cheotHwamyeon()
        case .jihacheol:
            // 2.40.0 이미 열차를 타고 가는 중이면(움직임 감지기가 탈것, 또는 땅속에서 탈것) 역 입구 안내를 건너뛰고 곧장 역 알림
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 땅속 승강장에 서 계실 때 곧장 역 알림을 시작하던 일 —
            //   움직임 감지기가 지하철로 보고 30초 안에 탈것이 실제로 움직였을 때만 타고 가는 중으로 봄.
            //   아니면 역 입구에 닿은 것으로 보아(ipguDochak) 열차가 움직이면 저절로 역 알림을 시작
            let tg2 = TalgeotGamji.shared
            let umjigimNa = tg2.majimakTalgeot.map { Date().timeIntervalSince($0) < 30 } ?? false
            if tg2.chujeong == .jihacheol && umjigimNa {
                SoriEngine.shared.mal(apMal + "타고 가시는 중이니 지하철 길을 찾아 곧장 역 알림을 시작합니다.", .jeongbo)
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 이용자가 지하철로 가자고 하셨으니 짐작 안내가 아님(땅 위 구간에서 차로 되돌리지 않음)
                JihacheolEngine.shared.jungganSijak(j, jadong: false) { ok, mal in
                    GilGil.shared.cheotHwamyeon()
                    dap(ok ? mal : mal + " 잠시 뒤 다시 지하철로 가자고 말씀해 주십시오.", false)
                }
                return
            }
            let ttangsok = tg2.jiha
            let buteo: Wichi? = ttangsok ? tg2.jisangJari : nil
            SoriEngine.shared.mal(apMal + "지하철 길을 찾는 중입니다.", .jeongbo)
            Task {
                let (gg, k) = await JihacheolEngine.gilChatgi(j, buteo: buteo)
                DispatchQueue.main.async {
                    if let gg = gg {
                        if ttangsok {
                            // 이미 땅속(역 안) — 걷는 안내 없이 역 입구에 닿은 것으로
                            dap(gg.mal, false)
                            AnnaeEngine.shared.jihacheolGagi(j, gg, malEopsi: true)
                            JihacheolEngine.shared.ipguDochak()
                        } else {
                            dap("\(gg.mal) 들어갈 곳은 \(gg.ipgu.ireum)입니다.", false)
                            AnnaeEngine.shared.jihacheolGagi(j, gg)
                        }
                        GilGil.shared.cheotHwamyeon()
                    } else {
                        yj.jeonghagi(mk)
                        self.mureum = .bangsik
                        self.mureumTtae = Date()
                        dap(k + " 걸어서, 차로, 버스로 가운데 어떻게 가실까요?", true)
                    }
                }
            }
        case .beoseu:
            guard let w = WichiEngine.shared.jigeum else {
                dap(apMal + "아직 위치를 잡는 중이라 가까운 정류장을 찾지 못했습니다. 잠시 뒤 다시 말씀해 주십시오.", false)
                return
            }
            SoriEngine.shared.mal(apMal + "가까운 정류장을 찾는 중입니다.", .jeongbo)
            Task {
                let r = await Beoseu.gakkaun(w.lat, w.lon)
                DispatchQueue.main.async {
                    if let jr = r?.first {
                        dap("가장 가까운 정류장은 \(Beoseu.julMal(jr))입니다.", false)
                        AnnaeEngine.shared.beoseuGagi(j, jr)
                        GilGil.shared.cheotHwamyeon()
                    } else {
                        yj.jeonghagi(mk)
                        self.mureum = .bangsik
                        self.mureumTtae = Date()
                        dap("가까운 정류장을 찾지 못했습니다. 걸어서, 차로, 지하철로 가운데 어떻게 가실까요?", true)
                    }
                }
            }
        }
    }

    // MARK: 2.12.5 역 이름과 번호

    /// 한글로 적힌 번호를 숫자로 — "약수역 오 번출구" → "약수역 5번 출구"(번 뒤에 출구·출입구가 올 때만)
    static func beonhoSutja(_ t: String) -> String {
        let su: [String: String] = ["일": "1", "이": "2", "삼": "3", "사": "4", "오": "5", "육": "6", "륙": "6", "칠": "7", "팔": "8", "구": "9",
                                    "십": "10", "십일": "11", "십이": "12", "십삼": "13", "십사": "14", "십오": "15", "십육": "16",
                                    "십칠": "17", "십팔": "18", "십구": "19", "이십": "20"]
        guard let re = try? NSRegularExpression(pattern: "(?<![가-힣0-9])(이십|십[일이삼사오육륙칠팔구]?|[일이삼사오육륙칠팔구])\\s*번\\s*(?=출)") else { return t }
        var s = t
        let ns = s as NSString
        for m in re.matches(in: s, range: NSRange(location: 0, length: ns.length)).reversed() {
            let w = ns.substring(with: m.range(at: 1))
            guard let d = su[w], let r = Range(m.range, in: s) else { continue }
            s.replaceSubrange(r, with: d + "번 ")
        }
        return s
    }

    /// 역 이름을 바로잡음 — 역을 찾는 말이 아니면 nil. "제기역 2번 출구" → "제기동역 2번 출구", "제기 전철역" → "제기동역"
    static func yeokBarojapgi(_ q: String) async -> String? {
        let t = q.trimmingCharacters(in: .whitespaces)
        guard let re = try? NSRegularExpression(pattern: "^(.+?)\\s*(지하철역|전철역|지하철|전철|역)\\s*(?:(\\d{1,2})\\s*번\\s*(?:출구|출입구)?)?$") else { return nil }
        let ns = t as NSString
        guard let m = re.firstMatch(in: t, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let bon = ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespaces)
        let beon = m.range(at: 3).location != NSNotFound ? ns.substring(with: m.range(at: 3)) : ""
        guard !bon.isEmpty, bon.count <= 10 else { return nil }
        var ireum = bon.replacingOccurrences(of: " ", with: "")
        if let o = await Chatgi.json("yeok.php", ["a": "chatgi", "q": ireum]),
           let rows = o["rows"] as? [[String: Any]] {
            let nms = rows.compactMap { $0["nm"] as? String }
            if let n = nms.first(where: { $0 == ireum }) ?? nms.first(where: { $0.hasPrefix(ireum) }) { ireum = n }
        }
        let bakkum = ireum + "역" + (beon.isEmpty ? "" : " \(beon)번 출구")
        Girok.shared.namgi("yeok_barojapgi", ["jeon": String(t.prefix(30)), "hu": bakkum])
        return bakkum
    }

    /// 가까운 역 이름(받아쓰기에 미리 알려 줄 것) — 자리가 300미터 넘게 바뀌면 새로 받음
    private var gakkaunYeok: [String] = []
    private var gakkaunYeokJari: (Double, Double)?
    private func gakkaunYeokGaengsin() {
        guard let w = WichiEngine.shared.jigeum else { return }
        if let j = gakkaunYeokJari, WichiEngine.geori(j.0, j.1, w.lat, w.lon) < 300 { return }
        gakkaunYeokJari = (w.lat, w.lon)
        Task {
            let q = ["a": "gakkaun", "lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon)]
            guard let o = await Chatgi.json("yeok.php", q), let rows = o["rows"] as? [[String: Any]] else { return }
            var l: [String] = []
            for r in rows { if let y = r["yeok"] as? String, !l.contains(y + "역") { l.append(y + "역") } }
            DispatchQueue.main.async { self.gakkaunYeok = Array(l.prefix(12)) }
        }
    }

    /// 명령을 들을 때 받아쓰기에 미리 알려 줄 말
    private func doumMal() -> [String] {
        var l = gakkaunYeok
        l += Jeulgyeo.shared.mokrok.prefix(30).map { $0.ireum }
        l += ["출구", "번 출구", "여기가 어디야", "길 위의 음악", "라디오 틀어 줘", "뉴스 들려줘", "집으로 가자"]
        return Array(l.prefix(80))
    }

    /// 되살린 이름(qB)으로 찾아 첫 곳 이름에 그 말이 들어 있으면 그것을, 아니면 뗀 이름(qA)으로
    private static func jangsoChatgi(_ qA: String, _ qB: String?) async -> (String, [Jangso]?) {
        if let b = qB, let rb = await Chatgi.jangso(b), !rb.isEmpty {
            // 뗀 이름이 한 자뿐이면(종로 → 종) 되살린 이름으로, 아니면 찾은 곳 이름에 그 말이 들어 있을 때만
            let bz = MalSajeon.ttuk(b)
            if MalSajeon.ttuk(qA).count <= 1 || rb.prefix(3).contains(where: { MalSajeon.ttuk($0.ireum).contains(bz) }) {
                return (b, rb)
            }
        }
        return (qA, await Chatgi.jangso(qA))
    }

    // MARK: 긴급통화

    /// 해설사·봉사자·명단의 이름이 바로 들리면 곧장 요청 — 하면 참
    private func gingeupJikjeop(_ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        if z.contains("해설") {
            mureum = .eopseum
            dap("", false)
            GinGeup.shared.yocheong(.haeseolsa)
            return true
        }
        if z.contains("봉사") {
            mureum = .eopseum
            dap("", false)
            GinGeup.shared.yocheong(.haebong)
            return true
        }
        let l = Jiin.shared.mokrok.sorted { $0.name.count > $1.name.count }
        if let s = l.first(where: { let nz = MalSajeon.ttuk($0.name); return !nz.isEmpty && z.contains(nz) }) {
            mureum = .eopseum
            dap("", false)
            GinGeup.shared.yocheong(.jiin, s)
            return true
        }
        return false
    }

    /// 누구에게 요청할지 여쭘(명단을 먼저 받아 봄)
    private func gingeup(_ t: String, _ dap: @escaping (String, Bool) -> Void) {
        let z = MalSajeon.ttuk(t)
        Task {
            if Jiin.shared.mokrok.isEmpty { await Jiin.shared.bureogi() }
            DispatchQueue.main.async {
                if self.gingeupJikjeop(z, dap) { return }
                let l = Jiin.shared.mokrok
                self.mureum = .galrae
                self.mureumTtae = Date()
                if z.contains("가족") || z.contains("지인") {
                    if l.isEmpty {
                        dap("가족·지인 명단이 비어 있습니다. 설정 탭의 가족·지인 명단에서 먼저 등록해 주십시오. 자원봉사자나 현장영상해설사에게 요청하시려면 말씀해 주십시오.", true)
                    } else {
                        dap("가족·지인 가운데 누구에게 요청할까요? " + l.prefix(5).map { $0.name }.joined(separator: ", ") + ".", true)
                    }
                } else {
                    dap("\(self.ho), 누구에게 요청할까요? 가족·지인이면 이름을, 아니면 자원봉사자나 현장영상해설사라고 말씀해 주십시오.", true)
                }
            }
        }
    }

    // MARK: 콜

    private func kolJong(_ alts: [String]) -> String? {
        if sajeon.itda(alts, "kol_jangaein") { return "jangaein" }
        if sajeon.itda(alts, "kol_nabi") { return "nabi" }
        if sajeon.itda(alts, "kol_bokji") { return "bokji" }
        return nil
    }

    private func kolMutgi() -> String {
        let l = sajeon.kolDeul()
        if l.isEmpty { return sajeon.kolJiyeokMal() }
        return sajeon.kolJiyeokMal() + " 어디에 전화할까요? " + l.prefix(5).map { $0.ireum }.joined(separator: ", ") + " 가운데 말씀해 주십시오. 복지콜이라고만 하셔도 이 지역 센터로 겁니다."
    }

    /// 2.60.0 곳 이름 같은 말인가(코스·입구·산책로·공원·출구·정류장·광장·시장·병원·센터·둘레길)
    static func jangsoGateunMal(_ t: String) -> Bool {
        ["코스", "입구", "산책로", "공원", "출구", "정류장", "광장", "시장", "병원", "센터", "둘레길", "주차장", "매표소"].contains { t.contains($0) }
    }

    /// 2.60.0 곳 이름으로 찾지 않을 한 마디(맞장구·망설임·대답)
    static let geunyangMalDeul: Set<String> = ["그럼", "그럼요", "그래", "그래요", "그래그래", "그래서", "그러면", "그러니까", "그렇지", "그렇죠", "그렇구나",
        "응", "응응", "어", "어어", "음", "음음", "으음", "글쎄", "글쎄요",
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「됐어」「됐어요」는 뺌 — 맞장구가 아니라 아니오·그만으로 받음(「네, 말씀하십시오」라 하던 일)
        "좋아", "좋아요", "알았어", "알았어요", "알겠어", "알겠어요", "알겠습니다", "맞아", "맞아요", "오케이", "잠깐", "잠깐만",
        "뭐", "뭐야", "왜", "저기", "저기요", "있잖아", "여보세요", "나는그래", "아", "아아", "에", "야", "자", "참", "글쎄다", "그래서요"]
    static func geunyangMal(_ z: String) -> Bool {
        geunyangMalDeul.contains(z)
    }

    // MARK: 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 네·아니오는 말 전체로만 — 안드로이드 MalHagi.hwaginDap 과 같은 낱말, 같은 잣대

    enum HwaginDap { case ye, ani }
    /// 네로 받는 말(띄어쓰기를 뺀 꼴)
    static let yeMalDeul: [String] = ["네", "예", "응", "그래", "그래요", "좋아", "좋아요", "맞아", "맞아요", "그렇게해", "그렇게해줘",
        "가자", "안내해", "안내해줘", "걸어줘", "전화해", "전화해줘", "부탁해", "오케이", "네네", "응응", "예예", "그럼요"]
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 배차되었다는 대답인가 — 「배차됐어」「배차 됐어」「배차되었어」(알아들은 말 가운데 하나라도)
    static func baechaDapMal(_ alts: [String]) -> Bool {
        alts.contains { a in
            let z = MalSajeon.ttuk(a)
            return z.count <= 12 && (z.contains("배차됐") || z.contains("배차되었") || z.contains("배차돼"))
        }
    }

    /// 아니오로 받는 말(띄어쓰기를 뺀 꼴)
    static let aniMalDeul: [String] = ["아니", "아니요", "아니오", "아뇨", "됐어", "안해", "싫어", "그만", "하지마"]
    /// 대답 낱말 뒤에 붙어도 되는 끝 글자(두 자까지) — 「네요」「아니야」는 받고 「응급실」「예술」「네거리」는 받지 않음
    private static let kkoriGeulja: Set<Character> = ["요", "네", "예", "응", "죠", "지", "야", "어", "에", "해", "줘", "다", "니", "세", "용", "여"]

    /// 한 마디가 네인가 아니오인가 — 말 전체가 대답 낱말이거나, 대답 낱말 뒤에 끝 글자 두 자까지, 또는 대답 낱말 둘(네 좋아요)
    static func hwaginDap1(_ z0: String) -> HwaginDap? {
        let z = MalSajeon.ttuk(z0)
        guard !z.isEmpty else { return nil }
        if aniMalDeul.contains(z) { return .ani }
        if yeMalDeul.contains(z) { return .ye }
        let mokrok: [([String], HwaginDap)] = [(aniMalDeul, .ani), (yeMalDeul, .ye)]
        for (l, d) in mokrok {
            for w in l.sorted(by: { $0.count > $1.count }) where z.hasPrefix(w) {
                let kkori = String(z.dropFirst(w.count))
                if kkori.count <= 2 && kkori.allSatisfy({ kkoriGeulja.contains($0) }) { return d }
                if l.contains(kkori) || (d == .ye && ["알겠어", "알겠어요", "알겠습니다", "알았어", "알았어요"].contains(kkori)) { return d }
            }
        }
        return nil
    }

    /// 알아들은 말(맨 앞의 것)이 네인가 아니오인가 — 다른 후보 말로는 셈하지 않음
    static func hwaginDap(_ alts: [String]) -> HwaginDap? {
        guard let a = alts.first else { return nil }
        return hwaginDap1(a)
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 콜을 취소했다는 말 — "콜 취소", "콜 취소했어", "택시 취소", "복지콜 취소했어"
    static func kolChwisoMal(_ z: String) -> Bool {
        (z.contains("콜") || z.contains("택시")) && z.contains("취소") && z.count <= 12
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 묻는 말인가 — 곳 이름으로 다시 찾지 않음
    static func mutneunMal(_ t: String) -> Bool {
        let z = MalSajeon.ttuk(t)
        return ["어때", "알려줘", "어떻게", "얼마", "가격", "예약", "몇시"].contains { z.contains($0) }
    }

    /// 2.60.0 목적지를 바꾸자는 말("변경", "목적지 변경", "목적지 바꿔", "다른 데로 바꿔")
    static func byeongyeongMal(_ z: String) -> Bool {
        if ["변경", "변경해", "변경해줘", "바꿔", "바꿔줘", "바꾸자", "목적지", "목적지변경", "목적지바꿔", "목적지바꿔줘", "목적지바꾸자", "목적지변경해줘",
            "목적지를변경", "목적지를바꿔", "목적지를바꿔줘", "목적지를변경해줘", "가는곳바꿔", "가는곳변경", "다른곳으로바꿔", "다른데로바꿔"].contains(z) { return true }
        return z.count <= 10 && (z.hasPrefix("목적지") || z.hasPrefix("가는곳")) && (z.contains("변경") || z.contains("바꿔") || z.contains("바꾸"))
    }

    /// 2.60.0 부르는 말인가 — 전화·불러·걸어·연결·호출·콜 해
    static func kolBureumMal(_ alts: [String]) -> Bool {
        alts.contains { a in
            let z = MalSajeon.ttuk(a)
            return ["전화", "불러", "부르", "걸어", "걸자", "걸까", "연결", "호출", "콜해", "콜좀", "콜불", "잡아", "요청"].contains { z.contains($0) }
        }
    }

    /// 2.60.0 (261009-I14, 이사장님 승인) 콜에 걸기 전 — 3시간 안에 이미 부르셨으면 형편을 알려 드리고, 걸 때는 늘 한 번 여쭘
    private func kolHwaginMutgi(jong: String?, k k0: KolBeonho?, bureum: Bool, _ dap: @escaping (String, Bool) -> Void) {
        var k = k0
        if k == nil, let jong = jong { k = sajeon.kolChatgi(jong) }
        guard let kk = k else {
            if let jong = jong { kolGeolgi(jong, dap) }   // 이 지역 목록에 없음 등의 대답
            return
        }
        var hyeongpyeon = ""
        if let h = ChaBureugi.shared.choegeun {
            let cal = Calendar.current
            let si = JeonggiHochul.sigakMal(cal.component(.hour, from: h.ttae), cal.component(.minute, from: h.ttae))
            hyeongpyeon = "\(si)에 \(h.ireum)에 전화하셨습니다. "
            if h.tan != nil { hyeongpyeon += "지금 차에 타고 계신 것으로 압니다. " }
            else if h.gyeolgwa == "baecha" { hyeongpyeon += "배차되어 차를 기다리시는 중입니다. " }
            else if h.gyeolgwa == "gidarim" { hyeongpyeon += "기다리라고 해서 기다리시는 중입니다. " }
        }
        if !bureum && !hyeongpyeon.isEmpty {
            mureum = .eopseum
            dap(hyeongpyeon + "다시 거시려면 \(kk.ireum)에 전화해 줘라고 말씀해 주십시오.", false)
            return
        }
        kolDaegi = kk
        mureum = .kolHwagin
        mureumTtae = Date()
        dap(hyeongpyeon + (hyeongpyeon.isEmpty ? "" : "그래도 다시 ") + "\(kk.ireum)에 전화할까요?", true)
    }

    private func kolGeolgi(_ jong: String, _ dap: @escaping (String, Bool) -> Void) {
        guard let k = sajeon.kolChatgi(jong) else {
            if sajeon.kolDeul().isEmpty { dap(sajeon.kolJiyeokMal(), false); return }
            dap("그 콜은 이 지역 목록에 없습니다. 콜 번호 알려 줘라고 말씀하시면 이 지역 번호를 읽어 드립니다.", false)
            return
        }
        Girok.shared.namgi("malhagi_kol", ["k": jong])
        kolGeolgiK(k, dap)
    }

    /// 2.39.0 고른 콜에 바로 걸기(지역 이름을 함께 알려 드림)
    private func kolGeolgiK(_ k: KolBeonho, _ dap: @escaping (String, Bool) -> Void) {
        Girok.shared.namgi("malhagi_kol_georeum", ["ireum": k.ireum])
        // 2.58.0 차 부르기 엔진으로 — 기록을 남기고, 상담원께 말할 것을 들려 드린 뒤 겁니다
        ChaBureugi.shared.geolgi(k, dap: dap)
    }

    // MARK: 돕는 말

    /// 남은 거리와 대략 걸리는 시간(길 막힘은 넣지 못한 셈)
    private func geollineunMal() -> String {
        guard let y = YeojeongEngine.shared.jigeum else { return "아직 가시는 곳이 없습니다." }
        guard let w = WichiEngine.shared.jigeum else { return "아직 위치를 잡는 중입니다. 잠시 뒤 다시 물어 주십시오." }
        let d = WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        let t = YeojeongEngine.shared.talgeot
        let sokdo: Double
        let doragam: Double
        switch t {
        case .georeum: sokdo = 1.1; doragam = 1.3
        case .jihacheol: sokdo = 8.5; doragam = 1.3
        case .beoseu: sokdo = 5.0; doragam = 1.4
        case .gicha: sokdo = 25; doragam = 1.2
        case .gosokbeoseu: sokdo = 20; doragam = 1.2
        case .cha: sokdo = 7.0; doragam = 1.35
        }
        let bun = max(1, Int((d * doragam / sokdo / 60).rounded()))
        let bunMal = bun < 60 ? "\(bun)분" : (bun % 60 == 0 ? "\(bun / 60)시간" : "\(bun / 60)시간 \(bun % 60)분")
        let tal = t == .georeum ? "걸어서" : "\(t.ireum)\(MalHagi.ro(t.ireum))"
        return "\(y.mokjeok.ireum)까지 남은 거리는 \(Annae.geoMal(d)), \(tal) 대략 \(bunMal) 걸립니다. 길 막힘은 넣지 못한 셈입니다."
    }

    private static func jigeumSigak() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일 EEEE a h시 m분"
        return "지금은 \(f.string(from: Date()))입니다."
    }

    /// 2.12.6 하던 일 멈추기 때 — 묻던 말과 기다리던 목적지·탈것·후보를 모두 비움
    func mureumChoGihwa() {
        mureum = .eopseum
        mok = nil
        talgeotDaegi = nil
        hubo = []
        huboI = 0
        huboTalgeot = nil
        huboHwagin = false
    }

    static let doumalMal = "이렇게 말씀하시면 됩니다. 집으로 가자. 걸어서 가자. 지하철로 가자. 버스로 가자. 차에 탔어. 내렸어. 자세히 안내해, 간단히 안내해, 보통으로 안내해. 얼마나 남았어. 지금 어디야. 지금 가는 길 알려 줘. 즐겨찾기 목록. 즐겨찾기에 담아 줘. 복지콜에 전화해 줘. 콜 번호 알려 줘. 콜 취소. 신호기 울려 줘. 신호기 찾아 줘. 근처 약국. 음악 틀어 줘. 트롯 틀어 줘. 다음 곡. 라디오 틀어 줘. MBC 라디오. 뉴스 들려줘. 음악 꺼. 고장 노래 틀어 줘. 기분이 꿀꿀해. 날씨에 맞게 틀어 줘. 날씨 어때. 길 기억해 줘. 되짚어 나가자. QR 찾아 줘. 말로 그린 길. 음성유도기 어디 있어. 현장영상해설 받고 싶어. 문 찾아 줘. 글자 읽어 줘. 가리키는 거 읽어 줘. 사람 있어. 바코드 읽어 줘, 이 상품 뭐야. 무슨 색이야, 얼마짜리야. 불 켜져 있어, 밝은 쪽 찾아 줘. 이게 뭐야, 뭐가 보여. 도착. 목적지 바꿔. 도와줘, 또는 가족 이름과 화상통화. 몇 시야. 말 빠르게, 말 느리게. 다시 말해. 그만. 여정 끝. 하던 일 멈춰. 점지도를 따라 걸을 때는 다음에 무엇, 그만 걷기, 여기 문제 있어, 여기 걸렸어. 가까운 점지도 찾아 줘."

    /// 2.29.0 인공지능이 풀어 본 때(거듭 묻지 않으려고)
    private var aiTtae = Date.distantPast

    /// 찾는 곳을 못 찾았을 때의 대답
    private func motChatgiDap(_ t: String, _ q: String, _ hwagin: Bool, _ dap: @escaping (String, Bool) -> Void) {
        motAradeureum(t)
        mureum = .mokjeok
        mureumTtae = Date()
        // 2.60.0 (261009-I14, 이사장님 승인) 곳 이름 같은 말("남산 산책로 B코스")을 못 찾으면 말벗으로 넘기지 않고
        //   앞 낱말(남산)로 다시 찾아 가까운 곳을 여쭘
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 가자고 하셨든, 어디로 가실까요의 대답이든 늘 다시 찾음(말벗으로 넘기는 것만 가자는 말이 없을 때).
        //   다만 묻는 말(어때·알려 줘·어떻게·얼마·가격·예약·몇 시)이면 다시 찾지 않음
        let nat = t.split(separator: " ").map(String.init)
        if MalHagi.jangsoGateunMal(t), !MalHagi.mutneunMal(t), nat.count >= 2, let ap = nat.first, ap.count >= 2 {
            Task {
                let r = await Chatgi.jangso(ap)
                await MainActor.run {
                    if let r = r, !r.isEmpty {
                        self.hubo = Array(r.prefix(3))
                        self.huboI = 0
                        self.huboTalgeot = nil
                        let ap2 = "\(q)\(MalHagi.eul(q)) 찾지 못했습니다. 비슷한 곳으로 "
                        self.huboAnnae({ m, b in dap(ap2 + (m.hasPrefix("네, ") ? String(m.dropFirst(3)) : m), b) }, hwagin: true)
                    } else {
                        dap("죄송합니다. \(q)\(MalHagi.eul(q)) 찾지 못했습니다. 다른 이름으로 말씀해 주십시오.", true)
                    }
                }
            }
            return
        }
        if hwagin {
            // 2.49.0 명령도 곳 이름도 아니면 말벗(서버 인공지능)에게 물어 끝까지 대답함
            malbeotMutgi(t) { [weak self] d in
                guard let self = self else { return }
                if let d = d {
                    self.mureum = .eopseum
                    dap(d, false)
                } else {
                    dap(self.sagwa() + "다시 말씀해 주십시오.", true)
                }
            }
        } else {
            dap("죄송합니다. \(q)\(MalHagi.eul(q)) 찾지 못했습니다. 다른 이름으로 말씀해 주십시오.", true)
        }
    }

    /// 2.60.0 말벗 대답을 기다리는 차례 번호(0이면 기다리지 않음)
    private var malbeotBeon = 0
    private var malbeotGidarim = 0

    /// 2.49.0 말벗 — 명령이 아닌 질문은 협회 리눅스 서버의 인공지능(엑사원)에게. 지금 자리·가는 곳·앞의 대화 넉 마디를 함께 넘김
    private func malbeotMutgi(_ t: String, _ kkeut: @escaping (String?) -> Void) {
        var c = URLComponents(string: "https://lvd.ada.or.kr/jeom/malbeot.php")
        var q = [URLQueryItem(name: "mal", value: t)]
        if !AnnaeEngine.shared.majimakGil.isEmpty { q.append(URLQueryItem(name: "juso", value: AnnaeEngine.shared.majimakGil)) }
        if let y = YeojeongEngine.shared.jigeum { q.append(URLQueryItem(name: "mok", value: y.mokjeok.ireum)) }
        if let d = try? JSONSerialization.data(withJSONObject: Array(daehwaGirok.suffix(4))), let s = String(data: d, encoding: .utf8) {
            q.append(URLQueryItem(name: "ijeon", value: s))
        }
        c?.queryItems = q
        guard let u = c?.url else { kkeut(nil); return }
        // 2.60.0 (261009-I14, 이사장님 승인 2026-10-09 남산) 대답이 20초 넘게 걸리는 동안 아무 말이 없던 일 — 10초가 넘으면 그만두고 다시 여쭘
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 「알아보는 중입니다」는 말씀을 마치신 때부터 셈(deureum 에서).
        //   URLRequest 의 기다림은 「소식이 끊긴 동안」만 세므로, 부른 때부터 10초가 넘으면 꼭 그만두는 마감을 따로 둠
        var r = URLRequest(url: u)
        r.timeoutInterval = 10
        r.cachePolicy = .reloadIgnoringLocalCacheData
        malbeotBeon += 1
        let beon = malbeotBeon
        malbeotGidarim = beon
        let magam = DispatchWorkItem { [weak self] in
            guard let self = self, self.malbeotGidarim == beon else { return }
            self.malbeotGidarim = 0
            Girok.shared.namgi("malbeot_mot", ["magam": true])
            kkeut(nil)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 10, execute: magam)
        Task {
            let dr = try? await URLSession.shared.data(for: r)
            let o = dr.flatMap { (try? JSONSerialization.jsonObject(with: $0.0)) as? [String: Any] }
            await MainActor.run {
                magam.cancel()
                guard self.malbeotGidarim == beon else { return }   // 마감이 이미 대답했거나, 전화로 거둔 물음
                self.malbeotGidarim = 0
                if let o = o, (o["ok"] as? Bool) == true, let d = o["dap"] as? String, !d.isEmpty {
                    self.daehwaGirok.append([t, d])
                    if self.daehwaGirok.count > 6 { self.daehwaGirok.removeFirst() }
                    Girok.shared.namgi("malbeot", ["cho": Chatgi.su(o["cho"]) ?? 0])
                    kkeut(d)
                } else {
                    Girok.shared.namgi("malbeot_mot", [:])
                    kkeut(nil)
                }
            }
        }
    }

    private func motAradeureum(_ t: String) {
        Girok.shared.namgi("mal_motaradeureum", ["mal": String(t.prefix(60))])
    }

    /// 모르면 먼저 사과하고, 거듭 모르면 기록해 두었다고 알림
    private func sagwa() -> String {
        let now = Date()
        motBeon = now.timeIntervalSince(motTtae) < 120 ? motBeon + 1 : 1
        motTtae = now
        return motBeon < 2 ? "죄송합니다. 제가 잘 알아듣지 못했습니다. "
                           : "죄송합니다. 이 말씀은 아직 배우지 못했습니다. 기록해 두었으니 다음 업그레이드에 넣겠습니다. "
    }

    // MARK: 탈것 말

    private static let talgeotMal: Set<String> = ["걸어서", "걸어", "도보로", "도보", "차로", "차", "택시로", "택시", "콜택시로",
                                                  "지하철로", "지하철", "전철로", "전철", "버스로", "버스", "타고", "차타고", "대중교통으로"]

    static func talgeotTteokgi(_ q: String) -> String {
        q.split(separator: " ").map(String.init)
            .filter { !talgeotMal.contains(MalSajeon.ttuk($0)) }
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
    }

    private func talgeotChatgi(_ alts: [String]) -> Talgeot? {
        let z = MalSajeon.ttuk(alts.first ?? "")
        if z.contains("지하철") || z.contains("전철") { return .jihacheol }
        if z.contains("버스") && !z.contains("버스터미널") && !z.contains("고속버스") { return .beoseu }
        if z.contains("걸어") || z.contains("도보") || z.contains("걷자") { return .georeum }
        if z.contains("택시") || z.contains("차타고") || z.hasPrefix("차로") || z.contains("차로가") || z.contains("타고가") { return .cha }
        return nil
    }

    // MARK: 토씨

    /// 받침이 있는가(ㄹ 받침인가)
    private static func batchim(_ w: String) -> (Bool, Bool) {
        guard let u = w.unicodeScalars.last else { return (false, false) }
        let c = Int(u.value) - 0xAC00
        guard c >= 0 && c < 11172 else { return (false, false) }
        let j = c % 28
        return (j != 0, j == 8)
    }
    static func eul(_ w: String) -> String { batchim(w).0 ? "을" : "를" }
    /// 2.26.0 "약수역 5번 출구", "약수역 5번출구" → 5
    static func chulguBeon(_ t: String) -> Int? {
        guard let r = t.range(of: "([0-9]{1,2})\\s*번\\s*(출구|출입구)", options: .regularExpression) else { return nil }
        return Int(t[r].prefix { $0.isNumber })
    }
    static func eun(_ w: String) -> String { batchim(w).0 ? "은" : "는" }
    static func i(_ w: String) -> String { batchim(w).0 ? "이" : "가" }
    static func ro(_ w: String) -> String { let b = batchim(w); return b.0 && !b.1 ? "으로" : "로" }
    static func irago(_ w: String) -> String { batchim(w).0 ? "이라고" : "라고" }
}
