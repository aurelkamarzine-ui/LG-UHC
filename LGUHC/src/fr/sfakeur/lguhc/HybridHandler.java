package fr.sfakeur.lguhc;

import org.bukkit.Bukkit;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class HybridHandler implements AlignHandler, org.bukkit.event.Listener {
	private final RoleService core;
	
	// HybridHandler.java (champs)
	private boolean coupleAnnounced = false;

	// public pour que RoleService puisse tester
	public boolean isCoupleAnnounced() { return coupleAnnounced; }


	public HybridHandler(RoleService core) {
		this.core = core;
	}

	// 5 minutes
	private static final long LOVE_WINDOW_MS = 5L * 60L * 1000L;

	// Util: état joueur
	private RoleService.RoleState st(Player p) {
		return core.get(p);
	}

	@Override
	public void tickPerSecond(int elapsedSec) {
		// 1) Couple aléatoire: si activé → désactive /lg love, et
		// Cupidon sera informé 5 minutes après le début de l’épisode 3.
		if (core.getGame().isRandomCoupleEnabled()) {
			for (RoleService.RoleState s : core.getStates().values()) {
				if (s.roleId != RoleService.RoleId.CUPIDON)
					continue;
				s.loveRandomMode = true;

				int ep = core.getGame().getEpisodeNumber();
				// fixe le couple si pas fixé au début de l’épisode 2
				if (ep >= 2 && !s.loveFixed) {
					// tire 2 vivants aléatoires
					java.util.List<Player> alive = new java.util.ArrayList<>();
					for (RoleService.RoleState t : core.getStates().values()) {
						Player pl = org.bukkit.Bukkit.getPlayer(t.owner);
						if (pl != null && pl.isOnline() && pl.getGameMode() == org.bukkit.GameMode.SURVIVAL)
							alive.add(pl);
					}
					if (alive.size() >= 2) {
						java.util.Collections.shuffle(alive);
						Player a = alive.get(0), b = alive.get(1);
						if (core.setCouple(a, b)) {
							s.loveFixed = true;
							// Cupidon connaîtra le couple 5 min après **épisode 3**
							// => on attend juste l’épisode 3 + 5 min (ci-dessous)
						}
					}
				}
				// révélation à Cupidon 5 minutes après épisode 3
				if (s.loveFixed && !s.loveRevealedToCupid && ep >= 3) {
					int epLenSec = Math.max(60, core.getGame().getUhcConfig().getEpisodeMinutes() * 60);
					int secSinceEp3 = elapsedSec - (2 * epLenSec); // temps écoulé depuis début ép3
					if (secSinceEp3 >= 5 * 60) {
						Player cd = org.bukkit.Bukkit.getPlayer(s.owner);
						if (cd != null && cd.isOnline()) {
							Player A = (s.lovePickA != null ? org.bukkit.Bukkit.getPlayer(s.lovePickA) : null);
							Player B = (s.lovePickB != null ? org.bukkit.Bukkit.getPlayer(s.lovePickB) : null);
							cd.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Cupidon] " + org.bukkit.ChatColor.GRAY
									+ "Le couple est : " + org.bukkit.ChatColor.WHITE + (A != null ? A.getName() : "?")
									+ org.bukkit.ChatColor.GRAY + " ♥ " + org.bukkit.ChatColor.WHITE
									+ (B != null ? B.getName() : "?") + org.bukkit.ChatColor.GRAY + ".");
							s.loveRevealedToCupid = true;
						}
					}
				}
			}
		} else {
			// Mode manuel: si Cupidon a sélectionné, mais pas "fixé" (option), fixe au
			// timeout
			for (RoleService.RoleState s : core.getStates().values()) {
				if (s.roleId != RoleService.RoleId.CUPIDON)
					continue;
				if (!s.loveFixed && s.loveDeadlineMs > 0 && System.currentTimeMillis() >= s.loveDeadlineMs) {
					// pas de choix -> aléa parmi vivants
					java.util.List<Player> alive = new java.util.ArrayList<>();
					for (RoleService.RoleState t : core.getStates().values()) {
						Player pl = org.bukkit.Bukkit.getPlayer(t.owner);
						if (pl != null && pl.isOnline() && pl.getGameMode() == org.bukkit.GameMode.SURVIVAL)
							alive.add(pl);
					}
					if (alive.size() >= 2) {
						java.util.Collections.shuffle(alive);
						Player a = alive.get(0), b = alive.get(1);
						if (core.setCouple(a, b)) {
							s.loveFixed = true;
							announceCoupleDelayed(a, b, 5 * 60); // annonce aux amoureux dans 5 min
						}
					}
					s.loveDeadlineMs = 0L;
				}
			}
		}

		// Boussole des amoureux jusqu’à retrouvaille (≤ 5 blocs)
		for (RoleService.RoleState s : core.getStates().values()) {
			if (s.lover == null || s.loverMet == true)
				continue;

			org.bukkit.entity.Player me = org.bukkit.Bukkit.getPlayer(s.owner);
			org.bukkit.entity.Player luv = org.bukkit.Bukkit.getPlayer(s.lover);
			if (me == null || luv == null || !me.isOnline() || !luv.isOnline())
				continue;

			// garder la boussole pointée
			try {
				me.setCompassTarget(luv.getLocation());
			} catch (Throwable ignored) {
			}

			if (me.getWorld().equals(luv.getWorld()) && me.getLocation().distanceSquared(luv.getLocation()) <= 25.0) {
				// verrou côté A
				s.loverMet = true;
				// verrou côté B
				RoleService.RoleState sl = core.get(luv);
				if (sl != null)
					sl.loverMet = true;

				// message 1x chacun
				me.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Vous avez retrouvé votre âme soeur !");
				luv.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Vous avez retrouvé votre âme soeur !");

				// boussole “normale” : vers le spawn (nord)
				org.bukkit.Location spawn = me.getWorld().getSpawnLocation();
				try {
					me.setCompassTarget(spawn);
				} catch (Throwable ignored) {
				}
				try {
					luv.setCompassTarget(spawn);
				} catch (Throwable ignored) {
				}
			}
		}

	}

	@Override
	public void onPlayerKill(Player killer, Player victim) {
	}

	@Override
	public boolean handleSubCommand(String sub, Player sender, String[] args) {
		// /lg don : accessible à tout joueur amoureux (peu importe le rôle)
		if (sub.equalsIgnoreCase("don")) {
			return cmdCoupleDon(sender, args, core.get(sender));
		}
		// /lg love : réservé au Cupidon
		if (sub.equalsIgnoreCase("love")) {
			return cmdCupidonLove(sender, args, core.get(sender));
		}
		return false;

	}

	@Override
	public void onPlayerDeath(org.bukkit.entity.Player dead) {
		RoleService.RoleState sd = core.get(dead);
		if (sd == null || sd.lover == null)
			return;

		org.bukkit.entity.Player lover = org.bukkit.Bukkit.getPlayer(sd.lover);
		if (lover == null || !lover.isOnline())
			return;

		RoleService.RoleState sl = core.get(lover);
		if (sl != null && !sl.loverDoomed) {
			sl.loverDoomed = true; // anti-boucle (évite ping-pong infini)

			// 1) On marque l’autre amoureux pour une finalisation instant + message
			// “chagrin”
			fr.sfakeur.lguhc.DeathManager dm = core.getPlugin().getDeathManager();
			if (dm != null)
				dm.markLoverInstantFinalize(lover.getUniqueId());

			// 2) On inflige des dégâts le tick suivant pour déclencher PlayerDeathEvent
			new org.bukkit.scheduler.BukkitRunnable() {
				@Override
				public void run() {
					try {
						if (!lover.isDead() && lover.getHealth() > 0.0) {
							lover.damage(1000.0, dead); // l’event sera intercepté par DeathManager
						}
					} catch (Throwable ignored) {
					}
				}
			}.runTask(core.getPlugin());
		}
	}

	@Override
	public void onEpisodeStart(int episodeNumber) {
		if (episodeNumber != 2)
			return;

		for (RoleService.RoleState s : core.getStates().values()) {
			if (s.roleId != RoleService.RoleId.CUPIDON || s.loveFixed)
				continue;

			// Fenêtre de 5 min pour /lg love
			s.loveDeadlineMs = System.currentTimeMillis() + 5L * 60L * 1000L;
			org.bukkit.entity.Player cupi = org.bukkit.Bukkit.getPlayer(s.owner);
			if (cupi != null && cupi.isOnline()) {
				cupi.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Cupidon] " + org.bukkit.ChatColor.GRAY + "Tu as "
						+ org.bukkit.ChatColor.GOLD + "5 minutes" + org.bukkit.ChatColor.GRAY
						+ " pour choisir deux joueurs avec " + org.bukkit.ChatColor.WHITE
						+ "/lg love <pseudoA> <pseudoB>" + org.bukkit.ChatColor.GRAY + ".");
			}

			// À +5 min: si pas choisi, couple aléatoire; puis ANNONCE AUX AMOUREUX +
			// boussoles
			new org.bukkit.scheduler.BukkitRunnable() {
				@Override
				public void run() {
					if (!s.loveFixed) {
						java.util.List<org.bukkit.entity.Player> alive = new java.util.ArrayList<>();
						for (RoleService.RoleState t : core.getStates().values()) {
							org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(t.owner);
							if (pl != null && pl.isOnline() && pl.getGameMode() == org.bukkit.GameMode.SURVIVAL)
								alive.add(pl);
						}
						if (alive.size() >= 2) {
							java.util.Collections.shuffle(alive);
							org.bukkit.entity.Player a = alive.get(0), b = alive.get(1);
							if (core.setCouple(a, b))
								s.loveFixed = true;
							announceCoupleNow(a, b); // ← message + boussoles + loverAnnounced=true
						}
					} else {
						// Couple déjà fixé manuellement → annonce maintenant
						org.bukkit.entity.Player a = org.bukkit.Bukkit.getPlayer(s.lovePickA);
						org.bukkit.entity.Player b = org.bukkit.Bukkit.getPlayer(s.lovePickB);
						announceCoupleNow(a, b);
					}
				}
			}.runTaskLater(core.getPlugin(), 5L * 60L * 20L);
		}
	}

	private boolean cmdCupidonLove(org.bukkit.entity.Player p, String[] args, RoleService.RoleState cu) {
		if (cu == null || cu.roleId != RoleService.RoleId.CUPIDON)
			return false;

		if (cu.loveFixed) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Le couple a déjà été formé.");
			return true;
		}
		int ep = core.getGame().getEpisodeNumber();
		if (ep < 2) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Tu pourras former le couple à partir de l’épisode 2.");
			return true;
		}
		if (args.length < 2) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Usage: /lg love <pseudoA> <pseudoB>");
			return true;
		}

		org.bukkit.entity.Player a = org.bukkit.Bukkit.getPlayerExact(args[0]);
		org.bukkit.entity.Player b = org.bukkit.Bukkit.getPlayerExact(args[1]);
		if (a == null || b == null || !a.isOnline() || !b.isOnline()) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Joueurs introuvables ou hors-ligne.");
			return true;
		}
		if (a.getUniqueId().equals(b.getUniqueId())) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Les deux cibles doivent être différentes.");
			return true;
		}

		// ... validations faites avant
		cu.lovePickA = a.getUniqueId();
		cu.lovePickB = b.getUniqueId();
		// on fige le couple dès maintenant (mais on n’annonce pas)
		if (core.setCouple(a, b)) {
			cu.loveFixed = true;
			cu.loveDeadlineMs = 0L; // plus de deadline
			p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Cupidon] " + org.bukkit.ChatColor.GRAY + "Tu as uni "
					+ a.getName() + " ♥ " + b.getName() + ".");
		} else {
			p.sendMessage(org.bukkit.ChatColor.RED + "Impossible de former le couple (rôles non attribués ?).");
		}
		return true;

	}

	private boolean cmdCoupleDon(org.bukkit.entity.Player p, String[] args, RoleService.RoleState s) {
		if (s == null || s.lover == null) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Tu n’es pas en couple.");
			return true;
		}
		// ⛔ interdit avant l’annonce EP2+5min
		if (!s.loverAnnounced) {
			p.sendMessage(org.bukkit.ChatColor.RED
					+ "Tu ne peux donner de la vie qu’après l’annonce du couple (Épisode 2 + 5 min).");
			return true;
		}

		if (args.length < 1) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Usage: /lg don <pourcentage 1..99>");
			return true;
		}

		int pct;
		try {
			pct = Integer.parseInt(args[0]);
		} catch (Exception e) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Pourcentage invalide.");
			return true;
		}
		if (pct < 1 || pct > 99) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Pourcentage 1..99.");
			return true;
		}

		org.bukkit.entity.Player lover = org.bukkit.Bukkit.getPlayer(s.lover);
		if (lover == null || !lover.isOnline()) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Ton amoureux(se) est hors-ligne.");
			return true;
		}

		// 1% = 0,1 ♥ = 0,2 HP
		double donorAmtHP = pct * 0.2D;

		// Ne pas mourir en donnant : on doit garder > 0 HP (on laisse 1.0 HP mini)
		double maxGiveHP = Math.max(0.0D, p.getHealth() - 1.0D);
		if (donorAmtHP > maxGiveHP)
			donorAmtHP = maxGiveHP;

		if (donorAmtHP <= 0.0D) {
			p.sendMessage(org.bukkit.ChatColor.RED + "Tu n’as pas assez de vie pour donner.");
			return true;
		}

		// Le lover doit pouvoir recevoir TOUTE la quantité
		double canReceiveHP = lover.getMaxHealth() - lover.getHealth();
		if (canReceiveHP + 1e-6 < donorAmtHP) {
			p.sendMessage(org.bukkit.ChatColor.YELLOW + "Ton amoureux n’a pas besoin d’autant de vie.");
			return true;
		}

		// Transfert
		p.setHealth(Math.max(1.0D, p.getHealth() - donorAmtHP));
		lover.setHealth(Math.min(lover.getMaxHealth(), lover.getHealth() + donorAmtHP));

		// Affichage en cœurs (HP/2)
		double hearts = donorAmtHP / 2.0D;
		String heartsStr = String.format(java.util.Locale.ROOT, "%.1f", hearts);

		p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Couple] " + org.bukkit.ChatColor.GRAY + "Tu as donné "
				+ org.bukkit.ChatColor.GOLD + heartsStr + "♥" + org.bukkit.ChatColor.GRAY + " à "
				+ org.bukkit.ChatColor.WHITE + lover.getName() + org.bukkit.ChatColor.GRAY + ".");
		lover.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Couple] " + org.bukkit.ChatColor.WHITE + p.getName()
				+ org.bukkit.ChatColor.GRAY + " t’a offert " + org.bukkit.ChatColor.GOLD + heartsStr + "♥"
				+ org.bukkit.ChatColor.GRAY + ".");
		return true;
	}

	private void announceCoupleDelayed(org.bukkit.entity.Player a, org.bukkit.entity.Player b, int delaySec) {
	    if (a == null || b == null) return;
	    new org.bukkit.scheduler.BukkitRunnable() {
	        @Override public void run() {
	            announceCoupleNow(a, b); // ← pose loverAnnounced + markCoupleAnnouncedNow + messages
	        }
	    }.runTaskLater(core.getPlugin(), delaySec * 20L);
	}



	public synchronized void announceCoupleNow(org.bukkit.entity.Player a, org.bukkit.entity.Player b) {
	    if (a == null || b == null) return;

	    RoleService.RoleState sa = core.get(a);
	    RoleService.RoleState sb = core.get(b);
	    if (sa == null || sb == null) return;

	    // sécurité : vérifier que ce sont bien des amoureux réciproques
	    if (sa.lover == null || sb.lover == null) return;
	    if (!sa.lover.equals(sb.owner) || !sb.lover.equals(sa.owner)) return;

	    // 🔓 déverrouille globalement le /lg don
	    core.markCoupleAnnouncedNow();

	    // --- messages côté A → B (une seule fois) ---
	    if (!sa.loverAnnounced) {
	        a.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Vous êtes amoureux de " 
	                + org.bukkit.ChatColor.WHITE + b.getName()
	                + org.bukkit.ChatColor.LIGHT_PURPLE + " ! " 
	                + org.bukkit.ChatColor.GRAY
	                + "Si l’un meurt, l’autre le rejoindra par amour. "
	                + "Vous pouvez envoyer de la vie avec /don <pourcentage>.");
	        sa.loverAnnounced = true; // ✅ ne sera plus réannoncé
	    }

	    // --- messages côté B → A (une seule fois) ---
	    if (!sb.loverAnnounced) {
	        b.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Vous êtes amoureux de " 
	                + org.bukkit.ChatColor.WHITE + a.getName()
	                + org.bukkit.ChatColor.LIGHT_PURPLE + " ! " 
	                + org.bukkit.ChatColor.GRAY
	                + "Si l’un meurt, l’autre le rejoindra par amour. "
	                + "Vous pouvez envoyer de la vie avec /don <pourcentage>.");
	        sb.loverAnnounced = true; // ✅ idem
	    }

	    // boussoles + équipement (sans impact sur les doublons de message)
	    try { a.setCompassTarget(b.getLocation()); } catch (Throwable ignored) {}
	    try { b.setCompassTarget(a.getLocation()); } catch (Throwable ignored) {}
	    giveCompassIfMissing(a);
	    giveCompassIfMissing(b);
	}



	private void giveCompassIfMissing(org.bukkit.entity.Player p) {
		boolean has = false;
		for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
			if (it != null && it.getType() == org.bukkit.Material.COMPASS) {
				has = true;
				break;
			}
		}
		if (!has) {
			p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS, 1));
			p.updateInventory();
		}
	}

	// Donne une boussole si le joueur n’en a pas déjà une
	private void giveCompassOnce(org.bukkit.entity.Player p) {
		try {
			boolean has = false;
			for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
				if (it != null && it.getType() == org.bukkit.Material.COMPASS) {
					has = true;
					break;
				}
			}
			if (!has) {
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS, 1));
				p.updateInventory();
			}
		} catch (Throwable ignored) {
		}
	}

	@org.bukkit.event.EventHandler(ignoreCancelled = true)
	public void onCupidonV2Arrow(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
		if (!(e.getEntity() instanceof org.bukkit.entity.Player))
			return;

		org.bukkit.entity.Player target = (org.bukkit.entity.Player) e.getEntity();

		// flèche ?
		org.bukkit.entity.Entity damager = e.getDamager();
		if (!(damager instanceof org.bukkit.entity.Projectile))
			return;
		org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) damager;
		if (!(proj instanceof org.bukkit.entity.Arrow))
			return;

		// tireur = Player ?
		org.bukkit.projectiles.ProjectileSource src = proj.getShooter();
		if (!(src instanceof org.bukkit.entity.Player))
			return;
		org.bukkit.entity.Player shooter = (org.bukkit.entity.Player) src;

		RoleService.RoleState cup = core.get(shooter);
		if (cup == null || cup.roleId != RoleService.RoleId.CUPIDON_V2)
			return;

		// déjà fixé ?
		if (cup.loveFixed)
			return; // déjà 2 choisis

		// 1ère ou 2ème cible (il peut se choisir lui-même)
		if (cup.lovePickA == null) {
			cup.lovePickA = target.getUniqueId();
			shooter.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "[Cupidon v2] " + org.bukkit.ChatColor.GRAY
					+ "Première cible marquée : " + org.bukkit.ChatColor.WHITE + target.getName());
		} else if (cup.lovePickB == null) {
			if (target.getUniqueId().equals(cup.lovePickA)) {
				shooter.sendMessage(org.bukkit.ChatColor.RED + "Tu dois choisir deux personnes distinctes.");
				return;
			}
			cup.lovePickB = target.getUniqueId();

			// On a A et B → fixe le couple
			org.bukkit.entity.Player A = org.bukkit.Bukkit.getPlayer(cup.lovePickA);
			org.bukkit.entity.Player B = org.bukkit.Bukkit.getPlayer(cup.lovePickB);
			if (A != null && B != null) {
				applyCupidV2Couple(cup, A, B);
			}
		}
	}

	/**
	 * Force la désactivation de l’event Couple Aléatoire (menu = 0%, moteur OFF).
	 */
	private void disableRandomCoupleEvent() {
		try {
			// 1) côté moteur de game
			core.getGame().setRandomCoupleEnabled(false);
		} catch (Throwable ignored) {
		}

		try {
			// 2) côté menu/config (LGUHC) si tu stockes les % par nom
			fr.sfakeur.lguhc.LGUHC main = core.getPlugin();
			if (main != null) {
				main.randomEventPct.put("Couple Aléatoire", 0);
			}

		} catch (Throwable ignored) {
		}

	}

	/**
	 * Appelé quand Cupidon v2 a touché 2 cibles → fixe le couple et arme ses flags.
	 */
	private void applyCupidV2Couple(RoleService.RoleState cup, org.bukkit.entity.Player a, org.bukkit.entity.Player b) {
		if (cup == null || a == null || b == null)
			return;

		// fixe le couple via ton moteur existant
		boolean ok = core.setCouple(a, b);
		if (!ok)
			return;

		// marqueur côté Cupidon v2
		cup.cupid2Maker = true;
		cup.cupid2A = a.getUniqueId();
		cup.cupid2B = b.getUniqueId();
		cup.cupid2CoupleAlive = true;

		// “Les 2 villageois ?” → Cupidon v2 reste Village (comme le v1)
		RoleService.RoleState sa = core.get(a);
		RoleService.RoleState sb = core.get(b);
		boolean bothVillage = (sa != null && sa.align == RoleService.Align.VILLAGE)
				&& (sb != null && sb.align == RoleService.Align.VILLAGE);
		cup.cupid2CoupleBothVillage = bothVillage;

		// scelle la liaison
		cup.loveFixed = true;
		cup.lovePickA = a.getUniqueId();
		cup.lovePickB = b.getUniqueId();

		// message aux lovers
		try {
			a.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Vous êtes maintenant en Couple avec "
					+ org.bukkit.ChatColor.WHITE + b.getName() + org.bukkit.ChatColor.LIGHT_PURPLE + ".");
		} catch (Throwable ignored) {
		}
		try {
			b.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Vous êtes maintenant en Couple avec "
					+ org.bukkit.ChatColor.WHITE + a.getName() + org.bukkit.ChatColor.LIGHT_PURPLE + ".");
		} catch (Throwable ignored) {
		}

		// message à Cupidon v2
		org.bukkit.entity.Player cupPl = org.bukkit.Bukkit.getPlayer(cup.owner);
		if (cupPl != null && cupPl.isOnline()) {
			cupPl.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "[Cupidon v2] " + org.bukkit.ChatColor.GRAY
					+ "Couple fixé : " + org.bukkit.ChatColor.WHITE + a.getName() + org.bukkit.ChatColor.GRAY + " ♥ "
					+ org.bukkit.ChatColor.WHITE + b.getName() + org.bukkit.ChatColor.GRAY + ".");

		}

		// (optionnel) désactive l’event Couple Aléatoire s’il était actif
		try {
			core.getGame().setRandomCoupleEnabled(false);
		} catch (Throwable ignored) {
		}
		try {
			core.getPlugin().setRandomEventPercent("Couple Aléatoire", 0);
		} catch (Throwable ignored) {
		}
	}

	/** Si le couple v2 meurt (un des deux), Cupidon v2 revient Village. */
	public void handleCupidV2CoupleDeath(java.util.UUID dead) {
		if (dead == null)
			return;
		for (RoleService.RoleState s : core.getStates().values()) {
			if (s.roleId != RoleService.RoleId.CUPIDON_V2)
				continue;
			if (!s.cupid2Maker || !s.cupid2CoupleAlive)
				continue;
			if (dead.equals(s.cupid2A) || dead.equals(s.cupid2B)) {
				s.cupid2CoupleAlive = false; // => gagne à nouveau avec le Village
				org.bukkit.entity.Player cupPl = org.bukkit.Bukkit.getPlayer(s.owner);
				if (cupPl != null && cupPl.isOnline()) {
					cupPl.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "[Cupidon v2] " + org.bukkit.ChatColor.GRAY
							+ "Ton couple est brisé : tu rejoins le " + org.bukkit.ChatColor.GREEN + "Village"
							+ org.bukkit.ChatColor.GRAY + ".");
				}
			}
		}
	}
	
	// HybridHandler.java
	public void resetCoupleState() {
	    try {
	        // Remets ce que TU stockes vraiment (exemples)
	        // currentCouple = null; coupleAnnounced = false; loversDonUnlocked = false;
	    } catch (Throwable ignored) {}
	}
	
	


}
