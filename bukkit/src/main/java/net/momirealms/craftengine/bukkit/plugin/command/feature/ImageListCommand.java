package net.momirealms.craftengine.bukkit.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.momirealms.craftengine.bukkit.plugin.command.BukkitCommandFeature;
import net.momirealms.craftengine.bukkit.util.ImageUtils;
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
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
                .optional("images", StringParser.stringComponent().suggestionProvider((context, input) -> {
                    // Paper servers use ImageTabCompleter to replace these with image glyphs.
                    // This text fallback keeps the command usable on non-Paper platforms.
                    List<Suggestion> suggestions = new ArrayList<>();
                    suggestions.add(Suggestion.suggestion("pages"));
                    for (Map.Entry<Key, Image> entry : allImages()) {
                        suggestions.add(Suggestion.suggestion(entry.getKey().asString()));
                    }
                    return CompletableFuture.completedFuture(suggestions);
                }))
                .optional("page", IntegerParser.integerParser(), (context, input) -> {
                    String query = normalizeFilter(context.getOrDefault("images", null));
                    int totalPages = totalPages(query == null ? allImages() : matchImages(allImages(), query));
                    List<Suggestion> suggestions = new ArrayList<>(totalPages);
                    for (int i = 1; i <= totalPages; i++) {
                        suggestions.add(Suggestion.suggestion(String.valueOf(i)));
                    }
                    return CompletableFuture.completedFuture(suggestions);
                })
                .handler(context -> {
                    Sender sender = plugin().senderFactory().wrap(context.sender());
                    String rawQuery = context.getOrDefault("images", null);
                    Integer pageArgument = context.getOrDefault("page", null);
                    int requestedPage = pageArgument == null ? 1 : pageArgument;

                    String query = rawQuery;
                    if (query != null) {
                        if (query.equalsIgnoreCase("pages")) {
                            query = null;
                        } else {
                            Integer numericQuery = tryParseInt(query);
                            if (numericQuery != null) {
                                if (pageArgument == null) {
                                    requestedPage = numericQuery;
                                }
                                query = null;
                            }
                        }
                    }

                    List<Map.Entry<Key, Image>> images = allImages();
                    if (query != null && !query.isBlank()) {
                        images = matchImages(images, query);
                    }
                    if (images.isEmpty()) {
                        if (query != null && !query.isBlank()) {
                            sender.sendMessage(Component.text("No images found for '" + query + "'.", NamedTextColor.GRAY));
                        } else {
                            sender.sendMessage(Component.text("No images loaded.", NamedTextColor.GRAY));
                        }
                        return;
                    }

                    int totalPages = totalPages(images);
                    int page = Math.max(1, Math.min(requestedPage, totalPages));
                    int start = (page - 1) * PAGE_SIZE;
                    int end = Math.min(start + PAGE_SIZE, images.size());

                    // Send the whole page as one component. Sending each line as a separate
                    // packet can make the footer appear between entries on some clients.
                    Component pageComponent = Component.empty()
                            .append(Component.text("Images ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("(" + page + "/" + totalPages + ")", NamedTextColor.GRAY))
                            .append(Component.text(query == null || query.isBlank()
                                    ? " - " + images.size() + " total"
                                    : " - " + images.size() + " matching '" + query + "'", NamedTextColor.DARK_GRAY));

                    for (int i = start; i < end; i++) {
                        Map.Entry<Key, Image> entry = images.get(i);
                        pageComponent = pageComponent
                                .append(Component.newline())
                                .append(renderImage(i - start + 1, entry.getKey(), entry.getValue()));
                    }

                    pageComponent = pageComponent
                            .append(Component.newline())
                            .append(renderFooter(commandBase(), query, page, totalPages));

                    sender.sendMessage(pageComponent);
                });
    }

    private List<Map.Entry<Key, Image>> allImages() {
        return ImageUtils.sortedImages(plugin());
    }

    private static int totalPages(List<Map.Entry<Key, Image>> images) {
        return Math.max(1, (images.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static String normalizeFilter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.equalsIgnoreCase("pages")) return null;
        if (tryParseInt(raw) != null) return null;
        return raw;
    }

    private static Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<Map.Entry<Key, Image>> matchImages(List<Map.Entry<Key, Image>> images, String query) {
        if (query == null || query.isBlank()) {
            return images;
        }
        List<Map.Entry<Key, Image>> symbolMatches = new ArrayList<>();
        for (Map.Entry<Key, Image> entry : images) {
            ImageUtils.RawCell rawCell = ImageUtils.resolveRawCell(entry.getValue());
            if (rawCell != null && rawCell.raw().equals(query)) {
                symbolMatches.add(entry);
            }
        }
        if (!symbolMatches.isEmpty()) {
            return symbolMatches;
        }
        return filterImagesByName(images, query);
    }

    private static List<Map.Entry<Key, Image>> filterImagesByName(List<Map.Entry<Key, Image>> images, String query) {
        List<Map.Entry<Key, Image>> exactMatches = new ArrayList<>();
        for (Map.Entry<Key, Image> entry : images) {
            if (entry.getKey().asString().equalsIgnoreCase(query)) {
                exactMatches.add(entry);
            }
        }
        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<Map.Entry<Key, Image>> matches = new ArrayList<>();
        for (Map.Entry<Key, Image> entry : images) {
            if (entry.getKey().asString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                matches.add(entry);
            }
        }
        return matches;
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

    private static Component renderFooter(String commandBase, String query, int page, int totalPages) {
        String commandPrefix = query == null || query.isBlank()
                ? commandBase + " pages "
                : commandBase + " " + query + " ";
        Component previous = page > 1
                ? Component.text("[< Prev]", NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Page " + (page - 1), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(commandPrefix + (page - 1)))
                : Component.text("[< Prev]", NamedTextColor.DARK_GRAY);

        Component next = page < totalPages
                ? Component.text("[Next >]", NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Page " + (page + 1), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(commandPrefix + (page + 1)))
                : Component.text("[Next >]", NamedTextColor.DARK_GRAY);

        return Component.text("  ")
                .append(previous)
                .append(Component.text(" Page " + page + "/" + totalPages + " ", NamedTextColor.GRAY))
                .append(next);
    }

    private static Cell resolveCell(Image image) {
        ImageUtils.RawCell rawCell = ImageUtils.resolveRawCell(image);
        if (rawCell == null) {
            return null;
        }
        BitmapImage bitmap = rawCell.bitmap();
        Key font = bitmap.height() > BitmapImage.EM_PREVIEW_MAX_HEIGHT ? bitmap.previewFont() : bitmap.font();
        Component preview = Component.text(rawCell.raw())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .font(net.kyori.adventure.key.Key.key(font.namespace(), font.value()));
        return new Cell(rawCell.raw(), preview, bitmap);
    }

    private record Cell(String raw, Component preview, BitmapImage bitmap) {
    }

    @Override
    public String getFeatureID() {
        return "images";
    }
}
