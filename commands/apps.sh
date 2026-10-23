# commands/apps.sh — catálogo e instalação de apps de TV. (sourced)
source "$TVF_ROOT/lib/build.sh"

# pacote declarado no manifest de um app
_tvf_app_pkg() { grep -oE 'package="[^"]+"' "$1/AndroidManifest.xml" 2>/dev/null | head -1 | sed 's/package="//;s/"//'; }

tvf_apps() {
  local sub="${1:-list}"; shift || true
  case "$sub" in
    list)      tvf_apps_list ;;
    install)   tvf_apps_install "$@" ;;
    uninstall) tvf_apps_uninstall "$@" ;;
    *) cat <<EOF
tvforfun apps <sub>
  list                 lista os apps do TvForFun e se estão instalados na TV
  install <nome|all>   compila e instala (ex.: mic, cam, games, karaoke)
  uninstall <nome>     remove da TV
EOF
    ;;
  esac
}

tvf_apps_list() {
  tvf_need_adb || return 1
  tvf_head "Apps TvForFun (em tvapps/)"
  local installed; installed="$(tvf_adb shell 'pm list packages' 2>/dev/null | tr -d '\r')"
  local d name pkg mark
  for d in "$TVF_ROOT"/tvapps/*/; do
    [ -f "$d/AndroidManifest.xml" ] || continue
    name="$(basename "$d")"; pkg="$(_tvf_app_pkg "$d")"
    if echo "$installed" | grep -qx "package:$pkg"; then mark="${C_GREEN}instalado${C_RESET}"; else mark="${C_DIM}não instalado${C_RESET}"; fi
    printf "   %-10s %-26s %b\n" "$name" "$pkg" "$mark"
  done
  tvf_say ""
  tvf_say "   instalar:  tvforfun apps install <nome|all>"
}

tvf_apps_install() {
  tvf_need_adb || return 1
  local which="${1:-}"
  [ -n "$which" ] || { tvf_err "uso: tvforfun apps install <nome|all>"; return 1; }
  local dirs=()
  if [ "$which" = "all" ]; then
    for d in "$TVF_ROOT"/tvapps/*/; do [ -f "$d/AndroidManifest.xml" ] && dirs+=("$d"); done
  else
    [ -d "$TVF_ROOT/tvapps/$which" ] || { tvf_err "app '$which' não existe em tvapps/"; return 1; }
    dirs=("$TVF_ROOT/tvapps/$which")
  fi
  local d
  for d in "${dirs[@]}"; do
    tvf_build "$d" --install || tvf_warn "falhou: $(basename "$d")"
  done
}

tvf_apps_uninstall() {
  tvf_need_adb || return 1
  local which="${1:-}"
  local pkg
  if [ -d "$TVF_ROOT/tvapps/$which" ]; then pkg="$(_tvf_app_pkg "$TVF_ROOT/tvapps/$which")"; else pkg="$which"; fi
  [ -n "$pkg" ] || { tvf_err "uso: tvforfun apps uninstall <nome|pacote>"; return 1; }
  tvf_adb uninstall "$pkg" >/dev/null 2>&1 && tvf_ok "removido: $pkg" || tvf_warn "não estava instalado: $pkg"
}
