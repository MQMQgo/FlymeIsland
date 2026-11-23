from pathlib import Path
import sys


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Expected exactly one occurrence of {old!r}")
    return text.replace(old, new, 1)


def replace_block(method, start, end, replacement=""):
    if method.count(start) != 1 or method.count(end) != 1:
        raise ValueError(f"Expected one block between {start!r} and {end!r}")
    start_at = method.index(start)
    end_at = method.index(end, start_at)
    return method[:start_at] + replacement + method[end_at:]


def edit_method(text, signature, edit):
    if text.count(signature + "\n") != 1:
        raise ValueError(f"Expected exactly one method {signature}")
    start = text.index(signature + "\n")
    end = text.index("\n.end method", start)
    return text[:start] + edit(text[start:end]) + text[end:]


DEFAULT_TIMING = """    new-instance v6, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$$ExternalSyntheticLambda3;

    invoke-direct {v6}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$$ExternalSyntheticLambda3;-><init>()V

    invoke-virtual {v2, v6}, Landroid/animation/ValueAnimator;->setInterpolator(Landroid/animation/TimeInterpolator;)V

    .line 3217
    move/from16 v19, v0

    move/from16 p10, v1

    move-wide/from16 v0, p6

    invoke-virtual {v2, v0, v1}, Landroid/animation/ValueAnimator;->setDuration(J)Landroid/animation/ValueAnimator;"""

OPENING_TIMING = """    move/from16 v19, v0

    move/from16 p10, v1

    move-object/from16 v20, v2

    move-wide/from16 v21, p6

    move/from16 v23, p0

    move/from16 v24, p1

    move/from16 v25, v7

    move/from16 v26, v9

    move/from16 v27, p4

    move/from16 v28, p5

    move/from16 v29, p9

    sget-boolean v30, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->callMediumVisible:Z

    const/4 v6, 0x4

    invoke-static {v6}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->dp(I)I

    move-result v6

    invoke-virtual {v4, v1, v6}, Lio/github/flymeisland/diagnostic/CameraIslandLayout;->messageSecondaryWidthLimit(II)I

    move-result v31

    move/from16 v6, p2

    invoke-static {v11, v6}, Ljava/lang/Math;->max(FF)F

    move-result v6

    int-to-float v8, v0

    sub-float v32, v8, v6

    sget-object v33, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->visibleKey:Ljava/lang/String;

    invoke-static/range {v20 .. v33}, Lio/github/flymeisland/diagnostic/ExpansionTiming;->applyIfOpening(Landroid/animation/ValueAnimator;JFFFFFFFZIFLjava/lang/String;)Z

    move-result v6

    if-nez v6, :opening_timing_ready

    new-instance v6, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$$ExternalSyntheticLambda3;

    invoke-direct {v6}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$$ExternalSyntheticLambda3;-><init>()V

    invoke-virtual {v2, v6}, Landroid/animation/ValueAnimator;->setInterpolator(Landroid/animation/TimeInterpolator;)V

    .line 3217
    move-wide/from16 v0, p6

    invoke-virtual {v2, v0, v1}, Landroid/animation/ValueAnimator;->setDuration(J)Landroid/animation/ValueAnimator;

    :opening_timing_ready"""

DEFAULT_FRAME = """    invoke-static {p4, p5, v0}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->mix(FFF)F

    move-result p2

    invoke-static {p6, p7, v0}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->mix(FFF)F

    move-result p3

    invoke-static {p8, p9, v0}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->mix(FFF)F

    move-result p4

    sget p5, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->radius:F

    invoke-virtual {p1, p2, p3, p4, p5}, Lio/github/flymeisland/diagnostic/CameraIslandLayout;->setFrame(FFFF)V"""

EXPANSION_FRAME = """    move-object/from16 p2, p16

    const/4 p3, 0x0

    invoke-static {p2, p4, p5, v0, p3}, Lio/github/flymeisland/diagnostic/ExpansionTiming;->frameSize(Landroid/animation/ValueAnimator;FFFZ)F

    move-result p4

    const/4 p3, 0x1

    invoke-static {p2, p6, p7, v0, p3}, Lio/github/flymeisland/diagnostic/ExpansionTiming;->frameSize(Landroid/animation/ValueAnimator;FFFZ)F

    move-result p3

    invoke-static {p8, p9, v0}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->mix(FFF)F

    move-result p2

    sget p5, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->radius:F

    invoke-virtual {p1, p4, p3, p2, p5}, Lio/github/flymeisland/diagnostic/CameraIslandLayout;->setFrame(FFFF)V"""

CONTENT_PROGRESS = (
    "Lio/github/flymeisland/diagnostic/ExpansionTiming;"
    "->contentProgress(Landroid/animation/ValueAnimator;FF)F"
)


def time_expansion(method):
    method = replace_once(method, "    .locals 20", "    .locals 34")
    return replace_once(method, DEFAULT_TIMING, OPENING_TIMING)


def animate_frame(method):
    method = replace_once(method, DEFAULT_FRAME, EXPANSION_FRAME)
    method = replace_once(
        method,
        "    .line 3223\n    sget-object",
        "    .line 3223\n    move-object/from16 p4, p16\n\n    sget-object",
    )
    for start, target in [("p10", "p11"), ("p12", "p13"), ("p14", "p2")]:
        prefix = "    invoke-static "
        method = replace_once(
            method,
            f"{prefix}{{{start}, {target}, v0}}, ",
            f"{prefix}{{p4, v0, {target}}}, {CONTENT_PROGRESS}\n\n"
            "    move-result p5\n\n"
            f"{prefix}{{{start}, {target}, p5}}, ",
        )
    return method


MUSIC_MODE = """    sget-object v0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->mediaWave:Lio/github/flymeisland/diagnostic/MediaWaveView;

    if-eqz v0, :notification_mode

    invoke-virtual {v0}, Landroid/view/View;->getVisibility()I

    move-result v0

    if-nez v0, :notification_mode

    sget-object v0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->pillTitle:Landroid/widget/TextView;

    const/16 v1, 0x8

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setVisibility(I)V

    sget-object v0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->compactTicker:Lio/github/flymeisland/diagnostic/CameraSafeTickerView;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Lio/github/flymeisland/diagnostic/CameraSafeTickerView;->setCompactActive(Z)V

    const/4 v1, 0x4

    invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V

    return-void

    :notification_mode
"""

COMPACT_WIDTH = """.method private static compactPillWidth(Ljava/lang/String;I)I
    .locals 1

    const-string v0, "MUSIC"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :done

    const/16 v0, 0x70    # 112 dp

    invoke-static {p1, v0}, Ljava/lang/Math;->min(II)I

    move-result p1

    :done
    return p1
.end method"""

CLAMP_SHOW_WIDTH = """    move-object/from16 v2, p1

    move/from16 v0, p8

    invoke-static {v2, v0}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->compactPillWidth(Ljava/lang/String;I)I

    move-result v0

    move/from16 p8, v0
"""

BIND_WIDTH = """    :cond_20
    iget v4, p0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;->pillWidth:I
"""

CLAMP_BIND_WIDTH = BIND_WIDTH + """
    iget-object v5, p0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;->command:Ljava/lang/String;

    invoke-static {v5, v4}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->compactPillWidth(Ljava/lang/String;I)I

    move-result v4
"""

PROMOTE_MUSIC = """    .line 1708
    if-eqz v9, :animate_promotion

    sget-boolean v0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->isExpanded:Z

    if-nez v0, :cond_7d

    sget-boolean v0, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->musicAutoExpand:Z

    if-nez v0, :cond_7d

    :animate_promotion
"""

RESTORE_COMPACT = """    .line 1930
    :cond_aa
    const/16 v23, 0x0

    :try_start_d
"""

RESTORE_MUSIC = RESTORE_COMPACT + """    invoke-virtual {v11, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :schedule_restored

    invoke-static {}, Lio/github/flymeisland/diagnostic/SystemUiAicyIsland;->animateCompactArrival()V

    goto :goto_3e

    :schedule_restored
"""

WAVE_STYLE_DEFAULTS = """    :array_0
    .array-data 4
        0x5
        0x3
        0x3
        0x10
        0xc8
        0x0
        0x1
        0x0
        0x0
    .end array-data"""


def animate_music_arrival(method):
    method = replace_once(
        method,
        "    .line 1390\n    move-object/from16 v1, p0\n\n    move-object/from16 v2, p1\n",
        "    .line 1390\n    move-object/from16 v1, p0\n\n" + CLAMP_SHOW_WIDTH,
    )
    method = replace_once(method, "    .line 1708\n    if-nez v9, :cond_7d\n", PROMOTE_MUSIC)
    return replace_once(method, RESTORE_COMPACT, RESTORE_MUSIC)


if __name__ == "__main__":
    island = Path(sys.argv[1])
    smali = island.read_text(encoding="utf-8")
    smali = replace_once(smali, "PILL_INTRO_MS:J = 0x6eL", "PILL_INTRO_MS:J = 0x41L")
    smali = edit_method(
        smali,
        ".method private static animateArrival()V",
        lambda method: replace_once(method, "const-wide/16 v7, 0xf0", "const-wide/16 v7, 0xb4"),
    )
    for signature in (
        ".method private static animatePillFirst()V",
        ".method static synthetic lambda$animateArrival$38()V",
    ):
        smali = edit_method(
            smali, signature,
            lambda method: replace_once(method, "const-wide/16 v2, 0x6e", "const-wide/16 v2, 0x41"),
        )
    smali = edit_method(
        smali,
        ".method private static transition(FFFFFFJLjava/lang/Runnable;FZ)V",
        time_expansion,
    )
    smali = edit_method(
        smali,
        ".method static synthetic lambda$transition$36(ILio/github/flymeisland/diagnostic/CameraIslandLayout;"
        "FFFFFFFFFFFFFFLandroid/animation/ValueAnimator;)V",
        animate_frame,
    )
    smali = edit_method(
        smali,
        ".method private static refreshCompactMessageMode()V",
        lambda method: replace_once(method, "    .line 4890\n    :cond_0\n",
                                    "    .line 4890\n    :cond_0\n" + MUSIC_MODE),
    )
    smali = edit_method(
        smali,
        ".method private static bindNotice(Lio/github/flymeisland/diagnostic/SystemUiAicyIsland$Notice;Z)V",
        lambda method: replace_once(method, BIND_WIDTH, CLAMP_BIND_WIDTH),
    )
    smali = edit_method(
        smali,
        ".method private static show(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;[BLandroid/app/PendingIntent;Ljava/lang/String;Ljava/lang/String;IIIIIZJZI)Ljava/lang/String;",
        animate_music_arrival,
    )
    smali += "\n" + COMPACT_WIDTH + "\n"
    island.write_text(smali, encoding="utf-8")

    wave_style = island.with_name("WaveStylePrefs.smali")
    rotation_enabled = WAVE_STYLE_DEFAULTS.replace("        0x1\n        0x0", "        0x1\n        0x1")
    wave_style.write_text(
        replace_once(wave_style.read_text(encoding="utf-8"), WAVE_STYLE_DEFAULTS, rotation_enabled),
        encoding="utf-8",
    )
