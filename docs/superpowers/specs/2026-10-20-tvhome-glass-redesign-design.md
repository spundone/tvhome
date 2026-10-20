# tvHome glass redesign + SmartTube 2× (TCL T89C)

**Date:** 2026-10-20  
**Device:** TCL 55" T89C (G08 / Smart TV Pro), QLED, Android 14  
**Package:** `com.spandan.tvhome` (default HOME)  
**Status:** Design approved in chat; awaiting spec review before implementation plan

---

## 1. Goal

Make daily TV use feel denser and more intentional: Projectivy-inspired home density, iOS-like liquid glass chrome, always-visible HDMI switching, actionable up-next, smarter remote bindings, then fix SmartTube **2×** playback on **4K60** streams. Home-hub / usage dashboard is explicitly **out of scope** for this cycle.

## 2. Constraints and facts

- Evolve existing launcher in place (Approach 1). Do not make Projectivy default HOME. Keep Projectivy and Netfly installed. Wireless debugging stays on.
- Panel reports 3840×2160; Android exposes a single app mode at **60 Hz** (`alternativeRefreshRates` empty). TCL settings advertise 144 Hz / VRR / Game mode on the picture engine; apps do not see those as Display modes.
- RAM: ~2.4 GB total. Fake glass (translucency + dither + specular) is OK. Avoid continuous full-screen live blur.
- Golden rule unchanged: never uninstall system packages; disable-user + `tools/restore.sh` only.
- Verify with ADB screenshots after each meaningful UI change.

## 3. Phase A: Launcher UX

### 3.1 Layout (top → bottom)

1. **Wallpaper hero**  
   - Load first image from  
     `/sdcard/Android/data/com.spandan.tvhome/files/wallpapers/`  
   - If empty, use a bundled dark QLED-friendly still.  
   - Soft vignette / scrim so glass UI stays readable.

2. **Top-left status**  
   - Greeting, large clock, date.  
   - **Up-next chip** is focusable. OK opens JioHotstar (`in.startv.hotstar`).  
   - Later (same release if cheap): optional trailing `pkg:` / `url:` in `schedule.txt` lines.

3. **Top-right glass pills**  
   - All apps · Home · Wi‑Fi · Settings.  
   - Inputs removed from pills (moved to strip).

4. **Always-on HDMI strip**  
   - Capsules: Live TV · HDMI 1–4 · AirPlay (same keep-filter as today: passthrough + TCL tuner + AirPlay; no soft app inputs).  
   - Focus lifts the capsule; OK tunes / launches.  
   - Visible on clean home (not only behind a dropdown).

5. **Dense pins**  
   - Two rows, ~6–8 tiles per row (config-driven).  
   - Real gaps (~16–24 dp). No edge-kissing tiles or pills.  
   - Expand `dock_*` arrays (or rename to `pin_*`) for more entries.  
   - Keep deep-link pins (ABP / Aaj Tak via SmartTube search URLs).

6. **All apps drawer**  
   - Same open/close behavior; glass styling; BACK returns to clean home.

### 3.2 Liquid glass aesthetic

- Frosted panels: translucent fills + **ordered dither / blue-noise grain** (band-hiding on QLED).  
- Specular rim + soft inner highlight on focused control; spring scale ~1.06.  
- Ambient wash from wallpaper (and focused icon average color) behind clock / active pin.  
- Pref `advanced_glass` (default on). Off = flat translucent chrome, no grain animation.  
- `reduced_motion` still disables springs.

### 3.3 Focus / navigation

Zones: Top (up-next + pills) → HDMI strip → Pin rows → (drawer) Grid.  
UP/DOWN move one zone; LEFT/RIGHT within zone. Remember last focused pin and HDMI index.

### 3.4 Remote rebinds (while tvHome is resumed)

| Key family | Binding |
|---|---|
| TV / Input | Focus HDMI strip; if already focused, cycle next input |
| Guide / DVR | Toggle All apps drawer |
| PROG_RED / GREEN / YELLOW / BLUE | Launch pins 1–4 (defaults: SmartTube, BitChord, Browser, Stremio; `config.xml`) |
| CHANNEL_UP / DOWN | Cycle Live TV ↔ HDMI 1–4 |
| App hotkeys (Netflix, YouTube, etc. if delivered) | Launch matching package; unused hotkeys map to next free pin |
| Assistant | Unchanged (Katniss / Gemini) |
| Volume / Power / Home | Stock |

Document bindings in README. Keys that never reach the app (TCL global overlay / disabled package) stay noted as “system-owned”.

## 4. Phase B: SmartTube 2× on 4K60

**Symptom:** 2× speed on 4K60 streams fails or stutters in SmartTube (`org.smarttube.stable` 32.56).

**Approach:**
1. Reproduce with logcat + SmartTube player settings dump.  
2. Prefer hardware decode; identify format (VP9/AV1/H.264) that breaks at 2×.  
3. Apply safest persistent fix:  
   - Preferred: settings that keep 4K at 1× and sustain 2× (decoder / buffer / format).  
   - Fallback: auto-cap resolution to 1080p **only while speed > 1×**.  
4. Record before/after and undo steps in README.  
5. If SoC-bound, document limitation clearly; do not fake success.

## 5. Phase B adjacent: Gaming / HDMI (light touch)

- Confirm Game mode on the PC/console HDMI (HDMI 2 already shows game-related flags).  
- Do not disable VRR/ALLM-related TCL settings.  
- No claim of Android-side 120/144 Hz for apps; picture engine may still apply Game Master on HDMI.

## 6. Explicitly out of scope (this cycle)

- Home hub / usage dashboard / Home Assistant full dashboard.  
- Uninstalling Projectivy or Netfly.  
- Turning Wireless debugging off.  
- Real iOS-style continuous backdrop blur of the full 4K framebuffer.  
- Rewriting the launcher as a new package.

## 7. Config / files

| Path | Role |
|---|---|
| `launcher/.../values/config.xml` | Pins, color keys, quick-color remote map, glass toggles defaults |
| `.../files/wallpapers/` | User static wallpapers |
| `.../files/schedule.txt` | Up-next events; OK → Hotstar (optional pkg/url later) |
| `tools/build-install.sh` | Build + install + home preference |
| `tools/restore.sh` | Re-enable disabled packages |
| `shots/` | Screenshot verification |

## 8. Success criteria

**Phase A**
- Clean home shows wallpaper, HDMI strip, ≥10 pins across 2 rows, visible gaps.  
- Up-next chip focusable; OK launches JioHotstar.  
- Glass + dither visible; advanced_glass off still usable.  
- Color buttons and CH± do something useful on home.  
- Screenshot walk passes (home, strip, pins, drawer, up-next).

**Phase B**
- Documented repro of 2× 4K60 failure.  
- Working fix or honest SoC limitation with auto-downscale fallback.  
- Undo documented.

## 9. Risks

- Liquid glass overdraw on 2 GB class device → mitigate with advanced_glass toggle and static dither bitmap.  
- Some remote keys swallowed by `com.tcl.globalkeyoverlay` (currently user-disabled) or vendor layer → bind what reaches `onKeyDown`; list gaps in README.  
- SmartTube updates may reset prefs → prefer exportable / re-applyable settings snippet in tools/.

## 10. Implementation order (for the later plan)

1. Spacing + pin density + HDMI strip (structure).  
2. Wallpaper + glass/dither chrome.  
3. Up-next focus + Hotstar launch.  
4. Remote rebinds.  
5. SmartTube 2× investigation and fix.  
6. Light HDMI game-mode check + README.

---

## Spec self-review

- [x] No TBD placeholders for required decisions (hub deferred explicitly).  
- [x] No contradiction with “keep Projectivy / Netfly / debugging”.  
- [x] Phase A vs B vs hub boundaries clear.  
- [x] Wallpaper path, schedule behavior, HDMI filter, glass performance strategy specified.  
- [x] Success criteria are observable on-device.
