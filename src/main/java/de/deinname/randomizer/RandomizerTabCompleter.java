package de.deinname.randomizer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RandomizerTabCompleter implements TabCompleter {

    private final RandomizerPlugin plugin;

    public RandomizerTabCompleter(RandomizerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("gui");
            suggestions.add("info");
            return StringUtil.copyPartialMatches(args[0], suggestions, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            if (plugin.randomMobLoot) {
                addMobs(suggestions, args[1]);
            }
            return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
        }

        return Collections.emptyList();
    }

    private void addMobs(List<String> list, String input) {
        input = input.toUpperCase();
        for (EntityType t : EntityType.values()) {
            if (t.isAlive() && t.isSpawnable() && t.name().startsWith(input)) {
                list.add(t.name());
            }
        }
    }
}