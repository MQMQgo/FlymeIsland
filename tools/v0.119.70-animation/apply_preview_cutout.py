from pathlib import Path
import sys

from apply_animation import edit_method, replace_block, replace_once


PACKAGE = "Lio/github/flymeisland/diagnostic/"
BOUNDS = PACKAGE + "CameraCutoutBounds;"
AVOID = PACKAGE + "CameraTextAvoid;"
TICKER = PACKAGE + "CameraSafeTickerView;"
INTERSECTS = BOUNDS + "->intersectsViewport(Landroid/graphics/Rect;II)Z"


def read_screen_bounds(method):
    # Keep the view while the original method updates its geometry cache.
    method = replace_once(
        method, "    invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;\n"
        "\n    move-result-object p0\n",
        "    move-object v7, p0\n\n"
        "    invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;\n"
        "\n    move-result-object p0\n",
    )
    method = replace_block(method, "    .line 49\n", "    .line 50\n", f"""    .line 49
    sget v3, {AVOID}->rootX:I

    sget v4, {AVOID}->rootY:I

    invoke-static {{v7, v0, v3, v4}}, {BOUNDS}->onScreen(Landroid/view/View;Landroid/view/WindowInsets;II)Ljava/util/List;

    move-result-object p0

""")
    return replace_block(method, "    if-eqz v5, :cond_7\n", "    iget v0, v3, Landroid/graphics/Rect;->top:I\n")


def local_overlap(method):
    return replace_block(method, "    new-instance p2, Landroid/graphics/Rect;\n", "    move-result p0\n\n    return p0", f"""    invoke-virtual {{p0}}, Landroid/view/View;->getWidth()I

    move-result v0

    invoke-virtual {{p0}}, Landroid/view/View;->getHeight()I

    move-result v1

    invoke-static {{p1, v0, v1}}, {INTERSECTS}

""")


def patch_avoid(smali):
    smali = edit_method(smali, ".method private static refreshGeometry(Landroid/view/View;)V", read_screen_bounds)
    smali = edit_method(smali, ".method static local(Landroid/view/View;Landroid/graphics/Rect;[I)Z", local_overlap)
    return edit_method(smali, ".method private static rebuild()V", tighten_automatic_margins)


def tighten_automatic_margins(method):
    # Leave 2 dp on each side and 1 dp above and below the detected opening.
    method = replace_once(method, "    const/high16 v3, 0x40c00000    # 6.0f",
                          "    const/high16 v3, 0x40800000    # 4.0f")
    return replace_once(method, "    const/high16 v4, 0x40800000    # 4.0f",
                        "    const/high16 v4, 0x40000000    # 2.0f")


def patch_island(smali):
    return edit_method(smali, f".method private static refreshTickerCutoutGeometry({TICKER})V",
                       lambda method: replace_block(method, "    .line 4389\n", "    .line 4392\n", f"""    .line 4389
    invoke-virtual {{p0}}, {TICKER}->syncCameraCutout()V

    const/4 v2, 0x0

"""))


target = Path(sys.argv[1])
for name, patch in (
    ("CameraTextAvoid", patch_avoid),
    ("SystemUiAicyIsland", patch_island),
):
    path = target / (name + ".smali")
    path.write_text(patch(path.read_text(encoding="utf-8")), encoding="utf-8")
