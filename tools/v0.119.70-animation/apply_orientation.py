from pathlib import Path
import sys

from apply_animation import edit_method, replace_once


PACKAGE = "Lio/github/flymeisland/diagnostic/"
LISTENER = PACKAGE + "MusicListenerService;"
ISLAND = PACKAGE + "SystemUiAicyIsland;"

READ_ORIENTATION = """    invoke-virtual {{{context}}}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {{v0}}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    iget v0, v0, Landroid/content/res/Configuration;->orientation:I
"""

PUBLISH_ORIENTATION = READ_ORIENTATION.format(context="v2") + f"""
    const/4 v3, 0x2    # Configuration.ORIENTATION_LANDSCAPE

    if-ne v0, v3, :publish_media

    invoke-direct {{v2}}, {LISTENER}->hideMediaForLandscape()V

    return-void

    :publish_media
"""

MEDIA_CONFIGURATION = f""".method public onConfigurationChanged(Landroid/content/res/Configuration;)V
    .locals 2

    invoke-super {{p0, p1}}, Landroid/service/notification/NotificationListenerService;->onConfigurationChanged(Landroid/content/res/Configuration;)V

    iget-boolean v0, p0, {LISTENER}->connected:Z

    if-eqz v0, :done

    iget v0, p1, Landroid/content/res/Configuration;->orientation:I

    const/4 v1, 0x2    # Configuration.ORIENTATION_LANDSCAPE

    if-ne v0, v1, :refresh_media

    invoke-direct {{p0}}, {LISTENER}->hideMediaForLandscape()V

    return-void

    :refresh_media
    invoke-direct {{p0}}, {LISTENER}->requestPublish()V

    :done
    return-void
.end method

.method private hideMediaForLandscape()V
    .locals 2

    iget-boolean v0, p0, {LISTENER}->mediaPublished:Z

    if-eqz v0, :done

    const/4 v0, 0x0

    iput-boolean v0, p0, {LISTENER}->mediaPublished:Z

    const-string v1, "island:media"

    invoke-static {{p0, v1}}, {PACKAGE}SystemUiAicyClient;->removeState(Landroid/content/Context;Ljava/lang/String;)V

    const/4 v1, 0x0

    invoke-static {{v1, v0}}, {PACKAGE}AppAudioWaveService;->updatePlayer(Ljava/lang/String;Z)V

    iget-object v0, p0, {LISTENER}->waveCapture:{PACKAGE}AudioWaveCapture;

    if-eqz v0, :done

    invoke-virtual {{v0}}, {PACKAGE}AudioWaveCapture;->stop()V

    :done
    return-void
.end method
"""

SKIP_LANDSCAPE_MUSIC = f""".method private static skipLandscapeMusic(Landroid/content/Context;Ljava/lang/String;)Z
    .locals 2

    const-string v0, "MUSIC"

    invoke-virtual {{v0, p1}}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :allow

""" + READ_ORIENTATION.format(context="p0") + f"""
    const/4 v1, 0x2    # Configuration.ORIENTATION_LANDSCAPE

    if-ne v0, v1, :allow

    const-string v0, "island:media"

    sget-object v1, {ISLAND}->ACTIVE:Ljava/util/LinkedHashMap;

    invoke-virtual {{v1, v0}}, Ljava/util/LinkedHashMap;->containsKey(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :skip

    invoke-static {{v0}}, {ISLAND}->removeSource(Ljava/lang/String;)V

    :skip
    const/4 v0, 0x1

    return v0

    :allow
    const/4 v0, 0x0

    return v0
.end method
"""

SHOW_ORIENTATION = f"""    invoke-static {{v1, v2}}, {ISLAND}->skipLandscapeMusic(Landroid/content/Context;Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :show_in_orientation

    const-string v0, "横屏隐藏音乐胶囊"

    return-object v0

    :show_in_orientation
"""


def patch_listener(smali):
    entry = "    .line 881\n    move-object/from16 v2, p0\n\n"
    smali = edit_method(
        smali, ".method private publishCurrent()V",
        lambda method: replace_once(method, entry, entry + PUBLISH_ORIENTATION),
    )
    return smali + "\n" + MEDIA_CONFIGURATION


def patch_island(smali):
    # The renderer also checks orientation for any media update queued before rotation.
    entry = "    .line 1394\n    :cond_2\n"
    smali = edit_method(
        smali,
        ".method private static show(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;[BLandroid/app/PendingIntent;Ljava/lang/String;Ljava/lang/String;IIIIIZJZI)Ljava/lang/String;",
        lambda method: replace_once(method, entry, entry + SHOW_ORIENTATION),
    )
    return smali + "\n" + SKIP_LANDSCAPE_MUSIC


target = Path(sys.argv[1])
for name, patch in (("MusicListenerService", patch_listener), ("SystemUiAicyIsland", patch_island)):
    path = target / (name + ".smali")
    path.write_text(patch(path.read_text(encoding="utf-8")), encoding="utf-8")
