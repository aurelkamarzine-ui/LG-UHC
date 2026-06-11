package fr.sfakeur.lguhc;

import org.bukkit.Bukkit;


import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;




import org.bukkit.plugin.Plugin;


public class DeathManager  implements Listener {

    /** Ton main plugin, pour scheduler/sons/etc. */
    private final LGUHC plugin;
    /** Optionnel : pour MAJ scoreboard kills si tu as une méthode côté GameManager. */
    private final GameManager game;
    /** Pour récupérer les rôles, afficher le nom du rôle, router onPlayerKill/onPlayerDeath. */
    private final RoleService roleService;

    /** Zone “limbo” (sursis 10s). */
    private final Location limboLocation;
    
    /** Victimes en attente des 10s. */
    
 // champ
    private final ErmiteTabManager ermiteTab;
    
 // 15 secondes désormais
   


    // Pour “finaliser instantanément” certaines morts (ex: lover)
    private final java.util.Set<java.util.UUID> instantFinalize = new java.util.HashSet<>();
    public void markInstantFinalizeNextDeath(java.util.UUID id) { if (id != null) instantFinalize.add(id); }
    
 // en haut de DeathManager (champs):
    private static final long PENDING_TICKS = 15L * 20L; // 15 secondes
    private final java.util.Map<java.util.UUID, PendingDeath> pending = new java.util.HashMap<>();

    // pour /lg dissimuler (Assassin): killerId -> victimId (pd.victimId)
    private final java.util.Map<java.util.UUID, java.util.UUID> assassinPrompt = new java.util.HashMap<>();
    
 // Sorcière : witchId -> victimId (fenêtre 5s avant finalisation)
    private final java.util.Map<java.util.UUID, java.util.UUID> sorcierePrompt = new java.util.HashMap<>();
    
    
 // Offre d’infection en attente (une seule active à la fois)
    private java.util.UUID infectOfferVictim = null;
    private long infectOfferExpiresAtMs = 0L;
    
 // ===== IPDL OFFER (source de vérité) =====
    private java.util.UUID pendingInfectVictim = null;
    private long          pendingInfectExpiryMs = 0L;
    private org.bukkit.scheduler.BukkitTask pendingInfectTask = null;

    // 5s après la mort → on affiche l’offre ; l’offre dure 5s
    private static final long INFECT_OFFER_DELAY_TICKS = 5L * 20L;
    private static final long INFECT_OFFER_WINDOW_MS   = 5_000L;
    
 // SERVANTE : servanteId -> victimId (fenêtre : jusqu’à finalisation)
    private final java.util.Map<java.util.UUID, java.util.UUID> servantePrompt = new java.util.HashMap<>();
    
    
    
    
    
    
    
    
    

    
    


    
    
    
    
    


    private void clearSorcierePromptsForVictim(java.util.UUID victim) {
        java.util.List<java.util.UUID> rm = new java.util.ArrayList<>();
        for (java.util.Map.Entry<java.util.UUID, java.util.UUID> e : sorcierePrompt.entrySet())
            if (victim.equals(e.getValue())) rm.add(e.getKey());
        for (java.util.UUID k : rm) sorcierePrompt.remove(k);
    }


    // sous-ensemble: ceux-ci auront le message “par chagrin d’amour”
    private final java.util.Set<java.util.UUID> loverFinalize   = new java.util.HashSet<>();
    
 // DeathManager.java
    public boolean isPending(UUID id) { return pending.containsKey(id); }
    
    
    
   

    





    

    


    /* ====== Donnée figée d’une “mort en attente” ====== */
 // DeathManager.java (classe interne ou top-level)
    static class PendingDeath {
    	 public final java.util.UUID victimId;
    	 public final java.util.UUID killerId; // peut être null
        final fr.sfakeur.lguhc.RoleService roleService;

        // snapshots / état
        org.bukkit.Location deathLoc;                 // <- plus final
        org.bukkit.inventory.ItemStack[] storedContents;
        org.bukkit.inventory.ItemStack[] storedArmor;

        boolean ermiteSilent = false;
        boolean concealed = false;
        boolean nonResurrectable = false;

        String roleNameAtDeath = "?";

        org.bukkit.scheduler.BukkitTask task;
        
        boolean loverDeath = false; // ✅ nouveau champ
        
        boolean solitaireAtDeath = false;
        
        



        PendingDeath(org.bukkit.entity.Player victim,
                     org.bukkit.entity.Player killer,
                     fr.sfakeur.lguhc.RoleService rs) {
            this.victimId = (victim != null ? victim.getUniqueId() : null);
            this.killerId = (killer != null ? killer.getUniqueId() : null);
            this.roleService = rs;

            
            // ✔ initialise ici (donc plus d’assignation après)
            this.deathLoc = victim.getLocation().clone();
            this.storedContents = victim.getInventory().getContents();
            this.storedArmor    = victim.getInventory().getArmorContents();

            try {
                fr.sfakeur.lguhc.RoleService.RoleId rid = rs.getRole(victim);
                if (rid != null) this.roleNameAtDeath = rs.displayName(rid);
            } catch (Throwable ignored) {}
        }
    }


    /* ====== Constructeurs ====== */

    // Solution 1 : on passe directement le main plugin.
    public DeathManager(LGUHC plugin, GameManager game, RoleService roleService) {
        this.plugin = plugin;
        this.game = game;
        this.roleService = roleService;
        
        World w = Bukkit.getWorlds().get(0);
        this.limboLocation = new Location(w, 0.5, 200.0, 0.5);

        this.ermiteTab = new ErmiteTabManager(plugin); // OK: plugin est LGUHC
        
        org.bukkit.Bukkit.getLogger().info("[DeathManager] game = " + (game!=null?"OK":"NULL"));

    }

    // Surcharge pratique si jamais tu voulais créer depuis GameManager (pas obligatoire ici)
    public DeathManager(LGUHC plugin, RoleService roleService) {
        this(plugin, plugin.getGameManager(), roleService);
    }

    /* ====== Events ====== */
    @org.bukkit.event.EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final UUID vid = victim.getUniqueId();
        

        // ⛑️ anti-doublon: si déjà en “sursis”, on ignore
        if (pending.containsKey(vid)) {
            Bukkit.getLogger().info("[DeathManager] Duplicate death event ignored for " + victim.getName());
            return;
        }

        final Player killer = victim.getKiller(); // peut être null
        RoleService.RoleId rid = null;
        try { rid = roleService.getRole(victim); } catch (Throwable ignored) {}
        
     // tout en haut de onPlayerDeath(...)
     // --- Mort forcée instantanée (lover, etc.) ---
        if (instantFinalize.remove(vid)) {
            // Pas de message/drops de l’event vanilla
            event.setDeathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Construire un PendingDeath minimal et finaliser *ici*
            PendingDeath pd = new PendingDeath(victim, killer, roleService);
            if (loverFinalize.remove(vid)) {
                pd.loverDeath = true;  // affichage “par chagrin d’amour”
            }
            
         // juste après avoir mis 'pending.put(pd.victimId, pd);' et planifié la finalisation
         // (si PENDING_TICKS = 15s, on vise T+5s pour laisser 5s de fenêtre au clic)






            finalizeNowWithBroadcast(pd);      // drop + spectateur + broadcast
            try { roleService.onPlayerDeath(victim); } catch (Throwable t) { t.printStackTrace(); }
            return; // Surtout ne pas entrer dans le flux “15s”
        }

        // 1) Pas de message / pas de drop immédiat (on gère nous-mêmes)
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);




        // 2) Créditer le kill **UNE SEULE FOIS** ici (même si Ancien va ressusciter)
        if (killer != null && killer != victim) {
            try { if (game != null) game.addKillAndUpdateScoreboard(killer); } catch (Throwable ignored) {}
            try { roleService.onPlayerKill(killer, victim); } catch (Throwable t) { t.printStackTrace(); }
            Bukkit.getLogger().info("[DeathManager] +1 kill -> " + killer.getName() + " (victim=" + victim.getName() + ")");
        }

        // 3) Ancien : résurrection unique si tué par un Loup (pas de “15s”)
        try {
            RoleService.RoleState vs = roleService.get(victim);
            if (rid == RoleService.RoleId.ANCIEN && vs != null) {
                boolean killedByWolf = (killer != null && roleService.isWolf(killer));
                if (killedByWolf && !vs.ancienResUsed) {
                    // snapshot inv/armure
                    final org.bukkit.inventory.ItemStack[] contents = victim.getInventory().getContents();
                    final org.bukkit.inventory.ItemStack[] armor    = victim.getInventory().getArmorContents();

                    vs.ancienResUsed = true;
                    vs.ancienResistanceActive = false;

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try { victim.spigot().respawn(); } catch (Throwable ignored) {}
                        victim.setGameMode(GameMode.SURVIVAL);

                            double heal = Math.min(victim.getMaxHealth(), 10.0D);
                            if (heal <= 0.0D) heal = 10.0D;
                            victim.setHealth(heal);
                            victim.setFoodLevel(20);
                            victim.setFireTicks(0);
                            victim.setNoDamageTicks(60);

                            try {
                                victim.getInventory().setContents(contents);
                                victim.getInventory().setArmorContents(armor);
                                victim.updateInventory();
                            } catch (Throwable ignored) {}

                            victim.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);

                            org.bukkit.Location tp = game.randomSafeScatter();
                            if (tp != null) victim.teleport(tp);

                            victim.sendMessage(ChatColor.GOLD + "[Ancien] "
                                    + ChatColor.GRAY + "Tu as été tué par un Loup et ressuscites. "
                                    + "Tu " + ChatColor.RED + "perds définitivement ta Résistance" + ChatColor.GRAY + ".");
                            
                            // === NO-FALL permanent pour l’Ancien après résurrection ===
                            // On a déjà 'vs' plus haut; mais on peut re-récupérer proprement :
                            RoleService.RoleState s2 = roleService.get(victim);
                            if (s2 != null && s2.roleId == RoleService.RoleId.ANCIEN) {
                                s2.ancienNoFallPermanent = true;   // flag persistant côté rôle
                                victim.setFallDistance(0f);
                                victim.sendMessage(ChatColor.GOLD + "[Ancien] " + ChatColor.GRAY
                                        + "Tu es désormais insensible aux dégâts de chute pour le reste de la partie.");
                            }
                        
                    }, 1L);

                    // on ne lance PAS la mécanique des 15s pour ce cas
                    return;
                } else {
                    // toute autre mort d’Ancien : il perd sa résistance définitivement
                    if (vs.ancienResistanceActive) {
                        vs.ancienResistanceActive = false;
                        victim.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
                    }
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }

        // 4) Cas ERMITE : mort silencieuse (finalisation après PENDING_TICKS, sans broadcast)
        final PendingDeath pd = new PendingDeath(victim, killer, roleService);
        if (rid == RoleService.RoleId.ERMITE) {
            pd.ermiteSilent = true;
        }
        pending.put(pd.victimId, pd);
        
        
     // === Servante Dévouée : proposer l'Appropriation immédiatement ===
        try {
            for (RoleService.RoleState s : roleService.getStates().values()) {
                if (s.roleId != RoleService.RoleId.SERVANTE_DEVOUEE) continue;
                if (s.servanteUsed) continue; // 1x/partie

                // pas si c'est elle la tueuse
                if (pd.killerId != null && pd.killerId.equals(s.owner)) continue;

                // cible doit être "Village" (align effectif)
                if (roleService.effectiveWinAlign(pd.victimId) != RoleService.Align.VILLAGE) continue;

                org.bukkit.entity.Player serv = Bukkit.getPlayer(s.owner);
                if (serv == null || !serv.isOnline() || serv.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

                // mémorise l’offre pour cette servante
                servantePrompt.put(s.owner, pd.victimId);

                // bouton cliquable
                net.md_5.bungee.api.chat.ComponentBuilder cb = new net.md_5.bungee.api.chat.ComponentBuilder("")
                    .append("§d[Servante] §7S’approprier les pouvoirs de ")
                    .append("§f" + victim.getName() + "§7 ? ")
                    .append("[CLIQUER]").bold(true).color(net.md_5.bungee.api.ChatColor.GOLD)
                    .event(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg appropriation"))
                    .event(new net.md_5.bungee.api.chat.HoverEvent(
                        net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.ComponentBuilder("Appropriation (1x/partie) – valable jusqu’à la finalisation").create()));
                serv.spigot().sendMessage(cb.create());
            }
        } catch (Throwable t) { t.printStackTrace(); }

        
     // ===== Sorcière : prompt à T+10s (5s avant la fin du sursis) =====
        long delayWitch = Math.max(0, PENDING_TICKS - (5L * 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // Vérifier que la mort est toujours en sursis & pas dissimulée
                PendingDeath cur = pending.get(pd.victimId);
                if (cur == null) return;                   // déjà réanimé/finalisé
                if (cur.concealed || cur.nonResurrectable) return;

                for (RoleService.RoleState s : roleService.getStates().values()) {
                    // Sorcière vivante et avec au moins un des 2 pouvoirs restants ?
                    if (!roleService.canUseRolePower(s.owner, RoleService.RoleId.SORCIERE)) continue;
                    if (s.witchResUsed && s.witchCurseUsed) continue;

                    Player witch = Bukkit.getPlayer(s.owner);
                    if (witch == null || !witch.isOnline() || witch.getGameMode() != GameMode.SURVIVAL) continue;

                    // Enregistre le prompt (UNE SEULE map) : witch -> victim
                    // La commande /lg malediction relira le killer via pending.get(victimId)
                    sorcierePrompt.put(s.owner, pd.victimId);

                    // 80 blocs ? -> on révèle le pseudo de la victime ou non
                    boolean showName = false;
                    Player vNow = Bukkit.getPlayer(pd.victimId);
                    if (vNow != null && vNow.isOnline() && witch.getWorld() == vNow.getWorld()) {
                        if (witch.getLocation().distanceSquared(pd.deathLoc) <= 80.0 * 80.0) showName = true;
                    }

                    String cible = showName && vNow != null ? ("§f" + vNow.getName()) : "§fun joueur";

                    net.md_5.bungee.api.chat.ComponentBuilder cb = new net.md_5.bungee.api.chat.ComponentBuilder("")
                        .append("§5[Sorcière] §7Dans 5s, ").append(cible)
                        .append(" §7sera finalisé. Choisis une action : ");

                    // Bouton Résurrection
                    if (!s.witchResUsed) {
                        cb.append(" [Résurrection]").bold(true).color(net.md_5.bungee.api.ChatColor.GREEN)
                          .event(new net.md_5.bungee.api.chat.ClickEvent(
                              net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg resurrection"))
                          .event(new net.md_5.bungee.api.chat.HoverEvent(
                              net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                              new net.md_5.bungee.api.chat.ComponentBuilder("Ressusciter la victime").create()));
                    } else {
                        cb.append(" [Résurrection]").color(net.md_5.bungee.api.ChatColor.DARK_GRAY)
                          .event((net.md_5.bungee.api.chat.HoverEvent)null);
                    }

                    cb.append(" ");

                    // Bouton Malédiction (seulement si on a un killer)
                    if (!s.witchCurseUsed && pd.killerId != null) {
                        cb.append("[Malédiction]").bold(true).color(net.md_5.bungee.api.ChatColor.RED)
                          .event(new net.md_5.bungee.api.chat.ClickEvent(
                              net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg malediction"))
                          .event(new net.md_5.bungee.api.chat.HoverEvent(
                              net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                              new net.md_5.bungee.api.chat.ComponentBuilder("Le tueur gagne -1♥ d’Absorption par gapple").create()));
                    } else {
                        cb.append("[Malédiction]").color(net.md_5.bungee.api.ChatColor.DARK_GRAY)
                          .event((net.md_5.bungee.api.chat.HoverEvent)null);
                    }

                    witch.spigot().sendMessage(cb.create());
                }
            } catch (Throwable t) { t.printStackTrace(); }
        }, delayWitch);

        
     // 👉 Offre IPDL : killer est un loup (inclut infectés) → proposer aux IPDL ayant marqué cette victime
        if (killer != null && roleService != null && roleService.isWolf(killer)) {
            final java.util.UUID targetId = pd.victimId;
         // IPDL : offre 10s avant la fin (T+9s si sursis=15s)
            long delayIpdl = Math.max(0L, PENDING_TICKS - 10L * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    int offers = 0;
                    for (RoleService.RoleState s : roleService.getStates().values()) {
                        if (s.roleId != RoleService.RoleId.INFECT_PERE_DES_LOUPS) continue;
                        if (s.ipdlInfectUsed) continue;
                        if (!s.ipdlMarked.contains(pd.victimId)) continue;
                        Player ipdl = Bukkit.getPlayer(s.owner);
                        if (ipdl == null || !ipdl.isOnline()) continue;

                     // 👉 Infection : 5s après la mort, fenêtre de 5s
                        if (killer != null && roleService != null && roleService.isWolf(killer)) {
                            armInfectOfferAfterWolfKill(pd.victimId); // appelle openInfectWindow(...) à +5s
                        }

                        ipdl.sendMessage("§8(Tu as 5 secondes)");
                        offers++;
                    }
                    Bukkit.getLogger().info("[IPDL] Offers sent: " + offers);
                } catch (Throwable t) { t.printStackTrace(); }
            }, delayIpdl);

        }

        

        try {
            if (killer != null) {
                RoleService.RoleState ks = roleService.get(killer);
                // l’API helper global qui sait si un Player est loup:
                boolean killerIsWolf = roleService.isWolf(killer);
                // + cas particulier: Loup-Garou Blanc si tu veux qu’il déclenche aussi l’infection
                ///boolean killerIsLGB  = (ks != null && ks.roleId == RoleService.RoleId.LOUP_GAROU_BLANC);
               /// wolfKill = killerIsWolf || killerIsLGB;
            }
        } catch (Throwable ignored) {}



        // vider l’inventaire du joueur pendant le “sursis”
        victim.getInventory().clear();
        victim.getInventory().setArmorContents(null);
        victim.updateInventory();

        // 5) Assassin: prompt à T+10s (avec 15s de “sursis” => 5s avant la fin)
     // Assassin : prompt 6s avant la fin (T+9s si sursis=15s)
        if (killer != null) {
            RoleService.RoleState ks = roleService.get(killer);
            if (ks != null
                && roleService.canUseRolePower(ks.owner, RoleService.RoleId.ASSASSIN)
                && ks.assassinConcealLeft > 0) {

                long delayAss = Math.max(0L, PENDING_TICKS - 10L * 20L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player a = Bukkit.getPlayer(ks.owner);
                    if (a == null || !a.isOnline()) return;
                    assassinPrompt.put(ks.owner, pd.victimId);
                    a.spigot().sendMessage(
                        new net.md_5.bungee.api.chat.ComponentBuilder("")
                            .append("Dissimuler cette mort ? ").color(net.md_5.bungee.api.ChatColor.GRAY)
                            .append("[CLIQUER]").bold(true).color(net.md_5.bungee.api.ChatColor.GOLD)
                            .event(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg dissimuler"))
                            .create()
                    );
                    a.sendMessage(org.bukkit.ChatColor.DARK_GRAY + "(Tu as 5 secondes)");
                }, delayAss);
            }
        }
            
        

        


        // 6) Finalisation à T+15s (ou 10s si tu remets 10)
        pd.task = Bukkit.getScheduler().runTaskLater(plugin, () -> finalizeDeath(pd), PENDING_TICKS);
        

    }

        // NB : on laisse le respawn se produire, on rattrape dans onPlayerRespawn pour TP limbo.
    

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        final Player p = event.getPlayer();
        PendingDeath pd = pending.get(p.getUniqueId());
        if (pd == null) return; // pas une mort en sursis -> respawn normal (au cas où)

        // Envoyer en “limbo” (toujours en SURVIVAL, invisible, invulnérable quelques secondes)
        event.setRespawnLocation(limboLocation);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                p.setGameMode(GameMode.SURVIVAL);
                // Invisibilité “propre” le temps du sursis
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 12, 0, true, false));
                // Unbreakable période courte (au cas où)
                p.setNoDamageTicks(20 * 10);
            } catch (Throwable ignored) {}
        });
    }

    /* ====== Finalisation (après 10s si non réssuscité) ====== */
 // DeathManager.java
    private void finalizeDeath(PendingDeath pd) {
    	clearInfectOfferForVictim(pd.victimId);
    	// Nettoie l’offre Servante pour cette victime
    	clearServantePromptsForVictim(pd.victimId);


    	
        PendingDeath still = pending.remove(pd.victimId);
        if (still == null) return;

        final org.bukkit.entity.Player victim = org.bukkit.Bukkit.getPlayer(pd.victimId);
        if (victim == null) return;
        
     // ===== Voleur : vol au moment de la mort DÉFINITIVE =====
        try {
            // killer peut être hors-ligne → on travaille au niveau des states
            RoleService.RoleState ks = roleService.getStates().get(pd.killerId);
            RoleService.RoleState vs = roleService.getStates().get(pd.victimId);

            if (ks != null && ks.roleId == RoleService.RoleId.VOLEUR && !ks.voleurStolen && vs != null) {
                // Si la victime était en couple → le voleur prend sa place dans le couple
                if (vs.lover != null) {
                    roleService.transferCoupleToReplacement(pd.victimId, pd.killerId);
                }
                // Applique le vol (version UUID pour gérer killer hors-ligne)
                roleService.applyTheft(pd.killerId, pd.victimId);
            }
        } catch (Throwable t) { t.printStackTrace(); }
        // ===============================================

        applyDefinitiveKillBonuses(pd.killerId, pd.victimId);


        // 1) Drop ce qu'on avait mis de côté
        dropStoredItems(pd);

        // 2) Place le joueur en spectateur au lieu de le laisser "vivant" 15s
        try { victim.teleport(pd.deathLoc); } catch (Throwable ignored) {}
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        
        try { handleVoleurTheftOnFinalize(pd); } catch (Throwable t) { t.printStackTrace(); }
        
        try { roleService.offerSisterReveal(pd.victimId, pd.killerId); } catch (Throwable t) { t.printStackTrace(); }


        // 3) Cas silencieux : Ermite OU dissimulation Assassin → PAS de broadcast
        if (pd.ermiteSilent || pd.concealed) {
            try { roleService.onPlayerDeath(victim); } catch (Throwable t) { t.printStackTrace(); }
            // ➜ informer Cupidon v2 qu’un lover est définitivement mort
            try { roleService.handleCupidV2CoupleDeath(pd.victimId); } catch (Throwable ignored) {}
            scheduleWinCheck();
            return;
        }

        // 4) Broadcast standard "X est mort, il était Rôle"
     // Reconstruire le nom du rôle pour pouvoir ajouter "(solitaire)" si besoin
        String roleName;
        RoleService.RoleState vs2 = roleService.getStates().get(pd.victimId);
        if (vs2 != null && vs2.roleId != null) {
            String base = roleService.displayName(vs2.roleId);
            if (roleService.isSolitaireEnabled() && vs2.solitaire) {
                base += " " + org.bukkit.ChatColor.GRAY + "(solitaire)";
            }
            roleName = base;
        } else {
            roleName = (pd.roleNameAtDeath != null ? pd.roleNameAtDeath : "?");
        }

        // Broadcast standard
        org.bukkit.Bukkit.broadcastMessage(
            org.bukkit.ChatColor.GREEN + victim.getName() + " est mort, il était " + roleName + "."
        );


        // petit son
        for (org.bukkit.entity.Player all : org.bukkit.Bukkit.getOnlinePlayers()) {
            try { all.playSound(all.getLocation(), org.bukkit.Sound.ENDERDRAGON_GROWL, 1.0f, 0.8f); }
            catch (Throwable ignored) {}
        }

        // 5) Hooks rôles (déclencheurs internes)
        try { roleService.onPlayerDeath(victim); } catch (Throwable t) { t.printStackTrace(); }

        // 6) ➜ signaler Cupidon v2 (après finalisation)
        try { roleService.handleCupidV2CoupleDeath(pd.victimId); } catch (Throwable ignored) {}

        // 7) Check win (léger délai pour laisser les listeners finir)
        scheduleWinCheck();
    }




    /* ====== Helpers ====== */

 
    
    private void finalizeErmiteDeathNow(Player victim,
            org.bukkit.Location deathLoc,
            org.bukkit.inventory.ItemStack[] contents,
            org.bukkit.inventory.ItemStack[] armor) {
    	if (victim == null || deathLoc == null) return;

    	// 1) Drop du stuff au sol (à l’endroit de mort)
    	dropInventory(deathLoc, contents, armor);

    	// 2) Nettoie l’inventaire du joueur (et armure), puis spectateur sur place
    	try {
    		victim.getInventory().clear();
    		victim.getInventory().setArmorContents(null);
    		victim.updateInventory();
    	} catch (Throwable ignored) {}

    	try {
    		victim.teleport(deathLoc);
    		victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
    	} catch (Throwable ignored) {}

    	// 3) Marque l’Ermite comme "mort anonymement" dans le GameManager
    	try {
    		if (game != null) game.markHermitDead(victim.getUniqueId());
    	} catch (Throwable ignored) {}

    	// 4) Pas de broadcast, pas de son. Mais on notifie les rôles.
    	// finalizeErmiteDeath(pd)
    	try { roleService.onPlayerDeath(victim); } catch (Throwable t) { t.printStackTrace(); }
    	scheduleWinCheck();  // <<< À LA FIN

    }

    private void dropInventory(org.bukkit.Location loc,
   org.bukkit.inventory.ItemStack[] contents,
   org.bukkit.inventory.ItemStack[] armor) {
    	org.bukkit.World w = loc.getWorld();
    	if (w == null) return;

    	if (contents != null) {
    		for (org.bukkit.inventory.ItemStack it : contents) {
	if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
	try { w.dropItemNaturally(loc, it.clone()); } catch (Throwable ignored) {}
    		}
    	}
    	if (armor != null) {
    		for (org.bukkit.inventory.ItemStack it : armor) {
    			if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
    			try { w.dropItemNaturally(loc, it.clone()); } catch (Throwable ignored) {}
    		}
    	}
    }
    
    /** Finalisation d’un Ermite après 10s : drop + spectator, SANS broadcast/son. */
    private void finalizeErmiteDeath(PendingDeath pd) {
        PendingDeath still = pending.remove(pd.victimId);
        if (still == null) return;

        Player victim = Bukkit.getPlayer(pd.victimId);
        if (victim == null) return;

        // Drop le stuff à l’endroit de mort
        dropStoredItems(pd);

        // Spectator sur le lieu de mort
        victim.teleport(pd.deathLoc);
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);

        // Pas de message / pas de son

        // ⚠️ Tab : on “fige” l’entrée du joueur en mode SURVIVAL (fake) via le manager
        try { ermiteTab.addErmiteGhost(victim); } catch (Throwable t) { t.printStackTrace(); }

        // notifier les rôles
        try { roleService.onPlayerDeath(victim); } catch (Throwable t) { t.printStackTrace(); }
    }


    public boolean assassinTryConceal(java.util.UUID killerId) {
        java.util.UUID victimId = assassinPrompt.remove(killerId);
        if (victimId == null) return false;
        PendingDeath pd = pending.get(victimId);
        if (pd == null) return false;

        pd.concealed = true;
        pd.nonResurrectable = true;
        return true;
    }


    public void markInstantFinalize(java.util.UUID id) {
        instantFinalize.add(id);
    }
    
    private void finalizeNowWithBroadcast(PendingDeath pd) {
        org.bukkit.entity.Player victim = org.bukkit.Bukkit.getPlayer(pd.victimId);
        if (victim == null) return;
        
        try { handleVoleurTheftOnFinalize(pd); } catch (Throwable t) { t.printStackTrace(); }
        
     // ===== Voleur : vol au moment de la mort DÉFINITIVE (instant) =====
        try {
            RoleService.RoleState ks = roleService.getStates().get(pd.killerId);
            RoleService.RoleState vs = roleService.getStates().get(pd.victimId);

            if (ks != null && ks.roleId == RoleService.RoleId.VOLEUR && !ks.voleurStolen && vs != null) {
                if (vs.lover != null) {
                    roleService.transferCoupleToReplacement(pd.victimId, pd.killerId); // si tu l’as ajouté
                }
                roleService.applyTheft(pd.killerId, pd.victimId); // ← maintenant OK (UUID, UUID)
            }
        } catch (Throwable t) { t.printStackTrace(); }

        // ==============================================================


        // drop
        try {
            if (pd.storedContents != null) {
                for (org.bukkit.inventory.ItemStack it : pd.storedContents)
                    if (it != null && it.getType() != org.bukkit.Material.AIR)
                        victim.getWorld().dropItemNaturally(pd.deathLoc, it.clone());
            }
            if (pd.storedArmor != null) {
                for (org.bukkit.inventory.ItemStack it : pd.storedArmor)
                    if (it != null && it.getType() != org.bukkit.Material.AIR)
                        victim.getWorld().dropItemNaturally(pd.deathLoc, it.clone());
            }
        } catch (Throwable ignored) {}

        // spectateur
        try { victim.teleport(pd.deathLoc); } catch (Throwable ignored) {}
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);

        // message vert classique (sauf cas silencieux — mais ici on veut broadcast)
     // Reconstruire le nom du rôle pour "(solitaire)" si option active
        String roleName;
        RoleService.RoleState vs2 = roleService.getStates().get(pd.victimId);
        if (vs2 != null && vs2.roleId != null) {
            String base = roleService.displayName(vs2.roleId);
            if (roleService.isSolitaireEnabled() && vs2.solitaire) {
                base += " " + org.bukkit.ChatColor.GRAY + "(solitaire)";
            }
            roleName = base;
        } else {
            roleName = (pd.roleNameAtDeath != null ? pd.roleNameAtDeath : "?");
        }

        if (pd.loverDeath) {
            Bukkit.broadcastMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "Par chagrin d'amour, "
                + org.bukkit.ChatColor.WHITE + victim.getName()
                + org.bukkit.ChatColor.LIGHT_PURPLE + " qui était "
                + org.bukkit.ChatColor.WHITE + roleName
                + org.bukkit.ChatColor.LIGHT_PURPLE + " a décidé de rejoindre son âme sœur dans la tombe.");
        } else {
            Bukkit.broadcastMessage(org.bukkit.ChatColor.GREEN + victim.getName()
                + " est mort, il était " + roleName + ".");
        }



        for (org.bukkit.entity.Player all : org.bukkit.Bukkit.getOnlinePlayers()) {
            try { all.playSound(all.getLocation(), org.bukkit.Sound.ENDERDRAGON_GROWL, 1.0f, 0.8f); }
            catch (Throwable ignored) {}
        }

     // finalizeNowWithBroadcast(...)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { if (game != null) game.checkWin(); } catch (Throwable t) { t.printStackTrace(); }
        }, 1L);
    }

    
    private void dropStoredItems(PendingDeath pd) {
        org.bukkit.World w = pd.deathLoc.getWorld();
        if (w == null) return;

        // contenu
        if (pd.storedContents != null) {
            for (org.bukkit.inventory.ItemStack it : pd.storedContents) {
                if (it == null || it.getType() == null || it.getAmount() <= 0) continue;
                w.dropItemNaturally(pd.deathLoc, it);
            }
        }
        // armure
        if (pd.storedArmor != null) {
            for (org.bukkit.inventory.ItemStack it : pd.storedArmor) {
                if (it == null || it.getType() == null || it.getAmount() <= 0) continue;
                w.dropItemNaturally(pd.deathLoc, it);
            }
        }
    }

    private void dropStoredNowAndSpectate(PendingDeath pd) {
        org.bukkit.entity.Player v = org.bukkit.Bukkit.getPlayer(pd.victimId);
        if (v == null) return;

        try {
            if (pd.storedContents != null) {
                for (org.bukkit.inventory.ItemStack it : pd.storedContents)
                    if (it != null && it.getType() != org.bukkit.Material.AIR)
                        v.getWorld().dropItemNaturally(pd.deathLoc, it.clone());
            }
            if (pd.storedArmor != null) {
                for (org.bukkit.inventory.ItemStack it : pd.storedArmor)
                    if (it != null && it.getType() != org.bukkit.Material.AIR)
                        v.getWorld().dropItemNaturally(pd.deathLoc, it.clone());
            }
        } catch (Throwable ignored) {}

        try { v.teleport(pd.deathLoc); } catch (Throwable ignored) {}
        v.setGameMode(org.bukkit.GameMode.SPECTATOR);
    }

    public void markLoverInstantFinalize(java.util.UUID playerId) {
        loverFinalize.add(playerId);
        instantFinalize.add(playerId); // lover = instant par définition
    }
    
    
 // DeathManager.java
    public boolean reviveInfected(java.util.UUID victimId) {
        // Retire la mort en attente & annule sa finalisation
        PendingDeath pd = pending.remove(victimId);
        if (pd == null) return false;
        try { if (pd.task != null) pd.task.cancel(); } catch (Throwable ignored) {}
        // (optionnel) nettoie toute offre IPDL pointant dessus
        try { clearInfectOfferForVictim(victimId); } catch (Throwable ignored) {}

        org.bukkit.entity.Player v = org.bukkit.Bukkit.getPlayer(victimId);
        if (v == null) return false;

        // Forcer le respawn + mode SURVIVAL
        try { v.spigot().respawn(); } catch (Throwable ignored) {}
        v.setGameMode(org.bukkit.GameMode.SURVIVAL);

        // Restaure inventaire/armure stockés dans PendingDeath
        try {
            if (pd.storedContents != null) v.getInventory().setContents(pd.storedContents);
            if (pd.storedArmor    != null) v.getInventory().setArmorContents(pd.storedArmor);
            v.updateInventory();
        } catch (Throwable ignored) {}

        // Soin & sécurité
        double hp = Math.max(1.0D, v.getMaxHealth());
        v.setHealth(Math.min(hp, v.getMaxHealth()));
        v.setFoodLevel(20);
        v.setFireTicks(0);
        v.setNoDamageTicks(60);

        // TP safe (ou à l’endroit de mort si tu préfères)
        org.bukkit.Location tp = (game != null ? game.randomSafeScatter() : null);
        if (tp == null) tp = pd.deathLoc;
        try { if (tp != null) v.teleport(tp); } catch (Throwable ignored) {}
        
     // --- Annonce globale + MP victime (après la résurrection IPDL) ---
        try {
            org.bukkit.entity.Player vnow = org.bukkit.Bukkit.getPlayer(victimId);
            if (vnow != null && vnow.isOnline()) {
                vnow.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Infect] "
                    + org.bukkit.ChatColor.GRAY + "Tu as été ramené(e) à la vie par l’Infect Père des Loups. "
                    + "Tu dois désormais gagner " + org.bukkit.ChatColor.RED + "Loups-Garous");
            }
        } catch (Throwable ignored) {}


        // Surtout PAS de broadcast de mort.
        // Informe le RoleService pour terminer la conversion côté rôle (si besoin)
        try { roleService.onInfectedRevive(v); } catch (Throwable ignored) {}

        return true;
    }
   
    
 // DeathManager.scheduleWinCheck()
    private void scheduleWinCheck() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { if (game != null) game.checkWin(); } catch (Throwable t) { t.printStackTrace(); }
        }, 1L);
    }
    
 // DeathManager.java
    private void handleVoleurTheftOnFinalize(PendingDeath pd) {
        if (pd == null || pd.killerId == null || pd.victimId == null) return;

        Player killer = Bukkit.getPlayer(pd.killerId);
        Player victim = Bukkit.getPlayer(pd.victimId);
        if (killer == null || victim == null) return;

        RoleService.RoleState ks = roleService.get(killer);
        if (ks == null) return;

        if (ks.roleId == RoleService.RoleId.VOLEUR && !ks.voleurStolen) {
            // si la victime était en couple, on remplace le slot du mort par le voleur
            RoleService.RoleState vs = roleService.get(victim);
            try {
                if (vs != null && vs.lover != null) {
                    roleService.transferCoupleToReplacement(victim.getUniqueId(), killer.getUniqueId());
                }
            } catch (Throwable ignored) {}

            // applique le vol (rôle/cooldowns/camp effectif). L’effet Résistance est retiré dedans.
            try { roleService.applyTheft(killer, victim); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
    
    /** La sorcière tente de ressusciter la victime liée à son prompt (1x/partie). */
    public boolean sorciereTryResurrect(java.util.UUID witchId) {
        java.util.UUID victimId = sorcierePrompt.remove(witchId); // ⇐ retire l’offre
        if (victimId == null) { dbg("rez: no prompt for " + witchId); return false; }

        RoleService.RoleState ws = roleService.getStates().get(witchId);
        if (ws == null || ws.witchResUsed) { dbg("rez: already used or ws null"); return false; }
        if (witchId.equals(victimId)) { dbg("rez: self not allowed"); return false; }

        PendingDeath pd = pending.get(victimId);
        if (pd == null) { dbg("rez: pending null"); return false; }
        if (pd.concealed || pd.nonResurrectable) { dbg("rez: concealed/nonRes"); return false; }

        boolean ok = reviveByWitch(victimId);
        dbg("rez: reviveByWitch=" + ok);
        if (!ok) return false;

        ws.witchResUsed = true; // usage unique de résurrection
        return true;
    }





    public boolean sorciereTryCurse(java.util.UUID witchId) {
        java.util.UUID victimId = sorcierePrompt.remove(witchId); // ⇐ retire l’offre
        if (victimId == null) { dbg("curse: no prompt for " + witchId); return false; }

        RoleService.RoleState ws = roleService.getStates().get(witchId);
        if (ws == null || ws.witchCurseUsed) { dbg("curse: already used or ws null"); return false; }

        PendingDeath pd = pending.get(victimId);
        if (pd == null) { dbg("curse: pending null"); return false; }
        if (pd.killerId == null) { dbg("curse: killer null"); return false; }
        if (witchId.equals(pd.killerId)) { dbg("curse: self not allowed"); return false; }

        // Pose la malédiction
        RoleService.RoleState ks = roleService.getStates().get(pd.killerId);
        if (ks == null) { dbg("curse: killer state null"); return false; }
        ks.witchCursed = true;

        ws.witchCurseUsed = true; // usage unique de malédiction

        org.bukkit.entity.Player k = org.bukkit.Bukkit.getPlayer(pd.killerId);
        if (k != null && k.isOnline()) {
            k.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "[Malédiction] "
                + org.bukkit.ChatColor.GRAY + "Tes pommes dorées t’octroieront 1♥ d’absorption en moins.");
        }
        dbg("curse: applied to killer=" + pd.killerId);
        return true;
    }






    
    public boolean revivePendingVictim(java.util.UUID victimId) {
        PendingDeath pd = pending.remove(victimId);
        if (pd == null) return false;
        try { if (pd.task != null) pd.task.cancel(); } catch (Throwable ignored) {}

        org.bukkit.entity.Player v = org.bukkit.Bukkit.getPlayer(victimId);
        if (v == null) return false;

        try { v.spigot().respawn(); } catch (Throwable ignored) {}
        v.setGameMode(org.bukkit.GameMode.SURVIVAL);

        try {
            if (pd.storedContents != null) v.getInventory().setContents(pd.storedContents);
            if (pd.storedArmor    != null) v.getInventory().setArmorContents(pd.storedArmor);
            v.updateInventory();
        } catch (Throwable ignored) {}

        double hp = Math.max(1.0D, v.getMaxHealth());
        v.setHealth(Math.min(hp, v.getMaxHealth()));
        v.setFoodLevel(20);
        v.setFireTicks(0);
        v.setNoDamageTicks(60);

        org.bukkit.Location tp = (game != null ? game.randomSafeScatter() : null);
        if (tp == null) tp = pd.deathLoc;
        try { if (tp != null) v.teleport(tp); } catch (Throwable ignored) {}

        // PAS de broadcast
        // Notifie le RoleService s’il faut un hook (ici rien de spécial)
        return true;
    }
    
    public boolean reviveByWitch(java.util.UUID victimId) {
        PendingDeath pd = pending.remove(victimId);
        if (pd == null) return false;               // plus en sursis
        if (pd.concealed || pd.nonResurrectable) return false; // dissimulée/forcée => interdit
        try { if (pd.task != null) pd.task.cancel(); } catch (Throwable ignored) {}
        clearInfectOfferForVictim(victimId);

        org.bukkit.entity.Player v = org.bukkit.Bukkit.getPlayer(victimId);
        if (v == null) return false;

        // Respawn + SURVIVAL
        try { v.spigot().respawn(); } catch (Throwable ignored) {}
        v.setGameMode(org.bukkit.GameMode.SURVIVAL);

        // Inventaire/armure
        try {
            if (pd.storedContents != null) v.getInventory().setContents(pd.storedContents);
            if (pd.storedArmor    != null) v.getInventory().setArmorContents(pd.storedArmor);
            v.updateInventory();
        } catch (Throwable ignored) {}

        // Soin & TP safe
        double hp = Math.max(1.0D, v.getMaxHealth());
        v.setHealth(Math.min(hp, v.getMaxHealth()));
        v.setFoodLevel(20);
        v.setFireTicks(0);
        v.setNoDamageTicks(60);

        org.bukkit.Location tp = (game != null ? game.randomSafeScatter() : null);
        if (tp == null) tp = pd.deathLoc;
        try { if (tp != null) v.teleport(tp); } catch (Throwable ignored) {}

        // Hook rôle (si tu en as besoin pour la Sorcière — facultatif)
        try { roleService.onWitchRevive(v); } catch (Throwable ignored) {}

        // --- Annonce globale + MP victime ---
        try {
            v.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Sorcière] "
                + org.bukkit.ChatColor.GRAY + "Tu as été ramené(e) à la vie par la Sorcière. "
                + "Tu gardes ton rôle et ton camp.");
        } catch (Throwable ignored) {}

        return true;
    }
    
    

    
    
 // DeathManager.java
    public void fullResetForNewRun() {
        try {
            for (PendingDeath pd : new java.util.ArrayList<>(pending.values())) {
                try { if (pd.task != null) pd.task.cancel(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        try { pending.clear(); } catch (Throwable ignored) {}
        try { assassinPrompt.clear(); } catch (Throwable ignored) {}
        try { loverFinalize.clear(); } catch (Throwable ignored) {}
        try { instantFinalize.clear(); } catch (Throwable ignored) {}
        try { sorcierePrompt.clear(); } catch (Throwable ignored) {}   // <-- NOUVEAU
        try { servantePrompt.clear(); } catch (Throwable ignored) {}
        try { sorcierePrompt.clear(); } catch (Throwable ignored) {}


        pendingInfectVictim = null;
        pendingInfectExpiryMs = 0L;
    }


    
 // DeathManager.java

    /** Vrai si le joueur est en vie (pas en sursis, en SURVIVAL). */
    public boolean isAlive(java.util.UUID id) {
        // si tu gères un "sursis" via la map pending, on le considère comme "pas vivant"
        if (pending != null && pending.containsKey(id)) return false;

        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
        if (p == null) return false;
        if (!p.isOnline()) return false;
        return p.getGameMode() == org.bukkit.GameMode.SURVIVAL;
    }

    /** Vrai si le joueur est actuellement en sursis (en attente de finalisation). */
    public boolean isInPending(java.util.UUID id) {
        return pending != null && pending.containsKey(id);
    }
    



    private void startInfectOffer(java.util.UUID victimId) {
        // Trouver l’IPDL vivant et pas encore utilisé
        fr.sfakeur.lguhc.RoleService rs = plugin.getRoleService();
        fr.sfakeur.lguhc.RoleService.RoleState ipdl = null;
        for (fr.sfakeur.lguhc.RoleService.RoleState s : rs.getStates().values()) {
            if (s.roleId == fr.sfakeur.lguhc.RoleService.RoleId.INFECT_PERE_DES_LOUPS
                && !s.ipdlInfectUsed) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
                if (p != null && p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                    ipdl = s; break;
                }
            }
        }
        if (ipdl == null) return;

        // Armer l’offre
        pendingInfectVictim   = victimId;
        pendingInfectExpiryMs = System.currentTimeMillis() + INFECT_OFFER_WINDOW_MS;

        org.bukkit.entity.Player ipdlP = org.bukkit.Bukkit.getPlayer(ipdl.owner);
     // Récup nom sûr : RoleService → joueur en ligne → offline → fallback
        String vicName = null;
        try { 
            if (rs != null) vicName = rs.nameOfUUID(victimId);  // si tu as déjà ce helper
        } catch (Throwable ignored) {}

        if (vicName == null) {
            org.bukkit.entity.Player v = org.bukkit.Bukkit.getPlayer(victimId);
            if (v != null && v.isOnline()) vicName = v.getName();
        }

        if (vicName == null) {
            try { 
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(victimId);
                if (off != null && off.getName() != null) vicName = off.getName();
            } catch (Throwable ignored) {}
        }

        if (vicName == null) vicName = "un joueur";


        // UI cliquable (1.8)
        net.md_5.bungee.api.chat.TextComponent line =
            new net.md_5.bungee.api.chat.TextComponent(org.bukkit.ChatColor.DARK_RED + "[Infect] "
                + org.bukkit.ChatColor.GRAY + "Tu peux ressusciter " + org.bukkit.ChatColor.GOLD + vicName
                + org.bukkit.ChatColor.GRAY + " : ");
        net.md_5.bungee.api.chat.TextComponent btn =
            new net.md_5.bungee.api.chat.TextComponent(org.bukkit.ChatColor.RED + "[CLIQUER pour infecter]");
        btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg infect"));
        btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder("Utiliser l’infection (5s)").create()));
        line.addExtra(btn);

        if (ipdlP != null) ipdlP.spigot().sendMessage(line);

        // Auto-expiration silencieuse au bout de 5s (on ne touche pas au timer de 5s “delay”)
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInfectVictim != null && System.currentTimeMillis() >= pendingInfectExpiryMs) {
                pendingInfectVictim = null;
                pendingInfectExpiryMs = 0L;
            }
        }, 5L * 20L);
    }


    
 // Appelé APRES un kill par un loup : ouvre la fenêtre 5s après la mort
    public void armInfectOfferAfterWolfKill(UUID victimId) {
        if (victimId == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> openInfectWindow(victimId), 5L * 20L);
    }

    // Ouvre la fenêtre d’infection (5s) et push le bouton [CLIQUER]
    private synchronized void openInfectWindow(UUID victimId) {
        this.pendingInfectVictim = victimId;
        this.pendingInfectExpiryMs = System.currentTimeMillis() + 5_000L;
        notifyInfectClickable();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (DeathManager.this) {
                if (System.currentTimeMillis() >= pendingInfectExpiryMs) {
                    pendingInfectVictim = null;
                    pendingInfectExpiryMs = 0L;
                }
            }
        }, 5L * 20L);
    }

    private void notifyInfectClickable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            RoleService.RoleState s = plugin.getRoleService().get(p);
            if (!roleService.canUseRolePower(s.owner, RoleService.RoleId.INFECT_PERE_DES_LOUPS)) continue;
            if (s.ipdlInfectUsed) continue;

            net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent("§4[Infect] §7Tu peux infecter la dernière victime : ");
            net.md_5.bungee.api.chat.TextComponent btn  =
                new net.md_5.bungee.api.chat.TextComponent("§c[CLIQUER]");
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg infect"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("Infecter maintenant (fenêtre 5s)").create()));
            line.addExtra(btn);

            try { p.spigot().sendMessage(line); } catch (Throwable t) { p.sendMessage("§4[Infect] §7Tape §c/lg infect"); }
        }
    }

    // Consommation par /lg infect
    public UUID consumeInfectOffer(UUID ipdlId) {
        if (pendingInfectVictim == null) return null;
        if (System.currentTimeMillis() > pendingInfectExpiryMs) {
            pendingInfectVictim = null;
            pendingInfectExpiryMs = 0L;
            return null;
        }
        RoleService rs = plugin.getRoleService();
        RoleService.RoleState s = rs.getStates().get(ipdlId);
        if (s == null || s.roleId != RoleService.RoleId.INFECT_PERE_DES_LOUPS || s.ipdlInfectUsed) return null;

        UUID v = pendingInfectVictim;
        pendingInfectVictim = null;
        pendingInfectExpiryMs = 0L;
        return v;
    }

    // Si tu dois “purger” la fenêtre en cours pour une victime spécifique
    public void clearInfectOfferForVictim(UUID victimId) {
        if (pendingInfectVictim != null && pendingInfectVictim.equals(victimId)) {
            pendingInfectVictim = null;
            pendingInfectExpiryMs = 0L;
        }
    }
    
    /** Consomme l’offre d’appropriation pour cette servante (si valide et victime toujours en pending).
     *  Retourne victimId si OK, sinon null. */
    public java.util.UUID consumeServantePrompt(java.util.UUID servanteId) {
        java.util.UUID vic = servantePrompt.remove(servanteId);
        if (vic == null) return null;

        // valide seulement si la victime est encore en "pending"
        PendingDeath pd = pending.get(vic);
        if (pd == null) return null;

        // sécurité : si la servante était la tueuse (on double-check), refuse
        if (pd.killerId != null && pd.killerId.equals(servanteId)) return null;

        // cible doit être “Village” (align effectif) – sécurité côté DM aussi
        try {
            if (roleService.effectiveWinAlign(vic) != RoleService.Align.VILLAGE) return null;
        } catch (Throwable ignored) {}

        return vic;
    }

    /** Modifie le rôle affiché du mort pour le broadcast final en “Servante Dévouée”. */
    public boolean markServanteMask(java.util.UUID victimId) {
        PendingDeath pd = pending.get(victimId);
        if (pd == null) return false;
        pd.roleNameAtDeath = roleService.displayName(RoleService.RoleId.SERVANTE_DEVOUEE);
        return true;
    }

    /** Supprime toutes les offres servante visant cette victime (quand finalisée/sauvée). */
    private void clearServantePromptsForVictim(java.util.UUID victimId) {
        java.util.List<java.util.UUID> rm = new java.util.ArrayList<>();
        for (java.util.Map.Entry<java.util.UUID, java.util.UUID> e : servantePrompt.entrySet()) {
            if (victimId.equals(e.getValue())) rm.add(e.getKey());
        }
        for (java.util.UUID k : rm) servantePrompt.remove(k);
    }
    
    private void armWitchPrompt(java.util.UUID victimId, java.util.UUID killerId) {
        for (fr.sfakeur.lguhc.RoleService.RoleState s : roleService.getStates().values()) {
            if (s.roleId != fr.sfakeur.lguhc.RoleService.RoleId.SORCIERE) continue;
            org.bukkit.entity.Player w = org.bukkit.Bukkit.getPlayer(s.owner);
            if (w == null || !w.isOnline() || w.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;

            boolean canRes   = !s.witchResUsed   && !s.owner.equals(victimId);
            boolean canCurse = !s.witchCurseUsed && killerId != null && !s.owner.equals(killerId);

            // Ne pose le prompt que s’il y a au moins une action possible
            if (!canRes && !canCurse) continue;

            // ⚠ on mémorise seulement la VICTIME ; le tueur sera relu via pending.get(victimId)
            sorcierePrompt.put(s.owner, victimId);
            dbg("arm: set sorcierePrompt[" + s.owner + "]=" + victimId + " (canRes=" + canRes + ", canCurse=" + canCurse + ")");

            // Message + boutons
            net.md_5.bungee.api.chat.ComponentBuilder cb = new net.md_5.bungee.api.chat.ComponentBuilder("")
                .append("§5[Sorcière] §7Dans 5s, ")
                .append("§f" + (org.bukkit.Bukkit.getPlayer(victimId) != null ? org.bukkit.Bukkit.getPlayer(victimId).getName() : "un joueur"))
                .append(" §7sera finalisé. Choisis : ");

            if (canRes) {
                cb.append(" [Résurrection]").bold(true).color(net.md_5.bungee.api.ChatColor.GREEN)
                  .event(new net.md_5.bungee.api.chat.ClickEvent(
                      net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg resurrection"))
                  .event(new net.md_5.bungee.api.chat.HoverEvent(
                      net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                      new net.md_5.bungee.api.chat.ComponentBuilder("Ressusciter la victime").create()));
            } else {
                cb.append(" [Résurrection]").color(net.md_5.bungee.api.ChatColor.DARK_GRAY);
            }

            cb.append(" ");

            if (canCurse) {
                cb.append("[Malédiction]").bold(true).color(net.md_5.bungee.api.ChatColor.RED)
                  .event(new net.md_5.bungee.api.chat.ClickEvent(
                      net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg malediction"))
                  .event(new net.md_5.bungee.api.chat.HoverEvent(
                      net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                      new net.md_5.bungee.api.chat.ComponentBuilder("Les pommes du tueur donneront 1♥ d’absorption en moins").create()));
            } else {
                cb.append("[Malédiction]").color(net.md_5.bungee.api.ChatColor.DARK_GRAY);
            }

            w.spigot().sendMessage(cb.create());
        }
    }


    
    private void dbg(String msg) {
        try { plugin.getLogger().info("[WitchDBG] " + msg); } catch (Throwable ignored) {}
    }
    
 // DeathManager.java
    private void applyDefinitiveKillBonuses(java.util.UUID killerId, java.util.UUID victimId) {
        if (killerId == null) return;

        RoleService.RoleState ks = roleService.getStates().get(killerId);
        if (ks == null) return;

        org.bukkit.entity.Player k = org.bukkit.Bukkit.getPlayer(killerId);
        if (k == null || !k.isOnline() || k.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;

        // --- Wolves: Speed I + Absorption I for 60s, on definitive death ---
        boolean isWolf =
            roleService.isWolf(ks); // ← if you don’t have this overload, use roleService.isWolf(k) or your own helper

        // Exclude Amnésique v1 not yet awake, if your isWolf(...) doesn’t already do it:
        if (ks.roleId == RoleService.RoleId.LOUP_GAROU_AMNESIQUE_V1 && !ks.amnV1Awake) isWolf = false;

        if (isWolf) {
            int dur = 60 * 20;
            try {
                k.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, dur, 0, true, false));
                k.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.ABSORPTION, dur, 0, true, false));
            } catch (Throwable ignored) {}

            try {
                k.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loup] "
                    + org.bukkit.ChatColor.GRAY + "Bonus après mort définitive : "
                    + org.bukkit.ChatColor.GOLD + "Vitesse I + Absorption I (60s).");
            } catch (Throwable ignored) {}
        }

        // --- Arnacoeur (laisse ce que tu avais déjà ici) ---
        if (ks.roleId == RoleService.RoleId.ARNACOEUR) {
            int halves = ks.arnaHalvesTotal; // 2=1♥, 4=2♥, 6=3♥
            if (halves >= 2 && halves < 6) {
                k.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 60*20, 0, true, false));
            }
            if (halves >= 4 && halves < 6) {
                k.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.ABSORPTION, 60*20, 0, true, false));
            }
            // 6 halves (3♥) = Speed I permanent via tes passifs
            try {
                k.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "[Arnacoeur] "
                    + org.bukkit.ChatColor.GRAY + "Bonus appliqué (mort définitive de ta victime).");
            } catch (Throwable ignored) {}
        }
    }













}
