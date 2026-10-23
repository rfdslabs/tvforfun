<div align="center">

<img src="docs/banner.png" alt="TvForFun — Fire TV / Android TV CLI kit" width="820">

<br><br>

**A hacker's command-line kit for your Fire TV / Android TV.**

Point it at a TV on your Wi-Fi and: strip the ads, install useful apps, stream your
PC's mic/webcam/screen to the big screen, and build your own TV apps — all from the terminal,
over ADB, no Play Store, no root.

Compiles native Android TV apps **without Gradle** (just `aapt` + `d8` + `apksigner`) and
pushes them over ADB in one command.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform: Fire TV / Android TV](https://img.shields.io/badge/platform-Fire%20TV%20%2F%20Android%20TV-blue.svg)](#supported-tvs)
[![Shell](https://img.shields.io/badge/built%20with-bash%20%2B%20adb-1f2430.svg)](#)
[![No Gradle](https://img.shields.io/badge/build-no%20gradle-ff4fa3.svg)](#build-your-own-tv-app)

</div>

---

## Why

Smart TVs ship full of ads, tracking, and junk you can't remove — and the only way to put
*your own* stuff on the screen is a locked-down app store. TvForFun treats the TV like what
it is: an Android box with a debug bridge. One CLI to clean it, extend it, and play with it.

> ⚠️ **For technical users.** You run commands in a terminal. It talks to the TV over ADB.
> Everything it changes is reversible, but you should understand what each command does.

## Features

- **🧹 Clean** — disable manufacturer ads / telemetry / tracking (`pm disable-user`, never
  uninstalled), set an ad-blocking DNS, with automatic backup and one-command restore. A
  protected-package guard refuses anything that could brick the TV.
- **📦 Apps** — build and install TvForFun's own app suite (and your own) over ADB.
- **📡 Stream** — send your PC's **microphone**, **webcam**, or **screen** to the TV live,
  low latency, over your LAN.
- **🎉 Fun** — kid-friendly mini-games (math / reading / colors) and **karaoke** (synced
  lyrics on the TV + your mic).
- **🛠️ Framework** — `tvforfun new <name>` scaffolds, compiles, and installs a brand-new
  Android TV app in one command. Gradle-free build pipeline you can read top to bottom.

## Supported TVs

| Platform | Clean (debloat) | Apps / Stream / Fun |
|---|---|---|
| **Fire TV** (Fire OS 6/7/8) | ✅ | ✅ (apps install & run; Fire OS keeps its own home) |
| **Android TV / Google TV** | ⚠️ varies by brand | ✅ |
| Samsung (Tizen) · LG (webOS) · Vizio (SmartCast) | ❌ | ❌ (different OS, no ADB `pm`) |

The debloat list shipped in `data/firetv-ads.txt` targets Fire OS 7. Other devices: bring your
own list (any text file of package names); the protected-package guard still applies.

---

## Requirements

macOS (the stream scripts use `avfoundation`; Linux support is easy to add). Install the toolchain with Homebrew:

```bash
brew install --cask android-platform-tools      # adb
brew install --cask temurin                      # Java 17 (JDK)
brew install --cask android-commandlinetools     # Android SDK tools
brew install ffmpeg imagemagick                  # streaming + app icons

# Android SDK bits used by the gradle-free build:
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

TvForFun auto-detects the SDK at `/opt/homebrew/share/android-commandlinetools` or
`~/Library/Android/sdk`. Override with `export ANDROID_HOME=...`.

---

## 📺 Set up the TV (enable ADB)

This is the part that trips people up. You need **ADB debugging over the network** turned on.

### Fire TV (Amazon)

1. **Settings → My Fire TV → About** → highlight the **device name** (or **Build**) and press
   **Select / OK seven times** until it says *"You are now a developer."*
2. Go back → **Developer options** → turn **ADB debugging** → **ON**.
   (On some models it's called **Network debugging** or **USB debugging** — turn on what you see.)
3. If prompted, also enable **Apps from Unknown Sources**.
4. Leave the TV on. Note its IP under **Settings → Network** (same Wi-Fi as your computer).

### Android TV / Google TV (Sony, TCL, Xiaomi, Nvidia Shield, Chromecast with Google TV, …)

1. **Settings → Device Preferences → About** → scroll to **Build** and press **Select 7 times**
   → *"You are now a developer."*
2. Back → **Developer options** → turn on **USB debugging** (and **Network debugging** / **ADB
   debugging** if shown).
3. Some Google TV sets only offer **Wireless debugging** — enable it, then pair with a code
   (see Troubleshooting).

### First connection

```bash
./tvforfun connect            # scans your LAN for a TV with ADB open
# or, if you know the IP:
./tvforfun connect 192.168.0.40
```

The **first** time, the TV shows **"Allow USB debugging?"** — check **"Always allow from this
computer"** and accept. Then re-run `connect`. It saves the TV as your target so later commands
just work.

### 🔒 Turn ADB back OFF when you're done

Network ADB (port 5555) is **plaintext on your LAN** — any device on the network can get a shell
while it's on. When you finish:

- On the TV: **Settings → (My Fire TV / Device Preferences) → Developer options → ADB debugging → OFF**.
- On Fire TV, also **revoke USB debugging authorizations** in the same menu if you want to be thorough.
- Network ADB usually **does not survive a reboot** anyway, so rebooting the TV closes it too.

Verify it's closed:
```bash
nc -z -G2 <tv-ip> 5555 && echo "still open" || echo "closed"
# On macOS, trust only "Connection refused" / "closed" — "No route to host" can be a privacy block.
```

---

## Quickstart

```bash
./tvforfun connect                      # find & connect your TV
./tvforfun clean backup                 # snapshot packages & settings first
./tvforfun clean debloat --dry-run      # see what WOULD be disabled
./tvforfun clean debloat                # disable ads/telemetry (reversible)
./tvforfun clean dns on                 # ad-blocking DNS (AdGuard)

./tvforfun apps install games           # build + install the mini-games
./tvforfun fun games                    # open them on the TV

./tvforfun stream cam                   # your webcam, live on the TV (Ctrl+C to stop)
./tvforfun stream mic                   # your mic → TV speakers

./tvforfun new MyThing --install        # scaffold + build + install a new TV app
```

Undo everything the debloat did:
```bash
./tvforfun clean restore
```

---

## Command reference

```
tvforfun connect [ip]                 find/connect a TV, save as target
tvforfun target                       show the connected TV

tvforfun clean backup                 save packages/settings/getprop
tvforfun clean dns on|off             ad-blocking Private DNS (AdGuard)
tvforfun clean debloat [list] [--dry-run]   disable ads/telemetry (default: Fire TV list)
tvforfun clean restore [backupdir]    re-enable everything the last debloat disabled + DNS off

tvforfun apps list                    kit apps and whether they're installed
tvforfun apps install <name|all>      compile + install (mic, cam, games, karaoke, …)
tvforfun apps uninstall <name>

tvforfun stream mic [idx]             PC microphone → TV speakers   (idx 0=external, 1=MacBook)
tvforfun stream cam [idx]             PC webcam → TV screen         (idx 0=external, 2=MacBook)
tvforfun stream screen                PC screen → TV

tvforfun fun games                    mini-games (Math, Words, Colors)
tvforfun fun karaoke <mp3> <lrc> [mic_idx]   synced lyrics + your mic

tvforfun new <Name> [--install]       scaffold a new TV app
tvforfun build <dir> [--install] [--launch]  compile an app (no Gradle)
```

List your PC's audio/video device indexes with:
```bash
ffmpeg -f avfoundation -list_devices true -i ""
```

## How the streaming works

The TV app is the **server** (opens a TCP port); `ffmpeg` on your PC connects as the client.

- **mic / karaoke** — raw PCM (16-bit, 16 kHz, mono) over TCP → `AudioTrack` on the TV. ~0.2 s latency.
- **cam / screen** — MJPEG over TCP; the app decodes each JPEG frame and draws it fullscreen.

No cloud, no accounts — just your LAN.

## Karaoke

```bash
./tvforfun fun karaoke song.mp3 song.lrc 0
```
Pushes the song + an [LRC](https://en.wikipedia.org/wiki/LRC_(file_format)) lyrics file to the
app's folder, opens karaoke on the TV (music plays, lyrics scroll in sync with `[mm:ss.xx]`
timestamps), and streams your mic so you can sing over it.

## Build your own TV app

```bash
./tvforfun new PartyClock --install
# → creates tvapps/partyclock/ (manifest, Activity, icon, banner), builds, installs, launches.
# edit tvapps/partyclock/src/com/tvforfun/partyclock/MainActivity.java, then:
./tvforfun build tvapps/partyclock --install --launch
```

The build is plain shell: `javac` → `d8` → `aapt` → `zipalign` → `apksigner`. Read
`lib/build.sh` — there's no magic.

---

## Project layout

```
tvforfun            entry point (dispatcher)
lib/
  common.sh         colors, logging, config, target
  adb.sh            TV discovery + connection
  build.sh          gradle-free APK build
commands/           connect · clean · apps · stream · fun · karaoke · new
data/
  firetv-ads.txt    debloat list (Fire TV)
  protected.txt     packages that must NEVER be disabled (anti-brick guard)
tvapps/             the TV-side apps (com.tvforfun.*): mic · cam · games · karaoke
```

Per-user state (target, keystore, backups) lives in `~/.tvforfun/` — never in the repo.

## Safety

- `clean` only **disables** packages (`pm disable-user`) — nothing is uninstalled, and
  `clean restore` re-enables everything.
- `debloat` refuses any package listed in `data/protected.txt`, so it won't take out the
  launcher, SystemUI, package installer, Play services, etc.
- Always run `clean backup` (debloat does this automatically) before changing anything.
- Turn ADB off on the TV when you're done (see above).

## Troubleshooting

- **`connect` finds nothing** — ADB debugging isn't on, or the TV is asleep / on another
  Wi-Fi band. Enable it, keep the TV awake, and try `./tvforfun connect <ip>` with the IP from
  the TV's network settings.
- **`unauthorized`** — accept the "Allow USB debugging?" dialog on the TV (check "Always allow").
- **Port 5555 closed after reboot** — normal on Fire TV; toggle ADB debugging off/on again.
- **App streams but no sound/video** — the TV app must stay in the **foreground**; if the TV
  opens something else (e.g. YouTube), the socket drops — reopen the app and re-run the stream.
- **macOS says "No route to host" from adb/nc** — that's the Local Network privacy prompt, not
  the TV. Grant your terminal local-network access.
- **Google TV only has Wireless debugging** — enable it, then:
  `adb pair <ip>:<pair-port>` (code from the TV) → `adb connect <ip>:<port>` → `adb tcpip 5555`.

## Contributing

Issues and PRs welcome. Good first contributions: debloat lists for more devices
(drop a `data/<device>.txt`), Linux stream scripts (swap `avfoundation` for `v4l2`/`pulse`),
and new `tvapps/`.

## License

MIT — see [LICENSE](LICENSE).

> TvForFun talks to a debug interface Amazon/Google leave open on purpose. It doesn't bypass any
> DRM or security control. Use it on TVs you own. You're responsible for what you disable.
