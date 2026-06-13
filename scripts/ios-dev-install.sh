#!/usr/bin/env bash
#
# Build, sign, and install the SatScream iOS app (+ widget) on a USB-connected
# iPhone, driven over SSH from the Mac mini — mirrors PearCircle's
# scripts/ios-dev-install.sh.
#
# The iOS app is an XcodeGen project (ios/SatScream/project.yml) built with
# xcodebuild. It signs against the shared empty-password `buildkey` keychain on
# the Mac mini (the only way codesign works over a headless SSH session) using
# the personal Apple Development cert; the App IDs + App Group live under the org
# team G79ALD29NA.
#
# Env overrides:
#   MAC_MINI        host (default Tims-Mac-mini.local)
#   MAC_REPO_PATH   repo path on the Mac mini (default peerloomllc/SatScream)
#   TEAM_ID         signing team (default G79ALD29NA)
#   KEYCHAIN_PATH   signing keychain (default ~/Library/Keychains/buildkey.keychain)
#   DEVICE_UDID     target device (default: first connected iPhone)
#   SKIP_INSTALL    set to 1 to build only
#
set -euo pipefail

MAC_MINI="${MAC_MINI:-Tims-Mac-mini.local}"
MAC_REPO_PATH="${MAC_REPO_PATH:-peerloomllc/SatScream}"
TEAM_ID="${TEAM_ID:-G79ALD29NA}"
KEYCHAIN_PATH="${KEYCHAIN_PATH:-~/Library/Keychains/buildkey.keychain}"
BUNDLE_ID="com.peerloomllc.satscream"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# ── 1. Sync repo to the Mac mini ────────────────────────────────────────────
step "rsync $REPO_ROOT/ -> ${MAC_MINI}:${MAC_REPO_PATH}/"
ssh "$MAC_MINI" "mkdir -p ${MAC_REPO_PATH}"
rsync -az --delete \
  --exclude='.git' \
  --exclude='android/build' --exclude='android/app/build' --exclude='android/.gradle' \
  --exclude='.gradle' --exclude='android/.kotlin' \
  --exclude='ios/SatScream/.build' --exclude='ios/SatScream/build' \
  --exclude='ios/SatScream/SatScream.xcodeproj' \
  "$REPO_ROOT/" "${MAC_MINI}:${MAC_REPO_PATH}/"

# ── 2. Generate project + build (signed) on the Mac mini ────────────────────
# xcodegen runs after rsync because the .xcodeproj and generated Info.plists are
# gitignored. The keychain dance is what makes codesign work over SSH: unlock the
# empty-password buildkey, put it first in the search list, and grant the codesign
# tools access to its keys. Homebrew is stripped from PATH so its tools don't
# shadow Xcode's.
step "xcodegen + xcodebuild (Debug, generic/platform=iOS) on $MAC_MINI"
ssh "$MAC_MINI" "bash -lc '
  set -euo pipefail
  cd ${MAC_REPO_PATH}/ios/SatScream
  xcodegen generate
  security unlock-keychain -p \"\" ${KEYCHAIN_PATH}
  security list-keychains -s ${KEYCHAIN_PATH} ~/Library/Keychains/login.keychain-db /Library/Keychains/System.keychain
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k \"\" ${KEYCHAIN_PATH} >/dev/null 2>&1 || true
  XCODE_PATH=\$(printf %s \"\$PATH\" | sed \"s|/opt/homebrew/bin:||g; s|:/opt/homebrew/bin||g\")
  PATH=\"\$XCODE_PATH\" xcodebuild \
    -project SatScream.xcodeproj -scheme SatScream -configuration Debug \
    -destination generic/platform=iOS -derivedDataPath build \
    DEVELOPMENT_TEAM=${TEAM_ID} \
    -allowProvisioningUpdates build 2>&1 | grep -E \"^error:|BUILD (SUCCEEDED|FAILED)\" || true
'"

if [ "${SKIP_INSTALL:-0}" = "1" ]; then
  step "Build only (SKIP_INSTALL=1). Done."
  exit 0
fi

# ── 3. Install + launch on the iPhone ───────────────────────────────────────
DEVICE_UDID="${DEVICE_UDID:-$(ssh "$MAC_MINI" "xcrun devicectl list devices 2>/dev/null | grep -i iphone | grep -i available | grep -oE '[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}' | head -1")}"
if [ -z "$DEVICE_UDID" ]; then
  echo "No connected iPhone found (set DEVICE_UDID to override)." >&2
  exit 1
fi

step "install + launch on $DEVICE_UDID"
ssh "$MAC_MINI" "bash -lc '
  set -euo pipefail
  APP=${MAC_REPO_PATH}/ios/SatScream/build/Build/Products/Debug-iphoneos/SatScream.app
  xcrun devicectl device install app --device ${DEVICE_UDID} \"\$APP\"
  xcrun devicectl device process launch --device ${DEVICE_UDID} ${BUNDLE_ID}
'"

step "Done."
