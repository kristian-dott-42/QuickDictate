# QuickDictate for Android

A WisprFlow-style voice dictation tool for Android. Tap the floating mic button,
speak, and the cleaned-up text is typed into whatever app you're in.

It's a sibling of the macOS [QuickDictate](../README.md) and shares the same
pipeline — **record → Whisper transcription → LLM cleanup → insert at cursor** —
and the same hardened cleanup prompt. The platform plumbing is different (Kotlin,
not Swift), but the product is the same.

> **Status:** MVP scaffold. The full flow is implemented; it has not yet been
> hardened for the Play Store (see *Caveats*).

## How it differs from the desktop app

The Mac app grabs a global hotkey and synthesises a `⌘V` paste. Android's sandbox
forbids both, so we use the same approach a tool like WisprFlow does:

| Concern | macOS | Android |
| --- | --- | --- |
| Trigger | `fn` key via `CGEventTap` | Floating mic button (overlay) |
| Record | `AVAudioRecorder` | `MediaRecorder` (16 kHz AAC) |
| Transcribe | Whisper endpoint | **same** Groq/OpenAI endpoint |
| Clean up | LLM endpoint | **same** Groq/OpenAI endpoint |
| Insert text | synthesised `⌘V` | Accessibility service paste at cursor |
| Config | `~/.dictate/.env` | in-app settings (SharedPreferences) |

### Why the overlay + accessibility service?

A floating button can't type into another app on its own — it isn't a keyboard.
Two pieces make it work:

1. **Draw over other apps** (`SYSTEM_ALERT_WINDOW`) — shows the mic button on top
   of everything. It's set `FLAG_NOT_FOCUSABLE` so the text field underneath keeps
   input focus.
2. **Accessibility service** — finds the focused field and pastes the transcript at
   the cursor. This is the Android analogue of the desktop `⌘V`.

The alternative — a custom keyboard (IME) — avoids the accessibility service but
forces you to switch away from Gboard to dictate. The overlay keeps you on your
normal keyboard, which is the WisprFlow feel.

## Project layout

```
android/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/quickdictate/android/
│   │   ├── MainActivity.kt                  # settings + one-time permission setup
│   │   ├── OverlayService.kt                # floating button + pipeline orchestration
│   │   ├── DictationAccessibilityService.kt # inserts text into the focused field
│   │   ├── AudioRecorder.kt                 # MediaRecorder capture
│   │   ├── TranscriptionClient.kt           # STT + LLM cleanup (ported from Swift)
│   │   ├── Config.kt                        # provider settings (Groq defaults)
│   │   └── Prompts.kt                       # default cleanup prompt + hallucination filter
│   └── res/                                 # layouts, drawables, strings, a11y config
└── build.gradle.kts, settings.gradle.kts, …
```

## Building

Open the `android/` folder in **Android Studio** (Hedgehog or newer) and let it
sync — it will provision the Gradle wrapper and Android SDK automatically.

> The Gradle wrapper **jar** isn't committed (it's a binary). Android Studio
> generates it on first sync. To build from the CLI instead, run `gradle wrapper`
> once in `android/`, then `./gradlew assembleDebug`.

Requirements: Android SDK 35, JDK 17, a device/emulator on Android 8.0 (API 26)+.

## First-run setup (on device)

1. Launch the app.
2. **Permissions** — grant, in order:
   - Microphone
   - Draw over other apps
   - Text insertion (accessibility) — find *QuickDictate* under *Installed apps*
3. **API key** — paste your Groq key (`gsk_…`). Get one at
   [console.groq.com](https://console.groq.com). Defaults target Groq's
   `whisper-large-v3-turbo` + `llama-3.3-70b-versatile`.
4. Tap **Start**. A floating mic appears. Tap it to record, tap again to stop —
   the cleaned text is typed at your cursor. Drag the button to reposition it.

## Caveats (before this is Play-Store ready)

- **Accessibility-service policy.** Google Play scrutinises apps that use an
  accessibility service. You'll need a clear in-store disclosure and a privacy
  policy explaining it's used only to insert dictated text (the service never
  reads or stores screen content — see `accessibility_service_config.xml`).
- **Data safety.** Audio is sent to your configured endpoint (Groq/OpenAI). This
  must be declared in the Play data-safety form.
- **Key storage.** The MVP keeps the API key in plain `SharedPreferences`.
  Consider `EncryptedSharedPreferences` before shipping.
- **No streaming.** It records-then-sends (like the desktop app). Partial/streaming
  results would make it feel snappier and are a natural next step.

## Privacy

Like the desktop app, audio goes only to the provider endpoint you configure. No
telemetry, no analytics, no accounts. Your API key stays on the device.
