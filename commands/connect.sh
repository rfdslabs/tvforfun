# commands/connect.sh — acha/conecta a TV e salva como alvo. (sourced)
source "$TVF_ROOT/lib/adb.sh"

tvf_connect() {
  tvf_need_adb || return 1
  local ip="${1:-}"

  if [ -z "$ip" ]; then
    tvf_head "Procurando TVs na rede"
    local rows; rows="$(tvf_discover)"
    local open; open="$(echo "$rows" | awk -F'\t' '$3=="5555:open"{print}')"
    local n; n="$(printf '%s\n' "$open" | grep -c . || true)"
    if [ "$n" -eq 0 ]; then
      tvf_warn "nenhuma TV com ADB aberto (porta 5555)."
      tvf_say  "Ligue na TV: Settings → My Fire TV → Developer options → ADB debugging → ON"
      tvf_say  "Candidatos vistos (fabricante pelo MAC):"
      echo "$rows" | awk -F'\t' '$2!="?"{printf "   %s  %s  (%s)\n",$1,$2,$3}'
      tvf_say  "Depois: tvforfun connect <ip>"
      return 1
    elif [ "$n" -gt 1 ]; then
      tvf_warn "mais de uma TV com ADB aberto. Escolha uma:"
      echo "$open" | awk -F'\t' '{printf "   %s  %s\n",$1,$2}'
      tvf_say  "Rode: tvforfun connect <ip>"
      return 1
    fi
    ip="$(printf '%s\n' "$open" | head -1 | cut -f1)"
    tvf_ok "achei: $ip"
  fi

  tvf_head "Conectando em $ip:$ADB_PORT"
  local state; state="$(tvf_connect_ip "$ip")"
  case "$state" in
    device) : ;;
    unauthorized)
      tvf_warn "aceite 'Allow USB debugging?' na TV (marque 'Always allow') e rode de novo."
      return 1 ;;
    offline)
      tvf_warn "TV respondeu 'offline' — reabra a TV / religue o ADB e tente de novo."
      return 1 ;;
    *)
      tvf_err "não conectou. Confirme o IP e o ADB ligado na TV."
      return 1 ;;
  esac

  tvf_target_save "$ip:$ADB_PORT"
  local model rel sdk mfr
  model="$(tvf_getprop ro.product.model)"
  rel="$(tvf_getprop ro.build.version.release)"
  sdk="$(tvf_getprop ro.build.version.sdk)"
  mfr="$(tvf_getprop ro.product.manufacturer)"
  tvf_ok "conectado e salvo como alvo"
  tvf_say "   TV:      $mfr $model"
  tvf_say "   Android: $rel (SDK $sdk)"
  tvf_say "   ADB:     $ip:$ADB_PORT"
}

tvf_target_show() {
  local t; t="$(tvf_target_get)" || return 1
  local state; state="$(adb devices | awk -v t="$t" '$1==t{print $2}')"
  tvf_say "alvo: $t  (${state:-desconectado})"
}
