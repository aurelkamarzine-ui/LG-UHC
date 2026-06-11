package fr.sfakeur.lguhc;

import org.bukkit.entity.Player;

import fr.sfakeur.lguhc.RoleService.RoleId;

import org.bukkit.Bukkit; // 👈 manquait
import org.bukkit.ChatColor; // 👈 manquait

import org.bukkit.GameMode;
import org.bukkit.Location;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

import org.bukkit.scheduler.BukkitRunnable;

public class NeutralHandler implements AlignHandler, org.bukkit.event.Listener {
	private final RoleService core;

	// NeutralHandler
	private static final int FDB_CENTER_RADIUS = 150; // annonce à 300 blocs du centre
	private static final int FDB_MEET_RADIUS = 10; // détection rencontre
	private static final int FDB_SNEAK_SECS = 3; // 3s accroupi
	private static final int FDB_MEET_WINDOW_S = 60; // 1 minute pour le faire
	private static final int FDB_TALK_USES = 3;
	private static final int FDB_TALK_COOLDOWN_MIN = 20; // 20 minutes
	private static final int FDB_TRACK_DURATION_S = 60; // 60s de HUD

	private static final int FDB_TALK_COOLDOWN_S = 20 * 60; // 20 minutes
	private static final int FDB_TALK_DURATION_S = 60; // 1 minute de traque HUD

	// ===== Fou du Bus constants =====
	private static final int FDB_CENTER_BOUND = 150; // square: -150..+150 on X/Z
	private static final long FDB_TRACK_MS = 60_000L; // 1 minute tracking

	private static final int FDB_TRACK_SECONDS = 60;

	private final GameManager game;

	private java.util.UUID activeHowlEmitter = null;
	private long activeHowlEndMs = 0L;

	// Durée par défaut (si tu n’en as pas déjà une ailleurs)
	private static final int HOWL_DURATION_SEC = 60;

	private org.bukkit.scheduler.BukkitTask invisParticlesTask;
	
	// tâches planifiées des vols en attente: arnacoeur -> (victime -> task)
	private final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask>>
	    arnaPending = new java.util.HashMap<>();


	public NeutralHandler(RoleService core) {
		this.core = core;
		this.game = core.getGame();
		startInvisParticleTicker();
	}

	@Override
	public void tickPerSecond(int elapsedSec) {
		boolean isDay = core.getGame().isCurrentlyDay();
		for (RoleService.RoleState s : core.getStates().values()) { /// force augmenter je crois
			if (s.roleId != RoleService.RoleId.ASSASSIN)
				continue;
			Player pl = Bukkit.getPlayer(s.owner);
			if (pl == null || !pl.isOnline())
				continue;

			if (isDay) {
				// Force I (amplifier=0)
				if (!pl.hasPotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE)) {
					pl.addPotionEffect(new org.bukkit.potion.PotionEffect(
							org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 60, 0, true, false));
				}
			} else {
				pl.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
			}
		}

		// NeutralHandler.tickPerSecond(...)
		for (RoleService.RoleState s : core.getStates().values()) {
			if (s.roleId != RoleService.RoleId.VOLEUR)
				continue;
			org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(s.owner);
			if (pl == null || !pl.isOnline() || pl.getGameMode() != org.bukkit.GameMode.SURVIVAL)
				continue;

			// Résistance I tant qu'il n'a pas volé
			if (!s.voleurStolen) {
				if (!pl.hasPotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE)) {
					pl.addPotionEffect(new org.bukkit.potion.PotionEffect(
							org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
				}
			} else {
				// une fois volé, s'assurer que la résistance a disparu
				if (pl.hasPotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE)) {
					pl.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
				}
			}
		}

		// ===== Fou du Bus : tick =====
		for (RoleService.RoleState s : core.getStates().values()) {
			if (s.roleId != RoleService.RoleId.FOU_DU_BUS)
				continue;
			Player fou = Bukkit.getPlayer(s.owner);
			if (fou == null || !fou.isOnline() || fou.getGameMode() != GameMode.SURVIVAL)
				continue;

			// 1) Annonce "arrivée au centre" (une seule fois, |x|<=300 && |z|<=300) -> A
			// TOUT LE MONDE
			if (!s.fdbCenterAnnounced) {
				Location loc = fou.getLocation();
				if (Math.abs(loc.getX()) <= FDB_CENTER_RADIUS && Math.abs(loc.getZ()) <= FDB_CENTER_RADIUS) {
					s.fdbCenterAnnounced = true;
					Bukkit.broadcastMessage(
							ChatColor.RED + "⚠ " + ChatColor.GOLD + "Le Fou du Bus vient d'arriver en ville !");

				}
			}

			// 2) Rencontres à <=10 blocs : lancer la “quest” 60s si première fois
			for (Player other : Bukkit.getOnlinePlayers()) {
				if (other.equals(fou))
					continue;
				if (!other.isOnline() || other.getGameMode() != GameMode.SURVIVAL)
					continue;
				if (other.getWorld() != fou.getWorld())
					continue;
				if (other.getLocation().distanceSquared(fou.getLocation()) > (FDB_MEET_RADIUS * FDB_MEET_RADIUS))
					continue;

				if (!s.fdbAlreadyMet.contains(other.getUniqueId())) {
					s.fdbAlreadyMet.add(other.getUniqueId());
					long deadline = System.currentTimeMillis() + (FDB_MEET_WINDOW_S * 1000L);
					s.fdbQuestDeadlineMs.put(other.getUniqueId(), deadline);
					s.fdbSneakProgressSec.put(other.getUniqueId(), 0);
				}
			}

			// 3) Progression “accroupi 3s” pour chaque joueur qui a une quest active
			if (!s.fdbQuestDeadlineMs.isEmpty()) {
				long nowMs = System.currentTimeMillis();
				java.util.List<UUID> toFail = new java.util.ArrayList<>();
				for (Map.Entry<UUID, Long> e : s.fdbQuestDeadlineMs.entrySet()) {
					UUID pid = e.getKey();
					long deadline = e.getValue();
					Player pl = Bukkit.getPlayer(pid);
					if (pl == null || !pl.isOnline()) {
						// laisse la quest courir jusqu’au deadline (on ne la drop pas)
						if (nowMs > deadline)
							toFail.add(pid);
						continue;
					}
					// avant deadline : si accroupi & proche, avancer la jauge 1s
					if (nowMs <= deadline) {
						if (pl.isSneaking() && pl.getWorld() == fou.getWorld() && pl.getLocation()
								.distanceSquared(fou.getLocation()) <= (FDB_MEET_RADIUS * FDB_MEET_RADIUS)) {
							int prog = s.fdbSneakProgressSec.getOrDefault(pid, 0) + 1;
							s.fdbSneakProgressSec.put(pid, prog);
							if (prog >= FDB_SNEAK_SECS) {
								s.fdbQuestCompleted.add(pid);
								toFail.add(pid); // pour retirer la quest maintenant
								pl.sendMessage(ChatColor.GOLD + "[Fou du Bus] " + ChatColor.GREEN
										+ "Tu as respecté la consigne.");
							}
						}
					} else {
						// deadline dépassé → échec
						toFail.add(pid);
					}
				}
				// retirer / classer en “penalty next episode”
				for (UUID pid : toFail) {
					Long dl = s.fdbQuestDeadlineMs.remove(pid);
					s.fdbSneakProgressSec.remove(pid);
					if (dl != null && !s.fdbQuestCompleted.contains(pid)) {
						s.fdbPendingPenaltyNextEpisode.add(pid); // => 2♥ au début du prochain épisode
						Player pl = Bukkit.getPlayer(pid);
						if (pl != null && pl.isOnline()) {
						}
					}
				}
			}

			// 4) HUD de traque 60s : rafraîchir compas, couper à la fin
			// 4) HUD de traque 60s : compas + action bar flèche, couper à la fin
			if (s.fdbTrackTarget != null) {
				Player tgt = Bukkit.getPlayer(s.fdbTrackTarget);
				if (tgt != null && tgt.isOnline() && tgt.getWorld() == fou.getWorld()) {
					try {
						fou.setCompassTarget(tgt.getLocation());
					} catch (Throwable ignored) {
					}
					// Envoie une flèche directionnelle + distance en action bar
					sendDirectionArrowLikePeureux(fou, tgt, "§e");
				}
				if (System.currentTimeMillis() > s.fdbTrackEndMs) {
					s.fdbTrackTarget = null;
					s.fdbTrackEndMs = 0L;
					fou.sendMessage(ChatColor.GRAY + "[Fou du Bus] Fin de la traque.");
				}
			}
		}

		boolean isNight = core.getGame().isCurrentlyNight();
		long now = System.currentTimeMillis();

		for (RoleService.RoleState s : core.getStates().values()) {
			org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
			if (p == null || !p.isOnline() || p.getGameMode() != org.bukkit.GameMode.SURVIVAL)
				continue;

			// Démarrage unique → passe par tryStartInvisibility
			tryStartInvisibility(s, p);

			// Fin (temps écoulé OU armure remise) → passe par endInvisibility
			if (s.invisActive) {
			    boolean timeExpired = (s.roleId == RoleService.RoleId.FEU_FOLLET) ? false : (now >= s.invisEndMs);
			    if (timeExpired || hasAnyArmor(p)) {
			        endInvisibility(s, p);
			    }
			}

			// Particules entre invisibles (PF voit FF/Perfide en rouge; FF/Perfide voient
			// PF en bleu)
			if (s.invisActive) {
				showInvisibleLinksOnce(); // ← affiche 1 particule sous CHAQUE invisible vu par “p”
			}

			// Petite Fille : HUD hurlement (flèche dynamique + distance) — PAS besoin
			// d’être invisible
			if (s.roleId == RoleService.RoleId.PETITE_FILLE && now < s.pfHowlHudEndMs) {
				java.util.UUID emitter = core.getWolfHandler().getActiveHowlEmitter();
				if (emitter != null) {
					org.bukkit.entity.Player wolf = org.bukkit.Bukkit.getPlayer(emitter);
					if (wolf != null && wolf.isOnline() && wolf.getWorld() == p.getWorld()) {
						try {
							p.setCompassTarget(wolf.getLocation());
						} catch (Throwable ignored) {
						}
						String arrow = dirArrow(p, wolf.getLocation());
						int dist = (int) Math.round(p.getLocation().distance(wolf.getLocation()));
						sendHurlActionBar(p, "§4Hurlement §7→ " + arrow + " §7(" + dist + "m) §f" + wolf.getName());
					}
				}
			}
		}
		///Arnacoeur
		applyArnaqueScheduled();

	}

	@Override
	public void onPlayerKill(Player killer, Player victim) {

		RoleService.RoleState ks = core.get(killer);
		if (ks != null && ks.roleId == RoleId.FOU_DU_BUS) {
			// Même épisode que /lg talk ET c'était sa cible ?
			if (ks.fdbTrackTarget != null && victim.getUniqueId().equals(ks.fdbTrackTarget)
					&& ks.fdbTrackEpisode == game.getEpisodeNumber()) {

				core.changeMaxHearts(killer, +1); // +1 cœur = +2 HP

				killer.sendMessage(ChatColor.GOLD + "[Fou du Bus] " + ChatColor.GREEN + "Tu gagnes +1♥ permanent !");
				// On termine la traque actuelle
				ks.fdbTrackTarget = null;
				ks.fdbTrackEndMs = 0L;
			}
		}
		

	}

	@Override
	public boolean handleSubCommand(String sub, Player sender, String[] args) {
		if (sub.equalsIgnoreCase("dissimuler")) {
			RoleService.RoleState st = core.get(sender);
			if (st == null || st.roleId != RoleService.RoleId.ASSASSIN)
				return false;
			if (st.assassinConcealLeft <= 0) {
				sender.sendMessage(ChatColor.RED + "Tu n’as plus de dissimulation.");
				return true;
			}

			// délégué au DeathManager
			DeathManager dm = core.getPlugin().getDeathManager(); // 👈 assure-toi d’avoir ce getter (section 5)
			boolean ok = dm.assassinTryConceal(sender.getUniqueId());
			if (!ok)
				sender.sendMessage(ChatColor.RED + "Aucune mort en attente à dissimuler.");
			else {
				st.assassinConcealLeft--;
				sender.sendMessage(ChatColor.GOLD + "[Assassin] " + ChatColor.GRAY + "Tu as dissimulé cette mort.");
			}
			return true;
		}

		if ("talk".equalsIgnoreCase(sub)) {
			RoleService.RoleState s = core.get(sender);
			if (s == null || s.roleId != RoleService.RoleId.FOU_DU_BUS) {
				sender.sendMessage(ChatColor.RED + "Tu n'es pas le Fou du Bus.");
				return true;
			}
			if (s.fdbTalkUsesLeft <= 0) {
				sender.sendMessage(ChatColor.RED + "Tu as déjà utilisé tes 3 /lg talk.");
				return true;
			}
			long now = System.currentTimeMillis();
			if (now < s.fdbTalkCooldownEndMs) {
				long sec = Math.max(1, (s.fdbTalkCooldownEndMs - now) / 1000L);
				sender.sendMessage(ChatColor.RED + "Encore " + sec + "s de cooldown.");
				return true;
			}
			if (args.length < 1) {
				sender.sendMessage(ChatColor.YELLOW + "Usage: /lg talk <pseudo>");
				return true;
			}
			Player target = Bukkit.getPlayerExact(args[0]);
			if (target == null || !target.isOnline()) {
				sender.sendMessage(ChatColor.RED + "Joueur introuvable.");
				return true;
			}
			if (target.equals(sender)) {
				sender.sendMessage(ChatColor.RED + "Impossible de te traquer toi-même.");
				return true;
			}

			// Armer la traque 60s + HUD + sauvegarder l’épisode courant
			s.fdbTrackTarget = target.getUniqueId();
			s.fdbTalkUsesLeft--;
			s.fdbTalkCooldownEndMs = now + (FDB_TALK_COOLDOWN_S * 1000L);
			s.fdbTrackEndMs = System.currentTimeMillis() + FDB_TRACK_MS; // 60s
			s.fdbTrackEpisode = core.getGame().getEpisodeNumber();
			s.fdbTrackTarget = target.getUniqueId();
			s.fdbTrackEndMs = System.currentTimeMillis() + FDB_TRACK_SECONDS * 1000L;
			s.fdbTrackEpisode = core.getGame().getEpisodeNumber();

			sender.sendMessage(ChatColor.GOLD + "[Fou du Bus] " + ChatColor.YELLOW + "Traque de " + ChatColor.WHITE
					+ target.getName() + ChatColor.YELLOW + " pendant 1 minute. Tue-le pendant cet épisode pour gagner "
					+ ChatColor.GREEN + "+1♥" + ChatColor.YELLOW + " permanent.");
			return true;
		}
		if ("folie".equalsIgnoreCase(sub)) {
			Player pl = sender;
			RoleService.RoleState s = core.get(pl);
			if (s == null || s.roleId != RoleService.RoleId.FEU_FOLLET) {
				pl.sendMessage(org.bukkit.ChatColor.RED + "Commande réservée au Feu Follet.");
				return true;
			}
			long now = System.currentTimeMillis();
			if (now < s.ffFolieCdEndMs) {
				long sec = (s.ffFolieCdEndMs - now) / 1000L;
				pl.sendMessage("§cFolie en recharge : " + sec + "s.");
				return true;
			}
			s.ffFolieEndMs = now + 60_000L; // 60s
			s.ffFolieCdEndMs = now + 10 * 60_000L; // 10 min
			// Effets (Speed I immédiat + “Fire Aspect I” = côté 1.8 on simule par FIRE_RES
			// + msg, ou tu gères via kit/arme)
			try {
				pl.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 60 * 20,
						0, true, false));
				// “Fire Aspect I 60s” côté 1.8: le plus simple est d’appliquer FIRE_RESISTANCE
				// et d’annoncer qu’il enflamme ses coups
				// Si tu gères un listener sur EntityDamageByEntity pour ce buff, mets un flag
				// (ffFolieEndMs) et applique un petit fireTicks :
				pl.sendMessage("§6Tes attaques enflammeront brièvement pendant 60s !");
			} catch (Throwable ignored) {
			}
			return true;
		}
		

	    if ("arnaquer".equalsIgnoreCase(sub)) {
	        org.bukkit.entity.Player p = sender;
	        fr.sfakeur.lguhc.RoleService.RoleState s = core.get(p);
	        if (s == null || s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.ARNACOEUR) return false;

	        long now = System.currentTimeMillis();
	        if (now < s.arnaNextStealAllowedAtMs) {
	            long left = (s.arnaNextStealAllowedAtMs - now) / 1000L;
	            p.sendMessage(org.bukkit.ChatColor.RED + "Tu dois attendre encore " + left + "s avant de pouvoir arnaquer.");
	            return true;
	        }
	        if (s.arnaHalvesTotal >= 6) {
	            p.sendMessage(org.bukkit.ChatColor.RED + "Tu as déjà volé le maximum (3 coeurs).");
	            return true;
	        }
	        if (args.length < 1) { p.sendMessage(org.bukkit.ChatColor.RED + "Usage: /lg arnaquer <pseudo>"); return true; }

	        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
	        if (target == null || !target.isOnline()) { p.sendMessage(org.bukkit.ChatColor.RED + "Joueur introuvable."); return true; }
	        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(org.bukkit.ChatColor.RED + "Pas sur toi-même."); return true; }
	        if (target.getGameMode() != org.bukkit.GameMode.SURVIVAL) { p.sendMessage(org.bukkit.ChatColor.RED + "Cible invalide."); return true; }

	        // programme le vol à +5min
	        final java.util.UUID aid = s.owner;
	        final java.util.UUID vid = target.getUniqueId();

	        // évite de programmer 2 fois le même couple (facultatif)
	        java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> byVict = arnaPending.computeIfAbsent(aid, k->new java.util.HashMap<>());
	        if (byVict.containsKey(vid)) {
	            p.sendMessage(org.bukkit.ChatColor.GRAY + "Un vol est déjà programmé sur " + target.getName() + ".");
	            return true;
	        }

	        p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Arnacœur] "
	                + org.bukkit.ChatColor.GRAY + "Tu arnaques " + org.bukkit.ChatColor.WHITE + target.getName()
	                + org.bukkit.ChatColor.GRAY + ". Le vol s’appliquera dans " + org.bukkit.ChatColor.GOLD + "5 minutes" + org.bukkit.ChatColor.GRAY + ".");

	        org.bukkit.scheduler.BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
	            @Override public void run() {
	                // re-check états
	                org.bukkit.entity.Player arn = org.bukkit.Bukkit.getPlayer(aid);
	                org.bukkit.entity.Player vic = org.bukkit.Bukkit.getPlayer(vid);
	                fr.sfakeur.lguhc.RoleService.RoleState as = core.getStates().get(aid);
	                if (arn == null || !arn.isOnline() || as == null ||
	                    as.roleId != fr.sfakeur.lguhc.RoleService.RoleId.ARNACOEUR ||
	                    arn.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
	                    // arnacœur plus là/plus vivant → on annule
	                    cleanupPending(aid, vid);
	                    return;
	                }
	                if (vic == null || !vic.isOnline() || vic.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
	                    cleanupPending(aid, vid);
	                    return;
	                }
	                if (as.arnaHalvesTotal >= 6) { cleanupPending(aid, vid); return; } // cap max

	                // applique -½♥ à la victime
	                boolean ok = addMaxHp(vic, -1.0D);
	                if (ok) {
	                    int got = as.arnaStolenHalves.getOrDefault(vid, 0) + 1;
	                    as.arnaStolenHalves.put(vid, got);
	                    as.arnaHalvesTotal++;

	                    // message Arnacœur
	                    arn.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Arnacœur] "
	                            + org.bukkit.ChatColor.GRAY + "Tu voles " + org.bukkit.ChatColor.GOLD + "½ ♥ permanent "
	                            + org.bukkit.ChatColor.GRAY + "à " + org.bukkit.ChatColor.WHITE + vic.getName()
	                            + org.bukkit.ChatColor.GRAY + " (" + (as.arnaHalvesTotal/2.0) + "♥ sur 3♥).");

	                    // message victime (+ faux suspects)
	                    java.util.List<String> suspects = fakeSuspects(aid, 2 + new java.util.Random().nextInt(2));
	                    vic.sendMessage(org.bukkit.ChatColor.RED + "Tu as été arnaqué d'un " + org.bukkit.ChatColor.GOLD + "½ ♥" + org.bukkit.ChatColor.RED + "permanent.");

	                    // bonus passifs selon total
	                    applyArnacoeurPassives(arn, as);
	                }
	                cleanupPending(aid, vid);
	            }
	        }.runTaskLater(core.getPlugin(), 5L * 60L * 20L); // 5 minutes

	        byVict.put(vid, task);
	        return true;
	    }

	    return false;

	}

	@org.bukkit.event.EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
	public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
		org.bukkit.entity.Player p = e.getEntity();
		RoleService.RoleState s = core.get(p);
		if (s != null && s.invisActive) {
			endInvisibility(s, p); // retire INVIS + buffs (PF: Weakness/Speed, Perfide: Strength, FF: Absorption)
		}	
		
	}

	@org.bukkit.event.EventHandler
	public void onGamemodeChange(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
		if (e.getNewGameMode() != org.bukkit.GameMode.SURVIVAL) {
			org.bukkit.entity.Player p = e.getPlayer();
			RoleService.RoleState s = core.get(p);
			if (s != null && s.invisActive)
				endInvisibility(s, p);
		}
	}

	@Override
	public void onEpisodeStart(int episodeNumber) {
		// ===== Fou du Bus : début d’épisode -> appliquer pénalités PERMANENTES (-2♥)
		// =====
		for (RoleService.RoleState s : core.getStates().values()) {
			if (s.roleId != RoleService.RoleId.FOU_DU_BUS)
				continue;
			if (s.fdbPendingPenaltyNextEpisode.isEmpty())
				continue;

			java.util.Set<java.util.UUID> apply = new java.util.HashSet<>(s.fdbPendingPenaltyNextEpisode);
			s.fdbPendingPenaltyNextEpisode.clear();

			for (java.util.UUID pid : apply) {
				Player pl = Bukkit.getPlayer(pid);
				if (pl == null || !pl.isOnline() || pl.getGameMode() != GameMode.SURVIVAL)
					continue;

				// −2 coeurs PERMANENTS (max health)
				boolean ok = core.changeMaxHearts(pl, -2); // -2 hearts = -4 HP
				// si le joueur est plein vie, sa vie actuelle sera clampée par
				// changeMaxHearts()
				if (ok) {
					pl.sendMessage(ChatColor.GOLD + "[Fou du Bus] " + ChatColor.RED
							+ "Tu n’as pas respecté la consigne. −2♥ permanents.");
				}
				if (ok) {
					s.fdbPenaltyHeartsLost += 2; // mémoriser la pénalité
					enforceMaxHealth(pl, s); // voir fonction ci-dessous
				}

			}

			// 2) si une traque a survécu au changement d’épisode, notifier la cible
			if (s.fdbTrackTarget != null && s.fdbTrackEpisode >= 1 && s.fdbTrackEpisode < episodeNumber) {
				Player tgt = Bukkit.getPlayer(s.fdbTrackTarget);
				if (tgt != null && tgt.isOnline()) {
					tgt.sendMessage(ChatColor.GOLD + "[Fou du Bus] " + ChatColor.YELLOW
							+ "Quelqu’un a essayé de te parler pendant l’épisode précédent...");
				}
				s.fdbTrackTarget = null;
				s.fdbTrackEndMs = 0L;
				s.fdbTrackEpisode = -1;
				stopFdbTrackingHUD(s);
			}
		}
	}

	private void announceFouArriveToHalfPlayers(Player fou) {
		java.util.List<Player> list = new java.util.ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers())
			if (p.isOnline())
				list.add(p);
		if (list.isEmpty())
			return;
		java.util.Collections.shuffle(list);
		int half = Math.max(1, list.size() / 2);
		String msg = ChatColor.GOLD + "[Annonce] " + ChatColor.YELLOW + "Le Fou du Bus arrive en ville...";
		for (int i = 0; i < half; i++) {
			try {
				list.get(i).sendMessage(msg);
			} catch (Throwable ignored) {
			}
		}
	}

	private void sendDirectionArrowLikePeureux(Player viewer, Player target, String prefixColor) {
		Location loc = viewer.getLocation();
		Location tgt = target.getLocation();
		if (!loc.getWorld().equals(tgt.getWorld())) {
			sendActionBar(viewer, "§7Cible hors-monde");
			return;
		}

		double dx = tgt.getX() - loc.getX();
		double dz = tgt.getZ() - loc.getZ();

		float absYawToTarget = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float relYaw = (absYawToTarget - loc.getYaw() + 360f) % 360f;

		String arrow;
		if (relYaw >= 337.5 || relYaw < 22.5)
			arrow = "↑";
		else if (relYaw < 67.5)
			arrow = "↗";
		else if (relYaw < 112.5)
			arrow = "→";
		else if (relYaw < 157.5)
			arrow = "↘";
		else if (relYaw < 202.5)
			arrow = "↓";
		else if (relYaw < 247.5)
			arrow = "↙";
		else if (relYaw < 292.5)
			arrow = "←";
		else
			arrow = "↖";

		int dist = (int) Math.round(loc.distance(tgt));
		sendActionBar(viewer, prefixColor + arrow + " §7(" + dist + "m) §f" + target.getName());
	}

	private void startFdbTrackingHUD(RoleService.RoleState s, Player fou) {
		// coupe un ancien HUD
		if (s.fdbHudTask != null) {
			try {
				s.fdbHudTask.cancel();
			} catch (Throwable ignored) {
			}
			s.fdbHudTask = null;
		}

		s.fdbHudTask = new BukkitRunnable() {
			@Override
			public void run() {
				if (fou == null || !fou.isOnline()) {
					cancel();
					s.fdbHudTask = null;
					return;
				}
				if (s.fdbTrackTarget == null || System.currentTimeMillis() > s.fdbTrackEndMs) {
					cancel();
					s.fdbHudTask = null;
					sendActionBar(fou, "§6➤ §7Traque terminée.");
					return;
				}
				Player tgt = Bukkit.getPlayer(s.fdbTrackTarget);
				if (tgt == null || !tgt.isOnline()) {
					sendActionBar(fou, "§6➤ §7Cible déconnectée…");
					return;
				}
				// compas + flèche
				try {
					fou.setCompassTarget(tgt.getLocation());
				} catch (Throwable ignored) {
				}
				sendDirectionArrowLikePeureux(fou, tgt, "§6➤ §e");
			}
		}.runTaskTimer(core.getPlugin(), 0L, 10L); // 0.5s
	}

	private void stopFdbTrackingHUD(RoleService.RoleState s) {
		if (s.fdbHudTask != null) {
			try {
				s.fdbHudTask.cancel();
			} catch (Throwable ignored) {
			}
			s.fdbHudTask = null;
		}
	}

	private void applyInvisBase(org.bukkit.entity.Player p) {
		safeAddPotion(p, org.bukkit.potion.PotionEffectType.INVISIBILITY, 5 * 60 * 20, 0); // durées réelles posées au
																							// déclenchement
		// Vision nocturne de nuit pour PF & Perfide : déjà dans l’énoncé “Vision
		// nocturne de nuit”
		// Tu peux gérer séparément : si isNight → add NV 30s refresh ailleurs, mais pas
		// obligatoire ici.
	}

	private void clearInvisBase(org.bukkit.entity.Player p) {
		try {
			p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
		} catch (Throwable ignored) {
		}
	}

	private void safeAddPotion(org.bukkit.entity.Player p, org.bukkit.potion.PotionEffectType t, int ticks, int amp) {
		try {
			p.addPotionEffect(new org.bukkit.potion.PotionEffect(t, ticks, amp, true, false));
		} catch (Throwable ignored) {
		}
	}

	@org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
	public void onFeuFolletFeatherUse(org.bukkit.event.player.PlayerInteractEvent e) {
		final org.bukkit.event.block.Action a = e.getAction();
		if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
				&& a != org.bukkit.event.block.Action.LEFT_CLICK_AIR
				&& a != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
			return;
		}

		final org.bukkit.entity.Player p = e.getPlayer();

		// Récupère l’item de façon robuste (1.8)
		org.bukkit.inventory.ItemStack it = e.getItem();
		if (it == null || it.getType() == org.bukkit.Material.AIR)
			it = p.getItemInHand();
		if (it == null || it.getType() != org.bukkit.Material.FEATHER)
			return;
		if (it.getItemMeta() == null || it.getItemMeta().getDisplayName() == null)
			return;
		if (!"§ePlume du Feu Follet".equals(it.getItemMeta().getDisplayName()))
			return;

		RoleService.RoleState s = core.get(p);
		if (s == null || s.roleId != RoleService.RoleId.FEU_FOLLET)
			return;

		// Anti double-fire (spam client) : ignore si <200ms
		long now = System.currentTimeMillis();
		if (now - s.ffFeatherLastUseMs < 200L) {
			e.setCancelled(true);
			return;
		}
		s.ffFeatherLastUseMs = now;

		// Cooldown / usages
		if (now < s.ffFeatherCdEndMs) {
			long sec = Math.max(1L, (s.ffFeatherCdEndMs - now) / 1000L);
			p.sendMessage("§cPlume en recharge : " + sec + "s.");
			e.setCancelled(true);
			return;
		}
		if (s.ffFeatherUsesLeft <= 0) {
			p.sendMessage("§7Tu as épuisé la plume.");
			e.setCancelled(true);
			return;
		}

		// Spot sûr dans un rayon de 50 (même monde)
		org.bukkit.Location origin = p.getLocation();
		org.bukkit.Location dest = findRandomSafeSpotAround(origin, 50, 40, true);
		if (dest == null) {
			p.sendMessage("§7Impossible de trouver un endroit sûr.");
			e.setCancelled(true);
			return;
		}

		// Empêche l’interaction du bloc (coffre/porte, etc.)
		e.setCancelled(true);

		// Particules départ
		spawnFeuFolletParticles(origin);

		// TP
		try {
			p.teleport(dest);
		} catch (Throwable ignored) {
		}

		// Particules arrivée
		spawnFeuFolletParticles(dest);

		// MAJ usages + cooldown
		s.ffFeatherUsesLeft--;
		s.ffFeatherCdEndMs = now + 10L * 60_000L; // 10 min
		p.sendMessage("§ePlume utilisée. Restant: §6" + s.ffFeatherUsesLeft);

		try {
			updateFeatherLore(it, s.ffFeatherUsesLeft);
		} catch (Throwable ignored) {
		}
	}

	// Feu Follet : pendant /lg folie, enflamme les cibles au corps-à-corps (Fire
	// Aspect I-like)
	@org.bukkit.event.EventHandler(ignoreCancelled = true)
	public void onFolletMeleeIgnite(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
		// 1.8: melee = ENTITY_ATTACK (pas de projectiles)
		if (e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK)
			return;

		if (!(e.getDamager() instanceof org.bukkit.entity.Player))
			return;
		org.bukkit.entity.Player attacker = (org.bukkit.entity.Player) e.getDamager();

		RoleService.RoleState s = core.get(attacker);
		if (s == null || s.roleId != RoleService.RoleId.FEU_FOLLET)
			return;

		long now = System.currentTimeMillis();
		if (now >= s.ffFolieEndMs)
			return; // buff /lg folie non actif → rien

		// Fire Aspect I ≈ 4s → 80 ticks. On n’écourte jamais un feu déjà plus long.
		final int FA1_TICKS = 80;

		// Victime = joueur (si tu veux enflammer les mobs aussi, switch sur
		// LivingEntity)
		if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity))
			return;
		org.bukkit.entity.LivingEntity victim = (org.bukkit.entity.LivingEntity) e.getEntity();
		int current = Math.max(victim.getFireTicks(), 0);
		victim.setFireTicks(Math.max(current, FA1_TICKS));

		// IMPORTANT : ne rien faire sur l’invis ici.
		// Pas de clear de potion, pas de s.invisActive=false, etc.
	}

	// Cherche un endroit sûr à ~rayon blocs autour d'une origine.
	// tries = nb d'essais, respectBorder = évite de sortir de la WorldBorder.
	private org.bukkit.Location findRandomSafeSpotAround(org.bukkit.Location origin, int radius, int tries,
			boolean respectBorder) {
		java.util.Random r = new java.util.Random();
		org.bukkit.World w = origin.getWorld();

		while (tries-- > 0) {
			// échantillonnage polaire (uniforme en angle & distance)
			double angle = r.nextDouble() * Math.PI * 2.0;
			double dist = r.nextDouble() * radius;
			double dx = Math.cos(angle) * dist;
			double dz = Math.sin(angle) * dist;

			int x = origin.getBlockX() + (int) Math.round(dx);
			int z = origin.getBlockZ() + (int) Math.round(dz);

			// bordure du monde
			if (respectBorder) {
				org.bukkit.WorldBorder wb = w.getWorldBorder();
				if (wb != null) {
					org.bukkit.Location test = new org.bukkit.Location(w, x + 0.5, origin.getY(), z + 0.5);
					if (!isInsideBorder(wb, test))
						continue;
				}
			}

			// Assure-toi que le chunk est chargé
			if (!w.isChunkLoaded(x >> 4, z >> 4)) {
				try {
					w.loadChunk(x >> 4, z >> 4);
				} catch (Throwable ignored) {
				}
			}

			// Position de base: le plus haut bloc + 1
			int topY = Math.max(1, w.getHighestBlockYAt(x, z));
			org.bukkit.Location cand = new org.bukkit.Location(w, x + 0.5, topY, z + 0.5);

			// Ajuste pour être safe (descend un peu si besoin)
			org.bukkit.Location safe = snapToSafeStandLocation(cand, 6);
			if (safe != null)
				return safe;
		}
		return null;
	}

	// Essaie de trouver un "stand spot" sûr en descendant jusqu'à maxDrop blocs.
	private org.bukkit.Location snapToSafeStandLocation(org.bukkit.Location start, int maxDrop) {
		org.bukkit.World w = start.getWorld();
		int x = start.getBlockX();
		int z = start.getBlockZ();

		// on teste startY, puis descend (maxDrop)
		for (int dy = 0; dy <= maxDrop; dy++) {
			int y = start.getBlockY() - dy;
			if (y < 2)
				break;

			org.bukkit.Location feet = new org.bukkit.Location(w, x + 0.5, y, z + 0.5);
			if (isSafeStandLocation(feet))
				return feet;
		}
		return null;
	}

	// Vrai si: bloc sous les pieds solide & non dangereux, pieds et tête = air (ou
	// non-solide)
	private boolean isSafeStandLocation(org.bukkit.Location feet) {
		org.bukkit.World w = feet.getWorld();
		int x = feet.getBlockX();
		int y = feet.getBlockY();
		int z = feet.getBlockZ();

		org.bukkit.Material below = w.getBlockAt(x, y - 1, z).getType();
		org.bukkit.Material feetM = w.getBlockAt(x, y, z).getType();
		org.bukkit.Material headM = w.getBlockAt(x, y + 1, z).getType();

		// bloc support sûr ?
		if (isDangerous(below) || !isSolidGround(below))
			return false;

		// espace libre pieds & tête ?
		if (!isPassable(feetM))
			return false;
		if (!isPassable(headM))
			return false;

		return true;
	}

	private boolean isSolidGround(org.bukkit.Material m) {
		// solides communs sur 1.8
		switch (m) {
		case STONE:
		case GRASS:
		case DIRT:
		case COBBLESTONE:
		case SAND:
		case SANDSTONE:
		case GRAVEL:
		case WOOD:
		case LOG:
		case LOG_2:
		case LEAVES:
		case LEAVES_2: // (feuilles: pas idéal mais OK pour poser le joueur)
		case NETHERRACK:
		case SOUL_SAND:
		case CLAY:
		case HARD_CLAY:
		case STAINED_CLAY:
		case QUARTZ_BLOCK:
		case BRICK:
		case MOSSY_COBBLESTONE:
		case COAL_BLOCK:
		case IRON_BLOCK:
		case GOLD_BLOCK:
		case DIAMOND_BLOCK:
		case EMERALD_BLOCK:
		case REDSTONE_BLOCK:
		case LAPIS_BLOCK:
		case OBSIDIAN:
		case SNOW:
		case PACKED_ICE:
		case ICE:
			return true;
		default:
			// évite l’eau/lave/air/etc.
			return m.isSolid();
		}
	}

	private boolean isDangerous(org.bukkit.Material m) {
		switch (m) {
		case LAVA:
		case STATIONARY_LAVA:
		case CACTUS:
			return true;
		default:
			return false;
		}
	}

	private boolean isPassable(org.bukkit.Material m) {
		// air/plantes/eau… (on tolère l’eau: à toi de décider; si tu veux l’éviter,
		// return false sur WATER)
		switch (m) {
		case AIR:
		case LONG_GRASS:
		case YELLOW_FLOWER:
		case RED_ROSE:
		case BROWN_MUSHROOM:
		case RED_MUSHROOM:
		case WATER:
		case STATIONARY_WATER:
		case SAPLING:
		case TORCH:
		case REDSTONE_TORCH_OFF:
		case REDSTONE_TORCH_ON:
		case VINE:
			return true;
		default:
			return !m.isSolid();
		}
	}

	// Met à jour le lore avec le nombre d’utilisations restantes
	private void updateFeatherLore(org.bukkit.inventory.ItemStack it, int remaining) {
		org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
		if (im == null)
			return;
		java.util.List<String> lore = new java.util.ArrayList<>();
		lore.add("§7Téléportation aléatoire (≤50m)");
		lore.add("§7Utilisations: §6" + remaining);
		lore.add("§7Cooldown: 10 minutes");
		im.setLore(lore);
		it.setItemMeta(im);
	}

	private void tryStartInvisibility(RoleService.RoleState s, org.bukkit.entity.Player p) {
		if (s == null || p == null || !p.isOnline() || s.invisActive)
			return;

		boolean isNight = core.getGame().isCurrentlyNight();
		long now = System.currentTimeMillis();

		// dans ton tick / tryStartInvisibility...
		if (!s.invisActive && isNight && now >= s.invisCooldownEndMs && hasNoArmor(p)) {
			if (s.roleId == RoleService.RoleId.PETITE_FILLE) {
				s.invisActive = true;
				s.invisEndMs = now + 5 * 60_000L;
				s.invisCooldownEndMs = now + 10 * 60_000L; // CD posé au démarrage (PF)
				p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
						5 * 60 * 20, 0, true, false));
				p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,
						5 * 60 * 20, 0, true, false));
				p.sendMessage("§dTu es invisible (5min).");
			} else if (s.roleId == RoleService.RoleId.LOUP_GAROU_PERFIDE) {
				s.invisActive = true;
				s.invisEndMs = now + 5 * 60_000L;
				s.invisCooldownEndMs = now + 10 * 60_000L; // CD posé au démarrage (Perfide)
				p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
						5 * 60 * 20, 0, true, false));
				p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE,
						5 * 60 * 20, 0, true, false));
				p.sendMessage("§cTu es invisible (5min).");
			}

		}
		
	    // ---- FEU FOLLET : jour ET nuit, pas de cooldown -> juste "pas d'armure"
	    if (s.roleId == RoleService.RoleId.FEU_FOLLET) {
	        if (hasNoArmor(p)) {
	            s.invisActive = true;
	            s.invisEndMs = now + 5 * 60_000L; // 5 min
	            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.INVISIBILITY, 5 * 60 * 20, 0, true, /*showParticles=*/false));
	            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.ABSORPTION, 5 * 60 * 20, 2, true, /*showParticles=*/false));
	            p.sendMessage("§6Tu es invisible (5min).");
	        }
	        return; // très important : on sort, on ne passe pas dans la logique nuit
	    }

	}

	@org.bukkit.event.EventHandler(ignoreCancelled = true)
	public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
		if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player))
			return;
		final org.bukkit.entity.Player p = (org.bukkit.entity.Player) e.getWhoClicked();

		// On s’intéresse uniquement à l’inventaire du joueur
		if (e.getClickedInventory() == null)
			return;

		// Cas 1 : on clique dans l’armure (slots d’armure 1.8 =
		// HELMET/CHEST/LEGGINGS/BOOTS)
		boolean touchesArmor = false;
		org.bukkit.inventory.ItemStack current = e.getCurrentItem();
		org.bukkit.inventory.ItemStack cursor = e.getCursor();

		// Si le slot cliqué est un slot d’armure OU si on shift-click une pièce
		// d’armure depuis l’inventaire
		org.bukkit.event.inventory.InventoryAction action = e.getAction();
		org.bukkit.event.inventory.ClickType click = e.getClick();

		// Heuristique simple : si l’item déplacé/retiré est une armure, on re-checkera
		// juste après
		if (isArmorPiece(current) || isArmorPiece(cursor) || click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
				|| click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
			touchesArmor = true;
		}

		if (!touchesArmor)
			return;

		// Une fois le clic appliqué par Spigot, on vérifie au tick suivant si l’armure
		// est bien retirée & on tente
		org.bukkit.Bukkit.getScheduler().runTask(core.getPlugin(), () -> {
		    RoleService.RoleState s = core.get(p);
		    if (s == null) return;

		    if (hasAnyArmor(p)) {
		        if (s.invisActive) endInvisibility(s, p);        // armure mise -> visible tout de suite
		    } else {
		        tryStartInvisibility(s, p);                       // plus d’armure -> (re)devient invisible
		    }
		});
	}

	private boolean isArmorPiece(org.bukkit.inventory.ItemStack it) {
		if (it == null)
			return false;
		org.bukkit.Material m = it.getType();
		// Liste des armures 1.8 (cuir/fer/or/mailles/diamant)
		switch (m) {
		case LEATHER_HELMET:
		case LEATHER_CHESTPLATE:
		case LEATHER_LEGGINGS:
		case LEATHER_BOOTS:
		case IRON_HELMET:
		case IRON_CHESTPLATE:
		case IRON_LEGGINGS:
		case IRON_BOOTS:
		case GOLD_HELMET:
		case GOLD_CHESTPLATE:
		case GOLD_LEGGINGS:
		case GOLD_BOOTS:
		case CHAINMAIL_HELMET:
		case CHAINMAIL_CHESTPLATE:
		case CHAINMAIL_LEGGINGS:
		case CHAINMAIL_BOOTS:
		case DIAMOND_HELMET:
		case DIAMOND_CHESTPLATE:
		case DIAMOND_LEGGINGS:
		case DIAMOND_BOOTS:
			return true;
		default:
			return false;
		}
	}

	// Spigot 1.8: on évite POTION_BREAK; on fait un petit nuage + signal de perle
	private void spawnFeuFolletParticles(org.bukkit.Location loc) {
		org.bukkit.World w = loc.getWorld();
		try {
			// Effet nuage/fumée
			w.spigot().playEffect(loc, org.bukkit.Effect.CLOUD, 0, 0, 0.3f, 0.3f, 0.3f, 0.01f, 40, 32);
			// Petit "pop" visuel sans son agressif
			w.spigot().playEffect(loc, org.bukkit.Effect.ENDER_SIGNAL, 0, 0, 0, 0, 0, 0, 8, 32);
		} catch (Throwable ignored) {
			// fallback si spigot().playEffect indispo
			try {
				w.playEffect(loc, org.bukkit.Effect.CLOUD, 0);
				w.playEffect(loc, org.bukkit.Effect.ENDER_SIGNAL, 0);
			} catch (Throwable ignored2) {
			}
		}
	}

	private boolean isInsideBorder(org.bukkit.WorldBorder wb, org.bukkit.Location loc) {
		if (wb == null || loc == null)
			return true; // pas de bordure = on considère OK
		org.bukkit.Location c = wb.getCenter();
		double half = wb.getSize() / 2.0; // taille = diamètre
		double dx = Math.abs(loc.getX() - c.getX());
		double dz = Math.abs(loc.getZ() - c.getZ());
		// léger “tampon” pour éviter de coller la bordure
		double eps = 0.5;
		return (dx <= half - eps) && (dz <= half - eps);
	}

	// ===== Helpers COLOURED_DUST 1.8 (couleur fixe) =====
	private static final float EPS = 1.0E-4f; // éviter 0 -> invisible

	private void showDustColor(org.bukkit.entity.Player viewer, org.bukkit.Location loc, float r, float g, float b,
			float size, int repeats) {
		org.bukkit.Location p = loc.clone().add(0.0, 0.45, 0.0); // un peu au-dessus du sol
		for (int i = 0; i < repeats; i++) {
			viewer.spigot().playEffect(p, org.bukkit.Effect.COLOURED_DUST, 0, 0, (r <= 0f ? EPS : r),
					(g <= 0f ? EPS : g), (b <= 0f ? EPS : b), size, // ≤ ~4.0 sinon la couleur varie
					0, // count=0 => couleur EXACTE, pas d’arc-en-ciel
					32);
		}
	}

	private void dustBlue(org.bukkit.entity.Player viewer, org.bukkit.Location loc) {
		showDustColor(viewer, loc, 0.1f, 0.3f, 1.0f, 3.5f, 4);
	}

	private void dustRed(org.bukkit.entity.Player viewer, org.bukkit.Location loc) {
		showDustColor(viewer, loc, 1.0f, 0.05f, 0.05f, 3.5f, 4);
	}

	private void sendDirectionActionBar(org.bukkit.entity.Player from, org.bukkit.entity.Player to) {
		org.bukkit.Location fl = from.getLocation(), tl = to.getLocation();
		if (!fl.getWorld().equals(tl.getWorld())) {
			sendActionBar(from, "§6➤ §7cible hors-monde");
			return;
		}

		double dx = tl.getX() - fl.getX();
		double dz = tl.getZ() - fl.getZ();
		float abs = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float rel = (abs - fl.getYaw() + 360f) % 360f;

		String arrow;
		if (rel >= 337.5 || rel < 22.5)
			arrow = "↑";
		else if (rel < 67.5)
			arrow = "↗";
		else if (rel < 112.5)
			arrow = "→";
		else if (rel < 157.5)
			arrow = "↘";
		else if (rel < 202.5)
			arrow = "↓";
		else if (rel < 247.5)
			arrow = "↙";
		else if (rel < 292.5)
			arrow = "←";
		else
			arrow = "↖";

		int dist = (int) Math.round(fl.distance(tl));
		sendActionBar(from, "§6Hurlement §7→ §e" + arrow + " §7(" + dist + "m) §f" + to.getName());
	}

	private void sendActionBar(org.bukkit.entity.Player player, String message) {
		try {
			Class<?> ICB = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
			Class<?> Ser = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
			Object comp = Ser.getMethod("a", String.class).invoke(null,
					"{\"text\":\"" + message.replace("\"", "\\\"") + "\"}");
			Class<?> Pkt = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");
			Object pkt = Pkt.getConstructor(ICB, byte.class).newInstance(comp, (byte) 2);
			Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Object conn = handle.getClass().getField("playerConnection").get(handle);
			Class<?> P = Class.forName("net.minecraft.server.v1_8_R3.Packet");
			conn.getClass().getMethod("sendPacket", P).invoke(conn, pkt);
		} catch (Throwable t) {
			try {
				player.sendMessage(message);
			} catch (Throwable ignored) {
			}
		}
	}

	private boolean isAir(org.bukkit.inventory.ItemStack it) {
		return it == null || it.getType() == org.bukkit.Material.AIR;
	}

	private boolean hasNoArmor(org.bukkit.entity.Player p) {
		org.bukkit.inventory.PlayerInventory inv = p.getInventory();
		return isAir(inv.getHelmet()) && isAir(inv.getChestplate()) && isAir(inv.getLeggings())
				&& isAir(inv.getBoots());
	}

	private boolean hasAnyArmor(org.bukkit.entity.Player p) {
		return !hasNoArmor(p);
	}

	// ===== Flèche 8 directions vers une cible (relative au regard du viewer) =====
	private String dirArrow(org.bukkit.entity.Player viewer, org.bukkit.Location target) {
		org.bukkit.Location loc = viewer.getLocation();
		if (!loc.getWorld().equals(target.getWorld()))
			return "§7?";
		double dx = target.getX() - loc.getX();
		double dz = target.getZ() - loc.getZ();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float rel = (yaw - loc.getYaw() + 360f) % 360f;
		if (rel >= 337.5 || rel < 22.5)
			return "§c↑";
		else if (rel < 67.5)
			return "§c↗";
		else if (rel < 112.5)
			return "§c→";
		else if (rel < 157.5)
			return "§c↘";
		else if (rel < 202.5)
			return "§c↓";
		else if (rel < 247.5)
			return "§c↙";
		else if (rel < 292.5)
			return "§c←";
		else
			return "§c↖";
	}

	// ===== Wrapper d’envoi en action-bar pour le HUD du hurlement =====
	private void sendHurlActionBar(org.bukkit.entity.Player player, String message) {
		// Si tu as déjà sendActionBar(...) dans cette classe, on le réutilise
		try {
			sendActionBar(player, "§b" + message); // léger préfixe couleur, sinon enlève "§b"
		} catch (Throwable t) {
			try {
				player.sendMessage(message);
			} catch (Throwable ignored) {
			}
		}
	}

	private void endInvisibility(fr.sfakeur.lguhc.RoleService.RoleState s, org.bukkit.entity.Player p) {
		if (!s.invisActive)
			return;
		s.invisActive = false;
		s.invisEndMs = 0L;

		// 1) enlever l’invis
		try {
			p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
		} catch (Throwable ignored) {
		}

		// 2) enlever les buffs associés au rôle
		try {
			if (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.PETITE_FILLE) {
				p.removePotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS);
				p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
			} else if (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.LOUP_GAROU_PERFIDE) {
				p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
			} else if (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.FEU_FOLLET) {
				p.removePotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION);
			}
		} catch (Throwable ignored) {
		}

		p.sendMessage("§7Tu n’es plus invisible.");
	}

	private void startInvisParticleTicker() {
		if (invisParticlesTask != null)
			return;
		invisParticlesTask = new org.bukkit.scheduler.BukkitRunnable() {
			@Override
			public void run() {
				try {
					showInvisibleLinksOnce(); // une frame de particules
				} catch (Throwable ignored) {
				}
			}
		}.runTaskTimer(core.getPlugin(), 1L, 1L); // toutes les 1 tick (~20 fps)
	}

	// Montre 1 particule sous CHAQUE invisible visible par CHAQUE viewer à <=20m
	private void showInvisibleLinksOnce() {
		// Snapshot des joueurs pour stabilité pendant l’itération
		java.util.List<org.bukkit.entity.Player> online = new java.util.ArrayList<>();
		for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers())
			if (p.isOnline())
				online.add(p);

		for (org.bukkit.entity.Player viewer : online) {
			// Viewer doit être "vivant"
			if (viewer.getGameMode() != org.bukkit.GameMode.SURVIVAL || viewer.isDead())
				continue;

			RoleService.RoleState vs = core.get(viewer);
			if (vs == null)
				continue;

			// (Optionnel) si tu as un helper d'état de vie côté UHC:
			// if (!core.getGame().isAlive(viewer.getUniqueId())) continue;

			for (org.bukkit.entity.Player other : online) {
				if (other == viewer)
					continue;

				// Cible doit être "vivante"
				if (other.getGameMode() != org.bukkit.GameMode.SURVIVAL || other.isDead())
					continue;
				// if (!core.getGame().isAlive(other.getUniqueId())) continue; // idem si tu as
				// ce helper

				RoleService.RoleState os = core.get(other);
				if (os == null || !os.invisActive)
					continue;

				if (viewer.getWorld() != other.getWorld())
					continue;
				if (viewer.getLocation().distanceSquared(other.getLocation()) > 20 * 20)
					continue;

				// PF voit FF/Perfide en ROUGE ; FF/Perfide voient PF en BLEU
				if (vs.roleId == RoleService.RoleId.PETITE_FILLE && (os.roleId == RoleService.RoleId.FEU_FOLLET
						|| os.roleId == RoleService.RoleId.LOUP_GAROU_PERFIDE)) {
					dustRed(viewer, other.getLocation());
				} else if ((vs.roleId == RoleService.RoleId.FEU_FOLLET
						|| vs.roleId == RoleService.RoleId.LOUP_GAROU_PERFIDE)
						&& os.roleId == RoleService.RoleId.PETITE_FILLE) {
					dustBlue(viewer, other.getLocation());
				}
			}
		}
	}

	private void enforceMaxHealth(org.bukkit.entity.Player p, RoleService.RoleState s) {
		if (p == null || s == null)
			return;

		double base = 20.0; // 10 coeurs vanilla

		// Bonus/malus persistants connus :
		if (s.solitaireHeartsGiven)
			base += 8.0; // +4♥ = +8 HP
		base -= s.fdbPenaltyHeartsLost * 2.0; // -1♥ = -2 HP

		// (Ajoute ici d’autres modifs “permanentes” si tu en as)

		// Clamp mini 1 coeur
		base = Math.max(2.0, base);

		// Applique si nécessaire
		if (Math.abs(p.getMaxHealth() - base) > 0.05) {
			try {
				p.setMaxHealth(base);
				if (p.getHealth() > base)
					p.setHealth(base); // clamp la vie actuelle si besoin
			} catch (Throwable ignored) {
			}
		}
	}
	
	// NeutralHandler.java
	private static final long ARNA_DELAY_MS = 5L * 60_000L;    // 5 min
	private static final long ARNA_PROTECT_AFTER_RETURN_MS = 10L * 60_000L; // 10 min

	private boolean cmdArnaquer(org.bukkit.entity.Player p, String[] args, RoleService.RoleState s) {
	    if (args.length < 1) { p.sendMessage("§d[Arnacoeur] §7Usage: §f/lg arnaquer <pseudo>"); return true; }
	    if (s.arnaTotalHalvesStolen >= 6) { // 3 coeurs max
	        p.sendMessage("§d[Arnacoeur] §7Tu as déjà volé §63♥§7 (maximum).");
	        return true;
	    }

	    org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
	    if (target == null || !target.isOnline()) { p.sendMessage("§cJoueur introuvable."); return true; }
	    if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage("§cPas sur toi-même."); return true; }
	    if (target.getGameMode() != org.bukkit.GameMode.SURVIVAL) { p.sendMessage("§cCible inéligible."); return true; }

	    // protection (si cœurs restitués à cause d’une mort récente de l’Arnacoeur)
	    if (s.arnaRecentlyRestored.contains(target.getUniqueId())) {
	        p.sendMessage("§d[Arnacoeur] §7Cette cible est protégée momentanément (retour récent).");
	        return true;
	    }

	    // place une “charge” (½♥) sur la cible à +5 min
	    long now = System.currentTimeMillis();
	    s.arnaQueuedAtMs.put(target.getUniqueId(), now);
	    s.arnaFireAtMs.put(target.getUniqueId(), now + ARNA_DELAY_MS);

	    p.sendMessage("§d[Arnacoeur] §7Tu as programmé un vol de §l½♥§7 sur §f" + target.getName() + "§7 (dans 5 minutes).");
	    try { p.playSound(p.getLocation(), org.bukkit.Sound.ORB_PICKUP, 1f, 1.4f); } catch (Throwable ignored) {}

	    // Une seule “charge” posée par commande -> le tick par seconde appliquera à échéance.
	    return true;
	}
	
	private void applyArnaqueScheduled() {
	    long now = System.currentTimeMillis();
	    for (RoleService.RoleState s : core.getStates().values()) {
	        if (s.roleId != RoleService.RoleId.ARNACOEUR) continue;

	        java.util.List<java.util.UUID> toFire = new java.util.ArrayList<>();
	        for (java.util.Map.Entry<java.util.UUID, Long> e : s.arnaFireAtMs.entrySet()) {
	            if (now >= e.getValue()) toFire.add(e.getKey());
	        }
	        if (toFire.isEmpty()) continue;

	        for (java.util.UUID tid : toFire) {
	            s.arnaQueuedAtMs.remove(tid);
	            s.arnaFireAtMs.remove(tid);

	            if (s.arnaTotalHalvesStolen >= 6) continue; // sécurité cap

	            org.bukkit.entity.Player victim = org.bukkit.Bukkit.getPlayer(tid);
	            if (victim == null || !victim.isOnline() || victim.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

	            // applique -½♥ permanent (−1.0 HP)
	            boolean ok = addMaxHp(victim, -1.0D);
	            if (!ok) continue;

	            // book-keeping
	            s.arnaTotalHalvesStolen++;
	            s.arnaVictimHalves.put(tid, 1 + s.arnaVictimHalves.getOrDefault(tid, 0));

	            // message victime + “liste blanche” (2 à 3 pseudos qui ne sont PAS l’Arnacoeur)
	            sendMisleadListToVictim(victim, s.owner);

	            try { victim.playSound(victim.getLocation(), org.bukkit.Sound.ZOMBIE_WOOD, 1f, 0.7f); } catch (Throwable ignored) {}
	        }

	        // Si on vient d’atteindre 3♥ volés → poser Speed I permanent (sans particules)
	        if (s.arnaTotalHalvesStolen >= 6 && !s.arnaPermanentSpeedGiven) {
	            s.arnaPermanentSpeedGiven = true;
	            org.bukkit.entity.Player arna = org.bukkit.Bukkit.getPlayer(s.owner);
	            if (arna != null && arna.isOnline()) {
	                arna.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.SPEED, /*dur*/ 9_999_999, /*amp*/ 0,
	                    /*ambient*/ true, /*particles*/ false));
	                arna.sendMessage("§d[Arnacoeur] §7Tu as volé §63♥§7 : §aVitesse I permanente§7.");
	            }
	        }
	    }
	}
	
	/** +/− HP max “safe” (1.8), clamp et garde la vie actuelle dans [0..max]. */
	private boolean addMaxHp(org.bukkit.entity.Player p, double deltaHp) {
	    try {
	        double cur = p.getMaxHealth();
	        double nw  = Math.max(1.0D, Math.min(40.0D, cur + deltaHp)); // borne [0.5♥ ; 20♥]
	        if (Math.abs(nw - cur) < 0.001) return false;
	        p.setMaxHealth(nw);
	        if (p.getHealth() > nw) p.setHealth(nw);
	        return true;
	    } catch (Throwable t) { return false; }
	}


	private void sendMisleadListToVictim(org.bukkit.entity.Player victim, java.util.UUID arnaId) {
	    // 2 à 3 pseudos parmi les vivants ≠ Arnacoeur ≠ victime
	    java.util.List<String> pool = new java.util.ArrayList<>();
	    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
	        if (!pl.isOnline() || pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
	        if (pl.getUniqueId().equals(arnaId)) continue;
	        if (pl.getUniqueId().equals(victim.getUniqueId())) continue;
	        pool.add(pl.getName());
	    }
	    java.util.Collections.shuffle(pool);
	    int n = Math.min(pool.size(), 2 + new java.util.Random().nextInt(2)); // 2 ou 3
	    java.util.List<String> pick = pool.subList(0, n);

	    victim.sendMessage("§d[Arnacoeur] §7Tu perds §l½♥§7 permanent.");
	    if (!pick.isEmpty()) {
	        victim.sendMessage("§7Suspects potentiels (pas l’Arnacoeur) : §f" + String.join(", ", pick));
	    }
	}
	
	private void cleanupPending(java.util.UUID arnId, java.util.UUID vicId) {
	    java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> m = arnaPending.get(arnId);
	    if (m == null) return;
	    org.bukkit.scheduler.BukkitTask t = m.remove(vicId);
	    if (t != null) try { t.cancel(); } catch (Throwable ignored) {}
	    if (m.isEmpty()) arnaPending.remove(arnId);
	}

	/** Renvoie une liste de pseudos (2..3) ne contenant PAS l’Arnacœur réel. */
	private java.util.List<String> fakeSuspects(java.util.UUID exclude, int count) {
	    java.util.List<String> pool = new java.util.ArrayList<>();
	    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
	        if (!pl.isOnline()) continue;
	        if (pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
	        if (pl.getUniqueId().equals(exclude)) continue;
	        pool.add(pl.getName());
	    }
	    java.util.Collections.shuffle(pool);
	    if (pool.size() > count) pool = pool.subList(0, count);
	    return pool;
	}

	/** Applique les bonus “passifs” du total : 1♥ → buff post-kill ; 2♥ → +Absorption ; 3♥ → Vitesse I permanente. */
	private void applyArnacoeurPassives(org.bukkit.entity.Player arn, fr.sfakeur.lguhc.RoleService.RoleState s) {
	    // 3♥ (6 moitiés) => Vitesse I permanente
	    if (s.arnaHalvesTotal >= 6) {
	        if (!arn.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED)) {
	            arn.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                    org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
	        }
	    }
	}
	
	// NeutralHandler.java
	public void onArnacoeurDefinitiveDeath(java.util.UUID deadId) {
	    fr.sfakeur.lguhc.RoleService.RoleState s = core.getStates().get(deadId);
	    if (s == null || s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.ARNACOEUR) return;

	    // 1) Annule tous les vols en attente pour cet Arnacœur
	    java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> m = arnaPending.remove(s.owner);
	    if (m != null) for (org.bukkit.scheduler.BukkitTask t : m.values()) try { t.cancel(); } catch (Throwable ignored) {}

	    // 2) Restitue tous les ½♥ volés à chaque victime
	    for (java.util.Map.Entry<java.util.UUID,Integer> en : s.arnaStolenHalves.entrySet()) {
	        java.util.UUID vid = en.getKey();
	        int halves = en.getValue();
	        if (halves <= 0) continue;

	        org.bukkit.entity.Player v = org.bukkit.Bukkit.getPlayer(vid);
	        if (v != null && v.isOnline()) {
	            boolean ok = addMaxHp(v, +1.0D * halves);
	            if (ok) {
	                v.sendMessage(org.bukkit.ChatColor.GREEN + "Tes " + (halves/2.0) + "♥ t’ont été restitués (mort définitive de l’Arnacœur).");
	            }
	        }
	    }
	    s.arnaStolenHalves.clear();
	    s.arnaHalvesTotal = 0;

	    // retire Vitesse permanente si posée
	    try {
	        org.bukkit.entity.Player dead = org.bukkit.Bukkit.getPlayer(deadId);
	        if (dead != null) dead.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
	    } catch (Throwable ignored) {}

	    // 3) Cooldown 10 minutes avant de pouvoir re-arnaquer s’il revient d’une manière ou d’une autre
	    s.arnaNextStealAllowedAtMs = System.currentTimeMillis() + 10L * 60_000L;
	}





}
