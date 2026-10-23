# commands/clean.sh — backup, DNS anti-anúncio, debloat (com trava) e restore. (sourced)

tvf_clean() {
  local sub="${1:-help}"; shift || true
  case "$sub" in
    backup)  tvf_clean_backup "$@" ;;
    dns)     tvf_clean_dns "$@" ;;
    debloat) tvf_clean_debloat "$@" ;;
    restore) tvf_clean_restore "$@" ;;
    *) cat <<EOF
tvforfun clean <sub>
  backup                 salva pacotes/settings/getprop (feito automático antes do debloat)
  dns on|off             Private DNS anti-anúncio (AdGuard) liga/desliga
  debloat [lista] [--dry-run]   desabilita anúncios/telemetria (padrão: Fire TV)
  restore                reativa tudo que o último debloat desligou + DNS off
EOF
    ;;
  esac
}

tvf_clean_backup() {
  tvf_need_adb || return 1
  local model ts dir
  model="$(tvf_getprop ro.product.model)"; model="${model:-tv}"
  ts="$(date +%Y%m%d-%H%M%S)"
  dir="$TVF_HOME/backups/${model}-${ts}"
  mkdir -p "$dir"
  tvf_info "backup → $dir"
  tvf_adb shell 'getprop' > "$dir/getprop.txt" 2>/dev/null
  tvf_adb shell 'pm list packages'    | sort | tr -d '\r' > "$dir/packages-all.txt"
  tvf_adb shell 'pm list packages -e' | sort | tr -d '\r' > "$dir/packages-enabled.txt"
  tvf_adb shell 'pm list packages -d' | sort | tr -d '\r' > "$dir/packages-disabled-before.txt"
  local ns
  for ns in global system secure; do
    tvf_adb shell "settings list $ns" | tr -d '\r' > "$dir/settings-$ns.txt" 2>/dev/null
  done
  echo "$dir" > "$TVF_HOME/last-backup"
  tvf_ok "backup salvo ($(wc -l < "$dir/packages-all.txt" | tr -d ' ') pacotes)"
  echo "$dir"
}

tvf_clean_dns() {
  tvf_need_adb || return 1
  case "${1:-}" in
    on)
      tvf_adb shell settings put global private_dns_specifier dns.adguard-dns.com >/dev/null
      tvf_adb shell settings put global private_dns_mode hostname >/dev/null
      tvf_ok "Private DNS = AdGuard (dns.adguard-dns.com). Não bloqueia anúncio do YouTube." ;;
    off)
      tvf_adb shell settings put global private_dns_mode opportunistic >/dev/null
      tvf_ok "Private DNS = automático (off)." ;;
    *)
      local m h
      m="$(tvf_adb shell settings get global private_dns_mode | tr -d '\r')"
      h="$(tvf_adb shell settings get global private_dns_specifier | tr -d '\r')"
      tvf_say "DNS atual: mode=$m host=$h  (use: tvforfun clean dns on|off)" ;;
  esac
}

# um pacote é protegido se bater exatamente com uma linha de protected.txt
tvf_is_protected() {
  local pkg="$1"
  grep -qxF "$pkg" "$TVF_ROOT/data/protected.txt"
}

tvf_clean_debloat() {
  tvf_need_adb || return 1
  local list="$TVF_ROOT/data/firetv-ads.txt" dry=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --dry-run) dry=1 ;;
      *) [ -f "$1" ] && list="$1" ;;
    esac; shift
  done
  [ -f "$list" ] || { tvf_err "lista não encontrada: $list"; return 1; }

  local bdir; bdir="$(tvf_clean_backup | tail -1)"
  tvf_head "Debloat ($([ $dry -eq 1 ] && echo 'dry-run' || echo 'aplicando'))  lista: $(basename "$list")"

  local present="$bdir/packages-all.txt"
  local disabled_log="$bdir/tvf-disabled.txt"; : > "$disabled_log"
  local pkg count=0 skipped=0
  while read -r pkg; do
    pkg="$(echo "$pkg" | sed 's/#.*//' | tr -d '[:space:]')"
    [ -n "$pkg" ] || continue
    if tvf_is_protected "$pkg"; then tvf_warn "protegido, pulando: $pkg"; skipped=$((skipped+1)); continue; fi
    grep -qx "package:$pkg" "$present" || continue   # não existe nessa TV
    if [ $dry -eq 1 ]; then
      tvf_say "   [dry] desabilitaria: $pkg"; count=$((count+1)); continue
    fi
    local r; r="$(tvf_adb shell pm disable-user --user 0 "$pkg" 2>&1 | tr -d '\r')"
    if echo "$r" | grep -q "disabled-user"; then
      printf '%s\n' "$pkg" >> "$disabled_log"; tvf_ok "$pkg"; count=$((count+1))
    else
      tvf_warn "falhou: $pkg ($r)"
    fi
  done < "$list"

  if [ $dry -eq 1 ]; then
    tvf_info "dry-run: $count pacotes seriam desabilitados ($skipped protegidos pulados)."
  else
    tvf_ok "$count desabilitados. Restaurar: tvforfun clean restore"
  fi
}

tvf_clean_restore() {
  tvf_need_adb || return 1
  local bdir="${1:-$(cat "$TVF_HOME/last-backup" 2>/dev/null)}"
  local log="$bdir/tvf-disabled.txt"
  [ -f "$log" ] || { tvf_err "nada pra restaurar (sem $log)"; return 1; }
  tvf_head "Restaurando $(wc -l < "$log" | tr -d ' ') pacotes de $(basename "$bdir")"
  local pkg
  while read -r pkg; do
    [ -n "$pkg" ] || continue
    tvf_adb shell pm enable "$pkg" >/dev/null 2>&1 && tvf_ok "$pkg" || tvf_warn "falhou: $pkg"
  done < "$log"
  tvf_adb shell settings put global private_dns_mode opportunistic >/dev/null 2>&1
  tvf_ok "restaurado + DNS off"
}
