// AI점자도서관 앱 — 나스 창구 부르기 (판 0.3.0, 빌드 261006-1) — 목록 줄 수, 책 전체 글 받기
// 앱은 나스(lvd.ada.or.kr)로 요청만 넣고, 목소리 굽기는 서버 일꾼이 맡는다.
import Foundation

enum API {
    static let base = "https://lvd.ada.or.kr/nas/"
    static var key: String { Hoewon.shared.yeolsoe }   // 회원 열쇠 — 앱 코드에 나스 열쇠를 적지 않음
    static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AIJeomjaLib/0.1"
    static var perPage: Int { let n = UserDefaults.standard.integer(forKey: "julsu"); return [5, 10, 15, 20, 30].contains(n) ? n : 15 }   // 0.3.0 — 목록 줄 수(기본 15, 설정에서 고름)

    static func url(_ file: String, _ q: [String: String]) -> URL {
        var c = URLComponents(string: base + file)!
        var items = [URLQueryItem(name: "k", value: key)]
        for (k, v) in q.sorted(by: { $0.key < $1.key }) { items.append(URLQueryItem(name: k, value: v)) }
        c.queryItems = items
        return c.url!
    }

    static func request(_ u: URL, timeout: TimeInterval = 30) -> URLRequest {
        var r = URLRequest(url: u)
        r.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        r.cachePolicy = .reloadIgnoringLocalCacheData
        r.timeoutInterval = timeout
        return r
    }

    static func get<T: Decodable>(_ file: String, _ q: [String: String], as type: T.Type) async throws -> T {
        let (d, resp) = try await URLSession.shared.data(for: request(url(file, q)))
        if (resp as? HTTPURLResponse)?.statusCode == 403 { Hoewon.shared.ilheo() }   // 열쇠가 막히면 회원 등록 화면으로
        return try JSONDecoder().decode(T.self, from: d)
    }

    // 목록·찾기·책 정보
    static func gal() async throws -> GalResp { try await get("doseo.php", ["m": "j_gal"], as: GalResp.self) }
    static func list(_ g: String, _ o: Int) async throws -> ListResp { try await get("doseo.php", ["m": "j_list", "g": g, "o": String(o), "n": String(perPage)], as: ListResp.self) }
    static func jakbon(_ j: String, _ o: Int) async throws -> ListResp { try await get("doseo.php", ["m": "j_jakbon", "j": j, "o": String(o), "n": String(perPage)], as: ListResp.self) }
    static func find(_ s: String, _ o: Int) async throws -> ListResp { try await get("doseo.php", ["m": "j_find", "s": s, "o": String(o), "n": String(perPage)], as: ListResp.self) }
    static func book(_ i: Int) async throws -> BookResp { try await get("doseo.php", ["m": "j_book", "i": String(i)], as: BookResp.self) }

    // 독서기: 글자 한 쪽(60문단), 소리 요청, 소리 받기, 소리책·동영상 주소
    static func gulAll(_ i: Int) async throws -> GulResp { try await get("dokseo.php", ["m": "gul", "i": String(i), "all": "1"], as: GulResp.self) }   // 0.3.0 — 내려받기: 책 전체 글을 한 번에
    static func gul(_ i: Int, _ o: Int) async throws -> GulResp { try await get("dokseo.php", ["m": "gul", "i": String(i), "o": String(o)], as: GulResp.self) }

    static func yocheong(_ text: String, voice: Int) async throws -> YoResp {
        var r = request(url("dokseo.php", ["m": "yocheong"]))
        r.httpMethod = "POST"
        let boundary = "lib\(UUID().uuidString)"
        r.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        for (k, v) in [("t", text), ("v", String(voice))] {
            body.append("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(k)\"\r\n\r\n\(v)\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        r.httpBody = body
        let (d, _) = try await URLSession.shared.data(for: r)
        return try JSONDecoder().decode(YoResp.self, from: d)
    }

    /// 구운 소리를 받아 온다. 아직 구워지지 않았으면 nil.
    static func sori(_ h: String) async throws -> Data? {
        let (d, resp) = try await URLSession.shared.data(for: request(url("dokseo.php", ["m": "sori", "h": h])))
        guard let hr = resp as? HTTPURLResponse, hr.statusCode == 200,
              (hr.value(forHTTPHeaderField: "Content-Type") ?? "").contains("audio"), d.count > 100 else { return nil }
        return d
    }

    static func mediaURL(_ i: Int) -> URL { url("dokseo.php", ["m": "media", "i": String(i)]) }
}

struct Gal: Decodable, Identifiable, Hashable {
    let g: String
    let n: Int
    let dan: String
    var id: String { g }
}
struct GalResp: Decodable { let ok: Bool; let name: String?; let pan: String?; let gal: [Gal]? }
struct Item: Decodable, Identifiable, Hashable {
    let i: Int?
    let j: String?
    let t: String
    let g: String?
    var id: String { if let i { return "b\(i)" }; return "j\(j ?? t)" }
}
struct ListResp: Decodable { let ok: Bool; let modu: Int?; let o: Int?; let items: [Item]?; let ttl: String?; let msg: String? }
struct BookResp: Decodable { let ok: Bool; let i: Int?; let t: String?; let g: String?; let meg: Double?; let nal: String?; let kind: String?; let sogae: String?; let jieun: String?; let chulpan: String?; let msg: String? }
struct GulResp: Decodable { let ok: Bool; let ttl: String?; let modu: Int?; let o: Int?; let mun: [String]?; let msg: String? }
struct YoResp: Decodable { let ok: Bool; let h: String?; let msg: String? }
