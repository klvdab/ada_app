// BYOD 방송 아이폰 — 듣는 분께 소리를 나눠 보내는 작은 서버 (1.1.5판, 빌드 261010-BI1, 방송클)
// 안드로이드판(Seobeo.kt)·노트북판과 길 이름을 똑같이 맞춰, 노트북판 듣는 화면(deut.html)이 그대로 돕니다.
//   /            듣는 화면(deut.html)
//   /stream      해설 소리 한 줄기(뮤 8비트, 1초에 16000) — 1.9.0 ?gal=2·3 이면 그 갈래
//   /daegi.mp3 /ment.mp3  대기 음악·안내말
//   그 밖의 길(/status /beonho /deutnun /jiyeon /yocheong /pd …)은 1.9.0부터 HyeonjangGil 이 노트북판과 같게 답합니다.
// 앱은 80번 문을 열 수 없어 8080번 문을 씁니다.
// 안드로이드판은 듣는 분마다 일꾼을 하나씩 붙였지만, 아이폰은 일꾼 수를 아끼려고 ★보내는 일꾼 하나가 모든 폰에
// 기다리지 않는 방식으로 나눠 보냅니다. 느린 폰은 4초 넘게 밀리면 오래된 소리를 버리고, 10초 넘게 하나도 받지 못하면
// 끊긴 폰으로 보고 빼고 셉니다(안드로이드 1.1.5와 같은 잣대).
// 1.9.0 (빌드 261024-BI11, 새방송클, 대조표 송출 21·22·23번) 노트북판 현장 기능 옮기기 — 요청 머리·몸통(POST)·들어온 주소를 다 읽어
//   HyeonjangGil 로 넘김, 여러 갈래 해설(갈래 1 = 이 아이폰 방송 소리, 갈래 2·3 = 관계자 폰이 POST /pcm?gal=n 으로 보낸 소리),
//   갈래마다·공유기(주소 앞 세 마디)마다 듣는 분 수, 피디 화면의 해설 시작·방송중지(cue)는 듣는 분께 보내는 것만 막음(녹음은 그대로)
import Foundation
import Darwin

final class Seobeo {
    static let shared = Seobeo()

    static let MAKHIM: TimeInterval = 10      // 이만큼 하나도 못 보내면 끊긴 폰
    static let SSAAM = 16000 * 4               // 폰마다 4초까지 쌓아 둠(바이트)

    private final class Deut {
        let fd: Int32
        let gal: Int                 // 1.9.0 듣는 갈래(1·2·3)
        let ip: String               // 1.9.0 들어온 주소(공유기별 인원)
        var nameum = Data()          // 아직 못 보낸 소리
        var jinjeon = Date()         // 마지막으로 조금이라도 보낸 때
        init(fd: Int32, gal: Int, ip: String) { self.fd = fd; self.gal = gal; self.ip = ip }
    }

    private let q = DispatchQueue(label: "kr.or.ada.byod.ppuri", qos: .userInteractive)   // 보내는 일꾼 하나
    private var deutneun: [Deut] = []        // q 안에서만 만짐
    private var tik: DispatchSourceTimer?
    private var majimakJogak: [Int: Date] = [:]   // q 안에서만 — 갈래마다 마지막으로 소리를 보낸 때
    private let mun = NSLock()
    private var lsock: Int32 = -1
    private var _dolgo = false
    private var _su = 0
    private var _galSu: [Int: Int] = [:]          // 1.9.0 갈래마다 듣는 분 수
    private var _mang: [String: Int] = [:]        // 1.9.0 주소 앞 세 마디마다 듣는 분 수
    private var _galTtae: [Int: Date] = [:]       // 1.9.0 갈래마다 마지막으로 소리가 온 때
    private var _appTtae = Date.distantPast       // 1.9.0 이 아이폰 방송 소리(갈래 1)가 마지막으로 온 때
    var dolgo: Bool { mun.lock(); defer { mun.unlock() }; return _dolgo }
    private var jigeumMun: Int32 { mun.lock(); defer { mun.unlock() }; return lsock }
    /// 지금 듣는 분 수(어느 줄기에서나 읽음)
    var su: Int { mun.lock(); defer { mun.unlock() }; return _su }
    var galSu: [Int: Int] { mun.lock(); defer { mun.unlock() }; return _galSu }
    var mang: [String: Int] { mun.lock(); defer { mun.unlock() }; return _mang }
    /// 1.9.0 그 갈래에 4초 안에 소리가 왔는가
    func galKyeojim(_ g: Int) -> Bool {
        mun.lock(); defer { mun.unlock() }
        return Date().timeIntervalSince(_galTtae[g] ?? .distantPast) < 4
    }

    /// q 안에서 — 듣는 분 수가 바뀌었을 때
    private func suAllim() {
        let n = deutneun.count
        var gs: [Int: Int] = [:]
        var ms: [String: Int] = [:]
        for d in deutneun {
            gs[d.gal, default: 0] += 1
            if !d.ip.hasPrefix("127."), let i = d.ip.range(of: ".", options: .backwards) { ms[String(d.ip[d.ip.startIndex..<i.lowerBound]), default: 0] += 1 }
        }
        mun.lock(); _su = n; _galSu = gs; _mang = ms; mun.unlock()
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
        mun.lock(); lsock = s; _dolgo = true; _galTtae = [:]; _appTtae = .distantPast; mun.unlock()
        HyeonjangGil.shared.sijak()   // 1.9.0 현장 기능 — 이어 받기와 1분마다 저장
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
        if s >= 0 { HyeonjangGil.shared.kkeut() }   // 1.9.0 마지막 저장(행사 마감 결과보고에 들어감)
        q.async { [weak self] in
            guard let self = self else { return }
            for d in self.deutneun { close(d.fd) }
            self.deutneun.removeAll()
            self.suAllim()
        }
    }

    // MARK: 소리 나눠 보내기

    /// 해설 소리 한 조각(0.1초)을 듣는 분 모두에게 — 이 아이폰 방송 소리는 갈래 1
    func ppurida(_ b: Data) {
        mun.lock(); _appTtae = Date(); mun.unlock()
        neoki(b, gal: 1)
    }

    /// 1.9.0 관계자 폰이 보낸 해설 소리(POST /pcm) — 갈래 1은 이 아이폰 방송 소리가 4초 안에 왔으면 받지 않음(두 소리가 섞이지 않게)
    func bakkatSori(_ b: Data, gal: Int) -> Bool {
        mun.lock()
        let bappeum = gal == 1 && Date().timeIntervalSince(_appTtae) < 4
        mun.unlock()
        if bappeum { return false }
        neoki(b, gal: gal)
        return true
    }

    private func neoki(_ b: Data, gal: Int) {
        mun.lock(); _galTtae[gal] = Date(); mun.unlock()
        if !HyeonjangGil.shared.cueKyeojim { return }   // 1.9.0 피디 화면에서 방송중지 — 듣는 분께만 보내지 않음(녹음은 BangIl 쪽에서 그대로)
        q.async { [weak self] in
            guard let self = self else { return }
            self.majimakJogak[gal] = Date()
            for d in self.deutneun where d.gal == gal {
                d.nameum.append(b)
                if d.nameum.count > Seobeo.SSAAM { d.nameum = d.nameum.subdata(in: (d.nameum.endIndex - Seobeo.SSAAM)..<d.nameum.endIndex) }   // 밀리면 오래된 소리를 버림(새 그릇에 옮겨 담아 메모리가 쌓이지 않게)
            }
            self.moduBonaegi()
        }
    }

    private func salpigi() {
        let now = Date()
        for g in 1...3 where now.timeIntervalSince(majimakJogak[g] ?? .distantPast) > 5 {
            majimakJogak[g] = now
            let gy = MuLaw.goyo(160)
            for d in deutneun where d.gal == g && d.nameum.isEmpty { d.nameum.append(gy) }   // 끊긴 폰을 가려내려고 조금이라도 보냄
        }
        moduBonaegi()
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
            var sa = sockaddr_in()
            var sl = socklen_t(MemoryLayout<sockaddr_in>.size)
            let c = withUnsafeMutablePointer(to: &sa) { p in
                p.withMemoryRebound(to: sockaddr.self, capacity: 1) { accept(s, $0, &sl) }
            }
            if c < 0 {
                if !dolgo || jigeumMun != s { return }
                if errno == EINTR || errno == ECONNABORTED { continue }
                usleep(50_000)
                continue
            }
            // 1.9.0 들어온 쪽 주소(이 아이폰 자신인지, 어느 공유기 쪽인지)
            var addr = sa.sin_addr
            var jb = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            let ip = inet_ntop(AF_INET, &addr, &jb, socklen_t(INET_ADDRSTRLEN)) != nil ? String(cString: jb) : ""
            var one: Int32 = 1
            setsockopt(c, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
            setsockopt(c, IPPROTO_TCP, TCP_NODELAY, &one, socklen_t(MemoryLayout<Int32>.size))
            let t = Thread { [weak self] in self?.mutgi(c, ip) }
            t.name = "byod-son"
            t.stackSize = 256 * 1024
            t.start()
        }
    }

    /// 1.9.0 요청 하나를 다 읽음 — 머리(64킬로바이트까지)와 POST 몸통(4메가바이트 아래)
    private func ilkgi(_ c: Int32, _ ip: String) -> HjYocheong? {
        var buf = Data()
        var tmp = [UInt8](repeating: 0, count: 16384)
        var hEnd = -1
        var gaLen = 4
        while hEnd < 0 {
            let r = tmp.withUnsafeMutableBytes { p in recv(c, p.baseAddress!, p.count, 0) }
            if r <= 0 { if r < 0 && errno == EINTR { continue }; return nil }
            buf.append(contentsOf: tmp[0..<r])
            if let rg = buf.range(of: Data([13, 10, 13, 10])) { hEnd = rg.lowerBound - buf.startIndex; gaLen = 4 }
            else if let rg = buf.range(of: Data([10, 10])) { hEnd = rg.lowerBound - buf.startIndex; gaLen = 2 }
            else if buf.count >= 65536 { return nil }
        }
        let head = String(decoding: buf.prefix(hEnd), as: UTF8.self)
        let lines = head.components(separatedBy: "\n").map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "\r")) }
        guard let first = lines.first else { return nil }
        let parts = first.split(separator: " ")
        guard parts.count >= 2 else { return nil }
        var host = "", ua = ""
        var clen = 0
        for l in lines.dropFirst() {
            guard let ci = l.firstIndex(of: ":") else { continue }
            let k = l[l.startIndex..<ci].trimmingCharacters(in: .whitespaces).lowercased()
            let v = l[l.index(after: ci)...].trimmingCharacters(in: .whitespaces)
            if k == "host" { host = v } else if k == "user-agent" { ua = v } else if k == "content-length" { clen = Int(v) ?? 0 }
        }
        let method = String(parts[0]).uppercased()
        var mom = Data()
        if method == "POST" && clen > 0 && clen < 4_000_000 {
            let sijak = buf.startIndex + hEnd + gaLen
            if buf.endIndex > sijak { mom = buf.subdata(in: sijak..<min(buf.endIndex, sijak + clen)) }
            while mom.count < clen {
                let want = min(tmp.count, clen - mom.count)
                let r = tmp.withUnsafeMutableBytes { p in recv(c, p.baseAddress!, want, 0) }
                if r <= 0 { if r < 0 && errno == EINTR { continue }; break }
                mom.append(contentsOf: tmp[0..<r])
            }
        }
        let juso = String(parts[1])
        let gil: String
        let mul: String
        if let qi = juso.firstIndex(of: "?") {
            gil = String(juso[juso.startIndex..<qi])
            mul = String(juso[juso.index(after: qi)...])
        } else {
            gil = juso
            mul = ""
        }
        return HjYocheong(method: method, gil: gil, mul: mul, host: host, ua: ua, mom: mom, ip: ip, loop: ip.hasPrefix("127."))
    }

    private func mutgi(_ c: Int32, _ ip: String) {
        var keep = false
        defer { if !keep { close(c) } }
        var tv = timeval(tv_sec: 10, tv_usec: 0)
        setsockopt(c, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var tv2 = timeval(tv_sec: 20, tv_usec: 0)
        setsockopt(c, SOL_SOCKET, SO_SNDTIMEO, &tv2, socklen_t(MemoryLayout<timeval>.size))
        guard let rq = ilkgi(c, ip) else { return }
        if rq.method == "OPTIONS" { bonae(c, 204, "text/plain", Data()); return }   // 1.9.0 노트북판처럼 미리 묻기에 답함
        let gil = rq.gil
        if inteonetCheok(c, gil) { return }
        switch gil {
        case "/", "/index.html", "/deut.html", "/deut":
            if let d = Jaryo.pail("deut.html") { bonae(c, 200, "text/html; charset=utf-8", d) }
            else { geulja(c, 200, "text/html; charset=utf-8", Jaryo.GANDAN) }
        case "/stream":
            let g = Int(HyeonjangGil.gap(rq.mul, "gal")) ?? 1   // 1.9.0 여러 갈래 해설
            keep = deutgi(c, gal: (2...3).contains(g) ? g : 1, ip: ip)
        case "/daegi.mp3", "/ment.mp3":
            if let d = Jaryo.pail(String(gil.dropFirst())) { bonae(c, 200, "audio/mpeg", d) }
            else { geulja(c, 404, "text/plain; charset=utf-8", "없는 소리입니다.") }
        default:
            let d = HyeonjangGil.shared.cheori(rq)   // 1.9.0 노트북판 현장 기능의 길
            bonae(c, d.code, d.type, d.mom)
        }
    }

    /// 듣는 분 한 사람 붙이기 — 머리와 소리 없음 0.1초를 보낸 뒤, 보내는 일꾼에게 넘김
    private func deutgi(_ c: Int32, gal: Int, ip: String) -> Bool {
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
            self.deutneun.append(Deut(fd: c, gal: gal, ip: ip))
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
        switch code {
        case 200: mal = "OK"
        case 204: mal = "No Content"
        case 400: mal = "Bad Request"
        case 403: mal = "Forbidden"
        case 404: mal = "Not Found"
        default: mal = "OK"
        }
        let h = "HTTP/1.1 \(code) \(mal)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nConnection: close\r\n\r\n"
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
