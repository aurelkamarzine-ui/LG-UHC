package fr.sfakeur.lguhc;

import org.bukkit.*;


import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import fr.sfakeur.lguhc.GameManager.WinTeam;

import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import java.util.*;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;


import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GameManager implements org.bukkit.event.Listener {

	private final Plugin plugin;
	Set<Player> frozenPlayers = new HashSet<>();

	private final int rayonSpawn;
	private final int tempsFreeze = 10; // secondes de freeze

	private int nombreGroupes = 4; // À ajuster manuellement ou par commande
	private int tailleBordureDepart = 500; // Déjà utilisé
	private Map<UUID, Integer> playerKills = new HashMap<>();
	private boolean gameStarted = false;
	private final UHCConfig uhcConfig; // <- reste final
	// Cycle jour/nuit custom
	private boolean customCycleActive = false;
	private double cycleTicksPerSecond = 0.0; // 24000 ticks / (minutes * 60)
	private double cycleAccumulator = 0.0; // pour gérer la fraction
	private int dayCount = 1;
	private int nightCount = 0;
	private int lastAnnouncedEpisode = 0;

	// for the custom day/night mapping task:
	private BukkitRunnable dayNightTask;
	private int plannedCycleMinutes = 20; // default
	private int episodeLenSec = 0;
	private int nextEpisodeAtSec = 0;
	private int episodeNumber = 1; // affiché au scoreboard

	private enum Phase {
		DAY, NIGHT
	}

	private Phase lastAnnouncedPhase = null;

	private int elapsedSeconds = 0;
	private BukkitRunnable timerRunnable;

	// === Post-freeze one-time arming ===
	private boolean postFreezeInitialized = false;
	private boolean gameClockActive = false;

	private Phase lastPhase = Phase.DAY; // we start in day

	private static final String INGAME_OBJ = "lguhc_ingame";

	private final LGUHC main;

	// GameManager.java (champs)
	private boolean borderShrinkScheduled = false;
	private boolean borderShrinkStarted = false;

	private int borderStartDiameter = 0;
	private int borderEndDiameter = 0;
	private int borderStartAtSec = 0; // quand on lance la réduction
	private double borderSpeedBps = 1.0; // blocks / sec

	private boolean rolesAssigned = false; // ⬅ pour ne le faire qu’une fois

	private long currentNightNumber = 0; // compteur “nuit #” pour limitations “1 fois par nuit”

	private long cycleStartMillis;
	private boolean cycleArmed = false;
	
	private BukkitTask gameTickTask;   // handle du timer 1s
	
	// --- Option Solitaire ---
	private boolean solitaireEnabled = true;   // mets sur false par défaut si tu veux exposer ça à l’host
	private boolean solitaireTriggered = false;
	private int     solitaireTriggerAliveCount = -1; // seuil "vrai jeu" (65%..90%)
	private int     solitaireInitialAlive = -1;

	// --- Mode test : déclenche entre 60 et 120 secondes (1-2 min) ---
	private boolean solitaireTest = true;      // passe à false quand tu voudras la version % seulement
	private int     solitaireTriggerAtSec = -1;

	// accès rapides
	private final java.util.Random rand = new java.util.Random();

	
	

    // Si tu crées DeathManager ailleurs, assure-toi de l’injecter ici :
    public void setDeathManager(DeathManager dm) {
        this.deathManager = dm;
    }

	public void setRoleService(RoleService rs) {
		this.roleService = rs;
	}

	public RoleService getRoleService() {
		return roleService;
	}

	private DeathManager deathManager;

	public fr.sfakeur.lguhc.DeathManager getDeathManager() { return deathManager; }


	// en haut de GameManager
	// Dans GameManager
	public boolean isRunning() {
		return gameStarted;
	}

	private RoleService roleService; // non-final

	// Choix host : soit après X secondes, soit au début de l'épisode N
	// (tu peux les charger depuis UHCConfig si tu veux)
	private enum RoleAssignMode {
		AFTER_SECONDS, AT_EPISODE
	}

	private RoleAssignMode roleAssignMode = RoleAssignMode.AT_EPISODE; // ou AFTER_SECONDS
	private int assignAfterSeconds = 30; // si mode AFTER_SECONDS
	private int assignAtEpisode = 2; // si mode AT_EPISODE

	// Choix d’annonce des rôles (issu du menu)
	public enum RoleAnnounceMode {
		AFTER_SECONDS, AT_EPISODE
	}

	private RoleAnnounceMode roleAnnounceMode = RoleAnnounceMode.AFTER_SECONDS;
	private int announceAfterSeconds = 30; // si mode AFTER_SECONDS
	private int announceAtEpisode = 2; // si mode AT_EPISODE

	// Appelé par le menu quand l’host clique (mets-le où tu gères les clics)
	public void setRoleAnnounceMode(RoleAnnounceMode mode) {
		this.roleAnnounceMode = mode;
	}

	public void setAnnounceAfterSeconds(int s) {
		this.announceAfterSeconds = Math.max(1, s);
	}

	public void setAnnounceAtEpisode(int ep) {
		this.announceAtEpisode = Math.max(1, ep);
	}

	public int getKillsFor(Player p) {
		return playerKills.getOrDefault(p.getUniqueId(), 0);
	}

	// Evite les doublons d’annonce (appels concurrents DeathManager/RoleService)
	private boolean winAnnounced = false;

	private boolean victoryAnnounced = false;

	private boolean lastNightFlag = false;

	private boolean wolvesAlliesAnnounced = false;

	// ===== Vote LG9 =====
	private boolean voteWindowOpen = false;
	private int voteEpisodeOfWindow = 0;
	private long voteCloseAtMs = 0L;
	private org.bukkit.scheduler.BukkitTask voteCloseTask = null;

	// votes: voter -> target
	private final java.util.Map<java.util.UUID, java.util.UUID> currentVotes = new java.util.HashMap<>();
	// tally: target -> count (recalculé à la fermeture ou maintenu au fil de l’eau
	// si tu préfères)
	private final java.util.Map<java.util.UUID, Integer> tally = new java.util.HashMap<>();

	// malus temporaire appliqué par vote: pour gérer le retour
	private final java.util.Map<java.util.UUID, Integer> activeVoteDebuffHearts = new java.util.HashMap<>();
	private final java.util.Map<java.util.UUID, Long> activeVoteDebuffEndMs = new java.util.HashMap<>();

	private boolean randomCoupleEnabled = false; // default
	
	// GameManager.java (en haut, champs)
	private boolean coupleAnnounceScheduled = false;
	
	// GameManager.java
	public enum VoteSystem { OFF, LG9, LG10 }
	
	
	// 0 = OFF, 9 = LG9, 10 = LG10
	private int voteMode = 0;
	private boolean voteLockedLG9 = false;

	public int getVoteMode() { return voteMode; }
	public boolean isVoteLockedLG9() { return voteLockedLG9; }

	public void setVoteMode(int mode) {
	    if (voteLockedLG9) return;            // verrou actif => on ne change pas
	    voteMode = (mode == 9 || mode == 10) ? mode : 0;
	}

	public void forceVoteLG9Lock(boolean lock) {
	    voteLockedLG9 = lock;
	    if (lock) voteMode = 9;               // impose LG9
	}

	public boolean isVoteOff()          { return voteMode == 0; }
	public boolean isVoteLG9()          { return voteMode == 9; }
	public boolean isVoteLG10()         { return voteMode == 10; }
	

	private VoteSystem voteSystem = VoteSystem.OFF;
	private boolean voteSystemLocked = false;

	public VoteSystem getVoteSystem(){ return voteSystem; }
	public boolean isVoteSystemLocked(){ return voteSystemLocked; }


	

	public void setRandomCoupleEnabled(boolean enabled) {
		this.randomCoupleEnabled = enabled;
	}

	public GameManager(LGUHC main, int rayonSpawn, UHCConfig uhcConfig) {
		this.plugin = main; // ✅ initialise correctement
		this.main = main; // ✅ même référence
		this.rayonSpawn = rayonSpawn;
		this.uhcConfig = uhcConfig;

		// (Optionnel) tu peux enregistrer ici, sinon fais-le dans onEnable
		plugin.getServer().getPluginManager().registerEvents(this, plugin);

	}

	public LGUHC getMain() {
		return main;
	}

	public UHCConfig getUhcConfig() {
		return uhcConfig;
	}

	private void dbg(String msg) {
		Bukkit.getLogger().info("[LGUHC] " + msg);
	}

	private void broadcastAll(String msg) {
		// pour le compte à rebours uniquement, on diffuse à tout le monde
		for (Player p : Bukkit.getOnlinePlayers()) {
			p.sendMessage(msg);
		}
		dbg("BROADCAST: " + ChatColor.stripColor(msg));
	}


	public void startGame() {
	    if (gameStarted) { dbg("startGame ignoré: déjà en cours"); return; }
	    gameStarted = true;

	    playerKills.clear();
	    lastNightFlag = isCurrentlyNight();
	    nightCount = lastNightFlag ? 1 : 0;

	    addAllOnlineToGame();
	    
	    armSolitaireTriggerOnGameStart();

	    main.syncAllEnabledFromCounts();
	    if (roleService != null) roleService.resetCoupleAnnounced();

	    for (UUID id : playersInGame) {
	        Player pl = Bukkit.getPlayer(id);
	        if (pl == null || !pl.isOnline()) continue;
	        main.removeConfigItems(pl);
	        fullClear(pl);
	    }

	    broadcast(ChatColor.GREEN + "La partie commence dans 10 secondes !");

	    new BukkitRunnable() {
	        int time = 10;

	        @Override public void run() {
	            try {
	                if (time <= 0) {
	                    cancel();
	                    main.stopPregameBoardUpdater();
	                    main.releasePregameBoard();

	                    broadcastAll(ChatColor.GREEN + "Téléportation des joueurs...");
	                    World world = Bukkit.getWorlds().get(0);
	                    world.setGameRuleValue("doDaylightCycle", "false");
	                    world.setTime(0L);

	                    teleportPlayers();

	                    // init timer/épisodes
	                    elapsedSeconds   = 0;
	                    episodeLenSec    = Math.max(60, uhcConfig.getEpisodeMinutes() * 60);
	                    episodeNumber    = 1;
	                    nextEpisodeAtSec = episodeLenSec;

	                    // bordure
	                    int startRadius = uhcConfig.getBorderStartRadius();
	                    int endRadius   = uhcConfig.getBorderEndRadius();
	                    borderStartDiameter = startRadius * 2;
	                    borderEndDiameter   = endRadius   * 2;
	                    borderStartAtSec    = uhcConfig.getBorderStartMinute() * 60;
	                    borderSpeedBps      = uhcConfig.getBorderSpeedBps();

	                    WorldBorder wb = world.getWorldBorder();
	                    wb.setCenter(0, 0);
	                    wb.setSize(borderStartDiameter);

	                    tailleBordureDepart = borderStartDiameter;
	                    borderShrinkScheduled = true;
	                    borderShrinkStarted   = false;

	                 // 🚀 Démarrage du tick global (1 fois par seconde)
	                 // 🚀 Tick global (1 fois / seconde)
	                    if (gameTickTask != null) { try { gameTickTask.cancel(); } catch (Throwable ignored) {} }
	                    gameTickTask = Bukkit.getScheduler().runTaskTimer(main, () -> {
	                        try {
	                            // avance l’horloge
	                            elapsedSeconds++;

	                            // route vers les handlers (Wolf/Village/Neutral/Hybrid)
	                            if (roleService != null) {
	                                roleService.tickPerSecond(elapsedSeconds);
	                            }
	                        } catch (Throwable t) {
	                            t.printStackTrace();
	                        }
	                    }, 20L, 20L);


	                    return;
	                }

	                broadcastAll(ChatColor.YELLOW + "Démarrage dans " + time + "s...");
	                time--;
	            } catch (Throwable t) {
	                dbg("Exception dans compte à rebours: " + t.getMessage());
	                t.printStackTrace();
	                cancel();
	            }
	        }
	    }.runTaskTimer(main, 0L, 20L);
	}


	private void fullClear(Player p) {
		// inventaire + armure + curseur
		p.getInventory().clear();
		p.getInventory().setHelmet(null);
		p.getInventory().setChestplate(null);
		p.getInventory().setLeggings(null);
		p.getInventory().setBoots(null);
		p.setItemOnCursor(null);
		p.updateInventory();
	}

	private void checkEpisodeBoundary() {
		if (!postFreezeInitialized)
			return;

		// rattrape le retard si le serveur lag (while et pas if strict)
		while (elapsedSeconds >= nextEpisodeAtSec) {
			episodeNumber++;
			nextEpisodeAtSec += episodeLenSec;

			Bukkit.broadcastMessage(ChatColor.YELLOW + "Épisode " + episodeNumber);
			// si tu veux aussi mettre à jour un affichage local, fais-le ici
		}
	}

	private void teleportPlayers() {
		World world = Bukkit.getWorlds().get(0);
		// mets à jour la valeur affichée au scoreboard depuis la vraie bordure
		WorldBorder wb = world.getWorldBorder();
		if (wb != null) {
			tailleBordureDepart = (int) Math.round(wb.getSize()); // diamètre (100 => 100×100)
		}

		for (UUID id : playersInGame) {
			Player p = Bukkit.getPlayer(id);
			if (p != null && p.isOnline()) {
				Location loc = getSafeRandomLocation(world);
				if (loc != null) {
					p.teleport(loc);
					p.setGameMode(GameMode.SURVIVAL);
					freezePlayer(p);
				} else {
					p.sendMessage(ChatColor.RED + "Erreur de téléportation : aucun endroit sûr trouvé.");
				}
			}
		}
	}

	/**
	 * Renvoie une position aléatoire À L’INTÉRIEUR de la worldborder. On prend un
	 * petit "margin" pour éviter de spawn collé au mur.
	 */
	private Location getSafeRandomLocation(World world) {
		WorldBorder wb = world.getWorldBorder();
		// Fallback si la bordure est absente (devrait pas arriver, mais bon)
		if (wb == null) {
			wb = world.getWorldBorder();
			wb.setCenter(0.5, 0.5);
			wb.setSize(tailleBordureDepart <= 0 ? 1000 : tailleBordureDepart);
		}

		final double centerX = wb.getCenter().getX();
		final double centerZ = wb.getCenter().getZ();
		final double half = wb.getSize() / 2.0;

		final int margin = 3; // évite le bord
		final int minX = (int) Math.floor(centerX - half) + margin;
		final int maxX = (int) Math.floor(centerX + half) - margin;
		final int minZ = (int) Math.floor(centerZ - half) + margin;
		final int maxZ = (int) Math.floor(centerZ + half) - margin;

		if (minX > maxX || minZ > maxZ)
			return null; // bordure trop petite

		for (int attempt = 0; attempt < 80; attempt++) {
			int x = minX + RANDOM.nextInt(maxX - minX + 1);
			int z = minZ + RANDOM.nextInt(maxZ - minZ + 1);

			int y = world.getHighestBlockYAt(x, z);
			Material under = world.getBlockAt(x, y - 1, z).getType();

			// safe = bloc solide sous les pieds et pas de lave juste en dessous
			if (under != Material.AIR && under.isSolid() && under != Material.LAVA) {
				// clamping de sécurité (au cas où)
				if (x < minX)
					x = minX;
				if (x > maxX)
					x = maxX;
				if (z < minZ)
					z = minZ;
				if (z > maxZ)
					z = maxZ;

				return new Location(world, x + 0.5, y, z + 0.5);
			}
		}
		return null;
	}

	private static final java.util.Random RANDOM = new java.util.Random();

	private void freezePlayer(Player p) {
		frozenPlayers.add(p); // Bloque les dégâts
		p.setWalkSpeed(0f);
		p.setFlySpeed(0f);
		p.sendMessage(ChatColor.RED + "La partie commence dans " + tempsFreeze + " secondes...");
		spawnParticles(p);
		applyAutartEffects(10);

		new BukkitRunnable() {
			@Override
			public void run() {
				frozenPlayers.remove(p); // Débloque les dégâts
				p.setWalkSpeed(0.2f);
				p.setFlySpeed(0.1f);
				p.sendMessage(ChatColor.GREEN + "C'est parti !");

				// Scoreboard dynamique pour ce joueur
				setupScoreboard(p);

				// Armements one-shot
				if (!postFreezeInitialized) {
					postFreezeInitialized = true;

					if (!cycleArmed) {
						cycleArmed = true;
						startOrRestartDayNightTask(uhcConfig.getSelectedCycleMinutes());
					}
					armEpisodes();
					startTimer();
					gameClockActive = true;
				}
			}
		}.runTaskLater(plugin, tempsFreeze * 20L);
	}

	private void spawnParticles(Player p) {
		new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (ticks >= tempsFreeze * 20) {
					cancel();
					return;
				}

				Location loc = p.getLocation();
				p.getWorld().playEffect(loc, Effect.ENDER_SIGNAL, 0);
				p.getWorld().playEffect(loc, Effect.MOBSPAWNER_FLAMES, 0);
				ticks += 5;
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}
	
	private void applyAutartEffects(int seconds) {
	    int dur = Math.max(1, seconds) * 20; // ticks

	    for (Player p : Bukkit.getOnlinePlayers()) {
	        if (p.getGameMode() != GameMode.SURVIVAL) continue; // on évite staff/spec

	        // Blindness (niveau 1 suffit)
	        p.addPotionEffect(new PotionEffect(
	                PotionEffectType.BLINDNESS,
	                dur,
	                0,          // amplifier 0 = Blindness I
	                true,       // ambient
	                true        // particles visibles
	        ));

	        // Jump Boost ÉNORME -> empêche de sauter en 1.8
	        p.addPotionEffect(new PotionEffect(
	                PotionEffectType.JUMP,
	                dur,
	                250,        // niveau très élevé
	                true,       // ambient
	                true        // particles visibles (mets false si tu veux moins d'effets)
	        ));
	    }
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player) {
			Player p = (Player) e.getEntity();
			if (frozenPlayers.contains(p)) {
				e.setCancelled(true); // Bloque uniquement les joueurs congelés
			}
			// Sinon, laisse passer normalement (pas de e.setCancelled(true) ici)
		}
	}

	@EventHandler
	public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
		if (frozenPlayers.contains(e.getPlayer())) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
		if (e.getDamager() instanceof Player && frozenPlayers.contains((Player) e.getDamager())) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent e) {
		if (frozenPlayers.contains(e.getPlayer())) {
			e.setCancelled(true);
		}
	}

	public boolean isGameStarted() {
		return gameStarted;
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		if (plugin instanceof LGUHC) {
			LGUHC main = (LGUHC) plugin;
			if (isGameStarted()) {
				Bukkit.getScheduler().runTask(plugin, () -> main.removeConfigItems(event.getPlayer()));
			}
		}
	}

	private void giveStartInventory(Player p) {
		ItemStack[] main = uhcConfig.getStartMainTemplate();
		ItemStack[] armor = uhcConfig.getStartArmorTemplate();
		if (main != null) {
			// vide avant
			p.getInventory().clear();
			for (int i = 0; i < 36; i++) {
				ItemStack it = (main[i] == null ? null : main[i].clone());
				p.getInventory().setItem(i, it);
			}
		}
		if (armor != null) {
			// 0:helmet,1:chest,2:legs,3:boots
			p.getInventory().setHelmet(armor[0] == null ? null : armor[0].clone());
			p.getInventory().setChestplate(armor[1] == null ? null : armor[1].clone());
			p.getInventory().setLeggings(armor[2] == null ? null : armor[2].clone());
			p.getInventory().setBoots(armor[3] == null ? null : armor[3].clone());
		}
		p.updateInventory();
	}

	// GameManager.java
	private long startEpochMs = 0L;    // instant de départ (ms)
	private long lastTickedSec = -1L;  // dernière seconde traitée (elapsedSeconds)

	// Appelle UNE SEULE FOIS au vrai départ de la game
	public void startTimer() {
	    if (timerRunnable != null) {
	        try { timerRunnable.cancel(); } catch (Throwable ignored) {}
	        timerRunnable = null;
	    }

	    // initialise l’horloge
	    startEpochMs   = System.currentTimeMillis();
	    elapsedSeconds = 0;
	    lastTickedSec  = -1L;
	    
	    coupleAnnounceScheduled = false;

	    timerRunnable = new BukkitRunnable() {
	        @Override public void run() {
	            try {
	                // seconde courante depuis le départ, arrondie vers le bas
	                long nowSec = (System.currentTimeMillis() - startEpochMs) / 1000L;

	                // rien de nouveau à traiter (ex: tick trop rapide)
	                if (nowSec <= lastTickedSec) return;

	                // rattrapage: traite toutes les secondes manquées
	                for (long s = lastTickedSec + 1; s <= nowSec; s++) {
	                    elapsedSeconds = (int) s;

	                    // === tout ce que tu faisais "par seconde" reste ici ===
	                    updateScoreboardForAll();
	                    roleService.tickPerSecond(elapsedSeconds);
	                    tickRolesAssignmentIfReady();

	                    // Bordure – on déclenche au moment où on franchit le seuil
	                    if (borderShrinkScheduled && !borderShrinkStarted && elapsedSeconds >= borderStartAtSec) {
	                        borderShrinkStarted = true;
	                        World world = Bukkit.getWorlds().get(0);
	                        WorldBorder wb = world.getWorldBorder();
	                        world.setGameRuleValue("doDaylightCycle", "false");
	                        world.setTime(0L);

	                        double current = wb.getSize();
	                        double target  = borderEndDiameter;
	                        if (target < current && borderSpeedBps > 0) {
	                            long seconds = (long) Math.ceil((current - target) / borderSpeedBps);
	                            wb.setSize(target, seconds);
	                            Bukkit.broadcastMessage(ChatColor.GOLD + "La bordure commence à rétrécir jusqu’à "
	                                    + (int) target + " aux alentours de " + borderSpeedBps + " bloc/s.");
	                        }
	                    }

	                    // Épisodes — gère le rattrapage si plusieurs secondes/épisodes passés d’un coup
	                    while (elapsedSeconds >= nextEpisodeAtSec) {
	                        episodeNumber++;
	                        nextEpisodeAtSec += episodeLenSec;
	                        broadcastAll(ChatColor.YELLOW + "Épisode " + episodeNumber);
	                        roleService.onEpisodeStart(episodeNumber);
	                    }
	                    
	                 // planifie UNE FOIS à E2 + 5min
	                    if (!coupleAnnounceScheduled && episodeNumber == 2) {
	                        coupleAnnounceScheduled = true; // empêche les rescheduls
	                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
	                            try {
	                                java.util.UUID[] pair = roleService.getAliveLovers();
	                                if (pair != null) {
	                                    org.bukkit.entity.Player a = Bukkit.getPlayer(pair[0]);
	                                    org.bukkit.entity.Player b = Bukkit.getPlayer(pair[1]);
	                                    roleService.getHybridHandler().announceCoupleNow(a, b);
	                                }
	                            } catch (Throwable t) { t.printStackTrace(); }
	                        }, 5 * 60 * 20L); // +5 min
	                    }


	                    // Révélation des alliés loups (une seule fois quand ep >= 3)
	                    if (episodeNumber == 3 && !wolvesAlliesAnnounced) {
	                        wolvesAlliesAnnounced = true;
	                        roleService.announceWolvesAllies();
	                    }


	                    // Compteur de nuits
	                    boolean nowNight = isCurrentlyNight();
	                    if (nowNight && !lastNightFlag) {
	                        nightCount++;
	                    }
	                    lastNightFlag = nowNight;
	                }

	                // on est à jour
	                lastTickedSec = nowSec;

	            } catch (Throwable t) {
	                t.printStackTrace();
	            }
	        }
	    };

	    // Tâche toutes les 20 ticks (1s nominale) — la dérive est corrigée par le catch-up
	    timerRunnable.runTaskTimer(plugin, 20L, 20L);
	}


	public boolean isCurrentlyDay() {
		return Bukkit.getWorlds().get(0).getTime() < 12000L;
	}

	public int getCurrentDayNumber() {
		return dayCount;
	}

	private void tickRolesAssignmentIfReady() {
	    if (rolesAssigned) return;

	    // Choix host :
	    //  - delay > 0  => annonce/attribution après 'delay' secondes (ex: 30)
	    //  - delay <= 0 => annonce/attribution au début de l’épisode 2
	    int delay = uhcConfig.getAnnonceRolesDelaySeconds(); // 30 ou <=0 pour "épisode 2"

	    if (delay > 0) {
	        if (elapsedSeconds >= delay) {
	            doAssignRoles();
	        }
	        return;
	    }

	    // Mode "Épisode 2"
	    int epLenSec = Math.max(60, uhcConfig.getEpisodeMinutes() * 60); // sécurité min 60s
	    int currentEp = (elapsedSeconds / epLenSec) + 1;                  // 1-indexé
	    if (currentEp >= 2) {
	        doAssignRoles();
	    }
	}


	private void doAssignRoles() {
		if (rolesAssigned)
			return;

		RoleService rs = ((LGUHC) getPlugin()).getRoleService();

		LGUHC main = (LGUHC) getPlugin();
		List<String> enabled = new ArrayList<>();
		enabled.addAll(main.getEnabledVillageMajeurs());
		enabled.addAll(main.getEnabledVillageMineurs());
		enabled.addAll(main.getEnabledVillageAutres());
		enabled.addAll(main.getEnabledHybrides());
		enabled.addAll(main.getEnabledSolitaires());
		enabled.addAll(main.getEnabledLoups());

		if (enabled.isEmpty()) {
			getPlugin().getLogger().warning("[LGUHC] Aucun rôle activé dans les menus. Fallback -> Simple Villageois.");
		}

		getPlugin().getLogger().info("[LGUHC] Attribution des rôles…");
		getPlugin().getLogger().info("[LGUHC] Rôles activés: " + enabled);

		// ✅ Construire la liste des joueurs en jeu et en ligne
		List<Player> players = collectPlayersInGameOnline();

		rs.assignRandomRoles(players, enabled, /* allowDuplicates= */false);

		rolesAssigned = true;
		Bukkit.broadcastMessage(ChatColor.GOLD + "Les rôles ont été annoncés !");
	}

	private void updateScoreboard(Player player) {
		Scoreboard board = player.getScoreboard();
		if (board == null) {
			setupScoreboard(player);
			return;
		}

		Objective obj = board.getObjective(INGAME_OBJ);
		if (obj == null) {
			setupScoreboard(player);
			return;
		}

		board.getEntries().forEach(board::resetScores);

		int line = 9;
		obj.getScore(ChatColor.GRAY + "──────────").setScore(line--);

		int minutes = elapsedSeconds / 60;
		int seconds = elapsedSeconds % 60;
		String time = String.format("%02d:%02d", minutes, seconds);
		obj.getScore(ChatColor.WHITE + "Temps : " + ChatColor.GOLD + time).setScore(line--);

		int epMinutes = 10; // fallback
		try {
			if (uhcConfig != null)
				epMinutes = uhcConfig.getEpisodeMinutes();
		} catch (Exception ignored) {
		}
		int episode = (elapsedSeconds / (epMinutes * 60)) + 1;

		obj.getScore(ChatColor.WHITE + "Épisode : " + ChatColor.AQUA + episode).setScore(line--);

		String jourNuit = (player.getWorld().getTime() >= 0 && player.getWorld().getTime() < 12300) ? "Jour" : "Nuit";
		obj.getScore(ChatColor.WHITE + "Temps : " + ChatColor.GREEN + jourNuit).setScore(line--);

		obj.getScore(ChatColor.WHITE + "Groupes : " + ChatColor.LIGHT_PURPLE + nombreGroupes).setScore(line--);
		obj.getScore(ChatColor.WHITE + "Kills : " + ChatColor.RED + getKills(player)).setScore(line--);
		// AVANT (à éviter pour cet usage) : Bukkit.getOnlinePlayers().size()
		obj.getScore(ChatColor.WHITE + "Joueurs : " + ChatColor.DARK_PURPLE + playersInGame.size()).setScore(line--);

		// 👇 Affiche le DIAMÈTRE (100 = 100x100)
		obj.getScore(ChatColor.WHITE + "± Bordure : " + ChatColor.DARK_PURPLE + tailleBordureDepart / 2)
				.setScore(line--);

		obj.getScore(ChatColor.GRAY + "──────────").setScore(line--);
	}

	private void addKill(Player killer) {
		UUID id = killer.getUniqueId();
		playerKills.put(id, playerKills.getOrDefault(id, 0) + 1);
	}

	private void updateScoreboardForAll() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			updateScoreboard(p); // ← “Kills : ” affiche getKills(p) pour CHAQUE joueur
		}
	}

	private void setupScoreboard(Player p) {
		ScoreboardManager manager = Bukkit.getScoreboardManager();
		Scoreboard board = manager.getNewScoreboard();

		Objective obj = board.registerNewObjective(INGAME_OBJ, "dummy");
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		obj.setDisplayName(ChatColor.DARK_RED + "Loup-Garou UHC");

		p.setScoreboard(board);
		updateScoreboard(p);
	}

	// GameManager

	private BukkitRunnable customCycleTask;

	public void startOrRestartDayNightTask(int minutesPerCycle) {
		if (dayNightTask != null) {
			dayNightTask.cancel();
			dayNightTask = null;
		}
		if (minutesPerCycle <= 0)
			minutesPerCycle = 20;

		final World w = Bukkit.getWorlds().get(0);
		w.setGameRuleValue("doDaylightCycle", "false");

		// On démarre pile au début du jour (0 = levé du soleil)
		w.setTime(0L);
		cycleStartMillis = System.currentTimeMillis();

		// reset des compteurs/phase pour les annonces
		lastPhase = Phase.DAY; // on est en JOUR juste après setTime(0)
		dayCount = 1;
		nightCount = 0;
		// on n’annonce PAS “Jour 1” tout de suite (comme demandé)
		// currentNightNumber reste 0 tant qu’on n’est pas passé en nuit

		final long cycleSec = minutesPerCycle * 60L; // durée d’un cycle complet
		dayNightTask = new BukkitRunnable() {
			long lastMcTime = 0;

			@Override
			public void run() {
				long elapsedSec = (System.currentTimeMillis() - cycleStartMillis) / 1000L;
				long inCycle = (cycleSec == 0 ? 0 : (elapsedSec % cycleSec));
				long mcTime = Math.round(inCycle * (24000.0 / cycleSec)); // 0..23999

				w.setTime(mcTime);

				// Détection propre des transitions
				Phase now = (mcTime < 12000L) ? Phase.DAY : Phase.NIGHT;
				if (lastPhase != now) {
					if (now == Phase.NIGHT) {
						nightCount++;
						currentNightNumber++; // utile pour /lg telescope (1x/nuit)
						broadcast(ChatColor.BLUE + "Nuit " + nightCount);
					} else {
						dayCount++;
						broadcast(ChatColor.GOLD + "Jour " + dayCount);
					}
					lastPhase = now;
				}
				lastMcTime = mcTime;
			}
		};
		dayNightTask.runTaskTimer(plugin, 0L, 20L); // 1 fois par seconde : aucune dérive
	}

	private void checkEpisodeAndPhaseAnnouncements() {
		// Episode switch
		int epMinutes = 10;
		try {
			// pull from UHCConfig if available
			epMinutes = ((LGUHC) plugin).getUhcConfig().getEpisodeMinutes();
		} catch (Exception ignored) {
		}
		int epLenSec = Math.max(60, epMinutes * 60); // safety
		int currentEp = (elapsedSeconds / epLenSec) + 1;

		if (currentEp != lastAnnouncedEpisode) {
			lastAnnouncedEpisode = currentEp;
			broadcast(ChatColor.YELLOW + "Épisode " + currentEp);
		}

		// Day/Night switch (from current world time)
		World w = Bukkit.getWorlds().get(0);
		Phase now = (w.getTime() < 12000L) ? Phase.DAY : Phase.NIGHT;

		if (lastAnnouncedPhase == null) {
			lastAnnouncedPhase = now;
			return;
		}
		if (now != lastAnnouncedPhase) {
			if (now == Phase.DAY) {
				dayCount++;
				broadcast(ChatColor.GOLD + "Jour " + dayCount);
			} else {
				nightCount++;
				broadcast(ChatColor.BLUE + "Nuit " + nightCount);
			}

			lastAnnouncedPhase = now;
		}
	}

	private void armEpisodes() {
		episodeLenSec = Math.max(1, uhcConfig.getEpisodeMinutes() * 60);
		episodeNumber = 1; // scoreboard starts at 1
		nextEpisodeAtSec = episodeLenSec; // first announcement will be Episode 2
	}

	private void tickEpisodes() {
		if (elapsedSeconds >= nextEpisodeAtSec) {
			int completed = episodeNumber;
			roleService.dispatchConteuseSummary(completed);

			// on passe à l’épisode suivant
			episodeNumber++;
			nextEpisodeAtSec += episodeLenSec;

			// 👉 déclenche les hooks d’épisode
			roleService.onEpisodeStart(episodeNumber);
		}
	}

	// GameManager
	public int getEpisodeNumber() {
		int len = Math.max(1, uhcConfig.getEpisodeMinutes() * 60);
		// Épisode 1 pour 0..len-1, épisode 2 pour len..2*len-1, etc.
		return (elapsedSeconds / len) + 1;
	}

	// GameManager
	public Plugin getPlugin() {
		return plugin;
	}

	// tu l’as déjà
	public boolean isCurrentlyNight() {
		// si tu conduis l’horloge custom: basé sur world.getTime() 0..23999
		long t = Bukkit.getWorlds().get(0).getTime();
		return (t >= 12000L);
	}

	public long getCurrentNightNumber() {
		// ex: nuit #1 commence à la première fois où t>=12000 (ou calcule via
		// elapsedSeconds si tu préfères)
		return currentNightNumber; // mets à jour ce compteur dans ton tick cycle (incrément à chaque passage
									// jour->nuit)
	}

	public int getElapsedSeconds() {
		return elapsedSeconds;
	}

	public int getEpisodeLenSec() {
		return Math.max(1, episodeLenSec);
	}

	/**
	 * Appelée par DeathManager quand un kill PVP survient (même si résurrection
	 * ensuite).
	 */
	public void addKillAndUpdateScoreboard(Player killer) {
		UUID id = killer.getUniqueId();
		playerKills.put(id, playerKills.getOrDefault(id, 0) + 1);
		try {
			updateScoreboard(killer); // ta méthode existante
		} catch (Throwable ignored) {
		}
	}

	public int getKills(Player p) {
		return playerKills.getOrDefault(p.getUniqueId(), 0);
	}

	public org.bukkit.Location randomSafeScatter() {
		org.bukkit.World w = org.bukkit.Bukkit.getWorlds().get(0);
		org.bukkit.WorldBorder wb = w.getWorldBorder();
		java.util.Random r = new java.util.Random();

		double radius = wb.getSize() / 2.0 - 5.0; // marge anti-bordure
		org.bukkit.Location center = wb.getCenter();

		for (int tries = 0; tries < 200; tries++) {
			double x = center.getX() + (r.nextDouble() * 2 - 1) * radius;
			double z = center.getZ() + (r.nextDouble() * 2 - 1) * radius;

			int y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
			if (y <= 1)
				continue;

			org.bukkit.block.Block ground = w.getBlockAt((int) Math.floor(x), y - 1, (int) Math.floor(z));
			org.bukkit.Material gtype = ground.getType();
			if (gtype == org.bukkit.Material.LAVA || gtype == org.bukkit.Material.STATIONARY_LAVA
					|| gtype == org.bukkit.Material.WATER || gtype == org.bukkit.Material.STATIONARY_WATER) {
				continue;
			}

			return new org.bukkit.Location(w, Math.floor(x) + 0.5, y + 0.1, Math.floor(z) + 0.5);
		}
		// fallback
		return w.getSpawnLocation();
	}

	// Ermites morts anonymement (pour masquer leur quit-message et ne pas
	// décrémenter les compteurs)
	private final java.util.Set<java.util.UUID> hermitsDead = new java.util.HashSet<>();

	private final Set<UUID> playersInGame = new HashSet<>();

	public void markHermitDead(java.util.UUID id) {
		if (id != null)
			hermitsDead.add(id);
	}

	public boolean isHermitDead(java.util.UUID id) {
		return id != null && hermitsDead.contains(id);
	}

	// Compteur à utiliser dans le scoreboard (au lieu de OnlinePlayers.size())
	public int getPlayersCountForScoreboard() {
		return playersInGame.size();
	}

	// (Optionnel) si tu retires normalement les morts de playersInGame, ne le fais
	// PAS pour l'Ermite.
	// Exemple de méthode générique :
	public void onPlayerConfirmedDead(java.util.UUID id, boolean removeFromGameList) {
		if (id == null)
			return;
		// Pour tous SAUF l’ermite (mort anonyme) on retire :
		if (removeFromGameList)
			playersInGame.remove(id);
	}

	/** Remplit playersInGame avec les joueurs actuellement connectés. */
	public void addAllOnlineToGame() {
		playersInGame.clear();
		for (Player p : Bukkit.getOnlinePlayers()) {
			playersInGame.add(p.getUniqueId());
		}
	}

	/** Broadcast à tous les joueurs EN LIGNE présents dans playersInGame. */
	private void broadcast(String msg) {
		for (UUID id : playersInGame) {
			Player p = Bukkit.getPlayer(id);
			if (p != null && p.isOnline()) {
				p.sendMessage(msg);
			}
		}
	}

	/**
	 * Construit une liste <Player> depuis playersInGame (en filtrant les offline).
	 */
	private List<Player> collectPlayersInGameOnline() {
		List<Player> list = new ArrayList<>();
		for (UUID id : playersInGame) {
			Player p = Bukkit.getPlayer(id);
			if (p != null && p.isOnline())
				list.add(p);
		}
		return list;
	}

	// Dans GameManager
	public boolean isRandomCoupleEnabled() {
		try {
			return uhcConfig != null && uhcConfig.isRandomCoupleEnabled(); // crée ce getter côté UHCConfig
		} catch (Throwable t) {
			return false;
		}
	}

	// ---------- Annonces & fin ----------

	private void announceTeamWin(RoleService.Align align, java.util.List<org.bukkit.entity.Player> alive) {
		String camp = (align == RoleService.Align.VILLAGE) ? "Village" : "Loups-Garous";
		org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + "Victoire du " + camp + " !");
		listWinners(alive);
	}

	private void announceSoloWin(org.bukkit.entity.Player p, RoleService.RoleId rid) {
		String rn = roleService.displayName(rid);
		org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + "Victoire solo de " + org.bukkit.ChatColor.WHITE
				+ p.getName() + org.bukkit.ChatColor.GOLD + " (" + rn + ") !");
		listWinners(java.util.Collections.singletonList(p));
	}

	private void announceCoupleWin(java.util.UUID aId, java.util.UUID bId, java.util.UUID cupidonOpt) {
		org.bukkit.entity.Player a = org.bukkit.Bukkit.getPlayer(aId);
		org.bukkit.entity.Player b = org.bukkit.Bukkit.getPlayer(bId);
		String A = (a != null ? a.getName() : "?");
		String B = (b != null ? b.getName() : "?");
		if (cupidonOpt != null) {
			org.bukkit.entity.Player c = org.bukkit.Bukkit.getPlayer(cupidonOpt);
			String C = (c != null ? c.getName() : "?");
			org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Victoire du Couple (" + A + " ♥ "
					+ B + ") avec " + C + " (Cupidon) !");
			listWinners(namesToPlayers(new String[] { A, B, C }));
		} else {
			org.bukkit.Bukkit.broadcastMessage(
					org.bukkit.ChatColor.LIGHT_PURPLE + "Victoire du Couple (" + A + " ♥ " + B + ") !");
			listWinners(namesToPlayers(new String[] { A, B }));
		}
	}

	private java.util.List<org.bukkit.entity.Player> namesToPlayers(String[] names) {
		java.util.List<org.bukkit.entity.Player> out = new java.util.ArrayList<>();
		for (String n : names) {
			org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayerExact(n);
			if (p != null)
				out.add(p);
		}
		return out;
	}

	private void listWinners(java.util.List<org.bukkit.entity.Player> winners) {
		if (winners == null || winners.isEmpty())
			return;
		StringBuilder sb = new StringBuilder(org.bukkit.ChatColor.GREEN + "Gagnants : ");
		boolean first = true;
		for (org.bukkit.entity.Player p : winners) {
			if (!first)
				sb.append(org.bukkit.ChatColor.GRAY).append(", ");
			first = false;
			RoleService.RoleId r = roleService.getRole(p);
			String rn = (r != null ? roleService.displayName(r) : "?");
			sb.append(org.bukkit.ChatColor.WHITE).append(p.getName()).append(org.bukkit.ChatColor.GRAY).append(" (")
					.append(rn).append(")");
		}
		org.bukkit.Bukkit.broadcastMessage(sb.toString());
	}

	// GameManager.java

	// ===== Helpers “vivants” =====
	private boolean isAlive(java.util.UUID id) {
		org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
		return p != null && p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL && !p.isDead();
	}

	// Couple vivant réciproque (a <-> b) si présent
	private static class CoupleResult {
		java.util.UUID a, b;
	}

	private CoupleResult findCoupleAlive(java.util.List<java.util.UUID> alive) {
		java.util.HashSet<java.util.UUID> seen = new java.util.HashSet<>();
		for (java.util.UUID id : alive) {
			if (seen.contains(id))
				continue;
			RoleService.RoleState s = roleService.getStates().get(id);
			if (s == null || s.lover == null)
				continue;
			if (!alive.contains(s.lover))
				continue;
			RoleService.RoleState t = roleService.getStates().get(s.lover);
			if (t != null && id.equals(t.lover)) {
				CoupleResult cr = new CoupleResult();
				cr.a = id;
				cr.b = s.lover;
				return cr;
			}
		}
		return null;
	}

	private boolean isCupidon(java.util.UUID id) {
		return roleService.isCupidon(id);
	}

	// ===== Annonces =====
	private String nameOf(java.util.UUID id) {
		org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
		return (p != null ? p.getName() : "Joueur");
	}

	private void announceSideWin(String sideName) {
		org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + "Victoire : " + org.bukkit.ChatColor.WHITE
				+ sideName + org.bukkit.ChatColor.GOLD + " !");
	}

	private void announceSoloWin(java.util.UUID winner) {
		org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + "Victoire : " + org.bukkit.ChatColor.WHITE
				+ nameOf(winner) + org.bukkit.ChatColor.GOLD + " (Solo) !");
	}

	private void announceCoupleWin(java.util.UUID a, java.util.UUID b, java.util.List<java.util.UUID> also) {
		String txt = org.bukkit.ChatColor.LIGHT_PURPLE + "Victoire du Couple : " + org.bukkit.ChatColor.WHITE
				+ nameOf(a) + org.bukkit.ChatColor.LIGHT_PURPLE + " ♥ " + org.bukkit.ChatColor.WHITE + nameOf(b);
		if (also != null && !also.isEmpty()) {
			txt += org.bukkit.ChatColor.GRAY + " (+ " + org.bukkit.ChatColor.WHITE;
			boolean first = true;
			for (java.util.UUID id : also) {
				if (!first)
					txt += org.bukkit.ChatColor.GRAY + ", " + org.bukkit.ChatColor.WHITE;
				txt += nameOf(id);
				first = false;
			}
			txt += org.bukkit.ChatColor.GRAY + ")";
		}
		org.bukkit.Bukkit.broadcastMessage(txt);
	}

	// GameManager.java
	public enum WinTeam {
		VILLAGE, LOUPS, LOVERS, SOLO, UNKNOWN
	}

	private boolean cupidonCountsWithLovers = true; // si Cupidon gagne avec le couple

	public void setCupidonCountsWithLovers(boolean b) {
		this.cupidonCountsWithLovers = b;
	}

	private WinTeam winTeamOf(Player p) {
	    RoleService rs = roleService;
	    RoleService.RoleState s = rs.get(p);
	    if (s == null) return WinTeam.UNKNOWN;

	    // 1) Amoureux : prioritaire (ta logique existante : le couple peut "compter" Village)
	    if (s.lover != null) {
	        return (rs.getCurrentCoupleSide() == RoleService.CoupleSide.COUPLE)
	                ? WinTeam.LOVERS
	                : WinTeam.VILLAGE;
	    }

	    // 2) Infecté : compte Loups au team-check
	    if (s.infectedAsWolf) return WinTeam.LOUPS;

	    // 3) Cupidon v2 : suit le couple (sinon Village)
	    if (s.roleId == RoleService.RoleId.CUPIDON_V2) {
	        return rs.doesCupidV2SideWithCouple(s) ? WinTeam.LOVERS : WinTeam.VILLAGE;
	    }

	    // 4) Cupidon v1 (si option legacy activée)
	    if (s.roleId == RoleService.RoleId.CUPIDON && cupidonCountsWithLovers) {
	        java.util.UUID[] pair = rs.getAliveLovers();
	        if (pair != null && rs.getCurrentCoupleSide() == RoleService.CoupleSide.COUPLE) {
	            return WinTeam.LOVERS;
	        }
	        return WinTeam.VILLAGE;
	    }

	 // ---- Voleur ----
	    if (s.roleId == RoleService.RoleId.VOLEUR) {
	        if (s.voleurStolen) {
	            RoleService.Align eff = roleService.effectiveWinAlign(p.getUniqueId());
	            if (eff == RoleService.Align.LOUP)    return WinTeam.LOUPS;
	            if (eff == RoleService.Align.VILLAGE) return WinTeam.VILLAGE;
	            return WinTeam.SOLO; // sécurité : ex. rôle volé “neutre”
	        } else {
	            return WinTeam.SOLO; // tant qu’il n’a pas volé, c’est un solo
	        }
	    }

	    // 7) Reste : solos/unknown neutres
	    if (s.align == RoleService.Align.SOLITAIRE) return WinTeam.SOLO;
	    return WinTeam.UNKNOWN;
	}


	private void declareWinOnce(String msg) {
		if (winAnnounced)
			return;
		winAnnounced = true;
		Bukkit.broadcastMessage(msg);
		endGame(); // tu peux y laisser l’arrêt des tasks etc.
	}

	public List<Player> getAlivePlayers() {
		List<Player> out = new ArrayList<>();
		for (UUID id : playersInGame) { // playersInGame = Set<UUID> des joueurs en partie
			Player p = Bukkit.getPlayer(id);
			if (p == null)
				continue;
			if (!p.isOnline())
				continue;
			if (p.getGameMode() != GameMode.SURVIVAL)
				continue;
			if (p.isDead() || p.getHealth() <= 0.0)
				continue;

			// si ton DeathManager gère un état "sursis"
			if (deathManager != null && deathManager.isPending(id))
				continue;

			out.add(p);
		}
		return out;
	}

	public void endGame() {
		// Empêche les doubles arrêts
		if (!gameStarted && winAnnounced) {
			return;
		}
		
		if (gameTickTask != null) {
		    try { gameTickTask.cancel(); } catch (Throwable ignored) {}
		    gameTickTask = null;
		}


		try {
			if (timerRunnable != null)
				timerRunnable.cancel();
		} catch (Throwable ignored) {
		}
		timerRunnable = null;

		try {
			if (dayNightTask != null)
				dayNightTask.cancel();
		} catch (Throwable ignored) {
		}
		dayNightTask = null;

		try {
			if (customCycleTask != null)
				customCycleTask.cancel();
		} catch (Throwable ignored) {
		}
		customCycleTask = null;

		// 2) Rétablir les gamerules/monde
		try {
			World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
			if (w != null) {
				// Remet le cycle jour/nuit vanilla
				w.setGameRuleValue("doDaylightCycle", "true");
			}
		} catch (Throwable ignored) {
		}

		// 3) Débloquer et nettoyer tous les joueurs
		for (Player p : Bukkit.getOnlinePlayers()) {
			try {
				// si le joueur était freeze, on le “défreeze”
				if (frozenPlayers.remove(p)) {
					p.setWalkSpeed(0.2f);
					p.setFlySpeed(0.1f);
				}
				// tu peux le laisser dans son mode actuel, ou forcer :
				// p.setGameMode(GameMode.SURVIVAL);

				// Option : nettoyer le scoreboard côté joueur
				try {
					ScoreboardManager sm = Bukkit.getScoreboardManager();
					if (sm != null)
						p.setScoreboard(sm.getNewScoreboard());
				} catch (Throwable ignored) {
				}

				p.sendMessage(ChatColor.GOLD + "Partie terminée.");
			} catch (Throwable ignored) {
			}
		}

		// 4) Réinitialiser les états internes pour une future partie
		gameStarted = false;

		// On garde winAnnounced à true pour éviter tout “re-broadcast”
		// Il sera remis à false dans startGame().
		// winAnnounced = true;

		postFreezeInitialized = false;
		gameClockActive = false;

		rolesAssigned = false;

		// Bordure : on annule juste les flags (ne touche pas la taille réelle)
		borderShrinkScheduled = false;
		borderShrinkStarted = false;

		// Chronos / épisodes
		elapsedSeconds = 0;
		episodeNumber = 1;
		nextEpisodeAtSec = 0;

		// Cycle custom
		cycleArmed = false;
		lastPhase = Phase.DAY;
		dayCount = 1;
		nightCount = 0;

		// Kills & joueurs en jeu : à toi de voir si tu veux “purger”
		// Ici on nettoie tout : ça évite de garder des stats après la fin
		playerKills.clear();
		playersInGame.clear();

		// Option : petit broadcast de fin
		Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[LGUHC] La partie est terminée. Merci d'avoir joué !");
	}

	// GameManager.java
	public void checkWin() {
	    if (winAnnounced) return;
	    if (victoryAnnounced) return;

	    List<Player> alive = getAlivePlayers();
	    Bukkit.getLogger().info("[WinCheck] alive=" + alive.size());

	    // ✅ 0 ou 1 survivant -> traiter AVANT toute autre logique
	    if (alive.isEmpty()) {
	        declareWinOnce("§6Fin de partie : plus aucun survivant.");
	        return;
	    }
	    
	 // --- Priorité Solitaire : s'il existe un Solitaire vivant, la meute ne peut pas gagner tant qu'il reste >1 joueur.
//	     Si 1 seul joueur vivant et que c'est le Solitaire -> il gagne seul.

	boolean hasSolitaireAlive = false;
	java.util.UUID lastAlive = null;
	int aliveCount = 0;

	for (fr.sfakeur.lguhc.RoleService.RoleState s : roleService.getStates().values()) {
	    if (dm().isAlive(s.owner)) {
	        aliveCount++;
	        lastAlive = s.owner;
	        if (s.solitaire) hasSolitaireAlive = true;
	    }
	}

	if (hasSolitaireAlive) {
	    if (aliveCount == 1 && lastAlive != null) {
	        org.bukkit.entity.Player w = org.bukkit.Bukkit.getPlayer(lastAlive);
	        String nm = (w != null ? w.getName() : roleService.nameOfUUID(lastAlive));

	        // Annonce propre + fin de partie standard
	        declareWinOnce(org.bukkit.ChatColor.DARK_RED + " Le Loup-Garou Solitaire "
	                + org.bukkit.ChatColor.GOLD + nm
	                + org.bukkit.ChatColor.DARK_RED + " a remporte la partie !");
	        endGame(); // ou stopGame() selon ton implémentation
	        return;
	    }
	    // sinon on ne bloque pas le reste: le Solitaire n'est plus compté comme loup grâce à isWolf(s) modifié.
	}


	    
	    if (alive.size() == 1) {
	        Player last = alive.get(0);

	        // si tu as déjà winTeamOf(last), garde-le :
	        WinTeam t = winTeamOf(last);
	        switch (t) {
	            case VILLAGE:
	                declareWinOnce("§aVictoire du Village !");
	                break;
	            case LOUPS:
	                declareWinOnce("§cVictoire des Loups-Garous !");
	                break;
	            case LOVERS:
	                declareWinOnce("§dVictoire du Couple !");
	                break;
	            case SOLO: {
	                RoleService.RoleState s = roleService.get(last);
	                String rn = (s != null) ? roleService.displayName(s.roleId) : "Solo";
	                declareWinOnce("§eVictoire du " + rn + " !");
	                break;
	            }
	            default:
	                // Si winTeamOf retourne UNKNOWN, on tombe sur l’align effectif
	                RoleService.Align eff = roleService.effectiveWinAlign(last.getUniqueId());
	                if (eff == RoleService.Align.VILLAGE) {
	                    declareWinOnce("§aVictoire du Village !");
	                } else if (eff == RoleService.Align.LOUP) {
	                    declareWinOnce("§cVictoire des Loups-Garous !");
	                } else {
	                    RoleService.RoleState s = roleService.get(last);
	                    String rn = (s != null) ? roleService.displayName(s.roleId) : "Solo";
	                    declareWinOnce("§eVictoire du " + rn + " !");
	                }
	        }
	        return;
	    }
		
		// 3) Comptage simple des camps présents parmi les vivants
		boolean anyWolf = false;
		boolean anyVillage = false;
		boolean anyBlocker = false; // SOLO/LOVERS/UNKNOWN/hybrides bloquants

		for (Player p : alive) {
		    RoleService.RoleState s = roleService.get(p);

		    // ✅ aligne le comptage sur l'align *effectif* (gère Voleur après vol)
		    RoleService.Align eff = roleService.effectiveWinAlign(p.getUniqueId());
		    if (eff == RoleService.Align.LOUP)      anyWolf = true;
		    else if (eff == RoleService.Align.VILLAGE) anyVillage = true;
		    else anyBlocker = true; // NEUTRE, solos, Voleur avant vol, etc.


		    // Règles spéciales déjà en place
		    if (s != null && s.infectedAsWolf && s.infectedNoWolfWin) {
		        anyBlocker = true; // bloque la win des loups
		    }

		    // Statuts “hors camps” (Couple, Solo…) restent bloquants
		    WinTeam t = winTeamOf(p);
		    switch (t) {
		        case LOVERS:
		        case SOLO:
		        case UNKNOWN:
		            anyBlocker = true;
		            break;
		        case LOUPS:
		        case VILLAGE:
		        default:
		            // couvert par eff ci-dessus
		            break;
		    }
		}



		// Après avoir récupéré 'alive'
		boolean allLovers = alive.stream().allMatch(p -> winTeamOf(p) == WinTeam.LOVERS);
		if (allLovers) {
			declareWinOnce("§dVictoire du Couple !");
			return;
		}

		// 4) Victoires de camp robustes aux doublons
		// Loups gagnent si: il y a des loups, et aucun village, et aucun bloqueur
		if (anyWolf && !anyVillage && !anyBlocker) {
			declareWinOnce("§cVictoire des Loups-Garous !");
			return;
		}

		// Village gagne si: il y a du village, et aucun loup, et aucun bloqueur
		if (anyVillage && !anyWolf && !anyBlocker) {
			declareWinOnce("§aVictoire du Village !");
			return;
		}

		// 5) Pas de gagnant encore
		Bukkit.getLogger().info("[WinCheck] No winner yet. Camps: wolf=" + anyWolf + " village=" + anyVillage
				+ " blocker=" + anyBlocker);
	}

	public int getNightCount() {
		return nightCount;
	}

	@org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
		org.bukkit.entity.Player attacker = null;

		// melee
		if (e.getDamager() instanceof org.bukkit.entity.Player) {
			attacker = (org.bukkit.entity.Player) e.getDamager();
		}
		// projectiles tirés par un joueur (optionnel : commente si tu ne veux pas
		// nerfer à distance)
		else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
			Object shooter = ((org.bukkit.entity.Projectile) e.getDamager()).getShooter();
			if (shooter instanceof org.bukkit.entity.Player)
				attacker = (org.bukkit.entity.Player) shooter;
		}

		if (attacker == null)
			return;

		// Chercher un effet de Force
		org.bukkit.potion.PotionEffect eff = null;
		for (org.bukkit.potion.PotionEffect pe : attacker.getActivePotionEffects()) {
			if (pe.getType().equals(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE)) {
				eff = pe;
				break;
			}
		}
		if (eff == null)
			return; // pas de Force

		int amp = eff.getAmplifier(); // 0 = Force I, 1 = Force II
		double vanillaBonus = 3.0 * (amp + 1); // règle 1.8
		double reduction = vanillaBonus * 0.5; // nerf x2 -> enlève la moitié

		double newDamage = e.getDamage() - reduction;
		if (newDamage < 0.0)
			newDamage = 0.0; // sécurité
		e.setDamage(newDamage);
	}

	// Ouvre une fenêtre de vote de 5 minutes
	public void openVoteWindow(int episodeNumber) {
		if (voteWindowOpen)
			return;
		voteWindowOpen = true;
		voteEpisodeOfWindow = episodeNumber;
		currentVotes.clear();
		tally.clear();
		voteCloseAtMs = System.currentTimeMillis() + 5 * 60_000L; // 5 min

		broadcastAll(org.bukkit.ChatColor.GOLD + "[Vote] " + org.bukkit.ChatColor.YELLOW
				+ "Le vote est ouvert pour 5 minutes ! Utilisez " + org.bukkit.ChatColor.WHITE + "/lg vote <pseudo>"
				+ org.bukkit.ChatColor.YELLOW + ". (Épisode " + episodeNumber + ")");

		// Programme la fermeture
		if (voteCloseTask != null) {
			try {
				voteCloseTask.cancel();
			} catch (Throwable ignored) {
			}
		}
		voteCloseTask = new org.bukkit.scheduler.BukkitRunnable() {
			@Override
			public void run() {
				try {
					closeVoteWindow();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}.runTaskLater(main, 5 * 60 * 20L); // 5 min en ticks
	}

	// Ferme la fenêtre, comptabilise, applique le malus temporaire
	// Ferme la fenêtre, comptabilise, applique le malus temporaire
	private void closeVoteWindow() {
	    if (!voteWindowOpen) return;
	    voteWindowOpen = false;
	    voteCloseAtMs = 0L;
	    if (voteCloseTask != null) {
	        try { voteCloseTask.cancel(); } catch (Throwable ignored) {}
	        voteCloseTask = null;
	    }

	    // Recalcule le tally pour être sûr
	    tally.clear();
	    for (java.util.Map.Entry<java.util.UUID, java.util.UUID> e : currentVotes.entrySet()) {
	        java.util.UUID target = e.getValue();
	        tally.put(target, tally.getOrDefault(target, 0) + 1);
	    }

	    if (tally.isEmpty()) {
	        broadcastAll(org.bukkit.ChatColor.GOLD + "[Vote] " + org.bukkit.ChatColor.GRAY
	                + "Aucun vote reçu. Rien ne se passe.");
	        return;
	    }

	    // Trouve le max + tie-breaking aléatoire
	    int best = -1;
	    java.util.List<java.util.UUID> leaders = new java.util.ArrayList<>();
	    for (java.util.Map.Entry<java.util.UUID, Integer> e : tally.entrySet()) {
	        int c = e.getValue();
	        if (c > best) {
	            best = c;
	            leaders.clear();
	            leaders.add(e.getKey());
	        } else if (c == best) {
	            leaders.add(e.getKey());
	        }
	    }
	    java.util.UUID punished = leaders.get(new java.util.Random().nextInt(leaders.size()));
	    org.bukkit.entity.Player loser = org.bukkit.Bukkit.getPlayer(punished);

	    String loserName = (loser != null ? loser.getName() : "?");
	    broadcastAll(org.bukkit.ChatColor.GOLD + "[Vote] " + org.bukkit.ChatColor.YELLOW
	            + "Le vote est clos. Le joueur sanctionné est " + org.bukkit.ChatColor.RED + loserName
	            + org.bukkit.ChatColor.YELLOW + " (" + best + " vote" + (best > 1 ? "s" : "") + ").");

	    // Appliquer −5♥ max pendant 5 minutes, puis rendre les ♥ (sans soigner)
	    if (loser != null && loser.isOnline() && loser.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
	        applyTemporaryMaxHealthDebuff(loser, 5, 5 * 60); // 5 hearts, 300s
	    }

	    // === Déchu : attribuer les coeurs permanents (1 coeur / 2 votes) ===
	    try {
	        if (roleService != null) {
	            // copie défensive du tally pour le RoleService
	            java.util.Map<java.util.UUID, Integer> counts = new java.util.HashMap<>(tally);
	            java.util.UUID top = punished; // le plus voté après tie-break
	            roleService.onLG9Tally(counts, top);
	        }
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }
	}


	// Commande /lg vote <pseudo>
	public boolean handleVoteCommand(org.bukkit.entity.Player voter, String[] args) {
	    if (getVoteMode() != 9) {  // ✅ on interroge GameManager, pas LGUHC
	        voter.sendMessage("§7Le système de vote n’est pas actif.");
	        return true;
	    }
		if (!voteWindowOpen) {
			voter.sendMessage("§7Il n’y a pas de vote en cours.");
			return true;
		}
		if (args.length < 1) {
			voter.sendMessage("§eUsage: /lg vote <pseudo>");
			return true;
		}

		org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
		if (target == null || !target.isOnline()) {
			voter.sendMessage("§cJoueur introuvable.");
			return true;
		}
		if (target.equals(voter)) {
			voter.sendMessage("§cTu ne peux pas voter pour toi-même.");
			return true;
		}
		if (!isAlive(target.getUniqueId())) { // si tu as déjà un helper else fais un check via DeathManager
			voter.sendMessage("§cTu ne peux pas voter pour un joueur mort.");
			return true;
		}

		java.util.UUID vId = voter.getUniqueId();
		java.util.UUID tId = target.getUniqueId();

		// Si le joueur change son vote, on remplace
		java.util.UUID old = currentVotes.put(vId, tId);
		if (old == null) {
			voter.sendMessage("§aVote enregistré pour §f" + target.getName() + "§a.");
		} else if (!old.equals(tId)) {
			voter.sendMessage("§aVote modifié pour §f" + target.getName() + "§a.");
		} else {
			voter.sendMessage("§7Tu avais déjà voté pour §f" + target.getName() + "§7.");
		}

		return true;
	}

	// Applique un malus temporaire de max HP (en coeurs) pour durationSec, puis
	// rend les coeurs (sans soigner)
	private void applyTemporaryMaxHealthDebuff(org.bukkit.entity.Player p, int hearts, int durationSec) {
		// Empilement simple : si déjà sous malus, on remplace par le plus sévère / ou
		// on ignore. Ici on remplace.
		removeVoteDebuffIfAny(p);

		boolean ok = roleService.changeMaxHearts(p, -hearts); // -5 hearts = -10 HP
		if (!ok) {
			p.sendMessage("§7Le malus de vote n’a pas pu être appliqué.");
			return;
		}
		activeVoteDebuffHearts.put(p.getUniqueId(), hearts);
		long endAt = System.currentTimeMillis() + (durationSec * 1000L);
		activeVoteDebuffEndMs.put(p.getUniqueId(), endAt);

		p.sendMessage("§c-5♥ max pendant 5 minutes (vote).");

		// planifie la fin
		new org.bukkit.scheduler.BukkitRunnable() {
			@Override
			public void run() {
				try {
					removeVoteDebuffIfAny(p);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}.runTaskLater(main, durationSec * 20L);
	}

	private void removeVoteDebuffIfAny(org.bukkit.entity.Player p) {
		java.util.UUID id = p.getUniqueId();
		Integer h = activeVoteDebuffHearts.remove(id);
		activeVoteDebuffEndMs.remove(id);
		if (h != null && h > 0) {
			roleService.changeMaxHearts(p, +h); // on rend les cœurs, la vie actuelle n’est PAS soignée (comportement
												// voulu)
			p.sendMessage("§7Le malus de vote est terminé. Tes cœurs max reviennent.");
		}
	}
	
	// GameManager.java
	private int countAlivePlayers() {
	    int c = 0;
	    DeathManager dm = dm(); // raccourci
	    for (fr.sfakeur.lguhc.RoleService.RoleState s : roleService.getStates().values()) {
	        if (dm.isAlive(s.owner)) c++;
	    }
	    return c;
	}

	
	private void armSolitaireTriggerOnGameStart() {
	    this.solitaireInitialAlive = countAlivePlayers();
	    if (solitaireInitialAlive <= 0) return;

	    // Seuil aléatoire 65%..90% des vivants initiaux
	    double pct = 0.65 + (rand.nextDouble() * 0.25); // 0.65 → 0.90
	    this.solitaireTriggerAliveCount = (int) Math.ceil(solitaireInitialAlive * pct);

	    // Mode test : 60..120 secondes
	    if (solitaireTest) {
	        this.solitaireTriggerAtSec = 60 + rand.nextInt(61); // [60;120]
	    }

	    org.bukkit.Bukkit.getLogger().info("[Solitaire] seuil=" + solitaireTriggerAliveCount
	            + " / init=" + solitaireInitialAlive
	            + " ; testAt=" + solitaireTriggerAtSec + "s");
	}
	
	// GameManager.java
	private DeathManager dm() {
	    return roleService.getPlugin().getDeathManager(); // RoleService -> LGUHC -> DeathManager
	}



	
	



}
