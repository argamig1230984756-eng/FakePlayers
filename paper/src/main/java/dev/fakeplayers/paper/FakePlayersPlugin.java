package dev.fakeplayers.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FakePlayersPlugin extends JavaPlugin implements Listener {
    private FakeManager manager;
    private ProxyBridge bridge;

    public FakeManager manager() { return manager; }
    public ProxyBridge bridge() { return bridge; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new FakeManager(this);
        manager.load();
        bridge = new ProxyBridge(this);
        FakeCommand cmd = new FakeCommand(this);
        getCommand("fp").setExecutor(cmd);
        getCommand("fp").setTabCompleter(cmd);
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskLater(this, manager::spawnAuto, 40L); // after worlds are loaded
        long save = getConfig().getLong("autosave-seconds", 60) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, manager::save, save, save);
        long sync = getConfig().getLong("sync-interval-seconds", 30) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, bridge::syncNow, sync, sync);
    }

    @Override
    public void onDisable() { if (manager != null) manager.shutdown(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (FakeServerPlayer.isFake(e.getPlayer())) {
            if (getConfig().getBoolean("suppress-join-quit-messages")) e.joinMessage(null);
        } else {
            Bukkit.getScheduler().runTaskLater(this, bridge::syncNow, 20L); // a real player can now carry messages
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (FakeServerPlayer.isFake(e.getPlayer()) && getConfig().getBoolean("suppress-join-quit-messages"))
            e.quitMessage(null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (FakeServerPlayer.isFake(p) && getConfig().getBoolean("auto-respawn", true))
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isDead()) p.spigot().respawn(); }, 5L);
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Player hp && FakeServerPlayer.isFake(hp)) {
            FakeData d = manager.get(hp.getName());
            if (d != null) manager.capture(d);
        }
    }
}
