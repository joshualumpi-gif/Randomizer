package de.deinname.randomizer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class RandomizerPlugin extends JavaPlugin {

    private static RandomizerPlugin instance;
    private RandomizerManager randomizerManager;
    private DiscoveryManager discoveryManager;
    private RandomizerGUI gui;

    public boolean randomBlockDrops = false;
    public boolean randomMobLoot = false;
    public boolean randomCrafting = false;

    public boolean wikiEnabled = true; // NEU: Wiki komplett an/aus
    public boolean wikiDiscoveryMode = true; // NEU: Nur entdeckte Sachen anzeigen

    private boolean isPaused = false;

    public enum Scope { GLOBAL, PLAYER }
    public Scope currentScope = Scope.GLOBAL;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadConfigSettings();

        long seed = getConfig().getLong("seed", 0);
        if (seed == 0) {
            seed = new Random().nextLong();
            getConfig().set("seed", seed);
            saveConfig();
        }

        this.randomizerManager = new RandomizerManager(this, seed);
        this.discoveryManager = new DiscoveryManager(this);
        this.gui = new RandomizerGUI(this);

        Bukkit.getPluginManager().registerEvents(new RandomizerListener(this), this);
        Bukkit.getPluginManager().registerEvents(gui, this);

        getCommand("randomizer").setExecutor(new RandomizerCommand(this));
        getCommand("randomizer").setTabCompleter(new RandomizerTabCompleter(this));

        if (Bukkit.getPluginManager().getPlugin("Timer") != null) {
            getLogger().info("Timer-Plugin (BasePlugin) gefunden! Hooking into Events...");
            this.isPaused = true;
            Bukkit.getPluginManager().registerEvents(new TimerBridgeListener(this), this);
        } else {
            this.isPaused = false;
        }

        getLogger().info("Randomizer Plugin aktiviert!");
    }

    @Override
    public void onDisable() {
        if (discoveryManager != null) {
            discoveryManager.saveData();
        }
    }

    public void loadConfigSettings() {
        randomBlockDrops = getConfig().getBoolean("settings.blocks", false);
        randomMobLoot = getConfig().getBoolean("settings.mobs", false);
        randomCrafting = getConfig().getBoolean("settings.crafting", false);

        wikiEnabled = getConfig().getBoolean("settings.allow-wiki", true);
        wikiDiscoveryMode = getConfig().getBoolean("settings.wiki-discovery", true);

        try {
            currentScope = Scope.valueOf(getConfig().getString("settings.scope", "GLOBAL"));
        } catch (Exception e) {
            currentScope = Scope.GLOBAL;
        }
    }

    public void saveConfigSettings() {
        getConfig().set("settings.blocks", randomBlockDrops);
        getConfig().set("settings.mobs", randomMobLoot);
        getConfig().set("settings.crafting", randomCrafting);
        getConfig().set("settings.allow-wiki", wikiEnabled);
        getConfig().set("settings.wiki-discovery", wikiDiscoveryMode);
        getConfig().set("settings.scope", currentScope.name());
        getConfig().set("seed", randomizerManager.getSeed());
        saveConfig();
    }

    public boolean isPaused() { return isPaused; }
    public void setPaused(boolean paused) { this.isPaused = paused; }

    public static RandomizerPlugin getInstance() { return instance; }
    public RandomizerManager getManager() { return randomizerManager; }
    public DiscoveryManager getDiscovery() { return discoveryManager; }
    public RandomizerGUI getGui() { return gui; }
}