package net.momirealms.craftengine.core.font;

import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates stable, per-session aliases for oversized images. The aliases are registered in
 * minecraft:default so they can be used by tab completion, where a custom font cannot be
 * specified. They are never used by regular game content.
 */
public final class ImagePreviewAliases {
    // Supplementary Private Use Area-A, far away from normal image codepoints.
    private static final int ALIAS_START = 0xF0000;
    private static final int ALIAS_END = 0xFFFFD;

    private ImagePreviewAliases() {
    }

    public static Map<Key, Alias> create(FontManager fontManager) {
        List<Map.Entry<Key, Image>> entries = new ArrayList<>(fontManager.loadedImages().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().asString()));

        Map<Key, Alias> aliases = new LinkedHashMap<>();
        int nextCodepoint = ALIAS_START;
        for (Map.Entry<Key, Image> entry : entries) {
            if (nextCodepoint > ALIAS_END) {
                break;
            }
            int row = 0;
            int column = 0;
            Image target = entry.getValue();
            if (target instanceof ReferenceImage referenceImage) {
                row = referenceImage.row();
                column = referenceImage.col();
                target = referenceImage.image();
            }
            if (!(target instanceof BitmapImage bitmap)) {
                continue;
            }
            if (bitmap.height() <= BitmapImage.EM_PREVIEW_MAX_HEIGHT) {
                continue;
            }
            int codepoint = bitmap.isValidCoordinate(row, column) ? bitmap.codepointAt(row, column) : 0;
            if (codepoint == 0) {
                int[] firstVisible = firstVisibleCell(bitmap);
                if (firstVisible == null) {
                    continue;
                }
                row = firstVisible[0];
                column = firstVisible[1];
            }
            int previewHeight = BitmapImage.EM_PREVIEW_MAX_HEIGHT;
            int previewAscent = Math.max(1, (int) Math.round(bitmap.ascent() * (double) previewHeight / bitmap.height()));
            String raw = new String(Character.toChars(nextCodepoint));
            aliases.put(entry.getKey(), new Alias(entry.getKey(), bitmap, row, column, raw, previewHeight, previewAscent));
            nextCodepoint++;
        }
        return aliases;
    }

    private static int[] firstVisibleCell(BitmapImage image) {
        for (int row = 0; row < image.rows(); row++) {
            for (int column = 0; column < image.columns(); column++) {
                if (image.codepointAt(row, column) != 0) {
                    return new int[]{row, column};
                }
            }
        }
        return null;
    }

    public record Alias(Key id, BitmapImage bitmap, int row, int column, String raw, int height, int ascent) {
    }
}
