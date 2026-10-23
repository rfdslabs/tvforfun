# lib/adb.sh — descoberta e conexão de TVs por ADB. (sourced)

ADB_PORT="${ADB_PORT:-5555}"

# Prefixo /24 da rede local (ex.: 192.168.0)
tvf_lan_prefix() {
  local ip
  ip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
  [ -n "$ip" ] || { tvf_err "sem IP de LAN (Wi-Fi conectado?)"; return 1; }
  echo "${ip%.*}"
}

# Fabricante aproximado por prefixo de MAC (OUI) — só pra dar pista na descoberta.
tvf_mac_vendor() {
  case "$(echo "$1" | tr 'A-F' 'a-f' | cut -c1-8)" in
    48:b4:23|80:c:f9|80:0c:f9|74:c2:46|f0:27:2d|fc:65:de|68:54:fd|44:65:0d) echo "Amazon (Fire TV)";;
    3c:9b:d6|2c:64:1f|00:19:9d|e8:48:b8) echo "Vizio";;
    *) echo "?";;
  esac
}

# Varre a LAN e lista candidatos a TV (porta 5555 aberta = ADB ligado).
# Saída: linhas "IP\tvendor\t5555:open|closed"
tvf_discover() {
  local pfx; pfx="$(tvf_lan_prefix)" || return 1
  tvf_info "varrendo ${pfx}.0/24 (pode levar alguns segundos)..." >&2
  local i
  for i in $(seq 1 254); do ping -c1 -W1 "${pfx}.$i" >/dev/null 2>&1 & done; wait
  # hosts vivos na ARP
  local arp; arp="$(arp -a -n 2>/dev/null | grep "$pfx" | grep -v incomplete || true)"
  # checa 5555 em paralelo
  while read -r line; do
    [ -n "$line" ] || continue
    local ip mac
    ip="$(echo "$line" | grep -oE "$pfx\.[0-9]+" | head -1)"
    mac="$(echo "$line" | grep -oE '([0-9a-f]{1,2}:){5}[0-9a-f]{1,2}' | head -1)"
    [ -n "$ip" ] || continue
    ( if nc -z -G2 "$ip" "$ADB_PORT" 2>/dev/null; then st="open"; else st="closed"; fi
      printf '%s\t%s\t5555:%s\n' "$ip" "$(tvf_mac_vendor "$mac")" "$st" ) &
  done <<< "$arp"
  wait
}

# Conecta e diz o estado: imprime "device"|"unauthorized"|"offline"|"fail"
tvf_connect_ip() {
  local ip="$1" t="$1:$ADB_PORT"
  adb connect "$t" >/dev/null 2>&1 || true
  sleep 1
  adb devices -l 2>/dev/null | awk -v t="$t" '$1==t{print $2; found=1} END{if(!found)print "fail"}'
}

# getprop de uma chave no alvo
tvf_getprop() { tvf_adb shell "getprop $1" 2>/dev/null | tr -d '\r'; }
