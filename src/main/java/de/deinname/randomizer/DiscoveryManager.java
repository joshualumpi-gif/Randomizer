package de.deinname.randomizer;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DiscoveryManager {

    private final RandomizerPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    private final Map<UUID, Set<Material>> brokenBlocks = new HashMap<>();
    private final Map<UUID, Set<EntityType>> killedMobs = new HashMap<>();
    private final Map<UUID, Set<Material>> usedRecipes = new HashMap<>();

    public DiscoveryManager(RandomizerPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discovery.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    private void loadAll() {
        for (String uuidStr : config.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            loadSet(uuidStr + ".blocks", brokenBlocks, uuid, Material.class);
            loadSet(uuidStr + ".mobs", killedMobs, uuid, EntityType.class);
            loadSet(uuidStr + ".recipes", usedRecipes, uuid, Material.class);
        }
    }

    private <T extends Enum<T>> void loadSet(String path, Map<UUID, Set<T>> map, UUID uuid, Class<T> enumClass) {
        List<String> list = config.getStringList(path);
        Set<T> set = new HashSet<>();
        for (String s : list) {
            try { set.add(Enum.valueOf(enumClass, s)); } catch (Exception ignored) {}
        }
        map.put(uuid, set);
    }

    public void saveData() {
        for (UUID uuid : brokenBlocks.keySet()) config.set(uuid + ".blocks", toList(brokenBlocks.get(uuid)));
        for (UUID uuid : killedMobs.keySet()) config.set(uuid + ".mobs", toList(killedMobs.get(uuid)));
        for (UUID uuid : usedRecipes.keySet()) config.set(uuid + ".recipes", toList(usedRecipes.get(uuid)));
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private <T extends Enum<T>> List<String> toList(Set<T> set) {
        List<String> list = new ArrayList<>();
        set.forEach(e -> list.add(e.name()));
        return list;
    }

    public void discoverBlockBreak(Player p, Material block) {
        brokenBlocks.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(block);
    }
    public void discoverMobKill(Player p, EntityType mob) {
        killedMobs.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(mob);
    }
    public void discoverRecipeUse(Player p, Material originalRecipeResult) {
        usedRecipes.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(originalRecipeResult);
    }

    public boolean hasDiscoveredBlock(Player p, Material block) {
        return !plugin.wikiDiscoveryMode || brokenBlocks.getOrDefault(p.getUniqueId(), Collections.emptySet()).contains(block);
    }
    public boolean hasDiscoveredMob(Player p, EntityType mob) {
        return !plugin.wikiDiscoveryMode || killedMobs.getOrDefault(p.getUniqueId(), Collections.emptySet()).contains(mob);
    }
    public boolean hasDiscoveredRecipe(Player p, Material recipeResult) {
        return !plugin.wikiDiscoveryMode || usedRecipes.getOrDefault(p.getUniqueId(), Collections.emptySet()).contains(recipeResult);
    }
}