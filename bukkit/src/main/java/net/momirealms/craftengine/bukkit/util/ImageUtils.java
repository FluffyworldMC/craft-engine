package net.momirealms.craftengine.bukkit.util;

import net.momirealms.craftengine.core.font.BitmapImage;
import net.momirealms.craftengine.core.font.Image;
import net.momirealms.craftengine.core.font.ReferenceImage;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ImageUtils {
    private ImageUtils() {
    }

    public static List<Map.Entry<Key, Image>> sortedImages(CraftEngine plugin) {
        List<Map.Entry<Key, Image>> images = new ArrayList<>(plugin.fontManager().loadedImages().entrySet());
        images.sort(Comparator.comparing(entry -> entry.getKey().asString()));
        return images;
    }

    public static RawCell resolveRawCell(Image image) {
        int row = 0;
        int column = 0;
        Image target = image;
        if (image instanceof ReferenceImage referenceImage) {
            row = referenceImage.row();
            column = referenceImage.col();
            target = referenceImage.image();
        }
        if (!(target instanceof BitmapImage bitmap)) {
            return null;
        }
        int codepoint = bitmap.isValidCoordinate(row, column) ? bitmap.codepointAt(row, column) : 0;
        if (codepoint == 0) {
            int[] firstVisible = firstVisibleCell(bitmap);
            if (firstVisible == null) {
                return null;
            }
            row = firstVisible[0];
            column = firstVisible[1];
            codepoint = bitmap.codepointAt(row, column);
        }
        return new RawCell(new String(Character.toChars(codepoint)), bitmap);
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

    public record RawCell(String raw, BitmapImage bitmap) {
    }
}
