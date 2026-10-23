# lib/common.sh — cores, logs, config e checagens compartilhadas.
# (sourced; não executar direto)

export PATH="/opt/homebrew/bin:$PATH"

TVF_HOME="${TVF_HOME:-$HOME/.tvforfun}"
TVF_TARGET_FILE="$TVF_HOME/target"
mkdir -p "$TVF_HOME"

# ---- saída colorida ----
if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_B=$'\033[1m'
  C_BLUE=$'\033[38;5;75m'; C_GREEN=$'\033[38;5;78m'
  C_YEL=$'\033[38;5;221m'; C_RED=$'\033[38;5;203m'
else
  C_RESET=""; C_DIM=""; C_B=""; C_BLUE=""; C_GREEN=""; C_YEL=""; C_RED=""
fi

tvf_say()  { printf '%s\n' "$*"; }
tvf_info() { printf '%s»%s %s\n' "$C_BLUE" "$C_RESET" "$*"; }
tvf_ok()   { printf '%s✓%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
tvf_warn() { printf '%s!%s %s\n' "$C_YEL" "$C_RESET" "$*"; }
tvf_err()  { printf '%s✗%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }
tvf_head() { printf '\n%s%s%s\n' "$C_B" "$*" "$C_RESET"; }

# ---- dependências ----
tvf_need() {
  local bin="$1" hint="${2:-}"
  command -v "$bin" >/dev/null 2>&1 && return 0
  tvf_err "falta '$bin'. ${hint}"
  return 1
}

tvf_need_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    tvf_err "adb não encontrado. Instale: brew install --cask android-platform-tools"
    return 1
  fi
}

# ---- alvo (a TV salva) ----
tvf_target_save() { printf '%s\n' "$1" > "$TVF_TARGET_FILE"; }
tvf_target_get() {
  [ -f "$TVF_TARGET_FILE" ] || { tvf_err "nenhuma TV conectada. Rode: tvforfun connect"; return 1; }
  cat "$TVF_TARGET_FILE"
}

# adb já apontando pro alvo salvo
tvf_adb() {
  local t; t="$(tvf_target_get)" || return 1
  adb -s "$t" "$@"
}

# helpers de ADB disponíveis pra todos os comandos
# shellcheck source=lib/adb.sh
source "$TVF_ROOT/lib/adb.sh"

tvf_banner() {
  if [ -t 1 ] && [ -f "$TVF_ROOT/lib/banner.txt" ]; then
    printf '\n'; cat "$TVF_ROOT/lib/banner.txt"; printf '\n'
  else
    printf '\n%sTvForFun%s — Fire TV / Android TV CLI kit\n\n' "$C_B" "$C_RESET"
  fi
}

tvf_help() {
  tvf_banner
  cat <<EOF
${C_B}TvForFun${C_RESET} — kit de CLI pra Fire TV / Android TV (ADB na Wi-Fi)

  ${C_B}tvforfun connect${C_RESET} [ip]      acha/conecta a TV e salva como alvo
  ${C_B}tvforfun target${C_RESET}            mostra a TV conectada
  ${C_B}tvforfun clean${C_RESET} <sub>       debloat / DNS / backup / restore
  ${C_B}tvforfun stream${C_RESET} <mic|cam|screen>   manda áudio/vídeo/tela do PC pra TV
  ${C_B}tvforfun cast${C_RESET}                 espelha a tela do PC na TV COM áudio
  ${C_B}tvforfun apps${C_RESET} <list|install> catálogo de apps úteis
  ${C_B}tvforfun fun${C_RESET} <games|karaoke>  brincadeiras
  ${C_B}tvforfun new${C_RESET} <nome>        cria um app de TV novo (scaffold)
  ${C_B}tvforfun build${C_RESET} <dir>       compila um app de TV (sem gradle)

Config em: $TVF_HOME
EOF
}
