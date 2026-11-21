import json
from pathlib import Path
import sys

from apply_animation import edit_method, replace_once


ISLAND = "Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;"
POWER = "Lio/github/flymeisland/diagnostic/ChargingPowerDisplay;"
LEVEL = f"{ISLAND}->chargeLabel:Landroid/widget/TextView;"
TITLE = f"{ISLAND}->pillTitle:Landroid/widget/TextView;"


def charge_window(smali):
    smali = replace_once(smali, "WINDOW_MS:J = 0x7d0L", "WINDOW_MS:J = 0x1388L")
    for signature, register in (
        (".method public static onBatteryEvent(Landroid/content/Context;Landroid/content/Intent;)V", "v0"),
        (".method public static testIfPlugged(Landroid/content/Context;)Ljava/lang/String;", "v2"),
    ):
        smali = edit_method(
            smali, signature,
            lambda method: replace_once(method, f"const-wide/16 {register}, 0x7d0", f"const-wide/16 {register}, 0x1388"),
        )
    return smali


def default_charge_width(smali):
    return edit_method(
        smali, ".method public static width(Landroid/content/Context;Ljava/lang/String;)I",
        lambda method: replace_once(method, "const/16 p1, 0xb4", "const/16 p1, 0x9c"),
    )


def compact_charge_mode(method):
    method = replace_once(method, "    move-result-object p1\n\n    .line 4804\n    :cond_2\n", f"""    move-result-object p1

    sget-object v3, {LEVEL}

    if-eqz v3, :cond_2

    invoke-virtual {{v3, p1}}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    .line 4804
    :cond_2
    invoke-virtual {{v1, p0}}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v5

""")
    method = replace_once(
        method,
        "    invoke-virtual {v1, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n\n"
        "    move-result p0\n\n    if-eqz p0, :cond_3\n",
        "    if-eqz v5, :cond_3\n",
    )
    method = replace_once(
        method, f"    sget-object p0, {TITLE}\n\n    invoke-static {{p1}}, {ISLAND}->limitCompactText",
        f"    sget-object p0, {TITLE}\n\n    if-nez v5, :bind_power\n\n"
        f"    invoke-static {{p1}}, {ISLAND}->limitCompactText",
    )
    return replace_once(
        method, f"    invoke-virtual {{p0, p1}}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n\n"
        f"    sget-object p0, {TITLE}\n",
        f"    invoke-virtual {{p0, p1}}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n\n"
        f"    :bind_power\n    invoke-static {{p0, v5}}, {POWER}->setActive(Landroid/widget/TextView;Z)V\n\n"
        f"    sget-object p0, {TITLE}\n",
    )


def patch_island(smali):
    smali = edit_method(
        smali, ".method private static setCompactMode(Ljava/lang/String;Ljava/lang/String;)V", compact_charge_mode,
    )
    return edit_method(
        smali, ".method private static close()V",
        lambda method: replace_once(method, f"    sput-object v0, {TITLE}\n",
                                    f"    invoke-static {{}}, {POWER}->stop()V\n\n    sput-object v0, {TITLE}\n"),
    )


def patch_settings(smali):
    return replace_once(
        smali, json.dumps("接入电源时短暂显示充电状态"),
        json.dumps("接入电源时显示约 5 秒，右侧显示电池端功率"),
    )


target = Path(sys.argv[1])
for name, patch in (
    ("ChargingController", charge_window),
    ("AppSizePrefs", default_charge_width),
    ("SystemUiAicyIsland", patch_island),
    ("MainActivity", patch_settings),
):
    path = target / (name + ".smali")
    path.write_text(patch(path.read_text(encoding="utf-8")), encoding="utf-8")
