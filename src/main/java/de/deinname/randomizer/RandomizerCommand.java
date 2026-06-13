package de.deinname.randomizer;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RandomizerCommand implements CommandExecutor {

    private final RandomizerPlugin plugin;

    public RandomizerCommand(RandomizerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            plugin.getGui().openSettingsGUI(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {

            if (args.length == 1) {
                ItemStack handItem = p.getInventory().getItemInMainHand();
                if (handItem.getType() == Material.AIR) {
                    p.sendMessage("§cHold an item in your hand or specify one: /randomizer info <Item/Mob>");
                    return true;
                }
                plugin.getGui().openSelectionGUI(p, handItem.getType());
                return true;
            }

            else {
                String targetName = args[1].toUpperCase();

                try {
                    EntityType type = EntityType.valueOf(targetName);
                    if (type.isAlive() && type.isSpawnable()) {
                        plugin.getGui().openMobChainGUI(p, type);
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {}

                try {
                    Material mat = Material.valueOf(targetName);
                    if (mat.isItem() || mat.isBlock()) {
                        plugin.getGui().openSelectionGUI(p, mat);
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {}

                p.sendMessage("§cItem or Mob '" + targetName + "' not found.");
                return true;
            }
        }

        p.sendMessage("§cUsage: /randomizer info [Item/Mob]");
        return true;
    }
}