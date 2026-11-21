from pathlib import Path
from shutil import copytree
import sys

from apply_animation import replace_once


VERSION_CODE = 209
VERSION_NAME = "0.119.70-fork.209"

project = Path(sys.argv[1])
manifest = project / "AndroidManifest.xml"
manifest.write_text(
    replace_once(
        manifest.read_text(encoding="utf-8"),
        'android:icon="@drawable/ic_island"',
        'android:icon="@mipmap/flyme_launcher" android:roundIcon="@mipmap/flyme_launcher"',
    ),
    encoding="utf-8",
)

metadata = project / "apktool.yml"
metadata.write_text(
    replace_once(
        metadata.read_text(encoding="utf-8"),
        "versionInfo:\n  versionCode: 201\n  versionName: 0.119.70-stack-icon-clip-advert-experiment\n",
        f"versionInfo:\n  versionCode: {VERSION_CODE}\n  versionName: {VERSION_NAME}\n",
    ),
    encoding="utf-8",
)

copytree(Path(__file__).with_name("launcher-res"), project / "res", dirs_exist_ok=True)
