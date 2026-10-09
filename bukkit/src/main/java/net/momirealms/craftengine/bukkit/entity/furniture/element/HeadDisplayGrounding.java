package net.momirealms.craftengine.bukkit.entity.furniture.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemDefinition;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.model.definition.BaseItemModel;
import net.momirealms.craftengine.core.pack.model.definition.ItemModel;
import net.momirealms.craftengine.core.pack.model.definition.ModernItemModel;
import net.momirealms.craftengine.core.pack.model.legacy.LegacyOverridesModel;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Item display context {@code head} applies the model json {@code display.head}
 * transform and then centers the cube with {@code (-0.5, -0.5, -0.5)}.
 * Blockbench writes that translation so the model sits on an armor-stand head.
 * On a furniture item display the same translation drops the model into the ground.
 * This lifts the entity so model-space {@code y = 0} lands on the furniture anchor.
 */
final class HeadDisplayGrounding {
    private HeadDisplayGrounding() {}

    static float lift(Key itemId) {
        try {
            Key model = resolveModel(itemId);
            if (model == null) {
                return 0f;
            }
            JsonObject head = readHead(model, 0);
            if (head == null) {
                return 0f;
            }
            return groundLift(head);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private static float groundLift(JsonObject head) {
        float[] translation = vector(head.get("translation"), 0f, 0f, 0f);
        float[] scale = vector(head.get("scale"), 1f, 1f, 1f);
        float[] rotation = vector(head.get("rotation"), 0f, 0f, 0f);
        Vector3f corner = new Vector3f(-0.5f, -0.5f, -0.5f).mul(scale[0], scale[1], scale[2]);
        if (rotation[0] != 0f || rotation[1] != 0f || rotation[2] != 0f) {
            new Quaternionf()
                    .rotationXYZ((float) Math.toRadians(rotation[0]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]))
                    .transform(corner);
        }
        corner.add(translation[0] / 16f, translation[1] / 16f, translation[2] / 16f);
        return -corner.y;
    }

    private static float[] vector(@Nullable JsonElement element, float x, float y, float z) {
        if (element == null || element.isJsonNull()) {
            return new float[]{x, y, z};
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            float value = element.getAsFloat();
            return new float[]{value, value, value};
        }
        if (!element.isJsonArray()) {
            return new float[]{x, y, z};
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > 0 && array.get(0).isJsonPrimitive()) x = array.get(0).getAsFloat();
        if (array.size() > 1 && array.get(1).isJsonPrimitive()) y = array.get(1).getAsFloat();
        if (array.size() > 2 && array.get(2).isJsonPrimitive()) z = array.get(2).getAsFloat();
        return new float[]{x, y, z};
    }

    @Nullable
    private static Key resolveModel(Key itemId) {
        ItemManager manager = CraftEngine.instance().itemManager();
        Item item = Item.byId(itemId);
        if (item != null) {
            Key fromItem = modelFromItem(manager, item);
            if (fromItem != null) {
                return fromItem;
            }
        }
        return geometryPath(manager.modernItemModels1_21_4().get(itemId));
    }

    @Nullable
    private static Key modelFromItem(ItemManager manager, Item item) {
        Key fromComponent = item.itemModel().map(Key::of).orElse(null);
        if (fromComponent != null) {
            Key geometry = geometryPath(manager.modernItemModels1_21_4().get(fromComponent));
            if (geometry != null) {
                return geometry;
            }
            Key legacy = firstOverride(manager.modernItemModels1_21_2().get(fromComponent), item.customModelData().orElse(0));
            if (legacy != null) {
                return legacy;
            }
            return fromComponent;
        }
        int cmd = item.customModelData().orElse(0);
        if (cmd <= 0) {
            return null;
        }
        Key material = item.getDefinition().map(ItemDefinition::clientBoundMaterial).orElse(null);
        if (material == null) {
            return null;
        }
        TreeMap<Integer, ModernItemModel> modern = manager.modernItemOverrides().get(material);
        if (modern != null) {
            Key geometry = geometryPath(modern.get(cmd));
            if (geometry != null) {
                return geometry;
            }
        }
        return firstOverride(manager.legacyItemOverrides().get(material), cmd);
    }

    @Nullable
    private static Key geometryPath(@Nullable ModernItemModel modern) {
        if (modern == null) {
            return null;
        }
        return geometryPath(modern.model());
    }

    @Nullable
    private static Key geometryPath(@Nullable ItemModel model) {
        if (model instanceof BaseItemModel base) {
            return base.path();
        }
        return null;
    }

    @Nullable
    private static Key firstOverride(@Nullable TreeSet<LegacyOverridesModel> overrides, int cmd) {
        if (overrides == null) {
            return null;
        }
        for (LegacyOverridesModel override : overrides) {
            if (override.customModelData() == cmd) {
                return override.model();
            }
            if (override.customModelData() > cmd) {
                break;
            }
        }
        return null;
    }

    @Nullable
    private static JsonObject readHead(Key model, int depth) throws IOException {
        if (depth > 4) {
            return null;
        }
        Path file = findModelFile(model);
        if (file == null) {
            return null;
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (root.has("display") && root.get("display").isJsonObject()) {
            JsonObject display = root.getAsJsonObject("display");
            if (display.has("head") && display.get("head").isJsonObject()) {
                return display.getAsJsonObject("head");
            }
            return null;
        }
        Key next = referencedModel(root);
        if (next == null || next.equals(model)) {
            return null;
        }
        return readHead(next, depth + 1);
    }

    @Nullable
    private static Key referencedModel(JsonObject root) {
        if (root.has("parent") && root.get("parent").isJsonPrimitive()) {
            return Key.of(root.get("parent").getAsString());
        }
        if (!root.has("model")) {
            return null;
        }
        JsonElement model = root.get("model");
        if (model.isJsonPrimitive()) {
            return Key.of(model.getAsString());
        }
        if (model.isJsonObject()) {
            JsonObject object = model.getAsJsonObject();
            if (object.has("model") && object.get("model").isJsonPrimitive()) {
                return Key.of(object.get("model").getAsString());
            }
        }
        return null;
    }

    @Nullable
    private static Path findModelFile(Key model) {
        for (Pack pack : CraftEngine.instance().packManager().loadedPacks()) {
            for (Path folder : pack.resourcePackFolders()) {
                Path file = folder.resolve("assets").resolve(model.namespace()).resolve("models").resolve(model.value() + ".json");
                if (Files.isRegularFile(file)) {
                    return file;
                }
            }
        }
        return null;
    }
}
