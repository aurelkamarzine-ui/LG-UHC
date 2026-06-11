package fr.sfakeur.lguhc;

import org.bukkit.entity.Player;


import org.bukkit.entity.Player;

public interface AlignHandler {
    default void onEpisodeStart(int episodeNumber) {}
    default void tickPerSecond(int elapsedSec) {}
    default boolean handleSubCommand(String sub, Player sender, String[] args) { return false; }
    default void onPlayerKill(Player killer, Player victim) {}
    default void onPlayerDeath(Player dead) {}
}
