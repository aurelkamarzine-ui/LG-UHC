package fr.sfakeur.lguhc;

import fr.sfakeur.lguhc.RoleService;



import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;

public class WolfHandler implements AlignHandler, org.bukkit.event.Listener {
	
	 private static final int HUGE = 9_999_999; // durée très longue, pas de flicker
	 
	 private final java.util.Random rnd = new java.util.Random();
	 private boolean wasNight = false;
	 
	 
	
	 private final RoleService core;
	 
	// tâches d’update de la flèche pour chaque Peureux
	 private final Map<UUID, BukkitTask> peureuxArrowTasks = new HashMap<>();
	 
	// ==== Hurlement global (un seul hurlement actif à la fois) ====
	 private java.util.UUID activeHowlEmitter = null; // loup qui vient de hurler
	 private long activeHowlEndMs;               // fin de l’effet (compas 60s)

	 private static final int HOWL_RADIUS_BLOCKS = 50;  // rayon pour compter les loups proches
	 private static final int HOWL_DURATION_SEC  = 60;  // durée de la flèche/hud

	 
	 @Override public void onPlayerDeath(Player dead) {}
	 
	 @Override
	 public void onEpisodeStart(int episodeNumber) {
	     int minutes = Math.max(1, core.getGame().getUhcConfig().getEpisodeMinutes());
	     ///S'il le garde que jusuqu'a fin d'ep
	     for (RoleService.RoleState s : core.getStates().values()) {
	    	    if (s.roleId == RoleService.RoleId.LOUP_GAROU_FEUTRE) {
	    	        s.feutreActive = false;
	    	        s.feutreShownRole = null;
	    	    }
	    	}
	     ///
	     long delayTicks = (minutes * 60L / 2L) * 20L; // milieu d’épisode
	     org.bukkit.Bukkit.getScheduler().runTaskLater(core.getPlugin(),
	         () -> assignFeutreFacadeForEpisode(episodeNumber),
	         delayTicks);
	 }


	 private org.bukkit.scheduler.BukkitTask activeHowlTask;
	 
	 private static final int HOWL_TICK_PERIOD  = 10; // 0.5s (20 ticks = 1s)
	 
	// en haut du WolfHandler (ou utils)
	 private static final int INFINITE = Integer.MAX_VALUE; // ~2,1 milliards de ticks ≈ “infini” côté client
	 
	// en haut de la classe
	 private boolean wolfListAnnounced = false;

	 public boolean isWolfListAnnounced() { return wolfListAnnounced; }
	 public void resetWolfListAnnounced() { wolfListAnnounced = false; }
	 private void setWolfListAnnounced(boolean b) { wolfListAnnounced = b; }
	 
	// WolfHandler.java
	 public java.util.List<String> visibleWolfNamesFor(java.util.UUID viewerId) {
	     return wolfNamesVisibleTo(viewerId); // ta méthode privée existante
	 }
	 
	 
	// Chanceux : qui a déjà reçu le teaser "dans 1 min" pour ce cycle jour
	 private final java.util.Set<java.util.UUID> luckyPreviewSent = new java.util.HashSet<>();
	 
	// --- Chanceux : pré-annonce 1 min avant la nuit ---
	 private long lastPhaseChangeMs = 0L;   // timestamp du dernier basculement jour/nuit
	 private boolean preNightWarnArmed = false; // armé pendant le jour pour prévenir à -60s
	 
	// ==== Solitaire (lazy init) ====
	 private boolean soloInitDone = false;
	 private boolean soloTriggered = false;
	 private int     soloTriggerAtSec = -1;          // si testMode -> 60..120 s
	 private int     soloTriggerAliveThreshold = -1; // sinon -> seuil de vivants

	 
	 








	 
	 

	 



	 
	 
	 
	 


	 public WolfHandler(RoleService core) {
		    this.core = core;
	 }



	 // Remplace entièrement isWolfNow(...) et isWolfish(...)
	    private boolean isWolf(RoleService.RoleState s) {
	        return core != null && core.isWolf(s); // inclut align=LOUP ou infectedAsWolf
	    }




	    @Override
	    public void tickPerSecond(int elapsedSec) {
	        boolean isNight = core.getGame().isCurrentlyNight();
	        
	     // --- Solitaire: init paresseuse + déclenchement ---
	        ensureSoloInit();
	        if (core.isSolitaireEnabled() && !soloTriggered) {
	            boolean fire = false;
	            if (soloTriggerAtSec > 0 && elapsedSec >= soloTriggerAtSec) {
	                fire = true;
	            } else if (soloTriggerAliveThreshold > 0) {
	                int alive = countAlivePlayers();
	                if (alive <= soloTriggerAliveThreshold) fire = true;
	            }
	            if (fire) tryTriggerSolitaire();
	        }

	        
	

	        if (isNight != wasNight) {
	            // Changement de phase
	            wasNight = isNight;
	            core.setLastNightFlag(isNight);

	            // marque le moment du basculement et arme/désarmer le warning
	            lastPhaseChangeMs = System.currentTimeMillis();
	            preNightWarnArmed = !isNight; // on arme pendant le jour (pour prévenir 1 min avant la nuit)

	            if (isNight) {
	                startLuckyNight();
	                applyNightEffectsForAllWolves(true);
	            } else {
	                clearLuckyNight();
	                applyNightEffectsForAllWolves(false);
	            }
	        }
	        
	        // --- PRE-ANNONCE CHANCEUX : 1 minute avant le début de la nuit ---
	        // (on ne fait ça que le jour, une seule fois par cycle)
	        if (!isNight && preNightWarnArmed) {
	            long nextNightAt = lastPhaseChangeMs + (cycleTotalMs() / 2L); // la nuit commence à mi-cycle
	            long warnAt      = nextNightAt - 60_000L;                     // -60s
	            long now         = System.currentTimeMillis();

	            if (now >= warnAt && now < nextNightAt) {
	                // Pour chaque Loup Chanceux, tire (si pas déjà tiré) et annonce le buff de la prochaine nuit
	                for (RoleService.RoleState s : core.getStates().values()) {
	                    if (s.roleId != RoleService.RoleId.LOUP_GAROU_CHANCEUX) continue;
	                    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	                    if (p == null || !p.isOnline()) continue;

	                    // si aucun buff “pré-tiré” pour la nuit à venir, on le tire maintenant et on le mémorise
	                    if (s.luckyTonight == null) {
	                        RoleService.RoleState.LuckyBuff[] pool = new RoleService.RoleState.LuckyBuff[] {
	                            RoleService.RoleState.LuckyBuff.RESIST,
	                            RoleService.RoleState.LuckyBuff.REGEN,
	                            RoleService.RoleState.LuckyBuff.STRENGTH,
	                            RoleService.RoleState.LuckyBuff.SPEED
	                        };
	                        s.luckyTonight = pool[rnd.nextInt(pool.length)];
	                    }

	                    p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loup Chanceux] "
	                        + org.bukkit.ChatColor.GRAY + "Dans 1 minute, tu recevras : "
	                        + org.bukkit.ChatColor.GOLD + niceBuffName(s.luckyTonight));
	                }
	                preNightWarnArmed = false; // on ne spam pas
	            }
	        }

	        wasNight = isNight;

	        // Optionnel: si la durée d’un effet expire, on le “rafraîchit” de temps en temps pendant la nuit
	        if (isNight && (elapsedSec % 15 == 0)) {
	            refreshMissingEffects();
	        }
	        updatePeureuxArrowForAll();
	        
	       tickIpdlMarkingPerSecond();
	       
	       long now = System.currentTimeMillis();
	       if (isWolfListAnnounced()) { // <-- clé : jamais avant EP3
	           for (RoleService.RoleState s : core.getStates().values()) {
	               if (s.roleId != RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) continue;
	               if (!s.amnV1Awake) continue;
	               if (s.amnV1NextRevealAtMs <= 0L || now < s.amnV1NextRevealAtMs) continue;

	               // Chercher un loup vivant pas encore connu (différent de soi)
	               java.util.UUID toReveal = null;
	               for (RoleService.RoleState cand : core.getStates().values()) {
	                   if (cand.owner.equals(s.owner)) continue;
	                   if (!core.isWolf(cand)) continue;
	                   if (!isAliveUUID(cand.owner)) continue;        // helper (voir plus bas)
	                   if (s.amnV1KnownWolves.contains(cand.owner)) continue;
	                   toReveal = cand.owner; break;
	               }

	               if (toReveal != null) {
	                   s.amnV1KnownWolves.add(toReveal);
	                   org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	                   org.bukkit.entity.Player w = org.bukkit.Bukkit.getPlayer(toReveal);
	                   String nm = (w != null ? w.getName() : core.nameOfUUID(toReveal));
	                   if (p != null && p.isOnline() && nm != null) {
	                       p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
	                           + org.bukkit.ChatColor.GRAY + "Tu apprends le nom d’un allié : "
	                           + org.bukkit.ChatColor.GOLD + nm);
	                   }
	               }

	               s.amnV1NextRevealAtMs = now + 5L * 60_000L; // prochaine tranche 5 min
	           }
	       }



	       
	       tickAmnesiqueV1Timers();
	       

	        
	        
	    }


	    @Override
	    public void onPlayerKill(Player killer, Player victim) {
	        if (killer == null || !core.isWolf(killer)) return;
	        RoleService.RoleState ks = core.get(killer);
	        if (ks != null && ks.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1 && !ks.amnV1Awake) return;

	        // ➜ ouvre la fenêtre “ancienne méthode”
	        core.getPlugin().getDeathManager().armInfectOfferAfterWolfKill(victim.getUniqueId());

	    }




	    @Override
	    public boolean handleSubCommand(String sub, Player sender, String[] args) {
	    	if ("infect".equalsIgnoreCase(sub)) {
	    	    return cmdInfect(sender);
	    	}

	    	
	    	if ("hurler".equalsIgnoreCase(sub)) {
	            org.bukkit.entity.Player senderPl = sender;
	            RoleService.RoleState st = core.get(senderPl);
	            if (st == null) return true;

	            // Option host activée ?
	            LGUHC main = core.getPlugin();
	            if (main == null || !main.isWolfHowlEnabled()) {
	                sender.sendMessage(org.bukkit.ChatColor.RED + "Le hurlement est désactivé par l’host.");
	                return true;
	            }

	            // 1) Loup & non exclu
	         // 1) Éligibilité au hurlement
	            boolean excluded = isHowlExcluded(st.roleId);

	            // Exception pour l'Amnésique v1 : il NE peut pas hurler avant le réveil,
	            // mais il PEUT hurler dès qu'il est réveillé OU s'il est infecté.
	            if (st.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) {
	                if (st.amnV1Awake || st.infectedAsWolf) {
	                    excluded = false; // autoriser après réveil / infection
	                } else {
	                    excluded = true;  // bloquer avant réveil
	                }
	            }

	            // Doit être considéré "loup" côté logique (réveillé/infecté) ET non exclu
	            if (!core.isWolf(st) || excluded) {
	                senderPl.sendMessage(org.bukkit.ChatColor.RED + "Tu ne peux pas hurler.");
	                return true;
	            }


	            // 2) À partir de l’épisode 3
	            if (core.getGame().getEpisodeNumber() < 3) {
	                senderPl.sendMessage(org.bukkit.ChatColor.YELLOW + "Tu ne peux hurler qu’à partir de l’épisode 3.");
	                return true;
	            }

	            // 3) 1 fois par partie
	            if (st.wolfHowlUsed) {
	                senderPl.sendMessage(org.bukkit.ChatColor.GRAY + "Tu as déjà hurlé.");
	                return true;
	            }

	            // 4) Pas de hurlement déjà en cours (global)
	            long now = System.currentTimeMillis();
	            if (activeHowlEmitter != null && now < activeHowlEndMs) {
	                senderPl.sendMessage(org.bukkit.ChatColor.GRAY + "Un hurlement est déjà en cours.");
	                return true;
	            }

	            // --- Déclencher le hurlement correctement ---
	            st.wolfHowlUsed = true;

	            // Son global (aucun message “chat” de position)
	            for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
	                try { pl.playSound(senderPl.getLocation(), org.bukkit.Sound.WOLF_HOWL, 1.0f, 1.0f); } catch (Throwable ignored) {}
	            }
	            
	            boolean isAlpha = (st.roleId == RoleService.RoleId.LOUP_GAROU_ALPHA);
	            if (isAlpha) {
	                org.bukkit.Location L = senderPl.getLocation();
	                String coord = ChatColor.RED + "x:" + ChatColor.WHITE + (int)Math.floor(L.getX())
	                             + ChatColor.RED + " y:" + ChatColor.WHITE + (int)Math.floor(L.getY())
	                             + ChatColor.RED + " z:" + ChatColor.WHITE + (int)Math.floor(L.getZ());

	                for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
	                    RoleService.RoleState ws = core.get(w);
	                    if (ws != null && core.isWolf(ws) && !w.getUniqueId().equals(senderPl.getUniqueId())) {
	                        w.sendMessage(ChatColor.DARK_RED + "[Hurlement Alpha] "
	                                    + ChatColor.GRAY + "Rendez-vous aux coordonnées: " + coord);
	                    }
	                }
	            }


	         // Après avoir lancé le hurlement (activeHowlEmitter/EndMs), et joué le son :
		         for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
		             RoleService.RoleState s = core.get(pl);
		             if (s == null) continue;

		             // Petite Fille : si elle est invisible → HUD (5s au 1er hurlement, +3s par hurlement, cap 30s)
		             if (s.roleId == RoleService.RoleId.PETITE_FILLE && s.invisActive) {
		                 long base = Math.max(now, s.pfHowlHudEndMs);
		                 // +5s la première fois, +3s à chaque hurlement suivant
		                 s.pfHowlHudEndMs = base + 5_000L;
		                 s.pfHowlHudEndMs += 3_000L;
		                 // plafond pour éviter l’abus
		                 if (s.pfHowlHudEndMs > now + 30_000L) s.pfHowlHudEndMs = now + 30_000L;
		             }
		         }


	            // (optionnel) petit retour au hurleur pour confirmer
	            senderPl.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Hurlement] " 
	                    + org.bukkit.ChatColor.GRAY + "Tu hurles. Tes alliés voient une flèche indiquant ta position.");

	    	 // Lance la tâche HUD flèche+boussole pour tous les loups
	    	 startHowlArrowTaskForAll(senderPl);

	    	    // Comptage des loups (loup blanc/chien-loup inclus si tu les marques dans isWolfish) à 50 blocs du hurleur
	    	    int count = 0;
	    	    org.bukkit.Location cLoc = senderPl.getLocation();
	    	    double r2 = HOWL_RADIUS_BLOCKS * HOWL_RADIUS_BLOCKS;
	    	    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
	    	        if (pl.equals(senderPl)) continue; // “autour de lui” → on n’inclut pas l’émetteur
	    	        if (!pl.isOnline() || pl.getWorld() != senderPl.getWorld()) continue;
	    	        if (pl.getLocation().distanceSquared(cLoc) > r2) continue;
	    	        RoleService.RoleState os = core.get(pl);
	    	        if (os != null && isWolf(os)) count++;;
	    	    }
	    	    senderPl.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Hurlement] "
	    	            + org.bukkit.ChatColor.YELLOW + "Loups détectés à " + HOWL_RADIUS_BLOCKS
	    	            + " blocs : " + org.bukkit.ChatColor.WHITE + count);

	    	    return true;
	    	}
	    	
	    	if ("call".equalsIgnoreCase(sub)) {
	    	    RoleService.RoleState s = core.get(sender);
	    	    if (s == null || s.roleId != RoleService.RoleId.LOUP_GAROU_ALPHA) {
	    	        sender.sendMessage(ChatColor.RED + "Commande réservée au Loup-Garou Alpha.");
	    	        return true;
	    	    }

	    	    int ep = core.getGame().getEpisodeNumber();
	    	    if (s.alphaCallLastEpisode == ep) {
	    	        sender.sendMessage(ChatColor.GRAY + "Tu as déjà utilisé /lg call à l’épisode " + ep + ".");
	    	        return true;
	    	    }

	    	    if (args == null || args.length == 0) {
	    	        sender.sendMessage(ChatColor.YELLOW + "Usage: /lg call <message>");
	    	        return true;
	    	    }

	    	    String msg = String.join(" ", args);
	    	    s.alphaCallLastEpisode = ep;

	    	    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
	    	        RoleService.RoleState ws = core.get(pl);
	    	        if (ws != null && core.isWolf(ws) && pl.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
	    	            pl.sendMessage(ChatColor.DARK_RED + "[Alpha] " + ChatColor.GRAY + msg);
	    	        }
	    	    }
	    	    sender.sendMessage(ChatColor.DARK_RED + "[Alpha] " + ChatColor.GRAY + "Message envoyé à la meute.");
	    	    return true;
	    	}
	    	
	    	if ("tenebres".equalsIgnoreCase(sub)) {
	    	    RoleService.RoleState s = core.get(sender);
	    	    if (s == null || s.roleId != RoleService.RoleId.LOUP_GAROU_TENEBREUX) {
	    	        sender.sendMessage(org.bukkit.ChatColor.RED + "Commande réservée au Loup-Garou Ténébreux.");
	    	        return true;
	    	    }
	    	    if (sender.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
	    	        sender.sendMessage(org.bukkit.ChatColor.RED + "Tu dois être en vie.");
	    	        return true;
	    	    }
	    	    long now = System.currentTimeMillis()/1000L;
	    	    long cd  = RoleService.RoleState.TENEBRES_COOLDOWN_SEC;
	    	    if (now < s.tenebresLastUseSec + cd) {
	    	        long left = s.tenebresLastUseSec + cd - now;
	    	        sender.sendMessage(org.bukkit.ChatColor.RED + "Cooldown: " + left + "s.");
	    	        return true;
	    	    }

	    	    // OK → applique cécité autour (100 blocs) aux NON-loups pendant 15s
	    	    final int R = 100;
	    	    final int D = 15 * 20; // 15s
	    	    org.bukkit.Location L = sender.getLocation();

	    	    int affected = 0;
	    	    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
	    	        if (pl.equals(sender)) continue;
	    	        if (!pl.isOnline() || pl.getWorld() != sender.getWorld()) continue;
	    	        if (pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
	    	        if (pl.getLocation().distanceSquared(L) > R*R) continue;

	    	        RoleService.RoleState ts = core.get(pl);
	    	        // cible = non-loup
	    	        if (core.isWolf(ts)) continue;

	    	        try {
	    	            pl.addPotionEffect(new org.bukkit.potion.PotionEffect(
	    	                org.bukkit.potion.PotionEffectType.BLINDNESS, D, 0,
	    	                true /* ambient */, false /* particles OFF */));
	    	            affected++;
	    	        } catch (Throwable ignored) {}
	    	    }

	    	    s.tenebresLastUseSec = now;
	    	    sender.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Ténébreux] "
	    	        + org.bukkit.ChatColor.GRAY + "Cécité appliquée aux non-loups dans " + R + " blocs "
	    	        + org.bukkit.ChatColor.GOLD + "(" + affected + " joueur" + (affected>1?"s":"") + ").");
	    	    return true;
	    	}



	        return false;
	    }
	    
	    private void startLuckyNight() {
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_CHANCEUX) continue;
	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline()) continue;

	            // Tirage une seule fois par nuit : si déjà pré-tiré lors du warning, on le respecte
	            if (!s.luckyNightApplied) {
	                if (s.luckyTonight == null) {
	                    fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff[] pool =
	                        new fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff[] {
	                            fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.RESIST,
	                            fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.REGEN,
	                            fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.STRENGTH,
	                            fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.SPEED
	                        };
	                    s.luckyTonight = pool[rnd.nextInt(pool.length)];
	                }
	                s.luckyNightApplied = true;
	            }

	            applyLuckyBuff(p, s.luckyTonight);
	            giveNightVision(p, true);

	            p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loup Chanceux] "
	                + org.bukkit.ChatColor.GRAY + "Cette nuit, tu obtiens : "
	                + org.bukkit.ChatColor.GOLD + niceBuffName(s.luckyTonight));
	        }
	    }



	    private void endLuckyNight() {
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_CHANCEUX) continue;
	            s.luckyNightApplied = false;        // on rerollera la nuit prochaine
	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline()) continue;
	            // Optionnel: retirer les effets nocturnes si tu les appliques en “permanent”
	            // (Si tu appliques chaque tick/à la minute, pas besoin de remove ici)
	            removeLuckyBuffs(p);
	            giveNightVision(p, false);
	        }
	    }

	    private void removeLuckyBuffs(org.bukkit.entity.Player p) {
	        p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
	        p.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
	        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
	        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
	    }

	    private void clearLuckyNight() {
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.LOUP_GAROU_CHANCEUX) continue;
	            Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline()) continue;

	            try {
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
	            } catch (Throwable ignored) {}

	            // Réarmer pour la nuit suivante
	            s.luckyNightApplied = false;
	            s.luckyTonight = null; // on refera un tirage (ou un pré-tirage) la prochaine journée
	        }
	    }



	    private void refreshMissingEffects() {
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.LOUP_GAROU_CHANCEUX) continue;
	            Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline()) continue;
	            if (s.luckyTonight == null) continue;

	            // Si le joueur a perdu l’effet (lait, autre), on le remet
	            boolean has = hasEffectFor(p, s.luckyTonight);
	            if (!has) applyLuckyBuff(p, s.luckyTonight);
	        }
	    }

	    private boolean hasEffectFor(Player p, RoleService.RoleState.LuckyBuff b) {
	        org.bukkit.potion.PotionEffectType t =
	                (b == RoleService.RoleState.LuckyBuff.RESIST)   ? org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE :
	                (b == RoleService.RoleState.LuckyBuff.REGEN)    ? org.bukkit.potion.PotionEffectType.REGENERATION :
	                (b == RoleService.RoleState.LuckyBuff.STRENGTH) ? org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE :
	                                                                   org.bukkit.potion.PotionEffectType.SPEED;
	        return p.hasPotionEffect(t);
	    }

	    private void applyLuckyBuff(Player p, RoleService.RoleState.LuckyBuff b) {
	        int dur = 6 * 60 * 20; // ~6 min (suffit pour la nuit classique, on refresh si besoin)
	        switch (b) {
	            case RESIST:
	                // “Résistance 0.5” n’existe pas en vanilla : on met Résistance I (amplifier=0)
	                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                        org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, HUGE, 0, true, false));
	                break;
	            case REGEN:
	                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                        org.bukkit.potion.PotionEffectType.REGENERATION, HUGE, 0, true, false));
	                break;
	            case STRENGTH:
	                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                        org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, HUGE, 0, true, false));
	                break;
	            case SPEED:
	                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                        org.bukkit.potion.PotionEffectType.SPEED, HUGE, 0, true, false));
	                break;
	        }
	    }

	    private String niceBuffName(RoleService.RoleState.LuckyBuff b) {
	        switch (b) {
	            case RESIST:   return "Résistance I";
	            case REGEN:    return "Régénération I";
	            case STRENGTH: return "Force I";
	            case SPEED:    return "Vitesse I";
	        }
	        return "?";
	    }




	    private void giveNightVision(Player p, boolean on) {
	        if (on) {
	            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.NIGHT_VISION, 20 * 60 * 10, 0, true, false));
	        } else {
	            p.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
	        }
	    }
	    

	    // Jour: on retire les buffs nocturnes du Peureux
	    private void clearNightBuffs() {
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.LOUP_GAROU_PEUREUX) continue;
	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline()) continue;
	            try {
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
	            } catch (Throwable ignored) {}
	        }
	    }

	    // ---- PHOBIE : boussole vers le loup le plus proche (allié), rayon 100 ----
	 // WolfHandler.java
	    private void updatePeureuxArrow(Player peureux) {
	        // (optionnel) si un hurlement est actif, on laisse son HUD prioritaire
	        if (getActiveHowlEmitter() != null) return;

	        Player nearestWolf = getNearestWolf(peureux, 100);
	        if (nearestWolf == null) {
	            sendActionBar(peureux, "§7Aucun loup proche");
	            return;
	        }

	        Location loc = peureux.getLocation();
	        Location target = nearestWolf.getLocation();
	        if (!loc.getWorld().equals(target.getWorld())) {
	            sendActionBar(peureux, "§7Aucun loup proche");
	            return;
	        }

	        double dx = target.getX() - loc.getX();
	        double dz = target.getZ() - loc.getZ();
	        float absoluteYawToTarget = (float) Math.toDegrees(Math.atan2(-dx, dz));
	        float relativeYaw = (absoluteYawToTarget - loc.getYaw() + 360f) % 360f;

	        String arrow;
	        if (relativeYaw >= 337.5 || relativeYaw < 22.5)      arrow = "§a↑";
	        else if (relativeYaw < 67.5)  arrow = "§a↗";
	        else if (relativeYaw < 112.5) arrow = "§a→";
	        else if (relativeYaw < 157.5) arrow = "§a↘";
	        else if (relativeYaw < 202.5) arrow = "§a↓";
	        else if (relativeYaw < 247.5) arrow = "§a↙";
	        else if (relativeYaw < 292.5) arrow = "§a←";
	        else                          arrow = "§a↖";

	        int dist = (int) Math.round(loc.distance(target));
	        // ✅ ENVOI EFFECTIF EN ACTION BAR
	        sendActionBar(peureux, "§eLoup proche : " + arrow + " §7(" + dist + "m)");
	    }

	    
	    private void updatePeureuxArrowForAll() {
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId == RoleService.RoleId.LOUP_GAROU_PEUREUX) {
	                Player p = Bukkit.getPlayer(s.owner);
	                if (p != null && p.isOnline()) {
	                    updatePeureuxArrow(p); // ← la méthode que tu as déjà
	                }
	            }
	        }
	    }

	    
	    private void startPeureuxArrowTask(Player peureux) {
	        stopPeureuxArrowTask(peureux); // évite doublons

	        BukkitTask task = new BukkitRunnable() {
	            @Override public void run() {
	                if (peureux == null || !peureux.isOnline()) { cancel(); return; }

	                RoleService.RoleState st = core.get(peureux);
	                if (st == null || st.roleId != RoleService.RoleId.LOUP_GAROU_PEUREUX) {
	                    // Plus le bon rôle → on arrête
	                    cancel(); 
	                    return;
	                }
	                updatePeureuxArrow(peureux);
	            }
	        }.runTaskTimer(core.getPlugin(), 0L, 20L); // chaque seconde

	        peureuxArrowTasks.put(peureux.getUniqueId(), task);
	    }

	    private void stopPeureuxArrowTask(Player p) {
	        BukkitTask t = peureuxArrowTasks.remove(p.getUniqueId());
	        if (t != null) t.cancel();
	    }
	    
	    private Player getNearestWolf(Player origin, int radius) {
	        Player best = null;
	        double bestDist2 = (double) radius * radius;

	        for (Player p : Bukkit.getOnlinePlayers()) {
	            if (p.getUniqueId().equals(origin.getUniqueId())) continue;
	            if (!p.isOnline()) continue;

	            RoleService.RoleState st = core.get(p);
	            if (!core.isWolf(st)) continue;

	            if (!p.getWorld().equals(origin.getWorld())) continue;

	            double d2 = p.getLocation().distanceSquared(origin.getLocation());
	            if (d2 <= bestDist2) {
	                bestDist2 = d2;
	                best = p;
	            }
	        }
	        return best;
	    }
	    
	    public void armPeureux(Player p) {
	        startPeureuxArrowTask(p);
	    }

	    public void disarmPeureux(Player p) {
	        stopPeureuxArrowTask(p);
	    }
	    
	    
	 // WolfHandler.java
	    private boolean isHowlExcluded(RoleService.RoleId r) {
	        if (r == null) return true;
	        // Craintif
	        //if (r == RoleService.RoleId.LOUP_GAROU_PEUREUX) return true;

	        // Si tu as/ajouteras ces enums dans RoleId :
	        try {
	            if (r.name().equals("LOUP_GAROU_AMNESIQUE")) return true;
	            if (r.name().equals("LOUP_GAROU_AMNESIQUE_V1")) return true;
	        } catch (Throwable ignored) {}

	        return false;
	    }
	    
	    private void sendHurlActionBar(org.bukkit.entity.Player player, String message) {
	        try {
	            Class<?> iChatBaseComponent = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
	            Class<?> chatSerializer = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
	            Object comp = chatSerializer.getMethod("a", String.class)
	                .invoke(null, "{\"text\":\"" + message.replace("\"","\\\"") + "\"}");
	            Class<?> packetPlayOutChat = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");
	            Object packet = packetPlayOutChat
	                .getConstructor(iChatBaseComponent, byte.class)
	                .newInstance(comp, (byte)2); // 2 = action bar
	            Object handle = player.getClass().getMethod("getHandle").invoke(player);
	            Object connection = handle.getClass().getField("playerConnection").get(handle);
	            Class<?> packetClass = Class.forName("net.minecraft.server.v1_8_R3.Packet");
	            connection.getClass().getMethod("sendPacket", packetClass).invoke(connection, packet);
	        } catch (Throwable t) {
	            // Fallback (vieux spigot) : message normal
	            try { player.sendMessage(message); } catch (Throwable ignored) {}
	        }
	    }
	    
	    
	    

public void startHowl(Player wolf) {
    this.activeHowlEmitter = wolf.getUniqueId();
    this.activeHowlEndMs   = System.currentTimeMillis() + HOWL_DURATION_SEC * 1000L;
}

public java.util.UUID getActiveHowlEmitter() {
    if (activeHowlEmitter == null) return null;
    if (System.currentTimeMillis() > activeHowlEndMs) return null;
    return activeHowlEmitter;
}

public long getActiveHowlRemainingMs() {
    long left = activeHowlEndMs - System.currentTimeMillis();
    return Math.max(0L, left);
}


//WolfHandler.java
public void revealWolfAlliesToWolves() {
 // Pas d'annonce globale fixe : on envoie à chacun SA liste visible
 for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
     if (!core.isWolf(s)) continue;

     org.bukkit.entity.Player viewer = org.bukkit.Bukkit.getPlayer(s.owner);
     if (viewer == null || !viewer.isOnline()) continue;

     java.util.List<String> names = wolfNamesVisibleTo(viewer.getUniqueId());
     String line = names.isEmpty() ? "aucun" : String.join(", ", names);

     viewer.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
         + org.bukkit.ChatColor.GRAY + "Alliés visibles : "
         + org.bukkit.ChatColor.GOLD + line);
 }
}


//Action bar (1.8)
private void sendActionBar(org.bukkit.entity.Player p, String msg) {
    try {
        Class<?> icbc = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
        Class<?> ser  = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
        Object comp   = ser.getMethod("a", String.class)
                           .invoke(null, "{\"text\":\"" + msg.replace("\"","\\\"") + "\"}");
        Class<?> pktC = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");
        Object pkt    = pktC.getConstructor(icbc, byte.class).newInstance(comp, (byte)2);
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Object conn   = handle.getClass().getField("playerConnection").get(handle);
        Class<?> P    = Class.forName("net.minecraft.server.v1_8_R3.Packet");
        conn.getClass().getMethod("sendPacket", P).invoke(conn, pkt);
    } catch (Throwable ignored) { try { p.sendMessage(msg); } catch (Throwable ig) {} }
}


//Flèche relative (8 directions)
private String dirArrow(org.bukkit.entity.Player viewer, org.bukkit.Location target) {
 org.bukkit.Location loc = viewer.getLocation();
 if (!loc.getWorld().equals(target.getWorld())) return "§7?";
 double dx = target.getX() - loc.getX();
 double dz = target.getZ() - loc.getZ();
 float absYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
 float rel = (absYaw - loc.getYaw() + 360f) % 360f;

 if (rel >= 337.5 || rel < 22.5) return "§c↑";
 else if (rel < 67.5)  return "§c↗";
 else if (rel < 112.5) return "§c→";
 else if (rel < 157.5) return "§c↘";
 else if (rel < 202.5) return "§c↓";
 else if (rel < 247.5) return "§c↙";
 else if (rel < 292.5) return "§c←";
 else                  return "§c↖";
}

private void stopHowlTask() {
    try { if (activeHowlTask != null) activeHowlTask.cancel(); } catch (Throwable ignored) {}
    activeHowlTask = null;
}

private void startHowlArrowTaskForAll(final org.bukkit.entity.Player emitter) {
    stopHowlTask();

    this.activeHowlEmitter = emitter.getUniqueId();
    this.activeHowlEndMs   = System.currentTimeMillis() + HOWL_DURATION_SEC * 1000L;

    // <<< CLASSE NOMMÉE, PAS D’ANONYME >>>
    this.activeHowlTask = new HowlHudTask(this.activeHowlEmitter, this.activeHowlEndMs).runTaskTimer(
            core.getPlugin(), 0L, 10L); // 0.5s
}

//Classe interne nommée => sera compilée en WolfHandler$HowlHudTask.class
private final class HowlHudTask extends org.bukkit.scheduler.BukkitRunnable {
 private final java.util.UUID emitter;
 private final long endMs;

 HowlHudTask(java.util.UUID emitter, long endMs) {
     this.emitter = emitter;
     this.endMs   = endMs;
 }

 @Override public void run() {
     long now = System.currentTimeMillis();
     if (now > endMs) {
         stopHowlTask();
         activeHowlEmitter = null;
         activeHowlEndMs   = 0L;
         return;
     }

     org.bukkit.entity.Player em = org.bukkit.Bukkit.getPlayer(emitter);
     if (em == null || !em.isOnline()) {
         stopHowlTask();
         activeHowlEmitter = null;
         activeHowlEndMs   = 0L;
         return;
     }

     for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
         RoleService.RoleState s = core.get(pl);
         if (!isWolf(s)) continue;
         if (pl.getUniqueId().equals(emitter)) continue;

         try { pl.setCompassTarget(em.getLocation()); } catch (Throwable ignored) {}

         if (pl.getWorld().equals(em.getWorld())) {
             int dist = (int) Math.round(pl.getLocation().distance(em.getLocation()));
             String arrow = dirArrow(pl, em.getLocation());
             sendActionBar(pl,  arrow + " §7(" + dist + "m)");
         } else {
             sendActionBar(pl, "§4Hurlement §7→ §7cible hors-monde");
         }
     }
 }
}

//constantes (une seule fois dans le fichier)
private static final int    IPDL_NEED_SEC   =600;   // 5 min
private static final double IPDL_R          = 8.0;   // rayon (blocs)
private static final double IPDL_R2         = IPDL_R * IPDL_R;
private static final long   HUD_PERIOD_MS   = 1000L; // HUD chaque seconde

//Si tu utilises l'option B (HUD local) :
private final java.util.Map<java.util.UUID, Long> ipdlHudLastMs = new java.util.HashMap<>();

private void tickIpdlMarkingPerSecond() {
 java.util.List<org.bukkit.entity.Player> online = new java.util.ArrayList<>();
 for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) if (p.isOnline()) online.add(p);

 long now = System.currentTimeMillis();

 for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
     if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.INFECT_PERE_DES_LOUPS) continue;

     org.bukkit.entity.Player ipdl = org.bukkit.Bukkit.getPlayer(s.owner);
     if (ipdl == null || !ipdl.isOnline() || ipdl.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

     // 👉 Si déjà une cible marquée définitivement, on n’essaie PLUS de marquer d’autres joueurs
     final java.util.UUID locked = s.ipdlMarkedTarget;

     java.util.HashSet<java.util.UUID> seenThisTick = new java.util.HashSet<>();

     for (org.bukkit.entity.Player target : online) {
         if (target.equals(ipdl)) continue;
         if (target.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
         if (target.getWorld() != ipdl.getWorld()) continue;
         if (target.getLocation().distanceSquared(ipdl.getLocation()) > IPDL_R2) continue;

         java.util.UUID tid = target.getUniqueId();

         // 👉 Si verrouillé sur une cible, on ignore tout le reste
         if (locked != null && !tid.equals(locked)) continue;

         seenThisTick.add(tid);

         // Tant que rien n'est verrouillé, on peut accumuler sur plusieurs personnes ;
         // Mais dès qu’une atteint le seuil, on verrouille définitivement.
         int sec = s.ipdlProxSec.getOrDefault(tid, 0) + 1;
         s.ipdlProxSec.put(tid, sec);

         if (sec >= IPDL_NEED_SEC && s.ipdlMarkedTarget == null) {
             // ✅ Marquage RÉUSSI → on lock sur CETTE cible
             s.ipdlMarkedTarget = tid;

             // Normalise les structures existantes à "une seule cible"
             s.ipdlMarked.clear();
             s.ipdlMarked.add(tid);
             s.ipdlProxSec.clear(); // on ne compte plus du tout sur d’autres

             ipdl.sendMessage("§4[Infect] §7Tu as §lmarqué définitivement§7 §f" + target.getName() + "§7.");
             try { ipdl.playSound(ipdl.getLocation(), org.bukkit.Sound.LEVEL_UP, 1f, 1.2f); } catch (Throwable ignored) {}
         }
     }

     // Nettoyage : on efface les compteurs des joueurs sortis du rayon (seulement si pas encore verrouillé)
     if (s.ipdlMarkedTarget == null && !s.ipdlProxSec.isEmpty()) {
         java.util.ArrayList<java.util.UUID> toReset = new java.util.ArrayList<>();
         for (java.util.Map.Entry<java.util.UUID,Integer> e : s.ipdlProxSec.entrySet()) {
             if (!seenThisTick.contains(e.getKey())) toReset.add(e.getKey());
         }
         for (java.util.UUID id : toReset) s.ipdlProxSec.remove(id);
     }
 }
}





private java.util.List<String> currentWolfNames() {
    java.util.List<String> out = new java.util.ArrayList<>();
    for (RoleService.RoleState s : core.getStates().values()) {
        if (!core.isWolf(s)) continue;               // ✅ inclut infectés
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
        if (p != null && p.isOnline()) out.add(p.getName());
    }
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    return out;
}

public void announceWolfListNow() {
    wolfListAnnounced = true;
    
    // ⏱️ Démarrer la cadence 5 min pour chaque Amnésique v1 déjà réveillé
    long now = System.currentTimeMillis();
    for (RoleService.RoleState s : core.getStates().values()) {
        if (s.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1 && s.amnV1Awake) {
            // (Re)base le timer sur l’annonce si non amorcé ou déjà passé
            if (s.amnV1NextRevealAtMs == 0L || s.amnV1NextRevealAtMs <= now) {
                s.amnV1NextRevealAtMs = now + 5L * 60_000L;
            }
        }
    }

    java.util.List<String> names = new java.util.ArrayList<>();
    for (RoleService.RoleState s : core.getStates().values()) {
        if (!isWolf(s)) continue;
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
        if (p != null && p.isOnline()) names.add(p.getName());
    }
    java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    String line = names.isEmpty() ? "aucun" : String.join(", ", names);

    for (RoleService.RoleState s : core.getStates().values()) {
        if (!isWolf(s)) continue;
        org.bukkit.entity.Player w = org.bukkit.Bukkit.getPlayer(s.owner);
        if (w != null && w.isOnline()) {
            w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
                + org.bukkit.ChatColor.GRAY + "Loups actuels : "
                + org.bukkit.ChatColor.GOLD + line);
        }
    }
}




public void sendWolfListTo(org.bukkit.entity.Player wolf) {
    java.util.List<String> names = currentWolfNames();
    wolf.sendMessage(ChatColor.DARK_RED + "[Loups] " + ChatColor.GRAY + "Loups actuels : " +
                     ChatColor.GOLD + (names.isEmpty() ? "aucun" : String.join(", ", names)));
}

public void broadcastNewWolfJoin(String name) {
    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
        RoleService.RoleState s = core.get(pl);
        if (!isWolf(s)) continue;
        pl.sendMessage(ChatColor.DARK_RED + "[Loups] " +  ChatColor.GRAY + " Un nouveau joueur a rejoint votre camp.");
    }
}


//Durée "quasi infinie" (30 minutes) : 30*60*20 = 36 000 ticks
private static final int LONG = 30 * 60 * 20;

//Appelé à CHAQUE passage jour -> nuit
//Appliqué au changement de phase uniquement
private void applyNightEffectsForAllWolves(boolean night) {
 for (RoleService.RoleState s : core.getStates().values()) {
	 if (!isWolf(s)) continue; // <-- ton helper doit déjà exclure v1 non réveillé
	    // sécurité spécifique v1 :
	    if (s.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) continue;
	    
     org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
     if (p == null || !p.isOnline()) continue;
     

     if (night) {
         p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, HUGE, 0, true, false));
         if (s.roleId == RoleService.RoleId.LOUP_GAROU_CHANCEUX) {
        	 
         }
         else {
         p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, HUGE, 0, true, false));
         }
     } else {
         p.removePotionEffect(PotionEffectType.NIGHT_VISION);
         p.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
     }
 }
}

private void clearNightEffectsFromWolves() {
 for (RoleService.RoleState s : core.getStates().values()) {
     if (!isWolf(s)) continue;
     org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
     if (p == null || !p.isOnline()) continue;
     p.removePotionEffect(PotionEffectType.NIGHT_VISION);
     p.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
 }
}



public void announceWolvesListIfNeeded() {
 // ta logique existante d’annonce initiale...
 // à la fin :
 wolfListAnnounced = true;
}



//Optionnel : si tu veux armer juste son HUD sans relancer pour tous
private void startHowlArrowTaskForPlayer(org.bukkit.entity.Player p) {
 // Si tu gères déjà une tâche globale, tu peux ignorer ; sinon, applique le même code que pour “Lance la tâche HUD ... pour tous les loups”
}

public boolean isWolvesListAnnounced() { return wolfListAnnounced; }

public java.util.List<org.bukkit.entity.Player> currentWolvesOnline() {
    java.util.List<org.bukkit.entity.Player> out = new java.util.ArrayList<>();
    for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
        RoleService.RoleState s = core.get(p);
        if (s == null) continue;
        if (core.isWolf(s)) out.add(p); // ✅ inclut infectés
    }
    return out;
}




public void refreshPackAfterInfect(java.util.UUID newWolfId) {
    org.bukkit.entity.Player nw = org.bukkit.Bukkit.getPlayer(newWolfId);
    if (nw == null) return;

    // 1) Toujours prévenir la meute (message générique)
    java.util.List<org.bukkit.entity.Player> wolves = currentWolvesOnline();
    for (org.bukkit.entity.Player w : wolves) {
        if (w.equals(nw)) continue;
        try {
            w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
                + org.bukkit.ChatColor.GRAY + "Un nouveau joueur a rejoint votre camp.");
        } catch (Throwable ignored) {}
    }

    // 2) N’envoyer la liste au nouvel infecté QUE si l’annonce globale a déjà eu lieu
    if (isWolfListAnnounced()) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (org.bukkit.entity.Player w : wolves) names.add(w.getName());
        try {
            nw.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
                + org.bukkit.ChatColor.GRAY + "Meute actuelle : "
                + org.bukkit.ChatColor.GOLD + String.join(", ", names));
            nw.sendMessage(org.bukkit.ChatColor.GRAY + "Tu peux utiliser "
                + org.bukkit.ChatColor.WHITE + "/lg hurler" + org.bukkit.ChatColor.GRAY + ".");
        } catch (Throwable ignored) {}
    }
}

//Filtre la liste vue par un "viewer" (l'Amnésique ne voit que les loups découverts,
//les loups "classiques" ne voient pas l'Amnésique tant qu'il n'est pas révélé)
/// Filtre la liste vue par un "viewer"
public java.util.List<String> wolfNamesVisibleTo(java.util.UUID viewerId) {
    // Porte EP3 : tant que la liste n’est pas annoncée, rien
    if (!isWolfListAnnounced()) return java.util.Collections.emptyList();

    fr.sfakeur.lguhc.RoleService.RoleState vs = core.getStates().get(viewerId);
    java.util.List<String> out = new java.util.ArrayList<>();
    if (vs == null) return out;

    // 0) Le viewer voit TOUJOURS son propre pseudo s'il est loup (inclut Amnésique v1 réveillé)
    if (core.isWolf(vs)) {
        org.bukkit.entity.Player self = org.bukkit.Bukkit.getPlayer(viewerId);
        String selfName = (self != null && self.isOnline()) ? self.getName() : core.nameOfUUID(viewerId);
        if (selfName != null) out.add(selfName);
    }

    // 1) Cas Amnésique v1 (réveillé) : sa liste = lui-même + amnV1KnownWolves
    if (vs.roleId == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) {
        if (vs.amnV1Awake) {
            for (java.util.UUID id : vs.amnV1KnownWolves) {
                if (id.equals(viewerId)) continue; // déjà ajouté
                org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(id);
                String nm = (pl != null && pl.isOnline()) ? pl.getName() : core.nameOfUUID(id);
                if (nm != null) out.add(nm);
            }
        }
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out; // avant ou après EP3, on revient ici (porte EP3 est tout en haut)
    }

    // 2) Autres loups (non-amnésique v1)
    for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
        if (!core.isWolf(s)) continue;
        if (s.owner.equals(viewerId)) continue; // self déjà ajouté

        if (vs.roleId == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_AMNESIQUE) {
            // Amnésique “classique” : ne voit que les loups découverts (≤10 blocs)
            if (!vs.amnDiscovered.contains(s.owner)) continue;
        } else {
            // Les autres loups ne voient pas un amnésique tant qu'il n'est pas visible
            boolean isAmn = (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_AMNESIQUE
                          || s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1);
            if (isAmn && !s.amnVisibleToWolves) continue;
        }

        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
        if (p != null && p.isOnline()) out.add(p.getName());
    }
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    return out;
}


// Helper "vivant ?" (mets-le dans WolfHandler si tu ne l'as pas déjà)
private boolean isAliveUUID(java.util.UUID id) {
    try { return core.getPlugin().getDeathManager().isAlive(id); }
    catch (Throwable ignored) {
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
        return p != null && p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL;
    }
}



//Mi-EP4 : rend l'Amnésique "visible" dans la liste des loups, puis envoie une liste à jour à la meute
public void revealAmnesiquesToWolves() {
 for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
     if (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_AMNESIQUE && !s.amnVisibleToWolves) {
         s.amnVisibleToWolves = true;
         org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
         if (p != null && p.isOnline()) {
             p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Amnésique] "
                 + org.bukkit.ChatColor.GRAY + "Tu es désormais reconnu par les loups.");
         }
     }
 }

 // Prévenir chaque loup avec sa liste "visible" à jour
 for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
     fr.sfakeur.lguhc.RoleService.RoleState ws = core.get(w);
     if (ws != null && core.isWolf(ws)) {
         java.util.List<String> names = wolfNamesVisibleTo(w.getUniqueId());
         String line = names.isEmpty() ? "aucun" : String.join(", ", names);
         w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
             + org.bukkit.ChatColor.GRAY + "Un nouveau joueur viens de rejoindre votre camp");
     }
 }
}


private void tickAmnesiqueV1Timers() {
    long now = System.currentTimeMillis();
    java.util.Random r = new java.util.Random();

    for (RoleService.RoleState s : core.getStates().values()) {
        if (s.roleId != RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) continue;
        if (!s.amnV1Awake) continue; // pas encore mordu

        // 1) 3 minutes après le coup → il devient visible aux autres loups
        if (!s.amnVisibleToWolves && (now - s.amnV1AwakenAtMs) >= 180_000L) {
            s.amnVisibleToWolves = true;   // il apparaît chez les autres
            s.amnV1ListActive = true;      // sa liste “existe” (vide)
            s.amnV1NextRevealAtMs = now + 300_000L; // première révélation dans 5 min

            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
            if (p != null && p.isOnline()) {
                p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Amnésique v1] "
                    + org.bukkit.ChatColor.GRAY + "Tu es Loup-Garou Amnésique V1: les loups te reconaissent");
            }

        }

        // 2) Toutes les 5 minutes → il révèle 1 nouveau loup dans SA liste
        if (s.amnV1ListActive && now >= s.amnV1NextRevealAtMs) {
        	s.amnV1NextRevealAtMs = now + 300_000L; // prochaine fenêtre

            java.util.List<java.util.UUID> candidates = new java.util.ArrayList<>();
            for (RoleService.RoleState ws : core.getStates().values()) {
                if (!core.isWolf(ws)) continue;
                if (ws.owner.equals(s.owner)) continue; // pas lui-même
                // On ne révèle que des loups qu’il ne connaît pas déjà
                if (!s.amnV1KnownWolves.contains(ws.owner)) candidates.add(ws.owner);
            }
            if (candidates.isEmpty()) continue;

            java.util.UUID chosen = candidates.get(r.nextInt(candidates.size()));
            s.amnV1KnownWolves.add(chosen);

            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
            org.bukkit.entity.Player c = org.bukkit.Bukkit.getPlayer(chosen);
            if (p != null && p.isOnline()) {
                String name = (c != null ? c.getName() : "un Loup");
                p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Amnésique v1] "
                    + org.bukkit.ChatColor.GRAY + "Tu découvres un Loup : "
                    + org.bukkit.ChatColor.GOLD + name);
            }
        }
    }
}


@org.bukkit.event.EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
public void onAmnV1DamagedByWolf(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
    // Victime doit être un joueur
    if (!(e.getEntity() instanceof org.bukkit.entity.Player)) return;
    org.bukkit.entity.Player victim = (org.bukkit.entity.Player) e.getEntity();

    // Récupérer le joueur qui a frappé (corps à corps ou projectiles)
    org.bukkit.entity.Player damagerP = null;
    if (e.getDamager() instanceof org.bukkit.entity.Player) {
        damagerP = (org.bukkit.entity.Player) e.getDamager();
    } else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
        Object sh = ((org.bukkit.entity.Projectile) e.getDamager()).getShooter();
        if (sh instanceof org.bukkit.entity.Player) damagerP = (org.bukkit.entity.Player) sh;
    }
    if (damagerP == null) return;

    // États rôles
    RoleService.RoleState vs = core.get(victim);
    RoleService.RoleState ds = core.get(damagerP);
    if (vs == null || ds == null) return;

    // Doit être frappé par un vrai loup (loups natifs ou infectés)
    if (!core.isWolf(ds)) return;

    // Doit être un Loup-Garou Amnésique v1 pas encore déclenché
    if (vs.roleId != RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) return;
    if (vs.amnV1Awake) return;            // déjà réveillé
    if (vs.amnV1TriggerAtMs != 0L) return; // déjà déclenché (on ne programme qu'une fois)

    // --- T0 : on démarre le compte à rebours de 3 minutes ---
    vs.amnV1TriggerAtMs = System.currentTimeMillis();

    // --- +3 minutes : RÉVEIL effectif ---
    org.bukkit.Bukkit.getScheduler().runTaskLater(core.getPlugin(), () -> {
        RoleService.RoleState s = core.get(victim);
        if (s == null) return;
        if (s.amnV1Awake) return; // déjà traité par ailleurs

        // Il “devient” loup maintenant
        s.amnV1Awake = true;
        s.amnV1AwakenAtMs = System.currentTimeMillis();
        s.amnVisibleToWolves = false; // il n’apparaît pas encore chez les autres (sera géré par tes timers)
        s.amnV1KnownWolves.clear();

        // ✅ Aura neutre après réveil ; constellation inchangée (MOUTON)
        try { s.aura = RoleService.Aura.NEUTRE; } catch (Throwable ignored) {}
        try { s.constellation = RoleService.Constellation.MOUTON; } catch (Throwable ignored) {}

        // Cadence des révélations toutes les 5 min :
        // - si la liste est déjà annoncée (EP3 passé), on arme tout de suite
        // - sinon on attend l’annonce (elle armera le timer)
        if (isWolfListAnnounced()) {
            s.amnV1NextRevealAtMs = System.currentTimeMillis() + 5L * 60_000L;
        } else {
            s.amnV1NextRevealAtMs = 0L;
        }

        // Messages
        victim.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Amnésique] "
            + org.bukkit.ChatColor.GRAY + "Tu te souviens... Tu es un Loup-Garou !");
        victim.sendMessage(org.bukkit.ChatColor.GRAY + "Ta liste d’alliés apparaîtra petit à petit (un nom toutes les 5 minutes).");

        // Prévenir la meute qu’un nouvel allié s’est éveillé
        for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
            RoleService.RoleState ws = core.get(w);
            if (ws != null && core.isWolf(ws) && !w.getUniqueId().equals(victim.getUniqueId())) {
                w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
                    + org.bukkit.ChatColor.GRAY + "Un nouvel allié s’est éveillé dans votre camp.");
            }
        }
    }, 3L * 60L * 20L); // 3 minutes en ticks 1.8
}



/** Appelé à +3 min : il devient “loup” et les autres loups le voient, sa liste démarre vide. */
private void amnV1Awaken(java.util.UUID playerId) {
    RoleService.RoleState s = core.getStates().get(playerId);
    if (s == null || s.roleId != RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) return;
    if (s.amnV1Awake) return;

    s.amnV1Awake = true;
    s.amnV1VisibleToWolves = true;               // les autres loups voient son nom (si liste dispo côté meute)
    s.amnV1KnownWolves.clear();                  // sa liste commence vide
    s.amnV1NextRevealAtMs = 0L; // on démarrera la cadence à l’annonce de la liste

    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerId);
    if (p != null && p.isOnline()) {
        p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Amnésique] "
            + org.bukkit.ChatColor.GRAY + "Tu te souviens... Tu es un Loup-Garou !");
        p.sendMessage(org.bukkit.ChatColor.GRAY + "Ta liste d’alliés apparaîtra petit à petit (un nom toutes les 5 minutes).");
    }

    // prévenir la meute qu’un nouveau loup s’éveille (message générique)
    try {
        for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
            RoleService.RoleState ws = core.get(w);
            if (ws != null && core.isWolf(ws) && !w.getUniqueId().equals(playerId)) {
                w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
                    + org.bukkit.ChatColor.GRAY + "Un nouveau loup s’éveille dans votre camp.");
            }
        }
    } catch (Throwable ignored) {}
}


/** EP3 : avertit les loups que la liste est désormais visible via /lg role */
public void notifyWolvesListNowAvailable() {
    for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
        RoleService.RoleState s = core.get(w);
        if (s != null && core.isWolf(s)) {
            w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
                + org.bukkit.ChatColor.GRAY + "La liste des loups est désormais disponible."
                + " Utilise " + org.bukkit.ChatColor.WHITE + "/lg role" + org.bukkit.ChatColor.GRAY + " pour la voir.");
        }
    }

    // 👉 Armer le minuteur de “révélations toutes les 5 min” pour les v1 déjà réveillés,
    //    mais seulement maintenant que la liste est officiellement annoncée.
    long now = System.currentTimeMillis();
    for (RoleService.RoleState s : core.getStates().values()) {
        if (s.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1 && s.amnV1Awake) {
            if (s.amnV1NextRevealAtMs <= 0L) {
                s.amnV1NextRevealAtMs = now + 5L * 60_000L; // 5 min
            }
        }
    }
}

//Envoie une offre cliquable à l’Infect quand un loup tue quelqu’un
//Programme l’ouverture de l’offre à +5s après la mort

//Envoie l’offre cliquable à l’Infect, valide pendant windowMs (ici 5s)



private boolean cmdInfect(org.bukkit.entity.Player sender) {
    RoleService.RoleState s = core.get(sender);
    if (s == null || s.roleId != RoleService.RoleId.INFECT_PERE_DES_LOUPS) return false;

    if (s.infectUsed) { sender.sendMessage("§4[Infect] §7Pouvoir déjà utilisé."); return true; }
    if (s.infectPendingVictim == null) { sender.sendMessage("§4[Infect] §7Aucune cible en attente."); return true; }
    if (System.currentTimeMillis() > s.infectOfferExpireAtMs) {
        s.infectPendingVictim = null;
        sender.sendMessage("§4[Infect] §7Offre expirée.");
        return true;
    }

    java.util.UUID vid = s.infectPendingVictim;
    s.infectPendingVictim = null;
    s.infectUsed = true;

    RoleService.RoleState vs = core.getStates().get(vid);
    if (vs == null) { sender.sendMessage("§4[Infect] §7Cible introuvable."); return true; }

    // Convertit : dorénavant comptera comme loup partout
    vs.infectedAsWolf = true;

    // Réanimation “safe” si la mort vient d’avoir lieu
    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(vid);
    if (p != null) {
        try { p.spigot().respawn(); } catch (Throwable ignored) {}
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setHealth(Math.max(1.0D, Math.min(p.getMaxHealth(), 20.0D)));
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setNoDamageTicks(60);
    }

    // Messages
    if (p != null) p.sendMessage("§4[Infection] §7Tu as été §cconverti§7 : tu rejoins §cles loups§7.");
    sender.sendMessage("§4[Infect] §aConversion effectuée.");

    for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
        RoleService.RoleState ws = core.get(w);
        if (ws != null && core.isWolf(ws)) {
            w.sendMessage("§4[Loups] §7Un nouveau loup a été §cinfecté§7.");
        }
    }

    // Si la liste est annoncée, envoie-lui la meute actuelle
    if (isWolfListAnnounced() && p != null) sendWolfListTo(p);

    return true;
}

private boolean isHowlExcludedNow(RoleService.RoleState s) {
    if (s == null) return true;
    // Bloquer le Peureux si tu le souhaites
    if (s.roleId == RoleService.RoleId.LOUP_GAROU_PEUREUX) return true;

    // Amnésique "classique" : hurle seulement s’il est infecté
    try {
        if (s.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE) {
            return !s.infectedAsWolf;
        }
    } catch (Throwable ignored) {}

    // Amnésique v1 : hurle s’il est réveillé OU infecté
    if (s.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1) {
        return !(s.amnV1Awake || s.infectedAsWolf);
    }

    // Autres loups : OK
    return false;
}




//Envoie le teaser 60s avant la nuit, et fige s.luckyTonight pour la nuit à venir
private void tickLuckyPreviewOneMinuteBeforeNight() {
 org.bukkit.World w = org.bukkit.Bukkit.getWorlds().get(0);
 if (w == null) return;

 long dayTime = w.getTime() % 24000L;
 // La nuit vanilla commence ~13000. 60s = 1200 ticks -> fenêtre [11800 ; 13000[
 boolean inPreviewWindow = (dayTime >= 11800L && dayTime < 13000L);

 if (!inPreviewWindow) return; // on ne tease que dans cette fenêtre

 java.util.Random r = rnd; // tu l’as déjà en champ
 for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
     if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_CHANCEUX) continue;

     if (luckyPreviewSent.contains(s.owner)) continue; // déjà prévenu ce jour
     org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
     if (p == null || !p.isOnline() || p.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

     // Si pas encore figé pour cette nuit : on tire maintenant et on CONSERVERA ce choix
     if (s.luckyTonight == null) {
         fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff[] pool =
             new fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff[] {
                 fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.RESIST,
                 fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.REGEN,
                 fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.STRENGTH,
                 fr.sfakeur.lguhc.RoleService.RoleState.LuckyBuff.SPEED
             };
         s.luckyTonight = pool[r.nextInt(pool.length)];
     }

     p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loup Chanceux] "
         + org.bukkit.ChatColor.GRAY + "Dans une minute (à la nuit), tu recevras : "
         + org.bukkit.ChatColor.GOLD + niceBuffName(s.luckyTonight));

     luckyPreviewSent.add(s.owner);
 }
}

//Durée totale "jour+night" en ms (5/10/20 min selon host)
//Essaie d'abord le GameManager, sinon fallback config, sinon 10 min.
//WolfHandler.java
private long cycleTotalMs() {
 // Clé config proposée : dayNight.cycleMinutes = durée (en minutes) du cycle JOUR+NUIT
 int cycleMin = 10; // défaut
 try {
     cycleMin = core.getPlugin().getConfig().getInt("dayNight.cycleMinutes", 10);
 } catch (Throwable ignored) {}
 return cycleMin * 60_000L;
}

//Initialise le déclencheur la première fois que l’option est ON
private void ensureSoloInit() {
 if (soloInitDone) return;
 if (!core.isSolitaireEnabled()) return; // pas encore activé -> on attend

 soloInitDone = true;

 if (core.isSolitaireTestMode()) {
     // MODE TEST : 60..120 secondes
     soloTriggerAtSec = 5400 + rnd.nextInt(1800); // [60..120]
     soloTriggerAliveThreshold = -1;
     try { core.getPlugin().getLogger().info("[Solitaire] Test: déclenche à " + soloTriggerAtSec + " s"); } catch (Throwable ignored) {}
 } else {
     // MODE RÉEL : quand vivants <= 65%..90% des joueurs initiaux
     int initialPlayers = Math.max(1, core.getStates().size());
     double perc = 0.65 + rnd.nextDouble() * 0.25; // 65%..90%
     soloTriggerAliveThreshold = Math.max(1, (int)Math.ceil(initialPlayers * perc));
     soloTriggerAtSec = -1;
     try { core.getPlugin().getLogger().info("[Solitaire] Réel: seuil vivants <= " + soloTriggerAliveThreshold); } catch (Throwable ignored) {}
 }
}

//Compte les vivants via DeathManager (ou fallback)
private int countAlivePlayers() {
 int c = 0;
 for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
     if (isAliveUUID(s.owner)) c++;
 }
 return c;
}

//Déclenche → choisit un loup et le bascule en Solitaire (+4 coeurs)
private void tryTriggerSolitaire() {
 if (soloTriggered) return;
 if (!core.isSolitaireEnabled()) return;

 java.util.List<RoleService.RoleState> candidates = new java.util.ArrayList<>();
 for (RoleService.RoleState s : core.getStates().values()) {
     if (!core.isWolf(s)) continue;                 // loup effectif
     if (s.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) continue; // v1 non réveillé -> non
     if (s.solitaire) continue;                     // déjà solo
     if (!isAliveUUID(s.owner)) continue;           // doit être vivant
     candidates.add(s);
 }
 if (candidates.isEmpty()) return;

 RoleService.RoleState pick = candidates.get(rnd.nextInt(candidates.size()));
 pick.solitaire = true;

 org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(pick.owner);
 if (p != null && p.isOnline()) {
     // +4 slots de coeurs (8 HP) — 1.8: setMaxHealth existe
     if (!pick.solitaireHeartsGiven) {
         try {
             double nw = Math.min(40.0D, p.getMaxHealth() + 8.0D);
             p.setMaxHealth(nw);
             p.setHealth(nw);
             pick.solitaireHeartsGiven = true;
         } catch (Throwable ignored) {}
     }
     p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Solitaire] "
         + org.bukkit.ChatColor.GRAY + "Tu trahis la meute. Tu dois désormais gagner "
         + org.bukkit.ChatColor.GOLD + "seul");
     try { p.playSound(p.getLocation(), org.bukkit.Sound.WITHER_SPAWN, 1f, 1f); } catch (Throwable ignored) {}
 }

 // Broadcast soft (ou seulement à lui si tu préfères)
 org.bukkit.Bukkit.broadcastMessage(
     org.bukkit.ChatColor.DARK_RED + "[Solitaire] " + org.bukkit.ChatColor.GRAY
     + "Un loup a décidé de jouer en solo...");

 soloTriggered = true;
}

private void assignFeutreFacadeForEpisode(int episodeNumber) {
    // pool = rôles non-loups encore présents
    java.util.Set<RoleService.RoleId> poolSet = new java.util.HashSet<>();
    for (RoleService.RoleState st : core.getStates().values()) {
        org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(st.owner);
        if (pl == null || !pl.isOnline() || pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
        if (!core.isWolf(st)) poolSet.add(st.roleId);
    }
    if (poolSet.isEmpty()) return;

    java.util.List<RoleService.RoleId> pool = new java.util.ArrayList<>(poolSet);
    java.util.Random r = new java.util.Random();

    for (RoleService.RoleState s : core.getStates().values()) {
        if (s.roleId != RoleService.RoleId.LOUP_GAROU_FEUTRE) continue;
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
        if (p == null || !p.isOnline() || p.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

        // 1 par épisode au milieu
        if (s.feutreEpisode == episodeNumber && s.feutreActive) continue;

        RoleService.RoleId fake = pool.get(r.nextInt(pool.size()));
        s.feutreShownRole = fake;
        s.feutreEpisode   = episodeNumber;
        s.feutreActive    = true;

        p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Feutré] "
            + org.bukkit.ChatColor.GRAY + "Ton rôle d’affichage pour cet épisode est : "
            + org.bukkit.ChatColor.WHITE + core.displayName(fake) + org.bukkit.ChatColor.GRAY + ".");
    }
}

@org.bukkit.event.EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
public void onDarkBlindHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof org.bukkit.entity.Player)) return;

    org.bukkit.entity.Player victim = (org.bukkit.entity.Player) e.getEntity();
    org.bukkit.entity.Player damagerP = null;

    if (e.getDamager() instanceof org.bukkit.entity.Player) {
        damagerP = (org.bukkit.entity.Player) e.getDamager();
    } else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
        Object sh = ((org.bukkit.entity.Projectile) e.getDamager()).getShooter();
        if (sh instanceof org.bukkit.entity.Player) damagerP = (org.bukkit.entity.Player) sh;
    }
    if (damagerP == null) return;

    RoleService.RoleState ds = core.get(damagerP);
    if (ds == null || !core.isWolf(ds)) return; // doit être un loup (n’importe lequel)

    // La cible doit être aveuglée
    if (!victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)) return;

    try { victim.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS); } catch (Throwable ignored) {}
}















































	    
	    ///private boolean isHowlExcluded(fr.sfakeur.lguhc.RoleService.RoleId rid) {
	        //return rid == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_PEUREUX
	               /* || rid == RoleId.LOUP_GAROU_AMNESIQUE
	               || rid == RoleId.LOUP_GAROU_AMNESIQUE_V1 */; // si/quan tu les crées
	    //}
	               
	              







	    
	    

}
