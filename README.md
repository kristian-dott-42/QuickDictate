# QuickDictate

A lightweight, fast push-to-talk dictation app for macOS — a free, self-hosted alternative to Wispr Flow.

Hold the `fn` key, speak, release. Your speech is transcribed by Whisper, cleaned up by an LLM, and pasted at your cursor — in any app.

- ⚡ **Fast**: ~1 second end-to-end with Groq (Whisper-large-v3-turbo + Llama-3.1-8B)
- 🪶 **Lightweight**: one ~250 KB Swift binary, no Electron, no Python, no dependencies
- 🔒 **Private**: your audio goes only to the provider you configure
- 🎯 **Smart cleanup**: removes filler words, fixes punctuation and capitalisation
- 🫧 **Visible**: floating bubble shows recording / transcribing, plus a ✅ on success or a clear failure state
- 📋 **Clipboard-safe**: your existing clipboard is restored after each paste
- 💸 **Cheap**: heavy daily use costs pennies a week with Groq's free tier

## Install

Requires macOS 13+ and Xcode Command Line Tools (`xcode-select --install`).

```bash
git clone https://github.com/kristian-dott-42/QuickDictate.git
cd QuickDictate
./install.sh
```

Then add your API key to `~/.dictate/.env` (copied from `.env.example` during install) and follow the on-screen prompts to grant Microphone + Accessibility permissions.

## Usage

- **Hold `fn`** — recording starts; a red pulsing bubble appears top-centre of screen
- **Release `fn`** — transcription begins (orange bubble), then the cleaned text pastes at your cursor

## Configuration

Edit `~/.dictate/.env`. Changes take effect on the next recording — no restart needed.

| Variable | Default | Purpose |
|---|---|---|
| `STT_URL` | OpenAI | Transcription endpoint |
| `STT_KEY` | — | API key for transcription |
| `STT_MODEL` | `whisper-1` | Transcription model |
| `LLM_URL` | OpenAI | Cleanup endpoint |
| `LLM_KEY` | — | API key for cleanup |
| `LLM_MODEL` | `gpt-4o-mini` | Cleanup model |
| `WHISPER_PROMPT` | — | Vocabulary hint — names, brands, jargon |
| `CLEANUP_PROMPT` | built-in | Override the cleanup instructions entirely |
| `HOTKEY_KEYCODE` | `63` (fn) | Push-to-talk key (e.g. `61` = right option) |
| `HOTKEY_EXCLUSIVE` | `false` | `true` consumes the key (active tap) so no other app sees it — see safety note below |

`.env` is re-read before every dictation, so changes to keys, models, or prompts take effect immediately — no restart. (`HOTKEY_*` are read once at launch, so restart the app after changing those.)

### Reliability & safety

By default the hotkey is observed with a **listen-only** `CGEventTap`: the system delivers key events to QuickDictate but never waits for it, so it can never delay, drop, or consume your keystrokes — it **cannot freeze the keyboard**, whatever the app is doing. The tap is also serviced on its own dedicated thread, keeping key delivery independent of any UI work. The paste is synthesised directly via `CGEvent`, with no AppleScript or System Events dependency.

**Exclusive mode (`HOTKEY_EXCLUSIVE=true`)** upgrades to an *active* tap so the hotkey is consumed and no other app (e.g. another dictation tool) sees it. This is opt-in because an active tap sits in the live event path. QuickDictate runs it on a dedicated thread and never blocks in the callback, so a freeze should not happen — but if the app ever does hang in this mode, force-quit it from **Activity Monitor** (or `pkill -f QuickDictate` over SSH) to restore the key. Most people don't need exclusive mode.

### Recommended: Groq

Free tier handles personal use without ever paying. ~10× faster than OpenAI's Whisper.

```env
GROQ_API_KEY=gsk_...
STT_URL=https://api.groq.com/openai/v1/audio/transcriptions
STT_KEY=gsk_...
STT_MODEL=whisper-large-v3-turbo
LLM_URL=https://api.groq.com/openai/v1/chat/completions
LLM_KEY=gsk_...
LLM_MODEL=llama-3.3-70b-versatile
```

Get a key at [console.groq.com](https://console.groq.com).

> **Note on the cleanup model:** use a capable model like `llama-3.3-70b-versatile`.
> Small models (e.g. `llama-3.1-8b-instant`) sometimes *respond* to dictation that
> sounds like a request instead of just cleaning it up. Groq runs the 70B fast
> enough that there's no real latency cost.

### Vocabulary tuning

Add your specific terms to `WHISPER_PROMPT` for better accuracy on names and jargon:

```env
WHISPER_PROMPT=Acme Corp, Jane Smith, Kubernetes, gRPC, Prometheus,
```

## How it works

1. **Hotkey** — an active `CGEventTap` at the head of the event stream watches for `fn` press/release
2. **Recording** — `AVAudioRecorder` captures 16 kHz mono PCM
3. **Focus capture** — remembers the frontmost app at the moment recording starts
4. **Transcription** — POST audio to Whisper endpoint
5. **Cleanup** — POST raw transcript to LLM with a cleanup prompt
6. **Paste** — re-activate the captured app, set the clipboard, send `⌘V` via `CGEvent`, then restore your previous clipboard

The whole app is a single ~500 line Swift file with no external dependencies.

## Cost

Typical 10-second dictation:

- **Groq**: ~$0.0002 (a fifth of a tenth of a cent)
- **OpenAI**: ~$0.001 (a tenth of a cent)

Heavy use (100 dictations a day) is roughly $0.50/month on Groq or $3-5/month on OpenAI.

## Troubleshooting

**Nothing pastes after transcription:**
Check `~/.dictate/dictate.log`. If you see `paste error: ... not allowed to send keystrokes`, Accessibility was revoked. Re-grant it:

```bash
tccutil reset Accessibility com.kristian.quickdictate
# toggle on in System Settings → Privacy & Security → Accessibility
pkill -f QuickDictate && open ~/Applications/QuickDictate.app
```

**Hotkey does nothing:**
Same as above — Accessibility permission was lost.

**"Nothing heard":**
Spoken too quietly or wrong mic. Check System Settings → Sound → Input.

**Whisper mishears names:**
Add them to `WHISPER_PROMPT` in `.env`.

**fn key opens the emoji picker instead:**
System Settings → Keyboard → "Press fn key to" → **Do Nothing**.

**After re-running `install.sh`:**
The binary changes so macOS revokes Accessibility. Re-grant as above.

## Privacy

QuickDictate runs entirely on your machine. Audio is sent only to the provider URL you configure (OpenAI or Groq by default). No telemetry, no analytics, no accounts. Your API key is stored locally in `~/.dictate/.env`.

## License

MIT — see [LICENSE](LICENSE).
