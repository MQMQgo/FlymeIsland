package io.github.flymeisland.diagnostic;

import java.util.Locale;

final class MediaActionFilter {
    private MediaActionFilter() {}

    static boolean isLyrics(String action, String label) {
        String description = (action + " " + label).toLowerCase(Locale.ROOT);
        return description.contains("lyric") || description.contains("歌词") || description.contains("歌詞");
    }
}
