from pathlib import Path
import sys

from apply_animation import edit_method, replace_once


ISLAND = "Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;"
MASK = "Lio/github/flymeisland/diagnostic/CapsuleIconMask;"
PACKAGE_ICON = MASK + "->setPackageIcon(Landroid/widget/ImageView;Landroid/content/Context;Ljava/lang/String;)V"


def replace_block(method, start, end, replacement=""):
    if method.count(start) != 1 or method.count(end) != 1:
        raise ValueError(f"Expected one block between {start!r} and {end!r}")
    start_at = method.index(start)
    end_at = method.index(end, start_at)
    return method[:start_at] + replacement + method[end_at:]


def align_secondary_contents(method):
    method = replace_block(method, "    .line 2893\n", "    .line 2898\n")
    method = replace_once(
        method,
        "    move-result v13\n\n    add-int/2addr v0, v13\n\n    .line 2901\n",
        "    move-result v15\n\n    .line 2901\n",
    )
    # The taller of the icon and text determines the content height. Apply one
    # top inset to both, then keep their individual offsets within that area.
    height_calculation = f"    .line 2908\n    sget v15, {ISLAND}->messageMediumH:I\n"
    return replace_block(method, height_calculation, "    .line 2912\n", f"""    .line 2908
    invoke-static {{v6}}, {ISLAND}->dp(I)I

    move-result v6

    add-int/2addr v6, v7

    add-int/2addr v14, v15

    add-int/2addr v14, v13

    invoke-static {{v6, v14}}, Ljava/lang/Math;->max(II)I

    move-result v6

    sget v14, {ISLAND}->messageMediumH:I

    sub-int/2addr v14, v6

    invoke-static {{v0, v14}}, Ljava/lang/Math;->max(II)I

    move-result v0

    add-int/2addr v6, v0

    sput v6, {ISLAND}->messageMediumH:I

    .line 2910
    add-int/2addr v7, v0

    iput v7, v10, Landroid/widget/FrameLayout$LayoutParams;->topMargin:I

    sget-object v13, {ISLAND}->messageMediumIcon:Landroid/widget/ImageView;

    invoke-virtual {{v13, v10}}, Landroid/widget/ImageView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    add-int/2addr v0, v15

    .line 2911
    sget v10, {ISLAND}->requestedMessageSecondaryTextX:I

    const/16 v13, 0x24

    const/16 v14, 0xb4

    invoke-static {{v10, v13, v14}}, Lio/github/flymeisland/diagnostic/SystemUiIslandPrefs;->clamp(III)I

    move-result v10

""")


def notification_capsule(method):
    method = replace_block(method, "    .line 2819\n", "    .line 2820\n")
    method = replace_block(method, "    .line 2858\n    :try_start_0\n", "    .line 2868\n", f"""    .line 2858
    sget-object v0, {ISLAND}->messageMediumIcon:Landroid/widget/ImageView;

    sget-object v13, {ISLAND}->context:Landroid/content/Context;

    iget-object v14, v1, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;->sourcePackage:Ljava/lang/String;

    invoke-static {{v0, v13, v14}}, {PACKAGE_ICON}

""")
    return align_secondary_contents(method)


def contact_icon(method):
    method = replace_once(
        method, "    invoke-virtual {v1, p2}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V",
        f"    invoke-static {{v1, p2}}, {MASK}->setContactPhoto(Landroid/widget/ImageView;Landroid/graphics/Bitmap;)V",
    )
    # Keep the package name in p1 when the command is neither WECHAT nor QQ.
    method = replace_once(method, "    move-result p1\n\n    if-eqz p1, :cond_4\n",
                          "    move-result p2\n\n    if-eqz p2, :cond_4\n")
    method = replace_once(method, "    :cond_4\n    const/4 p1, 0x0\n", "    :cond_4\n")
    return replace_block(method, "    .line 2429\n    :goto_1\n", "    .line 2439\n", f"""    .line 2429
    :goto_1
    sget-object p2, {ISLAND}->contactAvatar:Landroid/widget/ImageView;

    invoke-static {{p2, p0, p1}}, {PACKAGE_ICON}

""")


def restore_lyric_artwork(method, view, bitmap):
    return replace_once(
        method, f"    invoke-virtual {{{view}, {bitmap}}}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V",
        f"    invoke-static {{{view}, {bitmap}}}, {ISLAND}->setLyricArtwork(Landroid/widget/ImageView;Landroid/graphics/Bitmap;)V",
    )


def lyric_icon(method):
    method = restore_lyric_artwork(method, "v3", "v2")
    method = replace_block(method, "    .line 2769\n    :cond_9\n", "    .line 2773\n", "    :cond_9\n")
    # The frame radius still needs half the height after removing the icon outline.
    return replace_once(method, "    mul-float/2addr v1, v9\n",
                        "    const/high16 v9, 0x3f000000    # 0.5f\n\n    mul-float/2addr v1, v9\n")


LYRIC_ARTWORK = f""".method private static setLyricArtwork(Landroid/widget/ImageView;Landroid/graphics/Bitmap;)V
    .locals 2

    sget-object v0, {ISLAND}->lyricLayout:[I

    const/16 v1, 0x9

    aget v0, v0, v1

    invoke-static {{v0}}, {ISLAND}->dp(I)I

    move-result v0

    invoke-static {{p0, p1, v0}}, {MASK}->setLyricArtwork(Landroid/widget/ImageView;Landroid/graphics/Bitmap;I)V

    return-void
.end method
"""


def patch_island(smali):
    smali = edit_method(
        smali, ".method private static showMessageSecondary(Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z", notification_capsule,
    )
    smali = edit_method(
        smali, ".method private static showLyricMedium()V", lyric_icon,
    )
    smali = edit_method(
        smali, ".method private static updateLyricMedium()V",
        lambda method: restore_lyric_artwork(method, "v2", "v1"),
    )
    smali = edit_method(
        smali, ".method private static updateIconForPackage(Landroid/widget/ImageView;Landroid/content/Context;Ljava/lang/String;)V",
        lambda method: replace_once(method, "    .line 5192\n    :cond_4\n", f"""    .line 5192
    :cond_4
    sget-object v0, {ISLAND}->pillIcon:Landroid/widget/ImageView;

    if-eq p0, v0, :compact_app_icon

    invoke-static {{p0, p1, p2}}, {PACKAGE_ICON}

    return-void

    :compact_app_icon
"""),
    )
    smali = edit_method(
        smali, ".method private static updateContactAvatar(Landroid/content/Context;Ljava/lang/String;[B)V",
        contact_icon,
    )
    smali = replace_once(
        smali, "    iget-object v11, p0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;->command:Ljava/lang/String;\n\n"
        "    if-eqz v10, :cond_13",
        "    iget-object v11, p0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;->sourcePackage:Ljava/lang/String;\n\n"
        "    if-eqz v10, :cond_13",
    )
    smali = replace_once(
        smali, f"    invoke-static {{v2, v13, v5}}, {ISLAND}->updateContactAvatar(Landroid/content/Context;Ljava/lang/String;[B)V",
        "    move-object/from16 v6, p6\n\n"
        f"    invoke-static {{v2, v6, v5}}, {ISLAND}->updateContactAvatar(Landroid/content/Context;Ljava/lang/String;[B)V",
    )
    smali = replace_once(
        smali, "    new-instance v7, Lio/github/flymeisland/diagnostic/MessageStackIcon;",
        f"    invoke-static {{v2}}, {MASK}->restoreContactFrame(Landroid/widget/ImageView;)V\n\n"
        "    new-instance v7, Lio/github/flymeisland/diagnostic/MessageStackIcon;",
    )
    smali = edit_method(
        smali, ".method private static makeContent(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)Landroid/widget/FrameLayout;",
        lambda method: replace_block(method, "    .line 2295\n", "    .line 2303\n"),
    )
    return smali + "\n" + LYRIC_ARTWORK


def patch_avatar(smali):
    return edit_method(smali, ".method public draw(Landroid/graphics/Canvas;)V", lambda method: replace_once(
        method, "    .line 19\n    :cond_0\n", """    .line 19
    :cond_0
    invoke-virtual {p0}, Landroid/view/View;->getClipToOutline()Z

    move-result v0

    if-nez v0, :draw_photo

    invoke-super {p0, p1}, Landroid/widget/ImageView;->draw(Landroid/graphics/Canvas;)V

    return-void

    :draw_photo
"""))


def patch_stack_row(smali):
    return edit_method(
        smali, ".method constructor <init>(Lio/github/flymeisland/diagnostic/MessageStack$Entry;)V",
        lambda method: replace_block(method, "    iget-object v0, p0, "
            "Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$StackRow;->icon:Landroid/widget/ImageView;\n\n"
            "    sget-object v3, Landroid/widget/ImageView$ScaleType;->CENTER_CROP:",
            "    .line 121\n", f"""    .line 119
    iget-object p1, p1, Lio/github/flymeisland/diagnostic/MessageStack$Entry;->source:Ljava/lang/String;

    invoke-static {{v0, v3, p1}}, {PACKAGE_ICON}

"""),
    )


def patch_settings(smali):
    return replace_once(smali, r"\u5de6\u4fa7\u5706\u5f62\u56fe\u6807", r"\u5de6\u4fa7\u5e94\u7528\u56fe\u6807")


def patch_position_dialog(smali):
    return replace_once(
        smali,
        r"\u6587\u5b57\u4ecd\u9075\u5b88\u9876\u90e8\u907f\u8ba9",
        r"\u56fe\u6807\u548c\u6587\u5b57\u5171\u7528\u9876\u90e8\u907f\u8ba9",
    )


target = Path(sys.argv[1])
for name, patch in (
    ("SystemUiAicyIsland", patch_island),
    ("SnapshotSafeAvatarView", patch_avatar),
    ("SystemUiAicyIsland$StackRow", patch_stack_row),
    ("MainActivity", patch_settings),
    ("MessageSecondaryPositionDialog", patch_position_dialog),
):
    path = target / (name + ".smali")
    path.write_text(patch(path.read_text(encoding="utf-8")), encoding="utf-8")
