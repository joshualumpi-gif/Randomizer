package de.deinname.randomizer;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class RandomizerGUI implements Listener {

    private final RandomizerPlugin plugin;

    private final String TEX_ARROW_RIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWYxMzNlOTE5MTlkYjBhY2VmZGMyNzJkNjdmZDg3YjRiZTg4ZGM0NGE5NTg5NTg4MjQ0NzRlMjFlMDZkNTNlNiJ9fX0=";
    private final String TEX_SECRET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMyNzEwNTI3MTllZjY0MDc5ZWU4YzE0OTg5NTEyMzhhNzRkYWM0YzI3Yjk1NjQwZGI2ZmJkZGMyZDZiNWI2ZSJ9fX0=";

    private final Map<UUID, Integer> pageCache = new HashMap<>();
    private final Map<UUID, String> searchQueries = new HashMap<>();
    private final Map<UUID, String> navContext = new HashMap<>();
    private final Set<UUID> searchingPlayers = new HashSet<>(); // Zurückgebracht für Chat Search

    private final int[] sourceSlots = {20, 18, 16, 14, 12, 10};
    private final int[] sourceArrowSlots = {21, 19, 17, 15, 13, 11};
    private final int[] resultSlots = {24, 26, 28, 30, 32, 34};
    private final int[] resultArrowSlots = {23, 25, 27, 29, 31, 33};

    public RandomizerGUI(RandomizerPlugin plugin) {
        this.plugin = plugin;
    }

    public void openSelectionGUI(Player p, Material target) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Item Information", NamedTextColor.DARK_GRAY));

        inv.setItem(22, createItem(target, "§e" + formatName(target.name()), "§7Target Item"));

        if (plugin.randomCrafting) {
            inv.setItem(20, createItem(Material.CRAFTING_TABLE, "§fCrafting Chain", "§7View recipes and results"));
        } else {
            inv.setItem(20, createItem(Material.CRAFTING_TABLE, "§fCrafting Chain", "§cDisabled", "§7(Vanilla applies)"));
        }

        if (plugin.randomBlockDrops) {
            inv.setItem(24, createItem(Material.IRON_PICKAXE, "§fBlock Chain", "§7View sources and drops"));
        } else {
            inv.setItem(24, createItem(Material.IRON_PICKAXE, "§fBlock Chain", "§cDisabled", "§7(Vanilla applies)"));
        }

        inv.setItem(49, createItem(Material.BARRIER, "§cBack"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openChainGUI(Player p, Material target, String type) {
        String titleText = type.equals("BLOCK") ? "Block Chain" : "Crafting Chain";
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(titleText, NamedTextColor.DARK_GRAY));

        inv.setItem(22, createItem(target, "§e" + formatName(target.name()), "§7Target Item"));

        RandomizerManager rm = plugin.getManager();
        DiscoveryManager dm = plugin.getDiscovery();

        Material currentLeft = target;
        boolean hasLeft = false;

        for (int i = 0; i < 6; i++) {
            Material leftMat;
            String arrowName;

            if (type.equals("BLOCK")) {
                leftMat = rm.getBlockSource(currentLeft, p);
                arrowName = "§7Drops";
            } else {
                ItemStack res = rm.getRandomCraftingResult(currentLeft, p);
                leftMat = res != null ? res.getType() : null;
                arrowName = "§7Crafted from";
            }

            if (leftMat == null || leftMat == currentLeft || leftMat == Material.AIR) break;
            if (type.equals("BLOCK") && !isValidBlock(leftMat)) break;

            hasLeft = true;
            boolean discovered = !plugin.wikiDiscoveryMode || (type.equals("BLOCK") ? dm.hasDiscoveredBlock(p, leftMat) : dm.hasDiscoveredRecipe(p, leftMat));

            inv.setItem(sourceArrowSlots[i], createSkull(TEX_ARROW_RIGHT, arrowName));

            if (discovered) {
                inv.setItem(sourceSlots[i], createItem(leftMat, "§f" + formatName(leftMat.name()), "§eClick to center"));
            } else {
                inv.setItem(sourceSlots[i], createSkull(TEX_SECRET, "§7Unknown", "§7Discover to reveal"));
                break;
            }
            currentLeft = leftMat;
        }

        if (hasLeft) inv.setItem(45, createItem(Material.ARROW, "§fShift Left", "§7Navigate backward"));

        boolean canGoRight = type.equals("BLOCK") ? isValidBlock(target) : true;
        boolean hasRight = false;

        if (canGoRight) {
            Material currentRight = target;
            for (int i = 0; i < 6; i++) {
                Material rightMat;
                String arrowName;

                if (type.equals("BLOCK")) {
                    rightMat = rm.getRandomBlockDrop(currentRight, p);
                    arrowName = "§7Drops from";
                } else {
                    rightMat = rm.getCraftingSource(currentRight, p);
                    arrowName = "§7Crafts into";
                }

                if (rightMat == null || rightMat == currentRight || rightMat == Material.AIR) break;
                if (type.equals("BLOCK") && !isValidBlock(rightMat)) break;

                hasRight = true;
                boolean discovered = !plugin.wikiDiscoveryMode || (type.equals("BLOCK") ? dm.hasDiscoveredBlock(p, currentRight) : dm.hasDiscoveredRecipe(p, currentRight));

                inv.setItem(resultArrowSlots[i], createSkull(TEX_ARROW_RIGHT, arrowName));

                if (discovered) {
                    inv.setItem(resultSlots[i], createItem(rightMat, "§f" + formatName(rightMat.name()), "§eClick to center"));
                } else {
                    inv.setItem(resultSlots[i], createSkull(TEX_SECRET, "§7Unknown", "§7Discover to reveal"));
                    break;
                }

                currentRight = rightMat;
            }
        }

        if (hasRight) inv.setItem(53, createItem(Material.ARROW, "§fShift Right", "§7Navigate forward"));

        inv.setItem(49, createItem(Material.BARRIER, "§cBack"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openMobChainGUI(Player p, EntityType mob) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Mob Chain", NamedTextColor.DARK_GRAY));
        inv.setItem(22, createItem(getSpawnEgg(mob), "§e" + formatName(mob.name()), "§7Target Mob"));

        RandomizerManager rm = plugin.getManager();
        DiscoveryManager dm = plugin.getDiscovery();

        if (plugin.randomMobLoot) {
            EntityType currentSrc = mob;
            boolean hasLeft = false;
            for (int i = 0; i < 6; i++) {
                EntityType prev = rm.getMobSource(currentSrc, p);
                if (prev == null || prev == currentSrc) break;
                hasLeft = true;

                boolean discovered = !plugin.wikiDiscoveryMode || dm.hasDiscoveredMob(p, prev);
                inv.setItem(sourceArrowSlots[i], createSkull(TEX_ARROW_RIGHT, "§7Drops loot of"));

                if (discovered) {
                    inv.setItem(sourceSlots[i], createItem(getSpawnEgg(prev), "§f" + formatName(prev.name()), "§eClick to center"));
                } else {
                    inv.setItem(sourceSlots[i], createSkull(TEX_SECRET, "§7Unknown", "§7Discover to reveal"));
                    break;
                }
                currentSrc = prev;
            }
            if (hasLeft) inv.setItem(45, createItem(Material.ARROW, "§fShift Left", "§7Navigate backward"));

            EntityType currentRes = mob;
            boolean hasRight = false;
            for (int i = 0; i < 6; i++) {
                EntityType next = rm.getRandomMobType(currentRes, p);
                if (next == null || next == currentRes) break;
                hasRight = true;

                boolean discovered = !plugin.wikiDiscoveryMode || dm.hasDiscoveredMob(p, currentRes);
                inv.setItem(resultArrowSlots[i], createSkull(TEX_ARROW_RIGHT, "§7Loot drops from"));

                if (discovered) {
                    inv.setItem(resultSlots[i], createItem(getSpawnEgg(next), "§f" + formatName(next.name()), "§eClick to center"));
                } else {
                    inv.setItem(resultSlots[i], createSkull(TEX_SECRET, "§7Unknown", "§7Discover to reveal"));
                    break;
                }
                currentRes = next;
            }
            if (hasRight) inv.setItem(53, createItem(Material.ARROW, "§fShift Right", "§7Navigate forward"));

        } else {
            inv.setItem(21, createSkull(TEX_ARROW_RIGHT, "§7Drops loot of"));
            inv.setItem(20, createItem(getSpawnEgg(mob), "§f" + formatName(mob.name()), "§cDisabled", "§7(Vanilla applies)"));
            inv.setItem(23, createSkull(TEX_ARROW_RIGHT, "§7Loot drops from"));
            inv.setItem(24, createItem(getSpawnEgg(mob), "§f" + formatName(mob.name()), "§cDisabled", "§7(Vanilla applies)"));
        }

        inv.setItem(49, createItem(Material.BARRIER, "§cBack"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openSettingsGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Settings"));
        inv.setItem(10, createToggle(Material.GRASS_BLOCK, "Block Drops", plugin.randomBlockDrops));
        inv.setItem(12, createToggle(Material.CRAFTING_TABLE, "Crafting", plugin.randomCrafting));
        inv.setItem(14, createToggle(Material.ROTTEN_FLESH, "Mob Loot", plugin.randomMobLoot));
        String scopeName = (plugin.currentScope == RandomizerPlugin.Scope.GLOBAL) ? "§aGlobal" : "§ePersonal";
        inv.setItem(18, createItem(Material.COMPARATOR, "§fScope", "§7" + scopeName));
        String discoveryStatus = plugin.wikiDiscoveryMode ? "§aHidden" : "§cShown";
        inv.setItem(20, createItem(Material.SPYGLASS, "§fDiscovery", "§7Unknown items: " + discoveryStatus));
        inv.setItem(22, plugin.wikiEnabled ? createItem(Material.BOOK, "§fWiki") : createItem(Material.BARRIER, "§cWiki Disabled"));
        inv.setItem(16, createItem(Material.COMMAND_BLOCK, "§cReshuffle"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openWikiHub(Player p) {
        if (!plugin.wikiEnabled) return;
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Wiki", NamedTextColor.DARK_GRAY));

        if (plugin.randomBlockDrops) inv.setItem(11, createItem(Material.DIAMOND_PICKAXE, "§fBlocks", "§7Discovered Drops"));
        else inv.setItem(11, createItem(Material.BARRIER, "§cBlocks Disabled", "§7Randomizer is off"));

        if (plugin.randomCrafting) inv.setItem(13, createItem(Material.CRAFTING_TABLE, "§fCrafting", "§7Discovered Recipes"));
        else inv.setItem(13, createItem(Material.BARRIER, "§cCrafting Disabled", "§7Randomizer is off"));

        if (plugin.randomMobLoot) inv.setItem(15, createItem(Material.ZOMBIE_HEAD, "§fMobs", "§7Discovered Loot"));
        else inv.setItem(15, createItem(Material.BARRIER, "§cMobs Disabled", "§7Randomizer is off"));

        inv.setItem(22, createItem(Material.BARRIER, "§cBack"));
        inv.setItem(26, createItem(Material.NAME_TAG, "§eSearch", "§7Click to open chat search"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    private void openListGUI(Player p, String title, List<ItemStack> items, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));
        pageCache.put(p.getUniqueId(), page);

        if (items.isEmpty()) inv.setItem(22, createItem(Material.BARRIER, "§cEmpty"));

        int start = page * 45;
        int end = Math.min(start + 45, items.size());
        for (int i = start; i < end; i++) inv.addItem(items.get(i));

        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "§fPrevious Page"));
        inv.setItem(49, createItem(Material.BARRIER, "§cBack"));
        if (end < items.size()) inv.setItem(53, createItem(Material.ARROW, "§fNext Page"));

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) if (inv.getItem(i) == null) inv.setItem(i, glass);
        p.openInventory(inv);
    }

    private void openBlockWiki(Player p, int page) {
        List<ItemStack> displayItems = new ArrayList<>();
        DiscoveryManager dm = plugin.getDiscovery();

        for (Material target : plugin.getManager().getAllValidItems()) {
            Material source = plugin.getManager().getBlockSource(target, p);
            if (source != null && (!plugin.wikiDiscoveryMode || dm.hasDiscoveredBlock(p, source))) {
                displayItems.add(createItem(target, "§f" + formatName(target.name()), "§7Source: §a" + formatName(source.name()), "§8Item"));
            }
        }
        openListGUI(p, "Wiki: Blocks", displayItems, page);
    }

    private void openMobWiki(Player p, int page) {
        List<ItemStack> displayItems = new ArrayList<>();
        DiscoveryManager dm = plugin.getDiscovery();
        for (EntityType target : EntityType.values()) {
            if (!target.isAlive() || !target.isSpawnable()) continue;
            EntityType source = plugin.getManager().getMobSource(target, p);
            if (source != null && (!plugin.wikiDiscoveryMode || dm.hasDiscoveredMob(p, source))) {
                displayItems.add(createItem(getSpawnEgg(target), "§f" + formatName(target.name()), "§7Dropped by: §a" + formatName(source.name()), "§8Mob"));
            }
        }
        openListGUI(p, "Wiki: Mobs", displayItems, page);
    }

    private void openCraftingWiki(Player p, int page) {
        List<ItemStack> displayItems = new ArrayList<>();
        DiscoveryManager dm = plugin.getDiscovery();
        for (Material target : plugin.getManager().getAllValidItems()) {
            Material source = plugin.getManager().getCraftingSource(target, p);
            if (source != null && (!plugin.wikiDiscoveryMode || dm.hasDiscoveredRecipe(p, source))) {
                displayItems.add(createItem(target, "§f" + formatName(target.name()), "§7Crafted with: §a" + formatName(source.name()), "§8Item"));
            }
        }
        openListGUI(p, "Wiki: Crafting", displayItems, page);
    }

    private void openSearchWiki(Player p, String query, int page) {
        searchQueries.put(p.getUniqueId(), query);
        List<ItemStack> displayItems = new ArrayList<>();

        for (Material m : plugin.getManager().getAllValidItems()) {
            if (m.name().toLowerCase().contains(query)) displayItems.add(createItem(m, "§f" + formatName(m.name()), "§8Item"));
        }
        for (EntityType ent : EntityType.values()) {
            if (ent.isAlive() && ent.isSpawnable() && ent.name().toLowerCase().contains(query)) {
                displayItems.add(createItem(getSpawnEgg(ent), "§e" + formatName(ent.name()), "§8Mob"));
            }
        }
        openListGUI(p, "Wiki: Search", displayItems, page);
    }


    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (searchingPlayers.contains(p.getUniqueId())) {
            e.setCancelled(true);
            searchingPlayers.remove(p.getUniqueId());
            String query = e.getMessage().trim().toLowerCase();
            if (query.equals("cancel")) {
                p.sendMessage("§cSearch cancelled.");
                Bukkit.getScheduler().runTask(plugin, () -> openWikiHub(p));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> openSearchWiki(p, query, 0));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String title = LegacyComponentSerializer.legacySection().serialize(e.getView().title()).replaceAll("§[0-9a-fk-or]", "");
        if (isMyGui(title)) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = LegacyComponentSerializer.legacySection().serialize(e.getView().title()).replaceAll("§[0-9a-fk-or]", "");
        if (isMyGui(title)) e.setCancelled(true);
        else return;

        ItemStack item = e.getCurrentItem();
        if (item == null) return;

        if (title.equals("Settings")) {
            int slot = e.getSlot();
            if (slot == 10) { plugin.randomBlockDrops = !plugin.randomBlockDrops; plugin.saveConfigSettings(); playClick(p); openSettingsGUI(p); }
            else if (slot == 12) { plugin.randomCrafting = !plugin.randomCrafting; plugin.saveConfigSettings(); playClick(p); openSettingsGUI(p); }
            else if (slot == 14) { plugin.randomMobLoot = !plugin.randomMobLoot; plugin.saveConfigSettings(); playClick(p); openSettingsGUI(p); }
            else if (slot == 16) { plugin.getManager().shuffle(); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1); p.sendMessage("§aReshuffled."); }
            else if (slot == 18) { plugin.currentScope = (plugin.currentScope == RandomizerPlugin.Scope.GLOBAL) ? RandomizerPlugin.Scope.PLAYER : RandomizerPlugin.Scope.GLOBAL; plugin.getManager().shuffle(); plugin.saveConfigSettings(); playClick(p); openSettingsGUI(p); }
            else if (slot == 20) { plugin.wikiDiscoveryMode = !plugin.wikiDiscoveryMode; plugin.saveConfigSettings(); playClick(p); openSettingsGUI(p); }
            else if (slot == 22 && plugin.wikiEnabled) { playClick(p); openWikiHub(p); }
            return;
        }

        if (title.equals("Wiki")) {
            if (e.getSlot() == 11) {
                if (!plugin.randomBlockDrops) { p.sendMessage("§cBlock Randomizer is disabled."); return; }
                playClick(p); openBlockWiki(p, 0);
            }
            else if (e.getSlot() == 13) {
                if (!plugin.randomCrafting) { p.sendMessage("§cCrafting Randomizer is disabled."); return; }
                playClick(p); openCraftingWiki(p, 0);
            }
            else if (e.getSlot() == 15) {
                if (!plugin.randomMobLoot) { p.sendMessage("§cMob Randomizer is disabled."); return; }
                playClick(p); openMobWiki(p, 0);
            }
            else if (e.getSlot() == 22) { playClick(p); openSettingsGUI(p); }
            else if (e.getSlot() == 26) {
                playClick(p);
                p.closeInventory();
                searchingPlayers.add(p.getUniqueId());
                p.sendMessage("§a[Randomizer] §7Please enter your search query in chat (or type 'cancel').");
            }
            return;
        }

        if (title.startsWith("Wiki:")) {
            int page = pageCache.getOrDefault(p.getUniqueId(), 0);
            if (e.getSlot() == 49 && item.getType() == Material.BARRIER) { playClick(p); openWikiHub(p); return; }
            if (e.getSlot() == 45 && item.getType() == Material.ARROW) {
                playClick(p);
                if (title.contains("Blocks")) openBlockWiki(p, Math.max(0, page - 1));
                else if (title.contains("Mobs")) openMobWiki(p, Math.max(0, page - 1));
                else if (title.contains("Crafting")) openCraftingWiki(p, Math.max(0, page - 1));
                else if (title.contains("Search")) openSearchWiki(p, searchQueries.get(p.getUniqueId()), Math.max(0, page - 1));
                return;
            }
            if (e.getSlot() == 53 && item.getType() == Material.ARROW) {
                playClick(p);
                if (title.contains("Blocks")) openBlockWiki(p, page + 1);
                else if (title.contains("Mobs")) openMobWiki(p, page + 1);
                else if (title.contains("Crafting")) openCraftingWiki(p, page + 1);
                else if (title.contains("Search")) openSearchWiki(p, searchQueries.get(p.getUniqueId()), page + 1);
                return;
            }

            if (e.getSlot() < 45 && item.getType() != Material.GRAY_STAINED_GLASS_PANE && item.getType() != Material.ARROW) {
                playClick(p);
                if (title.contains("Blocks")) {
                    navContext.put(p.getUniqueId(), "WikiBlocks");
                    openChainGUI(p, item.getType(), "BLOCK");
                } else if (title.contains("Crafting")) {
                    navContext.put(p.getUniqueId(), "WikiCrafting");
                    openChainGUI(p, item.getType(), "CRAFTING");
                } else if (title.contains("Mobs")) {
                    navContext.put(p.getUniqueId(), "WikiMobs");
                    EntityType ent = getEntityFromEgg(item);
                    if (ent != null) openMobChainGUI(p, ent);
                } else if (title.contains("Search")) {
                    navContext.put(p.getUniqueId(), "WikiSearch");
                    boolean isMob = false;
                    if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                        for (Component c : item.getItemMeta().lore()) {
                            if (LegacyComponentSerializer.legacySection().serialize(c).contains("Mob")) isMob = true;
                        }
                    }
                    if (isMob) {
                        EntityType ent = getEntityFromEgg(item);
                        if (ent != null) openMobChainGUI(p, ent);
                    } else {
                        openSelectionGUI(p, item.getType());
                    }
                }
            }
            return;
        }

        String name = (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) ? LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName()) : "";
        if (name.contains("Unknown") || name.contains("Disabled")) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1); return; }

        if (item.getType() == Material.BARRIER && !name.contains("Back")) return;

        if (title.equals("Item Information")) {
            Material t = e.getInventory().getItem(22).getType();
            if (e.getSlot() == 20) {
                if (!plugin.randomCrafting) { p.sendMessage("§cCrafting Randomizer disabled."); return; }
                playClick(p); navContext.put(p.getUniqueId(), "Hub"); openChainGUI(p, t, "CRAFTING");
            }
            else if (e.getSlot() == 24) {
                if (!plugin.randomBlockDrops) { p.sendMessage("§cBlock Drop Randomizer disabled."); return; }
                playClick(p); navContext.put(p.getUniqueId(), "Hub"); openChainGUI(p, t, "BLOCK");
            }
            else if (e.getSlot() == 49 && item.getType() == Material.BARRIER) {
                playClick(p);
                String ctx = navContext.get(p.getUniqueId());
                if ("WikiSearch".equals(ctx)) {
                    openSearchWiki(p, searchQueries.get(p.getUniqueId()), pageCache.getOrDefault(p.getUniqueId(), 0));
                } else {
                    openWikiHub(p);
                }
            }
            return;
        }

        if (title.equals("Block Chain") || title.equals("Crafting Chain") || title.equals("Mob Chain")) {
            String type = title.equals("Block Chain") ? "BLOCK" : (title.equals("Crafting Chain") ? "CRAFTING" : "MOB");

            if (e.getSlot() == 49 && item.getType() == Material.BARRIER) {
                playClick(p);
                String ctx = navContext.get(p.getUniqueId());
                int page = pageCache.getOrDefault(p.getUniqueId(), 0);

                if ("WikiBlocks".equals(ctx)) openBlockWiki(p, page);
                else if ("WikiCrafting".equals(ctx)) openCraftingWiki(p, page);
                else if ("WikiMobs".equals(ctx)) openMobWiki(p, page);
                else if ("WikiSearch".equals(ctx) || "Hub".equals(ctx)) {
                    if (type.equals("MOB")) {
                        if ("WikiSearch".equals(ctx)) openSearchWiki(p, searchQueries.get(p.getUniqueId()), page);
                        else p.closeInventory();
                    } else {
                        Material target = e.getInventory().getItem(22).getType();
                        openSelectionGUI(p, target);
                    }
                } else {
                    p.closeInventory();
                }
            }
            else if (e.getSlot() == 45 && item.getType() == Material.ARROW) {
                playClick(p);
                if (type.equals("MOB")) {
                    EntityType current = getEntityFromEgg(e.getInventory().getItem(22));
                    EntityType source = plugin.getManager().getMobSource(current, p);
                    if (source != null) openMobChainGUI(p, source);
                } else {
                    Material current = e.getInventory().getItem(22).getType();

                    Material source;
                    if (type.equals("BLOCK")) {
                        source = plugin.getManager().getBlockSource(current, p);
                    } else {
                        ItemStack res = plugin.getManager().getRandomCraftingResult(current, p);
                        source = res != null ? res.getType() : null;
                    }

                    if (source != null && source.isItem()) {
                        if (type.equals("BLOCK") && !isValidBlock(source)) return;
                        openChainGUI(p, source, type);
                    }
                }
            }
            else if (e.getSlot() == 53 && item.getType() == Material.ARROW) {
                playClick(p);
                if (type.equals("MOB")) {
                    EntityType current = getEntityFromEgg(e.getInventory().getItem(22));
                    EntityType result = plugin.getManager().getRandomMobType(current, p);
                    if (result != null) openMobChainGUI(p, result);
                } else {
                    Material current = e.getInventory().getItem(22).getType();

                    Material result;
                    if (type.equals("BLOCK")) {
                        result = plugin.getManager().getRandomBlockDrop(current, p);
                    } else {
                        result = plugin.getManager().getCraftingSource(current, p);
                    }

                    if (result != null && result.isItem()) {
                        if (type.equals("BLOCK") && !isValidBlock(result)) return;
                        openChainGUI(p, result, type);
                    }
                }
            }
            else if (isDynamicChainSlot(e.getSlot()) && item.getType() != Material.PLAYER_HEAD) {
                playClick(p);
                if (type.equals("MOB")) {
                    EntityType ent = getEntityFromEgg(item);
                    if (ent != null) openMobChainGUI(p, ent);
                } else {
                    openChainGUI(p, item.getType(), type);
                }
            }
        }
    }

    private boolean isDynamicChainSlot(int slot) {
        for (int s : sourceSlots) if (s == slot) return true;
        for (int s : resultSlots) if (s == slot) return true;
        return false;
    }

    private boolean isValidBlock(Material m) {
        if (m == null || m == Material.AIR) return false;
        if (!m.isBlock()) return false;
        if (m.name().endsWith("_SPAWN_EGG")) return false; // Harter Filter für alle Eier!
        return true;
    }

    private boolean isMyGui(String title) {
        return title.equals("Settings") || title.equals("Wiki") || title.equals("Item Information") ||
                title.equals("Mob Chain") || title.equals("Block Chain") || title.equals("Crafting Chain") ||
                title.startsWith("Wiki:");
    }

    private void playClick(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1); }

    private String formatName(String input) {
        String[] words = input.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) if (w.length() > 0) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        return sb.toString().trim();
    }

    private EntityType getEntityFromEgg(ItemStack item) {
        if (item == null) return null;
        Material m = item.getType();
        String name = m.name();
        if (name.endsWith("_SPAWN_EGG")) {
            try { return EntityType.valueOf(name.replace("_SPAWN_EGG", "")); } catch (Exception ignored) {}
        }
        if (m == Material.MOOSHROOM_SPAWN_EGG) return EntityType.MOOSHROOM;
        if (m == Material.SNOW_GOLEM_SPAWN_EGG) return EntityType.SNOW_GOLEM;
        if (m == Material.DRAGON_HEAD) return EntityType.ENDER_DRAGON;

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String disp = LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName());
            disp = disp.replaceAll("§[0-9a-fk-or]", "").replace(" ", "_").toUpperCase();
            try { return EntityType.valueOf(disp); } catch (Exception ignored) {}
        }
        return null;
    }

    private Material getSpawnEgg(EntityType type) {
        if (type == null) return Material.CREEPER_HEAD;
        Material mobIcon = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        if (mobIcon == null) {
            if (type == EntityType.MOOSHROOM) mobIcon = Material.MOOSHROOM_SPAWN_EGG;
            else if (type == EntityType.SNOW_GOLEM) mobIcon = Material.SNOW_GOLEM_SPAWN_EGG;
            else if (type == EntityType.ENDER_DRAGON) mobIcon = Material.DRAGON_HEAD;
            else if (type == EntityType.IRON_GOLEM) mobIcon = Material.IRON_BLOCK;
            else mobIcon = Material.CREEPER_HEAD;
        }
        return mobIcon;
    }

    private ItemStack createItem(Material m, String name, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createToggle(Material m, String name, boolean active) {
        return createItem(m, "§f" + name, active ? "§aEnabled" : "§cDisabled");
    }

    private ItemStack createSkull(String base64, String name, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        head.setItemMeta(meta);
        return head;
    }

    private void fillGlass(Inventory inv) {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, glass);
    }
}