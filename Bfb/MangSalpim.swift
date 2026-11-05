// 2.11.0 / 1.16.0 (2026-11-05, 새방클, 이사장님 승인) 플을 못 찾았을 때 어디서 막혔는지 하나씩 살핌 —
// 아이폰 와이파이, 방송 와이파이인지, 로컬 네트워크 허용, 방송 서버 응답. 두 앱이 같은 파일을 씀(글자만 APP_IREUM 이 다름)
import Foundation
import Network
import Darwin

enum MangSalpim {
    static let APP_IREUM = "LVD 방송"
    static let SSID = "현장영상해설방송"
    private final class Kkeut {
        var doem = false
    }

    /// en0(와이파이)의 IPv4 주소
    static func wifiJuso() -> String? {
        var ifa: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifa) == 0, let first = ifa else { return nil }
        defer { freeifaddrs(ifa) }
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let i = p {
            defer { p = i.pointee.ifa_next }
            let flags = Int32(i.pointee.ifa_flags)
            guard (flags & IFF_UP) != 0, String(cString: i.pointee.ifa_name) == "en0",
                  let sa = i.pointee.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) != 0 { continue }
            return String(cString: host)
        }
        return nil
    }

    private static func peulJuso(_ h: String) -> Bool {
        ["192.168.8.", "192.168.9.", "192.168.10.", "192.168.11."].contains { h.hasPrefix($0) }
    }

    /// 어디서 막혔는지 — (기록용 낱말, 들려 드릴 말). 끝나면 큐 밖 메인에서 알림
    static func salpigi(_ kkeut: @escaping (String, String) -> Void) {
        guard let j = wifiJuso() else {
            DispatchQueue.main.async { kkeut("wifi_eopseum", "아이폰이 어느 와이파이에도 붙어 있지 않습니다. 와이파이 \(SSID) 에 붙여 주십시오.") }
            return
        }
        guard peulJuso(j) else {
            DispatchQueue.main.async { kkeut("dareun_wifi", "아이폰이 방송이 아닌 다른 와이파이에 붙어 있습니다. 와이파이 \(SSID) 에 붙여 주십시오.") }
            return
        }
        let q = DispatchQueue(label: "mang.salpim")
        let c = NWConnection(host: "192.168.8.2", port: 7880, using: .tcp)
        let k = Kkeut()
        func matchim(_ a: String, _ b: String) {
            if k.doem { return }
            k.doem = true
            c.cancel()
            DispatchQueue.main.async { kkeut(a, b) }
        }
        func heoyong() {
            matchim("local_geobu", "로컬 네트워크 허용이 꺼져 있습니다. 아이폰 설정에서 \(APP_IREUM) 을 찾아 로컬 네트워크를 켜 주십시오. 켜시면 바로 찾습니다.")
        }
        let eopseum = "방송 와이파이에는 붙었는데 방송 서버가 답하지 않습니다. 공유기가 켜진 지 1분이 안 되었으면 잠시 기다려 주십시오."
        c.stateUpdateHandler = { st in
            switch st {
            case .ready:
                matchim("seobeo_bappeum", "방송 서버에 닿았지만 답이 늦습니다. 잠시 뒤 다시 찾습니다.")
            case .waiting(let e), .failed(let e):
                if c.currentPath?.unsatisfiedReason == .localNetworkDenied || String(describing: e).contains("-65570") { heoyong() }
                else if case .failed = st { matchim("seobeo_mudap", eopseum) }
            default:
                break
            }
        }
        c.start(queue: q)
        q.asyncAfter(deadline: .now() + 4) { matchim("seobeo_mudap", eopseum) }
    }
}
