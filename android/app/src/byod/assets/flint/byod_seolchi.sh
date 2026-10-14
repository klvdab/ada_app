#!/bin/sh
# BYOD 방송 — 플린트 2(GL-MT6000) 준비하기
# 판 1.2.0 / 빌드 261014-F1 / 2026-10-14 (이사장님 승인 「1」, 방송클)
# 1.3.0 / 261014-F2 행사 녹음을 배프에 올릴 https 꾸러미(python3-openssl, ca-bundle)를 함께 깖
# BYOD 앱의 「플린트 2 준비하기」가 이 파일과 byod_flint.py 를 공유기 /tmp 에 올린 뒤 sh /tmp/byod_seolchi.sh 로 돌립니다.
# 사무실에서 한 번만 하면 됩니다. 공유기 인터넷(WAN) 단자에 사무실 인터넷 선이 꽂혀 있어야 합니다(파이썬과 mp3 꾸러미 받기).
# 하는 일
#   1. 파이썬과 lame(mp3 만들기) 깔기
#   2. 방송 서버를 /etc/byod 에 두고, 켤 때마다 저절로 돌게(procd)
#   3. 와이파이 이름 「현장영상해설방송」, 비밀번호 없음, 2.4와 5기가 모두
#   4. 듣는 분 1,000명까지 주소를 나눠 줄 수 있게(192.168.8.0/22)
#   5. 듣기 화면 주소 192.168.8.2, 폰이 어느 누리집을 물어도 듣기 화면으로(방송 중에만)
# 줄마다 「BYOD:」로 시작하는 말을 앱이 그대로 읽어 줍니다.

SSID="${BYOD_SSID:-현장영상해설방송}"
SEOBEO_IP="192.168.8.2"
mal() { echo "BYOD: $1"; }
TEO=/etc/byod

mal "플린트 2 준비를 시작합니다."
[ -f /tmp/byod_flint.py ] || { mal "방송 서버 파일이 올라오지 않았습니다. 다시 눌러 주십시오."; exit 1; }

# 1. 꾸러미
mal "파이썬을 받고 있습니다. 1분쯤 걸립니다."
opkg update >/tmp/byod_opkg.log 2>&1 || { mal "꾸러미 목록을 받지 못했습니다. 공유기 인터넷 단자에 사무실 인터넷 선이 꽂혀 있는지 보십시오."; exit 2; }
if ! command -v python3 >/dev/null 2>&1; then
  opkg install python3-light python3-asyncio python3-urllib python3-codecs >>/tmp/byod_opkg.log 2>&1 || opkg install python3 >>/tmp/byod_opkg.log 2>&1
fi
command -v python3 >/dev/null 2>&1 || { mal "파이썬을 깔지 못했습니다."; exit 3; }
python3 -c "import asyncio, json, urllib.parse" 2>/dev/null || { opkg install python3 >>/tmp/byod_opkg.log 2>&1; }
python3 -c "import asyncio, json, urllib.parse" 2>/dev/null || { mal "파이썬 부품이 모자랍니다."; exit 3; }
# 1.3.0 공유기가 인터넷이 될 때 행사 녹음을 스스로 배프에 올리려면 https 가 필요
opkg install python3-openssl ca-bundle >>/tmp/byod_opkg.log 2>&1 || true
if ! command -v lame >/dev/null 2>&1; then
  opkg install lame >>/tmp/byod_opkg.log 2>&1 && mal "화면을 잠가도 이어지는 소리(mp3)를 쓸 수 있습니다." || mal "mp3 꾸러미를 받지 못했습니다. 늦음이 가장 적은 방식만 씁니다."
fi

# 2. 방송 서버
mkdir -p $TEO
cp /tmp/byod_flint.py $TEO/byod_flint.py
[ -f $TEO/ttuigi ] || echo ttuim > $TEO/ttuigi
[ -f $TEO/tonggwa ] || echo an > $TEO/tonggwa
cat > /etc/init.d/byod <<'INIT'
#!/bin/sh /etc/rc.common
# BYOD 방송 서버(플린트 2) — 켜기·끄기
START=99
STOP=10
USE_PROCD=1
IP=192.168.8.2
start_service() {
  ip addr add $IP/22 dev br-lan 2>/dev/null
  # 폰이 어느 누리집을 물어도 듣기 화면 주소로(공유기 관리 화면은 그대로)
  mkdir -p /tmp/dnsmasq.d
  printf 'address=/#/%s\naddress=/console.gl-inet.com/192.168.8.1\n' $IP > /tmp/dnsmasq.d/byod.conf
  /etc/init.d/dnsmasq restart >/dev/null 2>&1
  procd_open_instance
  procd_set_param command /usr/bin/python3 /etc/byod/byod_flint.py
  procd_set_param respawn 3600 2 0
  procd_set_param stdout 1
  procd_set_param stderr 1
  procd_close_instance
}
stop_service() {
  rm -f /tmp/dnsmasq.d/byod.conf
  /etc/init.d/dnsmasq restart >/dev/null 2>&1
}
INIT
chmod +x /etc/init.d/byod
/etc/init.d/byod enable

# 80번으로 오는 「인터넷 되느냐」 물음을 방송 서버(7880)로 돌림 — 방화벽을 다시 읽어도 남게
if command -v nft >/dev/null 2>&1 && nft list table inet fw4 >/dev/null 2>&1; then
  mkdir -p /usr/share/nftables.d/chain-pre/dstnat
  echo "iifname \"br-lan\" ip daddr $SEOBEO_IP tcp dport 80 redirect to :7880" > /usr/share/nftables.d/chain-pre/dstnat/30-byod.nft
else
  touch /etc/firewall.user
  grep -q byod-80 /etc/firewall.user || echo "iptables -t nat -I PREROUTING -i br-lan -d $SEOBEO_IP -p tcp --dport 80 -j REDIRECT --to-ports 7880 -m comment --comment byod-80" >> /etc/firewall.user
  uci -q get firewall.byod_user >/dev/null || { uci set firewall.byod_user=include; uci set firewall.byod_user.path=/etc/firewall.user; uci set firewall.byod_user.reload=1; }
fi

# 3. 와이파이 — lan 에 붙은 공유기 와이파이 모두(손님용은 그대로)
mal "와이파이 이름을 $SSID 로 바꿉니다. 비밀번호는 없습니다."
for s in $(uci show wireless | sed -n "s/^wireless\.\([^.]*\)\.mode='ap'$/\1/p"); do
  [ "$(uci -q get wireless.$s.network)" = "lan" ] || continue
  uci set wireless.$s.ssid="$SSID"
  uci set wireless.$s.encryption='none'
  uci -q delete wireless.$s.key
  uci set wireless.$s.disabled='0'
  uci set wireless.$s.isolate='0'
  uci set wireless.$s.disassoc_low_ack='0'
done
for r in $(uci show wireless | sed -n "s/^wireless\.\([^.]*\)=wifi-device$/\1/p"); do uci set wireless.$r.disabled='0'; done

# 4. 주소 1,000명까지 — 192.168.8.1/22, 나눠 줄 주소는 .10부터, 2시간
uci set network.lan.netmask='255.255.252.0'
uci set network.lan.ipv6='0'
uci set dhcp.lan.start='10'
uci set dhcp.lan.limit='1000'
uci set dhcp.lan.leasetime='2h'
uci set dhcp.lan.ra='disabled'
uci set dhcp.lan.dhcpv6='disabled'
uci commit

# 5. 시험: 방송 서버가 이 공유기에서 뜨는지
python3 -m py_compile $TEO/byod_flint.py 2>/dev/null || { mal "방송 서버 파일이 상했습니다. 다시 눌러 주십시오."; exit 4; }
mal "준비를 마쳤습니다. 공유기가 다시 켜집니다. 1분 뒤 와이파이 $SSID 에 붙으면 듣기 화면이 저절로 뜹니다."
echo "BYOD-KKEUT"
( sleep 3; reboot ) >/dev/null 2>&1 &
exit 0
