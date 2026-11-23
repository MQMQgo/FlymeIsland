#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 4 ]]; then
    printf 'Usage: %s ORIGINAL_APK APKTOOL_JAR ANDROID_SDK OUTPUT_UNSIGNED_APK\n' "$0" >&2
    exit 2
fi

patch_dir=$(dirname -- "$(realpath -- "$0")")
original_apk=$(realpath -- "$1")
apktool_jar=$(realpath -- "$2")
android_sdk=$(realpath -- "$3")
output_dir=$(realpath -- "$(dirname -- "$4")")
output_apk="$output_dir/$(basename -- "$4")"

expected_sha256=1cafc65ec51f9c91ddabe02bc00fca9e95ea305d74f51aaa5421c86fc9b35d5e
if [[ $(sha256sum < "$original_apk") != "$expected_sha256  -" ]]; then
    printf 'The input APK must be the original v0.119.70 Release asset.\n' >&2
    exit 1
fi
if [[ $(java -jar "$apktool_jar" --version) != 3.0.3 ]]; then
    printf 'This patch uses Apktool 3.0.3 for its smali layout.\n' >&2
    exit 1
fi
if [[ -e "$output_apk" || -L "$output_apk" ]]; then
    printf 'The output path already exists.\n' >&2
    exit 1
fi

work=$(mktemp -d -- "$output_dir/.flyme-animation.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
package=io/github/flymeisland/diagnostic
android_jar="$android_sdk/platforms/android-36/android.jar"
build_tools="$android_sdk/build-tools/36.0.0"
mkdir -p "$work/classes" "$work/dex"

javac --release 8 -cp "$android_jar" -d "$work/classes" \
    "$patch_dir/ExpansionTiming.java" "$patch_dir/CapsuleIconMask.java" "$patch_dir/MediaActionFilter.java" \
    "$patch_dir/ChargingPowerDisplay.java" "$patch_dir/CameraCutoutBounds.java"
"$build_tools/d8" --min-api 28 --lib "$android_jar" --output "$work/dex" \
    "$work/classes/$package/"*.class
python3 - "$original_apk" "$work/dex/classes.dex" "$work/helper.apk" <<'PY'
import sys
from zipfile import ZipFile

original_path, helper_dex, helper_path = sys.argv[1:]
with ZipFile(original_path) as original, ZipFile(helper_path, "w") as helper:
    for name in ("AndroidManifest.xml", "resources.arsc"):
        helper.writestr(original.getinfo(name), original.read(name))
    helper.write(helper_dex, "classes.dex")
PY

java -jar "$apktool_jar" decode --no-res --frame-path "$work/framework" \
    -o "$work/helper" "$work/helper.apk"
java -jar "$apktool_jar" decode --frame-path "$work/framework" \
    -o "$work/app" "$original_apk"
target="$work/app/smali/$package"
cp -- "$work/helper/smali/$package/"*.smali "$target/"
python3 "$patch_dir/apply_animation.py" "$target/SystemUiAicyIsland.smali"
python3 -B "$patch_dir/apply_icons.py" "$target"
python3 -B "$patch_dir/apply_orientation.py" "$target"
python3 -B "$patch_dir/apply_release_ui.py" "$target"
python3 -B "$patch_dir/apply_charging.py" "$target"
python3 -B "$patch_dir/apply_preview_cutout.py" "$target"
python3 -B "$patch_dir/apply_launcher_icon.py" "$work/app"
java -jar "$apktool_jar" build --frame-path "$work/framework" \
    -o "$work/assembled.apk" "$work/app"

python3 - "$original_apk" "$work/assembled.apk" "$work/unsigned.apk" <<'PY'
import sys
from zipfile import ZipFile

original_path, assembled_path, output_path = sys.argv[1:]
old_signatures = {"META-INF/ANDROIDD.SF", "META-INF/ANDROIDD.RSA", "META-INF/MANIFEST.MF"}
rebuilt_entries = {"classes.dex", "AndroidManifest.xml", "resources.arsc"}
with ZipFile(original_path) as original, ZipFile(assembled_path) as assembled, ZipFile(output_path, "w") as output:
    if assembled.read("classes.dex") == original.read("classes.dex"):
        raise ValueError("Expected the animation patch to change classes.dex")
    for entry in original.infolist():
        name = entry.filename
        if name in old_signatures:
            continue
        if name in rebuilt_entries:
            output.writestr(entry, assembled.read(name))
        else:
            output.writestr(entry, original.read(entry))
    original_names = set(original.namelist())
    resource_timestamp = original.getinfo("resources.arsc").date_time
    for entry in assembled.infolist():
        if entry.filename.startswith("res/") and entry.filename not in original_names:
            entry.date_time = resource_timestamp
            output.writestr(entry, assembled.read(entry))
PY

"$build_tools/zipalign" -P 16 -f 4 "$work/unsigned.apk" "$work/aligned.apk"
"$build_tools/zipalign" -c -P 16 4 "$work/aligned.apk"
ln -T -- "$work/aligned.apk" "$output_apk"
printf 'Built unsigned APK at %s\n' "$output_apk"
