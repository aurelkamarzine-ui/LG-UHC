package fr.sfakeur.lguhc;

import org.bukkit.Bukkit;




import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Score;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabExecutor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import java.lang.reflect.Field;
import java.util.UUID;
import org.bukkit.inventory.meta.SkullMeta;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.lang.reflect.Field;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import fr.sfakeur.lguhc.WolfHandler;










import java.util.*;

public class LGUHC extends JavaPlugin implements Listener {
    private final Map<String, Boolean> uhcScenarios = new LinkedHashMap<>();
    private final Map<String, Boolean> lgScenarios = new LinkedHashMap<>();
    private final Map<String, Integer> timers = new HashMap<>();
    private final Map<String, Boolean> roles = new LinkedHashMap<>();
    private final Map<String, Integer> timerOptions = new HashMap<>();
    private int tailleBordureDepart = 1000;
    private int tailleBordureFinale = 100;
    private final Map<String, Object> limitations = new LinkedHashMap<>();
    private final Set<UUID> givenStars = new HashSet<>();
    private Scoreboard scoreboard;
    private Objective objective;
    private double borderSpeed = 1.0; // valeur par défaut
    private final int[] BORDER_SIZES = {50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000, 2250, 2500};
    private GameManager gameManager;
    private BukkitRunnable scoreboardTask;
    private final Map<String, Boolean> scenarios = new HashMap<>();
    private boolean evenementActif;
    private List<String> listeEvenements = new ArrayList<>(); // Tous les événements possibles
    private String nomPartie = "Loup-Garou UHC"; // valeur par défaut
    private final Map<Player, Boolean> enAttenteNomPartie = new HashMap<>();
    private final List<String> rolesLoups = Arrays.asList(
    	    "Grand Méchant Loup",
    	    "Infect Père des Loups",
    	    "Loup-Garou",
    	    "Loup-Garou Affamé",
    	    "Loup-Garou Alpha",
    	    "Loup-Garou Amnésique",
    	    "Loup-Garou Amnésique v1",
    	    "Loup-Garou Brumeux",
    	    "Loup-Garou Cannibale",
    	    "Loup-Garou Chanceux",
    	    "Loup-Garou Craintif",
    	    "Loup-Garou Déloyal",
    	    "Loup-Garou Faussaire",
    	    "Loup-Garou Feutré",
    	    "Loup-Garou Funeste",
    	    "Loup-Garou Grimeur",
    	    "Loup-Garou Hurleur",
    	    "Loup-Garou Mystique",
    	    "Loup-Garou Peureux",
    	    "Loup-Garou Perfide",
    	    "Loup-Garou Rustaud",
    	    "Loup-Garou Sanguinaire",
    	    "Loup-Garou Ténébreux",
    	    "Loup-Garou Troubleur",
    	    "Loup-Garou Vengeur",
    	    "Louveteau",
    	    "Vilain Petit Loup"
    	);

    	private final String LG_HEAD_BLACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0=";
    	private final String LG_HEAD_RED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjlhNTFmMjdkMmQ5Mzg4OTdiYzQyYTNmZTJjMzEzNWRhMjY3MTY4NmY1NzgyNDExNWY4ZjhkYTc4YSJ9fX0=";
    	private final String LG_HEAD_ORANGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODViZWMxNTc0ZGUzNzNkZmI3NzVhYjU4YjJjMWU0NjIxZDkyYzZkYWFjN2M2YTc0ZDc4ZmI3MGZmZjRkMCJ9fX0=";

    	private final Set<String> enabledRoles = new HashSet<>();


    	private final List<String> rolesSolitaires = Arrays.asList(
    		    "Ange", "Arnacoeur", "Assassin", "Barbare", "Enfant lune", "Ensorceleur", "Feu Follet", 
    		    "Fou du bus", "Illusionniste", "Imitateur", "Joueur de Flûte", "Mastermind", "Médium", 
    		    "Nécromancien", "Pyromane", "Rival", "Ronin", "Voyou", "Voleuse de Légumes"
    		);

    	private final Set<String> enabledSolitaires = new HashSet<>();
    	
    	private final List<String> rolesHybrides = Arrays.asList(
    		    "Auramancien", "Chien-Loup", "Cupidon", "Cupidon v2", "Enfant Sauvage", "Escroc", 
    		    "L'indécis", "Louve", "Mouton", "Renégat", "Servant", 
    		    "Trublion", "Voleur"
    		);

    	private final Set<String> enabledHybrides = new HashSet<>();
    	
    		// --- Villageois: listes par catégorie (sans emoji), triées alpha ---
    	private final List<String> rolesVillageMajeurs = new ArrayList<>(Arrays.asList(
    		   "Analyste","Astronome","Bohémienne","Constellationniste","Conteuse","Détective","Jumeau",
    		   "Montreur d'Ours","Occultiste","Oracle","Prêtre","Prêtresse","Prophétesse","Renard","Voyante"
    		));
    	private final List<String> rolesVillageMineurs = new ArrayList<>(Arrays.asList(
    		    "Aubergiste","Bibliothécaire","Chaman","Espion","Fossoyeur","Interprète","Marchande de Fruits",
    		    "Vaudouiste","Vieux Sage","Anxieux","Garde Forestier","Lynx","Ménestrel"
    		));
    	private final List<String> rolesVillageAutres = new ArrayList<>(Arrays.asList(
    		    "Ancien","Avocat","Bienfaiteur","Braconnier","Bouc Émissaire","Chasseur","Citoyen","Corbeau",
    		    "Druide","Ermite","Garde","Idiot du Village","Lapin","Mineur","Mire","Petite Fille",
    		    "Rebouteux","Salvateur","Sœur","Servante Dévouée","Serviteur","Simple Villageois",
    		    "Sorcière","Enchanteresse","Le déchus"
    		));

    		// états activés pour les 3 groupes
    	private final Set<String> enabledVillageMajeurs = new HashSet<>();
    	private final Set<String> enabledVillageMineurs = new HashSet<>();
    	private final Set<String> enabledVillageAutres = new HashSet<>();

    		// pagination par joueur
    	private final Map<UUID, Integer> villagePage = new HashMap<>();

    		// textures têtes (poudre de béton)
    	private static final String TEX_BLACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0=";
    	private static final String TEX_BLUE_DARK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWRhNzZkNmRkY2RiYjQ5NzMzMzIxNTU3OTZjMDMwY2ZhMmZlODU1Y2U1MzllODEzZTU4OGM2MWQ0ZDZiY2E1YyJ9fX0="; // majeurs
    	private static final String TEX_CYAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDgxODk5ZDA3ZWQ4YTU0NDFiYjNmYWYxZTE5ZGNjOTc1MzdjYmRmOTFjNzAzZWJmODVhYzMxYzg4MTk4ZmMifX19"; // mineurs
    	private static final String TEX_BLUE_LIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjllNGEzOTliZWZkOTY3ZWMyODJjNzE5ZmJmZDY0NDg2MzI4MTM3NGRjYjgxMzdhNzJmN2M0ZDAxM2E3M2YifX19"; // autres
    	
    	private int autresAppleRate = 0;     // 0..100 %
    	private int autresFlintRate = 0;     // 0..100 %
    	private int autresXpBoost   = 0;     // +0..+100 %
    	private int autresResistPct = 0;     // 0..100 %
    	private int autresStrengthPct = 0;   // 0..100 %
    	private int autresSpeedPct = 0;      // 0..100 %
    	private int autresDiamondLimit = 1;  // 1..24 puis Illimité (-1)
    	
    	// Bâtiments (état)
    	private boolean batMaisonHonneur = false;
    	private boolean batMaisonVote = false;
    	private int batDistanceMin = 50; // 50 / 100 / 150
    	
    	// --- Options Loups (état) ---
    	private boolean optLoupChat = false;       // chat des loups
    	private boolean optListeATrous = false;    // liste à trous ON/OFF
    	private int optNbLoupsListe = 0;           // 0..(loupsActifs-1)
    	
    	public UHCConfig getUhcConfig() { return uhcConfig; }

        private RoleService roleService;
        private DeathManager deathManager;



    	public GameManager getGameManager() {  // getter pratique
    	    return gameManager;
    	}
    	
    	// --- Evénements aléatoires ---
    	private final List<String> randomEvents = new ArrayList<>(Arrays.asList(
    	    "Aurore Boréale",
    	    "Coup de Foudre",
    	    "Couple Aléatoire",
    	    "Cupidon Indécis",
    	    "Cupidon Rancunier",
    	    "Exposed",
    	    "Exposed Inversé",
    	    "Feu de camp",
    	    "Folie",
    	    "Gri-gri d'immunité",
    	    "Loup Bourré",
    	    "Lune de Sang",
    	    "Mère Louve",
    	    "Not All Loups",
    	    "Rumeurs",
    	    "Temporalité",
    	    "Traumatisme",
    	    "Zizanie"
    	));
    	private final Set<String> enabledRandomEvents = new HashSet<>();

    	// textures têtes (béton noir / jaune)
    	private static final String TEX_YELLOW = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjdhOTk2NGY1NzJmZDAzYzMyZGZhMjU4NjE1NWZhM2QxMGU2MjdkZjc3OWE0MWYyNjJmZGU4MmJmYjQxYmEwIn19fQ==";
    	
    	// % par événement (0..100). Par défaut absent = 0%
    	final Map<String, Integer> randomEventPct = new HashMap<>();
    	
        // LGUHC.java (champ)
        private UHCConfig uhcConfig;
        
        private Scoreboard preSb;
        private Objective preObj;

        private BukkitRunnable preSbTask;
        
        

        public RoleService getRoleService() { return roleService; }
        
     // Etat Bâtiments
        private boolean batCompositionCachee = false; // ⭐ Composition Cachée ON/OFF

        public interface AlignHandler {
            /** Appelé chaque seconde de jeu. */
            void tickPerSecond(int elapsedSec);

            /** Appelé quand un joueur tue un autre joueur. */
            void onPlayerKill(Player killer, Player victim);

            /** Appelé juste après la mort d’un joueur (encore en event). */
            void onPlayerDeath(Player dead);

            /** Appelé au début d’un épisode (episodeNumber = 1,2,3,...) */
            void onEpisodeStart(int episodeNumber);

            /** Routage des /lg <sub> [args] pour les rôles gérés par ce handler. */
            boolean handleSubCommand(String sub, Player sender, String[] args);
        }
        
     // LGUHC.java
        public DeathManager getDeathManager() { return deathManager; }
        
        private boolean optLoupHowl = true; // par défaut: ON

        public boolean isWolfHowlEnabled() { return optLoupHowl; }
        public void setWolfHowlEnabled(boolean enabled) { this.optLoupHowl = enabled; }
        
     // 0 = OFF, 9 = LG 9, 10 = LG 10
        private int voteMode = 0;
        
        private WolfHandler wolfHandler;
        public WolfHandler getWolfHandler() { return wolfHandler; }



        
        


        

        
    	





    



        @Override
        public void onEnable() {
            getLogger().info("§aLG UHC activé");
            
            

            // 1) Enregistrer CE plugin comme listener (une seule fois)
            getServer().getPluginManager().registerEvents(this, this);

            // 2) (Optionnel) ton UHCConfig en mémoire
            uhcConfig = new UHCConfig(this);
            getServer().getPluginManager().registerEvents(uhcConfig, this);

            // 3) GameManager d'abord (on s'en sert juste après)
            gameManager = new GameManager(this, 500, uhcConfig);
            getServer().getPluginManager().registerEvents(gameManager, this);

            // 4) Options 100% mémoire
            this.optLoupHowl = true;          // hurlement ON par défaut
            gameManager.setVoteMode(0);       // 0 = OFF
            gameManager.forceVoteLG9Lock(false); // pas verrouillé au démarrage

            // 5) RoleService + handlers
            roleService = new RoleService(gameManager, this);
            getServer().getPluginManager().registerEvents(roleService, this);
            getServer().getPluginManager().registerEvents(roleService.getVillageHandler(), this);
            getServer().getPluginManager().registerEvents(roleService.getWolfHandler(), this);
            getServer().getPluginManager().registerEvents(roleService.getNeutralHandler(), this);
            getServer().getPluginManager().registerEvents(roleService.getHybridHandler(), this);

            // 6) DeathManager
            deathManager = new DeathManager(this, roleService);
            getServer().getPluginManager().registerEvents(deathManager, this);

            // Lier GM ↔ RS
            gameManager.setRoleService(roleService);

            // 7) Remise à neuf à chaque lancement
            try { roleService.fullResetForNewRun(); } catch (Throwable t) { t.printStackTrace(); }
            try { deathManager.fullResetForNewRun(); } catch (Throwable t) { t.printStackTrace(); }

            // 8) Normaliser les joueurs déjà connectés
            try { normalizeOnlinePlayersForNewRun(); } catch (Throwable t) { t.printStackTrace(); }
            org.bukkit.Bukkit.getScheduler().runTask(this, () -> {
                try { normalizeOnlinePlayersForNewRun(); } catch (Throwable t) { t.printStackTrace(); }
            });

            // 9) Si tu synchronises les rôles activés depuis tes compteurs en mémoire
            try { syncAllEnabledFromCounts(); } catch (Throwable ignored) {}
            
            org.bukkit.Bukkit.getScheduler().runTask(this, () -> {
                try { hardResetOnlinePlayers(); } catch (Throwable t) { t.printStackTrace(); }

                try { if (roleService != null) roleService.fullResetForNewRun(); } catch (Throwable ignored) {}
                try { if (deathManager != null) deathManager.fullResetForNewRun(); } catch (Throwable ignored) {}
                try { if (wolfHandler  != null) wolfHandler.resetWolfListAnnounced(); } catch (Throwable ignored) {}
            });
            

         // Nettoyage + distribution correcte des items config à TOUT LE MONDE connecté
            org.bukkit.Bukkit.getScheduler().runTask(this, () -> {
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                	grantStartItemsIfAdmin(p);
                }
            });

            getLogger().info("Plugin LGUHC prêt.");
            
            
        }

        
        @Override
        public void onDisable() {
            getConfig().set("optionsLoups.hurlement", optLoupHowl);
            saveConfig();
        }
    
        
        @org.bukkit.event.EventHandler
        public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
            try {
                if (gameManager != null && gameManager.isHermitDead(e.getPlayer().getUniqueId())) {
                    e.setQuitMessage(null); // pas de message "X a quitté le jeu"
                }
            } catch (Throwable ignored) {}
        }
        




    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("sauvegarder")) {
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Usage: /sauvegarder <nom>");
                return true;
            }
            saveConfig(p, args[0]);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("charger")) {
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Usage: /charger <nom>");
                return true;
            }
            loadConfig(p, args[0]);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("mes_saves")) {
            List<String> saves = getPlayerSaves(p);
            if (saves.isEmpty()) {
                p.sendMessage(ChatColor.YELLOW + "Tu n'as encore aucune sauvegarde.");
            } else {
                p.sendMessage(ChatColor.AQUA + "Tes sauvegardes :");
                for (String save : saves) {
                    p.sendMessage(ChatColor.GRAY + " - " + save);
                }
            }
            return true;
        }
        if (label.equalsIgnoreCase("delete_save")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cCommande réservée aux joueurs.");
                return true;
            }


            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "§cUsage: /delete_save <nom>");
                return true;
            }

            String saveName = args[0];
            UUID uuid = p.getUniqueId();
            File file = new File(getDataFolder(), "saves/" + uuid.toString() + "/" + saveName + ".yml");

            if (!file.exists()) {
                p.sendMessage(ChatColor.RED + "§cLa sauvegarde '" + saveName + "' n'existe pas.");
                return true;
            }

            boolean success = file.delete();
            if (success) {
                p.sendMessage(ChatColor.GREEN + "§aLa sauvegarde '" + saveName + "' a été supprimée.");
            } else {
                p.sendMessage(ChatColor.RED + "§cErreur lors de la suppression de '" + saveName + "'.");
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("lgstart")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Commande uniquement joueur.");
                return true;
            }
            
            if (!isAdmin(p)) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Tu n’as pas la permission.");
                return true;
            }
            
            if (gameManager == null) {
                p.sendMessage(ChatColor.RED + "Erreur : GameManager non initialisé.");
                return true;
            }
            if (gameManager.isGameStarted()) {
                p.sendMessage(ChatColor.RED + "La partie est déjà en cours !");
                return true;
            }

            p.getInventory().clear();
            gameManager.startGame();
            return true;
        }

        
     // dans ton onCommand de LGUHC (ou du main qui gère /lg)
        if (cmd.getName().equalsIgnoreCase("lg")) {
            if (!(sender instanceof Player)) return true;
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "Usage: /lg <sous-commande>");
                return true;
            }
            String sub = args[0];
            String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            boolean ok = gameManager.getRoleService().handleLgSubCommand(sub, p, subArgs);
            if (!ok) p.sendMessage(ChatColor.RED + "Tu n’as pas la capacité ou usage incorrect.");
            return true;
        }



        return false;
    }
    
    /* ==== Helpers items config ==== */

    private static final String NAME_UHC = ChatColor.GOLD + "Config UHC";
    private static final String NAME_LG  = ChatColor.RED  + "Config LG";


    public void removeConfigItems(Player p) {
        // inventaire principal
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isConfigItem(contents[i])) contents[i] = null;
        }
        p.getInventory().setContents(contents);

        // curseur éventuel
        ItemStack cursor = p.getItemOnCursor();
        if (isConfigItem(cursor)) p.setItemOnCursor(null);

        p.updateInventory();
    }

    /* === Donne les items au join seulement si la game n’est PAS lancée et si le joueur ne les a pas === */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        setupPregameBoard(player);

        // Nettoie toujours d'abord (au cas où)
        removeConfigItems(player);

        // Si la game est en cours -> pas d’items d’admin
        if (gameManager != null && gameManager.isGameStarted()) {
            return;
        }

        // Donne UNIQUEMENT aux admins/OP
        if (isAdmin(player)) {
            // Slots fixes pour éviter les doublons
            player.getInventory().setItem(0, getUHCConfigItem());
            player.getInventory().setItem(1, getLGHeadItem());
            player.updateInventory();
        }

        // Fix Ancien
        RoleService.RoleState s = roleService.get(player);
        if (s != null && s.roleId == RoleService.RoleId.ANCIEN && (!s.ancienResistanceActive || s.ancienResUsed)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
            
        }
        
        // Hard reset de tous les joueurs déjà en ligne (ex: /reload)
        org.bukkit.Bukkit.getScheduler().runTask(this, () -> hardResetOnlinePlayers());
    }


    private boolean hasItem(Player player, String displayName) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                if (ChatColor.stripColor(item.getItemMeta().getDisplayName())
                        .equalsIgnoreCase(displayName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Bloque drop / move de ces items (si jamais tu veux qu’ils soient “fixes” hors game aussi) */
    @EventHandler
    public void onItemInventoryClick(InventoryClickEvent event) {
        ItemStack cur = event.getCurrentItem();
        if (isConfigItem(cur)) event.setCancelled(true);
    }
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isConfigItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }



    // ✅ Empêche de poser la tête ou le coffre
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.equalsIgnoreCase("Config UHC") || name.equalsIgnoreCase("Config LG")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Cet objet ne peut pas être posé.");
        }
    }
    
    

    @org.bukkit.event.EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        // clic droit uniquement
        org.bukkit.event.block.Action act = e.getAction();
        if (act != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
         && act != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        org.bukkit.entity.Player p = e.getPlayer();
        if (p == null) return;

        org.bukkit.inventory.ItemStack it = p.getItemInHand();
        if (it == null || it.getType() != org.bukkit.Material.SKULL_ITEM) return; // tête (1.8)
        // (optionnel) si tu veux forcer la tête de joueur : short data == 3
        if (it.getDurability() != 3) return;

        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String name = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        if (name == null) return;

        // accepte “Config LG” avec ou sans mise en forme
        if (!name.equalsIgnoreCase("Config LG")) return;

        e.setCancelled(true);
        openLGMenu(p); // ✅ méthode de LGUHC
    }





    // ✅ Menu Config UHC vide
    private void openUHCMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Config UHC");
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }

    private void openLGMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§lConfig LG");

        ItemStack glass = createColoredGlassPane(DyeColor.RED, " ");
        inv.setItem(0, glass);
        inv.setItem(1, glass);
        inv.setItem(7, glass);
        inv.setItem(8, glass);
        inv.setItem(9, glass);
        inv.setItem(17, glass);
        inv.setItem(36, glass);
        inv.setItem(44, glass);
        inv.setItem(45, glass);
        inv.setItem(46, glass);
        inv.setItem(52, glass);
        inv.setItem(53, glass);
        inv.setItem(4, glass); // centre haut

        inv.setItem(10, createItem(Material.NETHER_STAR, ChatColor.YELLOW + "" + ChatColor.BOLD + "Autres"));
        inv.setItem(19, getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTk0Yzc5ZjAzZTE5MWI5MzQ3N2Y3YzE5NTU3NDA4ZjdhZjRmOTY2MGU1ZGZiMDY4N2UzYjhlYjkyZmJkM2FlMSJ9fX0=", ChatColor.YELLOW + "" + ChatColor.BOLD + "Bâtiments"));
        inv.setItem(28, getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjVlMDhlNGFjNGQ2M2ExYzM4Y2ZiMmE0ZTQxODVkYzJhMDEyZDQwZWI1YjM1OGJhZmIwM2RiNzA3MjI1NDFkMyJ9fX0=", ChatColor.RED + "" + ChatColor.BOLD + "Options Loups"));
        inv.setItem(37, createItem(Material.NAME_TAG, ChatColor.GOLD + "Nom de la partie"));
        
     // Têtes autour du centre (slot 4)
        inv.setItem(22, getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGI0YWNjM2YyNmU1NGI5MDA0MWZlOTVjNGU2MmI4ZTdiNjNhZWVmN2E5ZmMzOWNkOWJjN2U4ZWI5MWEzMzQ3MCJ9fX0=", ChatColor.GOLD + "" + ChatColor.BOLD + "Solitaires")); // haut
        inv.setItem(30,  getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDBjYzBhMTI1Nzc4OThlNzE2N2M0M2NmOWZlOGNiMjg5NTYzOTIxYjcwOTQ2MDEyYTYwODkzY2FiNzZlNTQ5In19fQ==", ChatColor.AQUA + "" + ChatColor.BOLD + "Villageois")); // gauche
        inv.setItem(40, getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhZGExZDFmOTdmODExZGI2MjNjMDBjNjI4OGYxZTBjNGRlM2JjYzQxMzY0YzZhZGM2ZjM3M2RkZmI0MTQzZiJ9fX0=", ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Hybrides")); // bas
        inv.setItem(32,  getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzY4ZDQzMTI5MzliYjMxMTFmYWUyOGQ2NWQ5YTMxZTc3N2Y4ZjJjOWZjNDI3NTAxY2RhOGZmZTNiMzY3NjU4In19fQ==", ChatColor.DARK_RED + "" + ChatColor.BOLD + "Loups-Garous")); // droite

        // 1. Event Aléatoire (slot 43)
        inv.setItem(16, createSkull(
            "§e§lÉvènements Aléatoires",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzE4MDc5ZDU4NDc0MTZhYmY0NGU4YzJmZWMyY2NkNDRmMDhkNzM2Y2E4ZTUxZjk1YTQzNmQ4NWY2NDNmYmMifX19"
        ));

     // === Bouton Vote (slot 25) ===
        GameManager gm = getGameManager();
        boolean locked = gm.isVoteLockedLG9();
        int vm = gm.getVoteMode();

        String voteTitle;
        String voteTex = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWVlOGQ2ZjVjYjdhMzVhNGRkYmRhNDZmMDQ3ODkxNWRkOWViYmNlZjkyNGViOGNhMjg4ZTkxZDE5YzhjYiJ9fX0=";

        if (vm == 0)      voteTitle = "§e§lVote : §cOFF";
        else if (vm == 9) voteTitle = "§e§lVote : §cLG 9";
        else              voteTitle = "§e§lVote : §cLG 10";

        ItemStack voteSkull = createSkull(voteTitle, voteTex);
        ItemMeta meta = voteSkull.getItemMeta();
        if (locked) {
            meta.setLore(java.util.Arrays.asList(
                ChatColor.DARK_PURPLE + "Verrouillé : LG 9",
                ChatColor.GRAY + "(activé par le rôle §dLe Déchu§7)"
            ));
        } else {
            meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Clic : change",
                ChatColor.DARK_GRAY + "OFF → LG 9 → LG 10 → OFF"
            ));
        }
        voteSkull.setItemMeta(meta);
        inv.setItem(25, voteSkull);



        // 3. Options Couple (slot 25) — version initiale = None
        boolean coupleEnabled = false; // à stocker et charger
        inv.setItem(43, createSkull(
            coupleEnabled ? "§d§lOptions Couple : §a§lOn" : "§d§lOptions Couple : §c§lNone",
            coupleEnabled ?
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGMzZmNlZjg5OTg5MDVkYzdjZTc4MDNkZDYxYzQ5YjEzMTNiYjQxMTJjZDEwMjA5Zjk3ZmUxZTM4ZmJkZjZlIn19fQ==" :
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0="
        ));

        // 4. Loup-Garou Solitaire (slot 16) — version initiale = Off
     // Slot 34 : Loup-Garou Solitaire (lis l'état depuis RoleService)
        boolean lgSoloEnabled = roleService.isSolitaireEnabled();
        inv.setItem(34, createSkull(
            lgSoloEnabled ? "§2§lLoup-Garou Solitaire : §a§lOn" : "§2§lLoup-Garou Solitaire : §c§lOff",
            lgSoloEnabled
                ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWNjMTc5Y2YzYjMyNzkyZDY4OGM4Y2M4NzZlNzU3MmVlOGYxZDMwMTEzNmUxNGQxYTUzZmRlMWY2MzdhOTYwZSJ9fX0="
                : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0="
        ));



        player.openInventory(inv);
    }



    // ✅ Création de l'item coffre "Config UHC"
    public static ItemStack getUHCConfigItem() {
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Config UHC");
        chest.setItemMeta(meta);
        return chest;
    }

    public static ItemStack getLGHeadItem() {
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setDisplayName(ChatColor.RED + "Config LG");

        // Création du profil avec la texture
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        String value = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzY4ZDQzMTI5MzliYjMxMTFmYWUyOGQ2NWQ5YTMxZTc3N2Y4ZjJjOWZjNDI3NTAxY2RhOGZmZTNiMzY3NjU4In19fQ==";
        profile.getProperties().put("textures", new Property("textures", value));

        try {
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        skull.setItemMeta(skullMeta);
        return skull;
    }
    
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createColoredGlassPane(DyeColor color, String name) {
        ItemStack item = new ItemStack(Material.STAINED_GLASS_PANE, 1, color.getData());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getCustomHead(String value, String name) {
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", value));
        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        meta.setDisplayName(name);
        skull.setItemMeta(meta);
        return skull;
    }
    
    private boolean coupleEnabled = false;
    private boolean soloWolfEnabled = false;
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory(); // the menu
        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR) return;
        if (!cur.hasItemMeta() || !cur.getItemMeta().hasDisplayName()) return;

        String title = ChatColor.stripColor(event.getView().getTitle());
        String itemName = ChatColor.stripColor(cur.getItemMeta().getDisplayName());

        // ========= 1) MENU "AUTRES..." =========
        if (title.equalsIgnoreCase("Autres...")) {
            event.setCancelled(true);

            // Only react to clicks inside the menu, not in player inventory
            if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            if (!left && !right) return;

            // % helpers 0..100 with wrap
            java.util.function.IntUnaryOperator incPct = v -> (v + 1) % 101;
            java.util.function.IntUnaryOperator decPct = v -> (v - 1 + 101) % 101;

            switch (cur.getType()) {
                case APPLE:       // Taux drop pommes 0..100
                    autresAppleRate = left ? incPct.applyAsInt(autresAppleRate) : decPct.applyAsInt(autresAppleRate);
                    break;
                case FLINT:       // Taux drop silex 0..100
                    autresFlintRate = left ? incPct.applyAsInt(autresFlintRate) : decPct.applyAsInt(autresFlintRate);
                    break;
                case EXP_BOTTLE:  // Boost XP +0..+100
                    autresXpBoost = left ? incPct.applyAsInt(autresXpBoost) : decPct.applyAsInt(autresXpBoost);
                    break;
                case NETHER_STAR: // Résistance %
                    autresResistPct = left ? incPct.applyAsInt(autresResistPct) : decPct.applyAsInt(autresResistPct);
                    break;
                case BLAZE_ROD:   // Force %
                    autresStrengthPct = left ? incPct.applyAsInt(autresStrengthPct) : decPct.applyAsInt(autresStrengthPct);
                    break;
                case QUARTZ:      // Speed %
                    autresSpeedPct = left ? incPct.applyAsInt(autresSpeedPct) : decPct.applyAsInt(autresSpeedPct);
                    break;
                case DIAMOND:     // Limite diamants: 1..24 puis Illimité (-1)
                    if (left) {
                        if (autresDiamondLimit == -1) autresDiamondLimit = 1;
                        else if (autresDiamondLimit < 24) autresDiamondLimit++;
                        else autresDiamondLimit = -1; // illimité
                    } else { // right
                        if (autresDiamondLimit == -1) autresDiamondLimit = 24;
                        else if (autresDiamondLimit > 1) autresDiamondLimit--;
                        else autresDiamondLimit = -1; // illimité
                    }
                    break;
                case ARROW: // Retour
                    player.closeInventory();
                    openLGMenu(player);
                    return;
                default:
                    return;
            }

            // Refresh the same menu so the values update and the item stays in place
            openAutresMenu(player);
            return;
        }

        // ========= 2) MENU "CONFIG LG" =========
        if (title.equalsIgnoreCase("Config LG")) {
            event.setCancelled(true);
            
            if (itemName.equalsIgnoreCase("Bâtiments")) {
                openBatimentsMenu((Player) event.getWhoClicked());
                return;
            }
            
            if (itemName.equalsIgnoreCase("Options Loups")) {
                openOptionsLoupsMenu(player);
                return;
            }
            
            if (itemName.equalsIgnoreCase("Évènements Aléatoires")) {
            	player.closeInventory();
                openRandomEventsMenu(player);
                return;
            }
            
            // Open sub-menus first
            if (itemName.equalsIgnoreCase("Autres"))       { openAutresMenu(player);    return; }
            if (itemName.equalsIgnoreCase("Solitaires"))   { openSolitairesMenu(player);return; }
            if (itemName.equalsIgnoreCase("Villageois"))   { openVillageoisMenu(player);return; }
            if (itemName.equalsIgnoreCase("Hybrides"))     { openHybridesMenu(player);  return; }
            if (itemName.equalsIgnoreCase("Loups-Garous")) { openLoupsMenu(player);     return; }

            // Toggles
            if (itemName.contains("Options Couple")) {
                coupleEnabled = !coupleEnabled;
                updateLGMenu(top);
                return;
            }

            if (itemName.contains("Loup-Garou Solitaire")) {
                boolean newVal = !roleService.isSolitaireEnabled();
                roleService.setSolitaireEnabled(newVal);

                updateLGMenu(event.getView().getTopInventory()); // ou 'top' si tu l’as déjà
                player.updateInventory();
                player.sendMessage(ChatColor.DARK_GREEN + "[LG] "
                    + ChatColor.GRAY + "Loup-Garou Solitaire : "
                    + (newVal ? ChatColor.GREEN + "activé" : ChatColor.RED + "désactivé"));
                return;
            }


            // Name entry
            if (cur.getType() == Material.NAME_TAG) {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Écris le nom de la partie dans le chat.");
                enAttenteNomPartie.put(player, true);
            }
            
            if (title.equalsIgnoreCase("Config LG") || title.equalsIgnoreCase("§c§lConfig LG")) {
                event.setCancelled(true);

                // Clic uniquement dans le haut du menu
             // Dans ton InventoryClickEvent du menu
                if (title.equalsIgnoreCase("Config LG") || title.equalsIgnoreCase("§c§lConfig LG")) {
                    event.setCancelled(true);

                    if (event.getRawSlot() == 25) {
                        if (gameManager.isVoteLockedLG9()) {
                            player.sendMessage(ChatColor.DARK_PURPLE + "Le vote est verrouillé en LG 9 (Le Déchu est activé).");
                            openLGMenu(player);
                            return;
                        }
                        if (gameManager.getVoteMode() == 0)      gameManager.setVoteMode(9);
                        else if (gameManager.getVoteMode() == 9) gameManager.setVoteMode(10);
                        else                                     gameManager.setVoteMode(0);
                        openLGMenu(player);
                        return;
                    }

                    // ... le reste du menu
                }



                // ... tes autres boutons du menu LG ici ...
            }

            return;
            
            
            
        }
        
        
     // ========= 3) MENU "OPTIONS LOUPS" =========
        if (title.equalsIgnoreCase("Options Loups")) {
            event.setCancelled(true);

            // Sécurise: ne pas réagir aux clics dans l'inventaire du joueur
            if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;

            // Récupère le main proprement
            LGUHC main = (LGUHC) Bukkit.getPluginManager().getPlugin("LGUHC");
            if (main == null) return;

            String dn = ChatColor.stripColor(cur.getItemMeta().getDisplayName());

            // --- Toggle Hurlement des loups ---
            // (adapte le test si tu préfères le slot: if (event.getRawSlot()==11) {...})
            if (dn.startsWith("Hurlement des loups")) {
                main.setWolfHowlEnabled(!main.isWolfHowlEnabled());
                // Réouvre / rafraîchit ce même menu pour refléter l’état
                openOptionsLoupsMenu(player);
                player.playSound(player.getLocation(), Sound.CLICK, 1f, main.isWolfHowlEnabled() ? 1.2f : 0.8f);
                return;
            }

            // … ici mets tes autres toggles du menu loups (chat des loups, liste à trous, etc.)
            // Exemple:
            if (dn.startsWith("Chat des loups")) {
                optLoupChat = !optLoupChat;         // ta variable existante si tu en as une
                openOptionsLoupsMenu(player);
                return;
            }

            if (dn.equalsIgnoreCase("Retour")) {
                player.closeInventory();
                openLGMenu(player);
                return;
            }

            return;
        }

        // (Other menus handled elsewhere…)
    }

    
    private void updateLGMenu(Inventory inv) {
        // --- Tête Couple (inchangé) ---
        ItemStack coupleHead = getHeadFromValue(coupleEnabled
            ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGMzZmNlZjg5OTg5MDVkYzdjZTc4MDNkZDYxYzQ5YjEzMTNiYjQxMTJjZDEwMjA5Zjk3ZmUxZTM4ZmJkZjZlIn19fQ=="
            : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0=");
        ItemMeta coupleMeta = coupleHead.getItemMeta();
        coupleMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Options Couple : "
            + (coupleEnabled ? ChatColor.GREEN + "On" : ChatColor.RED + "None"));
        coupleHead.setItemMeta(coupleMeta);
        inv.setItem(43, coupleHead);

        // --- Tête Loup-Garou Solitaire (lire l’état depuis RoleService AVANT de construire l’item) ---
        boolean soloWolfEnabled = roleService.isSolitaireEnabled();

        ItemStack soloWolfHead = getHeadFromValue(
            soloWolfEnabled
                ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWNjMTc5Y2YzYjMyNzkyZDY4OGM4Y2M4NzZlNzU3MmVlOGYxZDMwMTEzNmUxNGQxYTUzZmRlMWY2MzdhOTYwZSJ9fX0="
                : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0="
        );
        ItemMeta soloMeta = soloWolfHead.getItemMeta();
        soloMeta.setDisplayName(ChatColor.DARK_GREEN + "Loup-Garou Solitaire : "
            + (soloWolfEnabled ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
        soloWolfHead.setItemMeta(soloMeta);
        inv.setItem(34, soloWolfHead);
    }

    
    public ItemStack getHeadFromValue(String value) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", value));

        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        head.setItemMeta(meta);
        return head;
    }

    public ItemStack createSkull(String displayName, String textureValue) {
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", textureValue));

        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        meta.setDisplayName(displayName);
        skull.setItemMeta(meta);
        return skull;
    }

    
    public ItemStack createCustomHead(String displayName, String base64) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setDisplayName(displayName);

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", base64));

        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        head.setItemMeta(meta);
        return head;
    }
    
    private Inventory createSubRoleMenu(String title) {
        Inventory inv = Bukkit.createInventory(null, 54, title); // 6 lignes = 54 slots
        ItemStack blackGlass = createColoredGlassPane(DyeColor.BLACK, " ");

        // Coins : 0,1,2 (haut gauche), 6,7,8 (haut droite), 45,46,47 (bas gauche), 51,52,53 (bas droite)
        int[] corners = {0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : corners) {
            inv.setItem(slot, blackGlass);
        }

        return inv;
    }
    
 // Wrapper pratique
    private void openVillageoisMenu(Player player) {
        int page = villagePage.getOrDefault(player.getUniqueId(), 0);
        openVillageoisMenu(player, page);
    }

    private void openVillageoisMenu(Player player, int page) {
        // ordre : majeurs -> mineurs -> autres
        List<String> all = new ArrayList<>();
        all.addAll(rolesVillageMajeurs);
        all.addAll(rolesVillageMineurs);
        all.addAll(rolesVillageAutres);

        final int perPage = 28; // 4 lignes * 7 colonnes
        int totalPages = (int) Math.ceil(all.size() / (double) perPage);
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = totalPages - 1;
        if (page >= totalPages) page = 0;
        villagePage.put(player.getUniqueId(), page);

        Inventory inv = createSubRoleMenu("§b§lRôles Villageois §7(Page " + (page+1) + "/" + totalPages + ")");

        // slots utilisables (colonnes 1..7, lignes 1..4)
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }

        int start = page * perPage;
        int end = Math.min(start + perPage, all.size());

        for (int i = start, si = 0; i < end && si < slots.size(); i++, si++) {
            String role = all.get(i);
            int count = Math.max(0, getRoleCount(role));
            String tex = pickVillageTexture(role, count > 0);

            ItemStack skull = createSkull("§f" + role + " §7×" + count, tex);
            ItemMeta im = skull.getItemMeta();
            addCountLore(im);
            skull.setItemMeta(im);

            inv.setItem(slots.get(si), skull);
        }

        // Pagination (slot 50)
        ItemStack pageItem = new ItemStack(Material.PAPER);
        ItemMeta pm = pageItem.getItemMeta();
        pm.setDisplayName(ChatColor.WHITE + "Page " + (page+1) + "/" + totalPages);
        pm.setLore(Arrays.asList(
            ChatColor.GRAY + "Clic gauche : Page suivante",
            ChatColor.GRAY + "Clic droit  : Page précédente"
        ));
        pageItem.setItemMeta(pm);
        inv.setItem(50, pageItem);

        // ===== Livre d’infos (slot 4) avec ×N =====
        List<String> loreLines = new ArrayList<>();
        List<String> maj = enabledWithCounts(rolesVillageMajeurs);
        List<String> min = enabledWithCounts(rolesVillageMineurs);
        List<String> aut = enabledWithCounts(rolesVillageAutres);

        loreLines.add(ChatColor.BLUE + "" + ChatColor.BOLD + "Majeurs :");
        loreLines.addAll(maj.isEmpty() ? Arrays.asList(ChatColor.DARK_GRAY + "  (aucun)") : maj);

        loreLines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "Mineurs :");
        loreLines.addAll(min.isEmpty() ? Arrays.asList(ChatColor.DARK_GRAY + "  (aucun)") : min);

        loreLines.add(ChatColor.WHITE + "" + ChatColor.BOLD + "Autres :");
        loreLines.addAll(aut.isEmpty() ? Arrays.asList(ChatColor.DARK_GRAY + "  (aucun)") : aut);

        int totalEnabled = 0;
        for (String r : rolesVillageMajeurs) totalEnabled += getRoleCount(r);
        for (String r : rolesVillageMineurs) totalEnabled += getRoleCount(r);
        for (String r : rolesVillageAutres)  totalEnabled += getRoleCount(r);

        String bookTitle = ChatColor.AQUA + "" + ChatColor.BOLD + "Villageois sélectionnés"
                + ChatColor.GRAY + " (" + ChatColor.GREEN + totalEnabled + ChatColor.GRAY + ")";
        inv.setItem(4, createInfoBook(bookTitle, loreLines));

        // Back
        inv.setItem(48, createBackItem());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }


    private void openLoupsMenu(Player player) {
        Inventory inv = createSubRoleMenu("§4§lRôles Loups-Garous");

        // Textures
        String blackTexture  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0=";
        String redTexture    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjlhNTFmMjdkMmQ5Mzg4OTdiYzQyYTNmZTJjMzEzNWRhMjY3MTY4NmY1NzgyNDExNWY4ZjhkYTc4YSJ9fX0=";
        String orangeTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODViZWMxNTc0ZGUzNzNkZmI3NzVhYjU4YjJjMWU0NjIxZDkyYzZkYWFjN2M2YTc0ZDc4ZmI3MGZmZjRkMCJ9fX0=";

        // Slots utilisables
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }

        // Rôles (sauf Blanc), tri alpha
        List<String> sortedRoles = new ArrayList<>(rolesLoups);
        Collections.sort(sortedRoles);

        int si = 0;
        for (String role : sortedRoles) {
            if (si >= slots.size()) break;

            int count = Math.max(0, getRoleCount(role));
            String tex = (count > 0 ? redTexture : blackTexture);

            ItemStack skull = createSkull("§f" + role + " §7×" + count, tex);
            ItemMeta im = skull.getItemMeta();
            addCountLore(im);
            skull.setItemMeta(im);

            inv.setItem(slots.get(si++), skull);
        }

        // Loup-Garou Blanc à la fin si place
        String blanc = "Loup-Garou Blanc";
        if (si < slots.size()) {
            int count = Math.max(0, getRoleCount(blanc));
            String tex = (count > 0 ? orangeTexture : blackTexture);

            ItemStack skull = createSkull("§f" + blanc + " §7×" + count, tex);
            ItemMeta im = skull.getItemMeta();
            addCountLore(im);
            skull.setItemMeta(im);

            inv.setItem(slots.get(si), skull);
        }

        // ===== Livre d’infos (slot 4) avec ×N =====
        List<String> enabledLG = new ArrayList<>();
        for (String r : rolesLoups) {
            int n = getRoleCount(r);
            if (n > 0) enabledLG.add("  " + r + (n > 1 ? " ×" + n : ""));
        }
        // LG Blanc
        int nBlanc = getRoleCount(blanc);
        if (nBlanc > 0) enabledLG.add("  " + blanc + (nBlanc > 1 ? " ×" + nBlanc : ""));
        Collections.sort(enabledLG, String.CASE_INSENSITIVE_ORDER);

        int total = 0;
        for (String r : rolesLoups) total += getRoleCount(r);
        total += getRoleCount(blanc);

        String bookTitle = ChatColor.RED + "" + ChatColor.BOLD + "Rôles LG sélectionnés"
                + ChatColor.GRAY + " (" + ChatColor.GREEN + total + ChatColor.GRAY + ")";
        inv.setItem(4, createInfoBook(bookTitle, enabledLG));

        inv.setItem(48, createBackItem());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }

    
    private void openHybridesMenu(Player player) {
        Inventory inv = createSubRoleMenu("§5§lRôles Hybrides");

        String blackTexture  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0=";
        String purpleTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmM0ZGM4MzM4NDE1YjJiOTg5YWI3OTQ5NTk4NmVjNzg5NWFhMDM2NzlkMmJjZGQ2ZDllYmI2MzNiODdmYzQifX19";

        // colonnes 1..7 sur lignes 1..4 (même logique)
        int slot = 10; // 2e colonne (ligne 2)
        for (String role : rolesHybrides) {
            int count = Math.max(0, getRoleCount(role));
            String tex = (count > 0 ? purpleTexture : blackTexture);

            ItemStack skull = createSkull("§f" + role + " §7×" + count, tex);
            ItemMeta im = skull.getItemMeta();
            addCountLore(im);
            skull.setItemMeta(im);

            inv.setItem(slot, skull);

            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // saute colonnes bord
            if (slot >= 54) break;
        }

        // ===== Livre d’infos (slot 4) avec ×N =====
        List<String> list = new ArrayList<>();
        int total = 0;
        for (String r : rolesHybrides) {
            int n = getRoleCount(r);
            total += n;
            if (n > 0) list.add("  " + r + (n > 1 ? " ×" + n : ""));
        }
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);

        String bookTitle = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Hybrides sélectionnés"
                + ChatColor.GRAY + " (" + ChatColor.GREEN + total + ChatColor.GRAY + ")";
        inv.setItem(4, createInfoBook(bookTitle, list));

        inv.setItem(48, createBackItem());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }





    private void openSolitairesMenu(Player player) {
        Inventory inv = createSubRoleMenu("§6§lRôles Solitaires");

        String blackTexture  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM0MmI5YmY5ZjFmNjI5NTg0MmIwZWZiNTkxNjk3YjE0NDUxZjgwM2ExNjVhZTU4ZDBkY2ViZDk4ZWFjYyJ9fX0=";
        String orangeTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODViZWMxNTc0ZGUzNzNkZmI3NzVhYjU4YjJjMWU0NjIxZDkyYzZkYWFjN2M2YTc0ZDc4ZmI3MGZmZjRkMCJ9fX0=";

        // ⚠️ Utilise TA liste des solitaires si tu en as une globale ;
        // sinon on reprend ta liste “locale” existante :
        List<String> rolesSolitaires = Arrays.asList(
            "Ange", "Arnacoeur", "Assassin", "Barbare", "Enfant lune", "Ensorceleur", "Feu Follet",
            "Fou du bus", "Illusionniste", "Imitateur", "Joueur de Flûte", "Mastermind", "Médium",
            "Nécromancien", "Pyromane", "Rival", "Ronin", "Voleuse de Légumes", "Voyou"
        );
        List<String> sorted = new ArrayList<>(rolesSolitaires);
        Collections.sort(sorted);

        int slot = 10; // 2e colonne
        for (String role : sorted) {
            int count = Math.max(0, getRoleCount(role));
            String tex = (count > 0 ? orangeTexture : blackTexture);

            ItemStack skull = createSkull("§f" + role + " §7×" + count, tex);
            ItemMeta im = skull.getItemMeta();
            addCountLore(im);
            skull.setItemMeta(im);

            inv.setItem(slot, skull);

            slot++;
            if (slot % 9 == 8) slot += 2; // saute colonne 8
            if (slot >= 54) break;
        }

        // ===== Livre d’infos (slot 4) avec ×N =====
        List<String> list = new ArrayList<>();
        int total = 0;
        for (String r : sorted) {
            int n = getRoleCount(r);
            total += n;
            if (n > 0) list.add("  " + r + (n > 1 ? " ×" + n : ""));
        }
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);

        String bookTitle = ChatColor.GOLD + "" + ChatColor.BOLD + "Solitaires sélectionnés"
                + ChatColor.GRAY + " (" + ChatColor.GREEN + total + ChatColor.GRAY + ")";
        inv.setItem(4, createInfoBook(bookTitle, list));

        inv.setItem(48, createBackItem());
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
    }


    	


    
    @EventHandler
    public void onLGRoleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.equalsIgnoreCase("Rôles Loups-Garous")) return;

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) return;

        // ⬅️ Back
        if (current.getType() == Material.ARROW &&
            ChatColor.stripColor(current.getItemMeta().getDisplayName()).equalsIgnoreCase("Retour")) {
            player.closeInventory();
            openLGMenu(player);
            return;
        }

        // Rôle: ajustement du compteur
        if (current.getType() == Material.SKULL_ITEM) {
            String role = ChatColor.stripColor(current.getItemMeta().getDisplayName())
                    .replaceFirst("\\s×\\d+$", ""); // retire " ×N"

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            boolean shift = event.getClick().isShiftClick();

            int delta = 0;
            if (left)  delta = shift ? +5 : +1;
            if (right) delta = shift ? -5 : -1;

            int before = getRoleCount(role);
            int after  = clamp(before + delta, 0, ROLE_MAX);
            if (after != before) {
                setRoleCount(role, after); // sync enabled sets
                player.playSound(player.getLocation(), Sound.CLICK, 1f, (after > before ? 1.2f : 0.8f));
            }

            openLoupsMenu(player); // refresh
        }
    }

    @EventHandler
    public void onSolitairesRoleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = ChatColor.stripColor(event.getInventory().getTitle());
        if (!title.equalsIgnoreCase("Rôles Solitaires")) return;

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) return;

        // ⬅️ Back
        if (current.getType() == Material.ARROW &&
            ChatColor.stripColor(current.getItemMeta().getDisplayName()).equalsIgnoreCase("Retour")) {
            player.closeInventory();
            openLGMenu(player);
            return;
        }

        // Rôle: ajustement du compteur
        if (current.getType() == Material.SKULL_ITEM) {
            String role = ChatColor.stripColor(current.getItemMeta().getDisplayName())
                    .replaceFirst("\\s×\\d+$", "");

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            boolean shift = event.getClick().isShiftClick();

            int delta = 0;
            if (left)  delta = shift ? +5 : +1;
            if (right) delta = shift ? -5 : -1;

            int before = getRoleCount(role);
            int after  = clamp(before + delta, 0, ROLE_MAX);
            if (after != before) {
                setRoleCount(role, after);
                player.playSound(player.getLocation(), Sound.CLICK, 1f, (after > before ? 1.2f : 0.8f));
            }

            openSolitairesMenu(player); // refresh
        }
    }

    
    @EventHandler
    public void onHybrideRoleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = ChatColor.stripColor(event.getInventory().getTitle());
        if (!title.equalsIgnoreCase("Rôles Hybrides")) return;

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) return;

        // ⬅️ Back
        if (current.getType() == Material.ARROW &&
            ChatColor.stripColor(current.getItemMeta().getDisplayName()).equalsIgnoreCase("Retour")) {
            player.closeInventory();
            openLGMenu(player);
            return;
        }

        // Rôle: ajustement du compteur
        if (current.getType() == Material.SKULL_ITEM) {
            String role = ChatColor.stripColor(current.getItemMeta().getDisplayName())
                    .replaceFirst("\\s×\\d+$", "");

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            boolean shift = event.getClick().isShiftClick();

            int delta = 0;
            if (left)  delta = shift ? +5 : +1;
            if (right) delta = shift ? -5 : -1;

            int before = getRoleCount(role);
            int after  = clamp(before + delta, 0, ROLE_MAX);
            if (after != before) {
                setRoleCount(role, after);
                if (role.equalsIgnoreCase("Cupidon v2")) {
                    if (before == 0 && after > 0) {
                        // Cupidon v2 vient d’être ajouté → on désactive Couple Aléatoire
                        disableRandomCoupleEventEverywhere();
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "[Cupidon v2] "
                                + ChatColor.GRAY + "Couple Aléatoire a été forcé à 0%.");
                    }
                }
                player.playSound(player.getLocation(), Sound.CLICK, 1f, (after > before ? 1.2f : 0.8f));
            }

            openHybridesMenu(player); // refresh
        }
    }

    @EventHandler
    public void onVillageoisClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        String rawTitle = ChatColor.stripColor(inv.getTitle());
        if (!rawTitle.startsWith("Rôles Villageois")) return;

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) return;

        // ⬅️ Back
        if (current.getType() == Material.ARROW &&
            ChatColor.stripColor(current.getItemMeta().getDisplayName()).equalsIgnoreCase("Retour")) {
            player.closeInventory();
            openLGMenu(player);
            return;
        }

        // Pagination (papier)
        if (current.getType() == Material.PAPER) {
            int page = villagePage.getOrDefault(player.getUniqueId(), 0);
            if (event.getClick().isRightClick()) page--; else page++; // droit = précédent, gauche = suivant
            openVillageoisMenu(player, page);
            return;
        }

        // Rôle: ajustement du compteur
        if (current.getType() == Material.SKULL_ITEM) {
            String role = ChatColor.stripColor(current.getItemMeta().getDisplayName())
                    .replaceFirst("\\s×\\d+$", ""); // retire " ×N" si présent

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            boolean shift = event.getClick().isShiftClick();

            int delta = 0;
            if (left)  delta = shift ? +5 : +1;
            if (right) delta = shift ? -5 : -1;

            int before = getRoleCount(role);
            int after  = clamp(before + delta, 0, ROLE_MAX);
            if (after != before) {
                setRoleCount(role, after); // sync enabled sets
                player.playSound(player.getLocation(), Sound.CLICK, 1f, (after > before ? 1.2f : 0.8f));
            }

            int page = villagePage.getOrDefault(player.getUniqueId(), 0);
            openVillageoisMenu(player, page); // refresh
        }
    }


    
    private boolean isVillageRoleEnabled(String role) {
        if (rolesVillageMajeurs.contains(role)) return enabledVillageMajeurs.contains(role);
        if (rolesVillageMineurs.contains(role)) return enabledVillageMineurs.contains(role);
        return enabledVillageAutres.contains(role);
    }

    private void setVillageRoleEnabled(String role, boolean enabled) {
        if (rolesVillageMajeurs.contains(role)) {
            if (enabled) enabledVillageMajeurs.add(role); else enabledVillageMajeurs.remove(role);
        } else if (rolesVillageMineurs.contains(role)) {
            if (enabled) enabledVillageMineurs.add(role); else enabledVillageMineurs.remove(role);
        } else {
            if (enabled) enabledVillageAutres.add(role); else enabledVillageAutres.remove(role);
        }
    }

    // Retourne la texture adaptée à la catégorie + état activé
    private String pickVillageTexture(String role, boolean enabled) {
        String base;
        if (rolesVillageMajeurs.contains(role)) base = TEX_BLUE_DARK;
        else if (rolesVillageMineurs.contains(role)) base = TEX_CYAN;
        else base = TEX_BLUE_LIGHT;

        // si désactivé, on affiche noir; activé = texture colorée
        return enabled ? base : TEX_BLACK;
    }
    
    private ItemStack createInfoBook(String title, List<String> lines) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(title);

        // Limiter la taille du lore pour éviter les dépassements
        List<String> lore = new ArrayList<>();
        if (lines.isEmpty()) {
            lore.add(ChatColor.GRAY + "Aucun rôle activé.");
        } else {
            // On coupe à 40 lignes max et on ajoute "..."
            int max = Math.min(40, lines.size());
            for (int i = 0; i < max; i++) {
                lore.add(ChatColor.GRAY + "- " + ChatColor.WHITE + lines.get(i));
            }
            if (lines.size() > max) lore.add(ChatColor.DARK_GRAY + "... (" + (lines.size() - max) + " de plus)");
        }

        meta.setLore(lore);
        book.setItemMeta(meta);
        return book;
    }

    private List<String> sortedCopy(Collection<String> c) {
        List<String> list = new ArrayList<>(c);
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }
    
    private void openAutresMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§e§lAutres...");

        // 4 premières lignes = verre rouge
        ItemStack redPane = createColoredGlassPane(DyeColor.RED, " ");
        for (int slot = 0; slot <= 35; slot++) inv.setItem(slot, redPane);


        // --- Emplacements des contrôles (fixés proprement) ---
        // Bas gauche : résist/force/speed
        int SLOT_RESIST  = 51; // Nether Star
        int SLOT_STRENGH = 43; // Blaze Rod
        int SLOT_SPEED   = 53; // Quartz Ore

        // Milieu bas : flèche retour sous la colonne du milieu
        int SLOT_BACK = 49;    // flèche retour
        // Colonne du milieu (haut de la zone basse) : XP
        int SLOT_XP = 40;      // Bouteille d'XP

        // Bas droite : pomme / diamant / silex
        int SLOT_APPLE  = 45;  // Apple drop %
        int SLOT_DIAM   = 37;  // Diamond limit
        int SLOT_FLINT  = 47;  // Flint drop %

        // --- Items avec valeur actuelle (nom + lore) ---
        inv.setItem(SLOT_APPLE,  createValueItem(Material.APPLE, "§aTaux de drop de pomme", autresAppleRate + "%"));
        inv.setItem(SLOT_DIAM,   createValueItem(Material.DIAMOND, "§bLimite de diamants", (autresDiamondLimit == -1 ? "Illimité" : String.valueOf(autresDiamondLimit))));
        inv.setItem(SLOT_FLINT,  createValueItem(Material.FLINT, "§7Taux de drop de silex", autresFlintRate + "%"));

        inv.setItem(SLOT_XP,     createValueItem(Material.EXP_BOTTLE, "§dBoost d'XP", "+" + autresXpBoost + "%"));

        inv.setItem(SLOT_RESIST, createValueItem(Material.NETHER_STAR, "§fRésistance", autresResistPct + "%"));
        inv.setItem(SLOT_STRENGH,createValueItem(Material.BLAZE_ROD, "§6Force", autresStrengthPct + "%"));
        inv.setItem(SLOT_SPEED,  createValueItem(Material.QUARTZ, "§bVitesse", autresSpeedPct + "%"));

        // Flèche retour sous la colonne centrale
        inv.setItem(SLOT_BACK, createBackItemCustom("§cRetour"));

        p.openInventory(inv);
    }
    
    private ItemStack createValueItem(Material mat, String name, String valueLine) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Valeur: " + ChatColor.WHITE + valueLine + ChatColor.DARK_GRAY + "  (clic G/D pour +/-)"));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack createBackItemCustom(String title) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(title);
        it.setItemMeta(m);
        return it;
    }
    
    private void openBatimentsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§e§lBâtiments");

        // Verres rouges : coins + slot 4
        ItemStack red = createColoredGlassPane(DyeColor.RED, " ");
        int[] corners = {0,1,9, 7,8,17, 18,19, 25,26};
        for (int s : corners) inv.setItem(s, red);
        inv.setItem(4, red); // haut milieu

        // Slot 11 : tête BRIQUE (toggle Maisons d'honneur)
        String brickTex = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzViNDQ4ZmQ5NWM4NzRjOTVmZTc0ODQ0NDFhNDM5NGQ2NDZiNzJiYzgyYTUyNzQ4M2ZkYzcwY2E3OTg2ZmNhNSJ9fX0=";
        inv.setItem(11, createSkull(
                (batMaisonHonneur ? "§aMaisons d'honneur : §lON" : "§cMaisons d'honneur : §lOFF"),
                brickTex
        ));

        // Slot 12 : Book & Quill (toggle Maisons de vote)
        ItemStack vote = new ItemStack(Material.BOOK_AND_QUILL);
        ItemMeta vm = vote.getItemMeta();
        vm.setDisplayName(batMaisonVote ? "§aMaisons de vote : §lON" : "§cMaisons de vote : §lOFF");
        vote.setItemMeta(vm);
        inv.setItem(12, vote);

        // Slot 14 : tête FLÈCHE ROUGE (distance min)
        String arrowTex = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWNkYmY2MTNiYzBiODg1NTg3Mjk0ODU3YWYwY2YxMmMwMTE1MzI2NGFjMjVlMTU3NzNhOWQ4YmNkYzNmMDBhOSJ9fX0=";
        inv.setItem(14, createSkull("§eDistance minimale : §6" + batDistanceMin + " §eblocs", arrowTex));
        
     // Slot 15 : Nether Star (toggle Composition Cachée)
        ItemStack comp = new ItemStack(Material.NETHER_STAR);
        ItemMeta cm = comp.getItemMeta();
        cm.setDisplayName(batCompositionCachee ? "§aSanctuaires : §lON" : "§cSanctuaires : §lOFF");
        comp.setItemMeta(cm);
        inv.setItem(15, comp);


        // Slot 22 : flèche retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.RED + "Retour");
        back.setItemMeta(bm);
        inv.setItem(22, back);
        
        

        p.openInventory(inv);
    }
    
    @EventHandler
    public void onBatimentsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.equalsIgnoreCase("Bâtiments")) return; // "§e§lBâtiments" -> "Bâtiments"

        event.setCancelled(true);

        // On n’agit que si le clic est dans le menu (pas l’inventaire joueur)
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR || !cur.hasItemMeta() || !cur.getItemMeta().hasDisplayName()) return;

        String name = ChatColor.stripColor(cur.getItemMeta().getDisplayName());

        // Flèche retour
        if (cur.getType() == Material.ARROW || name.equalsIgnoreCase("Retour")) {
            p.closeInventory();
            openLGMenu(p);
            return;
        }

        // Tête brique -> toggle Maisons d'honneur
        if (cur.getType() == Material.SKULL_ITEM && name.startsWith("Maisons d'honneur")) {
            batMaisonHonneur = !batMaisonHonneur;
            openBatimentsMenu(p);
            return;
        }

        // Book & quill -> toggle Maisons de vote
        if (cur.getType() == Material.BOOK_AND_QUILL) {
            batMaisonVote = !batMaisonVote;
            openBatimentsMenu(p);
            return;
        }
        // Nether Star -> toggle Composition Cachée
       if (cur.getType() == Material.NETHER_STAR) {
          batCompositionCachee = !batCompositionCachee;
          openBatimentsMenu(p);
          return;
       }

        // Tête flèche rouge -> distance min (50 / 100 / 150), gauche=+ droite=-
        if (cur.getType() == Material.SKULL_ITEM && name.startsWith("Distance minimale")) {
            boolean left = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            int[] steps = {50, 100, 150};

            // trouver index actuel
            int idx = 0;
            for (int i = 0; i < steps.length; i++) if (steps[i] == batDistanceMin) { idx = i; break; }

            if (left) {
                idx = (idx + 1) % steps.length;
            } else if (right) {
                idx = (idx - 1 + steps.length) % steps.length;
            }
            


            batDistanceMin = steps[idx];
            openBatimentsMenu(p);
        }
    }
    
    private void openOptionsLoupsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§c§lOptions Loups");

        // Verre rouge : coins + slot 4
        ItemStack red = createColoredGlassPane(DyeColor.RED, " ");
        int[] corners = {0,1,9, 7,8,17, 18,19, 25,26};
        for (int s : corners) inv.setItem(s, red);
        inv.setItem(4, red);

        // Textures
        String wolfTex = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzY4ZDQzMTI5MzliYjMxMTFmYWUyOGQ2NWQ5YTMxZTc3N2Y4ZjJjOWZjNDI3NTAxY2RhOGZmZTNiMzY3NjU4In19fQ==";

        // Slot 11 : Tête loup -> Hurlement (toggle, exclusif avec Chat)
        boolean on = isWolfHowlEnabled();
        inv.setItem(11, createSkull(
            (optLoupHowl ? "§aHurlement des loups : §lON" : "§cHurlement des loups : §lOFF"),
            wolfTex
        ));

        // Slot 12 : Livre -> Chat des loups (toggle, exclusif avec Hurlement)
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta bm = book.getItemMeta();
        bm.setDisplayName(optLoupChat ? "§aChat des loups : §lON" : "§cChat des loups : §lOFF");
        book.setItemMeta(bm);
        inv.setItem(12, book);

        // Slot 14 : Toiles -> Liste à trous (toggle)
        ItemStack web = new ItemStack(Material.WEB);
        ItemMeta wm = web.getItemMeta();
        wm.setDisplayName(optListeATrous ? "§aListe à trous : §lON" : "§cListe à trous : §lOFF");
        web.setItemMeta(wm);
        inv.setItem(14, web);

     // Slot 15 : Fil -> Nombre de loups dans la liste (0..loupsActifs-1)
        int loupsActifs = getEnabledWolfCount();
        if (loupsActifs <= 0) loupsActifs = 1; // évite bornes négatives
        if (optNbLoupsListe > loupsActifs - 1) optNbLoupsListe = Math.max(0, loupsActifs - 1);

        ItemStack string = new ItemStack(Material.STRING);
        ItemMeta sm = string.getItemMeta();
        sm.setDisplayName("§eLoups dans la liste : §6" + optNbLoupsListe);
        sm.setLore(Arrays.asList(
            ChatColor.GRAY + "Max: " + (loupsActifs - 1),
            ChatColor.GRAY + "Clic gauche: +1",
            ChatColor.GRAY + "Clic droit : -1"
        ));
        string.setItemMeta(sm);
        inv.setItem(15, string);

        // Slot 22 : flèche retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backM = back.getItemMeta();
        backM.setDisplayName(ChatColor.RED + "Retour");
        back.setItemMeta(backM);
        inv.setItem(22, back);

        p.openInventory(inv);
    }
    
 // Compte tous les loups activés, y compris le LG Blanc
    private int getEnabledWolfCount() {
        int count = 0;
        for (String role : enabledRoles) {
            if (rolesLoups.contains(role)) count++;
        }
        // Si pour une raison quelconque "Loup-Garou Blanc" n'est pas dans rolesLoups,
        // on le compte quand même s'il est activé.
        if (enabledRoles.contains("Loup-Garou Blanc") && !rolesLoups.contains("Loup-Garou Blanc")) {
            count++;
        }
        return count;
    }

    
    @EventHandler
    public void onOptionsLoupsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.equalsIgnoreCase("Options Loups")) return;

        event.setCancelled(true);

        // uniquement si clic dans le haut (le menu)
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR || !cur.hasItemMeta() || !cur.getItemMeta().hasDisplayName()) return;

        String dn = ChatColor.stripColor(cur.getItemMeta().getDisplayName());

        // Retour
        if (cur.getType() == Material.ARROW || dn.equalsIgnoreCase("Retour")) {
            p.closeInventory();
            openLGMenu(p);
            return;
        }

        // Hurlement (tête de loup)
        if (cur.getType() == Material.SKULL_ITEM && dn.startsWith("Hurlement des loups")) {
            optLoupHowl = !optLoupHowl;
            if (optLoupHowl) optLoupChat = false; // exclusif
            openOptionsLoupsMenu(p);
            return;
        }

        // Chat des loups (livre)
        if (cur.getType() == Material.BOOK && dn.startsWith("Chat des loups")) {
            optLoupChat = !optLoupChat;
            if (optLoupChat) optLoupHowl = false; // exclusif
            openOptionsLoupsMenu(p);
            return;
        }

        // Liste à trous (toile)
        if (cur.getType() == Material.WEB && dn.startsWith("Liste à trous")) {
            optListeATrous = !optListeATrous;
            openOptionsLoupsMenu(p);
            return;
        }

        // Nombre de loups dans la liste (fil)
     // Nombre de loups dans la liste (fil)
        if (cur.getType() == Material.STRING && dn.startsWith("Loups dans la liste")) {
            if (!optListeATrous) {
                p.sendMessage(ChatColor.RED + "Active la liste à trous pour modifier ce paramètre.");
                return;
            }

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            if (!left && !right) return;

            int max = Math.max(0, getEnabledWolfCount() - 1);
            if (left) {
                optNbLoupsListe = (optNbLoupsListe + 1);
                if (optNbLoupsListe > max) optNbLoupsListe = 0;
            } else {
                optNbLoupsListe = (optNbLoupsListe - 1);
                if (optNbLoupsListe < 0) optNbLoupsListe = max;
            }
            openOptionsLoupsMenu(p);
        }


    }
    
    private void openRandomEventsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§e§lÉvènements Aléatoires");

        ItemStack red = createColoredGlassPane(DyeColor.RED, " ");

        // Coins + slot 4
        int[] corners = {0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int s : corners) inv.setItem(s, red);
        inv.setItem(4, red);

        // Colonnes 0 et 8 pleines
        for (int row = 0; row < 6; row++) {
            inv.setItem(row * 9, red);
            inv.setItem(row * 9 + 8, red);
        }

        // Slots disponibles : colonnes 1..7, lignes 1..4
        List<Integer> slots = new ArrayList<>();
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                slots.add(r * 9 + c);
            }
        }

        // Tri alphabétique
        List<String> sorted = new ArrayList<>(randomEvents);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);

        int i = 0;
        for (String ev : sorted) {
            if (i >= slots.size()) break;

            int pct = randomEventPct.getOrDefault(ev, 0);
            String tex = pct > 0 ? TEX_YELLOW : TEX_BLACK;

            ItemStack head = createSkull("§f" + ev, tex);
            ItemMeta m = head.getItemMeta();
            m.setLore(Arrays.asList(
                ChatColor.GRAY + "Chance d'apparition : " + ChatColor.GOLD + pct + "%",
                ChatColor.DARK_GRAY + "Clic gauche : +1%",
                ChatColor.DARK_GRAY + "Clic droit  : -1%"
            ));
            head.setItemMeta(m);

            inv.setItem(slots.get(i), head);
            i++;
        }

        // Flèche retour (milieu bas)
        inv.setItem(49, createBackItem());

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.CLICK, 1f, 1f);
    }

    
    @EventHandler
    public void onRandomEventsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        String title = ChatColor.stripColor(inv.getTitle());
        if (!title.equalsIgnoreCase("Évènements Aléatoires")) return;

        event.setCancelled(true);

        // On n'agit que dans le haut du menu
        if (event.getRawSlot() < 0 || event.getRawSlot() >= inv.getSize()) return;

        ItemStack cur = event.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR || !cur.hasItemMeta() || !cur.getItemMeta().hasDisplayName())
            return;

        String dn = ChatColor.stripColor(cur.getItemMeta().getDisplayName());

        // Retour
        if (cur.getType() == Material.ARROW || dn.equalsIgnoreCase("Retour")) {
            p.closeInventory();
            openLGMenu(p);
            return;
        }
        
     // Empêche d'activer "Couple Aléatoire" si "Cupidon v2" est présent dans la compo choisie
        if (dn.equalsIgnoreCase("Couple Aléatoire")) {
            // ici on se base sur le menu: getRoleCount("Cupidon v2")
            if (getRoleCount("Cupidon v2") > 0) {
                randomEventPct.put("Couple Aléatoire", 0);
                p.sendMessage(ChatColor.RED + "Impossible d'activer Couple Aléatoire : Cupidon v2 est dans la compo.");
                openRandomEventsMenu(p);
                return;
            }
        }


        // Clic sur un événement (tête)
        if (cur.getType() == Material.SKULL_ITEM) {
            String ev = dn; // displayName = nom de l’événement
            if (!randomEvents.contains(ev)) return;

            boolean left  = event.getClick().isLeftClick();
            boolean right = event.getClick().isRightClick();
            if (!left && !right) return;

            int pct = randomEventPct.getOrDefault(ev, 0);
            if (left)  pct = (pct + 1) % 101;              // +1 avec wrap 100 -> 0
            if (right) pct = (pct - 1 + 101) % 101;        // -1 avec wrap 0 -> 100

            randomEventPct.put(ev, pct);

            // refresh visuel
            openRandomEventsMenu(p);
        }
    }
    
    public void hardResetOnlinePlayers() {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            try {
                // 1) Nettoyage inventaire + armure
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.updateInventory();

                // 2) Effets (tous, y compris Absorption)
                for (org.bukkit.potion.PotionEffect eff : p.getActivePotionEffects()) {
                    p.removePotionEffect(eff.getType());
                }
                try { p.removePotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION); } catch (Throwable ignored) {}

             // 3) PV max = 20 (10 cœurs) + remettre la vie au max
                try { p.setMaxHealth(20.0D); } catch (Throwable ignored) {}
                try { p.setHealth(20.0D); } catch (Throwable ignored) {}


                // 4) Statuts “propres”
                p.setFireTicks(0);
                p.setFoodLevel(20);
                p.setSaturation(0f);
                p.setExhaustion(0f);
                p.setFallDistance(0f);
                p.setExp(0f);
                p.setLevel(0);

                // 5) Gamemode AVENTURE + pas de fly
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);

                // 6) Retire toute ancienne “config item” puis redonne AUX ADMINS SEULEMENT
                removeConfigItems(p);
                if (isAdmin(p)) {
                    p.getInventory().setItem(0, getUHCConfigItem()); // coffre "Config UHC"
                    p.getInventory().setItem(1, getLGHeadItem());    // tête "Config LG"
                }
                p.updateInventory();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    
 // --- Permissions ---
    public static final String PERM_ADMIN = "lguhc.admin";

    private static boolean isAdmin(org.bukkit.entity.Player p) {
        return p.isOp() || p.hasPermission(PERM_ADMIN);
    }

    // --- Reconnaître les items Config (par nom affiché) ---
    private static boolean isConfigItem(org.bukkit.inventory.ItemStack it) {
        if (it == null || it.getType() == org.bukkit.Material.AIR) return false;
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        if (m == null || !m.hasDisplayName()) return false;
        String dn = org.bukkit.ChatColor.stripColor(m.getDisplayName()).toLowerCase(java.util.Locale.ROOT);
        return dn.contains("config uhc") || dn.contains("config lg") || dn.contains("options loups");
    }

    // --- Nettoyer l’inventaire des items Config ---
    private static void stripConfigItems(org.bukkit.entity.Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            if (isConfigItem(it)) inv.setItem(i, null);
        }
        p.updateInventory();
    }

 // Donne UNIQUEMENT aux OP (ou à la perm) : coffre "Config UHC" + tête "Config LG"
    private void grantStartItemsIfAdmin(Player p) {
        if (!(p.isOp() || p.hasPermission("lgu.admin"))) return;

        // ne touche pas à l’inventaire des non-admins
        try {
            // Placements comme avant (0 = UHC, 8 = LG)
            p.getInventory().setItem(0, makeConfigUhcChest());
            p.getInventory().setItem(1, makeConfigLgHead());
            p.updateInventory();
        } catch (Throwable ignored) {}
    }

    
 // === Items "admin" donnés au démarrage ===
    private static final String WOLF_HEAD_TEX =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5l"
          + "Y3JhZnQubmV0L3RleHR1cmUvZWNjMTc5Y2YzYjMyNzkyZDY4OGM4Y2M4NzZlNzU3"
          + "MmVlOGYxZDMwMTEzNmUxNGQxYTUzZmRlMWY2MzdhOTYwZSJ9fX0="; // même tête "loup" que tes menus

    private ItemStack makeConfigUhcChest() {
        ItemStack it = new ItemStack(Material.CHEST, 1);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Config UHC");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeConfigLgHead() {
        // Tu as déjà getHeadFromValue(...) dans ta classe de menus.
        // Si tu ne l’as pas dans LGUHC, recopie-le ici ou appelle ta version util.
        ItemStack it = getHeadFromValue(WOLF_HEAD_TEX);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.RED + "Config LG");
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

    
    
    private int getClosestBorderIndex(int value) {
        for (int i = 0; i < BORDER_SIZES.length; i++) {
            if (BORDER_SIZES[i] >= value) {
                return i;
            }
        }
        return BORDER_SIZES.length - 1;
    }
    
        
    
        
 // Champ (dans ta classe principale ou où tu déclares startPregeneration)
    private final java.util.concurrent.atomic.AtomicBoolean pregenRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void startPregeneration(final Player player) {
        if (pregenRunning.getAndSet(true)) {
            player.sendMessage(ChatColor.RED + "Une prégénération est déjà en cours.");
            return;
        }

        final World world = Bukkit.getWorlds().get(0);

        // Récupère centre + taille de bordure (fallback sur tailleBordureDepart si non configurée)
        double size = world.getWorldBorder() != null ? world.getWorldBorder().getSize() : tailleBordureDepart;
        if (size <= 0) size = tailleBordureDepart;
        Location center = world.getWorldBorder() != null ? world.getWorldBorder().getCenter() : new Location(world, 0, 0, 0);

        final int half = (int)Math.floor(size / 2.0);

        // Borne exacte en blocs
        final int minX = (int)Math.floor(center.getX()) - half;
        final int maxX = (int)Math.floor(center.getX()) + half;
        final int minZ = (int)Math.floor(center.getZ()) - half;
        final int maxZ = (int)Math.floor(center.getZ()) + half;

        // Convertit en coords de chunks
        final int minChunkX = (int)Math.floor(minX / 16.0);
        final int maxChunkX = (int)Math.floor(maxX / 16.0);
        final int minChunkZ = (int)Math.floor(minZ / 16.0);
        final int maxChunkZ = (int)Math.floor(maxZ / 16.0);

        final int chunksX = (maxChunkX - minChunkX + 1);
        final int chunksZ = (maxChunkZ - minChunkZ + 1);
        final int totalChunks = Math.max(1, chunksX * chunksZ);
        final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(0);

        // prochain seuil à annoncer (10, 20, ... 100)
        final int[] nextThreshold = {10};

        player.sendMessage(ChatColor.GREEN + "Début de la prégénération dans la bordure... (" +
                chunksX + "×" + chunksZ + " chunks)");

        new BukkitRunnable() {
            int cx = minChunkX;
            int cz = minChunkZ;

            @Override public void run() {
                for (int i = 0; i < 10; i++) {
                    if (cx > maxChunkX) {
                        if (nextThreshold[0] <= 100) showProgress(player, 100);
                        player.sendMessage(ChatColor.GREEN + "✔ Prégénération terminée !");
                        pregenRunning.set(false);
                        cancel();
                        return;
                    }

                    world.getChunkAt(cx, cz).load(true);

                    int d = done.incrementAndGet();
                    int percent = (d * 100) / totalChunks;
                    if (percent >= nextThreshold[0]) {
                        showProgress(player, nextThreshold[0]); // un message par palier de 10%
                        nextThreshold[0] += 10;
                    }

                    cz++;
                    if (cz > maxChunkZ) { cz = minChunkZ; cx++; }
                }
            }
        }.runTaskTimer(this /* <= ton JavaPlugin */, 0L, 2L); //this
    }

    private void showProgress(Player player, int percent) {
        int barLength = 20;
        int filled = (percent * barLength) / 100;

        String bar = ChatColor.GREEN + "[" +
                ChatColor.YELLOW + repeat("|", filled) +
                ChatColor.GRAY + repeat("|", barLength - filled) +
                ChatColor.GREEN + "]";

        player.sendMessage(ChatColor.GOLD + "Prégénération: " + percent + "% " + bar);
    }

    private String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }




    
    
    private void setupScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (preSb == null) preSb = manager.getNewScoreboard();

        preObj = preSb.getObjective("pregame");
        if (preObj == null) {
            preObj = preSb.registerNewObjective("pregame", "dummy");
            preObj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        preObj.setDisplayName(ChatColor.DARK_RED + nomPartie);
        player.setScoreboard(preSb);
        updateScoreboard();
    }

    private void updateScoreboard() {
        if (preSb == null || preObj == null) return;
        preSb.getEntries().forEach(preSb::resetScores);

        int line = 5;
        preObj.setDisplayName(ChatColor.DARK_RED + nomPartie);
        preObj.getScore(ChatColor.GRAY + "──────────").setScore(line--);
        preObj.getScore(ChatColor.WHITE + "Joueurs : " + ChatColor.DARK_PURPLE + Bukkit.getOnlinePlayers().size()).setScore(line--);
        preObj.getScore(ChatColor.WHITE + "± Bordure : " + ChatColor.DARK_PURPLE + tailleBordureDepart).setScore(line--);
        preObj.getScore(ChatColor.GRAY + "──────────").setScore(line--);
    }
    


    
    private void startScoreboardUpdater() {
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getScoreboard() != scoreboard) {
                        setupScoreboard(player);
                    }
                }
                updateScoreboard();
            }
        };
        scoreboardTask.runTaskTimer(this, 0L, 40L); // toutes les 2 secondes
    }

    
    public void stopScoreboardUpdater() {
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
    }

    
 // ===== Pregame board setup =====
    private void setupPregameBoard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (preSb == null) {
            preSb = manager.getNewScoreboard();
            preObj = preSb.registerNewObjective("pregame", "dummy");
            preObj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        preObj.setDisplayName(ChatColor.DARK_RED + nomPartie);
        player.setScoreboard(preSb);
        updatePregameBoard();
    }

    private void updatePregameBoard() {
        if (preSb == null || preObj == null) return;

        // clear old lines
        for (String entry : preSb.getEntries()) preSb.resetScores(entry);

        int line = 5;
        preObj.setDisplayName(ChatColor.DARK_RED + nomPartie);
        preObj.getScore(ChatColor.GRAY + "──────────").setScore(line--);
        preObj.getScore(ChatColor.WHITE + "Joueurs : " + ChatColor.DARK_PURPLE + Bukkit.getOnlinePlayers().size()).setScore(line--);
        preObj.getScore(ChatColor.WHITE + "± Bordure : " + ChatColor.DARK_PURPLE + tailleBordureDepart).setScore(line--);
        preObj.getScore(ChatColor.GRAY + "──────────").setScore(line--);
    }

    // ===== Start/stop pregame updater =====
    public void stopPregameBoardUpdater() {
        if (preSbTask != null) {
            preSbTask.cancel();
            preSbTask = null;
        }
    }

    public void releasePregameBoard() {
        stopPregameBoardUpdater();
        preSb = null;
        preObj = null;
    }

    
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (!enAttenteNomPartie.containsKey(p)) return;

        event.setCancelled(true);
        String nouveauNom = ChatColor.translateAlternateColorCodes('&', event.getMessage());
        nomPartie = nouveauNom;
        enAttenteNomPartie.remove(p);

        Bukkit.getScheduler().runTask(this, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                setupScoreboard(online); // Réinitialise le scoreboard avec le nouveau nom
            }
        });

        p.sendMessage(ChatColor.GREEN + "Nom de la partie mis à jour : " + ChatColor.RESET + nouveauNom);
    }
    
    public void setTailleBordureDepart(int value) {
        this.tailleBordureDepart = value;
        // Si ton updater de scoreboard tourne avant le start, tu peux forcer un refresh immédiat :
        try {
            updateScoreboard(); // existe déjà chez toi (méthode privée). Si besoin, rends-la public/protected.
        } catch (Exception ignore) {}
    }




    
    public void saveConfig(Player p, String saveName) {
        UUID uuid = p.getUniqueId();
        File dir = new File(getDataFolder(), "saves/" + uuid.toString());
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, saveName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Rôles (tous : villageois, loups, neutres, hybrides)
        for (Map.Entry<String, Boolean> entry : roles.entrySet()) {
            config.set("roles." + entry.getKey(), entry.getValue());
        }

        // Bordure
        config.set("border.start", tailleBordureDepart);
        config.set("border.end", tailleBordureFinale);
        config.set("border.speed", borderSpeed);

        // Scénarios
        for (Map.Entry<String, Boolean> entry : scenarios.entrySet()) {
            config.set("scenarios." + entry.getKey(), entry.getValue());
        }

        // Limitations
        if (limitations != null) {
            for (Map.Entry<String, Object> entry : limitations.entrySet()) {
                config.set("limitations." + entry.getKey(), entry.getValue());
            }
        }

        // Timers
        for (Map.Entry<String, Integer> entry : timers.entrySet()) {
            config.set("timers." + entry.getKey(), entry.getValue());
        }

        // Événements
        config.set("evenement.actif", evenementActif); // booléen : true/false
        config.set("evenement.liste", listeEvenements); // liste de noms

        try {
            config.save(file);
            p.sendMessage(ChatColor.GREEN + "Sauvegarde '" + saveName + "' enregistrée !");
        } catch (IOException e) {
            p.sendMessage(ChatColor.RED + "Erreur lors de la sauvegarde.");
            e.printStackTrace();
        }
    }

    public void loadConfig(Player p, String saveName) {
        UUID uuid = p.getUniqueId();
        File file = new File(getDataFolder(), "saves/" + uuid.toString() + "/" + saveName + ".yml");

        if (!file.exists()) {
            p.sendMessage(ChatColor.RED + "Sauvegarde '" + saveName + "' introuvable.");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Rôles
        roles.clear();
        if (config.contains("roles")) {
            loadRoleSection(config.getConfigurationSection("roles"), "");
        }

        // Bordure
        tailleBordureDepart = config.getInt("border.start", 1000);
        tailleBordureFinale = config.getInt("border.end", 100);
        borderSpeed = config.getDouble("border.speed", 1.0);

        // Scénarios
        scenarios.clear();
        if (config.contains("scenarios")) {
            for (String key : config.getConfigurationSection("scenarios").getKeys(false)) {
                scenarios.put(key, config.getBoolean("scenarios." + key));
            }
        }

        // Limitations
        limitations.clear();
        if (config.contains("limitations")) {
            for (String key : config.getConfigurationSection("limitations").getKeys(false)) {
                limitations.put(key, config.get("limitations." + key));
            }
        }

        // Timers
        timers.clear();
        if (config.contains("timers")) {
            for (String key : config.getConfigurationSection("timers").getKeys(false)) {
                timers.put(key, config.getInt("timers." + key));
            }
        }

        // Événements
        evenementActif = config.getBoolean("evenement.actif", true);
        if (config.contains("evenement.liste")) {
            listeEvenements.clear();
            listeEvenements.addAll(config.getStringList("evenement.liste")); // ✅
        }

        p.sendMessage(ChatColor.GREEN + "Sauvegarde '" + saveName + "' chargée !");
    }


    
    public List<String> getPlayerSaves(Player p) {
        UUID uuid = p.getUniqueId();
        File dir = new File(getDataFolder(), "saves/" + uuid.toString());
        List<String> saves = new ArrayList<>();

        if (dir.exists() && dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.getName().endsWith(".yml")) {
                    saves.add(file.getName().replace(".yml", ""));
                }
            }
        }

        return saves;
    }
    
    private void loadRoleSection(ConfigurationSection section, String prefix) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                loadRoleSection((ConfigurationSection) value, prefix + key + ".");
            } else if (value instanceof Boolean) {
                roles.put(prefix + key, (Boolean) value);
            }
        }
    }

    
 // LGUHC.java
    public Set<String> getEnabledVillageMajeurs() { return enabledVillageMajeurs; }
    public Set<String> getEnabledVillageMineurs() { return enabledVillageMineurs; }
    public Set<String> getEnabledVillageAutres()  { return enabledVillageAutres; }
    public Set<String> getEnabledHybrides()       { return enabledHybrides; }
    public Set<String> getEnabledSolitaires()     { return enabledSolitaires; }

    // Loups activés = enabledRoles ∩ rolesLoups
    public Set<String> getEnabledLoups() {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (String r : enabledRoles) if (rolesLoups.contains(r)) s.add(r);
        return s;
    }
    
    private void ensureConfigExists() {
        File dir = getDataFolder();
        if (!dir.exists()) dir.mkdirs();

        File cfg = new File(dir, "config.yml");
        if (!cfg.exists()) {
            try (FileWriter w = new FileWriter(cfg)) {
                w.write("# LGUHC config (auto-généré)\n");
                w.write("wolfHowlEnabled: true\n");
                w.write("coupleEnabled: true\n");
                w.write("episodeMinutes: 10\n");
            } catch (IOException e) {
                getLogger().severe("Impossible de créer config.yml : " + e.getMessage());
            }
        }
    }
    
 // === Compteurs de rôles ===
 // clé = nom du rôle affiché dans les menus (ex: "Voyante", "Loup-Garou", "Fou du bus")
 private final java.util.Map<String, Integer> roleCounts = new java.util.HashMap<>();

 private static final int ROLE_MAX = 10;

 private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }


 // si tu gardes tes sets enabledVillage*/enabledRoles, on les sync ici
 private void setRoleEnabledFromCount(String role, boolean enabled) {
     if (rolesVillageMajeurs.contains(role) || rolesVillageMineurs.contains(role) || rolesVillageAutres.contains(role)) {
         setVillageRoleEnabled(role, enabled);
     } else if (rolesHybrides.contains(role)) {
         if (enabled) enabledHybrides.add(role); else enabledHybrides.remove(role);
     } else if (rolesLoups.contains(role) || "Loup-Garou Blanc".equals(role)) {
         if (enabled) enabledRoles.add(role); else enabledRoles.remove(role);
     } else { // Solitaires
         if (enabled) enabledSolitaires.add(role); else enabledSolitaires.remove(role);
     }
 }
 
 private java.util.List<String> enabledWithCounts(java.util.Collection<String> roles) {
	    java.util.List<String> out = new java.util.ArrayList<>();
	    for (String r : roles) {
	        int n = getRoleCount(r);
	        if (n > 0) out.add(r + " ×" + n);
	    }
	    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
	    return out;
	}
 
 /** Ajoute le lore d’aide à la tête (contrôles + bornes) */
 private void addCountLore(ItemMeta im) {
     List<String> lore = new ArrayList<>();
     lore.add(ChatColor.GRAY + "Clic gauche : +1");
     lore.add(ChatColor.GRAY + "Clic droit  : -1");
     lore.add(ChatColor.DARK_GRAY + "Maj+Gauche : +5");
     lore.add(ChatColor.DARK_GRAY + "Maj+Droit  : -5");
     lore.add(ChatColor.GRAY + "Min: 0  Max: " + ROLE_MAX);
     im.setLore(lore);
 }


public int getRoleCount(String displayName) {
  Integer v = roleCounts.get(displayName);
  return (v == null ? 0 : Math.max(0, Math.min(ROLE_MAX, v)));
}

public void setRoleCount(String displayName, int value) {
    int v = Math.max(0, Math.min(ROLE_MAX, value));
    roleCounts.put(displayName, v);
    // Optionnel: synchro immédiate d’un seul rôle
    syncEnabledSetsWithCount(displayName, v);

    // === Déchu -> verrou LG9 immédiat ===
    if (isDechuName(displayName)) {
        getGameManager().forceVoteLG9Lock(v > 0); // v>0 => lock LG9, v==0 => déverrouille
    }
}


//---- Synchro d'un seul rôle (appelée par setRoleCount) ----
private void syncEnabledSetsWithCount(String roleName, int count) {
  boolean enable = (count > 0);

  // Villageois
  if (rolesVillageMajeurs.contains(roleName)) {
      if (enable) enabledVillageMajeurs.add(roleName); else enabledVillageMajeurs.remove(roleName);
      return;
  }
  if (rolesVillageMineurs.contains(roleName)) {
      if (enable) enabledVillageMineurs.add(roleName); else enabledVillageMineurs.remove(roleName);
      return;
  }
  if (rolesVillageAutres.contains(roleName)) {
      if (enable) enabledVillageAutres.add(roleName); else enabledVillageAutres.remove(roleName);
      return;
  }

  // Loups (inclut LG Blanc si tu l’as dans rolesLoups ou séparé)
  if (rolesLoups.contains(roleName) || "Loup-Garou Blanc".equalsIgnoreCase(roleName)) {
      if (enable) enabledRoles.add(roleName); else enabledRoles.remove(roleName);
      return;
  }

  // Hybrides
  if (rolesHybrides.contains(roleName)) {
      if (enable) enabledHybrides.add(roleName); else enabledHybrides.remove(roleName);
      return;
  }

  // Solitaires (si tu as une liste globale des solitaires)
  if (rolesSolitaires != null && rolesSolitaires.contains(roleName)) {
      if (enable) enabledSolitaires.add(roleName); else enabledSolitaires.remove(roleName);
  }
}

//---- Synchro globale (appelée au démarrage, après reload config, etc.) ----
public void syncAllEnabledFromCounts() {
  // Optionnel: repartir d’un état propre
  enabledVillageMajeurs.clear();
  enabledVillageMineurs.clear();
  enabledVillageAutres.clear();
  enabledRoles.clear();        // loups (y compris LG Blanc si tu l’y ranges)
  enabledHybrides.clear();
  if (enabledSolitaires != null) enabledSolitaires.clear();

  // Repasser sur toutes tes listes de rôles connues et activer si count>0
  for (String r : rolesVillageMajeurs)  if (getRoleCount(r) > 0) enabledVillageMajeurs.add(r);
  for (String r : rolesVillageMineurs)  if (getRoleCount(r) > 0) enabledVillageMineurs.add(r);
  for (String r : rolesVillageAutres)   if (getRoleCount(r) > 0) enabledVillageAutres.add(r);

  for (String r : rolesLoups)           if (getRoleCount(r) > 0) enabledRoles.add(r);
  // Si le LG Blanc n’est pas dans rolesLoups, gère-le explicitement :
  if (getRoleCount("Loup-Garou Blanc") > 0) enabledRoles.add("Loup-Garou Blanc");

  for (String r : rolesHybrides)        if (getRoleCount(r) > 0) enabledHybrides.add(r);

  if (rolesSolitaires != null) {
      for (String r : rolesSolitaires)  if (getRoleCount(r) > 0) enabledSolitaires.add(r);
  }
  
  // === Vérifie si "Le déchu" est actif à partir des counts ===
  boolean dechuActif =
      getRoleCount("Le déchu")  > 0 ||
      getRoleCount("Le déchus") > 0 ||
      getRoleCount("Le dechu")  > 0 ||
      getRoleCount("Le dechus") > 0;

  getGameManager().forceVoteLG9Lock(dechuActif);
}


//LGUHC.java
public void setRandomEventPercent(String eventName, int pct) {
 if (randomEventPct == null) return;
 randomEventPct.put(eventName, Math.max(0, Math.min(100, pct)));
}

//LGUHC.java
public void disableRandomCoupleEventEverywhere() {
 try {
     setRandomEventPercent("Couple Aléatoire", 0);
 } catch (Throwable ignored) {}

 // if the running game exposes a flag:
 try {
     if (gameManager != null) gameManager.setRandomCoupleEnabled(false);
 } catch (Throwable ignored) {}
}


//LGUHC.java
private void normalizeOnlinePlayersForNewRun() {
 for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
     try {
         // Gamemode & sécurité
         p.setGameMode(org.bukkit.GameMode.SURVIVAL);
         p.setFireTicks(0);
         p.setNoDamageTicks(0);
         p.setFallDistance(0f);

         // Nourriture
         p.setFoodLevel(20);
         try { p.setSaturation(5.0f); } catch (Throwable ignored) {}

         // Effets de potions
         try {
             for (org.bukkit.potion.PotionEffect eff : p.getActivePotionEffects()) {
                 p.removePotionEffect(eff.getType());
             }
         } catch (Throwable ignored) {}

         // Absorption (NMS 1.8)
         try {
             ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) p)
                     .getHandle().setAbsorptionHearts(0.0F);
         } catch (Throwable ignored) {}

         // Cœurs max & vie courante (Spigot 1.8)
         try { p.setMaxHealth(20.0D); } catch (Throwable ignored) {}
         try { p.setHealth(20.0D); } catch (Throwable ignored) {}

         // (optionnel) compas neutre
         try { p.setCompassTarget(p.getWorld().getSpawnLocation()); } catch (Throwable ignored) {}

     } catch (Throwable t) {
         t.printStackTrace();
     }
 }
}

private boolean isDechuName(String name) {
    if (name == null) return false;
    String n = ChatColor.stripColor(name).toLowerCase(java.util.Locale.ROOT).trim();
    return n.equals("le déchu") || n.equals("le dechus") || n.equals("le déchus") || n.equals("le dechu");
}









 


    
    


    
    





 } 