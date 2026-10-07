# 도서관 앱 가상 폰 전수 시험 (판 0.2.0, 빌드 261007-2, 도서관 창 클)
# 화면마다 글자를 모두 받아 적고, 이름으로 단추를 찾아 눌러 가며 아이폰판과 같은 기능이 다 도는지 살핀다.
import subprocess, time, re, os, xml.etree.ElementTree as ET
P='kr.or.ada.lib'; OUT='gyeolgwa'; os.makedirs(OUT, exist_ok=True)
log=open(OUT+'/jeonsu.txt','w',encoding='utf8')
def sh(c, t=60): return subprocess.run(c, shell=True, capture_output=True, text=True, timeout=t).stdout
def W(s): print(s, flush=True); log.write(s+'\n'); log.flush()
n=[0]
def dump(name):
    n[0]+=1
    sh('adb shell uiautomator dump /sdcard/d.xml >/dev/null 2>&1; adb pull /sdcard/d.xml %s/%02d.xml >/dev/null 2>&1'%(OUT,n[0]))
    sh('adb exec-out screencap -p > %s/%02d_%s.png'%(OUT,n[0],re.sub(r'\W','',name)[:12]))
    try: root=ET.parse('%s/%02d.xml'%(OUT,n[0])).getroot()
    except Exception: W('== %02d %s : 화면을 못 읽음'%(n[0],name)); return []
    nodes=[]
    for e in root.iter('node'):
        t=(e.get('text') or '').strip(); d=(e.get('content-desc') or '').strip()
        if t or d: nodes.append((t or d, e.get('bounds'), e.get('clickable')=='true'))
    seen=[]; 
    for t,_,c in nodes:
        if t not in seen: seen.append(t)
    W('== %02d %s : %d줄'%(n[0],name,len(seen)))
    for t in seen[:60]: W('   '+t.replace('\n',' ')[:120])
    alive=sh('adb shell pidof '+P).strip()
    if not alive: W('   !! 앱이 꺼졌음')
    return nodes
def tap(nodes, word, nth=0):
    hits=[(t,b) for t,b,c in nodes if word in t]
    if len(hits)<=nth: W('   -> 「%s」 못 찾음'%word); return False
    b=hits[nth][1]; x1,y1,x2,y2=map(int,re.findall(r'\d+',b)); sh('adb shell input tap %d %d'%((x1+x2)//2,(y1+y2)//2)); W('   -> 「%s」 누름'%hits[nth][0][:40]); time.sleep(4); return True
def back(): sh('adb shell input keyevent 4'); time.sleep(3)
def swipe_up(): sh('adb shell input swipe 500 1600 500 500 400'); time.sleep(2)
sh('adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%P); time.sleep(20)
cheot=dump('첫 화면')
swipe_up(); cheot2=dump('첫 화면 아래')
for mun in ['주제별','장르별','테마별']:
    sh('adb shell am force-stop %s; adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%(P,P)); time.sleep(15)
    a=dump('첫 화면 다시')
    if tap(a,mun):
        b=dump(mun+' 목록')
        cands=[t for t,_,c in b if c and len(t)>1 and not any(k in t for k in ['뒤로','더 보기','이전','새로고침','첫 화면',mun])]
        if cands and tap(b,cands[min(2,len(cands)-1)]):
            c=dump(mun+' 속')
            cand2=[t for t,_,cl in c if cl and len(t)>3 and not any(k in t for k in ['뒤로','더 보기','이전','새로고침','첫 화면'])]
            if cand2 and tap(c,cand2[0]):
                d=dump(mun+' 속 속')
# 책 하나 끝까지: 글자책 찾아 들어가 듣기
sh('adb shell am force-stop %s; adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%(P,P)); time.sleep(15)
a=dump('첫 화면(책 찾으러)')
if tap(a,'글자책') or tap(a,'장르별'):
    for i in range(4):
        b=dump('글자책 내려가기 %d'%i)
        books=[t for t,_,c in b if c and len(t)>3 and not any(k in t for k in ['뒤로','더 보기','이전','새로고침','첫 화면','갈래','권'])]
        if any('책 정보' in t or '듣기' in t for t,_,c in b): break
        if books: tap(b,books[0])
    b=dump('책 정보')
    if tap(b,'독서기') or tap(b,'듣기'):
        time.sleep(25); r=dump('독서기')
        for w in ['재생 위치','앞으로 30초','뒤로 30초','책갈피','목소리','멈춤','처음부터']:
            W('   독서기에 「%s」 %s'%(w,'있음' if any(w in t for t,_,c in r) else '없음'))
        tap(r,'앞으로 30초'); dump('앞으로 30초 뒤')
        tap(r,'책갈피'); dump('책갈피 뒤')
        back(); dump('독서기에서 뒤로')
for tab in ['내 서재','설정','도움말','더 보기']:
    sh('adb shell am force-stop %s; adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%(P,P)); time.sleep(15)
    a=dump('첫 화면('+tab+')')
    if tap(a,tab):
        s=dump(tab)
        if tab=='설정':
            swipe_up(); s2=dump('설정 아래')
            tap(s+s2,'남자 1'); time.sleep(10); dump('남자 1 고른 뒤')
            for w in ['여자 1','여자 5','남자 5','한 번에','5퍼센트','와이파이','새로고침','업데이트','판']:
                W('   설정에 「%s」 %s'%(w,'있음' if any(w in t for t,_,c in s+s2) else '없음'))
crash=sh('adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: " | head -40')
W('== 앱 오류 기록'); W(crash or '   없음')
log.close()
