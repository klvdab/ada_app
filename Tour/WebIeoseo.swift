// LVD투어스튜디오 앱 — 웹에서 이어서 고치기 (판 1.4.3, 빌드 261123-T9, 2026-11-23 새방클, 이사장님 승인 「1」)
// 앱에서 하던 코스·지점을 웹 투어스튜디오에서 바로 이어 엽니다. 많은 글은 컴퓨터의 웹이 편합니다(안드로이드 webIeoseo 와 같음).
import SwiftUI
import UIKit

enum WebJuso {
    static let gibon = "https://lvd.ada.or.kr/lvdts/lvdts.html"
    static func ieoseo(kos: String, spot: String?) -> String {
        let q = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=+?"))
        var u = gibon + "?open=" + (kos.addingPercentEncoding(withAllowedCharacters: q) ?? kos)
        if let s = spot, !s.isEmpty { u += "&spot=" + (s.addingPercentEncoding(withAllowedCharacters: q) ?? s) }
        return u
    }
}

struct WebIeoseoView: View {
    @EnvironmentObject var store: TourStore
    let spotId: String?
    var body: some View {
        Form {
            if let k = store.kos {
                let ju = WebJuso.ieoseo(kos: k.id, spot: spotId)
                Section {
                    Text("아래 주소를 열면 웹 투어스튜디오에서 " + (spotId == nil ? "이 코스가" : "이 코스의 이 지점이") + " 바로 열립니다. 같은 아이디로 들어오십시오. 컴퓨터에서 여시려면 주소 복사를 눌러 문자나 메일로 보내십시오.")
                    Text("주소: " + ju).textSelection(.enabled)
                    Link("열기", destination: URL(string: ju) ?? URL(string: WebJuso.gibon)!)
                    Button("주소 복사") { UIPasteboard.general.string = ju; store.say("주소를 복사했습니다. 문자나 메일에 붙여 컴퓨터로 보내십시오.") }
                }
            }
        }
        .navigationTitle(spotId == nil ? "웹에서 이 코스 이어서 고치기" : "웹에서 이 지점 이어서 고치기")
        .task { _ = await store.save(quiet: true) }
    }
}
