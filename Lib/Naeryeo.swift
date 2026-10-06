// AI점자도서관 앱 — 내려받기(인터넷 없이 읽기) (판 0.3.0, 빌드 261006-1)
// 이사장님 지시(2026-10-06): 인터넷이 안 되거나 데이터가 모자라도 독서가 끊기지 않게.
// 글자책은 글 전체만 받아 두고, 인터넷이 없으면 폰 목소리로 읽는다. 소리책은 소리 파일을 통째로 받는다.
// 받은 것은 앱만 여는 보관 자리(Application Support)에 두고, 아이클라우드 백업에서 빼고, 폰 잠금으로 잠기는 보호(Data Protection)를 건다.
import Foundation
import SwiftUI
import AVFoundation
import Network

final class Offline: ObservableObject {
    static let shared = Offline()
    @Published var busy: Int = -1
    @Published var done: Int = 0
    @Published var total: Int = 0
    @Published var items: [Item] = []
    @Published var net: Bool = true
    struct Item: Codable, Identifiable, Hashable { var id: Int; var t: String; var kind: String; var size: Int64; var nal: String }
    private var task: Task<Void, Never>?
    private let mon = NWPathMonitor()

    static let synth = AVSpeechSynthesizer()     // 인터넷이 없을 때 읽는 폰 목소리
    static var speakToken = -1
    static var saidPhone = false
    static var online = true
    static var cheap = true                      // 와이파이처럼 데이터 걱정이 없는 길

    init() {
        mon.pathUpdateHandler = { [weak self] p in
            let on = p.status == .satisfied
            Offline.online = on
            Offline.cheap = on && !p.isExpensive && !p.isConstrained
            if on { Offline.saidPhone = false }
            DispatchQueue.main.async { self?.net = on }
        }
        mon.start(queue: DispatchQueue(label: "kr.or.ada.lib.net"))
        items = Offline.readItems()
    }

    static var wifiOnly: Bool { UserDefaults.standard.object(forKey: "wifiOnly") as? Bool ?? true }

    static var root: URL {
        let fm = FileManager.default
        let u = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("books", isDirectory: true)
        if !fm.fileExists(atPath: u.path) {
            try? fm.createDirectory(at: u, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
            var v = URLResourceValues(); v.isExcludedFromBackup = true
            var uu = u; try? uu.setResourceValues(v)
        }
        return u
    }
    static var oldRoot: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("books", isDirectory: true) }
    static var cacheDir: URL {
        let u = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("sori", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }
    static func dir(_ i: Int) -> URL { root.appendingPathComponent("\(i)", isDirectory: true) }
    // 0.2.0 때 받아 둔 문단 소리가 있으면 그대로 쓴다
    static func audioURL(_ i: Int, _ o: Int, _ v: Int) -> URL? {
        let n = dir(i).appendingPathComponent("\(o)_\(v).mp3")
        if FileManager.default.fileExists(atPath: n.path) { return n }
        return oldRoot.appendingPathComponent("\(i)", isDirectory: true).appendingPathComponent("\(o)_\(v).mp3")
    }
    static func loadParas(_ i: Int) -> [String]? {
        for d in [dir(i), oldRoot.appendingPathComponent("\(i)", isDirectory: true)] {
            if let data = try? Data(contentsOf: d.appendingPathComponent("paras.json")),
               let a = try? JSONDecoder().decode([String].self, from: data), !a.isEmpty { return a }
        }
        return nil
    }
    static func localMedia(_ i: Int) -> URL? {
        guard let fs = try? FileManager.default.contentsOfDirectory(atPath: dir(i).path) else { return nil }
        if let f = fs.first(where: { $0.hasPrefix("sori.") }) { return dir(i).appendingPathComponent(f) }
        return nil
    }
    static func asset(_ i: Int) -> AVURLAsset? { localMedia(i).map { AVURLAsset(url: $0) } }
    static func readItems() -> [Item] {
        guard let ds = try? FileManager.default.contentsOfDirectory(atPath: root.path) else { return [] }
        var out: [Item] = []
        for d in ds {
            if let data = try? Data(contentsOf: root.appendingPathComponent(d).appendingPathComponent("meta.json")),
               let it = try? JSONDecoder().decode(Item.self, from: data) { out.append(it) }
        }
        return out.sorted { $0.nal == $1.nal ? $0.t < $1.t : $0.nal > $1.nal }
    }
    static func folderSize(_ u: URL) -> Int64 {
        var s: Int64 = 0
        if let e = FileManager.default.enumerator(at: u, includingPropertiesForKeys: [.fileSizeKey]) {
            for case let f as URL in e { s += Int64((try? f.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) }
        }
        return s
    }
    var totalSize: Int64 { items.reduce(0) { $0 + $1.size } }
    static func meg(_ b: Int64) -> String { String(format: "%.1f메가", Double(b) / 1_048_576) }
    static func today() -> String { let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd HH:mm"; return f.string(from: Date()) }

    func download(i: Int, title: String, kind: String = "geul") {
        guard busy < 0 else { Store.shared.say("다른 책을 내려받는 중입니다."); return }
        if !Offline.online { Store.shared.say("인터넷이 연결되지 않아 내려받을 수 없습니다."); return }
        if Offline.wifiOnly && !Offline.cheap {
            Store.shared.say("와이파이에서만 내려받기로 정해 두셨습니다. 와이파이에 연결한 뒤 다시 눌러 주십시오. 설정에서 바꿀 수 있습니다.")
            return
        }
        busy = i; done = 0; total = 1
        Store.shared.say("내려받기를 시작합니다.")
        task = Task { @MainActor in
            let fm = FileManager.default
            let d = Offline.dir(i)
            try? fm.createDirectory(at: d, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
            var ok = false
            if kind == "geul" {
                if let r = try? await API.gulAll(i), r.ok, let mun = r.mun, !mun.isEmpty, let data = try? JSONEncoder().encode(mun) {
                    ok = (try? data.write(to: d.appendingPathComponent("paras.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])) != nil
                }
            } else {
                if let res = try? await URLSession.shared.download(for: API.request(API.mediaURL(i), timeout: 900)) {
                    let (tmp, resp) = res
                    let hr = resp as? HTTPURLResponse
                    let mime = hr?.value(forHTTPHeaderField: "Content-Type") ?? ""
                    let ext = mime.contains("mp4") || mime.contains("m4a") ? "m4a" : (mime.contains("wav") ? "wav" : (mime.contains("ogg") ? "ogg" : "mp3"))
                    if hr?.statusCode == 200, !Task.isCancelled {
                        let dst = d.appendingPathComponent("sori.\(ext)")
                        try? fm.removeItem(at: dst)
                        ok = (try? fm.moveItem(at: tmp, to: dst)) != nil
                        try? fm.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: dst.path)
                    } else { try? fm.removeItem(at: tmp) }
                }
            }
            if ok && !Task.isCancelled {
                let it = Item(id: i, t: title, kind: kind, size: Offline.folderSize(d), nal: Offline.today())
                if let m = try? JSONEncoder().encode(it) { try? m.write(to: d.appendingPathComponent("meta.json")) }
                self.items = Offline.readItems()
                Store.shared.downloaded.insert(i); Store.shared.save()
                self.done = 1
                Store.shared.say("내려받기를 마쳤습니다. 이제 인터넷이 없어도 들을 수 있습니다.")
            } else {
                try? fm.removeItem(at: d)
                if !Task.isCancelled { Store.shared.say("내려받지 못했습니다. 인터넷을 확인하고 다시 눌러 주십시오.") }
            }
            self.busy = -1
        }
    }
    func cancel() { task?.cancel(); busy = -1; Store.shared.say("내려받기를 멈췄습니다.") }
    func remove(_ i: Int) {
        try? FileManager.default.removeItem(at: Offline.dir(i))
        try? FileManager.default.removeItem(at: Offline.oldRoot.appendingPathComponent("\(i)", isDirectory: true))
        Store.shared.downloaded.remove(i); Store.shared.save()
        items = Offline.readItems()
    }
    func removeAll() {
        for it in items { try? FileManager.default.removeItem(at: Offline.dir(it.id)) }
        try? FileManager.default.removeItem(at: Offline.oldRoot)
        Store.shared.downloaded.removeAll(); Store.shared.save()
        items = []
        Store.shared.say("내려받은 책을 모두 지웠습니다.")
    }
}

// 내 서재 맨 위 — 내려받은 책
struct NaeryeoSection: View {
    @EnvironmentObject var offline: Offline
    @EnvironmentObject var nav: Nav
    var body: some View {
        if !offline.items.isEmpty {
            Section("내려받은 책 \(offline.items.count)권, \(Offline.meg(offline.totalSize))") {
                ForEach(offline.items) { it in
                    Button(it.t) { nav.push(Route.reader(it.id, it.t, it.kind)) }
                        .accessibilityHint("인터넷 없이 들을 수 있습니다")
                }
            }
        }
    }
}

// 설정 — 목록 줄 수, 와이파이에서만 내려받기, 저장 공간
struct NaeryeoSeoljeong: View {
    @EnvironmentObject var offline: Offline
    @AppStorage("wifiOnly") private var wifiOnly = true
    @AppStorage("julsu") private var julsu = 15
    @State private var jiugi = false
    var body: some View {
        Section("목록과 내려받기") {
            Picker("목록 줄 수", selection: $julsu) {
                ForEach([5, 10, 15, 20, 30], id: \.self) { Text("\($0)줄").tag($0) }
            }
            Toggle("와이파이에서만 내려받기", isOn: $wifiOnly)
            Text("내려받은 책 \(offline.items.count)권, \(Offline.meg(offline.totalSize))")
            if !offline.items.isEmpty {
                Button("내려받은 책 모두 지우기", role: .destructive) { jiugi = true }
                    .confirmationDialog("내려받은 책을 모두 지울까요?", isPresented: $jiugi) {
                        Button("모두 지우기", role: .destructive) { offline.removeAll() }
                        Button("그만두기", role: .cancel) {}
                    }
            }
        }
    }
}
