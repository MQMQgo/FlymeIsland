import json
from pathlib import Path
import sys

from apply_animation import edit_method, replace_once


UPSTREAM = "sisjxj/FlymeIsland"
FORK = "MQMQgo/FlymeIsland"


def replace_string(smali, old, new):
    return replace_once(smali, json.dumps(old), json.dumps(new))


def patch_media_page(method):
    start = method.index("    .line 301\n")
    end = method.index("    .line 306\n", start)
    method = replace_once(method, method[start:end], "")
    return replace_string(
        method,
        "开启：歌词先收为小胶囊，再移除；关闭：已打开的卡片正常收起后再移除。恢复播放时重新显示。",
        "开启后，暂停时尽快收回音乐胶囊。恢复播放时重新显示。",
    )


def patch_about_page(method):
    method = replace_string(
        method,
        f"\n项目：{UPSTREAM}\n许可证：GPL-3.0-only",
        f"\n项目：{FORK}\n作者：sisjxj（原版）、MQMQgo（Fork）\n许可证：GPL-3.0-only",
    )
    method = replace_string(
        method,
        "官方版本免费，请勿向声称需要付费下载、激活或购买官方授权的人转账。",
        "此 Fork 免费，请勿向声称需要付费下载、激活或购买官方授权的人转账。",
    )
    return replace_string(method, "点击检查 GitHub 上的最新正式版本。", "点击查看此 Fork 在 GitHub 上的最新发布。")


def patch_activity(smali):
    smali = edit_method(smali, ".method private buildMediaPage()V", patch_media_page)
    smali = edit_method(smali, ".method private buildAboutPage()V", patch_about_page)
    for suffix in ("", "/releases"):
        smali = replace_string(smali, f"https://github.com/{UPSTREAM}{suffix}", f"https://github.com/{FORK}{suffix}")
    old = ("Flyme Island 官方版本免费，不收取下载费、激活费或使用费。"
           f"请通过 {UPSTREAM} 官方仓库及 Releases 核实下载和更新；")
    new = ("Flyme Island Fork 免费，不收取下载费、激活费或使用费。"
           f"请通过 {FORK} 的 Releases 核实本分支的下载和更新。")
    return replace_once(smali, json.dumps(old)[1:-1], json.dumps(new)[1:-1])


def remove_lyric_button(method):
    start = method.index("    .line 2119\n")
    end = method.index("    .line 2132\n", start)
    # These values are also used by later message and media views.
    return replace_once(method, method[start:end], """    const/16 p2, 0x12    # 18sp

    const/16 v13, 0x8    # View.GONE

    const/16 v19, 0x24    # 36dp

""")


def patch_island(smali):
    return edit_method(
        smali,
        ".method private static makeContent(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)Landroid/widget/FrameLayout;",
        remove_lyric_button,
    )


def patch_music_listener(smali):
    entry = "    .line 923\n    :cond_e\n"
    filter_action = """    invoke-static {v8, v9}, Lio/github/flymeisland/diagnostic/MediaActionFilter;->isLyrics(Ljava/lang/String;Ljava/lang/String;)Z

    move-result v3

    if-nez v3, :cond_11

"""
    return edit_method(smali, ".method private publishCurrent()V",
                       lambda method: replace_once(method, entry, entry + filter_action))


def patch_music_prefs(smali):
    signature = ".method static hideNativeLyrics(Landroid/content/Context;)Z"
    return edit_method(smali, signature, lambda method: signature + """
    .locals 1

    const/4 v0, 0x0

    return v0
""")


def patch_update_checker(smali):
    if smali.count(UPSTREAM) != 4:
        raise ValueError("Unexpected update checker endpoints")
    return smali.replace(UPSTREAM, FORK)


target = Path(sys.argv[1])
for name, patch in (
    ("MainActivity", patch_activity),
    ("SystemUiAicyIsland", patch_island),
    ("MusicListenerService", patch_music_listener),
    ("MusicPrefs", patch_music_prefs),
    ("UpdateChecker", patch_update_checker),
):
    path = target / (name + ".smali")
    path.write_text(patch(path.read_text(encoding="utf-8")), encoding="utf-8")
