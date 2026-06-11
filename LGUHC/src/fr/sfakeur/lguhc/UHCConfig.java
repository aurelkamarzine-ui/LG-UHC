package fr.sfakeur.lguhc;

import org.bukkit.Bukkit;


import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.World;
import org.bukkit.WorldBorder;
//imports à ajouter en haut de UHCConfig
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;







public class UHCConfig implements Listener {
    private final LGUHC main;
    public UHCConfig(LGUHC main) { this.main = main; }

    // Titres (évite les fautes et facilite la comparaison)
    private static final String TITLE_MAIN     = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC";
    private static final String TITLE_TIMERS   = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC - Timers";
    private static final String TITLE_SCENS    = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC - Scénarios";
    private static final String TITLE_LIMITES  = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC - Limites";
    
 // --- Ajoute ces constantes de titres en haut de la classe ---
    private static final String TITLE_INV     = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC - Inventaire";
    private static final String TITLE_BEDROCK = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC - Bedrock";// Titres
    private static final String TITLE_UHC_MAIN   = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC";
    private static final String TITLE_INVENTAIRE = ChatColor.GREEN + "" + ChatColor.BOLD + "Config UHC - Inventaire";
    private static final String TITLE_INV_EDITOR = ChatColor.GREEN + "" + ChatColor.BOLD + "Éditeur inventaire de mort";
    private static final String TITLE_BORDURE    = ChatColor.AQUA   + "" + ChatColor.BOLD + "Bordure";

    // === États Timers ===
    private int idxCycle = 2; // 0:5m,1:10m,2:20m
    private final int[] CYCLE_MIN = {5,10,20};

    private int idxPvp = 0; // 0: 1min, 1: Jour 2
    // On garde les 2 modes par nom pour l’affichage
    private final String[] PVP_MODES = {"1 min", "Episode 2"};

    private int idxAnnonce = 0; // 0: 30s, 1: Jour 2
    private final String[] ANN_MODES = {"30 sec", "Episode 2"};

    private int idxMeetup = 3; // 0..11
    private final int[] MEETUP_MIN = {40,45,50,55,60,65,70,75,80,90,100,110,120};

    // === États Limites (enchants) ===
    private int protFer      = 0; // 0..4
    private int protDia      = 0; // 0..4
    private int sharpFer     = 0; // 0..5
    private int sharpDia     = 0; // 0..5
    private int power        = 0; // 0..5
    private int punch        = 0; // 0..2
    private int knockback    = 0; // 0..2

    // === États Inventaires ===
    private ItemStack[] startInvTemplate = null; // inventaire de départ (peut être null = par défaut)
    private ItemStack[] deathExtraTemplate = new ItemStack[27]; // ce que l’host configure dans l’éditeur

    // === États Bordure ===
    private final int[] BORDER_SIZES = {50,100,200,300,400,500,750,1000,1250,1500,1750,2000,2250,2500};
    private int idxBorderStart = 7; // 500 par défaut
    private int idxBorderEnd   = 2; // 200 par défaut (doit rester < start)

    private final int[] BORDER_TIMES_MIN = {10,20,40,60,80,100,120,130,140,150};
    private int idxBorderTime = 0;

    private final double[] BORDER_SPEEDS = {0.5,1.0,1.5,2.0};
    private int idxBorderSpeed = 1;

    // Helper cycle
    private int incIdx(int cur, int max) { return (cur + 1) % max; }
    private int decIdx(int cur, int max) { return (cur - 1 + max) % max; }
    
 // modèle d’inventaire de départ
    private ItemStack[] startMainTemplate = new ItemStack[36]; // slots 0..35 (hotbar incluse)
    private ItemStack[] startArmorTemplate = new ItemStack[4]; // 0:helmet,1:chest,2:legs,3:boots

    private static final String TITLE_INV_START_EDITOR = "§a§lÉditeur inventaire de départ";
    
    public ItemStack[] getStartMainTemplate() { return startMainTemplate; }
    public ItemStack[] getStartArmorTemplate() { return startArmorTemplate; }
    
 // --- Timers: durée d'un épisode ---
    private static final int[] EPISODE_MIN = {5, 10, 15, 20};
    private int idxEpisode = 1; // par défaut 10 min (index 1)

 // UHCConfig.java
    public int getBorderStartRadius() { return BORDER_SIZES[idxBorderStart]; } // rayon
    public int getBorderEndRadius()   { return BORDER_SIZES[idxBorderEnd]; }   // rayon
    public int getBorderStartMinute() { return BORDER_TIMES_MIN[idxBorderTime]; }
    public double getBorderSpeedBps() { return BORDER_SPEEDS[idxBorderSpeed]; } // blocks/s
    
 // Stocke les rôles activés par l’host (noms d’affichage des boutons)
    private final Set<String> enabledRoles = new HashSet<>();
    
    




    /* =========================
       OUVERTURE MENUS (public)
       ========================= */
    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_UHC_MAIN);

        ItemStack green = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,7,8, 9,17, 36,44, 45,46,52,53};
        for (int s : corners) inv.setItem(s, green);
        inv.setItem(4, green);

        inv.setItem(20, makeItem(Material.WATCH, ChatColor.GOLD + "Timers"));
        inv.setItem(22, makeItem(Material.BOOK, ChatColor.YELLOW + "Scénarios"));   // (si tes scénarios LG sont ailleurs, garde juste l’entrée)
        inv.setItem(24, makeItem(Material.ANVIL, ChatColor.GREEN + "Limites"));
        inv.setItem(30, makeItem(Material.CHEST, ChatColor.GREEN + "Inventaire"));
        inv.setItem(32, makeItem(Material.MAP, ChatColor.AQUA + "Bordure"));
        inv.setItem(40, makeItem(Material.PISTON_BASE, ChatColor.GREEN + "Prégénération du monde"));


        inv.setItem(49, makeBack());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }


    public void openTimersMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_TIMERS);

        ItemStack glass = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,9, 7,8,17, 18,19, 25,26};
        for (int s : corners) inv.setItem(s, glass);
        inv.setItem(4, glass);
        inv.setItem(22, makeBack());

        // Cycle Jour/Nuit (slot 11)
        inv.setItem(11, withLore(
            makeItem(Material.WATCH, ChatColor.YELLOW + "Cycle Jour/Nuit : " + ChatColor.AQUA + CYCLE_MIN[idxCycle] + " min"),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));

        // PVP (slot 12)
        inv.setItem(12, withLore(
            makeItem(Material.IRON_SWORD, ChatColor.YELLOW + "PVP : " + ChatColor.AQUA + PVP_MODES[idxPvp]),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));

        // Annonce des rôles (slot 13)
        inv.setItem(13, withLore(
            makeItem(Material.PAPER, ChatColor.YELLOW + "Annonce des rôles : " + ChatColor.AQUA + ANN_MODES[idxAnnonce]),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));

        // Meetup (slot 15)
        inv.setItem(15, withLore(
            makeItem(Material.NETHER_STAR, ChatColor.YELLOW + "Meetup : " + ChatColor.AQUA + MEETUP_MIN[idxMeetup] + " min"),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));
       
     // Exemple de rendu
        int curEp = EPISODE_MIN[idxEpisode];
        inv.setItem(14, withLore(
        	    makeItem(Material.WATCH, ChatColor.GOLD + "Durée d’un épisode: " + ChatColor.WHITE + curEp + " min"),
        	    ChatColor.GRAY + "Clic gauche: suivant",
        	    ChatColor.GRAY + "Clic droit : précédent"));




        p.openInventory(inv);
    }
    
    

    private void openScenarios(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SCENS);
        ItemStack green = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,7,8, 9,17, 36,44, 45,46,52,53};
        for (int s : corners) inv.setItem(s, green);
        inv.setItem(4, green);

        // TODO : place ici tes items de scénarios UHC
        inv.setItem(49, makeBack());
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.CLICK, 1f, 1f);
    }

    public void openLimitesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_LIMITES);

        ItemStack glass = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,9, 7,8,17, 18,19, 25,26};
        for (int s : corners) inv.setItem(s, glass);
        inv.setItem(4, glass);
        inv.setItem(22, makeBack());

        // Rangée du haut : protections
        inv.setItem(10, withLore(makeItem(Material.IRON_CHESTPLATE, ChatColor.YELLOW + "Protection Fer : " + ChatColor.AQUA + protFer),
                ChatColor.GRAY + "0..4 | G/D = +/-"));
        inv.setItem(11, withLore(makeItem(Material.DIAMOND_CHESTPLATE, ChatColor.YELLOW + "Protection Diamant : " + ChatColor.AQUA + protDia),
                ChatColor.GRAY + "0..4 | G/D = +/-"));

        // Sharpness
        inv.setItem(13, withLore(makeItem(Material.IRON_SWORD, ChatColor.YELLOW + "Tranchant Fer : " + ChatColor.AQUA + sharpFer),
                ChatColor.GRAY + "0..5 | G/D = +/-"));
        inv.setItem(14, withLore(makeItem(Material.DIAMOND_SWORD, ChatColor.YELLOW + "Tranchant Diamant : " + ChatColor.AQUA + sharpDia),
                ChatColor.GRAY + "0..5 | G/D = +/-"));

        // Arcs
        inv.setItem(16, withLore(makeItem(Material.BOW, ChatColor.YELLOW + "Power : " + ChatColor.AQUA + power),
                ChatColor.GRAY + "0..5 | G/D = +/-"));

        // Rangée du bas : Punch + Knockback
        inv.setItem(20, withLore(makeItem(Material.FEATHER, ChatColor.YELLOW + "Punch : " + ChatColor.AQUA + punch),
                ChatColor.GRAY + "0..2 | G/D = +/-"));
        inv.setItem(24, withLore(makeItem(Material.STICK, ChatColor.YELLOW + "Knockback : " + ChatColor.AQUA + knockback),
                ChatColor.GRAY + "0..2 | G/D = +/-"));

        p.openInventory(inv);
    }


    /* =========================
       EVENTS
       ========================= */

    // Option A (recommandée si tu veux centraliser) :
    // UHCConfig ouvre le menu quand on clique l’item "Config UHC".
    // Si tu gardes un handler similaire dans LGUHC, supprime l’un des deux pour éviter les doublons.
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta() || !it.getItemMeta().hasDisplayName())
            return;

        String dn = ChatColor.stripColor(it.getItemMeta().getDisplayName());
        if (!dn.equalsIgnoreCase("Config UHC")) return;

        e.setCancelled(true);
        openMenu(e.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
    	
    	
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR || !cur.hasItemMeta() || !cur.getItemMeta().hasDisplayName())
            return;

        String title = ChatColor.stripColor(e.getView().getTitle());
        String name  = ChatColor.stripColor(cur.getItemMeta().getDisplayName());
        boolean left  = e.getClick().isLeftClick();
        boolean right = e.getClick().isRightClick();

        // Empêche toute prise/déplacement dans nos GUIs
        if (title.equalsIgnoreCase(ChatColor.stripColor(TITLE_UHC_MAIN)) ||
            title.equalsIgnoreCase(ChatColor.stripColor(TITLE_TIMERS)) ||
            title.equalsIgnoreCase(ChatColor.stripColor(TITLE_LIMITES)) ||
            title.equalsIgnoreCase(ChatColor.stripColor(TITLE_INVENTAIRE)) ||
            title.equalsIgnoreCase(ChatColor.stripColor(TITLE_BORDURE))) {
            e.setCancelled(true);
            if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
        }

        // --- MENU PRINCIPAL UHC ---
        if (title.equalsIgnoreCase(ChatColor.stripColor(TITLE_UHC_MAIN))) {
            if (name.equalsIgnoreCase("Timers")) {
                p.closeInventory(); openTimersMenu(p); return;
            }
            if (name.equalsIgnoreCase("Scénarios")) {
                p.closeInventory(); /* ouvre ton menu scénarios côté main si besoin */ return;
            }
            if (name.equalsIgnoreCase("Limites")) {
                p.closeInventory(); openLimitesMenu(p); return;
            }
            if (name.equalsIgnoreCase("Inventaire")) {
                p.closeInventory(); openInventaireMenu(p); return;
            }
            if (name.equalsIgnoreCase("Bordure")) {
                p.closeInventory(); openBordureMenu(p); return;
            }
            if (name.equalsIgnoreCase("Retour")) {
                p.closeInventory(); return;
            }
            
            if (name.equalsIgnoreCase("Prégénération du monde")) {
                p.closeInventory();
                main.startPregeneration(p); // appelle la méthode du main
                return;
            }

            
            return;
        }
        

        // --- TIMERS ---
        if (title.equalsIgnoreCase(ChatColor.stripColor(TITLE_TIMERS))) {
        	if (name.startsWith("Cycle Jour/Nuit")) {
        	    if (left)  idxCycle = incIdx(idxCycle, CYCLE_MIN.length);
        	    if (right) idxCycle = decIdx(idxCycle, CYCLE_MIN.length);

        	    // si la game est en cours, applique immédiatement
        	    if (main.getGameManager().isGameStarted()) {
        	        main.getGameManager().startOrRestartDayNightTask(getSelectedCycleMinutes());
        	    }

        	    openTimersMenu(p);
        	    return;
        	}

            if (name.startsWith("PVP")) {
                if (left)  idxPvp = incIdx(idxPvp, PVP_MODES.length);
                if (right) idxPvp = decIdx(idxPvp, PVP_MODES.length);
                openTimersMenu(p); return;
            }
            if (name.startsWith("Annonce des rôles")) {
                if (left)  idxAnnonce = incIdx(idxAnnonce, ANN_MODES.length);
                if (right) idxAnnonce = decIdx(idxAnnonce, ANN_MODES.length);
                openTimersMenu(p); return;
            }
            if (name.startsWith("Meetup")) {
                if (left)  idxMeetup = incIdx(idxMeetup, MEETUP_MIN.length);
                if (right) idxMeetup = decIdx(idxMeetup, MEETUP_MIN.length);
                openTimersMenu(p); return;
            }
            if (name.startsWith("Durée d’un épisode")) {
                if (left)  idxEpisode = incIdx(idxEpisode, EPISODE_MIN.length);
                if (right) idxEpisode = decIdx(idxEpisode, EPISODE_MIN.length);
                openTimersMenu(p);
                return;
            }

            if (name.equalsIgnoreCase("Retour")) { p.closeInventory(); openMenu(p); }
            return;
        }

     // --- LIMITES ---
        if (title.equalsIgnoreCase(ChatColor.stripColor(TITLE_LIMITES))) {
            // petite trace debug pour voir EXACTEMENT le nom cliqué
            // p.sendMessage(ChatColor.GRAY + "[DEBUG] Click LIMITES: " + name);

            // on retire la partie après ":" pour comparer un libellé stable
            String base = name;
            int idx = base.indexOf(':');
            if (idx != -1) base = base.substring(0, idx).trim(); // ex: "Protection (Fer)"

            switch (base) {
                case "Protection (Fer)":
                    if (left)  protFer = incWrap(protFer, 0, 4);
                    if (right) protFer = decWrap(protFer, 0, 4);
                    openLimitesMenu(p);
                    return;

                case "Protection (Diamant)":
                    if (left)  protDia = incWrap(protDia, 0, 4);
                    if (right) protDia = decWrap(protDia, 0, 4);
                    openLimitesMenu(p);
                    return;

                case "Tranchant (Fer)":
                    if (left)  sharpFer = incWrap(sharpFer, 0, 5);
                    if (right) sharpFer = decWrap(sharpFer, 0, 5);
                    openLimitesMenu(p);
                    return;

                case "Tranchant (Diamant)":
                    if (left)  sharpDia = incWrap(sharpDia, 0, 5);
                    if (right) sharpDia = decWrap(sharpDia, 0, 5);
                    openLimitesMenu(p);
                    return;

                case "Power":
                    if (left)  power = incWrap(power, 0, 5);
                    if (right) power = decWrap(power, 0, 5);
                    openLimitesMenu(p);
                    return;

                case "Punch":
                    if (left)  punch = incWrap(punch, 0, 2);
                    if (right) punch = decWrap(punch, 0, 2);
                    openLimitesMenu(p);
                    return;

                case "Knockback":
                    if (left)  knockback = incWrap(knockback, 0, 2);
                    if (right) knockback = decWrap(knockback, 0, 2);
                    openLimitesMenu(p);
                    return;

                case "Retour":
                    p.closeInventory();
                    openMenu(p);
                    return;

                default:
                    return;
            }
        }


        // --- INVENTAIRE ---
        if (title.equalsIgnoreCase(ChatColor.stripColor(TITLE_INVENTAIRE))) {
            if (cur.getType() == Material.CHEST && name.startsWith("Inventaire de départ")) {
                if (left) {
                    // Save inv actuel
                    startInvTemplate = p.getInventory().getContents().clone();
                    p.sendMessage(ChatColor.GREEN + "Inventaire de départ enregistré depuis votre inventaire.");
                } else if (right) {
                    startInvTemplate = null;
                    p.sendMessage(ChatColor.YELLOW + "Inventaire de départ effacé.");
                }
                openInventaireMenu(p); return;
            }
            if (cur.getType() == Material.ENDER_CHEST && name.startsWith("Inventaire de mort")) {
                if (left) {
                    p.closeInventory();
                    openDeathInvEditor(p);
                }
                return;
            }
            if (name.equalsIgnoreCase("Retour")) { p.closeInventory(); openMenu(p); }
            return;
        }

        // --- BORDURE ---
        if (title.equalsIgnoreCase(ChatColor.stripColor(TITLE_BORDURE))) {
        	if (name.startsWith("Bordure de départ")) {
        	    if (left)  idxBorderStart = incIdx(idxBorderStart, BORDER_SIZES.length);
        	    if (right) idxBorderStart = decIdx(idxBorderStart, BORDER_SIZES.length);

        	    // ICI: BORDER_SIZES contient directement la taille TOTALE voulue (diamètre)
        	    int newSize = BORDER_SIZES[idxBorderStart]; // 100 => 100x100

        	    // Sauvegarde côté main (pour le scoreboard avant start, etc.)
        	    main.setTailleBordureDepart(newSize);

        	    // Applique à la vraie world border
        	    World world = Bukkit.getWorlds().get(0);
        	    WorldBorder wb = world.getWorldBorder();
        	    wb.setCenter(0.5, 0.5);   // centre propre
        	    wb.setSize(newSize*2);      // taille TOTALE (diamètre), pas de *2

        	    // Contraindre la fin < départ
        	    if (BORDER_SIZES[idxBorderEnd] >= BORDER_SIZES[idxBorderStart]) {
        	        int newEnd = idxBorderStart - 1;
        	        if (newEnd < 0) newEnd = 0;
        	        idxBorderEnd = newEnd;
        	    }

        	    openBordureMenu(p);
        	    return;
        	}



            if (name.startsWith("Début réduction")) {
                if (left)  idxBorderTime = incIdx(idxBorderTime, BORDER_TIMES_MIN.length);
                if (right) idxBorderTime = decIdx(idxBorderTime, BORDER_TIMES_MIN.length);
                openBordureMenu(p); return;
            }
            if (name.startsWith("Vitesse")) {
                if (left)  idxBorderSpeed = incIdx(idxBorderSpeed, BORDER_SPEEDS.length);
                if (right) idxBorderSpeed = decIdx(idxBorderSpeed, BORDER_SPEEDS.length);
                openBordureMenu(p); return;
            }
            if (name.startsWith("Bordure de fin")) {
                if (left)  idxBorderEnd = incIdx(idxBorderEnd, BORDER_SIZES.length);
                if (right) idxBorderEnd = decIdx(idxBorderEnd, BORDER_SIZES.length);
                // enforce fin < départ
                if (BORDER_SIZES[idxBorderEnd] >= BORDER_SIZES[idxBorderStart]) {
                    // recule jusqu’à trouver une valeur < départ
                    while (BORDER_SIZES[idxBorderEnd] >= BORDER_SIZES[idxBorderStart]) {
                        idxBorderEnd = decIdx(idxBorderEnd, BORDER_SIZES.length);
                    }
                }
                openBordureMenu(p); return;
            }
            if (name.equalsIgnoreCase("Retour")) { p.closeInventory(); openMenu(p); }
            return;
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private ItemStack makeItem(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBack() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.RED + "Retour");
        it.setItemMeta(m);
        return it;
    }
    
    private ItemStack createBackItem() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Retour");
        it.setItemMeta(meta);
        return it;
    }


    private ItemStack createColoredGlassPane(DyeColor color, String name) {
        ItemStack item = new ItemStack(Material.STAINED_GLASS_PANE, 1, color.getData());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack withLore(ItemStack it, String... lines) {
        ItemMeta m = it.getItemMeta();
        m.setLore(java.util.Arrays.asList(lines));
        it.setItemMeta(m);
        return it;
    }
    
    private int incWrap(int v, int min, int maxIncl) { // min..maxIncl
        v++;
        if (v > maxIncl) v = min;
        return v;
    }
    private int decWrap(int v, int min, int maxIncl) {
        v--;
        if (v < min) v = maxIncl;
        return v;
    }


    
 // --- Sous-menus 27 slots : même frame (verre vert aux coins + slot 4), flèche retour slot 22 ---
    public void openInventaireMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_INVENTAIRE);

        ItemStack glass = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,9, 7,8,17, 18,19, 25,26};
        for (int s : corners) inv.setItem(s, glass);
        inv.setItem(4, glass);
        inv.setItem(22, makeBack());

        String startLore = (startInvTemplate == null)
                ? ChatColor.RED + "Aucun inventaire défini."
                : ChatColor.GREEN + "Inventaire défini.";

        inv.setItem(11, withLore(
                makeItem(Material.CHEST, ChatColor.YELLOW + "Inventaire de départ"),
                startLore,
                ChatColor.GRAY + "Clic gauche: enregistrer votre inventaire actuel",
                ChatColor.GRAY + "Clic droit : effacer"));

        inv.setItem(15, withLore(
                makeItem(Material.ENDER_CHEST, ChatColor.YELLOW + "Inventaire de mort"),
                ChatColor.GRAY + "Clic gauche: ouvrir l’éditeur 27 slots"));

        p.openInventory(inv);
    }

    private void openDeathInvEditor(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_INV_EDITOR);
        if (deathExtraTemplate != null) {
            for (int i = 0; i < Math.min(27, deathExtraTemplate.length); i++) {
                if (deathExtraTemplate[i] != null) inv.setItem(i, deathExtraTemplate[i].clone());
            }
        }
        p.openInventory(inv);
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        String t = ChatColor.stripColor(e.getView().getTitle());
        if (!t.equalsIgnoreCase(ChatColor.stripColor(TITLE_INV_EDITOR))) return;

        Inventory inv = e.getInventory();
        deathExtraTemplate = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack it = inv.getItem(i);
            deathExtraTemplate[i] = (it == null) ? null : it.clone();
        }
        ((Player)e.getPlayer()).sendMessage(ChatColor.GREEN + "Inventaire de mort sauvegardé.");
    }



    public void openBordureMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_BORDURE);

        ItemStack glass = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,9, 7,8,17, 18,19, 25,26};
        for (int s : corners) inv.setItem(s, glass);
        inv.setItem(4, glass);
        inv.setItem(22, makeBack());

        // Départ (slot 10)
        inv.setItem(10, withLore(
            makeItem(Material.MAP, ChatColor.YELLOW + "Bordure de départ : " + ChatColor.AQUA + BORDER_SIZES[idxBorderStart]),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));

        // Timer de réduction (slot 12)
        inv.setItem(12, withLore(
            makeItem(Material.WATCH, ChatColor.YELLOW + "Début réduction : " + ChatColor.AQUA + BORDER_TIMES_MIN[idxBorderTime] + " min"),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));

        // Vitesse (slot 14)
        inv.setItem(14, withLore(
            makeItem(Material.SUGAR, ChatColor.YELLOW + "Vitesse : " + ChatColor.AQUA + BORDER_SPEEDS[idxBorderSpeed] + " block/s"),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent"));

        // Fin (slot 16)
        inv.setItem(16, withLore(
            makeItem(Material.BARRIER, ChatColor.YELLOW + "Bordure de fin : " + ChatColor.AQUA + BORDER_SIZES[idxBorderEnd]),
            ChatColor.GRAY + "Clic gauche: suivant",
            ChatColor.GRAY + "Clic droit : précédent",
            ChatColor.DARK_GRAY + "(Doit être < départ)"));

        p.openInventory(inv);
    }
    
    private ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    
    public void openStartInvEditor(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_INV_START_EDITOR);

        // bandeau/verres (comme d’hab’ si tu veux)
        ItemStack glass = createColoredGlassPane(DyeColor.GREEN, " ");
        int[] corners = {0,1,7,8, 9,17, 36,44, 45,46,52,53};
        for (int s : corners) inv.setItem(s, glass);
        inv.setItem(4, glass);

        // Légendes
        inv.setItem(2, named(new ItemStack(Material.PAPER), ChatColor.WHITE + "Place les items ici ↓"));
        inv.setItem(47, named(new ItemStack(Material.PAPER), ChatColor.WHITE + "Armure ici →"));

        // Pré-remplir depuis le template (si défini)
        // main: slots GUI 9..44 (36 cases)  => index 0..35
        for (int i = 0; i < 36; i++) {
            ItemStack it = startMainTemplate[i];
            inv.setItem(9 + i, (it == null ? null : it.clone()));
        }

        // armure: GUI 45..48 => [45]=helmet, [46]=chest, [47]=legs, [48]=boots
        for (int i = 0; i < 4; i++) {
            ItemStack it = startArmorTemplate[i];
            inv.setItem(45 + i, (it == null ? null : it.clone()));
        }

        // Bouton retour
        inv.setItem(49, createBackItem());

        // Note: on NE CANCEL PAS les déplacements dans cet inventaire,
        // pour laisser l’host poser/prendre/enchérir librement.
        p.openInventory(inv);
    }
    
    @EventHandler
    public void onStartInvClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        String t = ChatColor.stripColor(e.getView().getTitle());
        if (!t.equalsIgnoreCase(ChatColor.stripColor(TITLE_INV_START_EDITOR))) return;

        Inventory inv = e.getInventory();

        // save main (9..44) -> [0..35]
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(9 + i);
            startMainTemplate[i] = (it == null ? null : it.clone());
        }
        // save armor (45..48) -> [0..3]
        for (int i = 0; i < 4; i++) {
            ItemStack it = inv.getItem(45 + i);
            startArmorTemplate[i] = (it == null ? null : it.clone());
        }

        ((Player)e.getPlayer()).sendMessage(ChatColor.GREEN + "Inventaire de départ sauvegardé.");
    }
    
    @EventHandler
    public void onStartInvClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        String t = ChatColor.stripColor(e.getView().getTitle());
        if (!t.equalsIgnoreCase(ChatColor.stripColor(TITLE_INV_START_EDITOR))) return;

        // On autorise la manipulation dans la grille (9..48),
        // mais on bloque les verres et le bouton retour.
        int raw = e.getRawSlot();

        if (raw < 0) return;

        // Items qu’on veut bloquer (vitres et "Retour")
        ItemStack cur = e.getCurrentItem();
        if (raw == 49 || (cur != null && cur.getType() == Material.STAINED_GLASS_PANE)) {
            e.setCancelled(true);
            if (raw == 49) { // retour
                ((Player)e.getWhoClicked()).closeInventory();
                openInventaireMenu((Player)e.getWhoClicked()); // ton menu “Inventaire”
            }
            return;
        }

        // Si clic dans le bas (inventaire joueur), ne rien cancel, normal.
        // Si clic dans le haut mais en dehors de 9..48 (zones “légende/verres”), on bloque.
        if (raw < e.getInventory().getSize()) {
            boolean inEditableGrid = (raw >= 9 && raw <= 48);
            if (!inEditableGrid) e.setCancelled(true);
        }
    }
    
    public int getSelectedCycleMinutes() {
        return CYCLE_MIN[idxCycle];
    }
    
 // Getter pour GameManager
    public int getEpisodeMinutes() { 
        return EPISODE_MIN[idxEpisode]; 
    }
    
 // "30 secondes" ou "Jour 2"
    public boolean isAnnonceJour2() {
        return ANN_MODES[idxAnnonce].toLowerCase().contains("jour");
    }
    
 // UHCConfig.java
    public int getAnnonceRolesDelaySeconds() {
        // "30 secondes" => 30, "Jour 2" => -1 (signal)
        String mode = ANN_MODES[idxAnnonce];
        return mode.toLowerCase().contains("30") ? 30 : -1;
    }
    
    /** Retourne la liste plate des rôles activés par l’host (pour l’assignation). */
    public List<String> getEnabledRolesFlat() {
        return new ArrayList<>(enabledRoles);
    }
    
    public boolean isRandomCoupleEnabled() {
        // branche-le sur ton “événement aléatoire couple” si tu en as un,
        // sinon retourne false par défaut.
    	// avant: return enabledRandomEvents.contains(...);
    	// maintenant:
    	return isRandomCoupleEnabled();

    }
    
    public boolean isWolfHowlEnabled() {
    	try { return main.isWolfHowlEnabled(); } catch (Throwable t) { return true; }
    }
    
    

    
    

    
    
    
    



    
    




}
