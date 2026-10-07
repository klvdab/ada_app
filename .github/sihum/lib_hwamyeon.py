# 도서관 앱 안드로이드 화면 하나하나 눌러 보기 (판 0.1.1, 빌드 261007-2, 도서관 창 클) — 0.1.1: 알림 허락, 회원 등록 넘기기
import subprocess,time,re,os,html
def sh(c,t=60):
    try: return subprocess.run(c,shell=True,capture_output=True,text=True,timeout=t).stdout
    except Exception as e: return ''
P='kr.or.ada.lib'; os.makedirs('g',exist_ok=True); OUT=open('g/gyeolgwa.txt','w',encoding='utf8'); n=[0]
def say(s): print(s,flush=True); OUT.write(s+'\n'); OUT.flush()
def dump(name,show=True):
    n[0]+=1; f='g/%02d_%s'%(n[0],name)
    sh('adb shell uiautomator dump /sdcard/d.xml'); sh('adb pull /sdcard/d.xml %s.xml'%f); sh('adb exec-out screencap -p > %s.png'%f)
    try: x=open(f+'.xml',encoding='utf8').read()
    except Exception: x=''
    if show:
        ws=[]
        for nd in re.findall(r'<node [^>]*>',x):
            t=re.search(r' text="([^"]*)"',nd); c=re.search(r'content-desc="([^"]*)"',nd)
            s=html.unescape((t.group(1) if t else '') or (c.group(1) if c else ''))
            if s and s not in ws: ws.append(s)
        say('== %02d %s: %s'%(n[0],name,' | '.join(ws)[:1800]))
    return x
def find(x,label):
    for nd in re.findall(r'<node [^>]*>',x):
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
        sh('adb shell input swipe 540 1700 540 700 400'); time.sleep(1)
    say('!! 못 찾음: '+label); return False
def wiro():
    for i in range(6): sh('adb shell input swipe 540 600 540 1800 300')
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
dump('1_cheot'); ggeut('첫 화면')
for m in ['주제별로 찾기','장르별로 찾기','테마별로 찾기']:
    wiro()
    if tap(m): dump('mun_'+m[:3]); dwiro()
wiro()
if tap('찾기'):
    x=dump('chatgi_hwamyeon')
    p=find(x,'EditText') 
    sh('adb shell input text Pride'); sh('adb shell input keyevent 66'); time.sleep(8); dump('chatgi_gyeolgwa')
    x=dump('g',False)
    if tap('Pride'): dump('chaek_jeongbo')
if tap('읽기') or tap('듣기'):
    time.sleep(30); dump('dokseogi'); ggeut('읽기 30초 뒤')
    if tap('앞으로 30초'): time.sleep(5); ggeut('앞으로 30초 뒤')
    sh('adb shell input keyevent 26'); time.sleep(25); ggeut('화면 끈 뒤 25초'); sh('adb shell input keyevent 224'); sh('adb shell input keyevent 82'); time.sleep(3)
    dump('dokseogi_dasi')
sh('adb shell am force-stop '+P); sh('adb shell monkey -p %s -c android.intent.category.LAUNCHER 1'%P); time.sleep(15)
for m in ['내 서재','설정']:
    if tap(m):
        dump('tab_'+m)
        for i in range(4): sh('adb shell input swipe 540 1700 540 700 400'); time.sleep(1); dump('tab_%s_%d'%(m,i))
if tap('여자 2'): time.sleep(20); ggeut('여자 2 미리 듣기')
if tap('도움말'): dump('doumal')
lg=sh('adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -30')
say('== 오류 기록: '+(lg.strip() or '없음'))
OUT.close()
