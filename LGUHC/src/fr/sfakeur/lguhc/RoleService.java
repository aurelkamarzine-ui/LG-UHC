package fr.sfakeur.lguhc;

import org.bukkit.Bukkit;




import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.GameMode;

import org.bukkit.scheduler.BukkitTask;

//-- imports si besoin --
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // <-- IMPORTANT
import java.util.*;

import org.bukkit.plugin.Plugin;

import org.bukkit.Bukkit;          // si pas déjà importé



import java.util.*;

/**
 * Rôle central : - enums, état par joueur, mapping noms<->ids - assignation des
 * rôles + kits - API util (displayName, changeMaxHearts, pools constellations,
 * logs racontables) - délégation vers les handlers d’alignement (Village, Loup,
 * Neutre, Hybride) - listener minimal (pomme jumeaux) -> délègue au
 * VillageHandler
 */
public class RoleService implements org.bukkit.event.Listener {

	private final LGUHC plugin;
	private final GameManager game;

	private final LGUHC main; // si tu veux garder ce nom pour compatibilité

	// === Rôles ===
	public enum RoleId {
		ANALYSTE, CONSTELLATIONNISTE, CONTEUSE, DETECTIVE, JUMEAU, MONTREUR_DOURS, ORACLE, PRETRESSE, LOUP_GAROU,
		RENARD, VOYANTE, SIMPLE_VILLAGEOIS, BIBLIOTHECAIRE, VIEUX_SAGE, MARCHANDE_DE_FRUITS, ANCIEN, ERMITE,
		LOUP_GAROU_CHANCEUX, LOUP_GAROU_PEUREUX, ASSASSIN, CUPIDON, MINEUR, FOU_DU_BUS, PETITE_FILLE,
		LOUP_GAROU_PERFIDE, FEU_FOLLET, CUPIDON_V2, INFECT_PERE_DES_LOUPS, VOLEUR, SORCIERE, DECHU, LOUP_GAROU_AMNESIQUE,
		LOUP_GAROU_ALPHA,SOEUR, LOUP_GAROU_AMNESIQUE_V1,SERVANTE_DEVOUEE, BIENFAITEUR, LOUP_GAROU_FEUTRE,ARNACOEUR,
		LOUP_GAROU_TENEBREUX,ENCHANTERESSE,

	}

	public enum Align {
		VILLAGE, LOUP, HYBRIDE, SOLITAIRE
	}

	public enum Constellation {
		SINGE, TAUREAU, MOUTON, TIGRE, CHIEN
	}

	public enum Aura {
		LUMINEUSE, NEUTRE, OBSCURE
	}

	public enum Fruit {
		POMME, POIRE, PECHE
	}

	public enum FruitInfo {
		GAPPLES, AURA_BASE, KILLS
	}

	// RoleService.java
	public enum CoupleSide {
		VILLAGE, LOUP, COUPLE
	}

	public CoupleSide getCurrentCoupleSide() {
		java.util.UUID[] pair = getAliveLovers();
		if (pair == null)
			return CoupleSide.VILLAGE;

		RoleState sa = states.get(pair[0]);
		RoleState sb = states.get(pair[1]);
		if (sa == null || sb == null)
			return CoupleSide.VILLAGE;

		boolean bothVillage = (sa.align == Align.VILLAGE && sb.align == Align.VILLAGE);
		boolean bothWolves = (sa.align == Align.LOUP && sb.align == Align.LOUP);

		if (bothVillage)
			return CoupleSide.VILLAGE;
		if (bothWolves)
			return CoupleSide.LOUP;
		return CoupleSide.COUPLE;
	}

	// Flag interne pour savoir si on était en nuit au tick précédent
	private boolean lastNightFlag = false;

	// Qui a déjà tué au moins une fois
	private final Map<UUID, Integer> killsByPlayer = new HashMap<>();
	// Dernière action “racontable” timestamp ms par joueur
	private final Map<UUID, Long> lastRacMs = new HashMap<>();

	// --- Annonce du couple (déverrouille /don) ---

	private long coupleAnnounceMs = 0L;

	// RoleService.java
	private volatile boolean coupleAnnounced = false;

	public boolean isCoupleAnnounced() {
		return coupleAnnounced;
	}

	/**
	 * Annonce le couple MAINTENANT: déverrouille /don et marque loverAnnounced sur
	 * les 2.
	 */
	public void markCoupleAnnouncedNow() {
		this.coupleAnnounced = true;

	}

	public void resetCoupleAnnounced() {
		coupleAnnounced = false;
	}
	
	// --- Option globale : Loup-Garou Solitaire ---
	private boolean solitaireEnabled = false;
	private boolean solitaireTestMode = true; // ← pour tes tests : déclenche 60..120 s

	public boolean isSolitaireEnabled() { return this.solitaireEnabled; }
	public void setSolitaireEnabled(boolean enabled) { this.solitaireEnabled = enabled; }

	public boolean isSolitaireTestMode() { return this.solitaireTestMode; }
	public void setSolitaireTestMode(boolean test) { this.solitaireTestMode = test; }

	
	

	// ===================== ETAT PAR JOUEUR =====================
	public static final class RoleState {
		public final java.util.UUID owner;
		public final RoleId roleId;
		public final Align align;

		// Constellation / Aura (optionnels si tu utilises tes maps)
		public Constellation constellation = Constellation.MOUTON;
		public Aura aura = Aura.NEUTRE;

		// ----- Détective -----
		public int detectiveLastUsedEpisode = 0;
		public final java.util.Set<java.util.UUID> detectiveSeenPlayers = new java.util.HashSet<>();

		// ----- Jumeaux -----
		public java.util.UUID twinPartner = null;
		public boolean twinIsRoleSide = false;
		public long lastFraterniteHalfIdx = -1;
		public org.bukkit.scheduler.BukkitRunnable twinLineTask = null;
		public long lastAbsorptionCheck = 0;

		// ----- Analyste -----
		public boolean analysteAnalyseUsed = false;
		public int analysteUsesLeft = 5;
		public long analysteLastUseSec = 0;
		public final java.util.Set<java.util.UUID> analysteObserved = new java.util.HashSet<>();
		public final java.util.Map<java.util.UUID, java.util.Set<org.bukkit.potion.PotionEffectType>> analysteLastObserved = new java.util.HashMap<>();

		// ----- Oracle -----
		public long oracleLastUseSec = 0;

		// Vision de nuit appliquée (flag)
		public boolean nightVisionApplied = false;

		// ----- Prêtresse -----
		public final java.util.Set<java.util.UUID> pretresseSeenWolves = new java.util.HashSet<>();

		// ----- Constellationniste -----
		public long lastTelescopeNight = -1;
		public boolean usedAstrologie = false;
		public org.bukkit.scheduler.BukkitRunnable telescopeTrailTask = null;

		// ----- Renard -----
		public int renardSuccesses = 0;
		public final java.util.LinkedHashSet<java.util.UUID> renardTargets = new java.util.LinkedHashSet<>();
		public java.util.UUID renardActiveTarget = null;
		public long renardEndAtMs = 0L;
		public org.bukkit.scheduler.BukkitRunnable renardTask = null;

		// ----- Bibliothécaire -----
		public java.util.UUID biblioBorrower = null;
		public int biblioArchiveLeft = 3;
		public int biblioLoanEpisode = -1;
		public int kills = 0;
		public long lastRacSec = 0L;

		// ----- Vieux Sage -----
		public int sageLumCount = 0;
		public int sageNeuCount = 0;
		public int sageObsCount = 0;

		// ----- Marchande de Fruits -----
		public int fruitSalesDone = 0;
		public java.util.UUID fruitSaleTarget = null;
		public org.bukkit.scheduler.BukkitRunnable fruitSaleTask = null;
		public java.util.EnumSet<Fruit> fruitUsed = java.util.EnumSet.noneOf(Fruit.class);
		public java.util.EnumSet<FruitInfo> infosGiven = java.util.EnumSet.noneOf(FruitInfo.class);
		public final java.util.EnumMap<Fruit, FruitInfo> fruitMap = new java.util.EnumMap<>(Fruit.class);

		// ----- Ancien -----
		public boolean ancienResistanceActive = true;
		public boolean ancienResUsed = false;
		public boolean ancienLostResistance = false;

		// ----- Ermite -----
		public int ermiteLastProfile = -1; // -1 inconnu, 0=Seul, 1=≤3, 2=≥4

		// ----- LG Chanceux -----
		public enum LuckyBuff {
			RESIST, REGEN, STRENGTH, SPEED
		}

		public LuckyBuff luckyTonight = null;
		public boolean luckyNightApplied = false;

		// ----- Assassin -----
		public int assassinConcealLeft = 2;

		// ----- Lover -----
		public boolean loverCompassActive = false;
		public java.util.UUID lover = null;
		public boolean loverMet = false;
		public boolean loveFixed = false;
		public long loveDeadlineMs = 0L;
		public java.util.UUID lovePickA = null;
		public java.util.UUID lovePickB = null;
		public boolean loverDoomed = false;
		public boolean loverAnnounced = false;

		// >>> AJOUTE CES DEUX-LÀ <<<
		public boolean loveRandomMode = false; // mode "couple aléatoire" actif pour Cupidon
		public boolean loveRevealedToCupid = false; // Cupidon a reçu l’info après le délai

		// ----- Hybride -----
		public Align hybridChosen = null;

		// ----- Fou du Bus -----
		public boolean fdbCenterAnnounced = false;
		public int fdbTalksUsed = 0;
		public long fdbNextTalkAllowedSec = 0L;
		public java.util.UUID fdbTrackTarget = null;
		public long fdbTrackEndMs = 0L;
		public int fdbTrackEpisode = -1;
		public final java.util.Set<java.util.UUID> fdbAlreadyMet = new java.util.HashSet<>();
		public final java.util.Map<java.util.UUID, Long> fdbQuestDeadlineMs = new java.util.HashMap<>();
		public final java.util.Map<java.util.UUID, Integer> fdbSneakProgressSec = new java.util.HashMap<>();
		public final java.util.Set<java.util.UUID> fdbQuestCompleted = new java.util.HashSet<>();
		public final java.util.Set<java.util.UUID> fdbPendingPenaltyNextEpisode = new java.util.HashSet<>();
		public int fdbTalkUsesLeft = 3;
		public long fdbTalkCooldownEndMs = 0L;
		public org.bukkit.scheduler.BukkitTask fdbHudTask = null;

		// ----- Invis commun PF/Perfide/Feu Follet -----
		public boolean invisActive = false;
		public long invisEndMs = 0L;
		public long invisCooldownEndMs = 0L;
		public boolean invisAnnounced = false;

		// Petite Fille : HUD hurlement
		public long pfHowlHudEndMs = 0L;
		// PF : 1x/nuit
		public long pfLastDetectionNightIndex = -1;

		// Feu Follet : plume + folie
		public int ffFeatherUsesLeft = 2;
		public long ffFeatherCdEndMs = 0L;
		public long ffFolieCdEndMs = 0L;
		public long ffFolieEndMs = 0L;
		public long ffFeatherLastUseMs = 0L; // anti double-fire client

		// Loup : hurlement 1x/partie
		public boolean wolfHowlUsed = false;

		// ==== Cupidon v2 ====
		public boolean cupid2Used = false; // 1x par partie
		public java.util.LinkedHashSet<java.util.UUID> cupid2Tagged = new java.util.LinkedHashSet<>(); // joueurs
																										// touchés

		// === Cupidon v2 ===
		public boolean cupid2Maker = false; // est-ce ce joueur qui a créé le couple v2 ?
		public UUID cupid2A = null; // lover A fixé par Cupidon v2
		public UUID cupid2B = null; // lover B fixé par Cupidon v2
		public boolean cupid2CoupleAlive = false; // les 2 vivants ?
		public boolean cupid2CoupleBothVillage = false; // les 2 lovers étaient village ?


		// --- Infect Père des Loups ---
		public final java.util.Map<java.util.UUID, Integer> ipdlProxSec = new java.util.HashMap<>();
		public final java.util.Set<java.util.UUID> ipdlMarked = new java.util.HashSet<>();
		public boolean ipdlInfectUsed = false; // 1x par partie

		// Infection appliquée à une cible (côté victime)
		public boolean infectedAsWolf = false; // donne les “capas de loup”
		public boolean infectedNoWolfWin = false; // ne compte pas avec loups à la win (lover / LGB / ange)
		
		// RoleState.java (ou dans RoleService.RoleState)
		 public final java.util.Map<java.util.UUID, Long> ipdlLastSeenMs = new java.util.HashMap<>();
		 public final java.util.Map<java.util.UUID, Long> ipdlLastIncMs  = new java.util.HashMap<>();
		 // ipdlProxSec et ipdlMarked existent déjà chez toi : on les garde

		 public long _ipdlLastHudMs = 0L; // ← celui qui manque
		 
		// --- IPDL ---
		 public java.util.UUID ipdlMarkedTarget = null; // une seule cible marquée dans toute la partie
		 
		// RoleService.RoleState
		// --- Voleur ---
		public boolean thiefStole = false;                  // a-t-il déjà volé ?
		public RoleService.RoleId thiefStolenRole = null;   // rôle volé (si besoin de log)
		public Align thiefOriginalAlign = Align.HYBRIDE;  // align de départ

		// Affichage info: toujours “Voleur”
		public boolean thiefMaskInfo = false;               // true => les rôles à info verront “Voleur”

		// Résistance I tant qu’il n’a pas tué
		public boolean thiefResiArmed = false;              // pour (re)donner Résistance en tick si retirée

		// --- Voleur ---
		public boolean voleurStolen = false;                 // a-t-il déjà volé ?
		public RoleId  voleurStolenFrom = null;              // rôle volé
		public Align   voleurWinAlign = Align.HYBRIDE;        // align de victoire une fois volé
		public boolean voleurMaskInfo = true;                // toujours révélé comme "Voleur" aux rôles d'information

		public boolean sorciereUsed = false; // 1x par partie
		
		// Sorcière
		public boolean witchResUsed = false;     // a déjà utilisé Résurrection ?
		public boolean witchCurseUsed = false;   // a déjà utilisé Malédiction ?

		// Côté cible (tueur maudit) : applique le -1♥ abso sur les gapples
		public boolean witchCursed = false;
		
		// Déchu
		public int dechuHeartsAwarded = 0; // cœurs permanents déjà donnés (0..5)

		// Amnésique LG
		public final java.util.Set<java.util.UUID> amnDiscovered = new java.util.HashSet<>(); // loups reconnus par proximité
		public boolean amnVisibleToWolves = false; // quand TRUE, les autres loups voient cet Amnésique dans leur liste
		
		public int alphaCallLastEpisode = 0; // épisode où /lg call a été utilisé (0 = jamais)
		
		// Pairage Sœur
		public java.util.UUID soeurPartner = null;

		// EP3+5min : déblocage de la ligne de particules (flag pas obligatoire mais pratique)
		public boolean soeurLineUnlocked = false;

		// Offre “Lien du sang”
		public boolean soeurRevealUsed = false;          // a déjà choisi pseudo/role
		public boolean soeurChoiceAvailable = false;     // une offre est active
		public java.util.UUID soeurOfferKillerId = null; // assassin de sa sœur (si player)
		
		// --- Amnésique v1 ---
		public boolean amnV1Awake = false;           // devient vrai quand il prend un coup d’un loup
		public long   amnV1AwakenAtMs = 0L;          // timestamp du “réveil”
		public boolean amnV1ListActive = false;      // devient vrai 3 min après le réveil (liste existe mais vide)
		public long   amnV1NextGrantMs = 0L;         // prochaine “révélation” de loup (toutes les 5 min)
		public java.util.LinkedHashSet<java.util.UUID> amnV1KnownWolves = new java.util.LinkedHashSet<>();
		// On réutilise s.amnVisibleToWolves (déjà présent pour l’amnésique “classique”) pour son affichage dans la liste des autres loups.


		public long    amnV1TriggerAtMs = 0L;        // premier coup d’un loup (horodatage)
		public long    amnV1NextRevealAtMs = 0L;     // prochaine “révélation” d’un nom (toutes les 5 min)

		public boolean amnV1VisibleToWolves = false;       // les autres loups le voient (à l’éveil)
		
		// Sœur : affichage du pseudo retardé
		public boolean soeurNameRevealed = false;

		// Jumeau : affichage du pseudo retardé
		public boolean twinNameRevealed = false;
		
		// Voyante: une utilisation max par épisode
		public int voyanteLastUsedEpisode = -1;
		
		// --- Infect (Père des Loups) ---
		public boolean infectUsed = false;                 // pouvoir déjà utilisé ?
		public java.util.UUID infectPendingVictim = null;  // cible en attente
		public long infectOfferExpireAtMs = 0L;            // expiration (ms)
		
		// Si true : l'Amnésique v1 reste vu comme Simple Villageois par les rôles à information, même après réveil
		private static final boolean AMN_V1_MASKS_INFO_AFTER_WAKE = true;
		
		// --- Solitaire ---
		public boolean solitaire = false;           // devient Loup Solitaire
		public boolean solitaireHeartsGiven = false; // pour ne pas donner les PV plusieurs fois
		
		// --- Option globale : Loup-Garou Solitaire ---
		private boolean solitaireEnabled = false;
		
		// --- Servante Dévouée ---
		public boolean servanteUsed = false;             // a déjà approprié (1x/partie)
		public RoleId servanteStolenFrom = null;         // rôle duquel elle a pris les pouvoirs

		// (exemples de compteurs si tu veux gérer certains pouvoirs)
		public boolean servanteVoyanteUsedThisEp = false;
		public int     servanteVoyanteLastEp      = 0;
		
		// RoleService.RoleState
		public int fdbPenaltyHeartsLost = 0; // nombre de coeurs perdus définitivement par FDB (2 -> -2♥)
		
		// RoleService.RoleState
		public boolean ancienNoFallPermanent = false; // passe à true après résurrection
		
		// --- Sorcière: prompts stockés par-joueuse ---
		public java.util.UUID witchPromptVictimId = null;   // cible à ressusciter
		public java.util.UUID witchPromptKillerId = null;   // tueur à maudire (peut être null)

		 // Bienfaiteur
	    public int  bienfUsesLeft = 3;                 // 3 utilisations de /lg vie
	    public java.util.Set<java.util.UUID> bienfGiven = new java.util.HashSet<>(); // cibles déjà boostées
	    public boolean bienfRegenActive = false;       // une fois les 3 cœurs donnés
	    public long bienfNextRegenMs = 0L;             // prochaine tick de régén (30s)
	    
	    // RoleService.RoleState
	    // --- LG Feutré (façade affichée aux rôles à infos) ---
	    public RoleId feutreShownRole = null; // rôle “d’affichage” (non-loup)
	    public int    feutreEpisode   = -1;   // épisode du tirage
	    public boolean feutreActive   = false;

	    // RoleService.RoleState
	    // --- Arnacoeur ---
	    public int arnaTotalHalvesStolen = 0; // total volé (en demi-coeurs), cap 6
	    public java.util.Map<java.util.UUID, Integer> arnaVictimHalves = new java.util.HashMap<>(); // victime -> nb de ½♥ volés
	 	public java.util.Map<java.util.UUID, Long> arnaQueuedAtMs = new java.util.HashMap<>(); // cible -> when queued
	 	public java.util.Map<java.util.UUID, Long> arnaFireAtMs = new java.util.HashMap<>();   // cible -> when to apply (now+5min)
	 	public java.util.Set<java.util.UUID> arnaRecentlyRestored = new java.util.HashSet<>(); // victimes protégées 10min après restitution
	 	public long arnaLastDeathRestoreMs = 0L; // dernier moment où on a restitué (info/debug)

	 	public boolean arnaPermanentSpeedGiven = false; // flag si speed permanent posé (3♥ volés)
	 	
	 // Arnacœur
	 	public java.util.Map<java.util.UUID, Integer> arnaStolenHalves = new java.util.HashMap<>(); // cible -> nb ½♥ volés
	 	public int   arnaHalvesTotal = 0;                      // total de ½♥ (max 6 = 3♥)
	 	public long  arnaNextStealAllowedAtMs = 0L;            // cooldown après sa mort (10 min)
	 	
	 	//LG Ténéreux
	 	
	 	public long tenebresLastUseSec = 0L; // /lg tenebres — dernier usage
	 	public static final long TENEBRES_COOLDOWN_SEC = 120L; // 2 min (ajuste si besoin)

	 	//Enchanteresse
	 	
	 // --- Enchanteresse ---
	 	public boolean enchUsed = false;                  // 1x/partie
	 	public java.util.UUID enchTarget = null;          // cible en cours (si prompt envoyé)
	 	public org.bukkit.scheduler.BukkitTask enchTask;  // timeout/maintenance (optionnel)

	 	// mapping aléatoire des mots-clés -> catégorie (figé au 1er usage)
	 	public enum EnchKey { SAVOIR_FAIRE, DEXTERITE, FORCE }
	 	public enum EnchCat { ARMURE, EPEE, ARC }
	 	public java.util.EnumMap<EnchKey, EnchCat> enchMap = null;

	 // Enchanteresse
	 	public long enchLastUseMs = 0L;     // cooldown 20 minutes entre utilisations



		
		





		





		



		
		
		
		
		
		

		// ----- Ctor -----
		public RoleState(java.util.UUID owner, RoleId roleId, Align align) {
			this.owner = owner;
			this.roleId = roleId;
			this.align = align;
			
			
			// si tu as des tables globales dans RoleService pour init :
			try {
				Constellation c = ROLE_TO_CONSTELLATION.get(roleId);
				if (c != null)
					this.constellation = c;
			} catch (Throwable ignored) {
			}
			try {
				Aura a = ROLE_TO_AURA.get(roleId);
				if (a != null)
					this.aura = a;
			} catch (Throwable ignored) {
			}

			if (this.roleId == RoleId.MARCHANDE_DE_FRUITS) {
				java.util.List<Fruit> fruits = java.util.Arrays.asList(Fruit.POMME, Fruit.POIRE, Fruit.PECHE);
				java.util.List<FruitInfo> infos = java.util.Arrays.asList(FruitInfo.GAPPLES, FruitInfo.KILLS,
						FruitInfo.AURA_BASE);
				java.util.Collections.shuffle(infos);
				for (int i = 0; i < fruits.size(); i++)
					fruitMap.put(fruits.get(i), infos.get(i));
			}
		}
	}
	
	

	private final Map<UUID, RoleState> states = new HashMap<>();

	// === Tables rôles → constellations/aura ===
	private static final Map<RoleId, Constellation> ROLE_TO_CONSTELLATION = new HashMap<>();
	static {
		ROLE_TO_CONSTELLATION.put(RoleId.ANALYSTE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.CONSTELLATIONNISTE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.SIMPLE_VILLAGEOIS, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.CONTEUSE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.DETECTIVE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.JUMEAU, Constellation.TAUREAU);
		ROLE_TO_CONSTELLATION.put(RoleId.MONTREUR_DOURS, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.ORACLE, Constellation.CHIEN);
		ROLE_TO_CONSTELLATION.put(RoleId.PRETRESSE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU, Constellation.CHIEN);
		ROLE_TO_CONSTELLATION.put(RoleId.RENARD, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.VOYANTE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.BIBLIOTHECAIRE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.VIEUX_SAGE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.MARCHANDE_DE_FRUITS, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.ANCIEN, Constellation.TAUREAU);
		ROLE_TO_CONSTELLATION.put(RoleId.ERMITE, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_CHANCEUX, Constellation.CHIEN);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_PEUREUX, Constellation.CHIEN);
		ROLE_TO_CONSTELLATION.put(RoleId.ASSASSIN, Constellation.TIGRE);
		ROLE_TO_CONSTELLATION.put(RoleId.CUPIDON, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.MINEUR, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.FOU_DU_BUS, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.PETITE_FILLE, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_PERFIDE, Constellation.CHIEN);
		ROLE_TO_CONSTELLATION.put(RoleId.FEU_FOLLET, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.CUPIDON_V2, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.INFECT_PERE_DES_LOUPS, Constellation.TIGRE);
		ROLE_TO_CONSTELLATION.put(RoleId.VOLEUR, Constellation.TAUREAU);
		ROLE_TO_CONSTELLATION.put(RoleId.SORCIERE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.DECHU, Constellation.TAUREAU); 
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_AMNESIQUE, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_ALPHA, Constellation.CHIEN);
		ROLE_TO_CONSTELLATION.put(RoleId.SOEUR, Constellation.TAUREAU);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_AMNESIQUE_V1, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.SERVANTE_DEVOUEE, Constellation.MOUTON);
		ROLE_TO_CONSTELLATION.put(RoleId.BIENFAITEUR, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_FEUTRE, Constellation.SINGE);
		ROLE_TO_CONSTELLATION.put(RoleId.ARNACOEUR, Constellation.TAUREAU);
		ROLE_TO_CONSTELLATION.put(RoleId.LOUP_GAROU_TENEBREUX, Constellation.TIGRE);
		ROLE_TO_CONSTELLATION.put(RoleId.ENCHANTERESSE, Constellation.TIGRE);

	}

	private static final Map<RoleId, Aura> ROLE_TO_AURA = new HashMap<>();
	static {
		ROLE_TO_AURA.put(RoleId.ANALYSTE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.CONSTELLATIONNISTE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.SIMPLE_VILLAGEOIS, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.CONTEUSE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.DETECTIVE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.JUMEAU, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.MONTREUR_DOURS, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.ORACLE, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.PRETRESSE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.RENARD, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.VOYANTE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.BIBLIOTHECAIRE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.VIEUX_SAGE, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.MARCHANDE_DE_FRUITS, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.ANCIEN, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.ERMITE, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_CHANCEUX, Aura.LUMINEUSE); // demandé
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_PEUREUX, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.ASSASSIN, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.CUPIDON, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.MINEUR, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.FOU_DU_BUS, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.PETITE_FILLE, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_PERFIDE, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.FEU_FOLLET, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.CUPIDON_V2, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.INFECT_PERE_DES_LOUPS, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.VOLEUR, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.SORCIERE, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.DECHU, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_AMNESIQUE, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_ALPHA, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.SOEUR, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_AMNESIQUE_V1, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.SERVANTE_DEVOUEE, Aura.NEUTRE);
		ROLE_TO_AURA.put(RoleId.BIENFAITEUR, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_FEUTRE, Aura.OBSCURE);
		ROLE_TO_AURA.put(RoleId.ARNACOEUR, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.LOUP_GAROU_TENEBREUX, Aura.LUMINEUSE);
		ROLE_TO_AURA.put(RoleId.ENCHANTERESSE, Aura.LUMINEUSE);

	}

	// === Mapping noms -> RoleId (menus) ===
	private static final Map<String, RoleId> NAME_TO_ROLE = new HashMap<>();
	static {
		NAME_TO_ROLE.put("analyste", RoleId.ANALYSTE);
		NAME_TO_ROLE.put("constellationniste", RoleId.CONSTELLATIONNISTE);
		NAME_TO_ROLE.put("conteuse", RoleId.CONTEUSE);
		NAME_TO_ROLE.put("simple villageois", RoleId.SIMPLE_VILLAGEOIS);
		NAME_TO_ROLE.put(norm("detective"), RoleId.DETECTIVE);
		NAME_TO_ROLE.put(norm("détective"), RoleId.DETECTIVE);
		NAME_TO_ROLE.put("jumeau", RoleId.JUMEAU);
		NAME_TO_ROLE.put(norm("montreur d'ours"), RoleId.MONTREUR_DOURS);
		NAME_TO_ROLE.put(norm("montreur d ours"), RoleId.MONTREUR_DOURS);
		NAME_TO_ROLE.put("oracle", RoleId.ORACLE);
		NAME_TO_ROLE.put(norm("pretresse"), RoleId.PRETRESSE);
		NAME_TO_ROLE.put(norm("prêtresse"), RoleId.PRETRESSE);
		NAME_TO_ROLE.put(norm("Loup-Garou"), RoleId.LOUP_GAROU);
		NAME_TO_ROLE.put(norm("Loup Garou"), RoleId.LOUP_GAROU);
		NAME_TO_ROLE.put("renard", RoleId.RENARD);
		NAME_TO_ROLE.put("voyante", RoleId.VOYANTE);
		NAME_TO_ROLE.put(norm("bibliothecaire"), RoleId.BIBLIOTHECAIRE);
		NAME_TO_ROLE.put(norm("bibliothécaire"), RoleId.BIBLIOTHECAIRE);
		NAME_TO_ROLE.put("vieux sage", RoleId.VIEUX_SAGE);
		NAME_TO_ROLE.put(norm("marchande de fruits"), RoleId.MARCHANDE_DE_FRUITS);
		NAME_TO_ROLE.put(norm("marchande"), RoleId.MARCHANDE_DE_FRUITS);
		NAME_TO_ROLE.put("ancien", RoleId.ANCIEN);
		NAME_TO_ROLE.put("ermite", RoleId.ERMITE);
		NAME_TO_ROLE.put(norm("Loup-Garou Chanceux"), RoleId.LOUP_GAROU_CHANCEUX);
		NAME_TO_ROLE.put(norm("Loup Garou Chanceux"), RoleId.LOUP_GAROU_CHANCEUX);
		NAME_TO_ROLE.put(norm("LG Chanceux"), RoleId.LOUP_GAROU_CHANCEUX);
		NAME_TO_ROLE.put(norm("Loup-Garou Peureux"), RoleId.LOUP_GAROU_PEUREUX);
		NAME_TO_ROLE.put(norm("Loup Garou Peureux"), RoleId.LOUP_GAROU_PEUREUX);
		NAME_TO_ROLE.put(norm("LG Peureux"), RoleId.LOUP_GAROU_PEUREUX);
		NAME_TO_ROLE.put(norm("Assassin"), RoleId.ASSASSIN);
		NAME_TO_ROLE.put("cupidon", RoleId.CUPIDON);
		NAME_TO_ROLE.put("mineur", RoleId.MINEUR);
		NAME_TO_ROLE.put(norm("fou du bus"), RoleId.FOU_DU_BUS);
		NAME_TO_ROLE.put(norm("petite fille"), RoleId.PETITE_FILLE);
		NAME_TO_ROLE.put(norm("Louo Garou Perfide"), RoleId.LOUP_GAROU_PERFIDE);
		NAME_TO_ROLE.put(norm("Loup-Garou Perfide"), RoleId.LOUP_GAROU_PERFIDE);
		NAME_TO_ROLE.put(norm("Feu Follet"), RoleId.FEU_FOLLET);
		NAME_TO_ROLE.put(norm("Cupidon v2"), RoleId.CUPIDON_V2);
		NAME_TO_ROLE.put(norm("Infect Pere Des Loups"), RoleId.INFECT_PERE_DES_LOUPS);
		NAME_TO_ROLE.put(norm("Infect Père Des Loups"), RoleId.INFECT_PERE_DES_LOUPS);
		NAME_TO_ROLE.put(norm("IPDL"), RoleId.INFECT_PERE_DES_LOUPS);
		NAME_TO_ROLE.put(norm("Voleur"), RoleId.VOLEUR);
	    NAME_TO_ROLE.put(norm("sorciere"), RoleId.SORCIERE);
	    NAME_TO_ROLE.put(norm("sorcière"), RoleId.SORCIERE);
	    NAME_TO_ROLE.put(norm("dechu"), RoleId.DECHU);
	    NAME_TO_ROLE.put(norm("ledechu"), RoleId.DECHU);
	    NAME_TO_ROLE.put(norm("dechus"), RoleId.DECHU);     // tolérance si faute
	    NAME_TO_ROLE.put(norm("ledechus"), RoleId.DECHU); 
	 // RoleService – init des mappings noms -> RoleId
	    NAME_TO_ROLE.put(norm("Le déchu"),  RoleId.DECHU);
	    NAME_TO_ROLE.put(norm("Le dechus"), RoleId.DECHU);
	    NAME_TO_ROLE.put(norm("Le déchus"), RoleId.DECHU);
	    NAME_TO_ROLE.put(norm("Le dechu"),  RoleId.DECHU);
	    NAME_TO_ROLE.put(norm("Loup-Garou Amnésique"), RoleId.LOUP_GAROU_AMNESIQUE);
	    NAME_TO_ROLE.put(norm("Loup-Garou Amnesique"), RoleId.LOUP_GAROU_AMNESIQUE);
	    NAME_TO_ROLE.put(norm("Loup Garou Amnésique"), RoleId.LOUP_GAROU_AMNESIQUE);
	    NAME_TO_ROLE.put(norm("Loup Garou Amnesique"), RoleId.LOUP_GAROU_AMNESIQUE);
	    NAME_TO_ROLE.put(norm("Loup-Garou Alpha"), RoleId.LOUP_GAROU_ALPHA);
	    NAME_TO_ROLE.put(norm("Loup Garou Alpha"), RoleId.LOUP_GAROU_ALPHA);
	    NAME_TO_ROLE.put(norm("Sœur"), RoleId.SOEUR);
	    NAME_TO_ROLE.put(norm("Soeur"), RoleId.SOEUR); // fallback sans accent
	    NAME_TO_ROLE.put(norm("Loup-Garou Amnésique v1"), RoleId.LOUP_GAROU_AMNESIQUE_V1);
	    NAME_TO_ROLE.put(norm("Loup Garou Amnésique v1"), RoleId.LOUP_GAROU_AMNESIQUE_V1);
	    NAME_TO_ROLE.put(norm("Loup-Garou Amnesique v1"), RoleId.LOUP_GAROU_AMNESIQUE_V1);
	    NAME_TO_ROLE.put(norm("Loup Garou Amnesique v1"), RoleId.LOUP_GAROU_AMNESIQUE_V1);
	    NAME_TO_ROLE.put(norm("Servante Dévouée"), RoleId.SERVANTE_DEVOUEE);
	    NAME_TO_ROLE.put(norm("Servante Devouee"), RoleId.SERVANTE_DEVOUEE);
	    NAME_TO_ROLE.put(norm("Bienfaiteur"), RoleId.BIENFAITEUR);
	    NAME_TO_ROLE.put(norm("Loup-Garou Feutre"), RoleId.LOUP_GAROU_FEUTRE);
	    NAME_TO_ROLE.put(norm("Loup Garou Feutre"), RoleId.LOUP_GAROU_FEUTRE);
	    NAME_TO_ROLE.put(norm("Loup-Garou Feutré"), RoleId.LOUP_GAROU_FEUTRE);
	    NAME_TO_ROLE.put(norm("Loup Garou Feutré"), RoleId.LOUP_GAROU_FEUTRE);
	    NAME_TO_ROLE.put(norm("Arnacoeur"), RoleId.ARNACOEUR);
	    NAME_TO_ROLE.put(norm("Loup-Garou Tenebreux"), RoleId.LOUP_GAROU_TENEBREUX);
	    NAME_TO_ROLE.put(norm("Loup-Garou Ténébreux"), RoleId.LOUP_GAROU_TENEBREUX);
	    NAME_TO_ROLE.put(norm("Loup Garou Tenebreux"), RoleId.LOUP_GAROU_TENEBREUX);
	    NAME_TO_ROLE.put(norm("Loup Garou Ténébreux"), RoleId.LOUP_GAROU_TENEBREUX);
	    NAME_TO_ROLE.put(norm("Enchanteresse"), RoleId.ENCHANTERESSE);


	}

	// Exemple de norm (si tu ne l’as pas déjà)
	private static String norm(String s) {
	    if (s == null) return "";
	    String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
	            .replaceAll("\\p{M}+","")      // enlève accents
	            .toLowerCase(java.util.Locale.ROOT)
	            .replaceAll("[^a-z0-9]+","");  // enlève espaces/ponctuation
	    return n;
	}


	// === Logs “Actions racontables” ===
	private final Map<Integer, Map<UUID, Set<String>>> racLogByEpisode = new HashMap<>();

	private Map<UUID, Set<String>> getEpisodeBucket(int ep) {
		return racLogByEpisode.computeIfAbsent(ep, k -> new HashMap<>());
	}

	// === Constellations : pools par signe (utilisé par Constellationniste) ===
	private static final Map<Constellation, List<RoleId>> CONSTELLATION_POOLS = new EnumMap<>(Constellation.class);
	static {
		CONSTELLATION_POOLS.put(Constellation.SINGE, new ArrayList<>(Arrays.asList(RoleId.ANALYSTE,
				RoleId.CONSTELLATIONNISTE, RoleId.SIMPLE_VILLAGEOIS, RoleId.DETECTIVE, RoleId.CONTEUSE)));
		CONSTELLATION_POOLS.put(Constellation.TAUREAU,
				new ArrayList<>(Collections.singletonList(RoleId.SIMPLE_VILLAGEOIS)));
		CONSTELLATION_POOLS.put(Constellation.MOUTON,
				new ArrayList<>(Collections.singletonList(RoleId.SIMPLE_VILLAGEOIS)));
		CONSTELLATION_POOLS.put(Constellation.TIGRE,
				new ArrayList<>(Collections.singletonList(RoleId.SIMPLE_VILLAGEOIS)));
		CONSTELLATION_POOLS.put(Constellation.CHIEN,
				new ArrayList<>(Collections.singletonList(RoleId.SIMPLE_VILLAGEOIS)));
	}

	public static List<RoleId> getConstellationPool(Constellation c) {
		return CONSTELLATION_POOLS.getOrDefault(c, Collections.emptyList());
	}

	public RoleService(GameManager game, LGUHC main) {
		this.game = java.util.Objects.requireNonNull(game, "game");
		this.main = java.util.Objects.requireNonNull(main, "main");
		this.plugin = main;

		this.villageHandler = new VillageHandler(this);
		this.wolfHandler = new WolfHandler(this);
		this.neutralHandler = new NeutralHandler(this);
		this.hybridHandler = new HybridHandler(this);
	}

	public LGUHC getPlugin() {
		return plugin;
	}

	public GameManager getGame() {
		return game;
	}

	public Map<UUID, RoleState> getStates() {
		return states;
	}

	public RoleId nameToRole(String name) {
		return NAME_TO_ROLE.get(norm(name));
	}

	private Align alignOfInternal(RoleId r) {
		switch (r) {
		case ANALYSTE:
		case CONSTELLATIONNISTE:
		case CONTEUSE:
		case SIMPLE_VILLAGEOIS:
		case DETECTIVE:
		case JUMEAU:
		case MONTREUR_DOURS:
		case ORACLE:
		case PRETRESSE:
		case RENARD:
		case VOYANTE:
		case BIBLIOTHECAIRE:
		case VIEUX_SAGE:
		case MARCHANDE_DE_FRUITS:
		case ANCIEN:
		case ERMITE:
		case MINEUR:
		case PETITE_FILLE:
		case SORCIERE:
		case DECHU:
		case SOEUR:
		case SERVANTE_DEVOUEE:
		case BIENFAITEUR:
		case ENCHANTERESSE:
			return Align.VILLAGE;
		case LOUP_GAROU:
		case LOUP_GAROU_CHANCEUX:
		case LOUP_GAROU_PEUREUX:
		case LOUP_GAROU_PERFIDE:
		case INFECT_PERE_DES_LOUPS:
		case LOUP_GAROU_AMNESIQUE:
		case LOUP_GAROU_ALPHA:
		case LOUP_GAROU_AMNESIQUE_V1:
		case LOUP_GAROU_FEUTRE:
		case LOUP_GAROU_TENEBREUX:
			return Align.LOUP;
		case ASSASSIN:
		case FOU_DU_BUS:
		case FEU_FOLLET:
		case ARNACOEUR:
			return Align.SOLITAIRE;
		case CUPIDON:
		case CUPIDON_V2:
		case VOLEUR:
			return Align.HYBRIDE;

		default:
			return Align.VILLAGE;
		}
	}

	public Align alignOf(RoleId r) {
		return alignOfInternal(r);
	}

	public void assignRandomRoles(Collection<Player> players, List<String> enabledPool,
			boolean allowDuplicatesIgnored) {
		final int rsHash = System.identityHashCode(this);
		Bukkit.getLogger().info(
				"[RoleService@" + rsHash + "] assignRandomRoles() players=" + (players == null ? 0 : players.size()));

		if (players == null || players.isEmpty()) {
			Bukkit.getLogger().warning("[RoleService@" + rsHash + "] No players to assign.");
			return;
		}

		// 0) Reset propre
		states.clear();

		// 1) Construire counts à partir des noms visibles (menus) -> RoleId
		java.util.Map<RoleId, Integer> counts = new java.util.HashMap<>();
	    boolean dechuActif = false; // ✅ DÉCLARATION ICI
		 if (enabledPool != null) {
		        for (String displayName : enabledPool) {
		        	RoleId rid = NAME_TO_ROLE.get(norm(displayName));
		        	if (rid == null) {
		        	    Bukkit.getLogger().warning("[RoleService@" + rsHash + "] Unknown role in enabledPool: " + displayName);
		        	    continue;
		        	}
		        	if (rid == RoleId.DECHU) dechuActif = true;
		            
		            plugin.getGameManager().forceVoteLG9Lock(dechuActif);

		            int n = Math.max(0, plugin.getRoleCount(displayName));


				if (rid == RoleId.JUMEAU) {
					// Le menu "Jumeaux" est exprimé en NOMBRE DE PAIRES
					int pairs = Math.min(ROLE_MAX, n);

					// (sécurité) pas plus de paires que joueurs/2,
					// sinon on risque d'avoir un jumeau seul après trim
					int maxPairs = (players != null) ? (players.size() / 2) : pairs;
					pairs = Math.min(pairs, Math.max(0, maxPairs));

					n = pairs * 2; // convertir paires -> joueurs
				}
				
		        // ✅ SŒUR : même logique que JUMEAU (1 = 2 joueuses)
		        if (rid == RoleId.SOEUR) {
		            int pairs = Math.min(ROLE_MAX, n);
		            int maxPairs = (players != null) ? (players.size()/2) : pairs;
		            pairs = Math.min(pairs, Math.max(0, maxPairs));
		            n = pairs * 2; // 2 joueuses par "1 Sœur"
		        }

		        if (n > 0) counts.put(rid, n);
		    }
		}
		
		 try {
			    plugin.getGameManager().forceVoteLG9Lock(dechuActif);
			} catch (Throwable ignored) {}

		// Fallback si la compo est vide
		if (counts.isEmpty()) {
			counts.put(RoleId.SIMPLE_VILLAGEOIS, players.size());
			Bukkit.getLogger().info("[RoleService@" + rsHash + "] Empty composition -> all SIMPLE_VILLAGEOIS");
		}

		// 3) Construire la pool exacte selon counts
		java.util.List<RoleId> pool = new java.util.ArrayList<>();
		for (java.util.Map.Entry<RoleId, Integer> e : counts.entrySet()) {
			for (int i = 0; i < e.getValue(); i++)
				pool.add(e.getKey());
		}

		// Ajuster à la taille des joueurs (trim / compléter en villageois)
		if (pool.size() > players.size()) {
			pool = new java.util.ArrayList<>(pool.subList(0, players.size()));
		} else if (pool.size() < players.size()) {
			for (int i = pool.size(); i < players.size(); i++)
				pool.add(RoleId.SIMPLE_VILLAGEOIS);
		}

		// 4) Shuffle joueurs & pool
		java.util.List<Player> plist = new java.util.ArrayList<>(players);
		java.util.Collections.shuffle(plist, new java.util.Random());
		java.util.Collections.shuffle(pool, new java.util.Random());

		// 5) Distribution 1:1 (UNE SEULE FOIS)
		for (int i = 0; i < plist.size(); i++) {
			Player p = plist.get(i);
			RoleId role = pool.get(i);

			RoleState st = new RoleState(p.getUniqueId(), role, alignOf(role));
			
			// --- Amnésique v1 : se croit SV tant qu'il n'est pas réveillé ---
			// NE PAS toucher à st.align (final)
			if (role == RoleId.LOUP_GAROU_AMNESIQUE_V1) {
			    st.amnV1Awake = false;
			    st.amnV1TriggerAtMs = 0L;
			    st.amnV1NextRevealAtMs = 0L; // sera armé plus tard
			    st.amnV1KnownWolves.clear();

			    // Apparence SV (uniquement pour l’affichage)
			    try { st.aura = ROLE_TO_AURA.get(RoleId.SIMPLE_VILLAGEOIS); } catch (Throwable ignored) {}
			    try { st.constellation = ROLE_TO_CONSTELLATION.get(RoleId.SIMPLE_VILLAGEOIS); } catch (Throwable ignored) {}

			    // Message d’annonce : Simple Villageois
			    p.sendMessage(ChatColor.GREEN + "Ton rôle: " + ChatColor.AQUA + displayName(RoleId.SIMPLE_VILLAGEOIS));
			} else {
			    p.sendMessage(ChatColor.GREEN + "Ton rôle: " + ChatColor.AQUA + displayName(role));
			}


			// Hooks spécifiques
			if (role == RoleId.MARCHANDE_DE_FRUITS)
				initMarchande(st);
			if (role == RoleId.ANCIEN)
				initAncienBuff(p, st);
			if (role == RoleId.LOUP_GAROU_PEUREUX) {
				try {
					((WolfHandler) wolfHandler).armPeureux(p);
				} catch (Throwable ignored) {
				}
			}

			states.put(p.getUniqueId(), st);

			giveRoleKit(p, role);

			Bukkit.getLogger().info(
					"[RoleService@" + rsHash + "] " + p.getName() + " -> " + role + " (" + displayName(role) + ")");
		}

		// 6) Post-traitement JUMEAUX : lier par paires
		java.util.List<RoleState> twinsList = new java.util.ArrayList<>();
		for (RoleState st : states.values())
			if (st.roleId == RoleId.JUMEAU)
				twinsList.add(st);
		java.util.Collections.shuffle(twinsList, new java.util.Random());

		for (int i = 0; i + 1 < twinsList.size(); i += 2) {
			RoleState a = twinsList.get(i);
			RoleState b = twinsList.get(i + 1);

			a.twinPartner = b.owner;
			b.twinPartner = a.owner;

			if (new java.util.Random().nextBoolean())
				a.twinIsRoleSide = true;
			else
				b.twinIsRoleSide = true;
		}
		
		// 6bis) Post-traitement SŒUR : lier par paires (aucun message ici)
		java.util.List<RoleState> sisters = new java.util.ArrayList<>();
		for (RoleState st : states.values()) if (st.roleId == RoleId.SOEUR) sisters.add(st);
		java.util.Collections.shuffle(sisters, new java.util.Random());
		for (int i = 0; i + 1 < sisters.size(); i += 2) {
		    RoleState a = sisters.get(i);
		    RoleState b = sisters.get(i+1);
		    a.soeurPartner = b.owner;
		    b.soeurPartner = a.owner;
		    a.soeurNameRevealed = false;
		    b.soeurNameRevealed = false;
		


		}
	

		Bukkit.getLogger().info("[RoleService@" + rsHash + "] Assign DONE. states=" + states.size());
	}

	private void giveRoleKit(Player p, RoleId r) {
		switch (r) {
		case CONTEUSE:
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOKSHELF, 2));
				p.updateInventory();
			});
			break;
		case PRETRESSE:
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.OBSIDIAN, 4));
				p.updateInventory();
			});
			break;
		case VOYANTE:
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.OBSIDIAN, 4));
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOKSHELF, 4));
				p.updateInventory();
			});
			break;
		case BIBLIOTHECAIRE:
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				// 3 livres enchantés (niveaux 1 à 3), sans Punch/Flame/Fire/Knockback
				p.getInventory().addItem(createRandomEnchantedBookSafe());
				p.getInventory().addItem(createRandomEnchantedBookSafe());
				p.getInventory().addItem(createRandomEnchantedBookSafe());

				// 1 livre à plumes
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK_AND_QUILL, 1));
				p.updateInventory();
			});

			break;
		case ASSASSIN: {
			// Force I de jour = appliqué par NeutralHandler (section 2)
			// Livres Prot III, Sharp III, Unbreaking III
			org.bukkit.inventory.ItemStack prot3 = new org.bukkit.inventory.ItemStack(
					org.bukkit.Material.ENCHANTED_BOOK);
			org.bukkit.inventory.meta.EnchantmentStorageMeta m1 = (org.bukkit.inventory.meta.EnchantmentStorageMeta) prot3
					.getItemMeta();
			m1.addStoredEnchant(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, 3, true);
			prot3.setItemMeta(m1);

			org.bukkit.inventory.ItemStack sharp3 = new org.bukkit.inventory.ItemStack(
					org.bukkit.Material.ENCHANTED_BOOK);
			org.bukkit.inventory.meta.EnchantmentStorageMeta m2 = (org.bukkit.inventory.meta.EnchantmentStorageMeta) sharp3
					.getItemMeta();
			m2.addStoredEnchant(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 3, true);
			sharp3.setItemMeta(m2);

			org.bukkit.inventory.ItemStack unb3 = new org.bukkit.inventory.ItemStack(
					org.bukkit.Material.ENCHANTED_BOOK);
			org.bukkit.inventory.meta.EnchantmentStorageMeta m3 = (org.bukkit.inventory.meta.EnchantmentStorageMeta) unb3
					.getItemMeta();
			m3.addStoredEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 3, true);
			unb3.setItemMeta(m3);

			p.getInventory().addItem(prot3, sharp3, unb3);
			break;
		}
		case CUPIDON: {
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				// Livre Power II, Punch I
				org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(
						org.bukkit.Material.ENCHANTED_BOOK);
				org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book
						.getItemMeta();
				meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, 2, true); // Power II
				meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK, 1, true); // Punch I
				book.setItemMeta(meta);
				p.getInventory().addItem(book);

				// 64 flèches
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW, 64));
			});
			break;
		}
		case MINEUR: {
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				org.bukkit.inventory.ItemStack pick = new org.bukkit.inventory.ItemStack(
						org.bukkit.Material.DIAMOND_PICKAXE, 1);
				pick.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DIG_SPEED, 3); // Efficiency III
				p.getInventory().addItem(pick);
				p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.TNT, 2));
				p.updateInventory();
			});
			break;
		}
		case FOU_DU_BUS: {
			Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
				try {
					changeMaxHearts(p, +2);
				} catch (Throwable ignored) {
				}
				p.updateInventory();
			});
			break;
		}
		case PETITE_FILLE: {
			// 3 TNT
			try {
				org.bukkit.inventory.ItemStack tnt = new org.bukkit.inventory.ItemStack(org.bukkit.Material.TNT, 3);
				p.getInventory().addItem(tnt);
			} catch (Throwable ignored) {
			}
			break;
		}
		case FEU_FOLLET: {
			// Plume de TP (2 uses)
			try {
				org.bukkit.inventory.ItemStack feather = new org.bukkit.inventory.ItemStack(org.bukkit.Material.FEATHER,
						1);
				org.bukkit.inventory.meta.ItemMeta im = feather.getItemMeta();
				im.setDisplayName("§ePlume du Feu Follet");
				im.setLore(java.util.Arrays.asList("§7Téléportation aléatoire (≤50m)", "§7Utilisations: §62",
						"§7Cooldown: 10 minutes"));
				feather.setItemMeta(im);
				p.getInventory().addItem(feather);

				RoleService.RoleState s = get(p);
				if (s != null)
					s.ffFeatherUsesLeft = 2;
			} catch (Throwable ignored) {
			}
			break;
		}
		case LOUP_GAROU_PERFIDE: {
			// rien de spécial à donner en item (bonus au kill automatique)
			break;
		}
		case CUPIDON_V2: {
			// Livre Power II, Punch I
			org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(
					org.bukkit.Material.ENCHANTED_BOOK);
			org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book
					.getItemMeta();
			meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, 2, true); // Power II
			meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK, 1, true); // Punch I
			book.setItemMeta(meta);
			p.getInventory().addItem(book);

			// 64 flèches
			p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW, 64));
			break;
		}

		case INFECT_PERE_DES_LOUPS: {
		    // 2 potions de soin I (splash) – data value 16421 en 1.8
		    org.bukkit.inventory.ItemStack splashHeal =
		        new org.bukkit.inventory.ItemStack(org.bukkit.Material.POTION, 1, (short)16421);
		    p.getInventory().addItem(splashHeal.clone(), splashHeal.clone());
		    break;
		}
		
		case SORCIERE: {
		    Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
		        try {
		            // Potion de Soin I (non splash)
		            org.bukkit.potion.Potion heal = new org.bukkit.potion.Potion(org.bukkit.potion.PotionType.INSTANT_HEAL, 1);
		            heal.setSplash(true);
		            org.bukkit.inventory.ItemStack healItem = heal.toItemStack(1);
		            org.bukkit.inventory.meta.ItemMeta im1 = healItem.getItemMeta();
		            if (im1 != null) { im1.setDisplayName("§dPotion de Soin"); healItem.setItemMeta(im1); }

		            // Potion de Dégâts I (splash)
		            org.bukkit.potion.Potion harm = new org.bukkit.potion.Potion(org.bukkit.potion.PotionType.INSTANT_DAMAGE, 1);
		            harm.setSplash(true);
		            org.bukkit.inventory.ItemStack harmItem = harm.toItemStack(1);
		            org.bukkit.inventory.meta.ItemMeta im2 = harmItem.getItemMeta();
		            if (im2 != null) { im2.setDisplayName("§5Potion de Dégâts"); harmItem.setItemMeta(im2); }

		            p.getInventory().addItem(healItem, harmItem);
		            p.updateInventory();
		        } catch (Throwable ignored) {}
		    });
		    break;
		}
		
		case BIENFAITEUR: {
			 try {
			        org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_BOOK, 1);
			        org.bukkit.inventory.meta.EnchantmentStorageMeta meta =
			            (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
			        meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, 2, true);
			        book.setItemMeta(meta);

			        p.getInventory().addItem(book.clone(), book.clone());
			        p.updateInventory();
			    } catch (Throwable ignored) {}
		 break;
		}
		



		default:
			break;
		}
	}

	public String displayName(RoleId id) {
		switch (id) {
		case ANALYSTE:
			return "Analyste";
		case CONSTELLATIONNISTE:
			return "Constellationniste";
		case SIMPLE_VILLAGEOIS:
			return "Simple Villageois";
		case CONTEUSE:
			return "Conteuse";
		case DETECTIVE:
			return "Détective";
		case JUMEAU:
			return "Jumeau";
		case MONTREUR_DOURS:
			return "Montreur d'Ours";
		case ORACLE:
			return "Oracle";
		case PRETRESSE:
			return "Prêtresse";
		case RENARD:
			return "Renard";
		case VOYANTE:
			return "Voyante";
		case BIBLIOTHECAIRE:
			return "Bibliothécaire";
		case VIEUX_SAGE:
			return "Vieux Sage";
		case MARCHANDE_DE_FRUITS:
			return "Marchande de Fruits";
		case ANCIEN:
			return "Ancien";
		case ERMITE:
			return "Ermite";
		case MINEUR:
			return "Mineur";
		case SORCIERE:
			return "Sorcière";
		case DECHU:
			return "Le Déchus";
		case SOEUR:
			return "Soeur";
		case SERVANTE_DEVOUEE:
			return "Servante Dévouée";
		case BIENFAITEUR:
			return "Bienfaiteur";
		case ENCHANTERESSE:
			return "Enchanteresse";

		case PETITE_FILLE:
			return "Petite Fille";
		case LOUP_GAROU_PERFIDE:
			return "Loup-Garou Perfide";
		case FEU_FOLLET:
			return "Feu Follet";

		case LOUP_GAROU_CHANCEUX:
			return "Loup-Garou Chanceux";
		case LOUP_GAROU:
			return "Loup-Garou";
		case LOUP_GAROU_PEUREUX:
			return "Loup-Garou Peureux";
		case INFECT_PERE_DES_LOUPS:
			return "Infect Père des Loups";
		case LOUP_GAROU_AMNESIQUE:
			return "Loup-Garou Amnésique";
		case LOUP_GAROU_ALPHA:
			return "Loup-Garou Alpha";
		case LOUP_GAROU_AMNESIQUE_V1:
			return "Loup-Garou Amnésique V1";
		case LOUP_GAROU_FEUTRE:
			return "Loup-Garou Feutré";
		case LOUP_GAROU_TENEBREUX:
			return "Loup-Garou Ténébreux";

		case ASSASSIN:
			return "Assassin";
		case FOU_DU_BUS:
			return "Fou du Bus";
		case ARNACOEUR:
			return "Arnacoeur";

		case CUPIDON:
			return "Cupidon";
		case CUPIDON_V2:
			return "Cupidon v2";
		case VOLEUR: 
			return "Voleur";

		}
		return id.name();
	}

	// === Routage /lg vers handlers ===
	public boolean handleLgSubCommand(String sub, Player sender, String[] args) {
		RoleState st = get(sender);
		
	    // ⛔ Un joueur mort (ou en sursis) ne peut pas utiliser /lg
	    DeathManager dm = plugin.getDeathManager();
	    if (dm != null && !dm.isAlive(sender.getUniqueId())) {
	        sender.sendMessage(org.bukkit.ChatColor.RED + "Tu es mort : tu ne peux plus utiliser /lg.");
	        return true;
	    }
		
		// === IPDL : /lg infect (déclenché par le [CLIQUER]) ===
		// RoleService.handleLgSubCommand(...)
	    // === IPDL : /lg infect (déclenché par le [CLIQUER]) ===
	    if (sub.equalsIgnoreCase("infect")) {
	        RoleService.RoleState s = get(sender);
	        if (s == null || s.roleId != RoleService.RoleId.INFECT_PERE_DES_LOUPS) {
	            sender.sendMessage(ChatColor.RED + "Commande réservée à l’Infect Père des Loups.");
	            return true;
	        }
	        if (s.ipdlInfectUsed) {
	            sender.sendMessage(ChatColor.RED + "Tu as déjà utilisé ton infection.");
	            return true;
	        }

	        // DeathManager : on est DANS RoleService -> utiliser 'plugin'
	        java.util.UUID victimId = dm.consumeInfectOffer(s.owner); // récupère l’offre (fenêtre 5s)
	        if (victimId == null) {
	            sender.sendMessage(ChatColor.RED + "Aucune infection disponible (trop tard ou mauvaise cible).");
	            return true;
	        }

	        // Ressuscite la victime
	        boolean ok = dm.reviveInfected(victimId);
	        if (!ok) {
	            sender.sendMessage(ChatColor.RED + "Échec de la résurrection (plus de sursis ?).");
	            return true;
	        }

	        // Marque l’usage + aligne la victime côté Loups (si pas déjà fait dans onInfectedRevive)
	        s.ipdlInfectUsed = true;

	        RoleService.RoleState vs = states.get(victimId);
	        if (vs != null) {
	            vs.infectedAsWolf = true;    // il est désormais loup côté logique
	            vs.infectedNoWolfWin = false;
	            // NE PAS toucher à vs.align (final). Le camp effectif sera pris en compte par
	            // baseAlignForCampCheck(...) et effectiveWinAlign(...) grâce à infectedAsWolf.
	        }


	        sender.sendMessage(ChatColor.DARK_RED + "[Infect] " + ChatColor.GRAY + "Infection réussie !");
	        return true;
	    }

		
		if ("don".equalsIgnoreCase(sub)) {
			// 1) Verrou global (EP2+5min OU couple annoncé)
			if (!this.canUseDonNow()) {
				sender.sendMessage("§7Le §f/don §7est verrouillé tant que le Couple n’est pas annoncé "
						+ "(ou jusqu’à §fÉpisode 2 + 5 min§7).");
				return true;
			}
			// 2) Laisse HybridHandler exécuter la commande
			return hybridHandler.handleSubCommand(sub, sender, args);
		}

		if (sub.equalsIgnoreCase("vote")) {
			return game.handleVoteCommand(sender, java.util.Arrays.copyOfRange(args, 0, args.length));
		}
		
		// Exemple typique dans ton onCommand / routeur central :
		if ("role".equalsIgnoreCase(sub)) {
		    return cmdRole(sender);
		}


		// APRÈS
		if (st != null) {
		    Align a = effectiveWinAlign(sender.getUniqueId()); // <-- clé : reflète “Village” tant que non réveillé
		    AlignHandler h = (a == Align.VILLAGE) ? villageHandler
		            : (a == Align.LOUP) ? wolfHandler : (a == Align.HYBRIDE) ? hybridHandler : neutralHandler;
		    if (h.handleSubCommand(sub, sender, args)) return true;
		}



		
		// Fallback “défensif”
		if (villageHandler.handleSubCommand(sub, sender, args))
			return true;
		if (wolfHandler.handleSubCommand(sub, sender, args))
			return true;
		if (hybridHandler.handleSubCommand(sub, sender, args))
			return true;
		if (neutralHandler.handleSubCommand(sub, sender, args))
			return true;
		return false;
	}
	
	private org.bukkit.inventory.ItemStack createRandomEnchantedBookSafe() {
		org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_BOOK, 1);
		org.bukkit.enchantments.Enchantment[] all = org.bukkit.enchantments.Enchantment.values();

		// exclusions : Punch, Flame, Fire (-> Fire Aspect & Fire Protection), Knockback
		java.util.Set<org.bukkit.enchantments.Enchantment> banned = new java.util.HashSet<>();
		banned.add(org.bukkit.enchantments.Enchantment.ARROW_KNOCKBACK); // Punch
		banned.add(org.bukkit.enchantments.Enchantment.KNOCKBACK); // Knockback
		banned.add(org.bukkit.enchantments.Enchantment.ARROW_FIRE); // Flame
		banned.add(org.bukkit.enchantments.Enchantment.FIRE_ASPECT); // Fire (mêlée)
		banned.add(org.bukkit.enchantments.Enchantment.PROTECTION_FIRE); // Fire (armure)

		// pool autorisé
		java.util.List<org.bukkit.enchantments.Enchantment> pool = new java.util.ArrayList<>();
		for (org.bukkit.enchantments.Enchantment e : all) {
			if (e == null)
				continue;
			if (banned.contains(e))
				continue;
			pool.add(e);
		}
		if (pool.isEmpty())
			return book;

		java.util.Collections.shuffle(pool);
		org.bukkit.enchantments.Enchantment chosen = pool.get(0);

		int max = Math.max(1, chosen.getMaxLevel());
		int lvl = 1 + new java.util.Random().nextInt(Math.min(3, max)); // niveau 1..3 (capé au max de l’enchant)

		org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book
				.getItemMeta();
		meta.addStoredEnchant(chosen, lvl, true);
		book.setItemMeta(meta);
		return book;
	}

	// === Délégations événements ===
	public void onPlayerKill(Player killer, Player victim) {
		// 1) marquer le kill pour Bibliothécaire
		recordKill(killer);
		logRacontable(killer, "kill_player");
		wolfHandler.onPlayerKill(killer, victim);
		villageHandler.onPlayerKill(killer, victim);
		neutralHandler.onPlayerKill(killer, victim);
		hybridHandler.onPlayerKill(killer, victim);
		RoleState ks = get(killer);
		if (ks != null)
			ks.kills++;
		if (ks != null && ks.roleId == RoleId.LOUP_GAROU_CHANCEUX) {
			// Vitesse I & Absorption 2♥ pendant 60s
			int dur = 60 * 20;
			try {
				killer.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, dur,
						0, true, true));
				killer.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION,
						dur, 0, true, true)); // amp 0 = 2♥
			} catch (Throwable ignored) {
			}
		}
		
		if (ks != null && (ks.roleId == RoleId.LOUP_GAROU_PEUREUX)) {
			int dur = 60 * 20;
			try {
				killer.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, dur,
						0, true, true));
				killer.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION,
						dur, 0, true, true)); // +2♥
			} catch (Throwable ignored) {
			}
		}
		
	}

	public void onPlayerDeath(Player dead) {
		// ... ton code actuel (Prêtresse, Jumeau, transferts, etc.) ...
		// --- Déclenchement "chagrin d'amour" ---
		RoleService.RoleState s = get(dead);
		if (s != null && s.lover != null) {
			final java.util.UUID loverId = s.lover;
			final Player lover = Bukkit.getPlayer(loverId);

			// Anti-boucle : si cette mort vient déjà d'un chagrin d'amour, on ne relance
			// pas
			if (s.loverDoomed) {
				s.loverDoomed = false; // reset pour propreté
			} else if (lover != null && lover.isOnline() && lover.getGameMode() == GameMode.SURVIVAL
					&& !lover.isDead()) {
				// Marque le partenaire comme "condamné par chagrin" pour éviter la boucle
				RoleService.RoleState ls = get(lover);
				if (ls != null) {
					ls.loverDoomed = true;
				}
				// Demande au DeathManager d'annoncer "par chagrin d'amour" et de finaliser
				// instantanément
				try {
					plugin.getDeathManager().markLoverInstantFinalize(loverId);
				} catch (Throwable ignored) {
				}

				// Tue immédiatement le partenaire (et laisse DeathManager gérer l'instant
				// finalize + broadcast violet)
				Bukkit.getScheduler().runTask(plugin, () -> {
					if (!lover.isDead() && lover.getGameMode() == GameMode.SURVIVAL) {
						try {
							lover.setHealth(0.0D);
						} catch (Throwable t) {
							// fallback si jamais setHealth est protégé
							lover.damage(1000.0D, dead);
						}
					}
				});
			}
		}

		// Appel de sécurité : on vérifie la win-condition juste après la finalisation.
		// On décale d’1 tick pour laisser au DeathManager le temps de basculer en
		// SPECTATOR,
		// déposer les items, etc.
		// ✅ Vérification de victoire 1 tick après

		if (s != null && s.lover != null) {
			// ordonne au DeathManager de finaliser instantanément l’amoureux restant
			try {
				getPlugin().getDeathManager().markLoverInstantFinalize(s.lover);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
				try {
					if (game != null) {
						game.checkWin();
						org.bukkit.Bukkit.getLogger().info("[WinCheck] finalizeDeath -> checkWin() called.");
					} else {
						org.bukkit.Bukkit.getLogger().warning("[WinCheck] finalizeDeath -> game==null (pas d'appel).");
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			
		}, 1L);
	}

	public void onEpisodeStart(int episodeNumber) {
	    // 1) Relais vers les handlers
	    try { getVillageHandler().onEpisodeStart(episodeNumber); } catch (Throwable t) { t.printStackTrace(); }
	    try { getWolfHandler().onEpisodeStart(episodeNumber);     } catch (Throwable t) { t.printStackTrace(); }
	    try { getNeutralHandler().onEpisodeStart(episodeNumber);  } catch (Throwable t) { t.printStackTrace(); }
	    try { getHybridHandler().onEpisodeStart(episodeNumber);   } catch (Throwable t) { t.printStackTrace(); }

	    // 2) Révélation alliés loups à l’épisode 3
	    if (episodeNumber == 3) {
	        try {
	            wolfHandler.announceWolfListNow(); // pose wolfListAnnounced = true + envoie la liste
	        } catch (Throwable ignored) {}
	    }

	    // 3) Vote LG9 à partir de l’épisode 3
	    try {
	        if (game.getVoteMode() == 9 && episodeNumber >= 3) {
	            game.openVoteWindow(episodeNumber);
	        }
	    } catch (Throwable t) { t.printStackTrace(); }

	    // 4) Amnésique visible à mi-EP4
	    if (episodeNumber == 4) {
	        int half = (game != null ? game.getEpisodeLenSec() / 2 : 10 * 60);
	        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
	            try { getWolfHandler().revealAmnesiquesToWolves(); } catch (Throwable ignored) {}
	        }, half * 20L);
	    }
	}


	public void tickPerSecond(int elapsedSec) {
		wolfHandler.tickPerSecond(elapsedSec);
		villageHandler.tickPerSecond(elapsedSec);
		neutralHandler.tickPerSecond(elapsedSec);
		hybridHandler.tickPerSecond(elapsedSec);
	}

	// === Listener spécifique : Golden Apple des jumeaux (malus) ===
	@org.bukkit.event.EventHandler
	public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent e) {
		org.bukkit.inventory.ItemStack it = e.getItem();
		if (it == null)
			return;

		Player p = e.getPlayer();
		RoleService.RoleState s = get(p);
		if (s == null)
			return;

		// --- Jumeaux : malus si éloignés et gapple
		if (s.roleId == RoleId.JUMEAU && it.getType() == org.bukkit.Material.GOLDEN_APPLE) {
			if (s.twinPartner != null) {
				Player mate = Bukkit.getPlayer(s.twinPartner);
				if (mate != null && mate.isOnline() && p.getWorld().equals(mate.getWorld())
						&& p.getLocation().distance(mate.getLocation()) > 50.0) {
					((VillageHandler) villageHandler).handleTwinGoldenApple(p);
				}
			}
		}

		// --- Marchande de Fruits : passifs (toujours évalués, indépendamment du rôle
		// Jumeau)
		if (s.roleId == RoleId.MARCHANDE_DE_FRUITS) {
			// 25% cœur bonus sur golden apple
			if (it.getType() == org.bukkit.Material.GOLDEN_APPLE && new java.util.Random().nextInt(4) == 0) {
				Bukkit.getScheduler().runTask(getPlugin(), () -> {
					double nh = Math.min(p.getMaxHealth(), p.getHealth() + 2.0D);
					p.setHealth(nh);
					p.sendMessage(
							ChatColor.GOLD + "[Marchande] " + ChatColor.GREEN + "La pomme t’a rendu un cœur bonus !");
				});
			}
			// +1 gigot sur toute nourriture
			Bukkit.getScheduler().runTask(getPlugin(), () -> {
				p.setFoodLevel(Math.min(20, p.getFoodLevel() + 2));
			});
		}
	}

	// === Utils communs ===
	private static final double HEART = 2.0D; // 1 cœur = 2 HP

	public boolean changeMaxHearts(Player p, int heartsDelta) {
		double max = p.getMaxHealth();
		double newMax = max + (heartsDelta * HEART);
		if (newMax < 2.0D)
			return false;
		if (newMax > 40.0D)
			newMax = 40.0D;
		p.setMaxHealth(newMax);
		if (p.getHealth() > newMax)
			p.setHealth(newMax);
		return true;
	}

	public boolean isVillage(Player p) {
		RoleState st = get(p);
		return st != null && st.align == Align.VILLAGE;
	}

	/** Récap Conteuse à la fin d’un épisode (appelé depuis GameManager). */
	public void dispatchConteuseSummary(int completedEpisode) {
		if (completedEpisode < 3)
			return;
		Map<UUID, Set<String>> bucket = racLogByEpisode.getOrDefault(completedEpisode, Collections.emptyMap());
		if (bucket.isEmpty()) {
			sendToAllConteuses(
					ChatColor.GRAY + "[Conteuse] Aucun joueur n’a effectué d’Action Racontable durant l’épisode "
							+ completedEpisode + ".");
			return;
		}
		List<String> lines = new ArrayList<>();
		for (UUID u : bucket.keySet()) {
			Player pl = Bukkit.getPlayer(u);
			String name = (pl != null) ? pl.getName() : ("Joueur-" + u.toString().substring(0, 6));
			lines.add(" - " + name);
		}
		Collections.sort(lines, String.CASE_INSENSITIVE_ORDER);
		sendToAllConteuses(ChatColor.GOLD + "[Conteuse] " + ChatColor.YELLOW + "Récap de l’épisode " + completedEpisode
				+ " :\n" + ChatColor.AQUA + String.join("\n", lines));
	}

	private void sendToAllConteuses(String msg) {
		for (RoleState st : states.values()) {
			if (st.roleId == RoleId.CONTEUSE) {
				Player p = Bukkit.getPlayer(st.owner);
				if (p != null && p.isOnline())
					p.sendMessage(msg);
			}
		}
	}

	public boolean isLastNightFlag() {
		return lastNightFlag;
	}

	public void setLastNightFlag(boolean flag) {
		this.lastNightFlag = flag;
	}

	@org.bukkit.event.EventHandler
	public void onEdit(org.bukkit.event.player.PlayerEditBookEvent e) {
		// On ne filtre que le "livre mémoire" (lore contenant "BIBLIO-")
		org.bukkit.inventory.meta.BookMeta meta = e.getNewBookMeta();
		if (meta == null || meta.getLore() == null)
			return;
		boolean isMemo = false;
		for (String l : meta.getLore())
			if (l != null && l.contains("BIBLIO-")) {
				isMemo = true;
				break;
			}
		if (!isMemo)
			return;

		// Est-ce bien un prêt en cours où l'éditeur est l'emprunteur ?
		Player editor = e.getPlayer();
		boolean allowed = false;
		for (RoleState s : states.values()) {
			if (s.roleId != RoleId.BIBLIOTHECAIRE)
				continue;
			if (s.biblioBorrower != null && s.biblioBorrower.equals(editor.getUniqueId())) {
				allowed = true;
				break;
			}
		}
		if (!allowed) {
			editor.sendMessage(ChatColor.RED + "Tu ne peux pas modifier ce livre.");
			e.setCancelled(true);
			return;
		}

		// Limite à 1 page
		java.util.List<String> pages = meta.getPages();
		if (pages != null && pages.size() > 1) {
			java.util.List<String> one = new java.util.ArrayList<>();
			one.add(pages.get(0));
			meta.setPages(one);
			e.setNewBookMeta(meta);
			editor.sendMessage(ChatColor.YELLOW + "Une seule page est autorisée.");
		}
	}

	public void recordKill(Player killer) {
		if (killer == null)
			return;
		java.util.UUID id = killer.getUniqueId();
		killsByPlayer.put(id, killsByPlayer.getOrDefault(id, 0) + 1);
	}

	public boolean hasKilled(java.util.UUID playerId) {
		return killsByPlayer.getOrDefault(playerId, 0) > 0;
	}

	public boolean hadRacontableInLastMinutes(java.util.UUID playerId, int minutes) {
		Long ms = lastRacMs.get(playerId);
		if (ms == null)
			return false;
		return (System.currentTimeMillis() - ms) <= minutes * 60_000L;
	}

	// garde ta logRacontable(...) mais rajoute la MAJ du timestamp :
	public void logRacontable(Player actor, String actionTag) {
		int ep = game.getEpisodeNumber();
		getEpisodeBucket(ep).computeIfAbsent(actor.getUniqueId(), k -> new java.util.HashSet<>()).add(actionTag);
		lastRacMs.put(actor.getUniqueId(), System.currentTimeMillis());
	}

	public RoleId getRole(UUID id) {
		RoleState s = states.get(id);
		return (s == null) ? null : s.roleId;
	}

	public RoleId getRole(Player p) { // garde si tu veux
		return (p == null) ? null : getRole(p.getUniqueId());
	}

	public String displayNameOrQ(RoleId id) {
		return (id == null) ? "?" : displayName(id);
	}

	// Compte les golden apples dans l’inventaire du joueur
	public int countGapples(org.bukkit.entity.Player p) {
		int n = 0;
		for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
			if (it == null)
				continue;
			if (it.getType() == org.bukkit.Material.GOLDEN_APPLE)
				n += it.getAmount();
		}
		return n;
	}

	// Renvoie l’aura de base (celle “par défaut” du rôle, pas l’aura courante qui
	// peut évoluer)
	public Aura baseAuraOf(org.bukkit.entity.Player p) {
		RoleState s = get(p);
		if (s == null)
			return Aura.NEUTRE;
		Aura a = ROLE_TO_AURA.get(s.roleId);
		return (a != null) ? a : Aura.NEUTRE;
	}

	// Formatage sympa de l’aura
	public String auraPretty(Aura a) {
		switch (a) {
		case LUMINEUSE:
			return org.bukkit.ChatColor.GOLD + "Lumineuse";
		case NEUTRE:
			return org.bukkit.ChatColor.YELLOW + "Neutre";
		case OBSCURE:
			return org.bukkit.ChatColor.DARK_PURPLE + "Obscure";
		}
		return a.name();
	}

	// Nombre de kills d’un joueur (on redirige vers GameManager si tu y stockes les
	// kills)
	public int getKills(org.bukkit.entity.Player p) {
		try {
			return game.getKillsFor(p); // <-- ajoute dans GameManager: public int getKillsFor(Player p) { return
										// playerKills.getOrDefault(p.getUniqueId(), 0); }
		} catch (Throwable t) {
			return 0;
		}
	}

	// RoleService (helper global)
	
	// RoleService.java
	// Déjà présent :
	// RoleService.java

	// === 1) La vérité gameplay : est-ce que CE joueur compte comme loup ? ===
	public boolean isWolf(RoleState s) {
	    if (s == null) return false;

	    // ❗️Solitaire : ne compte plus dans la meute quoi qu'il arrive
	    if (s.solitaire) return false;

	    // Infecté => loup (sauf solitaire déjà géré au-dessus)
	    if (s.infectedAsWolf) return true;

	    // Amnésique v1 => loup SEULEMENT après réveil
	    if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1) {
	        return s.amnV1Awake;
	    }

	    // Tous les autres loups (dont Amnésique "classique") ont align=LOUP
	    return s.align == Align.LOUP;
	}

	public boolean isWolf(org.bukkit.entity.Player p) {
	    return isWolf(get(p));
	}

	// === 2) Classement par RoleId (pour menus/UI, PAS pour gameplay) ===
	public boolean isWolf(RoleService.RoleId rid) {
	    if (rid == null) return false;
	    switch (rid) {
	        case LOUP_GAROU:
	        case LOUP_GAROU_PEUREUX:
	        case LOUP_GAROU_CHANCEUX:
	        case LOUP_GAROU_PERFIDE:
	        case INFECT_PERE_DES_LOUPS:
	        case LOUP_GAROU_ALPHA:
	        case LOUP_GAROU_AMNESIQUE:     // l’amnésique "classique" est bien un loup naturel
	            return true;

	        case LOUP_GAROU_AMNESIQUE_V1:  // v1 : dépend du réveil -> géré par isWolf(RoleState)
	            return false;

	        default:
	            return false;
	    }
	}





	// Renvoie l'état (ou null)
	public RoleState get(UUID id) {
		return states.get(id);
	}

	public RoleState get(Player p) {
		return states.get(p.getUniqueId());
	}

	// ===== Couple / Cupidon =====

	// Canonique (UUID)
	public boolean setCouple(java.util.UUID a, java.util.UUID b) {
		if (a == null || b == null || a.equals(b))
			return false;
		RoleState sa = states.get(a);
		RoleState sb = states.get(b);
		if (sa == null || sb == null)
			return false;

		sa.lover = b;
		sa.loverMet = false;
		sa.loverDoomed = false;
		sb.lover = a;
		sb.loverMet = false;
		sb.loverDoomed = false;

		markCoupleAnnouncedNow();

		return true;
	}

	// Helper (Player)
	public boolean setCouple(org.bukkit.entity.Player a, org.bukkit.entity.Player b) {
		if (a == null || b == null)
			return false;
		return setCouple(a.getUniqueId(), b.getUniqueId());
	}

	// Lover en ligne
	public org.bukkit.entity.Player getLoverOnline(org.bukkit.entity.Player p) {
		RoleState s = get(p);
		if (s == null || s.lover == null)
			return null;
		org.bukkit.entity.Player q = org.bukkit.Bukkit.getPlayer(s.lover);
		return (q != null && q.isOnline()) ? q : null;
	}

	/** Supprime le lien de couple pour les deux joueurs. */
	public void clearCouple(org.bukkit.entity.Player a, org.bukkit.entity.Player b) {
		if (a != null) {
			RoleState sa = get(a);
			if (sa != null) {
				sa.lover = null;
				sa.loverMet = false;
			}
		}
		if (b != null) {
			RoleState sb = get(b);
			if (sb != null) {
				sb.lover = null;
				sb.loverMet = false;
			}
		}
	}

	public Align effectiveAlign(RoleState s) {
		if (s == null)
			return Align.VILLAGE;
		if (s.align != Align.HYBRIDE)
			return s.align;
		return (s.hybridChosen != null ? s.hybridChosen : null); // null = pas encore choisi
	}

	// RoleService.java

	/** Un rôle “solo” ? Ajoute ici tes futurs rôles Solo si besoin. */
	public boolean isSolo(RoleId rid) {
		return rid == RoleId.ASSASSIN || rid == RoleId.FOU_DU_BUS;

	}

	/** Est-ce Cupidon ? (utile pour compter la victoire “couple + cupidon”) */
	public boolean isCupidon(java.util.UUID id) {
		RoleState s = states.get(id);
		return s != null && s.roleId == RoleId.CUPIDON;
	}

	// ===== Couple (helpers globaux pour GameManager) =====
	public java.util.UUID getLoverA() {
		for (RoleState s : states.values()) {
			if (s.lover != null) {
				RoleState other = states.get(s.lover);
				if (other != null && s.owner.equals(other.lover)) {
					return s.owner; // premier membre d’un couple réciproque
				}
			}
		}
		return null;
	}

	public java.util.UUID getLoverB() {
		java.util.UUID a = getLoverA();
		if (a == null)
			return null;
		RoleState s = states.get(a);
		return (s != null) ? s.lover : null;
	}

	// dans LGUHC (ou la classe qui gère les menus)
	private final Map<String, Integer> roleCounts = new HashMap<>();
	public static final int ROLE_MAX = 10;

	/** Initialise les 3 “fruits” de la Marchande de Fruits. */
	private void initMarchande(RoleState st) {
		try {
			// Si tu as déjà Fruit et FruitInfo (enums) dans ton projet
			java.util.List<FruitInfo> infos = new java.util.ArrayList<>();
			infos.add(FruitInfo.GAPPLES);
			infos.add(FruitInfo.AURA_BASE);
			infos.add(FruitInfo.KILLS);
			java.util.Collections.shuffle(infos);

			st.fruitMap.put(Fruit.POMME, infos.get(0));
			st.fruitMap.put(Fruit.POIRE, infos.get(1));
			st.fruitMap.put(Fruit.PECHE, infos.get(2));
		} catch (Throwable t) {
			// Si tes champs/enums diffèrent, évite le crash et log
			Bukkit.getLogger().warning("[RoleService] initMarchande: champs manquants ? " + t.getMessage());
		}
	}

	/** Donne la résistance de l’Ancien et met à jour ses flags d’état. */
	private void initAncienBuff(Player p, RoleState st) {
		try {
			st.ancienResistanceActive = true;
			st.ancienResUsed = false;
			st.ancienLostResistance = false;

			if (p != null && p.isOnline()) {
				p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, // durée
																											// “infinie”
						0, // niveau I
						true, // ambient
						true // particles
				));
			}
		} catch (Throwable t) {
			Bukkit.getLogger().warning("[RoleService] initAncienBuff: " + t.getMessage());
		}
	}

	public WolfHandler getWolfHandler() {
		return wolfHandler;
	}

	// (facultatif mais utile)
	public NeutralHandler getNeutralHandler() {
		return neutralHandler;
	}

	public VillageHandler getVillageHandler() {
		return villageHandler;
	}

	public HybridHandler getHybridHandler() {
		return hybridHandler;
	}

	// ✅ nouveau
	private final VillageHandler villageHandler;
	private final WolfHandler wolfHandler;
	private final NeutralHandler neutralHandler;
	private final HybridHandler hybridHandler;

	public LGUHC getMain() {
		return main;
	}

	// === Helpers UI locaux pour RoleService ===

	private static String bearingArrow(org.bukkit.entity.Player from, org.bukkit.Location to) {
		org.bukkit.Location a = from.getLocation();
		if (to == null || a == null || to.getWorld() == null || a.getWorld() == null)
			return "·";
		if (!a.getWorld().equals(to.getWorld()))
			return "·";

		double dx = to.getX() - a.getX();
		double dz = to.getZ() - a.getZ();

		// Yaw Minecraft: +Z = 0°, sens horaire; atan2(-dx, dz)
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double dyaw = ((targetYaw - a.getYaw() + 540.0) % 360.0) - 180.0; // [-180..180]

		if (dyaw > 157.5)
			return "↓";
		if (dyaw > 112.5)
			return "↙";
		if (dyaw > 67.5)
			return "←";
		if (dyaw > 22.5)
			return "↖";
		if (dyaw > -22.5)
			return "↑";
		if (dyaw > -67.5)
			return "↗";
		if (dyaw > -112.5)
			return "→";
		if (dyaw > -157.5)
			return "↘";
		return "↓";
	}

	private static void sendActionBar(org.bukkit.entity.Player player, String message) {
		if (player == null || message == null)
			return;
		try {
			// Spigot 1.8 R3: PacketPlayOutChat (byte 2 = action bar)
			Class<?> IChatBaseComponent = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
			Class<?> ChatSerializer = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent$ChatSerializer");
			Object comp = ChatSerializer.getMethod("a", String.class).invoke(null,
					"{\"text\":\"" + message.replace("\"", "\\\"") + "\"}");
			Class<?> PacketPlayOutChat = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");
			Object packet = PacketPlayOutChat.getConstructor(IChatBaseComponent, byte.class).newInstance(comp,
					(byte) 2);
			Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Object connection = handle.getClass().getField("playerConnection").get(handle);
			Class<?> Packet = Class.forName("net.minecraft.server.v1_8_R3.Packet");
			connection.getClass().getMethod("sendPacket", Packet).invoke(connection, packet);
		} catch (Throwable t) {
			// Fallback ultra-sécurisé
			try {
				player.sendMessage(message);
			} catch (Throwable ignored) {
			}
		}
	}

	// Petit wrapper pratique (optionnel)
	private static void sendDirectionActionBar(org.bukkit.entity.Player from, org.bukkit.entity.Player to) {
		if (from == null || to == null || !to.isOnline())
			return;
		String arrow = bearingArrow(from, to.getLocation());
		int dist = -1;
		try {
			if (from.getWorld().equals(to.getWorld()))
				dist = (int) Math.round(from.getLocation().distance(to.getLocation()));
		} catch (Throwable ignored) {
		}
		String suffix = (dist >= 0 ? (" §f" + dist + "m ") : " ");
		sendActionBar(from, "§6➤ §e" + arrow + suffix + "§7→ §e" + to.getName());
	}

	public void announceWolvesAllies() {
	    java.util.List<org.bukkit.entity.Player> wolves = new java.util.ArrayList<>();
	    for (RoleState st : states.values()) {
	        if (isWolf(st)) { // ✅ était: if (!isWolf(st))
	            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(st.owner);
	            if (p != null && p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
	                wolves.add(p);
	            }
	        }
	    }

	    for (org.bukkit.entity.Player wolf : wolves) {
	        java.util.List<String> allies = new java.util.ArrayList<>();
	        for (org.bukkit.entity.Player other : wolves) {
	            if (!other.equals(wolf)) allies.add(other.getName());
	        }
	    }
	}


	/** Retourne {A,B} si un couple vivant existe, sinon null. */
	public java.util.UUID[] getAliveLovers() {
		java.util.UUID A = getLoverA(), B = getLoverB();
		if (A == null || B == null)
			return null;
		org.bukkit.entity.Player pa = org.bukkit.Bukkit.getPlayer(A);
		org.bukkit.entity.Player pb = org.bukkit.Bukkit.getPlayer(B);
		if (pa == null || pb == null)
			return null;
		if (!pa.isOnline() || !pb.isOnline())
			return null;
		if (pa.getGameMode() != org.bukkit.GameMode.SURVIVAL || pb.getGameMode() != org.bukkit.GameMode.SURVIVAL)
			return null;
		return new java.util.UUID[] { A, B };
	}

	public boolean bothLoversAreVillage() {
		java.util.UUID[] pair = getAliveLovers();
		if (pair == null)
			return false;
		RoleState sa = states.get(pair[0]);
		RoleState sb = states.get(pair[1]);
		return sa != null && sb != null && sa.align == Align.VILLAGE && sb.align == Align.VILLAGE;
	}

	/**
	 * Cupidon v2 suit le couple si vivant ET si le couple choisit/est côté COUPLE.
	 */
	public boolean doesCupidV2SideWithCouple(RoleService.RoleState s) {
		if (s == null || s.roleId != RoleService.RoleId.CUPIDON_V2)
			return false;
		if (!s.cupid2Maker || !s.cupid2CoupleAlive)
			return false; // flags que tu poses quand il crée le couple et que les 2 sont en vie
		return getCurrentCoupleSide() == CoupleSide.COUPLE;
	}

	// Camp de base à utiliser pour le test “tout le monde même camp”

	// RoleService.java
	public void handleCupidV2CoupleDeath(java.util.UUID deadId) {
		if (deadId == null)
			return;

		// si la victime était l’un des amoureux -> le couple n’est plus “vivant”
		java.util.UUID A = getLoverA(), B = getLoverB();
		boolean loverDown = (A != null && A.equals(deadId)) || (B != null && B.equals(deadId));

		if (!loverDown)
			return;

		for (RoleState s : states.values()) {
			if (s.roleId == RoleId.CUPIDON_V2) {
				// ces deux flags doivent exister dans RoleState si tu suis le design précédent
				s.cupid2CoupleAlive = false; // Cupidon v2 repasse côté Village pour la suite
			}
		}
	}

	/**
	 * Règle unique : /lg don autorisé si coupleAnnounced, sinon à partir d’EP2 + 5
	 * min.
	 */
	// RoleService.java
	public boolean canUseDonNow() {
	    try {
	        return hybridHandler != null && hybridHandler.isCoupleAnnounced();
	    } catch (Throwable ignored) {
	        return false;
	    }
	}

	
	// RoleService.java
	// 1) Marquage “devient loup” (aucun respawn, aucun message ici)
	public boolean infectConvertVictim(java.util.UUID victimId) {
	    if (victimId == null) return false;
	    RoleState s = states.get(victimId);
	    if (s == null) return false;

	    // Devient un loup pour toutes les mécaniques (hurlement, liste, win, etc.)
	    s.infectedAsWolf = true;

	    // Exceptions de victoire (ex : amoureux ne fait pas gagner loups)
	    if (s.lover != null) s.infectedNoWolfWin = true;
	    // (ajoute ici Ange/LGB si tu les gères plus tard)

	    return true;
	}



	
	// RoleService.java
	// 2) Appelé APRES le respawn (par DeathManager.reviveInfected) pour finir l’intégration
	public void onInfectedRevive(org.bukkit.entity.Player v) {
	    if (v == null) return;
	    RoleState s = get(v);
	    if (s == null) return;

	    // Sécurité
	    s.infectedAsWolf = true;
	    if (s.lover != null) s.infectedNoWolfWin = true;

	    // Petit confort : si c’est la nuit, donner NV immédiate (Force gérée par WolfHandler la nuit)
	    try {
	        if (game != null && game.isCurrentlyNight()) {
	            v.addPotionEffect(new org.bukkit.potion.PotionEffect(
	                org.bukkit.potion.PotionEffectType.NIGHT_VISION, 20 * 20, 0, true, false));
	        }
	    } catch (Throwable ignored) {}

	    // 🔁 Rafraîchir la meute (liste/notifications)
	    try {
	        fr.sfakeur.lguhc.WolfHandler wh = (fr.sfakeur.lguhc.WolfHandler) this.wolfHandler;
	        if (wh != null) {
	            wh.refreshPackAfterInfect(v.getUniqueId());
	        }
	    } catch (Throwable ignored) {}
	}



	
	// Utilisé pour le comptage de camps (checkWin, etc.)
	public Align baseAlignForCampCheck(RoleState s) {
	    if (s == null) return Align.SOLITAIRE;
	    if (s.infectedAsWolf) return Align.LOUP;
	    if (s.roleId == RoleId.CUPIDON || s.roleId == RoleId.CUPIDON_V2) return Align.VILLAGE;

	    // Amnésique v1 tant qu’il n’est pas réveillé = Village
	    if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) return Align.VILLAGE;

	    // Voleur avant vol = HYBRIDE, etc. (si tu avais ce cas)
	    if (s.roleId == RoleId.VOLEUR && !s.voleurStolen) return Align.HYBRIDE;

	    return s.align;
	}



	
	// RoleService.java (ou WolfHandler si tu préfères)
	private void sendCurrentWolfListTo(org.bukkit.entity.Player recv) {
	    java.util.List<String> names = new java.util.ArrayList<>();
	    for (RoleService.RoleState x : states.values()) {
	        if (x.align == Align.LOUP || x.infectedAsWolf) {
	            org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(x.owner);
	            if (pl != null && pl.isOnline()) names.add(pl.getName());
	        }
	    }
	    if (names.isEmpty()) return;
	    recv.sendMessage("§4[Loups] §7Loups actuels : §c" + String.join("§7, §c", names));
	}
	
	/** Rôle à montrer aux rôles à information (Voyante, Prêtresse, etc.). */
	public RoleId publicRoleForInfo(java.util.UUID playerId) {
	    RoleState s = states.get(playerId);
	    if (s == null) return null;

	    // ✅ Masque Amnésique v1 tant qu’il n’est pas réveillé
	    if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) {
	        return RoleId.SIMPLE_VILLAGEOIS;
	    }

	    // (optionnel) Si tu masques le Voleur aux rôles info :
	    if (s.voleurMaskInfo) return RoleId.VOLEUR;

	    return s.roleId;
	}

	public String publicDisplayNameForInfo(java.util.UUID playerId) {
	    RoleId id = publicRoleForInfo(playerId);
	    return displayName(id);
	}

	
	/** Transfère le couple: le lover du mort devient le lover du remplaçant; coupe le lien côté mort. */
	public void transferCoupleToReplacement(java.util.UUID deadId, java.util.UUID replacementId) {
	    RoleState dead = states.get(deadId);
	    RoleState rep  = states.get(replacementId);
	    if (dead == null || rep == null) return;
	    if (dead.lover == null) return;

	    java.util.UUID loverId = dead.lover;
	    RoleState sl = states.get(loverId);
	    org.bukkit.entity.Player lover = org.bukkit.Bukkit.getPlayer(loverId);
	    org.bukkit.entity.Player repPl = org.bukkit.Bukkit.getPlayer(replacementId);
	    if (sl != null) {
	        sl.lover = replacementId;
	        if (rep != null) {
	            rep.lover = loverId;
	            // conserve l’état d’annonce/meet
	            rep.loverAnnounced = sl.loverAnnounced;
	            rep.loverMet       = sl.loverMet;
	        }
	    }
	    // coupe le lien côté mort
	    dead.lover = null;

	    // feedback doux (optionnel)
	    if (lover != null && lover.isOnline() && repPl != null && repPl.isOnline() && sl.loverAnnounced) {
	        lover.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Couple] "
	            + org.bukkit.ChatColor.WHITE + repPl.getName()
	            + org.bukkit.ChatColor.LIGHT_PURPLE + " a pris la place de ton/ta partenaire.");
	        repPl.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "[Couple] "
	            + org.bukkit.ChatColor.GRAY + "Tu rejoins le couple aux côtés de "
	            + org.bukkit.ChatColor.WHITE + lover.getName() + org.bukkit.ChatColor.GRAY + ".");
	    }
	}

	
	public String displayNameForInfo(RoleState s) {
	    if (s == null) return "?";
	    if (s.roleId == RoleId.VOLEUR || s.voleurMaskInfo) return displayName(RoleId.VOLEUR);
	    return displayName(s.roleId);
	}



	/** Copie les “uses/cooldowns” spécifiques selon le rôle volé (ajuste selon tes champs). */
	private void copyPerRoleLimitedUses(RoleState from, RoleState to) {
	    switch (from.roleId) {
	        case ANALYSTE:
	            to.analysteUsesLeft         = from.analysteUsesLeft;
	            to.analysteLastUseSec       = from.analysteLastUseSec;
	            to.analysteAnalyseUsed      = from.analysteAnalyseUsed;
	            break;
	        case CONSTELLATIONNISTE:
	            to.usedAstrologie           = from.usedAstrologie;
	            to.lastTelescopeNight       = from.lastTelescopeNight;
	            break;
	        case DETECTIVE:
	            to.detectiveLastUsedEpisode = from.detectiveLastUsedEpisode;
	            to.detectiveSeenPlayers.addAll(from.detectiveSeenPlayers);
	            break;
	        case FEU_FOLLET:
	            to.ffFeatherUsesLeft        = from.ffFeatherUsesLeft;
	            to.ffFeatherCdEndMs         = from.ffFeatherCdEndMs;
	            to.ffFolieCdEndMs           = from.ffFolieCdEndMs;
	            to.ffFolieEndMs             = from.ffFolieEndMs;
	            break;
	        case VOYANTE:
	            // ajoute tes champs/CD de voyante si tu en as
	            break;
	        case ORACLE:
	            to.oracleLastUseSec         = from.oracleLastUseSec;
	            break;
	        case BIBLIOTHECAIRE:
	            to.biblioBorrower           = from.biblioBorrower;
	            to.biblioLoanEpisode        = from.biblioLoanEpisode;
	            to.biblioArchiveLeft        = from.biblioArchiveLeft;
	            break;
	        // ... complète ici selon tes rôles à compteurs/flags
	        default:
	            break;
	    }
	}


	
	// Align de victoire effectif (utilisé par /lg role et win logic)
	public Align effectiveWinAlign(java.util.UUID id) {
	    RoleState s = states.get(id);
	    if (s == null) return Align.HYBRIDE;

	    // Amnésique v1 non réveillé = compte Village
	    if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) {
	        return Align.VILLAGE;
	    }

	    // Voleur volé
	    if (s.roleId == RoleId.VOLEUR && s.voleurStolen) return s.voleurWinAlign;

	    if (s.infectedAsWolf) return Align.LOUP;
	    return alignOf(s.roleId);
	}



    // NOUVELLE VERSION UUID → robuste hors-ligne
 // ————————————————————————————————————————————————
 // Voleur : vol au moment de la mort DÉFINITIVE (version UUID)
 // ————————————————————————————————————————————————
 public boolean applyTheft(java.util.UUID thiefId, java.util.UUID victimId) {
     if (thiefId == null || victimId == null) return false;

     RoleState ts = states.get(thiefId);
     RoleState vs = states.get(victimId);
     if (ts == null || vs == null) return false;

     // doit être un Voleur qui n’a pas encore volé
     if (ts.roleId != RoleId.VOLEUR || ts.voleurStolen) return false;
     if (vs.roleId == null) return false;

     // 1) marque le vol et fixe l’align de victoire sur le camp volé
     ts.voleurStolen = true;
     ts.voleurStolenFrom = vs.roleId;
     ts.voleurWinAlign = alignOf(vs.roleId);  // ← utilisé par effectiveWinAlign(...)

     // 2) retire la Résistance I du voleur (elle durait “jusqu’au vol”)
     try {
         org.bukkit.entity.Player thiefPl = org.bukkit.Bukkit.getPlayer(thiefId);
         if (thiefPl != null) {
             thiefPl.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
             thiefPl.sendMessage(org.bukkit.ChatColor.GOLD + "[Voleur] " + org.bukkit.ChatColor.GRAY
                     + "Tu as volé le rôle : " + org.bukkit.ChatColor.WHITE + displayName(vs.roleId)
                     + org.bukkit.ChatColor.GRAY + ".");
         }
     } catch (Throwable ignored) {}

     // 3) copie best-effort de compteurs/limiteurs utiles (si le rôle volé en a)
     try {
         ts.analysteUsesLeft        = vs.analysteUsesLeft;
         ts.analysteLastUseSec      = vs.analysteLastUseSec;
         ts.ffFeatherUsesLeft       = vs.ffFeatherUsesLeft;
         ts.ffFeatherCdEndMs        = vs.ffFeatherCdEndMs;
         ts.ffFolieCdEndMs          = vs.ffFolieCdEndMs;
         ts.ffFolieEndMs            = vs.ffFolieEndMs;
         // …ajoute ici d’autres champs si tu veux transmettre leurs limites/états.
     } catch (Throwable ignored) {}

     // IMPORTANT : on NE modifie PAS ts.roleId (le voleur reste “VOLEUR” pour les rôles à info)
     return true;
 }

 // (facultatif) Wrapper Player -> UUID si tu en as encore besoin ailleurs
 public boolean applyTheft(org.bukkit.entity.Player thief, org.bukkit.entity.Player victim) {
     return applyTheft(
         thief  != null ? thief.getUniqueId()  : null,
         victim != null ? victim.getUniqueId() : null
     );
 }
 
//RoleService.java
 /// /lg role
//RoleService.java
public boolean cmdRole(org.bukkit.entity.Player p) {
  RoleState s = get(p);
  if (s == null || s.roleId == null) {
      p.sendMessage(org.bukkit.ChatColor.RED + "Ton rôle n’est pas encore attribué.");
      return true;
  }

  // ✅ CAS PRIORITAIRE — Amnésique v1 NON réveillé :
  // Il SE PENSE Simple Villageois → on affiche SV et on sort IMMÉDIATEMENT.
//=== Masque Amnésique v1 : tant qu’il n’est PAS réveillé, il voit SV ===
  if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) {
	    // couple (si présent)
	    String coupleLine = ChatColor.GRAY + "Couple : " + ChatColor.GOLD + "aucun";
	    if (s.lover != null) {
	        String loverName = nameOfUUID(s.lover);
	        coupleLine = ChatColor.GRAY + "Couple : " + ChatColor.LIGHT_PURPLE + "♥ " + (loverName != null ? loverName : "?");
	    }

	    p.sendMessage(ChatColor.GOLD + "[Rôle] " + ChatColor.GRAY + "Tu es : " + ChatColor.WHITE + displayName(RoleId.SIMPLE_VILLAGEOIS));
	    p.sendMessage(ChatColor.GRAY + "Camp : " + ChatColor.GREEN + "Village");
	    try { p.sendMessage(ChatColor.GRAY + "Aura : " + ChatColor.GOLD + auraPretty(ROLE_TO_AURA.get(RoleId.SIMPLE_VILLAGEOIS))); } catch (Throwable ignored) {}
	    try {
	        Constellation c = ROLE_TO_CONSTELLATION.get(RoleId.SIMPLE_VILLAGEOIS);
	        p.sendMessage(ChatColor.GRAY + "Constellation : " + ChatColor.GOLD + (c != null ? c.name() : "?"));
	    } catch (Throwable ignored) {}
	    if (s.lover != null) p.sendMessage(coupleLine);
	    return true; // <-- on s'arrête ici : pas de liste, pas d’“effets loup”, rien
	}


  // === Cas normal (inclut Amnésique v1 RÉVEILLÉ) ===

  // 1) Nom du rôle affiché (Voleur → montrer le rôle volé s’il a volé)
  boolean isVoleur = (s.roleId == RoleId.VOLEUR);
  boolean voleurStolen = isVoleur && s.voleurStolen && s.voleurStolenFrom != null;
  String roleSelf = voleurStolen
      ? (org.bukkit.ChatColor.WHITE + displayName(s.voleurStolenFrom)
         + org.bukkit.ChatColor.GRAY + " (volé)")
      : (org.bukkit.ChatColor.WHITE + displayName(s.roleId));
  //=== Tag "(solitaire)" si applicable ===
  if (s.solitaire) {
   roleSelf += ChatColor.DARK_GRAY + " (" + ChatColor.GOLD + "solitaire" + ChatColor.DARK_GRAY + ")";
  }

   

  // 2) Camp effectif
  Align eff = effectiveWinAlign(p.getUniqueId());
  String camp = prettyAlign(eff);
  
//Surcharge d’affichage : le Solitaire est montré en “Solo”
if (s.solitaire) {
   camp = "Solitaire";
}


  // 3) Couple
  if (s.lover != null) {
      String loverName = nameOfUUID(s.lover);
      p.sendMessage(org.bukkit.ChatColor.GRAY + "Couple : "
          + org.bukkit.ChatColor.LIGHT_PURPLE + "♥ "
          + (loverName != null ? loverName : "?"));
  }

  // Sœur (affiche la partenaire si présent)
  if (s.roleId == RoleId.SOEUR && s.soeurPartner != null && s.soeurNameRevealed) {
	    org.bukkit.entity.Player sp = org.bukkit.Bukkit.getPlayer(s.soeurPartner);
	    String nm = (sp != null ? sp.getName() : nameOfUUID(s.soeurPartner));
	    p.sendMessage(org.bukkit.ChatColor.GRAY + "Sœur : "
	            + org.bukkit.ChatColor.LIGHT_PURPLE + (nm != null ? nm : "?"));
	}


  // 4) Aura / Constellation (réelles dans le cas normal)
  try {
      p.sendMessage(org.bukkit.ChatColor.GOLD + "[Rôle] "
          + org.bukkit.ChatColor.GRAY + "Tu es : " + roleSelf);
      p.sendMessage(org.bukkit.ChatColor.GRAY + "Camp : "
          + org.bukkit.ChatColor.GOLD + camp);
  } catch (Throwable ignored) {}
  try {
      p.sendMessage(org.bukkit.ChatColor.GRAY + "Aura : "
          + org.bukkit.ChatColor.GOLD + auraPretty(s.aura));
  } catch (Throwable ignored) {}
  try {
      p.sendMessage(org.bukkit.ChatColor.GRAY + "Constellation : "
          + org.bukkit.ChatColor.GOLD + (s.constellation != null ? s.constellation.name() : "?"));
  } catch (Throwable ignored) {}

  // 5) Spécificités
  if (isVoleur && !s.voleurStolen) {
      p.sendMessage(org.bukkit.ChatColor.DARK_AQUA
          + "Effet : Résistance I jusqu’à ta première mort définitive infligée.");
  }

  // 6) Loups : liste visible (Amnésique “classique” filtré, v1 réveillé traité comme loup)
  try {
	  if (isWolf(s)) {
		    if (!wolfHandler.isWolfListAnnounced()) {
		        p.sendMessage(ChatColor.DARK_RED + "[Loups] " + ChatColor.GRAY + "La liste des loups n’est pas encore révélée.");
		    } else {
		        java.util.List<String> names = wolfHandler.visibleWolfNamesFor(p.getUniqueId());
		        String line = names.isEmpty() ? "aucun" : String.join(", ", names);
		        p.sendMessage(ChatColor.DARK_RED + "[Loups] " + ChatColor.GRAY + "Alliés visibles : " + ChatColor.GOLD + line);
		    }
		}
  } catch (Throwable ignored) {}

  return true;
}



//Jolis noms d’align (pour le print)
private String prettyAlign(Align a) {
  if (a == null) return "Inconnu";
  switch (a) {
      case VILLAGE:   return "Village";
      case LOUP:      return "Loups-Garous";
      case SOLITAIRE: return "Solitaire";
      case HYBRIDE:   return "Hybride";
      default:        return a.name();
  }
}

//Récupère un nom à partir d’un UUID (en ligne ou hors-ligne)
String nameOfUUID(java.util.UUID id) {
  if (id == null) return null;
  org.bukkit.entity.Player on = org.bukkit.Bukkit.getPlayer(id);
  if (on != null) return on.getName();
  try {
      org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(id);
      if (off != null && off.getName() != null) return off.getName();
  } catch (Throwable ignored) {}
  return null;
}

//Liste des loups “vivants” (survival) — utilise ton helper isWolf(...)
private java.util.List<String> listWolfNamesAlive() {
  java.util.List<String> out = new java.util.ArrayList<>();
  for (RoleState s : getStates().values()) {
      org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(s.owner);
      if (pl == null || !pl.isOnline() || pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) continue;
      if (isWolf(pl)) out.add(pl.getName());
  }
  java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
  return out;
}

//Joueurs maudits par une Sorcière : -1♥ Absorption à chaque gapple jusqu’à la fin
private final java.util.Set<java.util.UUID> witchCursed = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
public void markWitchCursed(java.util.UUID id) { if (id != null) witchCursed.add(id); }
public boolean isWitchCursed(java.util.UUID id) { return id != null && witchCursed.contains(id); }

//appliqué après consommation d’une gapple
public void applyWitchAbsorptionPenalty(org.bukkit.entity.Player p) {
 if (p == null) return;
 try {
     net.minecraft.server.v1_8_R3.EntityPlayer handle = ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) p).getHandle();
     float cur = Math.max(0F, handle.getAbsorptionHearts());
     float next = Math.max(0F, cur - 2.0F); // -1 cœur = -2 HP
     handle.setAbsorptionHearts(next);
 } catch (Throwable t) {
     // fallback: petit “poke” comme tes Twins
     double before = p.getHealth();
     p.damage(2.0D);
     if (p.getHealth() < before) p.setHealth(Math.min(before, p.getMaxHealth()));
 }
}

public void onWitchRevive(org.bukkit.entity.Player p) {
    // Hook facultatif : si tu veux loguer, racontrable, etc.
    try { logRacontable(p, "witch_revive"); } catch (Throwable ignored) {}
}


/** Appelé par le GameManager après le décompte LG9. 
 *  @param votesAgainst  Map<UUID, Integer> nombre de votes contre chaque joueur
 *  @param mostVoted     joueur le plus voté (pour ton malus LG9), peut être null
 */
public void onLG9Tally(java.util.Map<java.util.UUID, Integer> votesAgainst, java.util.UUID mostVoted) {
    if (votesAgainst == null || votesAgainst.isEmpty()) return;
    for (java.util.Map.Entry<java.util.UUID, Integer> e : votesAgainst.entrySet()) {
        java.util.UUID id = e.getKey();
        int votes = Math.max(0, e.getValue());
        RoleState s = states.get(id);
        if (s == null || s.roleId != RoleId.DECHU) continue;

        // 1 coeur par 2 votes, plafonné à +5 coeurs
        int shouldHearts = Math.min(5, votes / 2);
        int delta = shouldHearts - s.dechuHeartsAwarded;
        if (delta > 0) {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                // cœurs PERMANENTS
                boolean ok = changeMaxHearts(p, delta);
                if (ok) {
                    s.dechuHeartsAwarded += delta;
                    p.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "[Déchu] "
                        + org.bukkit.ChatColor.GRAY + "Les votes contre toi t’endurcissent : "
                        + org.bukkit.ChatColor.GREEN + "+" + delta + "♥ "
                        + org.bukkit.ChatColor.GRAY + "(total " + s.dechuHeartsAwarded + "/5).");
                }
            } else {
                // s’il est hors-ligne, on enregistre quand même; l’augmentation sera appliquée à sa connexion si tu gères un hook
                s.dechuHeartsAwarded += delta;
            }
        }
        // S’il est le plus voté, rien à changer ici : il prendra quand même ton malus LG9 existant.
    }
}

public void revealAmnesiquesToWolves() {
    for (RoleState s : states.values()) {
        if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE && !s.amnVisibleToWolves) {
            s.amnVisibleToWolves = true;
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(s.owner);
            if (p != null) {
                p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Amnésique] "
                    + org.bukkit.ChatColor.GRAY + "Tu es désormais reconnu par la meute.");
            }
        }
    }
    // (Optionnel) prévenir discrètement les autres loups
    for (org.bukkit.entity.Player w : org.bukkit.Bukkit.getOnlinePlayers()) {
        RoleState ws = get(w);
        if (ws == null) continue;
        boolean isWolf = (ws.align == Align.LOUP || ws.infectedAsWolf);
        if (!isWolf) continue;
        w.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Loups] "
            + org.bukkit.ChatColor.GRAY + "Un nouvel         a rejoint la liste des loups.");
    }
}

public java.util.List<java.util.UUID> getWolfIdsVisibleTo(java.util.UUID viewerId) {
    RoleState vs = states.get(viewerId);
    java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
    for (java.util.Map.Entry<java.util.UUID, RoleState> e : states.entrySet()) {
        java.util.UUID id = e.getKey();
        RoleState s = e.getValue();
        if (s == null) continue;
        boolean isWolf = (s.align == Align.LOUP || s.infectedAsWolf);
        if (!isWolf) continue;

        if (vs != null && vs.roleId == RoleId.LOUP_GAROU_AMNESIQUE) {
            // L'amnésique voit seulement ceux qu'il a "découverts"
            if (vs.amnDiscovered.contains(id)) out.add(id);
        } else {
            // Un loup "classique" ne voit PAS l'amnésique tant qu'il n'est pas révélé
            if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE && !s.amnVisibleToWolves) continue;
            out.add(id);
        }
    }
    // retire le viewer lui-même si présent
    out.remove(viewerId);
    return out;
}

//RoleService.java (DANS la classe RoleService)
public void offerSisterReveal(java.util.UUID victimId, java.util.UUID killerId) {
 RoleService.RoleState vs = this.states.get(victimId);
 if (vs == null || vs.roleId != RoleService.RoleId.SOEUR) return;
 if (killerId == null) return; // mort non attribuée

 // Sœur survivante
 java.util.UUID partner = vs.soeurPartner;
 if (partner == null) return;
 RoleService.RoleState ps = this.states.get(partner);
 if (ps == null) return;

 org.bukkit.entity.Player sister = org.bukkit.Bukkit.getPlayer(partner);
 if (sister == null || !sister.isOnline() || sister.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;

 // Initialise l’offre
 ps.soeurOfferKillerId   = killerId;
 ps.soeurChoiceAvailable = true;

 // Message cliquable
 org.bukkit.entity.Player vNow = org.bukkit.Bukkit.getPlayer(victimId);
 String cible = (vNow != null ? vNow.getName() : "ta sœur");

 net.md_5.bungee.api.chat.ComponentBuilder cb = new net.md_5.bungee.api.chat.ComponentBuilder("")
     .append("§d[Sœur] §7Ta sœur §f" + cible + " §7a été tuée. Choisis une info : ");

 cb.append("[Pseudo]").bold(true).color(net.md_5.bungee.api.ChatColor.LIGHT_PURPLE)
   .event(new net.md_5.bungee.api.chat.ClickEvent(
       net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg soeurpseudo"))
   .event(new net.md_5.bungee.api.chat.HoverEvent(
       net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
       new net.md_5.bungee.api.chat.ComponentBuilder("Voir le pseudo de l’assassin").create()));

 cb.append(" ");

 cb.append("[Rôle]").bold(true).color(net.md_5.bungee.api.ChatColor.AQUA)
   .event(new net.md_5.bungee.api.chat.ClickEvent(
       net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/lg soeurrole"))
   .event(new net.md_5.bungee.api.chat.HoverEvent(
       net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
       new net.md_5.bungee.api.chat.ComponentBuilder("Voir le rôle de l’assassin").create()));

 sister.spigot().sendMessage(cb.create());
}

public String infoDisplayName(RoleState target) {
    if (target == null) return "Inconnu";
    // ✅ Amnésique v1 non réveillé : vu comme SV
    if (target.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !target.amnV1Awake)
        return displayName(RoleId.SIMPLE_VILLAGEOIS);
    // Voleur masqué (si tu as déjà une règle)
    if (target.roleId == RoleId.VOLEUR && target.voleurMaskInfo) return "Voleur";
    return displayName(target.roleId);
}

public Aura infoAura(RoleState target) {
    if (target == null) return Aura.NEUTRE;
    // ✅ Amnésique v1 non réveillé : aura du SV
    if (target.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !target.amnV1Awake)
        return ROLE_TO_AURA.get(RoleId.SIMPLE_VILLAGEOIS);
    return ROLE_TO_AURA.getOrDefault(target.roleId, Aura.NEUTRE);
}

public Constellation infoConstellation(RoleState target) {
    if (target == null) return null;
    // ✅ Amnésique v1 non réveillé : constellation du SV
    if (target.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !target.amnV1Awake)
        return ROLE_TO_CONSTELLATION.get(RoleId.SIMPLE_VILLAGEOIS);
    return ROLE_TO_CONSTELLATION.get(target.roleId);
}

private String displayNameForSelf(RoleState s) {
    // Voleur déjà volé : (si tu avais cette logique, garde-la éventuellement ici)
    if (s.roleId == RoleId.VOLEUR && s.voleurStolen && s.voleurStolenFrom != null) {
        return displayName(s.voleurStolenFrom) + "§7 (volé)";
    }
    // Amnésique v1 : se croit SV jusqu’au réveil
    if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) {
        return displayName(RoleId.SIMPLE_VILLAGEOIS);
    }
    // par défaut
    return displayName(s.roleId);
}

/** Aura à montrer aux rôles à information. */
public Aura publicAuraForInfo(java.util.UUID playerId) {
    RoleId vid = visibleRoleId(playerId);           // ⟵ clé
    if (vid == null) return Aura.NEUTRE;
    return ROLE_TO_AURA.getOrDefault(vid, Aura.NEUTRE);
}

/** Constellation à montrer aux rôles à information. */
public Constellation publicConstellationForInfo(java.util.UUID playerId) {
    RoleId vid = visibleRoleId(playerId);           // ⟵ clé
    if (vid == null) return null;
    return ROLE_TO_CONSTELLATION.getOrDefault(vid, Constellation.MOUTON);
}


@org.bukkit.event.EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
public void onCommandFromDead(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
    final org.bukkit.entity.Player p = e.getPlayer();

    // Vivant ? (isAlive() renvoie false si en "sursis" chez toi)
    DeathManager dm = plugin.getDeathManager();
    if (dm == null || dm.isAlive(p.getUniqueId())) return;

    // racine de la commande
    String msg = e.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
    if (msg.startsWith("/")) msg = msg.substring(1);
    String root = msg.split("\\s+", 2)[0];

    // Liste des commandes plugin à bloquer pour les morts
    if (root.equals("lg") || root.equals("vote")) {
        e.setCancelled(true);
        p.sendMessage(org.bukkit.ChatColor.RED + "Tu es mort : tu ne peux plus utiliser cette commande.");
    }
}

public void applySolitaire(java.util.UUID playerId) {
    RoleState s = states.get(playerId);
    if (s == null || s.solitaire) return;

    s.solitaire = true;

    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerId);
    if (p != null && p.isOnline()) {
        // +4 coeurs ( = +8 HP en 1.8)
        if (!s.solitaireHeartsGiven) {
            s.solitaireHeartsGiven = true;
            try {
                double newMax = Math.min(40.0D, p.getMaxHealth() + 8.0D); // plafond 20 coeurs si tu veux
                p.setMaxHealth(newMax);
                p.setHealth(newMax);
            } catch (Throwable ignored) {}
        }

        p.sendMessage(org.bukkit.ChatColor.DARK_RED + "[Solitaire] " + org.bukkit.ChatColor.GRAY
                + "Tu deviens " + org.bukkit.ChatColor.GOLD + "Loup Solitaire"
                + org.bukkit.ChatColor.GRAY + " : tu dois gagner seul. Tu obtiens "
                + org.bukkit.ChatColor.GOLD + "+4 coeurs" + org.bukkit.ChatColor.GRAY + ".");
    }
}

public void fullResetForNewRun() {
    // Annule les états des rôles de l’ancienne partie
    try { states.clear(); } catch (Throwable ignored) {}

    // Si tu as des caches/cooldowns/options globales, remets-les ici :
    // exemple :
    try { setSolitaireEnabled(false); } catch (Throwable ignored) {}
    // …remets à zéro d'autres flags/cooldowns globaux si nécessaire.
}

//Qui peut utiliser les pouvoirs de <target> ?
//- le joueur qui A ce rôle
//- OU la Servante Dévouée qui a volé ce rôle (servanteStolenFrom)
public boolean canUseRolePower(java.util.UUID uid, RoleId target) {
 RoleState s = states.get(uid);
 if (s == null || target == null) return false;
 if (s.roleId == target) return true;
 return s.roleId == RoleId.SERVANTE_DEVOUEE
     && s.servanteUsed
     && s.servanteStolenFrom == target;
}

//RoleService.java
public RoleId roleShownToInfo(java.util.UUID viewerId, java.util.UUID targetId) {
 RoleState t = getStates().get(targetId);
 if (t == null) return null;
 if (t.roleId == RoleId.LOUP_GAROU_FEUTRE && t.feutreActive && t.feutreShownRole != null) {
     return t.feutreShownRole;
 }
 return t.roleId;
}

//RoleService.java
public RoleId visibleRoleId(java.util.UUID playerId) {
 RoleState s = states.get(playerId);
 if (s == null) return null;

 // 1) Loup-Garou Feutré : s’il a une façade active, on montre la façade
 if (s.roleId == RoleId.LOUP_GAROU_FEUTRE && s.feutreActive && s.feutreShownRole != null) {
     return s.feutreShownRole;
 }

 // 2) Amnésique v1 non réveillé : apparaît comme Simple Villageois
 if (s.roleId == RoleId.LOUP_GAROU_AMNESIQUE_V1 && !s.amnV1Awake) {
     return RoleId.SIMPLE_VILLAGEOIS;
 }

 // 3) Par défaut : rôle réel
 return s.roleId;
}






















	









    
    





	
	





}
