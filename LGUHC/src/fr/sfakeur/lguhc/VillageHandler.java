package fr.sfakeur.lguhc;


import fr.sfakeur.lguhc.RoleService;


import fr.sfakeur.lguhc.RoleService.Fruit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

//En haut du fichier, garde SEULEMENT celui-ci pour la partie "UI cliquable"
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;

//PAS d'import de ChatColor Bukkit ici, ou alors on l'utilise en fully-qualified (org.bukkit.ChatColor)





import java.util.*;

import org.bukkit.plugin.java.JavaPlugin;

public class VillageHandler implements AlignHandler, org.bukkit.event.Listener {
	
	private final RoleService core;
	
	// tracking des ventes en cours: vendeur -> cible
	private final java.util.Map<java.util.UUID, java.util.UUID> fruitSaleTargetBySeller = new java.util.HashMap<>();

	// titre unique du GUI
	private static final String FRUIT_GUI_TITLE = ChatColor.GOLD + "Vente de fruits";
	
	private static final int ERMITE_RANGE = 20; // blocs
	
	private boolean lastNight = false;

	private static final int TICK_PERIOD = 20;  // on calcule toutes les 20 ticks (1s)
	
	private static final int HUGE = 9_999_999; // ~8.3 jours en ticks; pas de flicker
	
	// Sœur : affichage du pseudo retardé
	public boolean soeurNameRevealed = false;

	// Jumeau : affichage du pseudo retardé
	public boolean twinNameRevealed = false;
	
	// VillageHandler.java
	private boolean siblingsAnnouncementScheduled = false;
	private boolean siblingsAnnounced = false;
	
	// --- Enchanteresse (suivi temp 20 min + revert) ---
	private static final long ENCH_COOLDOWN_MS     = 20L * 60L * 1000L; // 20 min
	private static final long ENCH_DURATION_TICKS  = 20L * 60L * 20L;   // 20 min en ticks
	private final java.util.Random enchRnd = new java.util.Random();
	
	// snapshots par CIBLE (pour revert)
	private static final class EnchantSnapshot {
	    int slot; // -1 = main hand (1.8: à adapter si besoin), 36..39 pour armure
	    org.bukkit.enchantments.Enchantment ench;
	    int prev;
	    int now;
	    EnchantSnapshot(int slot, org.bukkit.enchantments.Enchantment e, int prev, int now) {
	        this.slot = slot; this.ench = e; this.prev = prev; this.now = now;
	    }
	}

	// on limite à 1 boost actif par cible pour rester simple
	private final java.util.Map<java.util.UUID, java.util.List<EnchantSnapshot>> enchSnapshots = new java.util.HashMap<>();
	private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask>  enchReverts   = new java.util.HashMap<>();
	
	// --- Helpers Servante (à coller dans VillageHandler) ---

	/** Vrai si s peut utiliser les commandes du rôle 'target' (soit il EST ce rôle, soit Servante l'a approprié). */
	private boolean canUseAs(fr.sfakeur.lguhc.RoleService.RoleState s,
	                         fr.sfakeur.lguhc.RoleService.RoleId target) {
	    if (s == null || target == null) return false;
	    if (s.roleId == target) return true;
	    return (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.SERVANTE_DEVOUEE
	         && s.servanteUsed
	         && s.servanteStolenFrom == target);
	}

	/** Rôle « effectif » pour le routage de commandes (/lg ...). */
	private fr.sfakeur.lguhc.RoleService.RoleId effectiveCommandRole(fr.sfakeur.lguhc.RoleService.RoleState s) {
	    if (s == null) return null;
	    if (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.SERVANTE_DEVOUEE
	        && s.servanteUsed
	        && s.servanteStolenFrom != null) {
	        return s.servanteStolenFrom; // proxy vers le rôle volé
	    }
	    return s.roleId;
	}



	

	
	




	    public VillageHandler(RoleService core) {
	        this.core = core;
	        // s’enregistrer pour les clics d’inventaire
	        org.bukkit.Bukkit.getPluginManager().registerEvents(this, core.getPlugin());
	    }

	    // ====== Util ======
	    private RoleService.RoleState st(Player p) { return core.get(p); }
	    private boolean isVillage(RoleService.RoleId id) {
	        if (id == null) return false;
	        switch (id) {
	            case ANALYSTE: case CONSTELLATIONNISTE: case CONTEUSE:
	            case DETECTIVE: case JUMEAU: case MONTREUR_DOURS:
	            case ORACLE: case PRETRESSE: case RENARD:
	            case VOYANTE: case SIMPLE_VILLAGEOIS: case BIBLIOTHECAIRE: 
	            case VIEUX_SAGE: case MARCHANDE_DE_FRUITS: case MINEUR:

	                return true;
	            default: return false;
	        }
	    }

	    // ====== Ticks / Événements ======
	    @Override
	    public void tickPerSecond(int elapsedSec) {
	        // Jumeaux: ligne de particules >= 45 min
	        final int TWIN_LINE_START_SEC = 45 * 60;
	        if (elapsedSec >= TWIN_LINE_START_SEC) {
	            for (RoleService.RoleState s : core.getStates().values()) {
	                if (s.roleId != RoleService.RoleId.JUMEAU) continue;
	                
	            }
	        }
	        // Jumeaux: Fraternité à chaque demi-épisode, à partir du milieu de l’épisode 3
	        tryTriggerFraternite(elapsedSec);
	        biblioTickRecall(elapsedSec);
	        
	        applySoeurProximityResist();
	       

	        
	     // 👩‍🦳 Vieux Sage : pour chaque seconde, +1 par joueur <=15 blocs, classé par aura
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.VIEUX_SAGE) continue;
	            Player sage = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (sage == null || !sage.isOnline()) continue;

	            for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
	                if (other.equals(sage)) continue;
	                if (!other.getWorld().equals(sage.getWorld())) continue;
	                if (other.getLocation().distance(sage.getLocation()) > 15.0) continue;

	                RoleService.RoleState os = core.get(other);
	                if (os == null) continue;
	                switch (os.aura) {
	                    case LUMINEUSE: s.sageLumCount++; break;
	                    case NEUTRE:    s.sageNeuCount++; break;
	                    case OBSCURE:   s.sageObsCount++; break;
	                    default: break;
	                }
	            }
	        }
	        
	     // Ancien : maintenir Résistance I tant que non perdue
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId == RoleService.RoleId.ANCIEN && s.ancienResistanceActive) {
	                Player pl = Bukkit.getPlayer(s.owner);
	                if (pl != null && pl.isOnline()) {
	                    // si le joueur a perdu l’effet (lait, mort, etc.), on le remet
	                    if (!pl.hasPotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE)) {
	                        pl.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                            org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, true, true
	                        ));
	                    }
	                }
	            }
	        }
	        
	     // Rafraîchit seulement chaque seconde
	        if (elapsedSec % 1 != 0) return;

	        boolean isDay = core.getGame().isCurrentlyDay();

	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.ERMITE) continue;

	            Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline()) continue;

	            // Compte des joueurs dans un rayon de 20 blocs (l’Ermite COMPRIS)
	            int nearby = 1; // on se compte soi-même
	            for (Player other : p.getWorld().getPlayers()) {
	                if (other == p) continue;
	                if (!other.isOnline()) continue;
	                if (!other.getWorld().equals(p.getWorld())) continue;
	                if (other.getLocation().distanceSquared(p.getLocation()) <= (ERMITE_RANGE * ERMITE_RANGE)) {
	                    nearby++;
	                }
	            }

	            // Profil:
	            // 1 joueur (lui seul) => Speed I
	            // 2..3 joueurs => Résistance I la nuit, Force I le jour
	            // 4+ joueurs => Faiblesse I
	            int profile;
	            if (nearby == 1) profile = 0;          // seul
	            else if (nearby <= 3) profile = 1;     // ≤3
	            else profile = 2;                      // ≥4

	            if (profile == s.ermiteLastProfile && s.ermiteLastProfile != -1) {
	                // même profil: on rafraîchit juste les effets pour éviter qu'ils expirent
	            } else {
	                // profil a changé → nettoie tous les effets du rôle
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
	                p.removePotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS);
	                s.ermiteLastProfile = profile;
	            }

	            // Applique l’effet selon profil
	            // durée courte (3s) et ré-appliquée chaque seconde pour rester fluide
	            int dur = 60; // 3s = 60 ticks
	            switch (profile) {
	                case 0: // seul
	                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                            org.bukkit.potion.PotionEffectType.SPEED, dur, 0, true, true), true);
	                    break;
	                case 1: // ≤3
	                    if (isDay) {
	                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                                org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, dur, 0, true, true), true);
	                    } else {
	                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                                org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, dur, 0, true, true), true);
	                    }
	                    break;
	                case 2: // ≥4
	                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                            org.bukkit.potion.PotionEffectType.WEAKNESS, dur, 0, true, true), true);
	                    break;
	            }
	        }
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.MINEUR) continue;
	            org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (pl == null || !pl.isOnline()) continue;

	            if (!pl.hasPotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING)) {
	                pl.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.FAST_DIGGING, Integer.MAX_VALUE, 1, true, true
	                ));
	            }
	        }
	        
	        boolean isNight = core.getGame().isCurrentlyNight(); 

	     // Début de nuit → Petite Fille détecte joueurs ≤100 blocs
	     if (isNight && !lastNight) {
	         for (RoleService.RoleState s : core.getStates().values()) {
	             if (s.roleId != RoleService.RoleId.PETITE_FILLE) continue;

	             org.bukkit.entity.Player pf = org.bukkit.Bukkit.getPlayer(s.owner);
	             if (pf == null || !pf.isOnline() || pf.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

	             java.util.List<String> names = new java.util.ArrayList<>();
	             org.bukkit.Location loc = pf.getLocation();
	             for (org.bukkit.entity.Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
	                 if (other.equals(pf)) continue;
	                 if (!other.isOnline() || other.getWorld() != pf.getWorld()) continue;
	                 if (other.getLocation().distanceSquared(loc) <= 100 * 100) {
	                     names.add(other.getName());
	                 }
	             }

	             if (names.isEmpty()) {
	                 pf.sendMessage("§d[Petite Fille] §7Début de nuit : personne à moins de 100 blocs.");
	             } else {
	                 pf.sendMessage("§d[Petite Fille] §fÀ moins de 100 blocs : §a" + String.join(", ", names));
	             }
	         }
	     }

	     lastNight = isNight;
	     
	  // === Mineur (Village) : Haste II sous Y<50, sinon Haste I — particules masquées ===
	     for (RoleService.RoleState s : core.getStates().values()) {
	         if (s.roleId != RoleService.RoleId.MINEUR) continue;

	         org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	         if (p == null || !p.isOnline() || p.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

	         int y = p.getLocation().getBlockY();
	         int desiredAmp = (y < 50 ? 1 : 0); // 1 = Haste II, 0 = Haste I

	         org.bukkit.potion.PotionEffect current = null;
	         for (org.bukkit.potion.PotionEffect fx : p.getActivePotionEffects()) {
	             if (fx.getType() == org.bukkit.potion.PotionEffectType.FAST_DIGGING) { current = fx; break; }
	         }

	         boolean needApply = (current == null || current.getAmplifier() != desiredAmp);
	         if (needApply) {
	             try { p.removePotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING); } catch (Throwable ignored) {}
	             // durée très longue ; on ne ré-applique que quand le palier change
	             p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                 org.bukkit.potion.PotionEffectType.FAST_DIGGING,
	                 9_999_999,
	                 desiredAmp,
	                 true,   // ambient
	                 false   // particles MASQUÉES pour les effets de rôle
	             ));
	         }
	     }
	     
	  // VillageHandler.java (dans tickPerSecond) pour Bienfaiteur
	     long now = System.currentTimeMillis();
	     for (RoleService.RoleState st : core.getStates().values()) {
	         if (st.roleId != RoleService.RoleId.BIENFAITEUR) continue;
	         if (!st.bienfRegenActive) continue;

	         org.bukkit.entity.Player bp = org.bukkit.Bukkit.getPlayer(st.owner);
	         if (bp == null || !bp.isOnline() || bp.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

	         if (now >= st.bienfNextRegenMs) {
	             st.bienfNextRegenMs = now + 30_000L; // prochaine tick dans 30s
	             try {
	                 double heal = Math.min(bp.getMaxHealth(), bp.getHealth() + 2.0D); // +1♥ = +2 HP
	                 bp.setHealth(heal);
	                 // (pas d’effets/potions -> pas de particules)
	             } catch (Throwable ignored) {}
	         }
	     }



	    
	    }

	    @Override
	    public void onPlayerKill(Player killer, Player victim) {
	        // Renard: vitesse 1 min (déjà fait côté étape 1, on garde)
	        RoleService.RoleState s = st(killer);
	        if (s != null && s.roleId == RoleService.RoleId.RENARD) {
	            int dur = 60 * 20;
	            killer.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.SPEED, dur, 0, true, true));
	            killer.sendMessage(ChatColor.GOLD + "[Renard] " + ChatColor.GRAY + "Bonus après kill: "
	                    + ChatColor.GOLD + "Vitesse I (60s).");
	        }
	    }

		    public void onPlayerDeath(Player dead) {
		        if (dead == null) return;
		        RoleService.RoleState sd = core.get(dead);
		        if (sd == null) return;
		        
		        UUID victimId = dead.getUniqueId();
		        
		        if (dead == null) return;
		        if (sd.roleId == RoleService.RoleId.ANCIEN) {
		            // il perd sa résistance à TOUTE mort
		            sd.ancienResistanceActive = false;
		            sd.ancienLostResistance = true; // si tu veux conserver ce flag
		            dead.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);

		            // résurrection silencieuse uniquement si tué par un loup et pas encore utilisée
		            Player killer = dead.getKiller();
		            boolean killedByWolf = (killer != null && core.isWolf(killer));

		            if (killedByWolf && !sd.ancienResUsed) {
		                sd.ancienResUsed = true;


		                // Respawn et remise d’aplomb 1 tick après
		                new org.bukkit.scheduler.BukkitRunnable() {
		                    @Override public void run() {
		                        try { dead.spigot().respawn(); } catch (Throwable ignored) {}
		                        dead.setGameMode(org.bukkit.GameMode.SURVIVAL);

		                        // Soins raisonnables
		                        double heal = Math.min(dead.getMaxHealth(), 20.0);
		                        if (heal <= 0.0D) heal = 10.0D;
		                        dead.setHealth(heal);
		                        dead.setFoodLevel(20);
		                        dead.setFireTicks(0);
		                        dead.setNoDamageTicks(60);

		                        // TP safe (ta méthode maison ; remplace si besoin)
		                        org.bukkit.Location safe = core.getGame().randomSafeScatter();
		                        if (safe != null) dead.teleport(safe);

		                        dead.sendMessage(org.bukkit.ChatColor.GOLD + "[Ancien] "
		                                + org.bukkit.ChatColor.GREEN + "Tu as été ressuscité (tué par un Loup). "
		                                + org.bukkit.ChatColor.GRAY + "Tu as définitivement "
		                                + org.bukkit.ChatColor.RED + "perdu ta Résistance.");
		                    }
		                }.runTask(core.getPlugin());
		                // NB: on ne relance PAS la résistance (elle est définitivement perdue)
		            }
		        }


		     // Prêtresse: si un loup (infectés inclus) précédemment espionné meurt → +1 cœur permanent
		        if (sd != null && core.isWolf(sd)) {  // 👈 au lieu de sd.align == LOUP
		            for (RoleService.RoleState s : core.getStates().values()) {
		                if (s.roleId != RoleService.RoleId.PRETRESSE) continue;
		                if (!s.pretresseSeenWolves.contains(dead.getUniqueId())) continue;

		                Player pretresse = Bukkit.getPlayer(s.owner);
		                if (pretresse != null && pretresse.isOnline()) {
		                    if (core.changeMaxHearts(pretresse, +1)) {
		                        pretresse.sendMessage(ChatColor.GREEN + "[Prêtresse] "
		                                + "La mort du loup observé te rend " + ChatColor.GOLD + "1 ♥"
		                                + ChatColor.GREEN + " permanent.");
		                    }
		                }
		                s.pretresseSeenWolves.remove(dead.getUniqueId());
		            }
		        }



	        // Jumeau: buff pour le survivant
	        if (sd.roleId == RoleService.RoleId.JUMEAU && sd.twinPartner != null) {
	            Player twin = Bukkit.getPlayer(sd.twinPartner);
	            if (twin != null && twin.isOnline()) {
	                int dur = 5 * 60 * 20;
	                twin.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                        org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, dur, 0, true, true));
	                twin.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                        org.bukkit.potion.PotionEffectType.SPEED, dur, 0, true, true));
	                twin.sendMessage(ChatColor.GOLD + "Ton jumeau est mort... Tu obtiens Force I et Vitesse I pendant 5 minutes.");
	            }
	        }

	        // Prêtresse: si un loup précédemment espionné meurt → +1 cœur permanent
	        if (sd.align == RoleService.Align.LOUP) {
	            for (RoleService.RoleState s : core.getStates().values()) {
	                if (s.roleId != RoleService.RoleId.PRETRESSE) continue;
	                if (!s.pretresseSeenWolves.contains(dead.getUniqueId())) continue;
	                Player pretresse = Bukkit.getPlayer(s.owner);
	                if (pretresse != null && pretresse.isOnline()) {
	                    if (core.changeMaxHearts(pretresse, +1)) {
	                        pretresse.sendMessage(ChatColor.GREEN + "[Prêtresse] "
	                                + "La mort du loup observé te rend " + ChatColor.GOLD + "1 ♥" + ChatColor.GREEN + " permanent.");
	                    }
	                }
	                s.pretresseSeenWolves.remove(dead.getUniqueId());
	            }
	        }

	        if (sd != null && sd.roleId == RoleService.RoleId.BIBLIOTHECAIRE) {
	            // Choisir un villageois (autre que le mort), idéalement en ligne
	            java.util.List<Player> candidates = new java.util.ArrayList<>();
	            for (RoleService.RoleState s : core.getStates().values()) {
	                if (s.align == RoleService.Align.VILLAGE && !s.owner.equals(dead.getUniqueId())) {
	                    Player pl = org.bukkit.Bukkit.getPlayer(s.owner);
	                    if (pl != null && pl.isOnline()) candidates.add(pl);
	                }
	            }
	            if (!candidates.isEmpty()) {
	                java.util.Collections.shuffle(candidates);
	                Player heir = candidates.get(0);

	                // récupérer le livre depuis l'inventaire du mort (ou depuis l’emprunteur si prêt)
	                org.bukkit.inventory.ItemStack memo = takeMemoBookFrom(dead);
	                if (memo == null && sd.biblioBorrower != null) {
	                    memo = takeMemoBookFrom(org.bukkit.Bukkit.getPlayer(sd.biblioBorrower));
	                    sd.biblioBorrower = null;
	                    sd.biblioLoanEpisode = -1;
	                }
	                if (memo != null) {
	                    heir.getInventory().addItem(memo);
	                    heir.updateInventory();
	                    org.bukkit.Bukkit.broadcastMessage(ChatColor.GOLD + "[Bibliothécaire] "
	                            + ChatColor.GRAY + "Dans un dernier souffle, la bibliothécaire vous confie sa mémoire.");
	                    heir.sendMessage(ChatColor.GREEN + "Tu hérites de la mémoire de la Bibliothécaire.");
	                }
	            }
	        }
	    }

	    @Override
	    public void onEpisodeStart(int episodeNumber) {
	        // Montreur d’Ours : GRRR visibles par tous + maj d’aura
	    	// Montreur d’Ours : GRRR visibles par tous + maj d’aura
	    	for (RoleService.RoleState s : core.getStates().values()) {
	    	    if (s.roleId != RoleService.RoleId.MONTREUR_DOURS) continue;
	    	    Player holder = Bukkit.getPlayer(s.owner);
	    	    if (holder == null || !holder.isOnline()) continue;

	    	    // ✅ Si le Montreur est lui-même infecté/loup → au moins 1 GRRR garanti
	    	    int count = isWolf(holder) ? 1 : 0;

	    	    // Loups/infectés proches ≤ 50 blocs
	    	    for (Player other : Bukkit.getOnlinePlayers()) {
	    	        if (other.equals(holder)) continue;
	    	        if (!other.getWorld().equals(holder.getWorld())) continue;
	    	        if (!other.isOnline() || other.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
	    	        if (!isWolf(other)) continue;                            // 👈 inclut infectés
	    	        if (other.getLocation().distance(holder.getLocation()) <= 50.0) count++;
	    	    }

	    	    // Aura selon le total (infecté compris)
	    	    s.aura = (count == 0) ? RoleService.Aura.LUMINEUSE
	    	                          : (count == 1 ? RoleService.Aura.NEUTRE : RoleService.Aura.OBSCURE);

	    	    // GRRR visibles par tous
	    	    if (count > 0) {
	    	        StringBuilder sb = new StringBuilder();
	    	        for (int i = 0; i < count; i++) {
	    	            if (i > 0) sb.append(" ");
	    	            sb.append("GRRR");
	    	        }
	    	        String msg = ChatColor.YELLOW + sb.toString();
	    	        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
	    	    }
	    	}

	        if (episodeNumber >= 3) {
	            for (RoleService.RoleState s : core.getStates().values()) {
	                if (s.roleId != RoleService.RoleId.VIEUX_SAGE) continue;
	                Player sage = org.bukkit.Bukkit.getPlayer(s.owner);
	                if (sage != null && sage.isOnline()) {
	                    sage.sendMessage(ChatColor.GOLD + "[Vieux Sage] " + ChatColor.GRAY
	                            + "Compteurs du dernier épisode — "
	                            + ChatColor.GOLD + "Lumineuse: " + ChatColor.WHITE + s.sageLumCount + ChatColor.GRAY + ", "
	                            + ChatColor.YELLOW + "Neutre: "    + ChatColor.WHITE + s.sageNeuCount + ChatColor.GRAY + ", "
	                            + ChatColor.DARK_PURPLE + "Obscure: " + ChatColor.WHITE + s.sageObsCount + ChatColor.GRAY + ".");
	                }
	                // reset
	                s.sageLumCount = 0;
	                s.sageNeuCount = 0;
	                s.sageObsCount = 0;
	            }
	        }
	        
	        if (episodeNumber == 3 && !siblingsAnnouncementScheduled) {
	            siblingsAnnouncementScheduled = true;
	            // +5 minutes après le début d'EP3
	            org.bukkit.Bukkit.getScheduler().runTaskLater(core.getPlugin(), () -> {
	                if (siblingsAnnounced) return;
	                siblingsAnnounced = true;
	                try {
	                    announceSistersAndTwinsAtEp3Plus5();
	                } catch (Throwable t) {
	                    t.printStackTrace();
	                }
	            }, 5L * 60L * 20L);
	        }
	    }
	    

	    @Override
	    public boolean handleSubCommand(String sub, Player sender, String[] args) {
	        // ✅ Bouton Marchande "choisir" utilisable par tous (comme avant)
	        if ("choisir".equalsIgnoreCase(sub)) {
	            return cmdMarchandeChoisir(sender, args, st(sender));
	        }

	        RoleService.RoleState s = st(sender);
	        if (s == null) return false;

	        // === SERVANTE : /lg appropriation (clic chat) ===
	        if (s.roleId == RoleService.RoleId.SERVANTE_DEVOUEE
	            && "appropriation".equalsIgnoreCase(sub)) {

	            if (s.servanteUsed) {
	                sender.sendMessage(ChatColor.RED + "Tu as déjà utilisé l’Appropriation.");
	                return true;
	            }

	            // Récupère l’offre (fenêtre 5s, et pas la tueuse)
	            DeathManager dm = core.getPlugin().getDeathManager();
	            java.util.UUID vic = dm.consumeServantePrompt(s.owner);
	            if (vic == null) {
	                sender.sendMessage(ChatColor.RED + "Aucune appropriation disponible.");
	                return true;
	            }

	            RoleService.RoleState vs = core.getStates().get(vic);
	            if (vs == null) {
	                sender.sendMessage(ChatColor.RED + "Cible invalide.");
	                return true;
	            }

	            // Ne peut voler que sur un VILLAGE (align effectif)
	            if (core.effectiveWinAlign(vic) != RoleService.Align.VILLAGE) {
	                sender.sendMessage(ChatColor.RED + "Tu ne peux t’approprier que les pouvoirs d’un Villageois.");
	                return true;
	            }

	            // Enregistre le rôle source des pouvoirs & masque le rôle du mort
	            s.servanteUsed = true;
	            s.servanteStolenFrom = vs.roleId;
	            dm.markServanteMask(vic);

	            sender.sendMessage(ChatColor.LIGHT_PURPLE + "[Servante] "
	                + ChatColor.GRAY + "Tu t’appropries les pouvoirs de : "
	                + ChatColor.GOLD + core.displayName(vs.roleId) + ChatColor.GRAY + ".");
	            return true;
	        }
	        
	        // === SORCIÈRE : /lg resurrection & /lg malediction ===  ⬅️ NEW
	        
	        String k = (sub == null ? "" : sub.toLowerCase(java.util.Locale.ROOT));
	        
	        
	        if (k.equals("resurrection") || k.equals("ressurection") || k.equals("resurection") || k.equals("rez")) {
	            return cmdSorciereRez(sender);          // Toujours return true (gère les messages d’erreur à l’intérieur)
	        }
	        if (k.equals("malediction") || k.equals("malédiction") || k.equals("curse")) {
	            return cmdSorciereCurse(sender);        // Toujours return true (gère les messages d’erreur à l’intérieur)
	        }
	        
	        if ("witchdbg".equalsIgnoreCase(sub)) {
	            if (s == null) {sender.sendMessage("no state"); return true; }
	            sender.sendMessage("witchResUsed=" + s.witchResUsed
	                    + " witchCurseUsed=" + s.witchCurseUsed
	                    + " promptVictim=" + s.witchPromptVictimId
	                    + " promptKiller=" + s.witchPromptKillerId);
	            return true;
	        }
	        
	        //Enchanteresse choix
	        
	        if ("enchanter_apply".equalsIgnoreCase(sub)) return cmdEnchanteresseApply(sender, args, st(sender));




	        // === À partir d’ici : on route sur le rôle « effectif » (Servante -> rôle volé) ===
	        RoleService.RoleId ridForCmd = effectiveCommandRole(s);

	        switch (ridForCmd) {
	            case RENARD:
	                return cmdRenard(sub, sender, args, s);

	            case VOYANTE:
	                return cmdVoyante(sub, sender, args, s);

	            case ANALYSTE:
	                return cmdAnalyste(sub, sender, args, s);

	            case CONSTELLATIONNISTE:
	                return cmdConstellation(sub, sender, args, s);

	            case DETECTIVE:
	                return cmdDetective(sub, sender, args, s);

	            case PRETRESSE:
	                return cmdPretresse(sub, sender, args, s);

	            case ORACLE:
	                return cmdOracle(sub, sender, args, s);

	            case BIBLIOTHECAIRE:
	                return cmdBibliothecaire(sub, sender, args, s);

	            case MARCHANDE_DE_FRUITS:
	                if ("vente".equalsIgnoreCase(sub)) return cmdMarchandeVente(sender, args, s);
	                // /lg choisir géré plus haut
	                sender.sendMessage(ChatColor.YELLOW + "Sous-commandes Marchande: /lg vente <pseudo>");
	                return true;
	                
	            case BIENFAITEUR:
	            	// VillageHandler.java (dans handleSubCommand)
	            	if ("vie".equalsIgnoreCase(sub)) {
	            	    return cmdBienfaiteurVie(sender, args, core.get(sender));
	            	}
	            	
	            case ENCHANTERESSE:
	                if ("enchanter".equalsIgnoreCase(sub)) return cmdEnchanteresse(sender, args, s);
	                sender.sendMessage(ChatColor.YELLOW + "Sous-commande : /lg enchanter <pseudo>");
	                return false;



	            case PETITE_FILLE:
	                if ("detect".equalsIgnoreCase(sub)) {
	                    // ⚠️ garde Servante-friendly :
	                    if (!canUseAs(s, RoleService.RoleId.PETITE_FILLE)) {
	                        sender.sendMessage(ChatColor.RED + "Commande réservée à la Petite Fille.");
	                        return true;
	                    }
	                    if (!core.getGame().isCurrentlyNight()) {
	                        sender.sendMessage(ChatColor.RED + "Tu ne peux détecter que la nuit.");
	                        return true;
	                    }
	                    int nightIndex = core.getGame().getNightCount();
	                    if (s.pfLastDetectionNightIndex == nightIndex) {
	                        sender.sendMessage(ChatColor.RED + "Tu as déjà détecté cette nuit.");
	                        return true;
	                    }
	                    s.pfLastDetectionNightIndex = nightIndex;

	                    java.util.List<String> names = new java.util.ArrayList<>();
	                    org.bukkit.Location loc = sender.getLocation();
	                    for (org.bukkit.entity.Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
	                        if (other.equals(sender)) continue;
	                        if (!other.isOnline() || other.getWorld() != sender.getWorld()) continue;
	                        if (other.getLocation().distanceSquared(loc) <= 100 * 100) {
	                            names.add(other.getName());
	                        }
	                    }
	                    if (names.isEmpty()) sender.sendMessage("§7Aucun joueur à moins de 100 blocs.");
	                    else                 sender.sendMessage("§aÀ moins de 100 blocs: §f" + String.join(", ", names));
	                    return true;
	                }
	                return false;

	            // Jumeau, Montreur d’Ours, Conteuse : pas de /lg actifs ici
	            default:
	                return false;
	        }
	    }



	    // ====== Renard ======
	    private static final int RENARD_START_MAX_DIST = 10;
	    private static final int RENARD_STAY_MAX_DIST  = 20;
	    private static final int RENARD_STAY_SECONDS   = 180;

	    private boolean cmdRenard(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (!sub.equalsIgnoreCase("flairer")) {
	            p.sendMessage(ChatColor.YELLOW + "Sous-commande : /lg flairer <pseudo>");
	            return true;
	        }
	        if (st.renardSuccesses >= 3) { p.sendMessage(ChatColor.RED + "Tu as déjà flairé 3 joueurs."); return true; }
	        if (st.renardActiveTarget != null) { p.sendMessage(ChatColor.RED + "Tu flaires déjà un joueur."); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg flairer <pseudo>"); return true; }

	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Pas sur toi-même."); return true; }
	        RoleService.RoleState ts = st(target);
	        if (ts == null) { p.sendMessage(ChatColor.RED + "Ce joueur n’a pas de rôle attribué."); return true; }

	        if (!p.getWorld().equals(target.getWorld()) ||
	            p.getLocation().distance(target.getLocation()) > RENARD_START_MAX_DIST) {
	            p.sendMessage(ChatColor.RED + "Tu dois être à ≤ 10 blocs pour commencer.");
	            return true;
	        }

	        if (st.renardTask != null) { st.renardTask.cancel(); st.renardTask = null; }
	        st.renardActiveTarget = target.getUniqueId();
	        st.renardEndAtMs = System.currentTimeMillis() + RENARD_STAY_SECONDS * 1000L;

	        p.sendMessage(ChatColor.GOLD + "[Renard] " + ChatColor.GRAY + "Tu flaires " + ChatColor.WHITE + target.getName()
	                + ChatColor.GRAY + " : reste à " + ChatColor.GOLD + "≤ 20 blocs" + ChatColor.GRAY + " pendant "
	                + ChatColor.GOLD + "3 minutes" + ChatColor.GRAY + ".");

	        core.logRacontable(p, "renard_flairer_start");

	        st.renardTask = new org.bukkit.scheduler.BukkitRunnable() {
	            @Override public void run() {
	                Player renard = Bukkit.getPlayer(st.owner);
	                Player tgt = Bukkit.getPlayer(st.renardActiveTarget);
	                if (renard == null || !renard.isOnline() || tgt == null || !tgt.isOnline()) { fail("Flair interrompu."); return; }
	                if (!renard.getWorld().equals(tgt.getWorld()) ||
	                    renard.getLocation().distance(tgt.getLocation()) > RENARD_STAY_MAX_DIST) {
	                    fail("Tu t’es trop éloigné (> 20 blocs). Flair raté.");
	                    return;
	                }
	                if (System.currentTimeMillis() >= st.renardEndAtMs) success(renard, tgt);
	            }
	            private void fail(String reason) { Player ren = Bukkit.getPlayer(st.owner); if (ren!=null) ren.sendMessage(ChatColor.RED+"[Renard] "+reason); cleanup(); }
	            private void success(Player ren, Player tgt) {
	                st.renardSuccesses++; st.renardTargets.add(tgt.getUniqueId());
	                ren.sendMessage(ChatColor.GREEN + "[Renard] Flair réussi sur " + ChatColor.GOLD + tgt.getName()
	                        + ChatColor.GREEN + " (" + st.renardSuccesses + "/3).");
	                core.logRacontable(ren, "renard_flairer_success");
	                cleanup();
	                if (st.renardSuccesses >= 3) {
	                    int wolves = 0;
	                    for (java.util.UUID u : st.renardTargets) {
	                        RoleService.RoleState rs = core.getStates().get(u);
	                        if (core.alignOf(core.visibleRoleId(u)) == RoleService.Align.LOUP) wolves++; // ✅ Amné v1 non réveillé = false
	                    }
	                    ren.sendMessage(ChatColor.AQUA + "[Renard] " + ChatColor.WHITE
	                            + "Parmi les 3 joueurs flairés, il y a "
	                            + ChatColor.GOLD + wolves + ChatColor.WHITE + " Loup" + (wolves>1?"s":"") + ".");
	                }

	            }
	            private void cleanup() {
	                st.renardActiveTarget = null; st.renardEndAtMs = 0;
	                if (st.renardTask != null) { try { st.renardTask.cancel(); } catch (Throwable ignored) {} st.renardTask = null; }
	            }
	        };
	        st.renardTask.runTaskTimer(core.getPlugin(), 0L, 10L);
	        return true;
	    }


	 // ====== Voyante ======
	    private boolean cmdVoyante(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (!sub.equalsIgnoreCase("voir")) { 
	            p.sendMessage(ChatColor.YELLOW + "Sous-commande : /lg voir <pseudo>"); 
	            return true; 
	        }

	        // ✅ Limite: 1 utilisation par épisode
	        int ep = core.getGame().getEpisodeNumber();
	        if (st.voyanteLastUsedEpisode == ep) {
	            p.sendMessage(ChatColor.RED + "Tu as déjà utilisé /lg voir pendant l’épisode " + ep + ".");
	            return true;
	        }

	        if (args.length < 1) { 
	            p.sendMessage(ChatColor.RED + "Usage: /lg voir <pseudo>"); 
	            return true; 
	        }

	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { 
	            p.sendMessage(ChatColor.RED + "Joueur introuvable."); 
	            return true; 
	        }
	        RoleService.RoleState ts = st(target);
	        if (ts == null) { 
	            p.sendMessage(ChatColor.RED + "Ce joueur n’a pas encore de rôle attribué."); 
	            return true; 
	        }

	        // Construit la pool (comme avant)
	        java.util.List<RoleService.RoleId> pool = new java.util.ArrayList<>();
	        java.util.List<String> enabledNames = null;
	        try { enabledNames = core.getGame().getUhcConfig().getEnabledRolesFlat(); } catch (Throwable ignored) {}
	        if (enabledNames != null && !enabledNames.isEmpty()) {
	            for (String n : enabledNames) {
	                RoleService.RoleId id = core.nameToRole(n);
	                if (id != null) pool.add(id);
	            }
	        }
	        if (pool.isEmpty()) {
	            for (RoleService.RoleState s2 : core.getStates().values()) {
	                if (s2.roleId != null && !pool.contains(s2.roleId)) pool.add(s2.roleId);
	            }
	        }

	        RoleService.RoleId trueId = core.visibleRoleId(target.getUniqueId());
	        boolean enemy = (core.alignOf(trueId) != RoleService.Align.VILLAGE);
	        if (enemy) {
	            // si la cible n'est pas village → ne proposer qu’un rôle village en leurre
	            for (java.util.Iterator<RoleService.RoleId> it = pool.iterator(); it.hasNext();) {
	                RoleService.RoleId r = it.next();
	                if (core.alignOf(r) != RoleService.Align.VILLAGE) it.remove();
	            }
	            if (pool.isEmpty()) pool.add(RoleService.RoleId.SIMPLE_VILLAGEOIS);
	        }

	        // 2) rôle bonus (depuis la pool)
	        java.util.Collections.shuffle(pool);
	        RoleService.RoleId bonus = pool.get(0);
	        if (bonus == trueId && pool.size() > 1) bonus = pool.get(1);

	        // 3) Mélange l’ordre d’affichage
	        java.util.List<String> options = new java.util.ArrayList<>();
	        options.add(core.displayName(trueId));
	        options.add(core.displayName(bonus));
	        java.util.Collections.shuffle(options);

	        // 4) Message unique
	        p.sendMessage(
	            ChatColor.AQUA + "[Voyante] " + ChatColor.WHITE + "Le rôle de "
	            + ChatColor.GOLD + target.getName() + ChatColor.WHITE + " est parmi : "
	            + ChatColor.GOLD + options.get(0) + ChatColor.WHITE + ", "
	            + ChatColor.GOLD + options.get(1) + ChatColor.WHITE + "."
	        );

	        core.logRacontable(p, "voyante_voir");

	        // ✅ Marque l’utilisation pour cet épisode
	        st.voyanteLastUsedEpisode = ep;

	        return true;
	    }


	    // ====== Analyste ======
	    private static final int ANALYSTE_MIN_EPISODE = 3;
	    private static final int ANALYSTE_MAX_USES = 5;
	    private static final long ANALYSTE_COOLDOWN_SEC = 300L;

	    private boolean cmdAnalyste(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (sub.equalsIgnoreCase("observer")) return analysteObserver(p, args, st);
	        if (sub.equalsIgnoreCase("analyser")) return analysteAnalyser(p, args, st);
	        p.sendMessage(ChatColor.YELLOW + "Sous-commandes Analyste: /lg observer <pseudo>, /lg analyser <pseudo>");
	        return true;
	    }

	    private boolean analysteObserver(Player p, String[] args, RoleService.RoleState st) {
	        if (core.getGame().getEpisodeNumber() < ANALYSTE_MIN_EPISODE) {
	            p.sendMessage(ChatColor.RED + "Disponible à partir de l’épisode " + ANALYSTE_MIN_EPISODE + "."); return true;
	        }
	        if (st.analysteUsesLeft <= 0) { p.sendMessage(ChatColor.RED + "Tu as épuisé toutes tes utilisations ("+ANALYSTE_MAX_USES+")."); return true; }
	        long now = System.currentTimeMillis()/1000L;
	        if (now < st.analysteLastUseSec + ANALYSTE_COOLDOWN_SEC) {
	            p.sendMessage(ChatColor.RED + "Cooldown: " + (st.analysteLastUseSec + ANALYSTE_COOLDOWN_SEC - now) + "s."); return true;
	        }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg observer <pseudo>"); return true; }
	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Pas sur toi-même."); return true; }

	        core.logRacontable(p, "analyste_observer");

	        Set<org.bukkit.potion.PotionEffectType> tracked = new HashSet<>(Arrays.asList(
	                org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE,
	                org.bukkit.potion.PotionEffectType.SPEED,
	                org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE,
	                org.bukkit.potion.PotionEffectType.ABSORPTION,
	                org.bukkit.potion.PotionEffectType.INVISIBILITY,
	                org.bukkit.potion.PotionEffectType.WEAKNESS
	        ));
	        Set<org.bukkit.potion.PotionEffectType> found = new HashSet<>();
	        for (org.bukkit.potion.PotionEffect eff : target.getActivePotionEffects())
	            if (tracked.contains(eff.getType())) found.add(eff.getType());

	        st.analysteUsesLeft--;
	        st.analysteLastUseSec = now;
	        st.analysteObserved.add(target.getUniqueId());

	        if (!found.isEmpty()) {
	            st.analysteLastObserved.put(target.getUniqueId(), found);
	            p.sendMessage(ChatColor.AQUA + "[Analyste] " + ChatColor.WHITE + target.getName()
	                    + " possède au moins un effet " + ChatColor.YELLOW);
	        } else {
	            st.analysteLastObserved.remove(target.getUniqueId());
	            p.sendMessage(ChatColor.AQUA + "[Analyste] " + ChatColor.WHITE + target.getName()
	                    + " ne possède aucun des effets suivis " + ChatColor.YELLOW);
	        }
	        p.sendMessage(ChatColor.GRAY + "Utilisations restantes: " + ChatColor.GOLD + st.analysteUsesLeft);
	        return true;
	    }

	    private boolean analysteAnalyser(Player p, String[] args, RoleService.RoleState st) {
	        if (st.analysteAnalyseUsed) { p.sendMessage(ChatColor.RED + "Tu as déjà utilisé ton analyse unique."); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg analyser <pseudo>"); return true; }
	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (!st.analysteObserved.contains(target.getUniqueId())) { p.sendMessage(ChatColor.RED + "Tu dois d’abord /lg observer ce joueur."); return true; }

	        Set<org.bukkit.potion.PotionEffectType> tracked = new HashSet<>(Arrays.asList(
	                org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE,
	                org.bukkit.potion.PotionEffectType.SPEED,
	                org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE,
	                org.bukkit.potion.PotionEffectType.ABSORPTION,
	                org.bukkit.potion.PotionEffectType.INVISIBILITY,
	                org.bukkit.potion.PotionEffectType.WEAKNESS
	        ));
	        Set<org.bukkit.potion.PotionEffectType> now = new HashSet<>();
	        for (org.bukkit.potion.PotionEffect eff : target.getActivePotionEffects())
	            if (tracked.contains(eff.getType())) now.add(eff.getType());

	        if (now.isEmpty()) {
	            p.sendMessage(ChatColor.AQUA + "[Analyste] " + ChatColor.WHITE + target.getName()
	                    + " n'a actuellement aucun des effets suivis.");
	        } else {
	            p.sendMessage(ChatColor.AQUA + "[Analyste] " + ChatColor.WHITE + target.getName()
	                    + ChatColor.GRAY + " a: " + ChatColor.GOLD + humanizeEffects(now));
	        }
	        target.sendMessage(ChatColor.YELLOW + "Tu as été analysé par un " + ChatColor.AQUA + "Analyste" + ChatColor.YELLOW + ".");
	        if (!core.isVillage(target)) target.sendMessage(ChatColor.RED + "L’Analyste est: " + ChatColor.GOLD + p.getName());

	        st.analysteAnalyseUsed = true;
	        core.logRacontable(p, "analyste_analyser");
	        return true;
	    }

	    private String humanizeEffects(Set<org.bukkit.potion.PotionEffectType> set) {
	        List<String> names = new ArrayList<>();
	        for (org.bukkit.potion.PotionEffectType t : set) {
	            if (t.equals(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE)) names.add("Force");
	            else if (t.equals(org.bukkit.potion.PotionEffectType.SPEED)) names.add("Vitesse");
	            else if (t.equals(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE)) names.add("Résistance");
	            else if (t.equals(org.bukkit.potion.PotionEffectType.ABSORPTION)) names.add("Absorption");
	            else if (t.equals(org.bukkit.potion.PotionEffectType.INVISIBILITY)) names.add("Invisibilité");
	            else if (t.equals(org.bukkit.potion.PotionEffectType.WEAKNESS)) names.add("Faiblesse");
	        }
	        if (names.isEmpty()) return "aucun";
	        return String.join(", ", names);
	    }

	    // ====== Constellationniste ======
	    private static final int CONST_TELESCOPE_MIN_EP = 3;
	    private static final int CONST_ASTRO_MIN_EP     = 2;

	    private boolean cmdConstellation(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (sub.equalsIgnoreCase("telescope")) return cmdTelescope(p, args, st);
	        if (sub.equalsIgnoreCase("astrologie")) return cmdAstrologie(p, args, st);
	        p.sendMessage(ChatColor.YELLOW + "Sous-commandes: /lg telescope <pseudo>, /lg astrologie <pseudo>");
	        return true;
	    }

	    private boolean ensureEpisode(Player p, int minEp) {
	        int ep = core.getGame().getEpisodeNumber();
	        if (ep < minEp) { p.sendMessage(ChatColor.RED + "Disponible à partir de l’épisode " + minEp + "."); return false; }
	        return true;
	    }

	    private boolean ensureOneUsePerNight(Player p, RoleService.RoleState st) {
	        if (!core.getGame().isCurrentlyNight()) return true;
	        long thisNight = core.getGame().getCurrentNightNumber();
	        if (st.lastTelescopeNight == thisNight) {
	            p.sendMessage(ChatColor.RED + "Tu as déjà utilisé le télescope cette nuit.");
	            return false;
	        }
	        st.lastTelescopeNight = thisNight;
	        return true;
	    }

	    private boolean cmdTelescope(Player p, String[] args, RoleService.RoleState st) {
	        if (!ensureEpisode(p, CONST_TELESCOPE_MIN_EP)) return true;
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg telescope <pseudo>"); return true; }
	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Pas sur toi-même."); return true; }

	        RoleService.RoleState tState = st(target);
	        if (tState == null) { p.sendMessage(ChatColor.RED + "Ce joueur n’a pas de rôle attribué."); return true; }

	        // Constellation publique (Amné v1 non réveillé => constellation du SV)
	        RoleService.Constellation tc = core.publicConstellationForInfo(target.getUniqueId());
	        if (tc == null) {
	            p.sendMessage(ChatColor.RED + "Constellation inconnue.");
	            return true;
	        }

	        p.sendMessage(ChatColor.AQUA + "[Télescope] " + ChatColor.WHITE + target.getName() +
	                " est de la constellation " + ChatColor.GOLD + tc.name());

	        if (tc != st.constellation) {
	            startTrailTask(p, target, st);
	            p.sendMessage(ChatColor.GREEN + "Une traînée dans le ciel pointe " + target.getName()
	                    + " pendant 2 minutes (maj toutes les 10s).");
	        } else {
	            p.sendMessage(ChatColor.GRAY + "Même constellation: aucune traînée générée.");
	        }
	        return true;
	    }


	    private void startTrailTask(Player viewer, Player target, RoleService.RoleState st) {
	        if (st.telescopeTrailTask != null) { st.telescopeTrailTask.cancel(); st.telescopeTrailTask = null; }
	        final long startedAt = System.currentTimeMillis();

	        st.telescopeTrailTask = new org.bukkit.scheduler.BukkitRunnable() {
	            long lastDraw = 0;
	            @Override public void run() {
	                if (!viewer.isOnline() || !target.isOnline()) { cancel(); return; }
	                long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
	                if (elapsed >= 120L) { cancel(); return; }
	                long now = System.currentTimeMillis();
	                if (now - lastDraw >= 10_000L) {
	                    lastDraw = now; drawSkyLine(viewer, target.getLocation());
	                }
	            }
	            @Override public void cancel() { super.cancel(); st.telescopeTrailTask = null; }
	        };
	        st.telescopeTrailTask.runTaskTimer(core.getPlugin(), 0L, 10L);
	    }

	    private void drawSkyLine(Player viewer, org.bukkit.Location targetLoc) {
	        org.bukkit.World w = targetLoc.getWorld();
	        double x = targetLoc.getX(), z = targetLoc.getZ();
	        int yStart = targetLoc.getBlockY() + 5;
	        int yEnd = Math.min(yStart + 40, w.getMaxHeight() - 1);
	        for (int y = yStart; y <= yEnd; y += 2) {
	            viewer.spigot().playEffect(new org.bukkit.Location(w, x, y, z),
	                    org.bukkit.Effect.FLYING_GLYPH, 0, 0, 0,0,0, 0, 8, 32);
	            viewer.spigot().playEffect(new org.bukkit.Location(w, x, y, z),
	                    org.bukkit.Effect.CLOUD, 0, 0, 0,0,0, 0, 6, 32);
	        }
	    }

	    private boolean cmdAstrologie(Player p, String[] args, RoleService.RoleState st) {
	        if (!ensureEpisode(p, CONST_ASTRO_MIN_EP)) return true;
	        if (st.usedAstrologie) { p.sendMessage(ChatColor.RED + "Tu as déjà utilisé Astrologie."); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg astrologie <pseudo>"); return true; }

	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }

	        RoleService.Constellation c = st.constellation;
	        java.util.List<RoleService.RoleId> pool =
	                RoleService.getConstellationPool(c); // on va exposer ce getter dans RoleService (voir étape 3)
	        if (pool == null || pool.isEmpty()) {
	            p.sendMessage(ChatColor.RED + "Aucun rôle connu pour sa constellation pour le moment.");
	            st.usedAstrologie = true;
	            return true;
	        }

	        java.util.List<RoleService.RoleId> copy = new java.util.ArrayList<>(pool);
	        java.util.Collections.shuffle(copy);
	        int n = Math.min(3, copy.size());
	        java.util.List<RoleService.RoleId> pick = copy.subList(0, n);

	        String list = "";
	        for (int i = 0; i < pick.size(); i++) {
	            if (i>0) list += ", ";
	            list += core.displayName(pick.get(i));
	        }
	        p.sendMessage(ChatColor.AQUA + "[Astrologie] Rôles possibles de sa constellation ("
	                + c.name() + ") : " + ChatColor.GOLD + list);

	        st.usedAstrologie = true;
	        return true;
	    }

	    // ====== Détective ======
	    private static final int DETECTIVE_MIN_EP = 3;
	    private Boolean detectiveBlueMeansSame = null;
	    private void ensureDetectiveMapping() {
	        if (detectiveBlueMeansSame == null) detectiveBlueMeansSame = new java.util.Random().nextBoolean();
	    }
	    private String detectiveCampOf(RoleService.Align al) {
	        switch (al) { case VILLAGE: return "Village"; case LOUP: return "Loups"; default: return "Neutre"; }
	    }

	 // ====== Détective ======
	    private boolean cmdDetective(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (!sub.equalsIgnoreCase("enquete")) return false;
	        int ep = core.getGame().getEpisodeNumber();
	        if (ep < DETECTIVE_MIN_EP) { p.sendMessage(ChatColor.RED + "Disponible à partir de l’épisode "+DETECTIVE_MIN_EP+"."); return true; }
	        if (st.detectiveLastUsedEpisode == ep) { p.sendMessage(ChatColor.RED + "Tu as déjà enquêté cet épisode."); return true; }
	        if (args.length < 2) { p.sendMessage(ChatColor.RED + "Usage: /lg enquete <pseudo1> <pseudo2>"); return true; }

	        Player a = Bukkit.getPlayerExact(args[0]);
	        Player b = Bukkit.getPlayerExact(args[1]);
	        if (a == null || !a.isOnline() || b == null || !b.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur(s) introuvable(s)."); return true; }
	        if (a.getUniqueId().equals(b.getUniqueId())) { p.sendMessage(ChatColor.RED + "Choisis deux joueurs différents."); return true; }
	        if (st.detectiveSeenPlayers.contains(a.getUniqueId()) || st.detectiveSeenPlayers.contains(b.getUniqueId())) {
	            p.sendMessage(ChatColor.RED + "Tu as déjà enquêté sur au moins un de ces joueurs."); return true;
	        }

	        RoleService.RoleState ra = st(a), rb = st(b);
	        if (ra == null || rb == null) { p.sendMessage(ChatColor.RED + "Rôles non attribués."); return true; }

	        // Utilise le camp "public" (Amné v1 non réveillé => VILLAGE)
	        RoleService.Align aEff = core.alignOf(core.visibleRoleId(a.getUniqueId()));
	        RoleService.Align bEff = core.alignOf(core.visibleRoleId(b.getUniqueId()));

	        int bucketA = (aEff == RoleService.Align.LOUP) ? 2 : (aEff == RoleService.Align.VILLAGE ? 1 : 0);
	        int bucketB = (bEff == RoleService.Align.LOUP) ? 2 : (bEff == RoleService.Align.VILLAGE ? 1 : 0);
	        boolean same = (bucketA == bucketB);

	        ensureDetectiveMapping();
	        String code = (same == detectiveBlueMeansSame) ? ChatColor.BLUE + "Code Bleu"
	                                                       : ChatColor.YELLOW + "Code Jaune";

	        p.sendMessage(ChatColor.AQUA + "[Enquête] " + ChatColor.WHITE + a.getName() + ChatColor.GRAY + " & "
	                + ChatColor.WHITE + b.getName() + ChatColor.GRAY + " → " + code);

	        st.detectiveLastUsedEpisode = ep;
	        st.detectiveSeenPlayers.add(a.getUniqueId());
	        st.detectiveSeenPlayers.add(b.getUniqueId());
	        core.logRacontable(p, "detective_enquete");
	        return true;
	    }


	    // ====== Prêtresse ======
	    private boolean cmdPretresse(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (!sub.equalsIgnoreCase("consulter")) { p.sendMessage(ChatColor.YELLOW + "Sous-commande : /lg consulter <pseudo>"); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg consulter <pseudo>"); return true; }

	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Pas sur toi-même."); return true; }
	        if (!p.getWorld().equals(target.getWorld()) || p.getLocation().distance(target.getLocation()) > 10.0) {
	            p.sendMessage(ChatColor.RED + "La cible doit être à moins de 10 blocs."); return true;
	        }

	        if (!core.changeMaxHearts(p, -2)) { p.sendMessage(ChatColor.RED + "Tu es trop faible pour utiliser ce pouvoir."); return true; }
	        core.logRacontable(p, "pretresse_espionne");

	        RoleService.RoleState ts = st(target);
	        if (ts == null) { 
	            p.sendMessage(ChatColor.GRAY + "Tu n’obtiens aucun détail.");
	            return true;
	        }

	        // Message informatif (tu peux garder la différence de message si loup ou pas)
	        RoleService.RoleId seen = core.visibleRoleId(target.getUniqueId());
	        boolean wolf = (core.alignOf(seen) == RoleService.Align.LOUP);  // 👈 inclut infectés
	        if (wolf) {
	            p.sendMessage(ChatColor.AQUA + "[Prêtresse] " + ChatColor.WHITE + target.getName()
	                    + " est " + ChatColor.GOLD + core.displayName(seen));
	        } else {
	            p.sendMessage(ChatColor.AQUA + "[Prêtresse] " + ChatColor.WHITE + target.getName()
	                    + ChatColor.GRAY + " n’est pas un Loup-Garou.");
	        }
	        // on mémorise toujours la cible espionnée
	        st.pretresseSeenWolves.add(target.getUniqueId());


	        return true;
	    }

	    // ====== Oracle ======
	    private static final int  ORACLE_MIN_EP        = 3;
	    private static final long ORACLE_COOLDOWN_SEC  = 600L;

	    private boolean cmdOracle(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (sub.equalsIgnoreCase("aura")) return cmdOracleAura(p, args, st);
	        if (sub.equalsIgnoreCase("auras")) {
	            p.sendMessage(ChatColor.AQUA + "[Auras] "
	                    + ChatColor.GOLD + "Lumineuse" + ChatColor.GRAY + ", "
	                    + ChatColor.YELLOW + "Neutre" + ChatColor.GRAY + ", "
	                    + ChatColor.DARK_PURPLE + "Obscure" + ChatColor.GRAY
	                    + " — certaines actions/événements peuvent les faire évoluer.");
	            return true;
	        }
	        p.sendMessage(ChatColor.YELLOW + "Sous-commandes Oracle: /lg aura <pseudo>, /lg auras");
	        return true;
	    }

	    private boolean isOracleUnlocked() {
	        int ep = core.getGame().getEpisodeNumber();
	        if (ep > ORACLE_MIN_EP) return true;
	        if (ep < ORACLE_MIN_EP) return false;
	        int len = Math.max(1, core.getGame().getEpisodeLenSec());
	        int intoEp = core.getGame().getElapsedSeconds() % len;
	        return intoEp >= (len / 2);
	    }

	    private String formatAura(RoleService.Aura a) {
	        switch (a) {
	            case LUMINEUSE: return ChatColor.GOLD + "Lumineuse";
	            case NEUTRE:    return ChatColor.YELLOW + "Neutre";
	            case OBSCURE:   return ChatColor.DARK_PURPLE + "Obscure";
	        }
	        return a.name();
	    }

	    private boolean cmdOracleAura(Player p, String[] args, RoleService.RoleState st) {
	        if (!isOracleUnlocked()) { p.sendMessage(ChatColor.RED + "Disponible à partir de la moitié de l’épisode 3."); return true; }

	        long now = System.currentTimeMillis()/1000L;
	        long left = (st.oracleLastUseSec + ORACLE_COOLDOWN_SEC) - now;
	        if (left > 0) { p.sendMessage(ChatColor.RED + "Cooldown: " + left + "s."); return true; }

	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg aura <pseudo>"); return true; }
	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }

	        // Aura publique (Amné v1 non réveillé => aura du SV)
	        RoleService.Aura seen = core.publicAuraForInfo(target.getUniqueId());

	        p.sendMessage(ChatColor.AQUA + "[Oracle] " + ChatColor.WHITE + target.getName()
	                + ChatColor.GRAY + " a une aura " + formatAura(seen) + ChatColor.GRAY + ".");
	        st.oracleLastUseSec = now;
	        return true;
	    }


	    // ====== Jumeaux: helpers ======
	    private void startOrKeepTwinLineTask(RoleService.RoleState s) {
	        if (s.twinPartner == null) return;
	        if (s.twinLineTask != null) return;
	        Player a = Bukkit.getPlayer(s.owner);
	        Player b = Bukkit.getPlayer(s.twinPartner);
	        if (a == null || b == null) return;

	        s.twinLineTask = new org.bukkit.scheduler.BukkitRunnable() {
	            @Override public void run() {
	                if (!a.isOnline() || !b.isOnline()) { cancel(); return; }
	                drawLineBetween(a, a.getLocation(), b.getLocation());
	                drawLineBetween(b, b.getLocation(), a.getLocation());
	            }
	            @Override public void cancel() { super.cancel(); s.twinLineTask = null; }
	        };
	        s.twinLineTask.runTaskTimer(core.getPlugin(), 0L, 20L);
	    }

	    private void drawLineBetween(Player viewer, org.bukkit.Location from, org.bukkit.Location to) {
	        org.bukkit.World w = from.getWorld();
	        if (w != to.getWorld()) return;
	        int steps = 20;
	        double dx = (to.getX()-from.getX())/steps, dy=(to.getY()-from.getY())/steps, dz=(to.getZ()-from.getZ())/steps;
	        for (int i = 0; i <= steps; i+=2) {
	            org.bukkit.Location p = new org.bukkit.Location(w, from.getX()+dx*i, from.getY()+dy*i+1.2, from.getZ()+dz*i);
	            viewer.spigot().playEffect(p, org.bukkit.Effect.CRIT, 0, 0, 0,0,0, 0, 5, 32);
	        }
	    }

	    private void tryTriggerFraternite(int elapsedSec) {
	        int epLen = Math.max(1, core.getGame().getUhcConfig().getEpisodeMinutes() * 60);
	        int half = Math.max(1, epLen / 2);
	        int curHalf = elapsedSec / half;
	        int firstAllowed = (2 * epLen + half) / half; // milieu de l’épisode 3
	        if (curHalf < firstAllowed) return;

	        for (RoleService.RoleState sa : core.getStates().values()) {
	            if (sa.roleId != RoleService.RoleId.JUMEAU || sa.twinPartner == null) continue;
	            if (sa.lastFraterniteHalfIdx == curHalf) continue;

	            RoleService.RoleState sb = core.getStates().get(sa.twinPartner);
	            if (sb == null || sb.roleId != RoleService.RoleId.JUMEAU) continue;

	            sa.lastFraterniteHalfIdx = curHalf;
	            sb.lastFraterniteHalfIdx = curHalf;

	            Player A = Bukkit.getPlayer(sa.owner);
	            Player B = Bukkit.getPlayer(sa.twinPartner);
	            if (A == null || B == null || !A.isOnline() || !B.isOnline()) continue;
	            if (A.getLocation().distance(B.getLocation()) <= 100.0) continue;

	            List<Player> nearA = nearby(A, 20.0);
	            List<Player> nearB = nearby(B, 20.0);
	            revealTwinInfos(sa, sb, nearA, nearB);
	        }
	    }

	    private List<Player> nearby(Player c, double r) {
	        List<Player> out = new ArrayList<>();
	        org.bukkit.Location L = c.getLocation();
	        for (Player p : Bukkit.getOnlinePlayers()) {
	            if (p.equals(c)) continue;
	            if (!p.getWorld().equals(c.getWorld())) continue;
	            if (p.getLocation().distance(L) <= r) out.add(p);
	        }
	        return out;
	    }

	    private void revealTwinInfos(RoleService.RoleState sa, RoleService.RoleState sb, List<Player> nearA, List<Player> nearB) {
	        if (sa.twinIsRoleSide) {
	            giveOneRoleFromListTo(sa, nearB, "proche de ton Jumeau");
	            giveNamesListTo(sb, nearA, "proches de ton Jumeau");
	        } else if (sb.twinIsRoleSide) {
	            giveOneRoleFromListTo(sb, nearA, "proche de ton Jumeau");
	            giveNamesListTo(sa, nearB, "proches de ton Jumeau");
	        } else {
	            giveOneRoleFromListTo(sa, nearB, "proche de ton Jumeau");
	            giveNamesListTo(sb, nearA, "proches de ton Jumeau");
	        }
	    }

	    private void giveOneRoleFromListTo(RoleService.RoleState recv, List<Player> pool, String label) {
	        Player rec = Bukkit.getPlayer(recv.owner);
	        if (rec == null || !rec.isOnline()) return;
	        Collections.shuffle(pool);
	        for (Player c : pool) {
	            RoleService.RoleState s = st(c);
	            if (s != null) {
	                rec.sendMessage(ChatColor.AQUA + "[Fraternité] " + ChatColor.WHITE + "Rôle d’un joueur " + label + ": "
	                        + ChatColor.GOLD + core.displayName(s.roleId));
	                return;
	            }
	        }
	        rec.sendMessage(ChatColor.AQUA + "[Fraternité] " + ChatColor.GRAY + "Aucun rôle à révéler.");
	    }

	    private void giveNamesListTo(RoleService.RoleState recv, List<Player> pool, String label) {
	        Player rec = Bukkit.getPlayer(recv.owner);
	        if (rec == null || !rec.isOnline()) return;
	        if (pool.isEmpty()) { rec.sendMessage(ChatColor.AQUA + "[Fraternité] " + ChatColor.GRAY + "Aucun joueur " + label + "."); return; }
	        List<String> names = new ArrayList<>();
	        for (Player p : pool) names.add(p.getName());
	        rec.sendMessage(ChatColor.AQUA + "[Fraternité] " + ChatColor.WHITE + "Joueurs " + label + ": "
	                + ChatColor.GOLD + String.join(", ", names));
	    }

	    // ====== Util loups ======
	 // Util loups (redirige vers RoleService, inclut infectedAsWolf)
	    private boolean isWolf(Player p) {
	        return core != null && core.isWolf(p);
	    }
	    // ====== Twins: correction Absorption après pomme ======
	    /** Appelée par RoleService quand un JUMEAU consomme une golden apple et est à >50 blocs de son mate. */
	    public void handleTwinGoldenApple(Player p) {
	        Bukkit.getScheduler().runTask(core.getPlugin(), () -> {
	            try {
	                net.minecraft.server.v1_8_R3.EntityPlayer handle =
	                        ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) p).getHandle();
	                float cur = handle.getAbsorptionHearts(); // 2.0 = 1 coeur
	                if (cur > 2.0F) handle.setAbsorptionHearts(2.0F);
	            } catch (Throwable t) {
	                double before = p.getHealth();
	                p.damage(2.0D);
	                if (p.getHealth() < before) p.setHealth(Math.min(before, p.getMaxHealth()));
	            }
	        });
	    }
	    
	 // ===== Bibliothécaire =====
	    private static final int ARCHIVE_MAX = 3;
	    private static final int ARCHIVE_RECENT_SEC = 20 * 60; // 20 minutes

	    private boolean cmdBibliothecaire(String sub, Player p, String[] args, RoleService.RoleState st) {
	        if (sub.equalsIgnoreCase("confier")) return biblioConfier(p, args, st);
	        if (sub.equalsIgnoreCase("archive")) return biblioArchive(p, args, st);
	        p.sendMessage(ChatColor.YELLOW + "Sous-commandes : /lg confier <pseudo>, /lg archive <pseudo>");
	        return true;
	    }

	    private boolean biblioConfier(Player p, String[] args, RoleService.RoleState st) {
	        int ep = core.getGame().getEpisodeNumber();
	        if (ep < 2) { p.sendMessage(ChatColor.RED + "Disponible à partir du 2ème épisode."); return true; }
	        if (st.biblioBorrower != null) { p.sendMessage(ChatColor.RED + "Ton livre est déjà confié."); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg confier <pseudo>"); return true; }

	        Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Pas sur toi-même."); return true; }

	        // Cherche le livre mémoire dans l’inventaire de la bibliothécaire
	        org.bukkit.inventory.ItemStack memo = takeMemoBookFrom(p);
	        if (memo == null) { p.sendMessage(ChatColor.RED + "Tu n’as pas ton livre mémoire sur toi."); return true; }

	        // Confie
	        Map<Integer, ItemStack> rem = target.getInventory().addItem(memo);
	        if (!rem.isEmpty()) { // inventaire plein, on remet au joueur
	            p.getInventory().addItem(memo);
	            p.sendMessage(ChatColor.RED + "L'inventaire de " + target.getName() + " est plein.");
	            return true;
	        }
	        target.updateInventory();
	        p.updateInventory();

	        st.biblioBorrower = target.getUniqueId();
	        st.biblioLoanEpisode = ep;

	        p.sendMessage(ChatColor.GOLD + "[Bibliothécaire] " + ChatColor.GRAY + "Tu confies ta mémoire à " + ChatColor.WHITE + target.getName() + ChatColor.GRAY + ".");
	        target.sendMessage(ChatColor.GOLD + "[Bibliothécaire] " + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " te confie sa " + ChatColor.GOLD + "Mémoire" + ChatColor.GRAY + ". Tu peux écrire " + ChatColor.GOLD + "une seule page" + ChatColor.GRAY + ".");
	        return true;
	    }

	    private boolean biblioArchive(Player p, String[] args, RoleService.RoleState st) {
	        if (st.biblioArchiveLeft <= 0) { p.sendMessage(ChatColor.RED+"Tu n'as plus d’archives."); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED+"Usage: /lg archive <pseudo>"); return true; }

	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED+"Joueur introuvable."); return true; }

	        java.util.UUID id = target.getUniqueId();

	        String info;
	        if (core.hasKilled(id)) {
	            info = ChatColor.GOLD + "Le joueur a éliminé au moins une personne.";
	        } else if (core.hadRacontableInLastMinutes(id, 20)) {
	            info = ChatColor.YELLOW + "Le joueur a réalisé une action racontable dans les 20 dernières minutes.";
	        } else {
	            info = ChatColor.GRAY + "Rien de notable.";
	        }

	        p.sendMessage(ChatColor.AQUA + "[Bibliothécaire] " + ChatColor.WHITE + target.getName() + ChatColor.GRAY + " → " + info);
	        st.biblioArchiveLeft--;
	        return true;
	    }


	    /** Rappelle automatiquement le livre 5 min avant la fin de l’épisode. Appelée depuis tickPerSecond. */
	    public void biblioTickRecall(int elapsedSec) {
	        int epLen = Math.max(60, core.getGame().getEpisodeLenSec());
	        int into = elapsedSec % epLen;
	        int left = epLen - into;
	        boolean recallWindow = (left <= 300); // 5 minutes
	        int currentEp = core.getGame().getEpisodeNumber();

	        if (!recallWindow) return;

	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != RoleService.RoleId.BIBLIOTHECAIRE) continue;
	            if (s.biblioBorrower == null) continue;
	            if (s.biblioLoanEpisode != currentEp) continue; // on ne rappelle que le prêt de cet épisode

	            Player owner = org.bukkit.Bukkit.getPlayer(s.owner);
	            Player bor = org.bukkit.Bukkit.getPlayer(s.biblioBorrower);
	            if (owner == null || !owner.isOnline()) continue;

	            // reprendre le livre du borrower
	            org.bukkit.inventory.ItemStack memo = takeMemoBookFrom(bor);
	            if (memo != null) {
	                owner.getInventory().addItem(memo);
	                if (bor != null && bor.isOnline()) bor.updateInventory();
	                owner.updateInventory();
	                if (bor != null && bor.isOnline())
	                    bor.sendMessage(ChatColor.GOLD + "[Bibliothécaire] " + ChatColor.GRAY + "Le livre t’est retiré, il retourne à " + ChatColor.WHITE + owner.getName() + ChatColor.GRAY + ".");
	                owner.sendMessage(ChatColor.GOLD + "[Bibliothécaire] " + ChatColor.GRAY + "Ta mémoire t’est rendue (fin d’épisode imminente).");
	            }
	            s.biblioBorrower = null;
	            s.biblioLoanEpisode = -1;
	        }
	    }

	    /** Retire le livre “Mémoire de Bibliothécaire” de l’inventaire du joueur si présent. */
	    private org.bukkit.inventory.ItemStack takeMemoBookFrom(Player p) {
	        if (p == null) return null;
	        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
	        for (int i = 0; i < inv.getSize(); i++) {
	            org.bukkit.inventory.ItemStack it = inv.getItem(i);
	            if (it == null || it.getType() != org.bukkit.Material.BOOK_AND_QUILL) continue;
	            org.bukkit.inventory.meta.BookMeta bm = (org.bukkit.inventory.meta.BookMeta) it.getItemMeta();
	            if (bm != null && bm.getLore() != null) {
	                for (String l : bm.getLore()) {
	                    if (l != null && l.contains("BIBLIO-")) {
	                        inv.setItem(i, null);
	                        return it.clone();
	                    }
	                }
	            }
	        }
	        return null;
	    }

	    private boolean cmdMarchandeVente(Player p, String[] args, RoleService.RoleState st) {
	        if (st.fruitSalesDone >= 3) { p.sendMessage(ChatColor.RED + "Tu as déjà utilisé tes 3 ventes."); return true; }
	        if (st.fruitSaleTarget != null) { p.sendMessage(ChatColor.RED + "Une vente est déjà en attente."); return true; }
	        if (args.length < 1) { p.sendMessage(ChatColor.RED + "Usage: /lg vente <pseudo>"); return true; }

	        Player target = Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Pas sur toi-même."); return true; }

	        st.fruitSaleTarget = target.getUniqueId();


	        p.sendMessage(ChatColor.GOLD + "[Marchande] " + ChatColor.GRAY + "Proposition envoyée à "
	                + ChatColor.WHITE + target.getName() + ChatColor.GRAY + ". Il a 30s pour choisir, sinon tu choisiras.");
	        target.sendMessage(ChatColor.GOLD + "[Marchande] " + ChatColor.GRAY + p.getName()
	                + " te propose un achat. Choisis un fruit ci-dessous :");
	        
	        // envoie les 3 fruits (cliquables) au ciblé
	        sendFruitButtonsToTarget(target, st);

	        // timeout 30s -> la marchande choisit
	        if (st.fruitSaleTask != null) try { st.fruitSaleTask.cancel(); } catch (Throwable ignored) {}
	        st.fruitSaleTask = new org.bukkit.scheduler.BukkitRunnable() {
	            @Override public void run() {
	                if (st.fruitSaleTarget == null) return; // déjà traité
	                Player marchande = Bukkit.getPlayer(st.owner);
	                if (marchande != null && marchande.isOnline()) {
	                    marchande.sendMessage(ChatColor.GOLD + "[Marchande] " + ChatColor.GRAY
	                            + "Le client n’a pas choisi. Sélectionne un fruit :");
	                    sendFruitButtonsToMarchande(marchande, st);
	                }
	            }
	        };
	        st.fruitSaleTask.runTaskLater(core.getPlugin(), 30L * 20L);

	        return true;
	    }
	    
	 // ---------- BOUTONS FRUITS (VERSION SAFE 1.8) ----------
	 // ========= ENVOI AU CIBLÉ =========
	    private void sendFruitButtonsToTarget(Player target, RoleService.RoleState st) {
	        TextComponent line = new TextComponent("");

	        line.addExtra(makeFruitButton("[Pomme]", ChatColor.RED, "pomme",
	                !st.fruitUsed.contains(RoleService.Fruit.POMME)));
	        line.addExtra(space());

	        line.addExtra(makeFruitButton("[Poire]", ChatColor.YELLOW, "poire",
	                !st.fruitUsed.contains(RoleService.Fruit.POIRE)));
	        line.addExtra(space());

	        line.addExtra(makeFruitButton("[Pêche]", ChatColor.LIGHT_PURPLE, "peche",
	                !st.fruitUsed.contains(RoleService.Fruit.PECHE)));

	        target.spigot().sendMessage(line);
	    }

	    // ========= ENVOI À LA MARCHANDE (timeout) =========
	    private void sendFruitButtonsToMarchande(Player marchande, RoleService.RoleState st) {
	        TextComponent line = new TextComponent("");

	        line.addExtra(makeFruitButton("[Pomme]", ChatColor.RED, "pomme",
	                !st.fruitUsed.contains(RoleService.Fruit.POMME)));
	        line.addExtra(space());

	        line.addExtra(makeFruitButton("[Poire]", ChatColor.YELLOW, "poire",
	                !st.fruitUsed.contains(RoleService.Fruit.POIRE)));
	        line.addExtra(space());

	        line.addExtra(makeFruitButton("[Pêche]", ChatColor.LIGHT_PURPLE, "peche",
	                !st.fruitUsed.contains(RoleService.Fruit.PECHE)));

	        marchande.spigot().sendMessage(line);
	    }

	    // ========= BOUTON CLIQUABLE =========
	    private TextComponent makeFruitButton(String label, ChatColor color, String cmd, boolean enabled) {
	        TextComponent btn = new TextComponent(label);
	        btn.setColor(net.md_5.bungee.api.ChatColor.RED);
	        btn.setBold(enabled);

	        if (enabled) {
	            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lg choisir " + cmd));
	            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
	                    new ComponentBuilder("Choisir " + label).create()));
	        } else {
	            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
	                    new ComponentBuilder("Déjà utilisé").color(net.md_5.bungee.api.ChatColor.GRAY).create()));
	        }
	        return btn;
	    }

	    private TextComponent space() {
	        return new TextComponent(" ");
	    }





	    private void addFruitButton(net.md_5.bungee.api.chat.TextComponent parent,
	                                String label, ChatColor color, String arg,
	                                boolean enabled) {
	        net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent("[" + label + "]");
	        btn.setColor(net.md_5.bungee.api.ChatColor.valueOf(color.name()));
	        if (enabled) {
	            btn.setBold(true);
	            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
	                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg choisir " + arg));
	            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
	                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
	                    new net.md_5.bungee.api.chat.ComponentBuilder("Choisir " + label).create()));
	        } else {
	            btn.setColor(net.md_5.bungee.api.ChatColor.GRAY);
	            btn.setBold(false);
	            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
	                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
	                    new net.md_5.bungee.api.chat.ComponentBuilder("Déjà utilisé").color(net.md_5.bungee.api.ChatColor.GRAY).create()));
	        }
	        parent.addExtra(btn);
	    }
	    
	    private boolean cmdMarchandeChoisir(Player clicker, String[] args, RoleService.RoleState ignoredStateOfClicker) {
	        if (args.length < 1) { clicker.sendMessage(ChatColor.RED + "Usage: /lg choisir <pomme|poire|peche>"); return true; }

	        // Trouver la vente en cours (la marchande qui t’a ciblé)
	        RoleService.RoleState m = null;
	        for (RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId == RoleService.RoleId.MARCHANDE_DE_FRUITS && s.fruitSaleTarget != null) {
	                // autoriser le clic si:
	                // - le clicker est la cible
	                // - OU c’est la marchande (choix de rattrapage)
	                if (clicker.getUniqueId().equals(s.fruitSaleTarget) || clicker.getUniqueId().equals(s.owner)) {
	                    m = s; break;
	                }
	            }
	        }
	        if (m == null) { clicker.sendMessage(ChatColor.RED + "Aucune vente en attente te concernant."); return true; }

	        RoleService.Fruit fruit = null;
	        String a = args[0].toLowerCase(java.util.Locale.ROOT);
	        if (a.startsWith("pom")) fruit = RoleService.Fruit.POMME;
	        else if (a.startsWith("poi")) fruit = RoleService.Fruit.POIRE;
	        else if (a.startsWith("pec") || a.startsWith("pêc")) fruit = RoleService.Fruit.PECHE;
	        if (fruit == null) { clicker.sendMessage(ChatColor.RED + "Fruit invalide: pomme, poire, peche."); return true; }

	        if (m.fruitUsed.contains(fruit)) { clicker.sendMessage(ChatColor.RED + "Ce fruit n’est plus disponible."); return true; }

	        Player marchande = Bukkit.getPlayer(m.owner);
	        Player target    = Bukkit.getPlayer(m.fruitSaleTarget);

	        revealFruitInfo(marchande, target, fruit, m);
	        cleanupSale(m);
	        return true;
	    }
	    
	    private void cleanupSale(RoleService.RoleState st) {
	        if (st == null) return;
	        st.fruitSaleTarget = null;
	        if (st.fruitSaleTask != null) try { st.fruitSaleTask.cancel(); } catch (Throwable ignored) {}
	        st.fruitSaleTask = null;
	    }

	    private void revealFruitInfo(Player marchande, Player client, RoleService.Fruit fruit, RoleService.RoleState m) {
	        if (marchande == null || !marchande.isOnline()) return;

	        RoleService.FruitInfo info = m.fruitMap.get(fruit);
	        if (info == null) { marchande.sendMessage(ChatColor.RED + "[Marchande] Mapping fruit inconnu."); return; }

	        // Si l’info a déjà été donnée, bascule sur une autre restante
	        if (m.infosGiven.contains(info)) {
	            java.util.List<RoleService.FruitInfo> pool = new java.util.ArrayList<>();
	            pool.add(RoleService.FruitInfo.GAPPLES);
	            pool.add(RoleService.FruitInfo.KILLS);
	            pool.add(RoleService.FruitInfo.AURA_BASE);
	            pool.removeAll(m.infosGiven);
	            if (!pool.isEmpty()) info = pool.get(new java.util.Random().nextInt(pool.size()));
	        }

	        m.fruitUsed.add(fruit);
	        m.infosGiven.add(info);
	        m.fruitSalesDone++;

	        // feedback “qui a cliqué”
	        if (client != null && client.isOnline())
	            client.sendMessage(ChatColor.GOLD + "[Marchande] " + ChatColor.GRAY + "La " + fruitNice(fruit) + ChatColor.GRAY + "a été choisi.");

	        switch (info) {
	            case GAPPLES: {
	                int n = (client != null ? core.countGapples(client) : 0);
	                marchande.sendMessage(ChatColor.GOLD + "[Marchande] "
	                        + ChatColor.WHITE + (client != null ? client.getName() : "Le joueur")
	                        + ChatColor.GRAY + " possède " + ChatColor.GOLD + n + ChatColor.GRAY + " gapple(s).");
	                break;
	            }
	            case KILLS: {
	                int k = (client != null ? core.getKills(client) : 0);
	                marchande.sendMessage(ChatColor.GOLD + "[Marchande] "
	                        + ChatColor.WHITE + (client != null ? client.getName() : "Le joueur")
	                        + ChatColor.GRAY + " a " + ChatColor.GOLD + k + ChatColor.GRAY + " kill(s).");
	                break;
	            }
	            case AURA_BASE: {
	                RoleService.Aura a = (client != null ? core.baseAuraOf(client) : RoleService.Aura.NEUTRE);
	                marchande.sendMessage(ChatColor.GOLD + "[Marchande] "
	                        + ChatColor.WHITE + (client != null ? client.getName() : "Le joueur")
	                        + ChatColor.GRAY + " a une aura de base " + core.auraPretty(a) + ChatColor.GRAY + ".");
	                break;
	            }
	        }

	        // rappel des correspondances (fixes) = Test si fruit fixe
	        ///marchande.sendMessage(ChatColor.GRAY + "Fruits→Infos (fixe) : "
	                ///+ ChatColor.RED + "Pomme" + ChatColor.GRAY + "=" + infoName(m.fruitMap.get(RoleService.Fruit.POMME)) + ChatColor.GRAY + ", "
	               /// + ChatColor.YELLOW + "Poire" + ChatColor.GRAY + "=" + infoName(m.fruitMap.get(RoleService.Fruit.POIRE)) + ChatColor.GRAY + ", "
	               // + ChatColor.LIGHT_PURPLE + "Pêche" + ChatColor.GRAY + "=" + infoName(m.fruitMap.get(RoleService.Fruit.PECHE)) + ChatColor.GRAY + ".");
	    }

	    private String fruitNice(RoleService.Fruit f) {
	        switch (f) {
	            case POMME: return ChatColor.RED + "Pomme";
	            case POIRE: return ChatColor.YELLOW + "Poire";
	            default:    return ChatColor.LIGHT_PURPLE + "Pêche";
	        }
	    }
	    private String infoName(RoleService.FruitInfo i) {
	        if (i == null) return "?";
	        switch (i) {
	            case GAPPLES: return "Gapples";
	            case KILLS: return "Kills";
	            case AURA_BASE: return "Aura de base";
	        }
	        return "?";
	    }
	    
	    private boolean cmdSorciereRez(org.bukkit.entity.Player p) {
	        RoleService.RoleState s = core.get(p);
	        if (s == null || s.roleId != RoleService.RoleId.SORCIERE) return false;
	        if (s.witchResUsed) { p.sendMessage(ChatColor.RED + "Tu as déjà utilisé la Résurrection."); return true; }
	        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) { p.sendMessage(ChatColor.RED + "Tu dois être en vie."); return true; }

	        DeathManager dm = core.getPlugin().getDeathManager();
	        boolean ok = dm != null && dm.sorciereTryResurrect(s.owner);
	        p.sendMessage(ok ? ChatColor.GOLD + "[Sorcière] " + ChatColor.GRAY + "Résurrection effectuée."
	                         : ChatColor.RED + "Trop tard ou aucune mort à ressusciter.");
	        return true;
	    }

	    private boolean cmdSorciereCurse(org.bukkit.entity.Player p) {
	        RoleService.RoleState s = core.get(p);
	        if (s == null || s.roleId != RoleService.RoleId.SORCIERE) return false;
	        if (s.witchCurseUsed) { p.sendMessage(ChatColor.RED + "Tu as déjà utilisé la Malédiction."); return true; }
	        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) { p.sendMessage(ChatColor.RED + "Tu dois être en vie."); return true; }

	        DeathManager dm = core.getPlugin().getDeathManager();
	        boolean ok = dm != null && dm.sorciereTryCurse(s.owner);
	        p.sendMessage(ok ? ChatColor.GOLD + "[Sorcière] " + ChatColor.GRAY + "Malédiction appliquée."
	                         : ChatColor.RED + "Trop tard ou pas de tueur à maudire.");
	        return true;
	    }




	    
	    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
	    public void onGapple(org.bukkit.event.player.PlayerItemConsumeEvent e) {
	        if (e.getItem() == null || e.getItem().getType() != org.bukkit.Material.GOLDEN_APPLE) return;

	        org.bukkit.entity.Player p = e.getPlayer();
	        if (!core.isWitchCursed(p.getUniqueId())) return;  // ✅ source unique

	        // Appliquer la réduction après que l'absorption ait été posée
	        org.bukkit.Bukkit.getScheduler().runTask(core.getPlugin(), () -> {
	            try {
	                net.minecraft.server.v1_8_R3.EntityPlayer h =
	                    ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) p).getHandle();
	                float cur = Math.max(0.0F, h.getAbsorptionHearts());
	                float after = Math.max(0.0F, cur - 1.0F); // −1 cœur = −2.0 HP
	                h.setAbsorptionHearts(after);
	            } catch (Throwable t) {
	                // Fallback: inflige 2 HP “fictifs” puis restaure la vraie vie si elle a baissé
	                double before = p.getHealth();
	                p.damage(1.0D);
	                if (p.getHealth() < before) p.setHealth(Math.min(before, p.getMaxHealth()));
	            }
	        });
	    }


	    
	    private void applySoeurProximityResist() {
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.SOEUR) continue;

	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (p == null || !p.isOnline() || p.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

	            java.util.UUID mateId = s.soeurPartner;
	            if (mateId == null) { // pas de partenaire: on s’assure que l’effet est retiré
	                safeRemoveResist(p);
	                continue;
	            }

	            org.bukkit.entity.Player mate = org.bukkit.Bukkit.getPlayer(mateId);
	            boolean inRange = false;
	            if (mate != null && mate.isOnline() && mate.getWorld() == p.getWorld()
	                    && mate.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
	                inRange = (p.getLocation().distanceSquared(mate.getLocation()) <= 20.0 * 20.0);
	            }

	            if (inRange) {
	                // Applique une seule fois si manquant (durée énorme, particules OFF pour l’esthétique)
	                if (!p.hasPotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE)) {
	                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                            org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, HUGE, 0,
	                            true, /* ambient */ false /* particles OFF */));
	                }
	            } else {
	                // Hors portée → retire l’effet s’il est présent
	                safeRemoveResist(p);
	            }
	        }
	    }

	    private void safeRemoveResist(org.bukkit.entity.Player p) {
	        try { p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE); } catch (Throwable ignored) {}
	    }

	    // Ligne de particules entre les sœurs (à partir d’EP3 + 5min)
	    private void drawSisterParticleLine(int elapsedSec) {
	        int epLen = core.getGame().getEpisodeLenSec(); // ex 10*60
	        int unlockAt = 3 * epLen + 5 * 60;
	        if (elapsedSec < unlockAt) return;

	        // Une fois débloqué, on dessine 1x/sec une ligne simple
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.SOEUR) continue;
	            if (s.soeurPartner == null) continue;

	            org.bukkit.entity.Player a = org.bukkit.Bukkit.getPlayer(s.owner);
	            org.bukkit.entity.Player b = org.bukkit.Bukkit.getPlayer(s.soeurPartner);
	            if (a == null || b == null) continue;
	            if (!a.isOnline() || !b.isOnline()) continue;
	            if (a.getGameMode() != org.bukkit.GameMode.SURVIVAL || b.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
	            if (a.getWorld() != b.getWorld()) continue;

	            // Dessine pour A et B une ligne d’étincelles (1.8 : FIREWORKS_SPARK)
	            try {
	                drawSimpleLineParticles(a, a.getLocation(), b.getLocation());
	                drawSimpleLineParticles(b, b.getLocation(), a.getLocation());
	            } catch (Throwable ignored) {}
	        }
	    }

	    // “ligne” 1.8 avec Effect.FIREWORKS_SPARK
	    private void drawSimpleLineParticles(org.bukkit.entity.Player viewer, org.bukkit.Location from, org.bukkit.Location to) {
	        int points = 12;
	        double dx = (to.getX() - from.getX()) / points;
	        double dy = (to.getY() - from.getY()) / points;
	        double dz = (to.getZ() - from.getZ()) / points;
	        org.bukkit.Location cur = from.clone();
	        for (int i = 0; i <= points; i++) {
	            viewer.getWorld().playEffect(cur, org.bukkit.Effect.FIREWORKS_SPARK, 0, 32);
	            cur.add(dx, dy, dz);
	        }
	    }

	    
	 // ---------- Annonce Sœurs & Jumeaux : EP3 + 5 min ----------
	    private void announceSistersAndTwinsAtEp3Plus5() {
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            java.util.UUID selfId = s.owner;
	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(selfId);
	            if (p == null || !p.isOnline()) continue;

	            // On ne notifie que les joueurs encore "vivants"
	            if (!isAlive(selfId)) continue;

	            // SOEURS
	            if (isSisterRole(s.roleId)) {
	                java.util.UUID mate = resolveSisterPartner(s);
	                if (mate != null) {
	                    String mateName = safeNameOf(mate);
	                    if (mateName != null) {
	                        p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Sœurs] "
	                            + org.bukkit.ChatColor.GRAY + "Ta sœur est "
	                            + org.bukkit.ChatColor.GOLD + mateName + org.bukkit.ChatColor.GRAY + ".");
	                    }
	                }
	                continue;
	            }

	            // JUMEAUX
	            if (isTwinRole(s.roleId)) {
	                java.util.UUID mate = resolveTwinPartner(s);
	                if (mate != null) {
	                    String mateName = safeNameOf(mate);
	                    if (mateName != null) {
	                        p.sendMessage(org.bukkit.ChatColor.AQUA + "[Jumeaux] "
	                            + org.bukkit.ChatColor.GRAY + "Ton jumeau est "
	                            + org.bukkit.ChatColor.GOLD + mateName + org.bukkit.ChatColor.GRAY + ".");
	                    }
	                }
	            }
	        }
	    }

	    // ---------- Helpers ----------

	    // "vivant" = pas en sursis et en SURVIVAL
	    private boolean isAlive(java.util.UUID id) {
	        try { return core.getPlugin().getDeathManager().isAlive(id); }
	        catch (Throwable t) {
	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
	            return p != null && p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL;
	        }
	    }

	    // Récupère un nom fiable (RoleService → online → offline)
	    private String safeNameOf(java.util.UUID id) {
	        try { String n = core.nameOfUUID(id); if (n != null) return n; } catch (Throwable ignored) {}
	        org.bukkit.entity.Player on = org.bukkit.Bukkit.getPlayer(id);
	        if (on != null && on.isOnline()) return on.getName();
	        try {
	            org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(id);
	            if (off != null && off.getName() != null) return off.getName();
	        } catch (Throwable ignored) {}
	        return null;
	    }

	    // Détecte les rôles "Sœur" (peu importe l'enum exact)
	    private boolean isSisterRole(fr.sfakeur.lguhc.RoleService.RoleId rid) {
	        if (rid == null) return false;
	        String n = rid.name();
	        return n.equalsIgnoreCase("SOEUR")
	            || n.equalsIgnoreCase("SOEURS")
	            || n.equalsIgnoreCase("SOEUR_1")
	            || n.equalsIgnoreCase("SOEUR_2")
	            || n.equalsIgnoreCase("SOEURS_1")
	            || n.equalsIgnoreCase("SOEURS_2");
	    }

	    // Détecte les rôles "Jumeau"
	    private boolean isTwinRole(fr.sfakeur.lguhc.RoleService.RoleId rid) {
	        if (rid == null) return false;
	        String n = rid.name();
	        return n.equalsIgnoreCase("JUMEAU")
	            || n.equalsIgnoreCase("JUMEAUX")
	            || n.equalsIgnoreCase("JUMEAU_1")
	            || n.equalsIgnoreCase("JUMEAU_2")
	            || n.equalsIgnoreCase("JUMEAUX_1")
	            || n.equalsIgnoreCase("JUMEAUX_2");
	    }

	    // Trouve la sœur associée :
	    //  - si tu as déjà un champ partner (ex: s.sisterPartner) il sera utilisé
	    //  - sinon on devine en prenant l'autre joueur taggé "sœur"
	    private java.util.UUID resolveSisterPartner(fr.sfakeur.lguhc.RoleService.RoleState self) {
	        try {
	            java.lang.reflect.Field f = self.getClass().getDeclaredField("sisterPartner");
	            f.setAccessible(true);
	            Object v = f.get(self);
	            if (v instanceof java.util.UUID) return (java.util.UUID) v;
	        } catch (Throwable ignored) {}

	        // fallback: prendre "l'autre sœur"
	        String me = (self.roleId != null ? self.roleId.name() : "");
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            if (s.owner.equals(self.owner)) continue;
	            if (!isSisterRole(s.roleId)) continue;

	            // Si tes enums sont *_1/*_2, on évite de jumeler 1-1 ou 2-2
	            String other = (s.roleId != null ? s.roleId.name() : "");
	            if ((me.endsWith("_1") && other.endsWith("_2")) || (me.endsWith("_2") && other.endsWith("_1")) || (!me.contains("_") && !other.contains("_")))
	                return s.owner;
	        }
	        return null;
	    }

	    // Trouve le jumeau associé (même logique que sœurs)
	    private java.util.UUID resolveTwinPartner(fr.sfakeur.lguhc.RoleService.RoleState self) {
	        try {
	            java.lang.reflect.Field f = self.getClass().getDeclaredField("twinPartner");
	            f.setAccessible(true);
	            Object v = f.get(self);
	            if (v instanceof java.util.UUID) return (java.util.UUID) v;
	        } catch (Throwable ignored) {}

	        String me = (self.roleId != null ? self.roleId.name() : "");
	        for (fr.sfakeur.lguhc.RoleService.RoleState s : core.getStates().values()) {
	            if (s.owner.equals(self.owner)) continue;
	            if (!isTwinRole(s.roleId)) continue;

	            String other = (s.roleId != null ? s.roleId.name() : "");
	            if ((me.endsWith("_1") && other.endsWith("_2")) || (me.endsWith("_2") && other.endsWith("_1")) || (!me.contains("_") && !other.contains("_")))
	                return s.owner;
	        }
	        return null;
	    }
	    
	    @org.bukkit.event.EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
	    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent e) {
	        if (e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
	        if (!(e.getEntity() instanceof org.bukkit.entity.Player)) return;

	        Player p = (Player) e.getEntity();
	        RoleService.RoleState s = core.get(p);
	        if (s == null || s.roleId != RoleService.RoleId.ANCIEN) return;

	        // No-fall permanent uniquement si l’Ancien s’est déjà ressuscité
	        if (s.ancienNoFallPermanent) {
	            e.setCancelled(true);
	            try { p.setFallDistance(0f); } catch (Throwable ignored) {}
	        }
	    }
	    
	 // VillageHandler.java
	    private boolean cmdBienfaiteurVie(org.bukkit.entity.Player p, String[] args, RoleService.RoleState s) {
	        if (s == null || s.roleId != RoleService.RoleId.BIENFAITEUR) return false;

	        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Tu dois être en vie pour utiliser ce pouvoir.");
	            return true;
	        }

	        if (s.bienfUsesLeft <= 0) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Tu as déjà donné tes 3 cœurs.");
	            return true;
	        }

	        if (args.length < 1) {
	            p.sendMessage(org.bukkit.ChatColor.YELLOW + "Usage: /lg vie <pseudo>");
	            return true;
	        }

	        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Joueur introuvable.");
	            return true;
	        }

	        if (target.getUniqueId().equals(p.getUniqueId())) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Tu ne peux pas te cibler toi-même.");
	            return true;
	        }

	        if (s.bienfGiven.contains(target.getUniqueId())) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Tu as déjà offert un cœur à ce joueur.");
	            return true;
	        }

	        // +1 cœur permanent (== +2 HP max)
	        boolean ok = core.changeMaxHearts(target, +1);
	        if (!ok) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Impossible de modifier les cœurs de " + target.getName() + ".");
	            return true;
	        }

	        s.bienfGiven.add(target.getUniqueId());
	        s.bienfUsesLeft--;

	        p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Bienfaiteur] "
	                + org.bukkit.ChatColor.GRAY + "Tu offres " + org.bukkit.ChatColor.GOLD + "+1♥ permanent "
	                + org.bukkit.ChatColor.GRAY + "à " + org.bukkit.ChatColor.WHITE + target.getName() + org.bukkit.ChatColor.GRAY + ".");
	        target.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Bienfaiteur] "
	                + org.bukkit.ChatColor.WHITE + p.getName() + org.bukkit.ChatColor.GRAY
	                + " t’offre " + org.bukkit.ChatColor.GOLD + "+1♥ permanent" + org.bukkit.ChatColor.GRAY + ".");

	        // Si c’était le 3e, activer la régén : +1♥ toutes les 30s
	        if (s.bienfUsesLeft <= 0 && !s.bienfRegenActive) {
	            s.bienfRegenActive = true;
	            s.bienfNextRegenMs = System.currentTimeMillis() + 30_000L;
	            p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Bienfaiteur] "
	                    + org.bukkit.ChatColor.GRAY + "Tu as donné tes 3 cœurs : "
	                    + "tu récupéreras " + org.bukkit.ChatColor.GOLD + "1♥" + org.bukkit.ChatColor.GRAY
	                    + " toutes les " + org.bukkit.ChatColor.GOLD + "30 secondes");
	        }

	        return true;
	    }
	    
	    private boolean cmdEnchanteresse(org.bukkit.entity.Player p, String[] args, RoleService.RoleState st) {
	        if (st == null || st.roleId != RoleService.RoleId.ENCHANTERESSE) return false;

	        long now = System.currentTimeMillis();
	        if (now < st.enchLastUseMs + ENCH_COOLDOWN_MS) {
	            long left = (st.enchLastUseMs + ENCH_COOLDOWN_MS - now) / 1000L;
	            p.sendMessage(org.bukkit.ChatColor.RED + "Cooldown: " + left + "s.");
	            return true;
	        }
	        if (args.length < 1) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Usage: /lg enchanter <pseudo>");
	            return true;
	        }
	        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(org.bukkit.ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(org.bukkit.ChatColor.RED + "Pas sur toi-même."); return true; }
	        if (target.getGameMode() != org.bukkit.GameMode.SURVIVAL) { p.sendMessage(org.bukkit.ChatColor.RED + "La cible doit être en SURVIVAL."); return true; }

	        // Envoie 3 boutons à l’Enchanteresse pour choisir la catégorie à booster
	        net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent("");
	        line.addExtra(enBtn("[Épée]", net.md_5.bungee.api.ChatColor.RED, "/lg enchanter_apply " + target.getName() + " sword"));
	        line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
	        line.addExtra(enBtn("[Arc]", net.md_5.bungee.api.ChatColor.GOLD, "/lg enchanter_apply " + target.getName() + " bow"));
	        line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
	        line.addExtra(enBtn("[Armure]", net.md_5.bungee.api.ChatColor.AQUA, "/lg enchanter_apply " + target.getName() + " armor"));
	        p.spigot().sendMessage(line);

	        p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Enchanteresse] "
	            + org.bukkit.ChatColor.GRAY + "Choisis ce que tu souhaites améliorer chez "
	            + org.bukkit.ChatColor.WHITE + target.getName() + org.bukkit.ChatColor.GRAY + ".");

	        return true;
	    }

	    private net.md_5.bungee.api.chat.TextComponent enBtn(String label, net.md_5.bungee.api.ChatColor col, String cmd) {
	        net.md_5.bungee.api.chat.TextComponent b = new net.md_5.bungee.api.chat.TextComponent(label);
	        b.setBold(true); b.setColor(col);
	        b.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
	            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, cmd));
	        b.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
	            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
	            new net.md_5.bungee.api.chat.ComponentBuilder("Appliquer: " + label).create()));
	        return b;
	    }


	    private net.md_5.bungee.api.chat.TextComponent makeEnchKeyButton(String label, net.md_5.bungee.api.ChatColor color, String arg) {
	        net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent("[" + label + "]");
	        btn.setColor(color);
	        btn.setBold(true);
	        btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
	                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg ech " + arg));
	        btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
	                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
	                new net.md_5.bungee.api.chat.ComponentBuilder("Choisir " + label).create()));
	        return btn;
	    }

	    private boolean cmdEnchanteresseApply(org.bukkit.entity.Player caster, String[] args, RoleService.RoleState st) {
	        if (st == null || st.roleId != RoleService.RoleId.ENCHANTERESSE) return true;
	        if (args.length < 2) { caster.sendMessage(org.bukkit.ChatColor.RED + "Usage interne."); return true; }

	        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { caster.sendMessage(org.bukkit.ChatColor.RED + "Cible hors-ligne."); return true; }
	        if (target.getUniqueId().equals(caster.getUniqueId())) { caster.sendMessage(org.bukkit.ChatColor.RED + "Pas sur toi-même."); return true; }
	        if (target.getGameMode() != org.bukkit.GameMode.SURVIVAL) { caster.sendMessage(org.bukkit.ChatColor.RED + "La cible doit être en SURVIVAL."); return true; }

	        long now = System.currentTimeMillis();
	        if (now < st.enchLastUseMs + ENCH_COOLDOWN_MS) {
	            long left = (st.enchLastUseMs + ENCH_COOLDOWN_MS - now) / 1000L;
	            caster.sendMessage(org.bukkit.ChatColor.RED + "Cooldown: " + left + "s.");
	            return true;
	        }

	        String kind = args[1].toLowerCase(java.util.Locale.ROOT);
	        boolean ok = false;

	        // Si la cible avait déjà un boost actif → on le revert d’abord
	        revertEnchantIfAny(target.getUniqueId());

	        if (kind.equals("sword")) {
	            ok = boostSword(target);
	        } else if (kind.equals("bow")) {
	            ok = boostBow(target);
	        } else if (kind.equals("armor")) {
	            ok = boostArmor(target);
	        }

	        if (!ok) {
	            caster.sendMessage(org.bukkit.ChatColor.RED + "Aucun enchantement éligible à booster chez " + target.getName() + ".");
	            return true;
	        }

	        st.enchLastUseMs = now; // lance cooldown
	        caster.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Enchanteresse] "
	            + org.bukkit.ChatColor.GRAY + "Boost appliqué à " + org.bukkit.ChatColor.WHITE + target.getName()
	            + org.bukkit.ChatColor.GRAY + " pour " + org.bukkit.ChatColor.GOLD + "20 minutes" + org.bukkit.ChatColor.GRAY + ".");
	        target.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Enchantement] "
	            + org.bukkit.ChatColor.GRAY + "Un mystérieux pouvoir renforce temporairement tes enchantements...");
	        return true;
	    }


	    private String keyLabel(RoleService.RoleState.EnchKey k) {
	        switch (k) {
	            case SAVOIR_FAIRE: return "Le Savoir-Faire";
	            case DEXTERITE:    return "La Dextérité";
	            default:           return "La Force";
	        }
	    }
	    
	    private boolean boostSword(org.bukkit.entity.Player target) {
	        org.bukkit.inventory.PlayerInventory inv = target.getInventory();
	        int bestSlot = -1; org.bukkit.inventory.ItemStack best = null;
	        // on parcourt tout l’inventaire et on prend la meilleure épée trouvée
	        for (int i = 0; i < inv.getSize(); i++) {
	            org.bukkit.inventory.ItemStack it = inv.getItem(i);
	            if (it == null) continue;
	            org.bukkit.Material t = it.getType();
	            if (t != org.bukkit.Material.WOOD_SWORD
	             && t != org.bukkit.Material.STONE_SWORD
	             && t != org.bukkit.Material.IRON_SWORD
	             && t != org.bukkit.Material.DIAMOND_SWORD
	             && t != org.bukkit.Material.GOLD_SWORD) continue;

	            int cur = it.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DAMAGE_ALL);
	            if (cur >= 3) { // éligible
	                // on préfère la plus forte (cur plus haut), puis meilleur matériau
	                if (best == null || cur > best.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DAMAGE_ALL)) {
	                    best = it; bestSlot = i;
	                }
	            }
	        }
	        if (best == null) return false;

	        int cur = best.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DAMAGE_ALL);
	        int next = Math.min(5, cur + 1);
	        if (next <= cur) return false;

	        // snapshot + set
	        snapshotAndSetEnchant(target, bestSlot, org.bukkit.enchantments.Enchantment.DAMAGE_ALL, cur, next);
	        scheduleRevert(target.getUniqueId());
	        return true;
	    }
	    
	    private boolean boostBow(org.bukkit.entity.Player target) {
	        org.bukkit.inventory.PlayerInventory inv = target.getInventory();
	        int slot = -1; org.bukkit.inventory.ItemStack bow = null;

	        for (int i = 0; i < inv.getSize(); i++) {
	            org.bukkit.inventory.ItemStack it = inv.getItem(i);
	            if (it == null || it.getType() != org.bukkit.Material.BOW) continue;
	            int pow = it.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE);
	            int pun = it.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK);
	            // au moins un des deux éligible ?
	            if ((pow >= 3 && pow < 5) || (pun >= 1 && pun < 2)) { bow = it; slot = i; break; }
	        }
	        if (bow == null) return false;

	        boolean changed = false;
	        int pow = bow.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE);
	        if (pow >= 3) {
	            int next = Math.min(5, pow + 1);
	            if (next > pow) { snapshotAndSetEnchant(target, slot, org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, pow, next); changed = true; }
	        }
	        int pun = bow.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK);
	        if (pun >= 1) {
	            int next = Math.min(2, pun + 1);
	            if (next > pun) { snapshotAndSetEnchant(target, slot, org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK, pun, next); changed = true; }
	        }
	        if (!changed) return false;

	        scheduleRevert(target.getUniqueId());
	        return true;
	    }
	    
	    private boolean boostArmor(org.bukkit.entity.Player target) {
	        boolean diamond = enchRnd.nextBoolean(); // true=DIAMANT, false=FER
	        org.bukkit.inventory.PlayerInventory inv = target.getInventory();

	        // Slots d’armure en 1.8 : 36 boots, 37 legs, 38 chest, 39 helmet
	        int[] slots = new int[] {36,37,38,39};
	        boolean any = false;

	        for (int slot : slots) {
	            org.bukkit.inventory.ItemStack it = inv.getItem(slot);
	            if (it == null) continue;

	            org.bukkit.Material m = it.getType();
	            boolean matchMat =
	                (diamond && (m == org.bukkit.Material.DIAMOND_BOOTS || m == org.bukkit.Material.DIAMOND_LEGGINGS ||
	                             m == org.bukkit.Material.DIAMOND_CHESTPLATE || m == org.bukkit.Material.DIAMOND_HELMET))
	             || (!diamond && (m == org.bukkit.Material.IRON_BOOTS || m == org.bukkit.Material.IRON_LEGGINGS ||
	                              m == org.bukkit.Material.IRON_CHESTPLATE || m == org.bukkit.Material.IRON_HELMET));
	            if (!matchMat) continue;

	            int cur = it.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL);
	            int need = diamond ? 2 : 3;
	            if (cur >= need && cur < 4) {
	                int next = Math.min(4, cur + 1);
	                snapshotAndSetEnchant(target, slot, org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, cur, next);
	                any = true;
	            }
	        }

	        if (!any) return false;
	        scheduleRevert(target.getUniqueId());
	        return true;
	    }
	    
	    private void snapshotAndSetEnchant(org.bukkit.entity.Player target, int slot,
                org.bukkit.enchantments.Enchantment ench,
                int prev, int next) {
	    	org.bukkit.inventory.PlayerInventory inv = target.getInventory();
	    	org.bukkit.inventory.ItemStack it = inv.getItem(slot);
	    	if (it == null) return;

	    	// enregistre
	    	java.util.List<EnchantSnapshot> list = enchSnapshots.computeIfAbsent(target.getUniqueId(), k -> new java.util.ArrayList<>());
	    	list.add(new EnchantSnapshot(slot, ench, prev, next));

	    	// applique
	    	try {
	    		it.addUnsafeEnchantment(ench, next);
	    		inv.setItem(slot, it);
	    		target.updateInventory();
	    	} catch (Throwable ignored) {}
	    }

	    private void scheduleRevert(java.util.UUID targetId) {
	    	// annule timer existant pour cette cible (on repart sur 20 min pleines)
	    	org.bukkit.scheduler.BukkitTask old = enchReverts.remove(targetId);
	    	if (old != null) try { old.cancel(); } catch (Throwable ignored) {}

	    	org.bukkit.scheduler.BukkitTask t = org.bukkit.Bukkit.getScheduler().runTaskLater(core.getPlugin(), () -> {
	    		revertEnchantIfAny(targetId);
	    	}, ENCH_DURATION_TICKS);
	    	enchReverts.put(targetId, t);
	    }

	    private void revertEnchantIfAny(java.util.UUID targetId) {
	    	java.util.List<EnchantSnapshot> snaps = enchSnapshots.remove(targetId);
	    	org.bukkit.scheduler.BukkitTask t = enchReverts.remove(targetId);
	    	if (t != null) try { t.cancel(); } catch (Throwable ignored) {}

	    	if (snaps == null || snaps.isEmpty()) return;
	    	org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(targetId);
	    	if (p == null || !p.isOnline()) return;

	    	org.bukkit.inventory.PlayerInventory inv = p.getInventory();
	    	for (EnchantSnapshot s : snaps) {
			org.bukkit.inventory.ItemStack it = inv.getItem(s.slot);
			if (it == null) continue;
			int cur = it.getEnchantmentLevel(s.ench);
			// on ne redescend que si c’est bien le niveau boosté
			if (cur == s.now) {
				try {
					if (s.prev > 0) {
						it.addUnsafeEnchantment(s.ench, s.prev);
					} else {
						it.removeEnchantment(s.ench);
					}
					inv.setItem(s.slot, it);
				} catch (Throwable ignored) {}
			}
	    	}
	    	p.updateInventory();
		try { p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Enchantement] " + org.bukkit.ChatColor.GRAY + "Ton renforcement temporaire a pris fin."); } catch (Throwable ignored) {}
	  }










	    


	    
	    
	    
	    


	    
	   





	
    
    

}
