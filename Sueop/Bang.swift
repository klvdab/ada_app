// LVD 수업 아이폰 — 수업 방 살피기 (0.1.0판, 빌드 261020-SI1, 방클, 이사장님 승인 2026-10-20 「단독으로 가자」)
// 웹 「수업 보기」(sueop/index.html 0.2판)와 같은 서버(sueop.php a=bogi)를 1.5초마다 살펴, 강사님이 바꾼 것만 받아 보여 드립니다.
// 웹에 있는 것: 방 번호, 지금 장 그림과 글(보여 줄 것 1 그림만·2 글만·3 둘 다·4 프로그램 화면), 빨간 테두리 가리키기, 수업 마침, 끊김 알림.
// 앱에서 더한 것: 바뀐 일만 한 번 알림(새 장, 가리키는 곳의 말, 프로그램 화면, 마침, 끊김·다시 이어짐), 지난번 방 기억, 이용 기록.
import Foundation
import UIKit

struct Sangtae: Equatable {
    var ireum = ""
    var jang = 0
    var su = 0
    var mode = 3
    var geul = ""
    var kkeut = 0
    var ver = -1
    var hw = 0
    var gv = 0
    var gon = 0
    var gx = 0, gy = 0, gw = 0, gh = 0
    var gmal = ""

    init() {}
    init(_ j: [String: Any]) {
        func n(_ k: String) -> Int {
            if let v = j[k] as? Int { return v }
            if let v = j[k] as? Double { return Int(v) }
            if let v = j[k] as? String { return Int(v) ?? 0 }
            if let v = j[k] as? Bool { return v ? 1 : 0 }
            return 0
        }
        ireum = (j["ireum"] as? String) ?? ""
        jang = n("jang"); su = n("su"); mode = n("mode"); if mode < 1 || mode > 4 { mode = 3 }
        geul = (j["geul"] as? String) ?? ""
        kkeut = n("kkeut"); ver = n("ver"); hw = n("hw"); gv = n("gv")
        gon = n("gon"); gx = n("gx"); gy = n("gy"); gw = n("gw"); gh = n("gh")
        gmal = (j["gmal"] as? String) ?? ""
    }

    /// 지금 보여 줄 그림의 이름(바뀌었는지 견주는 데 씀)
    var geurimKi: String {
        if kkeut != 0 || jang <= 0 { return "" }
        if mode == 4 { return "hw\(hw)" }
        if mode == 2 { return "" }
        return "p\(jang)v\(gv)"
    }
    /// 크게 보기를 제자리로 돌릴 때 견주는 이름 — 프로그램 화면은 1초마다 새 그림이 와도 같은 것으로 봄(키운 채로 두기)
    var bogiKi: String { mode == 4 ? "hw" : geurimKi }
    var gariBoim: Bool { gon != 0 && mode != 2 && mode != 4 && gw > 0 && gh > 0 && kkeut == 0 && jang > 0 }
}

final class Bang: ObservableObject {
    static let shared = Bang()
    static let API = "https://lvd.ada.or.kr/sueop/sueop.php"
    static let BAN = "https://lvd.ada.or.kr/sueop/bang/"

    @Published private(set) var beonho = ""
    @Published private(set) var deureoom = false
    @Published private(set) var s = Sangtae()
    @Published private(set) var geurim: UIImage?
    @Published private(set) var geurimIreum = ""
    @Published private(set) var kkeunkim = false
    @Published var chatMal = ""          // 첫 화면 한 줄(번호를 못 찾음 등)

    private var sigye: Timer?
    private var bogiJung = false
    private var mot = 0
    private var geurimKi = ""
    private var geurimBatneunKi = ""
    private var cheotBogi = true
    private var jeonJang = -1, jeonMode = -1, jeonGmal = "", jeonGon = 0
    private var cheotMal = ""   // 들어온 첫 알림 — 첫 장 알림과 합쳐 한 번에(앞말이 끊기지 않게)

    // MARK: 지난번 방(같은 날 다시 들어오기)
    var jinanBeonho: String? {
        let d = UserDefaults.standard
        guard let b = d.string(forKey: "sueop_jinan"), b.count == 4 else { return nil }
        let t = d.double(forKey: "sueop_jinan_t")
        return Date().timeIntervalSince1970 - t < 14 * 3600 ? b : nil
    }

    // MARK: 들어가기·나가기
    func deureogagi(_ b0: String) {
        let b = b0.filter(\.isNumber)
        guard b.count == 4 else { chatMal = "방 번호 네 자리를 넣어 주십시오."; allim(chatMal); return }
        if sigye != nil && b == beonho { return }   // 이미 그 방에 들어가는 중
        nagagi(malHagi: false)
        beonho = b; s = Sangtae(); geurim = nil; geurimIreum = ""; geurimKi = ""; geurimBatneunKi = ""
        mot = 0; kkeunkim = false; cheotBogi = true; jeonJang = -1; jeonMode = -1; jeonGmal = ""; jeonGon = 0; cheotMal = ""
        chatMal = "방 번호 \(b) 수업에 들어가고 있습니다."
        UIApplication.shared.isIdleTimerDisabled = true
        Girok.shared.namgi("sueop_deureom", ["bang": b])
        bogi()
        let tm = Timer(timeInterval: 1.5, repeats: true) { [weak self] _ in self?.bogi() }
        RunLoop.main.add(tm, forMode: .common)   // 화면을 쓸어 넘기는 동안에도 살핌
        sigye = tm
    }

    func nagagi(malHagi: Bool = true) {
        sigye?.invalidate(); sigye = nil
        BuKan.shared.datgi()   // 0.2.0 함께하기 칸 닫음(화상에 들어가 있었으면 나감)
        UIApplication.shared.isIdleTimerDisabled = false
        if deureoom { Girok.shared.namgi("sueop_nagam", ["bang": beonho, "jang": s.jang]) }
        if deureoom && malHagi { chatMal = "수업에서 나왔습니다. 같은 방 번호로 언제든 다시 들어오실 수 있습니다." }
        deureoom = false
    }

    /// 앱으로 돌아올 때 곧바로 한 번 더 살핌
    func dorawatda() { if sigye != nil { bogi() } }

    // MARK: 살피기
    private func bogi() {
        guard !bogiJung, !beonho.isEmpty else { return }
        bogiJung = true
        let b = beonho
        guard let u = URL(string: Bang.API + "?a=bogi&bang=\(b)&_=\(Int(Date().timeIntervalSince1970 * 1000))") else { bogiJung = false; return }
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
        r.setValue("LVDSueop-ios/\(Pan.PAN)", forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: r) { [weak self] d, res, _ in
            var j: [String: Any]? = nil
            if let d = d, (res as? HTTPURLResponse)?.statusCode == 200 { j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] }
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.bogiJung = false
                guard self.beonho == b, self.sigye != nil else { return }
                guard let j = j else { self.motBatgi(); return }
                if self.kkeunkim { self.kkeunkim = false; allim("다시 이어졌습니다.") }
                self.mot = 0
                if (j["ok"] as? Bool) != true {
                    if self.cheotBogi || (j["why"] as? String) == "bang" {
                        self.nagagi(malHagi: false)
                        self.chatMal = "방 번호 \(b)인 수업을 찾지 못했습니다. 강사님이 알려 준 번호를 다시 넣어 주십시오."
                        allim(self.chatMal)
                        Girok.shared.namgi("sueop_mot_chatgi", ["bang": b])
                    }
                    return
                }
                let ns = Sangtae(j)
                if self.cheotBogi {
                    self.cheotBogi = false
                    self.deureoom = true
                    self.chatMal = ""
                    UserDefaults.standard.set(b, forKey: "sueop_jinan")
                    UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "sueop_jinan_t")
                    let ir = ns.ireum.isEmpty ? "" : " \(ns.ireum)."
                    self.cheotMal = "수업에 들어왔습니다.\(ir)"
                }
                if ns.ver != self.s.ver || ns != self.s { self.bakkwim(ns) }
                if !self.cheotMal.isEmpty { let m = self.cheotMal; self.cheotMal = ""; allim(m) }   // 장 알림이 없었으면 들어왔다는 말만
                else if !self.s.geurimKi.isEmpty && self.s.geurimKi != self.geurimKi { self.geurimBatgi(self.s.geurimKi, self.s) }   // 그림을 못 받았으면 다시
            }
        }.resume()
    }

    private func motBatgi() {
        mot += 1
        if mot == 5 && !kkeunkim {
            kkeunkim = true
            allim("수업 화면과 잠시 끊겼습니다. 저절로 다시 잇습니다.")
            Girok.shared.namgi("sueop_kkeunkim", ["bang": beonho])
        }
    }

    /// 바뀐 것만 알리고 그림을 새로 받음
    /// 수업 중 알림 — 들어온 첫 알림이 남아 있으면 앞에 붙여 한 번에
    private func sueopMal(_ m: String) {
        let a = cheotMal.isEmpty ? m : cheotMal + " " + m
        cheotMal = ""
        allim(a)
    }

    private func bakkwim(_ ns: Sangtae) {
        let jeon = s
        s = ns
        if ns.kkeut != 0 {
            if jeon.kkeut == 0 { sueopMal("수업을 마쳤습니다. 수고하셨습니다."); Girok.shared.namgi("sueop_kkeut", ["bang": beonho]) }
            geurim = nil; geurimKi = ""
            return
        }
        if ns.jang > 0 {
            if ns.mode == 4 && jeonMode != 4 {
                sueopMal("강사님이 프로그램 화면을 보여 주십니다.")
            } else if ns.mode != 4 && (ns.jang != jeonJang || jeonMode == 4) {
                var m = "\(ns.jang)장"
                if ns.su > 0 { m += ", 모두 \(ns.su)장" }
                if ns.mode == 2 { m += ". 글만 보여 주십니다" }
                sueopMal(m)
            } else if ns.gariBoim && !ns.gmal.isEmpty && (ns.gmal != jeonGmal || jeonGon == 0) {
                sueopMal("가리키는 곳: \(ns.gmal)")
            }
            jeonJang = ns.jang; jeonMode = ns.mode
        }
        jeonGmal = ns.gmal; jeonGon = ns.gon
        let ki = ns.geurimKi
        if ki.isEmpty { geurim = nil; geurimKi = ""; return }
        if ki != geurimKi { geurimBatgi(ki, ns) }
    }

    private func geurimBatgi(_ ki: String, _ ns: Sangtae) {
        guard geurimBatneunKi != ki else { return }
        geurimBatneunKi = ki
        let b = beonho
        let juso = ns.mode == 4 ? Bang.BAN + "\(b)/hw.jpg?h=\(ns.hw)" : Bang.BAN + "\(b)/p\(ns.jang).jpg?v=\(ns.gv)"
        guard let u = URL(string: juso) else { return }
        let r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        URLSession.shared.dataTask(with: r) { [weak self] d, res, _ in
            let im = ((res as? HTTPURLResponse)?.statusCode == 200 && d != nil) ? UIImage(data: d!) : nil
            DispatchQueue.main.async {
                guard let self = self, self.beonho == b else { return }
                if self.geurimBatneunKi == ki { self.geurimBatneunKi = "" }
                guard self.s.geurimKi == ki else { return }   // 받는 사이 또 바뀌었으면 버림
                if let im = im {
                    self.geurim = im; self.geurimKi = ki
                    self.geurimIreum = ns.mode == 4 ? "강사님이 보여 주는 프로그램 화면" : "\(ns.jang)장 자료"
                }
            }
        }.resume()
    }
}
