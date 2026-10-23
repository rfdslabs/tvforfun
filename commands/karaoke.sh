# commands/karaoke.sh — karaokê: música + letra sincronizada na TV + microfone do PC. (sourced)
source "$TVF_ROOT/lib/build.sh"

# tvf_karaoke <song.mp3> <song.lrc> [mic_device]
tvf_karaoke() {
  tvf_need_adb || return 1
  tvf_need ffmpeg "brew install ffmpeg" || return 1
  local mp3="${1:-}" lrc="${2:-}" mic="${3:-0}"
  if [ -z "$mp3" ] || [ ! -f "$mp3" ]; then
    tvf_err "uso: tvforfun fun karaoke <song.mp3> <song.lrc> [mic_device]"
    return 1
  fi
  local ip pkg files
  ip="$(tvf_target_get | cut -d: -f1)" || return 1
  pkg="com.tvforfun.karaoke"
  files="/sdcard/Android/data/$pkg/files"

  # 1) garante o app instalado
  tvf_adb shell pm path "$pkg" >/dev/null 2>&1 || tvf_build "$TVF_ROOT/tvapps/karaoke" --install >/dev/null || return 1

  # 2) envia música e letra pra pasta do app (sem precisar de permissão)
  tvf_info "enviando música e letra pra TV..."
  tvf_adb shell "mkdir -p $files" >/dev/null 2>&1
  tvf_adb push "$mp3" "$files/song.mp3" >/dev/null 2>&1
  if [ -n "$lrc" ] && [ -f "$lrc" ]; then
    tvf_adb push "$lrc" "$files/song.lrc" >/dev/null 2>&1
  fi

  # 3) abre o karaokê (começa a tocar e mostrar a letra)
  tvf_adb shell am force-stop com.amazon.firetv.youtube >/dev/null 2>&1 || true
  tvf_adb shell am force-stop "$pkg" >/dev/null 2>&1 || true
  tvf_adb shell am start -n "$pkg/.KaraokeActivity" >/dev/null 2>&1
  tvf_ok "karaokê aberto na TV — a música já começou"

  # 4) manda seu microfone pra cantar junto
  local hex=138D i
  for i in $(seq 1 20); do
    tvf_adb shell "cat /proc/net/tcp6 2>/dev/null" | tr -d '\r' | grep -qi ":$hex .* 0A " && break
    sleep 0.5
  done
  tvf_ok "cante! (seu microfone :$mic → TV). Ctrl+C pra parar o microfone."
  exec ffmpeg -hide_banner -loglevel warning -fflags nobuffer -flags low_delay \
    -f avfoundation -i ":$mic" -ac 1 -ar 16000 -flush_packets 1 \
    -f s16le "tcp://$ip:$MIC_PORT_KAR"
}
MIC_PORT_KAR=5005
