// LVD투어스튜디오 앱 — 답사 탭 (판 1.0.1, 빌드 261014-T2)
// 현장을 걸으며: 「여기서부터 다음 지점 시작」으로 경계를 적고, 정지 현장영상해설 자리에서 시계 방향 네 쪽 사진을 찍습니다.
import SwiftUI
import UIKit

struct DapsaTab: View {
    @EnvironmentObject var store: TourStore
    @State private var saeIreum = ""
    @State private var bang = "12시 방향"
    @State private var kamera = false
    @AccessibilityFocusState private var meori: Bool
    static let ne = ["12시 방향", "3시 방향", "6시 방향", "9시 방향"]

    var body: some View {
        List {
            Section { GyeonggyeJul() }
            AllimJul()
            if let k = store.kos, let s = store.cur {
                Section("지금 지점") {
                    Text(k.spotFull(s)).font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader).accessibilityFocused($meori)
                    Text("어디서부터: " + k.spotFrom(s)).fixedSize(horizontal: false, vertical: true)
                    Text("사진 \(s.shots.count)장" + (s.lat != nil ? ", 자리 적힘" : ", 자리 아직 없음"))
                }
                if !k.isGyobon {
                    Section("경계 적기") {
                        if let nx = k.spotNext(s) {
                            Button("여기서부터 " + k.spotFull(nx) + " 시작") { store.boundGo(); jjik() }
                                .font(.headline)
                        } else {
                            Text("클로징 지점이 마지막입니다. 해산하는 자리를 아래 어디까지 칸에 적으십시오.")
                        }
                        VStack(alignment: .leading) {
                            Text(k.spotToName(s)).font(.footnote).accessibilityHidden(true)
                            TextField(k.spotToName(s), text: Binding(get: { s.s("to") }, set: { s.d["to"] = $0; store.bakkwim() }), axis: .vertical)
                                .accessibilityLabel(k.spotLab(s) + " " + k.spotToName(s))
                        }
                        TextField("새 지점 이름 (없어도 됩니다)", text: $saeIreum)
                        Button("여기서부터 새 지점 시작 (" + (s.kind == "close" ? "클로징 지점 바로 앞에" : k.spotLab(s) + " 바로 뒤에") + " 넣기)") {
                            store.addHere(name: saeIreum.trimmingCharacters(in: .whitespaces)); saeIreum = ""; jjik()
                        }
                    }
                    Section("사진 찍기 — 시계 방향으로 네 쪽") {
                        Text("정지 현장영상해설 자리에 서서 참여자가 바라볼 쪽을 12시로 두고 찍으십시오. 첫 사진을 찍을 때 이 지점의 자리가 적히고, 앞 지점에서 걸어온 거리로 이동 시간을 잡습니다.")
                            .font(.footnote)
                        Picker("찍을 방향", selection: $bang) {
                            ForEach(DapsaTab.ne, id: \.self) { Text($0).tag($0) }
                        }
                        Button(bang + " 사진 찍기") {
                            if UIImagePickerController.isSourceTypeAvailable(.camera) { kamera = true }
                            else { store.say("이 기기에서는 카메라를 쓸 수 없습니다.") }
                        }
                        .font(.headline)
                        let jjigeun = Set(s.shots.map { geulja($0["dir"]) })
                        let nameun = DapsaTab.ne.filter { !jjigeun.contains($0) }
                        Text(nameun.isEmpty ? "네 방향을 다 담았습니다. 다음 지점으로 옮기셔도 됩니다." : "아직 담지 않은 방향: " + nameun.joined(separator: ", "))
                    }
                }
                Section("지점 옮겨 보기") {
                    if let p = k.spotPrev(s) { Button("앞 지점 — " + k.spotFull(p)) { store.go(p); jjik() } }
                    if let n = k.spotNext(s) { Button("다음 지점 보기 — " + k.spotFull(n) + " (경계는 적지 않음)") { store.go(n); jjik() } }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("답사")
        .fullScreenCover(isPresented: $kamera) {
            Kamera { im in
                kamera = false
                if let im {
                    let b = bang
                    Task {
                        await store.photo(im, dir: b)
                        if let i = DapsaTab.ne.firstIndex(of: b), let s = store.cur {
                            let jjigeun = Set(s.shots.map { geulja($0["dir"]) })
                            if let nx = (DapsaTab.ne[(i + 1)...] + DapsaTab.ne[..<i]).first(where: { !jjigeun.contains($0) }) {
                                bang = nx
                                store.say(store.allim + " 몸을 시계 방향으로 돌리신 뒤 찍으십시오. 다음은 " + nx + "입니다.")
                            }
                        }
                    }
                }
            }
            .ignoresSafeArea()
        }
    }
    private func jjik() { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meori = true } }
}

/// 아이폰 카메라 — 찍으면 그림을, 그만두면 nil 을 돌려줌
struct Kamera: UIViewControllerRepresentable {
    let kkeut: (UIImage?) -> Void
    func makeCoordinator() -> Co { Co(kkeut) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = .camera
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    final class Co: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let kkeut: (UIImage?) -> Void
        init(_ k: @escaping (UIImage?) -> Void) { kkeut = k }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            kkeut(info[.originalImage] as? UIImage)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { kkeut(nil) }
    }
}
