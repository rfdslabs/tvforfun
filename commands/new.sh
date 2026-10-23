# commands/new.sh — cria um app de TV novo (scaffold). (sourced)

# tvf_new <nome> [--install]
tvf_new() {
  local name="" install=0
  while [ $# -gt 0 ]; do
    case "$1" in --install) install=1 ;; *) name="$1" ;; esac; shift
  done
  [ -n "$name" ] || { tvf_err "uso: tvforfun new <nome> [--install]"; return 1; }

  local slug seg dir pkg disp
  slug="$(echo "$name" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')"
  [ -n "$slug" ] || { tvf_err "nome inválido"; return 1; }
  seg="$slug"; pkg="com.tvforfun.$seg"
  disp="$name"
  dir="$TVF_ROOT/tvapps/$slug"
  [ -e "$dir" ] && { tvf_err "já existe: $dir"; return 1; }

  tvf_head "Criando app '$disp'  ($pkg)"
  mkdir -p "$dir/src/com/tvforfun/$seg" "$dir/res/mipmap-hdpi" "$dir/res/drawable-xhdpi"

  cat > "$dir/AndroidManifest.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$pkg" android:versionCode="1" android:versionName="1.0">
    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="28" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <application android:label="$disp" android:icon="@mipmap/ic_launcher"
        android:banner="@drawable/banner" android:allowBackup="false">
        <activity android:name=".MainActivity" android:exported="true" android:label="$disp"
            android:launchMode="singleTask"
            android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

  cat > "$dir/src/com/tvforfun/$seg/MainActivity.java" <<EOF
package $pkg;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App de TV gerado pelo TvForFun. Edite à vontade. */
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0A0A0F);
        root.addView(big("$disp", 72, 0xFFFFFFFF, true));
        root.addView(big("made with TvForFun", 28, 0xFF4FC3F7, false));
        setContentView(root);
    }
    private TextView big(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(color); t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif-light",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }
}
EOF

  # ícone + banner
  local F=/System/Library/Fonts/HelveticaNeue.ttc
  local letter; letter="$(echo "$disp" | cut -c1 | tr '[:lower:]' '[:upper:]')"
  if command -v magick >/dev/null 2>&1; then
    magick -size 256x256 -define png:color-type=2 xc:"#15181E" -colorspace sRGB -type TrueColor \
      -gravity center -font "$F" -fill "#4FC3F7" -pointsize 150 -annotate +0+0 "$letter" \
      PNG24:"$dir/res/mipmap-hdpi/ic_launcher.png" 2>/dev/null
    magick -size 320x180 -define png:color-type=2 xc:"#0A0A0F" -colorspace sRGB -type TrueColor \
      -gravity center -font "$F" -fill "#FFFFFF" -pointsize 40 -annotate +0+0 "$disp" \
      PNG24:"$dir/res/drawable-xhdpi/banner.png" 2>/dev/null
  fi

  tvf_ok "app criado em: $dir"
  tvf_say "   editar:  $dir/src/com/tvforfun/$seg/MainActivity.java"
  tvf_say "   compilar+instalar:  tvforfun build $dir --install --launch"

  if [ $install -eq 1 ]; then
    source "$TVF_ROOT/lib/build.sh"
    tvf_build "$dir" --install --launch
  fi
}
