// LVD 방송 아이폰(듣는 앱) — 행사장 와이파이 저절로 붙기와 현장 찾기 (2.0.0판, 빌드 261015-LI1, 방송클, 이사장님 승인 「1」)
// 1. 처음 한 번 허락을 받아 행사장 와이파이(현장영상해설방송, 비밀번호 없음)를 아이폰에 등록합니다(joinOnce = false).
//    그 뒤로는 행사장 범위 안에 들기만 하면 아이폰이 저절로 붙습니다.
// 2. 붙어 있으면 현장 방송(192.168.8.2:7880)이 답합니다. 앱을 열면 곧바로 들려 드립니다.
//    아이폰은 앱이 닫혀 있을 때 「와이파이에 붙었다」를 앱에 알려 주지 않으므로, 앱을 여는 것까지는 이용자가 합니다.
// 2.4.0 (261023-LI5, 새방클, 이사장님 지시 2026-10-23 「서둘러야 해」) 아이피타임 방식 BYOD 방송도 찾아 들음 —
//   아이피타임 방식에서는 보내는 폰(LVD 송출)이 방송 서버라 자리가 공유기가 나눠 주는 대로 매번 달라짐.
//   지금까지는 플린트 2 자리(192.168.8.2:7880)만 찾아 아이피타임 방식 방송을 찾지 못했음(10월 22일 시험, 듣는 분 0명).
//   이제 ①지금 자리 ②기억한 자리 ③플린트 2 자리 ④지금 붙은 와이파이 안에서 8080번 문을 연 LVD 송출 폰을 차례로 찾음(④는 30초에 한 번).
import Foundation
import NetworkExtension
import Network
import Darwin

enum Hyeonjang {
    static let SSID = "현장영상해설방송"
    static let IP = "192.168.8.2"
    static let WEB = 7880
    static let SONGCHUL_MUN = 8080             // 2.4.0 아이피타임 방식 — LVD 송출 폰이 여는 문
    /// 2.4.0 지금 듣는 방송 자리 — 「http://주소:문」
    static var jari = "http://\(IP):\(WEB)"
    private static var chatgiTtae = Date.distantPast
    static var stream: URL? { URL(string: jari + "/stream") }

    static var deungrokDoem: Bool {
        get { UserDefaults.standard.bool(forKey: "wifi_deungrok") }
        set { UserDefaults.standard.set(newValue, forKey: "wifi_deungrok") }
    }

    /// 행사장 와이파이 등록 — 아이폰이 「연결하시겠습니까」를 한 번 묻습니다. 결과 말을 돌려줌
    static func deungrok(_ kkeut: @escaping (String) -> Void) {
        let c = NEHotspotConfiguration(ssid: SSID)
        c.joinOnce = false
        NEHotspotConfigurationManager.shared.apply(c) { e in
            DispatchQueue.main.async {
                if let e = e as NSError?, e.domain == NEHotspotConfigurationErrorDomain,
                   e.code != NEHotspotConfigurationError.alreadyAssociated.rawValue {
                    if e.code == NEHotspotConfigurationError.userDenied.rawValue {
                        kkeut("등록하지 않았습니다. 더 보기의 행사장 와이파이 저절로 붙기 다시 하기로 언제든 다시 하실 수 있습니다.")
                    } else {
                        // 행사장 밖이라 지금 붙지 못한 것은 괜찮음 — 아이폰이 기억해 두었다가 범위 안에서 붙음
                        deungrokDoem = true
                        kkeut("행사장 와이파이를 등록했습니다. 행사장에 들어서면 아이폰이 저절로 붙습니다. 그때 LVD 방송을 열기만 하시면 들립니다.")
                    }
                } else {
                    deungrokDoem = true
                    kkeut("행사장 와이파이를 등록했습니다. 행사장에 들어서면 아이폰이 저절로 붙습니다. 그때 LVD 방송을 열기만 하시면 들립니다.")
                }
            }
        }
    }

    private static func mutgiJari(_ base: String, _ gil: String, ttae: TimeInterval, _ kkeut: @escaping (Data?) -> Void) {
        guard let u = URL(string: base + gil) else { kkeut(nil); return }
        var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: ttae)
        r.allowsCellularAccess = false   // 행사장 와이파이로만
        URLSession.shared.dataTask(with: r) { d, res, _ in
            kkeut((res as? HTTPURLResponse)?.statusCode == 200 ? d : nil)
        }.resume()
    }

    /// 현장 방송에 묻기(짧게) — 지금 찾아 둔 자리로
    static func mutgi(_ gil: String, ttae: TimeInterval = 3, _ kkeut: @escaping (Data?) -> Void) {
        mutgiJari(jari, gil, ttae: ttae, kkeut)
    }

    private static func hyeonjangInga(_ d: Data?) -> String? {
        guard let d = d, let j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any],
              j["hyeonjang"] as? Bool == true else { return nil }
        return j["jemok"] as? String ?? ""
    }

    /// 2.4.0 와이파이(en0)의 사설 IPv4 주소
    private static func wifiJuso() -> String? {
        var ifa: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifa) == 0, let first = ifa else { return nil }
        defer { freeifaddrs(ifa) }
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let i = p {
            defer { p = i.pointee.ifa_next }
            let flags = Int32(i.pointee.ifa_flags)
            guard (flags & IFF_UP) != 0, (flags & IFF_LOOPBACK) == 0, String(cString: i.pointee.ifa_name) == "en0",
                  let sa = i.pointee.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) != 0 { continue }
            let ip = String(cString: host)
            let q = ip.split(separator: ".").compactMap { Int($0) }
            guard q.count == 4 else { continue }
            if q[0] == 10 || (q[0] == 192 && q[1] == 168) || (q[0] == 172 && (16...31).contains(q[1])) { return ip }
        }
        return nil
    }

    private static func chajeum(_ b: String) {
        jari = b
        UserDefaults.standard.set(b, forKey: "hyeonjang_jari")
    }

    /// 행사장인가 — 현장 방송이 답하면 행사 이름(없으면 빈 글), 아니면 nil
    /// 2.4.0 지금 자리 → 기억한 자리 → 플린트 2 자리 → 와이파이 안 8080번 문(30초에 한 번) 차례로 찾음
    static func salpigi(_ kkeut: @escaping (String?) -> Void) {
        var hubo: [String] = [jari]
        if let g = UserDefaults.standard.string(forKey: "hyeonjang_jari"), !g.isEmpty, !hubo.contains(g) { hubo.append(g) }
        let flint = "http://\(IP):\(WEB)"
        if !hubo.contains(flint) { hubo.append(flint) }
        func charye(_ i: Int) {
            if i >= hubo.count { wifiChatgi(kkeut); return }
            mutgiJari(hubo[i], "/status", ttae: 2) { d in
                if let jm = hyeonjangInga(d) { chajeum(hubo[i]); DispatchQueue.main.async { kkeut(jm) } }
                else { charye(i + 1) }
            }
        }
        charye(0)
    }

    private static func wifiChatgi(_ kkeut: @escaping (String?) -> Void) {
        guard Date().timeIntervalSince(chatgiTtae) > 30, let nae = wifiJuso() else { DispatchQueue.main.async { kkeut(nil) }; return }
        chatgiTtae = Date()
        let ap = String(nae[..<(nae.lastIndex(of: ".").map { nae.index(after: $0) } ?? nae.endIndex)])
        let g = DispatchGroup()
        let q = DispatchQueue(label: "lvd.hyeonjang.chatgi")
        var chajeun: (String, String)? = nil
        for i in 1...254 {
            let ip = ap + String(i)
            if ip == nae { continue }
            let b = "http://\(ip):\(SONGCHUL_MUN)"
            g.enter()
            mutgiJari(b, "/status", ttae: 1.5) { d in
                if let jm = hyeonjangInga(d) { q.sync { if chajeun == nil { chajeun = (b, jm) } } }
                g.leave()
            }
        }
        g.notify(queue: .main) {
            guard let r = chajeun else { kkeut(nil); return }
            chajeum(r.0)
            kkeut(r.1)
        }
    }

    /// 도움 요청 — 현장영상해설사 방송 앱에 바로 뜸
    static func yocheong(_ jongryu: String, _ kkeut: @escaping (Bool) -> Void) {
        mutgi("/yocheong?k=\(jongryu)&n=") { d in DispatchQueue.main.async { kkeut(d != nil) } }
    }
}
