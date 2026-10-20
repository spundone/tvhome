# tvHome glass redesign Implementation Plan

> **For agentic workers:** Implement task-by-task. Verify on-device with screencap after each task.

**Goal:** Ship Projectivy-density liquid-glass home (wallpaper, HDMI strip, dense pins, actionable up-next, remote rebinds), then fix SmartTube 2× on 4K60.

**Architecture:** Evolve `com.spandan.tvhome` MainActivity in place; add small helper classes for glass/dither and wallpaper. Keep Projectivy installed/non-default.

**Tech Stack:** Java 17, Android 34 SDK, aapt2/d8 via `tools/build-install.sh`, ADB screenshots.

## Global Constraints

- Never uninstall system packages; disable-user + restore.sh only.
- No continuous full-screen live blur; fake glass + dither only.
- Debugging stays on; Netfly stays; Projectivy stays non-HOME.
- Hub / usage dashboard out of scope.

## Tasks

- [ ] **T1** Structure: remove Inputs pill; always-on HDMI strip; 2-row pins with 16–24dp gaps; expand `config.xml` pins
- [ ] **T2** Wallpaper folder + bundled default; vignette
- [ ] **T3** Liquid glass chrome (frost, dither, specular focus); `advanced_glass` pref
- [ ] **T4** Up-next focusable → launch JioHotstar
- [ ] **T5** Remote rebinds (CH±, color keys, TV/Input, Guide)
- [ ] **T6** SmartTube 2× 4K60 investigate + fix
- [ ] **T7** README update + screenshot verification set
