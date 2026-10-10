// BYOD 방송 아이폰 — 듣기 주소 찾기 (1.1.5판, 빌드 261010-BI1, 방송클)
// 아이폰이 공유기에 붙은 주소를 찾아 듣기 주소(http://주소:8080/)를 만듭니다.
// 유선 랜(어댑터)으로 붙었으면 그것을, 아니면 와이파이를 씁니다. 안드로이드판 jusoChatgi 와 같은 차례입니다.
import Foundation
import Darwin

enum Juso {
    /// (이름, 주소) 목록 — 켜진 IPv4 주소만
    static func moduJuso() -> [(String, String)] {
        var l: [(String, String)] = []
        var ifa: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifa) == 0, let first = ifa else { return l }
        defer { freeifaddrs(ifa) }
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let i = p {
            defer { p = i.pointee.ifa_next }
            let flags = Int32(i.pointee.ifa_flags)
            guard (flags & IFF_UP) != 0, (flags & IFF_LOOPBACK) == 0,
                  let sa = i.pointee.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) != 0 { continue }
            let ip = String(cString: host)
            let nm = String(cString: i.pointee.ifa_name)
            l.append((ireum(nm), ip))
        }
        return l
    }

    private static func ireum(_ nm: String) -> String {
        if nm == "en0" { return "와이파이" }
        if nm.hasPrefix("en") { return "유선 랜" }
        if nm.hasPrefix("bridge") { return "개인용 핫스팟" }
        if nm.hasPrefix("pdp_ip") { return "휴대폰 데이터" }
        return nm
    }

    /// 듣기 주소 — 유선 랜이 있으면 그것, 없으면 와이파이, 그다음 핫스팟. 없으면 빈 글
    static func deutgiJuso() -> String {
        let l = moduJuso()
        let ip = l.first(where: { $0.0 == "유선 랜" })?.1 ?? l.first(where: { $0.0 == "와이파이" })?.1 ?? l.first(where: { $0.0 == "개인용 핫스팟" })?.1
        guard let i = ip else { return "" }
        return "http://\(i):\(Bang.PORT)/"
    }
}
