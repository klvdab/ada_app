// BYOD 방송 아이폰 — 듣는 화면과 소리 자료 (1.1.5판, 빌드 261010-BI1, 방송클)
// 듣는 분 폰이 여는 화면(deut.html)과 대기 음악(daegi.mp3)·안내말(ment.mp3)은 노트북판·안드로이드판과 똑같은 것을
// 나스에서 받아 아이폰 안에 담아 둡니다. 현장에 인터넷이 없어도 담아 둔 것으로 방송합니다.
// 한 번도 받지 못했으면 앱에 든 간단한 듣는 화면을 대신 냅니다.
import Foundation

enum Jaryo {
    static let NAS = "https://lvd.ada.or.kr/bfblive/hyeonjang/"
    static let IREUM = ["deut.html", "daegi.mp3", "ment.mp3"]

    static var teo: URL {
        let b = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let u = b.appendingPathComponent("byod_jaryo", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    /// 담아 둔 자료(없거나 비었으면 nil)
    static func pail(_ nm: String) -> Data? {
        guard let d = try? Data(contentsOf: teo.appendingPathComponent(nm)), d.count > 0 else { return nil }
        return d
    }

    /// 나스에서 새로 받아 담음. 끝나면 받은 결과 한 줄을 화면 줄기에서 돌려줌
    static func batgi(_ kkeut: @escaping (String) -> Void) {
        let g = DispatchGroup()
        let jamgeum = NSLock()
        var an: [String] = []
        var ok = 0
        for nm in IREUM {
            g.enter()
            guard let u = URL(string: NAS + nm + "?z=\(Int(Date().timeIntervalSince1970))") else { g.leave(); continue }
            var r = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
            r.setValue("Mozilla/5.0 (iPhone) ByodBangsong/" + Bang.PAN, forHTTPHeaderField: "User-Agent")
            URLSession.shared.dataTask(with: r) { d, res, _ in
                var doem = false
                if let d = d, (res as? HTTPURLResponse)?.statusCode == 200, d.count >= 100 {
                    doem = (try? d.write(to: teo.appendingPathComponent(nm), options: .atomic)) != nil
                }
                jamgeum.lock()
                if doem { ok += 1 } else { an.append(nm) }
                jamgeum.unlock()
                g.leave()
            }.resume()
        }
        g.notify(queue: .main) {
            if an.isEmpty { kkeut("듣는 화면과 대기 음악을 나스에서 새로 받았습니다.") }
            else if ok == 0 { kkeut("인터넷에 닿지 않아 새로 받지 못했습니다. 담아 둔 것으로 방송합니다.") }
            else { kkeut("일부만 새로 받았습니다. 받지 못한 것: " + an.joined(separator: ", ")) }
        }
    }

    static var damgimMal: String {
        pail("deut.html") != nil ? "담아 둔 듣는 화면이 있습니다." : "담아 둔 듣는 화면이 없어 간단한 화면을 씁니다."
    }

    /// 나스에서 한 번도 받지 못했을 때 쓰는 간단한 듣는 화면 — 노트북판 듣는 화면과 같은 소리 방식
    static let GANDAN = #"""
<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>현장영상해설 듣기</title><style>
body{font-family:sans-serif;margin:0 auto;max-width:640px;padding:16px;background:#fff;color:#111}
button{box-sizing:border-box;display:block;width:100%;padding:30px 16px;font-size:24px;font-weight:700;margin:14px 0;border:2px solid #222;border-radius:14px;background:#f3f3f3;color:#111}
p{font-size:18px;line-height:1.7}
</style></head><body>
<button id="b">현장영상해설 듣기</button>
<p id="m" aria-live="polite">단추를 누르면 현장영상해설이 들립니다.</p>
<p>안드로이드 폰에서 인터넷이 없다며 연결을 유지할지 물으면 예를 누르십시오. 아이폰에서는 이 화면을 연 채로 두십시오.</p>
<script>
var DEC=new Float32Array(256);
for(var i=0;i<256;i++){var u=~i&0xFF,s=u&0x80,e=(u>>4)&7,m=u&0x0F;var x=(((m<<3)+0x84)<<e)-0x84;DEC[i]=(s?-x:x)/32768;}
var ctx=null,on=false,keu=false,next=0,jan=[],janLen=0,rate=16000;
function $(i){return document.getElementById(i);}
function mal(t){$("m").textContent=t;}
function jalgi(){fetch("/stream",{cache:"no-store"}).then(function(r){var rd=r.body.getReader();function ilk(){return rd.read().then(function(x){if(x.done)throw 0;batgi(x.value);return ilk();});}return ilk();}).catch(function(){setTimeout(jalgi,1000);});}
function batgi(u8){if(!on||!keu){jan=[];janLen=0;return;}jan.push(u8);janLen+=u8.length;if(janLen<1600)return;
var all=new Uint8Array(janLen),o=0;jan.forEach(function(a){all.set(a,o);o+=a.length;});jan=[];janLen=0;
var sr=ctx.sampleRate,n=Math.floor(all.length*sr/rate),ab=ctx.createBuffer(1,n,sr),d=ab.getChannelData(0),st=rate/sr;
for(var k=0;k<n;k++){var p=k*st,i=Math.floor(p),f=p-i,a=DEC[all[i]],b=i+1<all.length?DEC[all[i+1]]:a;d[k]=a+(b-a)*f;}
var now=ctx.currentTime;if(next<now+0.03)next=now+0.25;if(next>now+1.0)next=now+0.25;
var src=ctx.createBufferSource();src.buffer=ab;src.connect(ctx.destination);src.start(next);next+=ab.duration;}
function sangtae(){fetch("/status",{cache:"no-store"}).then(function(r){return r.json();}).then(function(j){if(j.keu!==keu){keu=j.keu;next=0;if(on)mal(keu?"해설이 들리고 있습니다.":"해설을 준비하고 있습니다.");}}).catch(function(){});}
$("b").onclick=function(){if(on){on=false;try{ctx.suspend();}catch(e){}$("b").textContent="현장영상해설 듣기";mal("멈추었습니다.");return;}
if(!ctx){ctx=new (window.AudioContext||window.webkitAudioContext)();var bi=ctx.createBufferSource();bi.buffer=ctx.createBuffer(1,1,22050);bi.connect(ctx.destination);bi.start(0);jalgi();}
try{ctx.resume();}catch(e){}on=true;$("b").textContent="듣기 멈추기";mal(keu?"해설이 들리고 있습니다.":"해설을 준비하고 있습니다.");};
sangtae();setInterval(sangtae,3000);
</script></body></html>
"""#
}
