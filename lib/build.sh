# lib/build.sh — compila um app de TV SEM gradle (aapt + d8 + apksigner). (sourced)

tvf_android_home() {
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then echo "$ANDROID_HOME"; return; fi
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
    [ -d "$d/platforms" ] && { echo "$d"; return; }
  done
  echo "/opt/homebrew/share/android-commandlinetools"
}

# tvf_build <appdir> [--install] [--launch]
tvf_build() {
  local appdir="" do_install=0 do_launch=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --install) do_install=1 ;;
      --launch)  do_launch=1 ;;
      *) appdir="$1" ;;
    esac; shift
  done
  [ -n "$appdir" ] && [ -f "$appdir/AndroidManifest.xml" ] || { tvf_err "uso: tvforfun build <dir-do-app>"; return 1; }
  appdir="$(cd "$appdir" && pwd)"

  local AH BT AJ name out
  AH="$(tvf_android_home)"
  BT="$AH/build-tools/34.0.0"; AJ="$AH/platforms/android-34/android.jar"
  name="$(basename "$appdir")"; out="$appdir/build"
  [ -f "$AJ" ] || { tvf_err "Android SDK não encontrado em $AH (platform android-34)."; return 1; }
  command -v javac >/dev/null 2>&1 || { tvf_err "falta Java (JDK 17)."; return 1; }

  tvf_head "Compilando $name"
  rm -rf "$out"; mkdir -p "$out/classes"
  javac --release 8 -classpath "$AJ" -d "$out/classes" $(find "$appdir/src" -name '*.java') || return 1
  "$BT/d8" --min-api 21 --lib "$AJ" --output "$out" $(find "$out/classes" -name '*.class') || return 1
  local sflag=""; [ -d "$appdir/res" ] && sflag="-S $appdir/res"
  "$BT/aapt" package -f -M "$appdir/AndroidManifest.xml" $sflag -I "$AJ" -F "$out/app-unaligned.apk" || return 1
  ( cd "$out" && "$BT/aapt" add app-unaligned.apk classes.dex >/dev/null )
  "$BT/zipalign" -f 4 "$out/app-unaligned.apk" "$out/app-aligned.apk"

  local ks="$TVF_HOME/tvforfun.keystore"
  if [ ! -f "$ks" ]; then
    keytool -genkeypair -keystore "$ks" -storepass tvforfun -keypass tvforfun \
      -alias tvforfun -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=TvForFun, O=TvForFun, C=US" >/dev/null 2>&1
  fi
  "$BT/apksigner" sign --ks "$ks" --ks-pass pass:tvforfun --key-pass pass:tvforfun \
    --out "$out/$name.apk" "$out/app-aligned.apk" 2>/dev/null
  rm -f "$out/app-unaligned.apk" "$out/app-aligned.apk" "$out/app-aligned.apk.idsig" 2>/dev/null
  tvf_ok "APK: $out/$name.apk"

  if [ $do_install -eq 1 ]; then
    local pkg; pkg="$(grep -oE 'package="[^"]+"' "$appdir/AndroidManifest.xml" | head -1 | sed 's/package="//;s/"//')"
    tvf_info "instalando na TV..."
    tvf_adb install -r "$out/$name.apk" >/dev/null 2>&1 && tvf_ok "instalado: $pkg" \
      || { tvf_warn "assinatura diferente? reinstalando limpo..."; tvf_adb uninstall "$pkg" >/dev/null 2>&1; tvf_adb install -r "$out/$name.apk" >/dev/null 2>&1 && tvf_ok "instalado: $pkg"; }
    if [ $do_launch -eq 1 ]; then
      local act; act="$(grep -oE 'android:name="\.[A-Za-z0-9_]+"' "$appdir/AndroidManifest.xml" | head -1 | sed 's/.*="//;s/"//')"
      tvf_adb shell am start -n "$pkg/$act" >/dev/null 2>&1 && tvf_ok "aberto na TV: $pkg/$act"
    fi
  fi
}
