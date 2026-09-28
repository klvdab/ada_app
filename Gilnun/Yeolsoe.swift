// 열쇠 곳간 — 아이폰 키체인에 담습니다. 앱을 지웠다 다시 깔아도 남습니다.
// 2026-09-28 이사장님: "오늘 이후는 이런 일이 또 없어야" — 가족·지인 명단의 주인 열쇠와 전화번호를 잃지 않게 여기에 둡니다.
// 전화번호는 폰 안(키체인)에만 두고 서버로 보내지 않습니다(웹 길눈과 같은 원칙).
import Foundation
import Security

enum Yeolsoe {
    private static let seobiseu = "kr.or.ada.gilnun"

    static func ilgi(_ k: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: seobiseu,
                                kSecAttrAccount as String: k,
                                kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var r: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &r) == errSecSuccess, let d = r as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func sseugi(_ k: String, _ v: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: seobiseu,
                                kSecAttrAccount as String: k]
        SecItemDelete(q as CFDictionary)
        var a = q
        a[kSecValueData as String] = Data(v.utf8)
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(a as CFDictionary, nil)
    }

    /// 가족·지인 명단의 주인 열쇠(스물두 자) — 한 번 만들면 바뀌지 않음
    static var juin: String {
        if let v = ilgi("juin"), v.count >= 16 { return v }
        let ja = Array("abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        var s = ""
        for _ in 0..<22 { s.append(ja[Int.random(in: 0..<ja.count)]) }
        sseugi("juin", s)
        return s
    }
}
