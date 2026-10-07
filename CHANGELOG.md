# Changelog

All notable changes to Hearing Aid are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-07

First release.

### Added
- **Hearing aid for any earphones**: wired, USB-C, Bluetooth (AirPods, Galaxy Buds and others), LE Audio and Android-compatible hearing aids. Live sound from the phone or headset microphone, about 5 ms of processing delay.
- **Sound processing** built for hearing: a 6-band FFT filter bank (250 Hz–8 kHz), separate gain for each ear and pitch, wide dynamic range compression that softens loud sounds, spectral noise reduction for steady noise, and a peak limiter that never exceeds the chosen maximum loudness.
- **Hearing test**: pure-tone test per ear at six pitches with the user's own earphones; the result is shown as an audiogram and tunes the app automatically.
- **Listen**: volume, left/right balance, situations (Everyday, Conversation, Noisy place, TV & music), microphone choice, live level meters, and a Bluetooth delay warning.
- **Adjust**: per-pitch sliders for both ears or each ear, loud-sound softening, maximum loudness, and the phone's call noise suppression.
- **Captions**: continuous speech-to-text in large adjustable text, with its own speech language (23 languages and variants).
- **Feedback protection**: sound pauses at once if earphones are unplugged, so the loudspeaker never howls.
- **Background**: keeps running with the screen off; the notification shows situation and volume with Quieter / Louder / Turn off buttons; a Quick Settings tile switches it on and off from anywhere; optional start when the app opens; a shortcut to the phone's battery settings.
- **Appearance**: light, dark or same as phone; colour skins Teal, Ocean, Sunset, Forest, Berry, High contrast and Wallpaper colours (Android 12+); three text sizes.
- **19 languages**: English, Persian, Arabic, Kurdish (Sorani and Kurmanji), Turkish, Chinese, German, Russian, Italian, French, Spanish, Portuguese, Hindi, Urdu, Japanese, Korean, Indonesian and Ukrainian, with right-to-left layouts.
- **Help** inside the app (More › Help) and in [docs/HELP.md](docs/HELP.md).
- **About & updates**: version, author, links and license, Copy details for bug reports, and self-updating from GitHub releases with SHA-256 verification.

[Unreleased]: https://github.com/AliMehraei/hearingaid/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/AliMehraei/hearingaid/releases/tag/v1.0.0
