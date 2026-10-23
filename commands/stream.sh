# commands/stream.sh — manda áudio/vídeo/tela do PC pra TV. (sourced)
source "$TVF_ROOT/lib/build.sh"

tvf_target_ip() { tvf_target_get | cut -d: -f1; }

# espera uma porta (hex) entrar em LISTEN na TV (~12s)
tvf_wait_listen() {
  local hex="$1" i
  for i in $(seq 1 24); do
    if tvf_adb shell "cat /proc/net/tcp6 2>/dev/null" | tr -d '\r' | grep -qi ":$hex .* 0A "; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# garante o app instalado e aberto
_tvf_launch_app() {
  local dir="$1" pkg="$2" act="$3"
  if ! tvf_adb shell pm path "$pkg" >/dev/null 2>&1; then
    tvf_info "instalando $pkg..."
    tvf_build "$dir" --install >/dev/null || return 1
  fi
  tvf_adb shell am force-stop com.amazon.firetv.youtube >/dev/null 2>&1 || true
  tvf_adb shell am start -n "$pkg/$act" >/dev/null 2>&1
}

tvf_stream() {
  tvf_need_adb || return 1
  tvf_need ffmpeg "instale: brew install ffmpeg" || return 1
  local kind="${1:-}"; shift || true
  local dev="${1:-}"
  local ip; ip="$(tvf_target_ip)" || return 1

  case "$kind" in
    mic)
      local d="${dev:-0}"   # 0 = microfone externo (ex. USB); 1 = MacBook
      tvf_head "Stream: microfone (device :$d) → TV $ip:5005"
      _tvf_launch_app "$TVF_ROOT/tvapps/mic" com.tvforfun.mic .MicActivity || return 1
      tvf_wait_listen 138D || { tvf_err "o app não abriu a porta 5005 (ele saiu de foco?)"; return 1; }
      tvf_ok "conectando áudio… (Ctrl+C pra parar)"
      exec ffmpeg -hide_banner -loglevel warning -fflags nobuffer -flags low_delay \
        -f avfoundation -i ":$d" -ac 1 -ar 16000 -flush_packets 1 \
        -f s16le "tcp://$ip:5005"
      ;;
    cam)
      local d="${dev:-0}"   # 0 = webcam externa; 2 = câmera do MacBook
      tvf_head "Stream: câmera (device $d) → TV $ip:5006"
      _tvf_launch_app "$TVF_ROOT/tvapps/cam" com.tvforfun.cam .CamActivity || return 1
      tvf_wait_listen 138E || { tvf_err "o app não abriu a porta 5006"; return 1; }
      tvf_ok "transmitindo vídeo… (Ctrl+C pra parar)"
      exec ffmpeg -hide_banner -loglevel warning \
        -f avfoundation -framerate 30 -i "$d" \
        -vf "scale=960:-2,format=yuvj420p" -r 15 -q:v 7 \
        -f mjpeg "tcp://$ip:5006"
      ;;
    screen)
      local d="${dev:-Capture screen 0}"
      tvf_head "Stream: tela do PC → TV $ip:5006"
      _tvf_launch_app "$TVF_ROOT/tvapps/cam" com.tvforfun.cam .CamActivity || return 1
      tvf_wait_listen 138E || { tvf_err "o app não abriu a porta 5006"; return 1; }
      tvf_ok "espelhando a tela… (Ctrl+C pra parar)"
      exec ffmpeg -hide_banner -loglevel warning \
        -f avfoundation -framerate 15 -i "$d" \
        -vf "scale=1280:-2,format=yuvj420p" -r 12 -q:v 8 \
        -f mjpeg "tcp://$ip:5006"
      ;;
    *)
      cat <<EOF
tvforfun stream <tipo> [device]
  mic [idx]     microfone do PC → som da TV      (idx padrão 0; MacBook = 1)
  cam [idx]     webcam do PC → vídeo na TV       (idx padrão 0; MacBook = 2)
  screen        tela do PC → TV
Veja os índices:  ffmpeg -f avfoundation -list_devices true -i ""
EOF
      ;;
  esac
}
