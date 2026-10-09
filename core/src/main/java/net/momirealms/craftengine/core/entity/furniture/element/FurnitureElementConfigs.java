package net.momirealms.craftengine.core.entity.furniture.element;

import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.KnownResourceException;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.core.registry.Registries;
import net.momirealms.craftengine.core.registry.WritableRegistry;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.ResourceKey;

import java.util.Optional;

public abstract class FurnitureElementConfigs {
    protected FurnitureElementConfigs() {}

    public static <E extends FurnitureElement> FurnitureElementConfigType<E> register(Key key, FurnitureElementConfigFactory<E> factory) {
        FurnitureElementConfigType<E> type = new FurnitureElementConfigType<>(key, factory);
        ((WritableRegistry<FurnitureElementConfigType<? extends FurnitureElement>>) BuiltInRegistries.FURNITURE_ELEMENT_TYPE)
                .register(ResourceKey.create(Registries.FURNITURE_ELEMENT_TYPE.location(), key), type);
        return type;
    }

    public static FurnitureElementConfig<? extends FurnitureElement> fromConfig(ConfigSection section) {
        Key type = getOrGuessType(section);
        if (Config.headDisplayAsArmorStand() && isHeadItemDisplay(section, type)) {
            type = Key.ce("armor_stand");
        }
        FurnitureElementConfigType<? extends FurnitureElement> configType = BuiltInRegistries.FURNITURE_ELEMENT_TYPE.getValue(type);
        if (configType == null) {
            throw new KnownResourceException("resource.furniture.element.unknown_type", section.assemblePath("type"), type.asString());
        }
        return configType.factory().create(section);
    }

    private static boolean isHeadItemDisplay(ConfigSection section, Key type) {
        if (!"item_display".equals(type.value())) {
            return false;
        }
        String context = section.getString("display_transform");
        if (context == null) {
            context = section.getString("display_context");
        }
        return context != null && context.equalsIgnoreCase("head");
    }

    private static Key getOrGuessType(ConfigSection section) {
        return Key.ce(Optional.ofNullable(section.getString("type")).orElseGet(() -> {
            if (section.containsKey("text")) {
                return "text_display";
            } else if (section.containsKey("item")) {
                return "item_display";
            } else if (section.containsKey("block")) {
                return "block_display";
            } else {
                // 到这里必定抛出异常
                return section.getNonNullString("type");
            }
        }));
    }
}
