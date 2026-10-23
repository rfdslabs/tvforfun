# commands/fun.sh — brincadeiras: jogos e karaokê. (sourced)
source "$TVF_ROOT/lib/build.sh"

_tvf_fun_launch() {
  local dir="$1" pkg="$2" act="$3"
  if ! tvf_adb shell pm path "$pkg" >/dev/null 2>&1; then
    tvf_build "$dir" --install >/dev/null || return 1
  fi
  tvf_adb shell am force-stop com.amazon.firetv.youtube >/dev/null 2>&1 || true
  tvf_adb shell am start -n "$pkg/$act" >/dev/null 2>&1 && tvf_ok "aberto na TV: $pkg"
}

tvf_fun() {
  tvf_need_adb || return 1
  local sub="${1:-}"; shift || true
  case "$sub" in
    games)
      tvf_head "Jogos (Math · Words · Colors)"
      _tvf_fun_launch "$TVF_ROOT/tvapps/games" com.tvforfun.games .MenuActivity
      ;;
    karaoke)
      source "$TVF_ROOT/commands/karaoke.sh"; tvf_karaoke "$@"
      ;;
    *) cat <<EOF
tvforfun fun <sub>
  games                 abre os minijogos na TV (Math, Words, Colors)
  karaoke <mp3> <lrc>   karaokê: letra sincronizada na TV + seu microfone
EOF
    ;;
  esac
}
