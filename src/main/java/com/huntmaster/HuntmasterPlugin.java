package com.huntmaster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.HttpUrl;
import okhttp3.Response;

import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;

import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;


@Slf4j
@PluginDescriptor(
		name = "Huntmaster",
		internalName = "huntmaster",
		legacyDataDirectory = "huntmaster",
		description = "Exclusively for Bosscape's Huntmaster Discord Bot, used to verify bossing kills."
)
public class HuntmasterPlugin extends Plugin
{
	@Provides BosscapeSettings provideBosscapeSettings(ConfigManager manager)
	{ return manager.getConfig(BosscapeSettings.class); }
	private long nextAccountSnapshotAt;
	private int accountSnapshotLoginTick;
	private boolean accountSnapshotInFlight;

	private void updateAccountSnapshot()
	{
		long now = System.currentTimeMillis();
		Player player = client.getLocalPlayer();
		if (!encounterCommunicationEnabled() || !registrationConfirmed || !healthReachable
			|| assignmentSyncRequired || accountSnapshotInFlight || now < nextAccountSnapshotAt
			|| client.getGameState() != GameState.LOGGED_IN || player == null || !sameRsn(player.getName(), assignmentRsn)
			|| client.getTickCount() - accountSnapshotLoginTick < 5) return;
		nextAccountSnapshotAt = now + 60_000L;
		JsonObject snapshot = AccountSnapshot.read(client);
		accountSnapshotInFlight = true;
		Request request = new Request.Builder().url(HUNTMASTER_API_BASE_URL + "/api/runelite/account-state")
			.post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(snapshot))).build();
		enqueueRequest(request, (status, body) -> { accountSnapshotInFlight = false; });
	}
	// ==================================================
	// HUNTMASTER API
	// ==================================================

	private static final String HUNTMASTER_API_BASE_URL =
			BotEndpoint.load(Boolean.getBoolean("huntmaster.developmentMode"));


	private static final long HUNTMASTER_API_TIMEOUT_SECONDS = 5;


	// ==================================================
	// VERIFIED KC RETRY QUEUE
	// ==================================================

	private static final long HUNTMASTER_RETRY_DELAY_MS =
			5_000L;


	private final Map<String, PendingKcEvent> pendingKcEvents =
			new ConcurrentHashMap<>();


	// ==================================================
	// PENDING KC PERSISTENCE
	//
	// Pending verified KC events are stored in RuneLite's
	// configuration so they survive a client restart.
	//
	// Each saved event retains:
	// - event ID
	// - RSN
	// - boss
	// - original creation time
	//
	// The same event ID is restored after restart so API
	// idempotency continues to protect against duplicate KC.
	// ==================================================

	private static final String HUNTMASTER_CONFIG_GROUP =
			"huntmaster";


	private static final String HUNTMASTER_PENDING_EVENTS_KEY =
			"pendingKcEvents";


	// ==================================================
	// RUNELITE INJECTIONS
	// ==================================================

	@Inject
	private Client client;


	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;


	// ==================================================
	// BOSS DETECTORS
	// ==================================================

	private final BossDetector[] bossDetectors =
			BossRegistry.createDetectors();
	private final Map<String, BossDetector> detectorsByName = indexDetectors();

	private Map<String, BossDetector> indexDetectors()
	{
		Map<String, BossDetector> index = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (BossDetector detector : bossDetectors) index.put(detector.getName(), detector);
		return index;
	}

	private BossDetector registeredDetector(String boss)
	{
		return boss == null ? null : detectorsByName.get(boss.trim());
	}


	// ==================================================
	// HTTP CLIENT
	// ==================================================

	private OkHttpClient httpClient;
	private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();
	private final ConnectionOutageState outage = new ConnectionOutageState();
	private static final String OUTAGE_STARTED_KEY = "outageStartedAt";
	private KcBaselineStore baselineStore;
	private BossDetector genericObservationDetector;
	private boolean registrationConfirmed;
	private String genericObservationAssignment;
	private int chestRewardInventory = -1;
	private int chestRewardTick = -1;
	private String chestRewardAssignment;
	private final Map<String, Integer> observedBaselines = new java.util.HashMap<>();
	private String baselineProfile;
	private ScheduledExecutorService connectionExecutor;
	private ScheduledFuture<?> connectionTask;
	private volatile long lifecycle;
	private volatile boolean running;
	private boolean connectionInitialized;
	private boolean healthInFlight;
	private boolean healthReachable;
	private boolean trackingPaused;
	private int outageNoticeStage;
	private boolean recoveryNoticePending;
	private boolean assignmentInFlight;
	private boolean assignmentSyncRequired = true;
	private String assignmentRsn;
	private String assignmentId;
	private String assignedBoss;
	private String assignmentNotice;
	private String assignmentNoticeRsn;
	private String lastInvalidatedNoticeId;
	private static final String ASSIGNMENT_CACHE_KEY = "assignmentCache";
	private static final String UNBOUND_EVENTS_KEY = "unboundPendingKcEvents";
	private final VerificationReliabilityState reliability = new VerificationReliabilityState();
	private String reliabilityRsn;
	private boolean logoutObserved;
	private String reliabilityNotice;
	private int lastReliabilityFailureTick = -1;
	private static final String VERIFICATION_TELEMETRY_KEY = "verificationTelemetry";
	private static final int MAX_TELEMETRY_ENTRIES = 128;
	private JsonObject verificationTelemetry = new JsonObject();
	private final EncounterReportQueue reportQueue = new EncounterReportQueue();
	private long savedReportRevision = -1;
	private long requestGeneration;
	private final EncounterCapture encounterCapture = new EncounterCapture(this::queueEncounterReport);
	private final DagannothComponentCollector dagannothCapture = new DagannothComponentCollector(this::queueEncounterReport);
	private final AssignedTotalTracker assignedTotalTracker = new AssignedTotalTracker();
	private boolean reportsEnabled;

	private final PluginLink pluginLink = new PluginLink();
	private boolean linkNoticeShown;
	private boolean membershipDenied;


	private static final String REPORT_QUEUE_KEY = "pendingEncounterReports";



	// ==================================================
	// SESSION STATE
	// ==================================================

	private boolean needsRsnDetection =
			true;


	// ==================================================
	// PENDING VERIFIED KC EVENT
	// ==================================================

	private static final class PendingKcEvent
	{
		private final String eventId;

		private final String rsn;

		private final String bossName;

		private final long createdAt;
		private final String assignmentId;


		private volatile int attempts =
				0;


		private volatile long nextAttemptAt =
				0L;


		private volatile boolean inFlight =
				false;


		private PendingKcEvent(
				String eventId,
				String rsn,
				String bossName,
				long createdAt,
				String assignmentId)
		{
			this.eventId =
					eventId;

			this.rsn =
					rsn;

			this.bossName =
					bossName;

			this.createdAt =
					createdAt;
			this.assignmentId = assignmentId;
		}
	}


	// ==================================================
	// PLUGIN STARTUP
	// ==================================================

	@Override
	protected void startUp()
	{
		membershipDenied = false;
		accountSnapshotInFlight = false;
		nextAccountSnapshotAt = System.currentTimeMillis() + 10_000L;
		accountSnapshotLoginTick = client.getTickCount();
		configManager.unsetConfiguration(HUNTMASTER_CONFIG_GROUP, "pluginCredential");
		linkNoticeShown = false;
		connectionInitialized = false;
		if (HUNTMASTER_API_BASE_URL == null)
		{
			log.warn("Huntmaster bot HTTPS endpoint is not configured; public communication is unavailable");
		}
		baselineStore = createBaselineStore();
		observedBaselines.clear();
		baselineProfile = null;
		genericObservationDetector = null;
		registrationConfirmed = false;
		genericObservationAssignment = null;
		running = true;
		long session = ++lifecycle;
		httpClient = okHttpClient.newBuilder()
				.followRedirects(false)
				.followSslRedirects(false)
				.connectTimeout(HUNTMASTER_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(HUNTMASTER_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.callTimeout(HUNTMASTER_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.build();
		BossRegistry.validateDetectors(
				bossDetectors
		);


		log.debug(
				"Huntmaster registered {} boss detectors",
				bossDetectors.length
		);


		log.debug(
				"Huntmaster boss detector registry validation PASSED"
		);


		// Reload any verified KC events that were still
		// waiting for Huntmaster when RuneLite last closed.
		loadPendingKcEvents();


		log.debug(
				"Huntmaster started"
		);


		if (
				!pendingKcEvents.isEmpty()
		)
		{
			log.debug(
					"Huntmaster restored {} pending verified KC event(s) waiting to retry",
					pendingKcEvents.size()
			);
		}


		clientThread.invokeLater(() ->
		{
			if (!running || lifecycle != session)
			{
				return;
			}
			String saved = configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, OUTAGE_STARTED_KEY);
			try
			{
				outage.restore(saved == null ? 0 : Long.parseLong(saved), System.currentTimeMillis());
			}
			catch (NumberFormatException ex)
			{
				outage.restore(0, System.currentTimeMillis());
				log.debug("Huntmaster ignored malformed outage timestamp", ex);
			}
			if (outage.getStartedAt() == 0)
			{
				configManager.unsetConfiguration(HUNTMASTER_CONFIG_GROUP, OUTAGE_STARTED_KEY);
			}
			else
			{
				configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, OUTAGE_STARTED_KEY, outage.getStartedAt());
			}
			healthInFlight = false;
			healthReachable = false;
			assignmentInFlight = false;
			assignmentSyncRequired = true;
			assignmentNotice = null;
			lastInvalidatedNoticeId = null;
			loadAssignmentCache();
			reliabilityRsn = null;
			reliability.restore(0);
			reliabilityNotice = null;
			logoutObserved = client.getGameState() == GameState.LOGIN_SCREEN;
			lastReliabilityFailureTick = -1;
			loadVerificationTelemetry();
			reportsEnabled = encounterCommunicationEnabled();
			loadEncounterReports();
			trackingPaused = false;
			outageNoticeStage = 0;
			recoveryNoticePending = false;
			connectionInitialized = true;
			for (PendingKcEvent pending : pendingKcEvents.values())
			{
				pending.inFlight = false;
			}
			updateOutageTracking();
			connectionExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
			{
				Thread thread = new Thread(runnable, "huntmaster-connection");
				thread.setDaemon(true);
				return thread;
			});
			connectionTask = connectionExecutor.scheduleWithFixedDelay(() -> clientThread.invokeLater(() ->
			{
				if (running && lifecycle == session)
				{
					updateOutageTracking();
					retryPendingKcEvents();
					diagnostic(this::updateEncounterReports);
					testHuntmasterApiConnection();
					diagnostic(this::updateAccountSnapshot);
				}
			}), 0, HUNTMASTER_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		});


		if (
				client.getGameState() ==
						GameState.LOGGED_IN
		)
		{
			logCurrentRsn();
		}
	}


	// ==================================================
	// PLUGIN SHUTDOWN
	// ==================================================

	@Override
	protected void shutDown()
	{
		if (reportsEnabled) diagnostic(() -> interruptEncounterCaptures(client.getTickCount(), EncounterObservation.Reason.SHUTDOWN));
		saveEncounterReports();
		clearEncounterCaptures();
		running = false;
		reportsEnabled = false;
		if (baselineStore != null) baselineStore.close();
		++lifecycle;
		if (connectionTask != null)
		{
			connectionTask.cancel(false);
		}
		if (connectionExecutor != null)
		{
			connectionExecutor.shutdownNow();
		}
		for (Call call : activeCalls)
		{
			call.cancel();
		}
		activeCalls.clear();
		savePendingKcEvents();


		if (
				!pendingKcEvents.isEmpty()
		)
		{
			log.warn(
					"Huntmaster stopped with {} pending verified KC event(s) safely persisted for the next startup",
					pendingKcEvents.size()
			);
		}


		log.debug(
				"Huntmaster stopped"
		);
	}


	// ==================================================
	// GAME TICK
	// ==================================================

	@Subscribe
	public void onGameTick(GameTick event)
	{
		showLinkNotice();
		if (reportsEnabled) diagnostic(() -> { encounterCapture.advance(client.getTickCount()); dagannothCapture.advance(client.getTickCount()); });
		updateVerificationRecovery();
		updateOutageTracking();
		showOutageNotices();
		if (!canTrackNewKills())
		{
			assignedTotalTracker.clear();
			loadObservedBaselines();
			BossDetector pausedDetector = registeredDetector(assignedBoss);
			if (pausedDetector != null) refreshDetectorBaseline(pausedDetector, client.getGameState() == GameState.LOGGED_IN);
			return;
		}
		diagnostic(this::observeAssignedSpecialTotal);
		retryPendingKcEvents();

		if (needsRsnDetection)
		{
			Player localPlayer =
					client.getLocalPlayer();

			if (localPlayer != null)
			{
				log.debug(
						"Huntmaster detected RSN: {}",
						localPlayer.getName()
				);

				needsRsnDetection = false;
			}
		}

		BossDetector detector = registeredDetector(assignedBoss);
		if (detector != null && !detector.getDefinition().isEvidenceOnly())
		{
			switch (detector.getDetectorType())
			{
				case STANDARD_NPC:
					handleStandardNpcGameTick(
							detector
					);
					break;

				case COMPLETION:
				case ACTIVITY:
					handleTotalGameTick(
							detector
					);
					break;

				case SPECIAL:
					// Custom encounters use the assignment-scoped evidence collectors.
					break;

				default:
					log.warn(
							"Huntmaster encountered unsupported detector type for {}: {}",
							detector.getName(),
							detector.getDetectorType()
					);
					break;
			}
		}
	}

	private void handleTotalGameTick(BossDetector detector)
	{
		String label = detector.getDetectorType() == BossDetectorType.ACTIVITY ? "activity" : "completion";
		Integer totalVarpId =
				detector.getCompletionVarpId();

		if (totalVarpId == null)
		{
			log.warn(
					"Huntmaster total-count detector {} has no dedicated varp",
					detector.getName()
			);

			return;
		}

		if (client.getGameState()
				!= GameState.LOGGED_IN)
		{
			return;
		}

		int currentTotal =
				client.getVarpValue(
						totalVarpId
				);

		Integer lastTotal =
				detector.getLastKc();

		BossVerificationState state =
				detector.getState();

		if (lastTotal == null)
		{
			detector.setLastKc(
					currentTotal
			);

			log.debug(
					"Huntmaster established {} total-count baseline: {}",
					detector.getName(),
					currentTotal
			);

			return;
		}

		if (currentTotal != lastTotal) observeCounter(detector, EncounterObservation.CounterSource.COMPLETION_VARP, lastTotal, currentTotal);
		int increase =
				currentTotal
						- lastTotal;

		if (increase == 1)
		{
			state.setKcIncreaseConfirmed(
					true
			);

			log.debug(
					"Huntmaster detected {} total-count increase: {} -> {} (+1)",
					detector.getName(),
					lastTotal,
					currentTotal
			);

			detector.setLastKc(
					currentTotal
			);

			if (!evaluateTotalVerification(
					detector))
			{
				detector.startPendingVerification();

				log.debug(
						"Huntmaster {} verification pending second signal",
						detector.getName()
				);
			}

			return;
		}

		if (increase > 1)
		{
			handleAmbiguousKc(
					detector.getName(),
					"unexpected " + label + " total increase "
							+ lastTotal
							+ " -> "
							+ currentTotal
							+ " (+"
							+ increase
							+ ")"
			);

			detector.setLastKc(
					currentTotal
			);

			detector.resetVerification();

			return;
		}

		if (increase < 0)
		{
			log.warn(
					"Huntmaster detected {} total-count decrease: {} -> {}. Resyncing baseline.",
					detector.getName(),
					lastTotal,
					currentTotal
			);

			detector.setLastKc(
					currentTotal
			);

			detector.resetVerification();

			return;
		}

		if (!state.isPendingVerification()
				|| state.getPendingTicksRemaining() <= 0)
		{
			return;
		}

		state.setPendingTicksRemaining(
				state.getPendingTicksRemaining() - 1
		);

		if (state.getPendingTicksRemaining() == 0)
		{
			if (!evaluateTotalVerification(
					detector))
			{
				handleUnresolvedKc(
						detector.getName(),
						label + " verification window expired"
				);

				detector.resetVerification();
			}
		}
	}


	private void handleStandardNpcGameTick(
			BossDetector detector)
	{
		BossVerificationState state =
				detector.getState();

		if (!state.isPendingVerification()
				|| state.getPendingTicksRemaining() <= 0)
		{
			return;
		}

		state.setPendingTicksRemaining(
				state.getPendingTicksRemaining() - 1
		);

		if (state.getPendingTicksRemaining() == 0)
		{
			if (!evaluateStandardNpcVerification(detector))
			{
				handleUnresolvedKc(
						detector.getName(),
						"verification window expired",
						StandardNpcVerification.countsExpiredAttemptAsFailure(detector.getDefinition())
				);

				detector.resetVerification();
			}
		}
	}

@Subscribe
public void onRuneScapeProfileChanged(
		RuneScapeProfileChanged event)
{
	loadObservedBaselines();
	for (BossDetector detector : bossDetectors)
	{
		/*
		 * COMPLETION and ACTIVITY detectors use
		 * dedicated live in-game varps rather than
		 * RuneLite's saved killcount profile value.
		 */
		if (detector.getDetectorType()
				== BossDetectorType.COMPLETION
				|| detector.getDetectorType()
				== BossDetectorType.ACTIVITY)
		{
			continue;
		}

		Integer savedKc =
				configManager.getRSProfileConfiguration(
						"killcount",
						detector.getProfileKey(),
						int.class
				);

		if (savedKc == null)
		{
			continue;
		}

		/*
		 * STANDARD_NPC behavior remains unchanged.
		 */
		detector.setLastKc(
				baselineWithCheckpoint(detector, savedKc)
		);

		log.debug(
				"Huntmaster loaded {} KC baseline from RuneLite profile: {}",
				detector.getName(),
				detector.getLastKc()
		);
	}
}


	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!canTrackNewKills())
		{
			return;
		}
		Actor actor =
				event.getActor();

		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc =
				(NPC) actor;

		if (collectDagannothNpc(npc.getName(), false)) return;

		BossDetector detector =
				findDetectorByNpcName(
						npc.getName()
				);

		if (detector == null)
		{
			return;
		}

		if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC) observePrimary(detector, EncounterObservation.SignalKind.DEATH);
		if (detector.getDefinition().isEvidenceOnly()) return;
		switch (detector.getDetectorType())
		{
			case STANDARD_NPC:
				handleStandardNpcDeath(
						detector
				);
				break;

			case COMPLETION:
			case ACTIVITY:
			case SPECIAL:
				// ActorDeath is not currently part of these
				// detector strategies.
				break;

			default:
				log.warn(
						"Huntmaster encountered unsupported detector type for {}: {}",
						detector.getName(),
						detector.getDetectorType()
				);
				break;
		}
	}

	private void handleStandardNpcDeath(
			BossDetector detector)
	{
		BossVerificationState state =
				detector.getState();

		log.debug(
				"Huntmaster detected {} kill candidate",
				detector.getName()
		);

		state.setDeathCandidate(
				true
		);

		boolean recentLoot =
				hasRecentLoot(
						state,
						detector.getPendingWindowTicks()
				);

		if (!recentLoot)
		{
			state.setLootOccurred(
					false
			);

			state.setLootTick(
					-1
			);
		}

		if (evaluateStandardNpcVerification(detector))
		{
			return;
		}

		detector.startPendingVerification();
	}

	@Subscribe
	public void onNpcLootReceived(
			NpcLootReceived event)
	{
		if (!canTrackNewKills())
		{
			return;
		}
		NPC npc =
				event.getNpc();

		if (npc == null)
		{
			return;
		}

		if (collectDagannothNpc(npc.getName(), true)) return;

		BossDetector detector =
				findDetectorByNpcName(
						npc.getName()
				);

		if (detector == null)
		{
			return;
		}

		if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC) observeLoot(detector);
		if (detector.getDefinition().isEvidenceOnly()) return;
		switch (detector.getDetectorType())
		{
			case STANDARD_NPC:
				handleStandardNpcLoot(
						detector
				);
				break;

			case COMPLETION:
			case ACTIVITY:
			case SPECIAL:
				// NPC loot is not currently part of these
				// detector strategies.
				break;

			default:
				log.warn(
						"Huntmaster encountered unsupported detector type for {}: {}",
						detector.getName(),
						detector.getDetectorType()
				);
				break;
		}
	}

	@Subscribe
	public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded event)
	{
		if (!canTrackNewKills()) return;
		BossDetector detector = genericObservationDetector();
		if (detector == null) return;
		int inventory = ChestCompletionAdapter.rewardInventory(detector.getName(), event.getGroupId());
		if (inventory < 0) return;
		chestRewardInventory = inventory;
		chestRewardTick = client.getTickCount();
		chestRewardAssignment = assignmentId;
		observePrimary(detector, EncounterObservation.SignalKind.COMPLETION);
		observeChestLoot(detector, client.getItemContainer(inventory));
	}

	@Subscribe
	public void onItemContainerChanged(net.runelite.api.events.ItemContainerChanged event)
	{
		if (!canTrackNewKills() || event.getContainerId() != chestRewardInventory
				|| !java.util.Objects.equals(assignmentId, chestRewardAssignment)
				|| client.getTickCount() < chestRewardTick || client.getTickCount() - chestRewardTick >= 10) return;
		BossDetector detector = genericObservationDetector();
		if (detector != null) observeChestLoot(detector, event.getItemContainer());
	}

	private void observeChestLoot(BossDetector detector, net.runelite.api.ItemContainer container)
	{
		if (container == null) return;
		for (net.runelite.api.Item item : container.getItems())
			if (item.getId() >= 0 && item.getQuantity() > 0) { observeLoot(detector); return; }
	}

	private void handleStandardNpcLoot(
			BossDetector detector)
	{
		BossVerificationState state =
				detector.getState();

		if (client.getTickCount()
				== state.getLastVerifiedTick())
		{
			log.debug(
					"Huntmaster ignored {} loot because this kill was already verified",
					detector.getName()
			);

			return;
		}

		state.setLootOccurred(
				true
		);

		state.setLootTick(
				client.getTickCount()
		);

		log.debug(
				"Huntmaster detected {} loot occurrence",
				detector.getName()
		);

		evaluateStandardNpcVerification(
				detector
		);
	}

	@Subscribe
	public void onChatMessage(
			ChatMessage event)
	{
		// Plugin notices are not kill evidence. Ignore our own synchronous chat events.
		if (event.getMessage() != null
				&& event.getMessage().startsWith("Huntmaster RuneLite Plugin "))
		{
			return;
		}
		if (!canTrackNewKills())
		{
			return;
		}
		if (event.getType()
				!= ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message =
				event.getMessage();

		if (canCollectDagannoth() && dagannothCapture.counter(message, client.getLocalPlayer().getName(),
			assignmentId, client.getTickCount(), System.currentTimeMillis())) return;

		BossDetector generic = genericObservationDetector();
		Integer genericTotal = generic == null ? null : GenericKcRouter.parse(message, generic.getName());
		if (genericTotal != null)
		{
			Integer previous = generic.getLastKc();
			observeCounter(generic, EncounterObservation.CounterSource.KC_MESSAGE, previous, genericTotal);
			if (previous != null && (long) genericTotal - previous > 1)
				diagnostic(() -> encounterCapture.uncertain(EncounterObservation.Outcome.AMBIGUOUS));
			generic.setLastKc(genericTotal);
			checkpointObservedKc(generic, genericTotal);
			return;
		}

		BossDetector detector =
				findDetectorByKcMessage(
						message
				);

		if (detector == null)
		{
			return;
		}

		if (detector.getDefinition().isEvidenceOnly())
		{
			diagnostic(() -> observeEvidenceOnlyMessage(detector, message));
			return;
		}
		switch (detector.getDetectorType())
		{
			case STANDARD_NPC:
				handleStandardNpcKcMessage(
						detector,
						message
				);
				break;

			case COMPLETION:
			case ACTIVITY:
				handleTotalKcMessage(
						detector,
						message
				);
				break;

			case SPECIAL:
				// Custom encounters use the assignment-scoped evidence collectors.
				break;

			default:
				log.warn(
						"Huntmaster encountered unsupported detector type for {}: {}",
						detector.getName(),
						detector.getDetectorType()
				);
				break;
		}
	}

	private void handleStandardNpcKcMessage(
			BossDetector detector,
			String message)
	{
		Integer parsed = GenericKcRouter.parse(message, detector.getName());
		if (parsed == null) return;
		int currentKc = parsed;
		checkpointObservedKc(detector, currentKc);

		Integer lastKc =
				detector.getLastKc();

		BossVerificationState state =
				detector.getState();

		if (lastKc == null)
		{
			detector.setLastKc(
					currentKc
			);

			log.debug(
					"Huntmaster established {} KC baseline: {}",
					detector.getName(),
					currentKc
			);

			return;
		}

		observeCounter(detector, EncounterObservation.CounterSource.KC_MESSAGE, lastKc, currentKc);
		int kcIncrease =
				currentKc - lastKc;

		if (kcIncrease == 1)
		{
			state.setKcIncreaseConfirmed(
					true
			);

			log.debug(
					"Huntmaster detected {} KC increase: {} -> {} (+1)",
					detector.getName(),
					lastKc,
					currentKc
			);

			if (!evaluateStandardNpcVerification(detector))
			{
				log.warn(
						"Huntmaster detected {} KC increase without enough verification evidence",
						detector.getName()
				);

				detector.startPendingVerification();

				log.debug(
						"Huntmaster {} verification pending second signal",
						detector.getName()
				);
			}
		}
		else if (kcIncrease > 1)
		{
			handleAmbiguousKc(
					detector.getName(),
					"unexpected increase "
							+ lastKc
							+ " -> "
							+ currentKc
							+ " (+"
							+ kcIncrease
							+ ")"
			);

			detector.resetVerification();
		}
		else
		{
			if (state.isDeathCandidate())
			{
				detector.startPendingVerification();

				log.warn(
						"Huntmaster detected {} death without a KC increase: {} -> {}",
						detector.getName(),
						lastKc,
						currentKc
				);

				log.debug(
						"Huntmaster {} verification pending loot tie-breaker",
						detector.getName()
				);
			}
			else
			{
				log.warn(
						"Huntmaster received {} KC without an increase: {} -> {}",
						detector.getName(),
						lastKc,
						currentKc
				);
			}
		}

		detector.setLastKc(
				currentKc
		);
	}

	private void handleTotalKcMessage(
			BossDetector detector,
			String message)
	{
		observePrimary(detector, detector.getDetectorType() == BossDetectorType.ACTIVITY
			? EncounterObservation.SignalKind.ACTIVITY_COMPLETION : EncounterObservation.SignalKind.COMPLETION);
		BossVerificationState state =
				detector.getState();

		log.debug(
				"Huntmaster detected {} completion/activity message: {}",
				detector.getName(),
				message
		);

		state.setDeathCandidate(
				true
		);

		if (!evaluateTotalVerification(
				detector))
		{
			detector.startPendingVerification();

			log.debug(
					"Huntmaster {} verification pending total-count increase",
					detector.getName()
			);
		}
	}


	private BossDetector findDetectorByNpcName(String npcName)
	{
		if (npcName == null)
		{
			return null;
		}

		BossDetector detector = registeredDetector(assignedBoss);
		if (detector != null)
		{
			if (detector.getDefinition().isEvidenceOnly()
					? detector.getName().equalsIgnoreCase(npcName) : detector.getName().equals(npcName))
			{
				return detector;
			}
		}

		BossDetector generic = genericObservationDetector();
		return generic != null && BossNpcAliases.matches(generic.getName(), npcName) ? generic : null;
	}

	private BossDetector genericObservationDetector()
	{
		if (!reportsEnabled || assignmentId == null || !GenericKcRouter.eligible(assignedBoss)
			|| "Dagannoth Kings".equalsIgnoreCase(assignedBoss)) return null;
		if (registeredDetector(assignedBoss) != null) return null;
		if (!java.util.Objects.equals(genericObservationAssignment, assignmentId)
				|| genericObservationDetector == null || !genericObservationDetector.getName().equalsIgnoreCase(assignedBoss))
		{
			genericObservationAssignment = assignmentId;
			genericObservationDetector = SpecialEncounterTotals.varp(assignedBoss) != null
				? new BossDetector(BossDefinition.totalCounterCandidate(assignedBoss, assignedBoss.toLowerCase(java.util.Locale.ROOT)))
				: new BossDetector(BossDefinition.betaCandidate(assignedBoss,
				assignedBoss.toLowerCase(java.util.Locale.ROOT), "Your " + assignedBoss + " kill count is:"));
		}
		return genericObservationDetector;
	}

	private BossDetector findDetectorByKcMessage(String message)
	{
		if (message == null)
		{
			return null;
		}

		BossDetector detector = registeredDetector(assignedBoss);
		if (detector != null)
		{
			if (GenericKcRouter.parse(message, detector.getName()) != null || (detector.getDefinition().isEvidenceOnly()
					? EvidenceOnlyCounter.matchesPrefix(message, detector.getKcMessagePrefix())
					: message.startsWith(detector.getKcMessagePrefix())))
			{
				return detector;
			}
		}

		return null;
	}

	private boolean hasRecentLoot(
			BossVerificationState state,
			int pendingWindowTicks)
	{
		int lootTick = state.getLootTick();

		if (!state.isLootOccurred() || lootTick < 0)
		{
			return false;
		}

		int lootAge = client.getTickCount() - lootTick;

		return lootAge >= 0
				&& lootAge <= pendingWindowTicks;
	}

	private boolean evaluateTotalVerification(
			BossDetector detector)
	{
		BossVerificationState state =
				detector.getState();

		if (state.isDeathCandidate()
				&& state.isKcIncreaseConfirmed())
		{
			handleVerifiedKc(
					detector.getName(),
					state,
					detector.getDetectorType() == BossDetectorType.ACTIVITY
					? "activity completion message + activity count increase"
					: "completion message + completion total increase"
			);

			state.reset();

			return true;
		}

		return false;
	}


	private boolean evaluateStandardNpcVerification(
			BossDetector detector)
	{
		BossVerificationState state =
				detector.getState();

		boolean recentLoot =
				hasRecentLoot(
						state,
						detector.getPendingWindowTicks()
				);

		EncounterObservation.Method selected = StandardNpcVerification.method(detector.getDefinition(), state, recentLoot);
		if (selected == EncounterObservation.Method.DEATH_AND_COUNTER)
		{
			handleVerifiedKc(
					detector.getName(),
					state,
					"death event + KC increase"
			);

			state.reset();

			return true;
		}

		if (selected == EncounterObservation.Method.DEATH_AND_LOOT)
		{
			handleVerifiedKc(
					detector.getName(),
					state,
					"death event + loot"
			);

			state.reset();

			return true;
		}

		if (selected == EncounterObservation.Method.COUNTER_AND_LOOT)
		{
			handleVerifiedKc(
					detector.getName(),
					state,
					"KC increase + loot"
			);

			state.reset();

			return true;
		}

		return false;
	}

// ==================================================
// HANDLE VERIFIED KC
// ==================================================

	private void handleVerifiedKc(
			String bossName,
			BossVerificationState state,
			String verificationMethod)
	{
		if (evidenceOnlyBoss(bossName) || !canTrackNewKills())
		{
			return;
		}
		state.setLastVerifiedTick(
				client.getTickCount()
		);


		log.debug(
				"Huntmaster VERIFIED {} KC: {}",
				bossName,
				verificationMethod
		);


		Player localPlayer =
				client.getLocalPlayer();


		if (
				localPlayer == null
		)
		{
			log.warn(
					"Huntmaster could not queue VERIFIED {} KC because the local player is unavailable",
					bossName
			);

			return;
		}


		String rsn =
				localPlayer.getName();


		if (
				rsn == null ||
						rsn.trim().isEmpty()
		)
		{
			log.warn(
					"Huntmaster could not queue VERIFIED {} KC because the RSN is unavailable",
					bossName
			);

			return;
		}


		if (!matchesAssignedBoss(bossName))
		{
			log.debug("Huntmaster ignored {} KC outside the known assignment", bossName);
			return;
		}

		if (reliability.getFailures() > 0)
		{
			reliability.recordVerified();
			saveVerificationReliability();
			reliabilityNotice = null;
		}
		String eventId =
				UUID.randomUUID()
						.toString();


		PendingKcEvent pendingEvent =
				new PendingKcEvent(
						eventId,
						rsn,
					bossName,
					System.currentTimeMillis(),
					assignmentId
				);


		pendingKcEvents.put(
				eventId,
				pendingEvent
		);
		recordVerificationTelemetry(bossName, VerificationTelemetry.Outcome.VERIFIED);
		if (reportsEnabled) diagnostic(() -> encounterCapture.verified(UUID.fromString(eventId), observationMethod(verificationMethod)));


		savePendingKcEvents();


		log.debug(
				"Huntmaster created KC event {} for {}",
				eventId,
				bossName
		);


		log.debug(
				"Huntmaster queued VERIFIED {} KC for RSN {} — event {}",
				bossName,
				rsn,
				eventId
		);


		sendPendingKcEvent(
				pendingEvent
		);
	}


// ==================================================
// RETRY PENDING VERIFIED KC EVENTS
// ==================================================

	private void retryPendingKcEvents()
	{
		if (!encounterCommunicationEnabled()) return;
		if (
				pendingKcEvents.isEmpty()
		)
		{
			return;
		}


		long now =
				System.currentTimeMillis();


		for (
				PendingKcEvent pendingEvent :
				pendingKcEvents.values()
		)
		{
			if (
					pendingEvent.inFlight
			)
			{
				continue;
			}


			if (
					now <
							pendingEvent.nextAttemptAt
			)
			{
				continue;
			}


			log.debug(
					"Huntmaster retrying pending KC event {} for {} at {}",
					pendingEvent.eventId,
					pendingEvent.rsn,
					pendingEvent.bossName
			);


			sendPendingKcEvent(
					pendingEvent
			);
		}
	}


// ==================================================
// SEND PENDING VERIFIED KC EVENT
// ==================================================

	private void sendPendingKcEvent(PendingKcEvent pendingEvent)
	{
		if (!running || pendingEvent == null || pendingEvent.inFlight
				|| !pendingKcEvents.containsKey(pendingEvent.eventId))
		{
			return;
		}
		pendingEvent.inFlight = true;
		pendingEvent.attempts++;
		JsonObject body = new JsonObject();
		body.addProperty("eventId", pendingEvent.eventId);
		body.addProperty("rsn", pendingEvent.rsn);
		body.addProperty("boss", pendingEvent.bossName);
		body.addProperty("assignmentId", pendingEvent.assignmentId);
		Request request = new Request.Builder()
				.url(HUNTMASTER_API_BASE_URL + "/api/runelite/kc")
				.post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(body)))
				.build();
		log.debug("Huntmaster sending KC event {} attempt {}", pendingEvent.eventId, pendingEvent.attempts);
		enqueueRequest(request, (status, responseBody) ->
		{
			pendingEvent.inFlight = false;
			if (status == 200)
			{
				// Never discard a persisted event on a malformed success response.
				try
				{
					JsonObject result = gson.fromJson(responseBody, JsonObject.class);
					if (result == null || !result.has("success") || !result.get("success").getAsBoolean())
					{
						throw new IllegalArgumentException("Missing success acknowledgement");
					}
				}
				catch (RuntimeException ex)
				{
					markConnectionFailure();
					schedulePendingKcRetry(pendingEvent, "Invalid API acknowledgement");
					return;
				}
				pendingKcEvents.remove(pendingEvent.eventId);
				savePendingKcEvents();
				log.debug("Huntmaster KC event {} accepted and removed from pending queue: {}", pendingEvent.eventId, responseBody);
				refreshAssignment();
				recoverConnectionIfReady();
			}
			else if (status >= 400 && status < 500)
			{
				try
				{
					JsonObject rejection = gson.fromJson(responseBody, JsonObject.class);
					if (rejection != null && rejection.has("status")
							&& "assignment_invalidated".equals(rejection.get("status").getAsString())
							&& rejection.has("sameBossReplacement") && rejection.get("sameBossReplacement").getAsBoolean()
							&& !pendingEvent.assignmentId.equals(lastInvalidatedNoticeId))
					{
						lastInvalidatedNoticeId = pendingEvent.assignmentId;
						assignmentNoticeRsn = pendingEvent.rsn;
						assignmentNotice = "Saved kills for " + pendingEvent.bossName
								+ " belonged to your previous Huntmaster assignment and were discarded. They cannot count toward your new assignment for the same boss.";
					}
				}
				catch (RuntimeException ex)
				{
					log.debug("Huntmaster could not read assignment rejection details", ex);
				}
				// Preserve the existing API rejection contract (including HTTP 409).
				pendingKcEvents.remove(pendingEvent.eventId);
				savePendingKcEvents();
				log.debug("Huntmaster KC event {} rejected with HTTP {}: {}", pendingEvent.eventId, status, responseBody);
				refreshAssignment();
				recoverConnectionIfReady();
			}
			else
			{
				markConnectionFailure();
				schedulePendingKcRetry(pendingEvent, "HTTP " + status + ": " + responseBody);
			}
		});
	}


// ==================================================
// SCHEDULE VERIFIED KC RETRY
// ==================================================

	private void schedulePendingKcRetry(
			PendingKcEvent pendingEvent,
			String reason)
	{
		if (
				pendingEvent == null
		)
		{
			return;
		}


		if (
				!pendingKcEvents.containsKey(
						pendingEvent.eventId
				)
		)
		{
			return;
		}


		pendingEvent.nextAttemptAt =
				System.currentTimeMillis()
						+ HUNTMASTER_RETRY_DELAY_MS;


		long ageMs =
				Math.max(
						0L,
						System.currentTimeMillis()
								- pendingEvent.createdAt
				);


		log.debug(
				"Huntmaster KC event {} remains pending for {} at {} — attempt {} failed — age {} ms — retrying in {} ms — {}",
				pendingEvent.eventId,
				pendingEvent.rsn,
				pendingEvent.bossName,
				pendingEvent.attempts,
				ageMs,
				HUNTMASTER_RETRY_DELAY_MS,
				reason
		);
	}


// ==================================================
// ERROR MESSAGE HELPER
// ==================================================

	private String getErrorMessage(
			Throwable error)
	{
		if (
				error == null
		)
		{
			return "unknown error";
		}


		Throwable cause =
				error;


		while (
				cause.getCause() != null
		)
		{
			cause =
					cause.getCause();
		}


		String message =
				cause.getMessage();


		if (
				message == null ||
						message.trim().isEmpty()
		)
		{
			return cause
					.getClass()
					.getSimpleName();
		}


		return cause
				.getClass()
				.getSimpleName()
				+ ": "
				+ message;
	}


	// ==================================================
// PENDING KC PERSISTENCE
// ==================================================

	private void savePendingKcEvents()
	{
		if (
				pendingKcEvents.isEmpty()
		)
		{
			configManager.unsetConfiguration(
					HUNTMASTER_CONFIG_GROUP,
					HUNTMASTER_PENDING_EVENTS_KEY
			);


			log.debug(
					"Huntmaster cleared persisted pending KC events"
			);


			return;
		}


		StringBuilder serialized =
				new StringBuilder();


		for (
				PendingKcEvent pendingEvent :
				pendingKcEvents.values()
		)
		{
			if (
					serialized.length() > 0
			)
			{
				serialized.append(
						"\n"
				);
			}


			serialized
					.append(
							pendingEvent.eventId
					)
					.append(
							"|"
					)
					.append(
							encodePersistenceValue(
									pendingEvent.rsn
							)
					)
					.append(
							"|"
					)
					.append(
							encodePersistenceValue(
									pendingEvent.bossName
							)
					)
					.append(
							"|"
					)
					.append(
							pendingEvent.createdAt
					)
					.append("|")
					.append(pendingEvent.assignmentId);
		}


		configManager.setConfiguration(
				HUNTMASTER_CONFIG_GROUP,
				HUNTMASTER_PENDING_EVENTS_KEY,
				serialized.toString()
		);


		log.debug(
				"Huntmaster persisted {} pending KC event(s)",
				pendingKcEvents.size()
		);
	}


// ==================================================
// LOAD PERSISTED PENDING KC EVENTS
// ==================================================

	private void loadPendingKcEvents()
	{
		pendingKcEvents.clear();


		String serialized =
				configManager.getConfiguration(
						HUNTMASTER_CONFIG_GROUP,
						HUNTMASTER_PENDING_EVENTS_KEY
				);


		if (
				serialized == null ||
						serialized.trim().isEmpty()
		)
		{
			return;
		}


		String[] records =
				serialized.split(
						"\\r?\\n"
				);


		int restoredCount =
				0;


		for (
				String record :
				records
		)
		{
			if (
					record == null ||
							record.trim().isEmpty()
			)
			{
				continue;
			}


			try
			{
				String[] parts =
						record.split(
								"\\|",
								-1
						);


				if (
						parts.length != 4 && parts.length != 5
				)
				{
					log.warn(
							"Huntmaster ignored malformed persisted KC event record"
					);

					continue;
				}


				String eventId =
						parts[0];


				String rsn =
						decodePersistenceValue(
								parts[1]
						);


				String bossName =
						decodePersistenceValue(
								parts[2]
						);


				long createdAt =
						Long.parseLong(
								parts[3]
						);
				if (parts.length == 4 || parts[4].trim().isEmpty())
				{
					// Keep unbound legacy events intact; guessing an assignment could miscredit KC.
					String unbound = configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, UNBOUND_EVENTS_KEY);
					if (unbound == null || !java.util.Arrays.asList(unbound.split("\\r?\\n")).contains(record))
					{
						configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, UNBOUND_EVENTS_KEY,
								unbound == null || unbound.isEmpty() ? record : unbound + "\n" + record);
					}
					log.debug("Huntmaster preserved unbound legacy event {} separately for review", eventId);
					continue;
				}


				if (
						eventId == null ||
								eventId.trim().isEmpty() ||
								rsn == null ||
								rsn.trim().isEmpty() ||
								bossName == null ||
								bossName.trim().isEmpty()
				)
				{
					log.warn(
							"Huntmaster ignored incomplete persisted KC event record"
					);

					continue;
				}


				PendingKcEvent pendingEvent =
						new PendingKcEvent(
								eventId,
								rsn,
								bossName,
								createdAt,
								parts[4]
						);


				pendingKcEvents.put(
						eventId,
						pendingEvent
				);


				restoredCount +=
						1;


				log.debug(
						"Huntmaster restored pending KC event {} for {} at {}",
						eventId,
						rsn,
						bossName
				);
			}
			catch (
					Exception exception
			)
			{
				log.warn(
						"Huntmaster could not restore a persisted KC event record",
						exception
				);
			}
		}


		if (
				restoredCount == 0
		)
		{
			configManager.unsetConfiguration(
					HUNTMASTER_CONFIG_GROUP,
					HUNTMASTER_PENDING_EVENTS_KEY
			);


			return;
		}


		/*
		 * Rewrite the saved value after loading.
		 *
		 * If one malformed record was skipped, this removes
		 * the bad entry while preserving all valid events.
		 */
		savePendingKcEvents();


		log.debug(
				"Huntmaster loaded {} persisted pending KC event(s)",
				restoredCount
		);
	}


// ==================================================
// PERSISTENCE VALUE ENCODING
// ==================================================

	private String encodePersistenceValue(
			String value)
	{
		if (
				value == null
		)
		{
			return "";
		}


		return Base64
				.getEncoder()
				.encodeToString(
						value.getBytes(
								StandardCharsets.UTF_8
						)
				);
	}


	private String decodePersistenceValue(
			String value)
	{
		if (
				value == null ||
						value.isEmpty()
		)
		{
			return "";
		}


		return new String(
				Base64
						.getDecoder()
						.decode(
								value
						),
				StandardCharsets.UTF_8
		);
	}

// ==================================================
// UNRESOLVED VERIFICATION
// ==================================================

	private void handleUnresolvedKc(String bossName, String reason)
	{
		handleUnresolvedKc(bossName, reason, true);
	}

	private void handleUnresolvedKc(String bossName, String reason, boolean countFailure)
	{
		if (reportsEnabled) diagnostic(() -> encounterCapture.uncertain(EncounterObservation.Outcome.UNRESOLVED));
		log.debug(
				"Huntmaster UNRESOLVED {} KC: {}",
				bossName,
				reason
		);
		if (countFailure && !evidenceOnlyBoss(bossName)) recordVerificationFailure(bossName, "unresolved", reason);
	}

	private void handleAmbiguousKc(String bossName, String reason)
	{
		if (reportsEnabled) diagnostic(() -> encounterCapture.uncertain(EncounterObservation.Outcome.AMBIGUOUS));
		log.debug(
				"Huntmaster AMBIGUOUS {} KC: {}. No KC verified.",
				bossName,
				reason
		);
		if (!evidenceOnlyBoss(bossName)) recordVerificationFailure(bossName, "ambiguous", reason);
	}

	private void logCurrentRsn()
	{
		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			log.warn(
					"Huntmaster could not detect the current RSN"
			);

			return;
		}

		String rsn =
				localPlayer.getName();

		log.debug(
				"Huntmaster detected RSN: {}",
				rsn
		);
	}


	// ==================================================
	// HUNTMASTER API CONNECTION TEST
	// ==================================================

	private void testHuntmasterApiConnection()
	{
		if (!encounterCommunicationEnabled()) return;
		if (healthInFlight)
		{
			return;
		}
		healthInFlight = true;
		Request request = new Request.Builder().url(HUNTMASTER_API_BASE_URL + "/health")
				.header("Cache-Control", "no-cache").build();
		enqueueRequest(request, (status, body) ->
		{
			healthInFlight = false;
			try
			{
				JsonObject result = gson.fromJson(body, JsonObject.class);
				healthReachable = status == 200 && result != null
						&& result.has("success") && result.get("success").getAsBoolean()
						&& result.has("service") && "huntmaster-runelite-api".equals(result.get("service").getAsString())
						&& result.has("status") && "online".equals(result.get("status").getAsString());
			}
			catch (RuntimeException ex)
			{
				healthReachable = false;
			}
			if (healthReachable)
			{
				refreshAssignment();
				recoverConnectionIfReady();
			}
			else
			{
				markConnectionFailure();
			}
		});
	}

	/** Read response bodies on OkHttp's pool; mutate plugin/client state on the client thread. */
	private void enqueueRequest(Request request, BiConsumer<Integer, String> completion)
	{
		if (!encounterCommunicationEnabled()) return;
		long session = lifecycle;
		long generation = requestGeneration;
		Call call = httpClient.newCall(pluginLink.authenticate(request, HUNTMASTER_API_BASE_URL));
		activeCalls.add(call);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failedCall, IOException error)
			{
				finish(-1, getErrorMessage(error));
			}

			@Override
			public void onResponse(Call responseCall, Response response)
			{
				try (Response closedResponse = response)
				{
					finish(response.code(), response.body() == null ? "" : response.body().string());
				}
				catch (IOException ex)
				{
					finish(-1, getErrorMessage(ex));
				}
			}

			private void finish(int status, String body)
			{
				activeCalls.remove(call);
				clientThread.invokeLater(() ->
				{
					if (running && lifecycle == session && requestGeneration == generation && encounterCommunicationEnabled())
					{
						if (status == 401 || status == 403) { rejectLink(); return; }
						completion.accept(status, body);
					}
				});
			}
		});
	}

	private void markConnectionFailure()
	{
		assignmentSyncRequired = true;
		healthReachable = false;
		if (outage.fail(System.currentTimeMillis()))
		{
			configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, OUTAGE_STARTED_KEY, outage.getStartedAt());
			log.debug("Huntmaster connection outage started at {}", outage.getStartedAt());
		}
		updateOutageTracking();
	}

	private boolean canTrackNewKills()
	{
		if (!running || !connectionInitialized || !encounterCommunicationEnabled())
		{
			return false;
		}
		updateOutageTracking();
		Player player = client.getLocalPlayer();
		return !trackingPaused && !reliability.isPaused() && player != null && assignmentId != null
				&& sameRsn(player.getName(), reliabilityRsn)
				&& (!assignmentSyncRequired || outage.getStartedAt() != 0)
				&& sameRsn(player.getName(), assignmentRsn);
	}

	private void updateOutageTracking()
	{
		if (outage.isPaused(System.currentTimeMillis()) && !trackingPaused)
		{
			trackingPaused = true;
			refreshDetectorBaselines();
			log.debug("Huntmaster new kill tracking paused after ten-minute outage; {} saved events retained", pendingKcEvents.size());
		}
	}

	// Never post chat notices inside another chat event or the login transition.
	private void showOutageNotices()
	{
		if (!encounterCommunicationEnabled()) return;
		if (!running || !connectionInitialized || client.getGameState() != GameState.LOGGED_IN
				|| client.getLocalPlayer() == null)
		{
			return;
		}
		if (trackingPaused && outageNoticeStage < 2)
		{
			// addChatMessage synchronously emits ChatMessage back to this plugin.
			// Claim the notice before dispatch so re-entry cannot emit it again.
			outageNoticeStage = 2;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster RuneLite Plugin has been unable to reach the Huntmaster Discord Bot for 10 minutes. New kills are no longer being recorded. Previously saved kills are preserved. Avoid further Huntmaster kills until tracking resumes.", null);
		}
		else if (outage.getStartedAt() != 0 && outageNoticeStage == 0)
		{
			outageNoticeStage = 1;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster RuneLite Plugin cannot reach the Huntmaster Discord Bot. Verified kills will be saved during the first 10 minutes of this outage.", null);
		}
		if (recoveryNoticePending)
		{
			recoveryNoticePending = false;
			String message = reliability.isPaused()
					? "Huntmaster RuneLite Plugin has reconnected to the Huntmaster Discord Bot. Saved events have been handled. Kill verification remains paused; log out and back in to reset verification baselines. Uncertain kills are not credited."
					: "Huntmaster RuneLite Plugin has reconnected to the Huntmaster Discord Bot. Saved events have been handled and kill tracking has resumed. Kills made while tracking was paused are not credited.";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
		}
		if (assignmentNotice != null && sameRsn(assignmentNoticeRsn, client.getLocalPlayer().getName()))
		{
			String message = assignmentNotice;
			assignmentNotice = null;
			assignmentNoticeRsn = null;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster RuneLite Plugin " + message, null);
		}
		if (reliabilityNotice != null && sameRsn(reliabilityRsn, client.getLocalPlayer().getName()))
		{
			String message = reliabilityNotice;
			reliabilityNotice = null;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster RuneLite Plugin " + message, null);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN) accountSnapshotLoginTick = client.getTickCount();
		nextAccountSnapshotAt = System.currentTimeMillis() + 10_000L;
		if (running && event.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (reportsEnabled) diagnostic(() -> interruptEncounterCaptures(client.getTickCount(), EncounterObservation.Reason.LOGOUT));
			logoutObserved = true;
		}
	}

	private String verificationReliabilityKey()
	{
		String normalized = reliabilityRsn.replace('\u00a0', ' ').trim().toLowerCase(java.util.Locale.ROOT);
		return "verificationReliability_" + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
	}

	private void updateVerificationRecovery()
	{
		Player player = client.getLocalPlayer();
		if (!running || !connectionInitialized || client.getGameState() != GameState.LOGGED_IN
				|| player == null || player.getName() == null)
		{
			return;
		}
		if (!sameRsn(player.getName(), reliabilityRsn))
		{
			reliabilityRsn = player.getName();
			reliabilityNotice = null;
			lastReliabilityFailureTick = -1;
			String saved = configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, verificationReliabilityKey());
			try
			{
				JsonObject state = saved == null ? null : gson.fromJson(saved, JsonObject.class);
				reliability.restore(state == null ? 0 : state.get("failures").getAsInt());
			}
			catch (RuntimeException ex)
			{
				// Malformed saved state must not silently bypass the safety pause.
				reliability.restore(VerificationReliabilityState.FAILURE_THRESHOLD);
				log.debug("Huntmaster could not restore verification reliability", ex);
			}
			refreshDetectorBaselines();
			if (reliability.isPaused())
			{
				reliabilityNotice = "Kill verification is paused. Log out and back in to reset verification baselines. Uncertain kills are not credited.";
			}
		}
		if (logoutObserved)
		{
			logoutObserved = false;
			boolean wasPaused = reliability.isPaused();
			reliability.restore(0);
			lastReliabilityFailureTick = -1;
			saveVerificationReliability();
			refreshDetectorBaselines();
			assignmentSyncRequired = true;
			refreshAssignment();
			reliabilityNotice = wasPaused
					? "Relog reset kill verification baselines. Uncertain kills were not credited. New kills can be tracked once your assignment and connection are ready."
					: null;
		}
	}

	private void saveVerificationReliability()
	{
		JsonObject saved = new JsonObject();
		saved.addProperty("failures", reliability.getFailures());
		configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, verificationReliabilityKey(), gson.toJson(saved));
	}

	private void recordVerificationFailure(String bossName, String kind, String reason)
	{
		if (!canTrackNewKills() || !matchesAssignedBoss(bossName)
				|| lastReliabilityFailureTick == client.getTickCount())
		{
			return;
		}
		lastReliabilityFailureTick = client.getTickCount();
		if (!reliability.recordFailure())
		{
			return;
		}
		saveVerificationReliability();
		JsonObject diagnostic = new JsonObject();
		recordVerificationTelemetry(bossName, "ambiguous".equals(kind)
				? VerificationTelemetry.Outcome.AMBIGUOUS : VerificationTelemetry.Outcome.UNRESOLVED);
		diagnostic.addProperty("recordedAt", System.currentTimeMillis());
		diagnostic.addProperty("assignmentId", assignmentId);
		diagnostic.addProperty("boss", bossName);
		diagnostic.addProperty("kind", kind);
		diagnostic.addProperty("reason", reason);
		diagnostic.addProperty("consecutiveFailures", reliability.getFailures());
		// Keep the latest report locally; broader telemetry is a separate roadmap item.
		configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, verificationReliabilityKey() + "_diagnostic", gson.toJson(diagnostic));
		log.debug("Huntmaster verification reliability report: {}", diagnostic);
		if (reliability.isPaused())
		{
			refreshDetectorBaselines();
			reliabilityNotice = "Kill tracking paused after three consecutive unverifiable kill events. Log out and back in to reset verification baselines. Uncertain kills are not credited; previously verified saved kills are preserved.";
		}
		else if (reliability.getFailures() == 1)
		{
			reliabilityNotice = "Could not verify a kill for " + bossName + ". It was not credited. Tracking will pause after three consecutive verification failures.";
		}
	}

	private void loadVerificationTelemetry()
	{
		verificationTelemetry = new JsonObject();
		String saved = configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, VERIFICATION_TELEMETRY_KEY);
		if (saved == null)
		{
			return;
		}
		try
		{
			if (saved.length() > 131_072)
			{
				throw new IllegalArgumentException("Oversized telemetry configuration");
			}
			JsonObject restored = gson.fromJson(saved, JsonObject.class);
			if (restored != null)
			{
				verificationTelemetry = restored;
				trimVerificationTelemetry();
			}
		}
		catch (RuntimeException ex)
		{
			verificationTelemetry = new JsonObject();
			log.debug("Huntmaster ignored malformed verification telemetry", ex);
		}
	}

	private void trimVerificationTelemetry()
	{
		while (verificationTelemetry.size() > MAX_TELEMETRY_ENTRIES)
		{
			String oldestKey = null;
			long oldestTime = Long.MAX_VALUE;
			for (Map.Entry<String, com.google.gson.JsonElement> entry : verificationTelemetry.entrySet())
			{
				long updatedAt = 0;
				try
				{
					updatedAt = entry.getValue().getAsJsonObject().get("updatedAt").getAsLong();
				}
				catch (RuntimeException ignored)
				{
					// Malformed entries are evicted first.
				}
				if (oldestKey == null || updatedAt < oldestTime)
				{
					oldestKey = entry.getKey();
					oldestTime = updatedAt;
				}
			}
			verificationTelemetry.remove(oldestKey);
		}
	}

	private void recordVerificationTelemetry(String bossName, VerificationTelemetry.Outcome outcome)
	{
		// Diagnostic failures must never interrupt KC queuing or verification safety.
		try
		{
			BossDetector detector = registeredDetector(bossName);
			if (detector == null)
			{
				return;
			}
			String key = bossName + "|" + detector.getDetectorType() + "|" + detector.getDetectorVersion();
			VerificationTelemetry stats = new VerificationTelemetry();
			try
			{
				JsonObject old = verificationTelemetry.getAsJsonObject(key);
				if (old != null)
				{
					stats.restore(old.get("verified").getAsLong(), old.get("unresolved").getAsLong(), old.get("ambiguous").getAsLong());
				}
			}
			catch (RuntimeException ex)
			{
				log.debug("Huntmaster reset malformed telemetry entry for {}", key, ex);
			}
			stats.record(outcome);
			JsonObject snapshot = new JsonObject();
			snapshot.addProperty("boss", bossName);
			snapshot.addProperty("detectorType", detector.getDetectorType().name());
			snapshot.addProperty("detectorVersion", detector.getDetectorVersion());
			snapshot.addProperty("completedAttempts", stats.getCompletedAttempts());
			snapshot.addProperty("verified", stats.getVerified());
			snapshot.addProperty("unresolved", stats.getUnresolved());
			snapshot.addProperty("ambiguous", stats.getAmbiguous());
			snapshot.addProperty("verificationRate", stats.getVerificationRate());
			snapshot.addProperty("pauseThreshold", VerificationReliabilityState.FAILURE_THRESHOLD);
			snapshot.addProperty("thresholdBasis", "consecutive_verification_failures");
			snapshot.addProperty("updatedAt", System.currentTimeMillis());
			verificationTelemetry.add(key, snapshot);
			trimVerificationTelemetry();
			configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, VERIFICATION_TELEMETRY_KEY, gson.toJson(verificationTelemetry));
			log.debug("Huntmaster local verification telemetry: {}", snapshot);
		}
		catch (RuntimeException ex)
		{
			log.debug("Huntmaster could not update local verification telemetry", ex);
		}
	}

	private void recoverConnectionIfReady()
	{
		// A healthy endpoint alone cannot reconcile undelivered events.
		if (!healthReachable || assignmentSyncRequired || !pendingKcEvents.isEmpty()
			|| hasPendingBetaCredit() || outage.getStartedAt() == 0)
		{
			return;
		}
		if (trackingPaused)
		{
			refreshDetectorBaselines();
		}
		outage.recover();
		configManager.unsetConfiguration(HUNTMASTER_CONFIG_GROUP, OUTAGE_STARTED_KEY);
		trackingPaused = false;
		outageNoticeStage = 0;
		recoveryNoticePending = true;
		log.debug("Huntmaster connection restored; saved queue drained and tracking resumed");
		updateOutageTracking();
	}

	private static boolean sameRsn(String first, String second)
	{
		return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
	}

	private boolean matchesAssignedBoss(String bossName)
	{
		return assignedBoss != null && bossName.trim().equalsIgnoreCase(assignedBoss.trim());
	}

	private void loadAssignmentCache()
	{
		assignmentRsn = null;
		assignmentId = null;
		assignedBoss = null;
		try
		{
			String saved = configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, ASSIGNMENT_CACHE_KEY);
			JsonObject cache = saved == null ? null : gson.fromJson(saved, JsonObject.class);
			if (cache != null)
			{
				assignmentRsn = cache.get("rsn").getAsString();
				assignmentId = cache.get("id").getAsString();
				assignedBoss = cache.get("boss").getAsString();
				UUID.fromString(assignmentId);
				if (assignmentRsn.trim().isEmpty() || assignedBoss.trim().isEmpty())
				{
					throw new IllegalArgumentException("Incomplete assignment cache");
				}
			}
		}
		catch (RuntimeException ex)
		{
			assignmentRsn = null;
			assignmentId = null;
			assignedBoss = null;
			log.debug("Huntmaster ignored malformed assignment cache", ex);
		}
	}

	private void refreshAssignment()
	{
		if (!encounterCommunicationEnabled()) return;
		Player player = client.getLocalPlayer();
		if (assignmentInFlight || client.getGameState() != GameState.LOGGED_IN
				|| player == null || player.getName() == null)
		{
			return;
		}
		String rsn = player.getName();
		assignmentInFlight = true;
		HttpUrl url = HttpUrl.parse(HUNTMASTER_API_BASE_URL + "/api/runelite/assignment")
				.newBuilder().addQueryParameter("rsn", rsn).build();
		enqueueRequest(new Request.Builder().url(url).header("Cache-Control", "no-cache").build(), (status, body) ->
		{
			assignmentInFlight = false;
			Player currentPlayer = client.getLocalPlayer();
			if (currentPlayer == null || !sameRsn(rsn, currentPlayer.getName()))
			{
				return;
			}
			try
			{
				JsonObject state = gson.fromJson(body, JsonObject.class);
				if (status != 200 || state == null || !state.get("success").getAsBoolean()
						|| !sameRsn(rsn, state.get("rsn").getAsString()))
				{
					throw new IllegalArgumentException("Invalid assignment state");
				}
				String mode = state.get("status").getAsString();
				String newId = null;
				String newBoss = null;
				if ("solo".equals(mode) || "group".equals(mode))
				{
					JsonObject task = state.getAsJsonObject("assignment");
					newId = task.get("id").getAsString();
					newBoss = task.get("boss").getAsString();
					if (newId.isEmpty() || newBoss.isEmpty())
					{
						throw new IllegalArgumentException("Incomplete solo assignment");
					}
				}
				else if (!"no_assignment".equals(mode) && !"unregistered".equals(mode) && !"group_unsupported".equals(mode))
				{
					throw new IllegalArgumentException("Unknown assignment mode");
				}
				boolean changed = !sameRsn(rsn, assignmentRsn) || !java.util.Objects.equals(newId, assignmentId);
				registrationConfirmed = !"unregistered".equals(mode);
				if (registrationConfirmed) { membershipDenied = false; linkNoticeShown = false; }
				assignmentRsn = rsn;
				if (changed && reportsEnabled) diagnostic(() -> interruptEncounterCaptures(client.getTickCount(), EncounterObservation.Reason.ASSIGNMENT_CHANGED));
				if (!registrationConfirmed)
				{
					clearEncounterCaptures();
					for (EncounterReportQueue.Entry queued : reportQueue.snapshot())
					{
						try
						{
							JsonObject report = gson.fromJson(queued.payload, JsonObject.class);
							if (sameRsn(rsn, report.get("rsn").getAsString())) reportQueue.acknowledge(queued.id);
						}
						catch (RuntimeException ex) { reportQueue.acknowledge(queued.id); }
					}
					saveEncounterReports();
				}
				assignmentId = newId;
				assignedBoss = newBoss;
				assignmentSyncRequired = false;
				if (newId == null)
				{
					configManager.unsetConfiguration(HUNTMASTER_CONFIG_GROUP, ASSIGNMENT_CACHE_KEY);
				}
				else
				{
					JsonObject cache = new JsonObject();
					cache.addProperty("rsn", rsn);
					cache.addProperty("id", newId);
					cache.addProperty("boss", newBoss);
					configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, ASSIGNMENT_CACHE_KEY, gson.toJson(cache));
				}
				if (changed)
				{
					refreshDetectorBaselines();
					log.debug("Huntmaster assignment synchronized: mode {}, boss {}, id {}", mode, newBoss, newId);
				}
				recoverConnectionIfReady();
			}
			catch (RuntimeException ex)
			{
				log.debug("Huntmaster assignment synchronization failed", ex);
				markConnectionFailure();
			}
		});
	}

	KcBaselineStore createBaselineStore()
	{
		return new KcBaselineStore(() -> getPluginDirectory().join("kc-baselines"));
	}

	private Integer baselineWithCheckpoint(BossDetector detector, Integer saved)
	{
		if (!java.util.Objects.equals(baselineProfile, configManager.getRSProfileKey())) return saved;
		Integer observed = observedBaselines.get(detector.getProfileKey());
		return KcBaselineStore.latest(saved, observed);
	}

	private void checkpointObservedKc(BossDetector detector, int total)
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null || baselineStore == null) return;
		if (!java.util.Objects.equals(profile, baselineProfile)) loadObservedBaselines();
		Integer previous = observedBaselines.get(detector.getProfileKey());
		if (previous != null && total <= previous) return;
		observedBaselines.put(detector.getProfileKey(), total);
		baselineStore.save(profile, detector.getProfileKey(), total);
	}

	private void loadObservedBaselines()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null || baselineStore == null) return;
		if (java.util.Objects.equals(profile, baselineProfile)) return;
		baselineProfile = profile;
		observedBaselines.clear();
		long session = lifecycle;
		java.util.List<String> keys = new java.util.ArrayList<>();
		for (BossDetector detector : bossDetectors)
			if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC) keys.add(detector.getProfileKey());
		baselineStore.load(profile, keys, totals -> clientThread.invokeLater(() -> {
			if (!running || lifecycle != session || !java.util.Objects.equals(profile, configManager.getRSProfileKey())) return;
			totals.forEach((key, total) -> observedBaselines.merge(key, total, Math::max));
			for (BossDetector detector : bossDetectors)
			{
				if (detector.getDetectorType() != BossDetectorType.STANDARD_NPC) continue;
				Integer baseline = baselineWithCheckpoint(detector, detector.getLastKc());
				detector.setLastKc(baseline);
			}
			log.debug("Huntmaster restored local observed KC checkpoints");
		}));
	}

	private void refreshDetectorBaselines()
	{
		loadObservedBaselines();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		for (BossDetector detector : bossDetectors)
			refreshDetectorBaseline(detector, loggedIn);
	}

	private void refreshDetectorBaseline(BossDetector detector, boolean loggedIn)
	{
		detector.resetVerification();
		detector.getState().setLastVerifiedTick(-1);
		Integer baseline = null;
		if (loggedIn)
		{
			if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC)
			{
				baseline = baselineWithCheckpoint(detector, configManager.getRSProfileConfiguration("killcount", detector.getProfileKey(), int.class));
			}
			else if (detector.getCompletionVarpId() != null)
			{
				baseline = client.getVarpValue(detector.getCompletionVarpId());
			}
		}
		detector.setLastKc(baseline);
	}

	private boolean hasPendingBetaCredit()
	{
		for (EncounterReportQueue.Entry entry : reportQueue.snapshot())
		{
			if (!entry.creditCandidate) continue;
			try
			{
				JsonObject report = gson.fromJson(entry.payload, JsonObject.class);
				if (sameRsn(assignmentRsn, report.get("rsn").getAsString())) return true;
			}
			catch (RuntimeException ex) { log.debug("Huntmaster malformed pending beta credit", ex); }
		}
		return false;
	}
	private static boolean isBetaCredit(JsonObject report)
	{
		return report.has("trackingMode") && "beta_candidate".equals(report.get("trackingMode").getAsString());
	}

	private boolean encounterCommunicationEnabled()
	{
		// Plugin enable/disable is the sole communication switch. Legacy consent keys are ignored.
		return running && HUNTMASTER_API_BASE_URL != null;
	}

	private void resetLinkRequests()
	{
		++requestGeneration;
		accountSnapshotInFlight = false;
		nextAccountSnapshotAt = 0;
		for (Call call : activeCalls) call.cancel();
		reportQueue.releaseInFlight();
		for (PendingKcEvent pending : pendingKcEvents.values()) pending.inFlight = false;
		healthInFlight = false;
		healthReachable = false;
		assignmentInFlight = false;
		assignmentSyncRequired = true;
		registrationConfirmed = false;
	}
	private void rejectLink()
	{
		membershipDenied = true;
		// Membership rejection is retried through registration refresh; no credentials.
		resetLinkRequests();
		clearEncounterCaptures();
		saveEncounterReports();
		savePendingKcEvents();
	}
	private void showLinkNotice()
	{
		if (!running || HUNTMASTER_API_BASE_URL == null || !membershipDenied
			|| linkNoticeShown || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
		linkNoticeShown = true;
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster could not confirm your RSN belongs to an active Bosscape member. Check your registration in Bosscape Discord. Tracking is paused; saved reports are preserved and the connection will retry.", null);
	}

	private void observeAssignedSpecialTotal()
	{
		if (!canTrackNewKills()) { assignedTotalTracker.clear(); return; }
		BossDetector detector = genericObservationDetector();
		Integer varp = detector == null ? null : SpecialEncounterTotals.varp(detector.getName());
		if (varp == null || !canObserve(detector)) { assignedTotalTracker.clear(); return; }
		int total = client.getVarpValue(varp);
		Integer previous = assignedTotalTracker.changed(client.getLocalPlayer().getName() + ":" + assignmentId + ":" + detector.getName(), total, client.getTickCount());
		if (previous == null) return;
		observePrimary(detector, EncounterObservation.SignalKind.COMPLETION);
		observeCounter(detector, EncounterObservation.CounterSource.COMPLETION_VARP, previous, total);
		if ((long) total - previous != 1) encounterCapture.uncertain(EncounterObservation.Outcome.AMBIGUOUS);
	}
	private boolean canCollectDagannoth()
	{
		return reportsEnabled && assignmentId != null && client.getLocalPlayer() != null
			&& "Dagannoth Kings".equalsIgnoreCase(assignedBoss);
	}
	private boolean collectDagannothNpc(String name, boolean loot)
	{
		return canCollectDagannoth() && dagannothCapture.npc(name, loot, client.getLocalPlayer().getName(),
			assignmentId, client.getTickCount(), System.currentTimeMillis());
	}
	private void interruptEncounterCaptures(int tick, EncounterObservation.Reason reason)
	{
		encounterCapture.interrupt(tick, reason);
		dagannothCapture.interrupt(tick, reason);
		assignedTotalTracker.clear();
	}
	private void clearEncounterCaptures()
	{
		encounterCapture.clear();
		dagannothCapture.clear();
		assignedTotalTracker.clear();
		chestRewardInventory = -1;
		chestRewardTick = -1;
		chestRewardAssignment = null;
	}
	private boolean canObserve(BossDetector detector)
	{
		return reportsEnabled && assignmentId != null && client.getLocalPlayer() != null
			&& matchesAssignedBoss(detector.getName()) && detector.getDetectorType() != BossDetectorType.SPECIAL;
	}
	private void observePrimary(BossDetector detector, EncounterObservation.SignalKind kind)
	{
		if (canObserve(detector)) diagnostic(() -> encounterCapture.primary(detector, client.getLocalPlayer().getName(), assignmentId,
			client.getTickCount(), System.currentTimeMillis(), kind));
	}
	private void observeCounter(BossDetector detector, EncounterObservation.CounterSource source, Integer previous, int total)
	{
		if (canObserve(detector)) diagnostic(() -> encounterCapture.counter(detector, client.getLocalPlayer().getName(), assignmentId,
			client.getTickCount(), System.currentTimeMillis(), source, previous, total));
	}
	private void observeLoot(BossDetector detector)
	{
		if (canObserve(detector)) diagnostic(() -> encounterCapture.loot(detector, client.getLocalPlayer().getName(), assignmentId,
			client.getTickCount(), System.currentTimeMillis()));
	}
	private static EncounterObservation.Method observationMethod(String method)
	{
		switch (method)
		{
			case "completion message + completion total increase": return EncounterObservation.Method.COMPLETION_AND_COUNTER;
			case "activity completion message + activity count increase": return EncounterObservation.Method.ACTIVITY_AND_COUNTER;
			case "death event + loot": return EncounterObservation.Method.DEATH_AND_LOOT;
			case "KC increase + loot": return EncounterObservation.Method.COUNTER_AND_LOOT;
			default: return EncounterObservation.Method.DEATH_AND_COUNTER;
		}
	}
	private void queueEncounterReport(EncounterObservation.Snapshot snapshot)
	{
		if (!reportsEnabled) return;
		try
		{
			JsonObject report = EncounterReportCodec.encode(snapshot);
			String payload = report.toString();
			boolean added = reportQueue.add(snapshot.reportId.toString(), payload, snapshot.observedAt,
				System.currentTimeMillis(), isBetaCredit(report));
			log.debug("Huntmaster encounter report {} finalized: {} (queued={})", snapshot.reportId, snapshot.outcome, added);
			if (added) saveEncounterReports();
		}
		catch (RuntimeException ex) { log.debug("Huntmaster diagnostic capture could not be queued", ex); }
	}
	private void saveEncounterReports()
	{
		if (savedReportRevision == reportQueue.revision()) return;
		try
		{
			configManager.setConfiguration(HUNTMASTER_CONFIG_GROUP, REPORT_QUEUE_KEY, gson.toJson(reportQueue.snapshot()));
			savedReportRevision = reportQueue.revision();
		}
		catch (RuntimeException ex) { log.debug("Huntmaster could not persist diagnostic reports", ex); }
	}
	private void loadEncounterReports()
	{
		savedReportRevision = -1;
		reportQueue.clear();
		// Restore saved delivery events regardless of the retired sharing preference.
		{
			String saved = configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, REPORT_QUEUE_KEY);
			try
			{
				if (saved != null)
				{
					EncounterReportQueue.Entry[] entries = gson.fromJson(saved, EncounterReportQueue.Entry[].class);
					if (entries != null) for (EncounterReportQueue.Entry entry : entries)
					{
						if (entry == null) continue;
						try
						{
							JsonObject body = gson.fromJson(entry.payload, JsonObject.class);
							if (!UUID.fromString(entry.id).toString().equals(body.get("reportId").getAsString())) continue;
							reportQueue.add(entry.id, entry.payload, entry.createdAt, System.currentTimeMillis(), isBetaCredit(body));
						}
						catch (RuntimeException ex) { log.debug("Huntmaster ignored malformed diagnostic queue entry", ex); }
					}
				}
			}
			catch (RuntimeException ex) { log.debug("Huntmaster ignored malformed diagnostic queue", ex); }
		}
		saveEncounterReports();
	}
	private void updateEncounterReports()
	{
		if (!encounterCommunicationEnabled()) return;
		Player reportPlayer = client.getLocalPlayer();
		if (!reportsEnabled || !registrationConfirmed || assignmentSyncRequired || !healthReachable
				|| reportPlayer == null || !sameRsn(reportPlayer.getName(), assignmentRsn)) return;
		EncounterReportQueue.Entry entry = reportQueue.next(System.currentTimeMillis());
		saveEncounterReports();
		if (entry == null) return;
		try
		{
			JsonObject report = gson.fromJson(entry.payload, JsonObject.class);
			if (!sameRsn(reportPlayer.getName(), report.get("rsn").getAsString()))
			{
				entry.nextAttemptAt = System.currentTimeMillis() + 60000;
				return;
			}
		}
		catch (RuntimeException ex) { reportQueue.acknowledge(entry.id); saveEncounterReports(); return; }
		entry.inFlight = true;
		Request request = new Request.Builder().url(HUNTMASTER_API_BASE_URL + "/api/runelite/encounter-reports")
			.post(RequestBody.create(MediaType.parse("application/json"), entry.payload)).build();
		enqueueRequest(request, (status, response) ->
		{
			entry.inFlight = false;
			boolean accepted = false;
			try
			{
				if (status == 200)
				{
					JsonObject result = gson.fromJson(response, JsonObject.class);
					String resultStatus = result.get("status").getAsString();
					accepted = entry.id.equals(result.get("reportId").getAsString())
						&& ("stored".equals(resultStatus) || "duplicate".equals(resultStatus));
				}
			}
			catch (RuntimeException ex) { log.debug("Huntmaster invalid diagnostic acknowledgement", ex); }
			// Old bots return 404: retain until expiry instead of impacting KC.
			if (accepted || status == 400 || status == 409 || status == 413) reportQueue.acknowledge(entry.id);
			else
			{
				entry.nextAttemptAt = System.currentTimeMillis() + 60_000L;
				if (entry.creditCandidate) markConnectionFailure();
			}
			log.debug("Huntmaster encounter report {} response {} accepted={}", entry.id, status, accepted);
			saveEncounterReports();
			recoverConnectionIfReady();
		});
	}


	private void diagnostic(Runnable action)
	{
		try { action.run(); }
		catch (RuntimeException ex) { log.debug("Huntmaster diagnostic capture failed without affecting KC", ex); }
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"killcount".equals(event.getGroup())) return;
		final long session = lifecycle;
		clientThread.invokeLater(() ->
		{
			if (!running || lifecycle != session || !canTrackNewKills() || !encounterCapture.hasActiveCapture()) return;
			for (BossDetector detector : bossDetectors)
			{
				if (detector.getDetectorType() != BossDetectorType.STANDARD_NPC
					|| !event.getKey().equals(detector.getProfileKey()) || !canObserve(detector)) continue;
				diagnostic(() ->
				{
					Integer total = configManager.getRSProfileConfiguration("killcount", detector.getProfileKey(), int.class);
					// Capture profile changes as supporting evidence, never as a new verification baseline.
					if (total != null) observeCounter(detector, EncounterObservation.CounterSource.RS_PROFILE, detector.getLastKc(), total);
				});
			}
		});
	}

	private boolean evidenceOnlyBoss(String boss)
	{
		BossDetector detector = registeredDetector(boss);
		return detector != null && detector.getDefinition().isEvidenceOnly();
	}
	private void observeEvidenceOnlyMessage(BossDetector detector, String message)
	{
		Integer total = EvidenceOnlyCounter.parse(message, detector.getKcMessagePrefix());
		if (total == null) return;
		checkpointObservedKc(detector, total);
		Integer previous = detector.getLastKc();
		observeCounter(detector, EncounterObservation.CounterSource.KC_MESSAGE, previous, total);
		if (previous != null && (long) total - previous > 1)
			diagnostic(() -> encounterCapture.uncertain(EncounterObservation.Outcome.AMBIGUOUS));
		detector.setLastKc(total);
	}
}
