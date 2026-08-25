# QuickDictate

A lightweight, fast push-to-talk dictation app for macOS — a free, self-hosted alternative to Wispr Flow.

Hold the `fn` key, speak, release. Your speech is transcribed by Whisper, cleaned up by an LLM, and pasted at your cursor — in any app.

- ⚡ **Fast**: ~1 second end-to-end with Groq (Whisper-large-v3-turbo + Llama-3.3-70B)
- 🪶 **Lightweight**: one ~250 KB Swift binary, no Electron, no Python, no dependencies
- 🔒 **Private**: your audio goes only to the provider you configure
- 🎯 **Smart cleanup**: removes filler words, fixes punctuation and capitalisation
- 🫧 **Visible**: floating bubble shows recording / transcribing, plus a ✅ on success — and on failure it names the cause ("No connection", "Key rejected", "Rate limited") rather than guessing
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

### Recommended: one-time signing certificate

QuickDictate is a locally-compiled app. If it's **ad-hoc signed**, macOS gives it a new code identity on every rebuild and **silently drops your Accessibility/Microphone grants each time you re-run `install.sh`** (symptom: the hotkey stops working and `dictate.log` shows `Accessibility trusted: false`, even though the toggle looks on).

To make the grants stick, create a self-signed code-signing certificate **once**:

1. Open **Keychain Access** → menu **Certificate Assistant → Create a Certificate…**
2. **Name:** `QuickDictate Local`
3. **Identity Type:** Self Signed Root
4. **Certificate Type:** Code Signing → **Create**

`install.sh` detects this certificate automatically and signs with it, so your permissions persist across rebuilds. (Override the name with `QUICKDICTATE_SIGN_IDENTITY` if you prefer a different one.)

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
| `HOTKEY_EXCLUSIVE` | `false` | `true` consumes the hotkey so no other app sees it — see safety note below |

`.env` is re-read before every dictation, so changes to keys, models, or prompts take effect immediately — no restart. (`HOTKEY_*` are read once at launch, so restart the app after changing those.)

### Reliability & safety

The hotkey is captured with a `CGEventTap` serviced on its **own dedicated thread** — separate from the main thread that draws the bubble, activates apps, and writes the clipboard. Because key delivery never shares a run loop with UI work, the app **cannot freeze the keyboard** the way an event tap on the main run loop can. The callback also never blocks: it just notes the key and returns immediately. The paste is synthesised directly via `CGEvent`, with no AppleScript or System Events dependency.

Only **Accessibility** permission is needed (the same one used to paste via ⌘V) — QuickDictate deliberately avoids a listen-only tap, which would require the separate *Input Monitoring* permission.

By default (`HOTKEY_EXCLUSIVE=false`) the hotkey is observed but passed straight through, so it keeps working normally everywhere else. Set `HOTKEY_EXCLUSIVE=true` only if you need to stop other apps (e.g. another dictation tool) from also seeing the key — it then consumes the hotkey. Either way, if the app ever misbehaves you can force-quit it from **Activity Monitor** (or `pkill -f QuickDictate` over SSH).

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

**A red bubble with a failure message:**
The bubble names which stage failed, so you can go straight to the fix. Full
detail for every one of these is written to `~/.dictate/dictate.log`.

| Bubble | What happened | Fix |
|---|---|---|
| `No speech` | The transcription succeeded but returned nothing — the recording was silent | Wrong or muted input device; check System Settings → Sound → Input |
| `No connection` | The request never reached the endpoint | **VPN or proxy** intercepting the traffic, or you're offline |
| `Timed out` | No reply within 30s | Slow link, or the provider is struggling |
| `Key rejected` | 401/403 | `STT_KEY` in `~/.dictate/.env` is wrong, expired or rotated |
| `Rate limited` | 429 | Free-tier quota — wait, or move to a paid tier |
| `Request refused` | 400/422 | Usually a retired or misspelled `STT_MODEL` |
| `Endpoint 404` | 404 | `STT_URL` points somewhere that isn't a transcription endpoint |
| `Provider down` | 5xx | Their side — check the provider's status page |
| `Bad response` | 2xx with an unrecognised body | Often a captive portal or proxy returning HTML |
| `Config error` | Bad `STT_URL`, or the recording couldn't be read | Check `.env` |

A `No connection` right after everything worked fine is nearly always a VPN
that came up in the background.

**Whisper mishears names:**
Add them to `WHISPER_PROMPT` in `.env`.

**fn key opens the emoji picker instead:**
System Settings → Keyboard → "Press fn key to" → **Do Nothing**.

**After re-running `install.sh` the hotkey stops working / `Accessibility trusted: false`:**
This happens when the app is **ad-hoc signed** — each rebuild gets a new code
identity, so macOS drops the grant (the toggle still *looks* on, but applies to
the old binary). Fix it permanently with the one-time signing certificate under
[Install](#recommended-one-time-signing-certificate). To recover right now:

```bash
pkill -f QuickDictate
tccutil reset Accessibility com.kristian.quickdictate
open ~/Applications/QuickDictate.app   # grant Accessibility when prompted
```

Toggling the switch off/on is often not enough — remove the entry with the **–**
button and re-add it, or use the `tccutil reset` above.

## Privacy

QuickDictate runs entirely on your machine. Audio is sent only to the provider URL you configure (OpenAI or Groq by default). No telemetry, no analytics, no accounts. Your API key is stored locally in `~/.dictate/.env`.

## License

MIT — see [LICENSE](LICENSE).
