#!/usr/bin/env bash
#
# package-apks.sh — turn one unsigned release APK into signed per-ABI + universal APKs.
#
# Usage:   package-apks.sh <unsigned-apk> <out-dir>
# Env:     SWIRL_KEYSTORE_PATH       release keystore (required)
#          SWIRL_KEYSTORE_PASSWORD   keystore password (required; only ever passed to
#                                    apksigner as --ks-pass env:..., never echoed)
#          ANDROID_HOME              Android SDK (required; picks the highest build-tools
#                                    containing apksigner)
#          EXPECTED_VERSION          optional; fail when it != the APK's versionName
#
# Steps per output: delete foreign lib/<abi>/ entries, zipalign -P 16, apksigner sign
# (v4 disabled — we ship plain APKs), verify, zipalign -c. Universal keeps every lib/.
#
# Output:  <out-dir>/swirl-music-<ver>-{arm64-v8a,armeabi-v7a,x86_64,universal}.apk
#          plus SHA256SUMS.
#
set -euo pipefail

fail() { echo "error: $*" >&2; exit 1; }

[ "${1:-}" != "" ] && [ "${2:-}" != "" ] || fail "usage: $0 <unsigned-apk> <out-dir>"
UNSIGNED="$1"
OUT_DIR="$2"
[ -f "$UNSIGNED" ] || fail "unsigned APK not found: $UNSIGNED"
[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME is unset"
[ -n "${SWIRL_KEYSTORE_PATH:-}" ] || fail "SWIRL_KEYSTORE_PATH is unset"
[ -f "$SWIRL_KEYSTORE_PATH" ] || fail "keystore not found: $SWIRL_KEYSTORE_PATH"
[ -n "${SWIRL_KEYSTORE_PASSWORD:-}" ] || fail "SWIRL_KEYSTORE_PASSWORD is unset"

# Highest build-tools version that has apksigner.
BUILD_TOOLS=""
for dir in "$ANDROID_HOME"/build-tools/*/; do
    [ -x "${dir}apksigner" ] || continue
    BUILD_TOOLS="$(printf '%s\n%s\n' "$BUILD_TOOLS" "$(basename "$dir")" | sort -V | tail -1)"
done
[ -n "$BUILD_TOOLS" ] || fail "no build-tools with apksigner under $ANDROID_HOME/build-tools"
BT="$ANDROID_HOME/build-tools/$BUILD_TOOLS"
echo "build-tools: $BUILD_TOOLS"

VERSION_NAME="$("$BT/aapt2" dump badging "$UNSIGNED" \
    | sed -n "s/^package: name='[^']*' versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/\2/p")"
[ -n "$VERSION_NAME" ] || fail "could not read versionName from aapt2 badging"
if [ -n "${EXPECTED_VERSION:-}" ] && [ "$EXPECTED_VERSION" != "$VERSION_NAME" ]; then
    fail "tag/expected version '$EXPECTED_VERSION' != APK versionName '$VERSION_NAME'"
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT_DIR"

pack_one() {
    local abi="$1"   # arm64-v8a | armeabi-v7a | x86_64 | universal
    local work_apk="$WORK/$abi.apk"
    cp "$UNSIGNED" "$work_apk"

    if [ "$abi" != "universal" ]; then
        local deletes=""
        local entry
        while IFS= read -r entry; do
            case "$entry" in
                lib/*)
                    case "$entry" in
                        "lib/$abi/"*) ;;
                        *) deletes="$deletes $entry" ;;
                    esac
                    ;;
            esac
        done <<EOF
$(unzip -Z1 "$work_apk")
EOF
        if [ -n "$deletes" ]; then
            # shellcheck disable=SC2086
            (cd "$WORK" && zip -q -d "$abi.apk" $deletes)
        fi
        # No `unzip | grep -q` here: grep -q closes the pipe early and unzip dies on
        # SIGPIPE, which pipefail reports as a failure.
        local remaining
        remaining="$(unzip -Z1 "$work_apk" | grep '^lib/' || true)"
        echo "$remaining" | grep -q "^lib/$abi/" \
            || fail "no lib/$abi/ entries left — the APK native layout changed"
    fi

    local aligned="$WORK/$abi-aligned.apk"
    "$BT/zipalign" -P 16 -f 4 "$work_apk" "$aligned"

    local final="$OUT_DIR/swirl-music-$VERSION_NAME-$abi.apk"
    "$BT/apksigner" sign \
        --ks "$SWIRL_KEYSTORE_PATH" \
        --ks-key-alias swirl \
        --ks-pass env:SWIRL_KEYSTORE_PASSWORD \
        --v4-signing-enabled false \
        --out "$final" \
        "$aligned"
    "$BT/apksigner" verify --print-certs "$final" >/dev/null
    "$BT/zipalign" -c -P 16 4 "$final"

    local size
    size="$(stat -c %s "$final" 2>/dev/null || stat -f %z "$final")"
    echo "built: $final ($size bytes)"
}

pack_one arm64-v8a
pack_one armeabi-v7a
pack_one x86_64
pack_one universal

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$OUT_DIR" && sha256sum swirl-music-"$VERSION_NAME"-*.apk > SHA256SUMS)
else
    (cd "$OUT_DIR" && shasum -a 256 swirl-music-"$VERSION_NAME"-*.apk > SHA256SUMS)
fi
echo "checksums: $OUT_DIR/SHA256SUMS"
