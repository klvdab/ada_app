// 앱 안 누리집 화면에서 방송사 채널을 틀지 않게 막기 (261109, 새방클, 이사장님 승인 「모두 승인」)
// 앱스토어 심사 지침 5.2(권리자 허락 없는 남의 영상·소리 틀기)를 지키려고, 앱 안 화면에서는
// 협회 주소(ada.or.kr), 유튜브 공식 화면, 화면 꾸밈 꾸러미(cdnjs·jsdelivr·unpkg)만 열고 나머지 방송사 주소는 막습니다.
// 협회의 해설 소리와 유튜브 공식 생방송은 그대로 나옵니다. 방송사 허락서를 받으면 이 목록에 더합니다.
import WebKit

enum BangsongsaMakgi {
    static let ireum = "lvd_bangsongsa_makgi_2"
    static let heorak = ["ada.or.kr", "youtube.com", "youtube-nocookie.com", "ytimg.com", "googlevideo.com", "ggpht.com",
                         "gstatic.com", "googleapis.com", "google.com", "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com"]
    static var gyuchik: String {
        // 웹킷 막기 규칙은 「|」(또는)을 못 쓰므로 data·blob·about 을 한 줄씩
        var r = [#"{"trigger":{"url-filter":".*"},"action":{"type":"block"}}"#]
        for k in ["data", "blob", "about"] {
            r.append(#"{"trigger":{"url-filter":"^"# + k + #":"},"action":{"type":"ignore-previous-rules"}}"#)
        }
        for h in heorak {
            let e = h.replacingOccurrences(of: ".", with: #"\\."#)
            r.append(#"{"trigger":{"url-filter":"^[a-z]+://([^/]*\\.)?"# + e + #"[:/]"},"action":{"type":"ignore-previous-rules"}}"#)
        }
        return "[" + r.joined(separator: ",") + "]"
    }
    /// 막기를 붙인 뒤 kkeut 을 부름(붙이지 못해도 부름 — 화면이 안 열리는 것보다 낫게)
    static func butigi(_ w: WKWebView, _ kkeut: @escaping () -> Void) {
        let st = WKContentRuleListStore.default()
        st?.lookUpContentRuleList(forIdentifier: ireum) { l, _ in
            if let l = l { DispatchQueue.main.async { w.configuration.userContentController.add(l); kkeut() }; return }
            st?.compileContentRuleList(forIdentifier: ireum, encodedContentRuleList: gyuchik) { l2, err in
                DispatchQueue.main.async {
                    if let l2 = l2 { w.configuration.userContentController.add(l2) }
                    else { print("방송사 막기 만들지 못함", err?.localizedDescription ?? "") }
                    kkeut()
                }
            }
        }
    }
}
