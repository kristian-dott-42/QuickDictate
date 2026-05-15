#!/bin/bash
# QuickDictate — one-shot install for macOS
# Compiles the Swift source, builds the .app bundle, signs it, and sets up
# the config dir. After running this, grant Microphone + Accessibility
# permissions when macOS prompts.

set -e

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$HOME/Applications/QuickDictate.app"
CONFIG_DIR="$HOME/.dictate"

echo ""
echo "=== QuickDictate Install ==="
echo ""

# Check Swift compiler is available
if ! command -v swiftc &>/dev/null; then
    echo "❌  Swift compiler not found."
    echo "    Install Xcode Command Line Tools first:"
    echo "    xcode-select --install"
    exit 1
fi

# ── Build app bundle ─────────────────────────────────────────────────────────
echo "Building app bundle…"
mkdir -p "$APP_DIR/Contents/MacOS"
cp "$REPO_DIR/Info.plist" "$APP_DIR/Contents/Info.plist"

swiftc "$REPO_DIR/QuickDictate.swift" \
    -framework AppKit \
    -framework AVFoundation \
    -framework Carbon \
    -O \
    -o "$APP_DIR/Contents/MacOS/QuickDictate"

# Ad-hoc code signing — required for TCC permission grants
codesign --sign - --force --deep "$APP_DIR" 2>&1 | grep -v "replacing" || true

# ── Config dir ───────────────────────────────────────────────────────────────
mkdir -p "$CONFIG_DIR"
if [ ! -f "$CONFIG_DIR/.env" ]; then
    cp "$REPO_DIR/.env.example" "$CONFIG_DIR/.env"
    echo ""
    echo "📝  Created $CONFIG_DIR/.env — open it and add your API key(s):"
    echo "    nano $CONFIG_DIR/.env"
fi

# ── Login Item (auto-start on login) ─────────────────────────────────────────
osascript -e 'tell application "System Events" to delete every login item whose name is "QuickDictate"' 2>/dev/null || true
osascript -e "tell application \"System Events\" to make login item at end with properties {path:\"$APP_DIR\", hidden:true}" >/dev/null

echo ""
echo "✅  Installed to $APP_DIR"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  NEXT STEPS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  1. Add your API key to $CONFIG_DIR/.env"
echo ""
echo "  2. Launch the app:"
echo "       open $APP_DIR"
echo ""
echo "  3. When macOS prompts, grant:"
echo "     • Microphone access (in the popup dialog)"
echo "     • Accessibility access (in System Settings → Privacy & Security)"
echo ""
echo "  4. After granting Accessibility, restart the app:"
echo "       pkill -f QuickDictate && open $APP_DIR"
echo ""
echo "  USAGE:  Hold 'fn' to record, release to paste."
echo "  LOGS:   tail -f $CONFIG_DIR/dictate.log"
echo ""
