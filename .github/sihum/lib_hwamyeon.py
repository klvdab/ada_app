# 도서관 앱 안드로이드 화면 하나하나 눌러 보기 (판 0.1.5, 빌드 261008-4, 도서관 창 클) — 0.1.5: 화면 크기에 맞춘 쓸기, 덤프 다시 하기·오류 이유 남기기, 화면마다 앱 꺼짐 살피기, 끝까지 내려 읽기
import subprocess,time,re,os,html
def sh(c,t=60):
    try: return subprocess.run(c,shell=True,capture_output=True,text=True,timeout=t).stdout
    except Exception as e: return ''
P='kr.or.ada.lib'; os.makedirs('g',exist_ok=True); OUT=open('g/gyeolgwa.txt','w',encoding='utf8'); n=[0]
def say(s): print(s,flush=True); OUT.write(s+'\n'); OUT.flush()
def dump(name,show=True):
    n[0]+=1; f='g/%02d_%s'%(n[0],name)
    x=''; er=''
    for k in range(4):
        sh('adb shell rm -f /sdcard/d.xml'); er=sh('adb shell uiautomator dump /sdcard/d.xml 2>&1'); sh('adb pull /sdcard/d.xml %s.xml'%f)
        try: x=open(f+'.xml',encoding='utf8').read()
        except Exception: x=''
        if '<node' in x: break
        time.sleep(2)
    sh('adb exec-out screencap -p > %s.png'%f)
    if '<node' not in x: say('!! 화면 글을 못 읽음(%s): %s'%(name,er.strip()[:200]))
    if show:
        ws=[]
        for nd in re.findall(r'<node [^>]*>',x):
            t=re.search(r' text="([^"]*)"',nd); c=re.search(r'content-desc="([^"]*)"',nd)
            s=html.unescape((t.group(1) if t else '') or (c.group(1) if c else ''))
            if s and s not in ws: ws.append(s)
        say('== %02d %s: %s'%(n[0],name,' | '.join(ws)[:1800]))
    return x
def find(x,label):
    nds=re.findall(r'<node [^>]*>',x)
    def key(nd):
        t=re.search(r' text="([^"]*)"',nd); c=re.search(r'content-desc="([^"]*)"',nd)
        return html.unescape(t.group(1) if t else '').strip()==label or html.unescape(c.group(1) if c else '').strip()==label
    nds=[nd for nd in nds if key(nd)]+[nd for nd in nds if not key(nd)]
    for nd in nds:
        t=re.search(r' text="([^"]*)"',nd); c=re.search(r'content-desc="([^"]*)"',nd); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',nd)
        s=html.unescape((t.group(1) if t else '')+' '+(c.group(1) if c else ''))
        if label in s and b:
            l,tp,r,bt=map(int,b.groups())
            if r>l and bt>tp: return ((l+r)//2,(tp+bt)//2)
    return None
def tap(label,tries=6):
    for i in range(tries):
        x=dump('chatgi',False); p=find(x,label)
        if p: sh('adb shell input tap %d %d'%p); time.sleep(3); return True
        ol(); time.sleep(1)
    say('!! 못 찾음: '+label); return False
_wm=re.search(r'(\d+)x(\d+)',sh('adb shell wm size')); W,H=(int(_wm.group(1)),int(_wm.group(2))) if _wm else (1080,2400)
def ol(): sh('adb shell input swipe %d %d %d %d 900'%(W//2,int(H*0.75),W//2,int(H*0.30)))
def wiro():
    for i in range(6): sh('adb shell input swipe %d %d %d %d 300'%(W//2,int(H*0.30),W//2,int(H*0.80)))
def kkeut(name):
    seen=[]
    for i in range(12):
        x=dump('%s_%d'%(name,i),False); b=len(seen)
        for nd in re.findall(r'<node [^>]*>',x):
            t=re.search(r' text="([^"]*)"',nd); c=re.search(r'content-desc="([^"]*)"',nd)
            v=html.unescape((t.group(1) if t else '') or (c.group(1) if c else ''))
            if v and v not in seen: seen.append(v)
        if i>0 and b==len(seen): break
        ol(); time.sleep(1)
    say('== %s 끝까지: %s'%(name,' | '.join(seen)[:3500]))
def kkeojim(name):
    c=sh('adb logcat -b crash -d')
    if P in c or 'FATAL' in c: say('!! 앱 꺼짐(%s): %s'%(name,c.strip()[-900:])); sh('adb logcat -b crash -c')
def dwiro(): sh('adb shell input keyevent 4'); time.sleep(2)
def salla(): return P in sh('adb shell pidof '+P) or sh('adb shell pidof '+P).strip()!=''
def sori(): o=sh('adb shell dumpsys audio'); return 'state:started' in o
def ggeut(name):
    say('-- %s: 앱 %s, 소리 %s'%(name,'살아 있음' if salla() else '꺼짐','남' if sori() else '안 남'))
sh('adb shell settings put global window_animation_scale 0')
sh('adb shell pm grant %s android.permission.POST_NOTIFICATIONS'%P)
sh('adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%P); time.sleep(20)
x=dump('0_deungrok')
if find(x,'휴대전화'):
    eds=[tuple(map(int,b)) for b in re.findall(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x)]
    if len(eds)>=2:
        l,t,r,b=eds[0]; sh('adb shell input tap %d %d'%((l+r)//2,(t+b)//2)); sh('adb shell input text keulsihum')
        l,t,r,b=eds[1]; sh('adb shell input tap %d %d'%((l+r)//2,(t+b)//2)); sh('adb shell input text 01000000000')
        sh('adb shell input keyevent 111'); time.sleep(1); tap('등록'); time.sleep(8)
    else: say('!! 등록 칸을 못 찾음 %d'%len(eds))
dump('1_cheot'); ggeut('첫 화면'); kkeut('cheot'); kkeojim('첫 화면')
for m in ['주제별로 찾기','장르별로 찾기','테마별로 찾기']:
    wiro()
    if tap(m): kkeut('mun_'+m[:3]); kkeojim(m); dwiro()
wiro()
x=dump('chatgi_hwamyeon',False)
eds=[tuple(map(int,b)) for b in re.findall(r'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x)]
if eds:
    l,t,r,b=eds[0]; sh('adb shell input tap %d %d'%((l+r)//2,(t+b)//2)); sh('adb shell input text Pride'); sh('adb shell input keyevent 111'); time.sleep(1)
    tap('찾기'); time.sleep(8); dump('chatgi_gyeolgwa')
if True:
    x=dump('g',False)
    if tap('Pride and Prejudice - Austen, Jane (영어)'):
        x=dump('chaek_jeongbo'); say('-- 책 정보 스크롤 가능: '+str('scrollable="true"' in x))
if tap('읽기') or tap('독서기로 듣기') or tap('듣기'):
    time.sleep(30); dump('dokseogi'); ggeut('읽기 30초 뒤')
    if tap('앞으로 30초'): time.sleep(5); ggeut('앞으로 30초 뒤')
    sh('adb shell input keyevent 26'); time.sleep(25); ggeut('화면 끈 뒤 25초'); sh('adb shell input keyevent 224'); sh('adb shell input keyevent 82'); time.sleep(3)
    dump('dokseogi_dasi')
sh('adb shell am force-stop '+P); sh('adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%P); time.sleep(15)
for m in ['내 서재','설정·도움말']:
    if tap(m):
        time.sleep(3); kkeut('tab_'+m); kkeojim(m)
        if m=='내 서재' and tap('더 보기 — 다 읽은 책'): kkeut('seojae_deobogi'); kkeojim('내 서재 더 보기')
wiro()
if tap('여자 2'): time.sleep(20); ggeut('여자 2 미리 듣기')
kkeojim('미리 듣기')
lg=sh('adb logcat -b crash -d | head -40')
say('== 오류 기록: '+(lg.strip() or '없음'))
OUT.close()
