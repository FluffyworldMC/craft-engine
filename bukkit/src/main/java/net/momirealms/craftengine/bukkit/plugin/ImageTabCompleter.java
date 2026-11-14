package net.momirealms.craftengine.bukkit.plugin;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import net.momirealms.craftengine.bukkit.util.ImageUtils;
import net.momirealms.craftengine.core.font.Image;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replaces the text suggestions of /em, /e and /images with the actual image glyphs.
 * Paper's AsyncTabCompleteEvent lets us return plain completions without Cloud's
 * suggestion pipeline, which is what makes the glyphs render in the client list.
 */
public final class ImageTabCompleter implements Listener {
    private static final String[] COMMANDS = {"/em ", "/e ", "/images "};
    private final CraftEngine plugin;

    public ImageTabCompleter(CraftEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!event.isCommand()) {
            return;
        }
        String buffer = event.getBuffer();
        String lowerBuffer = buffer.toLowerCase(Locale.ROOT);
        String commandPrefix = null;
        for (String command : COMMANDS) {
            if (lowerBuffer.startsWith(command)) {
                commandPrefix = command;
                break;
            }
        }
        if (commandPrefix == null) {
            return;
        }
        String argument = buffer.substring(commandPrefix.length());
        // The second argument is the page number, keep Cloud's page suggestions.
        if (argument.indexOf(' ') >= 0) {
            return;
        }

        String lowerArgument = argument.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if (argument.isEmpty() || "pages".startsWith(lowerArgument)) {
            completions.add("pages");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<Key, Image> entry : ImageUtils.sortedImages(this.plugin)) {
            if (!argument.isEmpty() && !entry.getKey().asString().toLowerCase(Locale.ROOT).contains(lowerArgument)) {
                continue;
            }
            ImageUtils.RawCell rawCell = ImageUtils.resolveRawCell(entry.getValue());
            if (rawCell == null || rawCell.raw().isEmpty() || !seen.add(rawCell.raw())) {
                continue;
            }
            completions.add(rawCell.raw());
        }
        event.setCompletions(completions);
        event.setHandled(true);
    }
}
