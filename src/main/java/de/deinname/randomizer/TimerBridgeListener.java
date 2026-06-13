package de.deinname.randomizer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.example.TimerPlugin;
import org.example.events.TimerFinishEvent;
import org.example.events.TimerStartEvent;

public class TimerBridgeListener implements Listener {

    private final RandomizerPlugin plugin;

    public TimerBridgeListener(RandomizerPlugin plugin) {
        this.plugin = plugin;
        syncState();
    }

    private void syncState() {
        boolean running = false;
        try {
            running = TimerPlugin.getInstance().isRunning();
        } catch (Exception ignored) {}

        plugin.setPaused(!running);
    }


    @EventHandler
    public void onTimerStart(TimerStartEvent e) {
        plugin.setPaused(false);
        updateGameMode(GameMode.SURVIVAL);
    }

    @EventHandler
    public void onTimerFinish(TimerFinishEvent e) {
        plugin.setPaused(true);
        updateGameMode(GameMode.ADVENTURE);
    }


    private void updateGameMode(GameMode mode) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(mode);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.isPaused()) {
                e.getPlayer().setGameMode(GameMode.ADVENTURE);
            } else {
                e.getPlayer().setGameMode(GameMode.SURVIVAL);
            }
        }
    }
}