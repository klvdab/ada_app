// BYOD 방송 아이폰 — 듣는 분께 소리를 나눠 보내는 작은 서버 (1.1.5판, 빌드 261010-BI1, 방송클)
// 안드로이드판(Seobeo.kt)·노트북판과 길 이름을 똑같이 맞춰, 노트북판 듣는 화면(deut.html)이 그대로 돕니다.
//   /            듣는 화면(deut.html)
//   /status      방송 상태(keu·n·pan …)
//   /stream      해설 소리 한 줄기(뮤 8비트, 1초에 16000)
//   /daegi.mp3 /ment.mp3  대기 음악·안내말
//   /beonho /deutnun /jiyeon /yocheong  듣는 분 쪽 알림(도움 요청은 첫 화면에 한 번 알림)
// 앱은 80번 문을 열 수 없어 8080번 문을 씁니다.
// 안드로이드판은 듣는 분마다 일꾼을 하나씩 붙였지만, 아이폰은 일꾼 수를 아끼려고 ★보내는 일꾼 하나가 모든 폰에
// 기다리지 않는 방식으로 나눠 보냅니다. 느린 폰은 4초 넘게 밀리면 오래된 소리를 버리고, 10초 넘게 하나도 받지 못하면
// 끊긴 폰으로 보고 빼고 셉니다(안드로이드 1.1.5와 같은 잣대).
import Foundation
import Darwin

final class Seobeo {
    static let shared = Seobeo()

    static let MAKHIM: TimeInterval = 10      // 이만큼 하나도 못 보내면 끊긴 폰
    static let SSAAM = 16000 * 4               // 폰마다 4초까지 쌓아 둠(바이트)

    private final class Deut {
        let fd: Int32
        var nameum = Data()          // 아직 못 보낸 소리
        var jinjeon = Date()         // 마지막으로 조금이라도 보낸 때
        init(fd: Int32) { self.fd = fd }
    }

    private let q = DispatchQueue(label: "kr.or.ada.byod.ppuri", qos: .userInteractive)   // 보내는 일꾼 하나
    private var deutneun: [Deut] = []        // q 안에서만 만짐
    private var tik: DispatchSourceTimer?
    private var majimakJogak = Date()
    private let mun = NSLock()
    private var lsock: Int32 = -1
    private var _dolgo = false
    private var _su = 0
    var dolgo: Bool { mun.lock(); defer { mun.unlock() }; return _dolgo }
    private var jigeumMun: Int32 { mun.lock(); defer { mun.unlock() }; return lsock }
    /// 지금 듣는 분 수(어느 줄기에서나 읽음)
    var su: Int { mun.lock(); defer { mun.unlock() }; return _su }

    /// q 안에서 — 듣는 분 수가 바뀌었을 때
    private func suAllim() {
        let n = deutneun.count
        mun.lock(); _su = n; mun.unlock()
        Bang.shared.suGochigi(n)
    }

    // MARK: 켜고 끄기

    /// 8080번 문을 엶. 열지 못하면 까닭을 글로 던짐
    func sijak() throws {
        if dolgo { return }
        signal(SIGPIPE, SIG_IGN)
        let s = socket(AF_INET, SOCK_STREAM, 0)
        if s < 0 { throw NSError(domain: "byod", code: Int(errno), userInfo: [NSLocalizedDescriptionKey: "방송 문을 만들지 못했습니다."]) }
        var one: Int32 = 1
        setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, socklen_t(MemoryLayout<Int32>.size))
        var a = sockaddr_in()
        a.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        a.sin_family = sa_family_t(AF_INET)
        a.sin_port = Bang.PORT.bigEndian
        a.sin_addr = in_addr(s_addr: INADDR_ANY)
        let r = withUnsafePointer(to: &a) { p in
            p.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        if r != 0 {
            close(s)
            throw NSError(domain: "byod", code: Int(errno), userInfo: [NSLocalizedDescriptionKey: "방송 문(8080)을 열지 못했습니다."])
        }
        if listen(s, 512) != 0 {
            close(s)
            throw NSError(domain: "byod", code: Int(errno), userInfo: [NSLocalizedDescriptionKey: "방송 문(8080)을 열지 못했습니다."])
        }
        mun.lock(); lsock = s; _dolgo = true; mun.unlock()
        majimakJogak = Date()
        let t = Thread { [weak self] in self?.batneunIl(s) }
        t.name = "byod-mun"
        t.stackSize = 256 * 1024
        t.start()
        // 0.5초마다 밀린 것을 보내고, 끊긴 폰을 가려내고, 해설이 쉬면 5초마다 소리 없음 한 조각
        let tm = DispatchSource.makeTimerSource(queue: q)
        tm.schedule(deadline: .now() + 0.5, repeating: 0.5)
        tm.setEventHandler { [weak self] in self?.salpigi() }
        tm.resume()
        tik = tm
    }

    func meomchugi() {
        mun.lock()
        let s = lsock
        lsock = -1
        _dolgo = false
        mun.unlock()
        if s >= 0 { shutdown(s, SHUT_RDWR); close(s) }
        tik?.cancel()
        tik = nil
        q.async { [weak self] in
            guard let self = self else { return }
            for d in self.deutneun { close(d.fd) }
            self.deutneun.removeAll()
            self.suAllim()
        }
    }

    // MARK: 소리 나눠 보내기

    /// 해설 소리 한 조각(0.1초)을 듣는 분 모두에게
    func ppurida(_ b: Data) {
        q.async { [weak self] in
            guard let self = self else { return }
            self.majimakJogak = Date()
            for d in self.deutneun {
                d.nameum.append(b)
                if d.nameum.count > Seobeo.SSAAM { d.nameum = d.nameum.subdata(in: (d.nameum.endIndex - Seobeo.SSAAM)..<d.nameum.endIndex) }   // 밀리면 오래된 소리를 버림(새 그릇에 옮겨 담아 메모리가 쌓이지 않게)
            }
            self.moduBonaegi()
        }
    }

    private func salpigi() {
        if Date().timeIntervalSince(majimakJogak) > 5 {
            majimakJogak = Date()
            let g = MuLaw.goyo(160)
            for d in deutneun where d.nameum.isEmpty { d.nameum.append(g) }   // 끊긴 폰을 가려내려고 조금이라도 보냄
        }
        moduBonaegi()
        let now = Date()
        var ppaem = false
        deutneun.removeAll { d in
            if !d.nameum.isEmpty && now.timeIntervalSince(d.jinjeon) > Seobeo.MAKHIM {
                close(d.fd); ppaem = true; return true
            }
            return false
        }
        if ppaem { suAllim() }
    }

    /// q 안에서 — 기다리지 않고 보낼 수 있는 만큼 보냄. 잘못되면 빼냄
    private func moduBonaegi() {
        var ppaem = false
        deutneun.removeAll { d in
            if d.nameum.isEmpty { d.jinjeon = Date(); return false }
            let n = d.nameum.withUnsafeBytes { (p: UnsafeRawBufferPointer) -> Int in
                guard let base = p.baseAddress else { return 0 }
                return Darwin.send(d.fd, base, p.count, 0)
            }
            if n > 0 {
                d.nameum = d.nameum.subdata(in: (d.nameum.startIndex + n)..<d.nameum.endIndex)   // 보낸 만큼 덜어 냄(새 그릇에 — 조각만 옮기면 메모리가 쌓임)
                d.jinjeon = Date()
                return false
            }
            if n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) { return false }
            close(d.fd); ppaem = true
            return true
        }
        if ppaem { suAllim() }
    }

    // MARK: 받는 일

    private func batneunIl(_ s: Int32) {
        while dolgo {
            if jigeumMun != s { return }   // 껐다 곧바로 켠 때 — 옛 문 일꾼은 물러남
            let c = accept(s, nil, nil)
            if c < 0 {
                if !dolgo || jigeumMun != s { return }
                if errno == EINTR || errno == ECONNABORTED { continue }
                usleep(50_000)
                continue
            }
            var one: Int32 = 1
            setsockopt(c, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
            setsockopt(c, IPPROTO_TCP, TCP_NODELAY, &one, socklen_t(MemoryLayout<Int32>.size))
            let t = Thread { [weak self] in self?.mutgi(c) }
            t.name = "byod-son"
            t.stackSize = 256 * 1024
            t.start()
        }
    }

    /// 머리 줄을 읽어 길(물음 포함)을 돌려줌
    private func meori(_ c: Int32) -> String? {
        var buf = [UInt8](repeating: 0, count: 8192)
        var n = 0
        while n < buf.count {
            let r = buf.withUnsafeMutableBytes { p in recv(c, p.baseAddress! + n, p.count - n, 0) }
            if r <= 0 { if r < 0 && errno == EINTR { continue }; return nil }
            n += r
            let s = String(decoding: buf[0..<n], as: UTF8.self)
            if s.contains("\r\n\r\n") || s.contains("\n\n") {
                guard let first = s.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false).first else { return nil }
                let parts = first.split(separator: " ")
                return parts.count >= 2 ? String(parts[1]) : nil
            }
        }
        return nil
    }

    private func gap(_ q: String, _ k: String) -> String {
        for kv in q.split(separator: "&") {
            guard let i = kv.firstIndex(of: "=") else { continue }
            if kv[kv.startIndex..<i] == Substring(k) {
                let v = String(kv[kv.index(after: i)...]).replacingOccurrences(of: "+", with: " ")
                return v.removingPercentEncoding ?? ""
            }
        }
        return ""
    }

    private func mutgi(_ c: Int32) {
        var keep = false
        defer { if !keep { close(c) } }
        var tv = timeval(tv_sec: 10, tv_usec: 0)
        setsockopt(c, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var tv2 = timeval(tv_sec: 20, tv_usec: 0)
        setsockopt(c, SOL_SOCKET, SO_SNDTIMEO, &tv2, socklen_t(MemoryLayout<timeval>.size))
        guard let juso = meori(c) else { return }
        let gil: String
        let mul: String
        if let qi = juso.firstIndex(of: "?") {
            gil = String(juso[juso.startIndex..<qi])
            mul = String(juso[juso.index(after: qi)...])
        } else {
            gil = juso
            mul = ""
        }
        if inteonetCheok(c, gil) { return }
        switch gil {
        case "/", "/index.html", "/deut.html", "/deut":
            if let d = Jaryo.pail("deut.html") { bonae(c, 200, "text/html; charset=utf-8", d) }
            else { geulja(c, 200, "text/html; charset=utf-8", Jaryo.GANDAN) }
        case "/status":
            geulja(c, 200, "application/json", sangtaeJson())
        case "/stream":
            keep = deutgi(c)
        case "/daegi.mp3", "/ment.mp3":
            if let d = Jaryo.pail(String(gil.dropFirst())) { bonae(c, 200, "audio/mpeg", d) }
            else { geulja(c, 404, "text/plain; charset=utf-8", "없는 소리입니다.") }
        case "/yocheong":
            let ok = Bang.shared.yocheongBatgi(beonho: gap(mul, "n"), jongryu: gap(mul, "k"))
            geulja(c, 200, "application/json", "{\"ok\":\(ok)}")
        case "/beonho", "/deutnun", "/jiyeon":
            geulja(c, 200, "application/json", "{\"ok\":true}")
        case "/bang", "/pd":
            geulja(c, 200, "text/html; charset=utf-8",
                   "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>BYOD 방송</title></head>" +
                   "<body style=\"font-family:sans-serif;font-size:20px;padding:16px\"><p>방송은 아이폰의 BYOD 방송 앱에서 다룹니다.</p></body></html>")
        default:
            geulja(c, 404, "text/plain; charset=utf-8", "없는 주소입니다.")
        }
    }

    private func sangtaeJson() -> String {
        let b = Bang.shared
        let n = su
        return "{\"hyeonjang\":true,\"byod\":true,\"keu\":\(b.keu),\"mic\":\(dolgo),\"n\":\(n),\"pan\":\"BYOD 아이폰 \(Bang.PAN)\",\"bild\":\"\(Bang.BILD)\"," +
            "\"nas\":\"\",\"dang\":\"\",\"dseq\":0,\"dsik\":\"\",\"dmok\":[],\"gin\":\"\",\"gseq\":0,\"saepan\":\"\"}"
    }

    /// 듣는 분 한 사람 붙이기 — 머리와 소리 없음 0.1초를 보낸 뒤, 보내는 일꾼에게 넘김
    private func deutgi(_ c: Int32) -> Bool {
        var big: Int32 = 65536
        setsockopt(c, SOL_SOCKET, SO_SNDBUF, &big, socklen_t(MemoryLayout<Int32>.size))
        let h = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        guard modu(c, Data(h.utf8)), modu(c, MuLaw.goyo(Sori.JOGAK)) else { return false }
        let fl = fcntl(c, F_GETFL, 0)
        _ = fcntl(c, F_SETFL, fl | O_NONBLOCK)
        q.async { [weak self] in
            guard let self = self else { close(c); return }
            guard self.dolgo else { close(c); return }
            self.deutneun.append(Deut(fd: c))
            self.suAllim()
        }
        return true
    }

    // MARK: 보내기 도우미(기다리며 다 보냄)

    @discardableResult
    private func modu(_ c: Int32, _ d: Data) -> Bool {
        var off = 0
        let total = d.count
        return d.withUnsafeBytes { (p: UnsafeRawBufferPointer) -> Bool in
            guard let base = p.baseAddress else { return total == 0 }
            while off < total {
                let r = Darwin.send(c, base + off, total - off, 0)
                if r > 0 { off += r; continue }
                if r < 0 && errno == EINTR { continue }
                return false
            }
            return true
        }
    }

    private func bonae(_ c: Int32, _ code: Int, _ type: String, _ body: Data) {
        let mal: String
        switch code { case 200: mal = "OK"; case 204: mal = "No Content"; case 404: mal = "Not Found"; default: mal = "OK" }
        let h = "HTTP/1.1 \(code) \(mal)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        if modu(c, Data(h.utf8)), !body.isEmpty { modu(c, body) }
    }

    private func geulja(_ c: Int32, _ code: Int, _ type: String, _ t: String) { bonae(c, code, type, Data(t.utf8)) }

    /// 인터넷 있는 척 — 폰이 인터넷을 확인하는 주소에 "됨"이라고 답해 와이파이가 끊기지 않게(노트북판·안드로이드판과 같음)
    private func inteonetCheok(_ c: Int32, _ gil: String) -> Bool {
        let g = gil.lowercased()
        if g.contains("generate_204") || g.contains("gen_204") { bonae(c, 204, "text/plain", Data()); return true }
        if g.contains("hotspot-detect") || g.contains("library/test/success") {
            geulja(c, 200, "text/html", "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>"); return true
        }
        if g.contains("connecttest.txt") { geulja(c, 200, "text/plain", "Microsoft Connect Test"); return true }
        if g.contains("ncsi.txt") { geulja(c, 200, "text/plain", "Microsoft NCSI"); return true }
        return false
    }
}
