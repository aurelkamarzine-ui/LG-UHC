package fr.sfakeur.lguhc;

import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.*;

//imports (keep what you already had)
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class ErmiteTabManager{
	
    private final org.bukkit.plugin.Plugin plugin;

	    // Ermites “morts” qu’on veut garder dans le tab
	    private final Map<UUID, Ghost> ghosts = new HashMap<>();

	    private static class Ghost {
	        final UUID uuid;
	        final String name;
	        final GameProfile profile;
	        final IChatBaseComponent displayName; // peut être null
	        final int ping;
	        final WorldSettings.EnumGamemode gamemode; // toujours SURVIVAL pour “masquer” spectator

	        Ghost(UUID uuid, String name, GameProfile profile) {
	            this.uuid = uuid;
	            this.name = name;
	            this.profile = profile;
	            this.displayName = null;
	            this.ping = 0;
	            this.gamemode = WorldSettings.EnumGamemode.SURVIVAL;
	        }
	    }

	    public ErmiteTabManager(org.bukkit.plugin.Plugin plugin) {
	        this.plugin = plugin;
	    }


	    /** Appelé quand l’Ermite est déclaré mort (finalisation 10s) pour “figer” son entrée tab. */
	    public void addErmiteGhost(Player victim) {
	        GameProfile gp = ((CraftPlayer) victim).getProfile();
	        Ghost g = new Ghost(victim.getUniqueId(), victim.getName(), cloneProfile(gp));
	        ghosts.put(g.uuid, g);

	        // Diffuser ADD_PLAYER avec SURVIVAL à tous (même si le joueur passe spectateur)
	        for (Player viewer : Bukkit.getOnlinePlayers()) {
	            sendAddPlayer(viewer, g);
	        }
	    }

	    /** À appeler si jamais tu veux le retirer (pas demandé ici). */
	    public void removeErmiteGhost(UUID id) {
	        Ghost g = ghosts.remove(id);
	        if (g == null) return;
	        for (Player viewer : Bukkit.getOnlinePlayers()) {
	            sendRemovePlayer(viewer, g);
	        }
	    }

	    /** Quand un nouveau joueur rejoint, on (re)push tous les ghosts dans sa tablist. */
	    @org.bukkit.event.EventHandler
	    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
	        final Player p = e.getPlayer();
	        // retarde d’un tick pour laisser Spigot faire ses trucs
	        Bukkit.getScheduler().runTask(plugin, () -> {
	            for (Ghost g : ghosts.values()) sendAddPlayer(p, g);
	        });
	    }

	    /** Si un Ermite mort quitte : pas de message + on re-push le “fake tab” juste après. */
	    @org.bukkit.event.EventHandler
	    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
	        Player p = e.getPlayer();
	        Ghost g = ghosts.get(p.getUniqueId());
	        if (g != null) {
	            // Pas de message de quit pour l’Ermite mort
	            e.setQuitMessage(null);

	            // Spigot va enlever la vraie entrée → on remet notre fake après un court délai
	            Bukkit.getScheduler().runTaskLater(plugin, () -> {
	                for (Player viewer : Bukkit.getOnlinePlayers()) {
	                    sendAddPlayer(viewer, g);
	                }
	            }, 2L);
	        }
	    }

	    /** Utilitaire : envoie ADD_PLAYER (GameMode.SURVIVAL) à un viewer pour un ghost. */
	    private void sendAddPlayer(Player viewer, Ghost g) {
	        try {
	            PacketPlayOutPlayerInfo packet = buildInfoPacketAdd(g);
	            ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
	        } catch (Throwable t) {
	            t.printStackTrace();
	        }
	    }

	    /** Utilitaire : envoie REMOVE_PLAYER (si jamais tu veux nettoyer). */
	    private void sendRemovePlayer(Player viewer, Ghost g) {
	        try {
	            PacketPlayOutPlayerInfo packet = buildInfoPacketRemove(g);
	            ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
	        } catch (Throwable t) {
	            t.printStackTrace();
	        }
	    }

	    // ====== Construction des packets (NMS 1_8_R3) ======

	    private PacketPlayOutPlayerInfo buildInfoPacketAdd(Ghost g) throws Exception {
	        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo();
	        setEnumAction(packet, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER);

	        PacketPlayOutPlayerInfo.PlayerInfoData data =
	                newInfoData(packet, g.profile, g.ping, g.gamemode, g.displayName);

	        setDataList(packet, Collections.singletonList(data));
	        return packet;
	    }

	    private PacketPlayOutPlayerInfo buildInfoPacketRemove(Ghost g) throws Exception {
	        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo();
	        setEnumAction(packet, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER);

	        PacketPlayOutPlayerInfo.PlayerInfoData data =
	                newInfoData(packet, g.profile, 0, WorldSettings.EnumGamemode.SURVIVAL, null);

	        setDataList(packet, Collections.singletonList(data));
	        return packet;
	    }

	    /** Create PlayerInfoData for 1.8 with either (packet, ...) or (profile, ...) signature. */
	    @SuppressWarnings("JavaReflectionMemberAccess")
	    private PacketPlayOutPlayerInfo.PlayerInfoData newInfoData(
	            PacketPlayOutPlayerInfo packet,
	            GameProfile profile,
	            int ping,
	            WorldSettings.EnumGamemode gamemode,
	            IChatBaseComponent displayName
	    ) throws Exception {
	        try {
	            // Try 5-arg: (PacketPlayOutPlayerInfo, GameProfile, int, EnumGamemode, IChatBaseComponent)
	            Constructor<?> c = PacketPlayOutPlayerInfo.PlayerInfoData.class.getDeclaredConstructor(
	                    PacketPlayOutPlayerInfo.class, GameProfile.class, int.class,
	                    WorldSettings.EnumGamemode.class, IChatBaseComponent.class);
	            c.setAccessible(true);
	            return (PacketPlayOutPlayerInfo.PlayerInfoData) c.newInstance(
	                    packet, profile, ping, gamemode, displayName);
	        } catch (NoSuchMethodException e) {
	            // Fallback 4-arg: (GameProfile, int, EnumGamemode, IChatBaseComponent)
	            Constructor<?> c = PacketPlayOutPlayerInfo.PlayerInfoData.class.getDeclaredConstructor(
	                    GameProfile.class, int.class, WorldSettings.EnumGamemode.class, IChatBaseComponent.class);
	            c.setAccessible(true);
	            PacketPlayOutPlayerInfo.PlayerInfoData data =
	                    (PacketPlayOutPlayerInfo.PlayerInfoData) c.newInstance(profile, ping, gamemode, displayName);

	            // Some mappings expect the private field referencing outer packet (rare). If present, set it:
	            try {
	                Field outer = PacketPlayOutPlayerInfo.PlayerInfoData.class.getDeclaredField("a"); // sometimes outer-ref, sometimes profile!
	                outer.setAccessible(true);
	                if (outer.getType().isAssignableFrom(PacketPlayOutPlayerInfo.class)) {
	                    outer.set(data, packet);
	                }
	            } catch (NoSuchFieldException ignored) {}
	            return data;
	        }
	    }

	    // champs privés du packet (1.8 R3)
	    @SuppressWarnings("unchecked")
	    private void setDataList(PacketPlayOutPlayerInfo packet, List<PacketPlayOutPlayerInfo.PlayerInfoData> list) throws Exception {
	        Field b = PacketPlayOutPlayerInfo.class.getDeclaredField("b"); // List<PlayerInfoData>
	        b.setAccessible(true);
	        b.set(packet, list);
	    }

	    private void setEnumAction(PacketPlayOutPlayerInfo packet, PacketPlayOutPlayerInfo.EnumPlayerInfoAction action) throws Exception {
	        Field a = PacketPlayOutPlayerInfo.class.getDeclaredField("a"); // EnumPlayerInfoAction
	        a.setAccessible(true);
	        a.set(packet, action);
	    }

	    private GameProfile cloneProfile(GameProfile src) {
	        GameProfile gp = new GameProfile(src.getId(), src.getName());
	        // copie les propriétés (textures/skin)
	        src.getProperties().entries().forEach(e -> gp.getProperties().put(e.getKey(), e.getValue()));
	        return gp;
	    }

	    // Petit helper pour debug (facultatif)
	    public void debugList(Player to) {
	        to.sendMessage(ChatColor.GRAY + "[ErmiteTab] ghosts=" + ghosts.size());
	        for (Ghost g : ghosts.values()) to.sendMessage(" - " + g.name + " " + g.uuid);
	    }
	
	    public void clearAllGhosts() {
	        for (java.util.UUID id : new java.util.ArrayList<>(ghosts.keySet())) {
	            removeErmiteGhost(id);
	        }
	        ghosts.clear();
	    }


}
