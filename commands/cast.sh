# commands/cast.sh — espelha a tela do PC na TV COM áudio (Chromecast caseiro). (sourced)
source "$TVF_ROOT/lib/build.sh"

# índice de um device avfoundation por seção (video|audio) e regex de nome (portável)
_tvf_av_index() {
  local section="$1" rx="$2"
  ffmpeg -hide_banner -f avfoundation -list_devices true -i "" 2>&1 \
    | awk -v sec="$section" '
        /AVFoundation video devices/ {cur="video"; next}
        /AVFoundation audio devices/ {cur="audio"; next}
        cur==sec {print}' \
    | grep -iE "$rx" \
    | grep -oE '\[[0-9]+\]' | head -1 | tr -d '[]'
}

tvf_wait_port() { # hex, até ~12s
  local hex="$1" i
  for i in $(seq 1 24); do
    tvf_adb shell "cat /proc/net/tcp6 2>/dev/null" | tr -d '\r' | grep -qi ":$hex .* 0A " && return 0
    sleep 0.5
  done; return 1
}

tvf_cast() {
  tvf_need_adb || return 1
  tvf_need ffmpeg "brew install ffmpeg" || return 1
  local no_audio=0 screen_rx="capture screen 0"
  while [ $# -gt 0 ]; do
    case "$1" in --no-audio) no_audio=1 ;; *) screen_rx="$1" ;; esac; shift
  done
  local ip; ip="$(tvf_target_get | cut -d: -f1)" || return 1

  # descobre devices
  local vid aud
  vid="$(_tvf_av_index video "$screen_rx")"
  [ -n "$vid" ] || { tvf_err "não achei a tela ('$screen_rx') no ffmpeg. Veja: ffmpeg -f avfoundation -list_devices true -i \"\""; return 1; }
  if [ $no_audio -eq 0 ]; then
    aud="$(_tvf_av_index audio 'blackhole|loopback|soundflower|aggregate|multi-output')"
  fi

  tvf_head "Cast: tela do PC → TV $ip  (vídeo :$vid$([ -n "$aud" ] && echo ", áudio :$aud" || echo ", sem áudio"))"

  # app
  if ! tvf_adb shell pm path com.tvforfun.cast >/dev/null 2>&1; then
    tvf_build "$TVF_ROOT/tvapps/cast" --install >/dev/null || return 1
  fi
  tvf_adb shell am force-stop com.amazon.firetv.youtube >/dev/null 2>&1 || true
  tvf_adb shell am start -n com.tvforfun.cast/.CastActivity >/dev/null 2>&1
  tvf_wait_port 138E || { tvf_err "o app não abriu a porta de vídeo (ficou em foco?)"; return 1; }

  if [ -z "$aud" ] && [ $no_audio -eq 0 ]; then
    tvf_warn "nenhum dispositivo de loopback de áudio encontrado — vai só o VÍDEO."
    tvf_say  "Pra ter som: instale o BlackHole (brew install blackhole-2ch), crie um"
    tvf_say  "'Multi-Output Device' (BlackHole + seus alto-falantes) no Audio MIDI Setup,"
    tvf_say  "selecione-o como saída do Mac, e rode o cast de novo."
  fi

  # áudio em background (se houver), vídeo em primeiro plano
  local apid=""
  if [ -n "$aud" ]; then
    tvf_wait_port 138D >/dev/null 2>&1
    ffmpeg -hide_banner -loglevel error -fflags nobuffer -flags low_delay \
      -f avfoundation -i ":$aud" -ac 1 -ar 16000 -flush_packets 1 \
      -f s16le "tcp://$ip:5005" &
    apid=$!
  fi
  # garante matar o áudio ao sair do vídeo (Ctrl+C)
  trap '[ -n "$apid" ] && kill "$apid" 2>/dev/null' INT TERM EXIT

  tvf_ok "espelhando! (Ctrl+C pra parar)"
  ffmpeg -hide_banner -loglevel warning \
    -f avfoundation -framerate 15 -capture_cursor 1 -i "$vid" \
    -vf "scale=1280:-2,format=yuvj420p" -r 12 -q:v 8 \
    -f mjpeg "tcp://$ip:5006"

  [ -n "$apid" ] && kill "$apid" 2>/dev/null
  trap - INT TERM EXIT
}
