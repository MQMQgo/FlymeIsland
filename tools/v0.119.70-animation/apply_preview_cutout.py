from pathlib import Path
import sys

from apply_animation import edit_method, replace_block, replace_once


PACKAGE = "Lio/github/flymeisland/diagnostic/"
BOUNDS = PACKAGE + "CameraCutoutBounds;"
AVOID = PACKAGE + "CameraTextAvoid;"
TICKER = PACKAGE + "CameraSafeTickerView;"
OBSERVER = "Landroid/view/ViewTreeObserver;"
LISTENER = "Landroid/view/ViewTreeObserver$OnPreDrawListener;"
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
    return edit_method(smali, ".method static local(Landroid/view/View;Landroid/graphics/Rect;[I)Z", local_overlap)


def track_preview(smali):
    smali = replace_once(smali, ".source \"CameraSafeTickerView.java\"\n",
                         f".source \"CameraSafeTickerView.java\"\n\n.implements {LISTENER}\n")
    smali = replace_once(smali, "# instance fields\n", f"""# instance fields
.field private cameraObserver:{OBSERVER}

.field private final previewCutouts:[Landroid/graphics/Rect;

.field private final previewLocation:[I

""")
    smali = edit_method(smali, ".method public constructor <init>(Landroid/content/Context;)V",
                        lambda method: replace_once(method, "    .line 66\n    return-void", f"""    .line 66
    const/4 v0, 0x1

    new-array v0, v0, [Landroid/graphics/Rect;

    new-instance v1, Landroid/graphics/Rect;

    invoke-direct {{v1}}, Landroid/graphics/Rect;-><init>()V

    const/4 v2, 0x0

    aput-object v1, v0, v2

    iput-object v0, p0, {TICKER}->previewCutouts:[Landroid/graphics/Rect;

    const/4 v3, 0x2

    new-array v0, v3, [I

    iput-object v0, p0, {TICKER}->previewLocation:[I

    return-void"""))
    smali = edit_method(smali, ".method protected onDetachedFromWindow()V",
                        lambda method: replace_once(method, "    .locals 0\n", f"""    .locals 2

    iget-object v0, p0, {TICKER}->cameraObserver:{OBSERVER}

    if-eqz v0, :camera_observer_removed

    invoke-virtual {{v0}}, {OBSERVER}->isAlive()Z

    move-result v1

    if-eqz v1, :camera_observer_removed

    invoke-virtual {{v0, p0}}, {OBSERVER}->removeOnPreDrawListener({LISTENER})V

    :camera_observer_removed
    const/4 v0, 0x0

    iput-object v0, p0, {TICKER}->cameraObserver:{OBSERVER}
"""))
    return smali + f"""
.method protected onAttachedToWindow()V
    .locals 1

    invoke-super {{p0}}, Landroid/view/View;->onAttachedToWindow()V

    invoke-virtual {{p0}}, {TICKER}->getViewTreeObserver(){OBSERVER}

    move-result-object v0

    iput-object v0, p0, {TICKER}->cameraObserver:{OBSERVER}

    invoke-virtual {{v0, p0}}, {OBSERVER}->addOnPreDrawListener({LISTENER})V

    return-void
.end method

.method public onPreDraw()Z
    .locals 1

    iget-boolean v0, p0, {TICKER}->messageMode:Z

    if-eqz v0, :camera_frame_ready

    invoke-virtual {{p0}}, {TICKER}->isShown()Z

    move-result v0

    if-eqz v0, :camera_frame_ready

    invoke-virtual {{p0}}, {TICKER}->syncCameraCutout()V

    :camera_frame_ready
    const/4 v0, 0x1

    return v0
.end method

.method final syncCameraCutout()V
    .locals 3

    iget-object v0, p0, {TICKER}->previewCutouts:[Landroid/graphics/Rect;

    const/4 v1, 0x0

    aget-object v1, v0, v1

    iget-object v2, p0, {TICKER}->previewLocation:[I

    invoke-static {{p0, v1, v2}}, {AVOID}->local(Landroid/view/View;Landroid/graphics/Rect;[I)Z

    move-result v1

    if-nez v1, :apply_camera_cutout

    const/4 v0, 0x0

    :apply_camera_cutout
    const/4 v1, 0x1

    invoke-virtual {{p0, v0, v1}}, {TICKER}->setCameraCutouts([Landroid/graphics/Rect;Z)V

    return-void
.end method
"""


def patch_ticker(smali):
    smali = track_preview(smali)
    # Clip the text row after resolving the cutout shape. Comparing full
    # bounds also keeps an unchanged, partially visible hole from restarting the ticker.
    smali = edit_method(smali, ".method public setCameraCutouts([Landroid/graphics/Rect;Z)V",
                        lambda method: replace_once(method,
                            "    invoke-virtual {v4, v2, v2, v3, v5}, Landroid/graphics/Rect;->intersect(IIII)Z\n",
                            f"    invoke-static {{v4, v3, v5}}, {INTERSECTS}\n"))
    smali = edit_method(smali, ".method private mergedCameraX()[I",
                        lambda method: replace_once(method, "    if-le v4, v0, :cond_3\n", f"""    invoke-static {{v3, v0}}, Ljava/lang/Math;->max(II)I

    move-result v0

    invoke-virtual {{p0}}, {TICKER}->getWidth()I

    move-result v5

    invoke-static {{v4, v5}}, Ljava/lang/Math;->min(II)I

    move-result v4

    if-le v4, v0, :cond_3
"""))
    # The ticker can show its cutout-aware ellipsis even when scrolling is disabled.
    smali = edit_method(smali, ".method public canRenderMessage()Z",
                        lambda method: replace_block(method, "    .line 171\n", "    .line 170\n    :cond_3\n", """    .line 171
    :cond_0
    const/4 v0, 0x1

    return v0

"""))
    return edit_method(smali, ".method protected onDraw(Landroid/graphics/Canvas;)V",
                       lambda method: replace_once(method, "    if-nez v2, :cond_5\n", f"""    if-nez v2, :cond_5

    iget-boolean v2, p0, {TICKER}->scrollEnabled:Z

    if-eqz v2, :cond_1
"""))


def patch_island(smali):
    return edit_method(smali, f".method private static refreshTickerCutoutGeometry({TICKER})V",
                       lambda method: replace_block(method, "    .line 4389\n", "    .line 4392\n", f"""    .line 4389
    invoke-virtual {{p0}}, {TICKER}->syncCameraCutout()V

    const/4 v2, 0x0

"""))


target = Path(sys.argv[1])
for name, patch in (
    ("CameraTextAvoid", patch_avoid),
    ("CameraSafeTickerView", patch_ticker),
    ("SystemUiAicyIsland", patch_island),
):
    path = target / (name + ".smali")
    path.write_text(patch(path.read_text(encoding="utf-8")), encoding="utf-8")
