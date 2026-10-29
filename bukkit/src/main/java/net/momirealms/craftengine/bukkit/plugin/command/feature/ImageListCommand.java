package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.core.font.BitmapImage;
import net.momirealms.craftengine.core.font.Image;
import net.momirealms.craftengine.core.font.ReferenceImage;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.command.CraftEngineCommandManager;
import net.momirealms.craftengine.core.plugin.command.sender.Sender;
import net.momirealms.craftengine.core.util.FormatUtils;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.IntegerParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lists loaded images. The output is sent directly to the command sender so other players
 * never see it. A separate preview font is used for oversized images, keeping this list
 * compact without changing the real image definition.
 */
public final class ImageListCommand extends BukkitCommandFeature<CommandSender> {
    private static final int PAGE_SIZE = 10;

    public ImageListCommand(CraftEngineCommandManager<CommandSender> commandManager, CraftEngine plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .optional("page", IntegerParser.integerParser(1))
                .handler(context -> {
                    Sender sender = plugin().senderFactory().wrap(context.sender());
                    List<Map.Entry<Key, Image>> images = new ArrayList<>(plugin().fontManager().loadedImages().entrySet());
                    images.sort(Comparator.comparing(entry -> entry.getKey().asString()));
                    if (images.isEmpty()) {
                        sender.sendMessage(Component.text("No images loaded.", NamedTextColor.GRAY));
                        return;
                    }

                    int totalPages = (images.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                    int page = Math.max(1, Math.min(context.getOrDefault("page", 1), totalPages));
                    int start = (page - 1) * PAGE_SIZE;
                    int end = Math.min(start + PAGE_SIZE, images.size());

                    // Send the whole page as one component. Sending each line as a separate
                    // packet can make the footer appear between entries on some clients.
                    Component pageComponent = Component.empty()
                            .append(Component.text("Images ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("(" + page + "/" + totalPages + ")", NamedTextColor.GRAY))
                            .append(Component.text(" - " + images.size() + " total", NamedTextColor.DARK_GRAY));

                    for (int i = start; i < end; i++) {
                        Map.Entry<Key, Image> entry = images.get(i);
                        pageComponent = pageComponent
                                .append(Component.newline())
                                .append(renderImage(i - start + 1, entry.getKey(), entry.getValue()));
                    }

                    pageComponent = pageComponent
                            .append(Component.newline())
                            .append(renderFooter(commandBase(), page, totalPages));

                    sender.sendMessage(pageComponent);
                });
    }

    private static Component renderImage(int index, Key id, Image image) {
        Cell cell = resolveCell(image);
        Component preview;
        if (cell == null) {
            preview = Component.text("?", NamedTextColor.DARK_GRAY);
        } else {
            preview = cell.preview()
                    .hoverEvent(HoverEvent.showText(buildHover(id, image, cell)))
                    .clickEvent(ClickEvent.copyToClipboard(cell.raw()));
        }

        Component idComponent = Component.text(id.asString(), NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy the image id", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.copyToClipboard(id.asString()));

        Component line = Component.text(" " + index + ". ", NamedTextColor.DARK_GRAY)
                .append(preview)
                .append(Component.text(" "))
                .append(idComponent);

        if (cell != null) {
            line = line.append(Component.text(" [copy]", NamedTextColor.DARK_GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy the symbol", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.copyToClipboard(cell.raw())));
            String miniMessage = FormatUtils.miniMessageFont(cell.raw(), cell.bitmap().font().asString());
            line = line.append(Component.text(" [mm]", NamedTextColor.DARK_GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy the MiniMessage tag", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.copyToClipboard(miniMessage)));
        }
        return line;
    }

    private static Component buildHover(Key id, Image image, Cell cell) {
        BitmapImage bitmap = cell.bitmap();
        Component hover = Component.text("Image: ", NamedTextColor.GRAY)
                .append(Component.text(id.asString(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("Font: ", NamedTextColor.GRAY))
                .append(Component.text(bitmap.font().asString(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("File: ", NamedTextColor.GRAY))
                .append(Component.text(bitmap.file(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Grid: ", NamedTextColor.GRAY))
                .append(Component.text(bitmap.rows() + "x" + bitmap.columns(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Size: ", NamedTextColor.GRAY))
                .append(Component.text("height " + bitmap.height() + ", ascent " + bitmap.ascent(), NamedTextColor.WHITE));
        if (image instanceof ReferenceImage referenceImage) {
            hover = hover.append(Component.newline())
                    .append(Component.text("Reference: ", NamedTextColor.GRAY))
                    .append(Component.text(referenceImage.reference().asString() + ":" + referenceImage.row() + ":" + referenceImage.col(), NamedTextColor.WHITE));
        }
        if (bitmap.height() > BitmapImage.EM_PREVIEW_MAX_HEIGHT) {
            hover = hover.append(Component.newline())
                    .append(Component.text("Preview scaled to " + BitmapImage.EM_PREVIEW_MAX_HEIGHT + "px for /em only", NamedTextColor.YELLOW));
        }
        return hover.append(Component.newline())
                .append(Component.text("Click the symbol to copy it", NamedTextColor.GREEN));
    }

    private String commandBase() {
        if (commandConfig != null && !commandConfig.getUsages().isEmpty()) {
            String usage = commandConfig.getUsages().getFirst();
            return usage.startsWith("/") ? usage : "/" + usage;
        }
        return "/em";
    }

    private static Component renderFooter(String commandBase, int page, int totalPages) {
        Component previous = page > 1
                ? Component.text("[< Prev]", NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Page " + (page - 1), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(commandBase + " " + (page - 1)))
                : Component.text("[< Prev]", NamedTextColor.DARK_GRAY);

        Component next = page < totalPages
                ? Component.text("[Next >]", NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Page " + (page + 1), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(commandBase + " " + (page + 1)))
                : Component.text("[Next >]", NamedTextColor.DARK_GRAY);

        return Component.text("  ")
                .append(previous)
                .append(Component.text(" Page " + page + "/" + totalPages + " ", NamedTextColor.GRAY))
                .append(next);
    }

    private static Cell resolveCell(Image image) {
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
        String raw = new String(Character.toChars(codepoint));
        Key font = bitmap.height() > BitmapImage.EM_PREVIEW_MAX_HEIGHT ? bitmap.previewFont() : bitmap.font();
        Component preview = Component.text(raw)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .font(net.kyori.adventure.key.Key.key(font.namespace(), font.value()));
        return new Cell(raw, preview, bitmap);
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

    private record Cell(String raw, Component preview, BitmapImage bitmap) {
    }

    @Override
    public String getFeatureID() {
        return "images";
    }
}
