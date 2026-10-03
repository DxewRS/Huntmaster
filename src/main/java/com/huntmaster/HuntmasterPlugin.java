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
	@Inject private net.runelite.client.ui.ClientToolbar clientToolbar;
	@Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
	@Inject private BosscapeSettings settings;
	private final AssignmentDashboardState dashboard = new AssignmentDashboardState();
	private volatile HuntmasterPanel dashboardPanel;
	private net.runelite.client.ui.NavigationButton dashboardNavigation;
	private AssignmentProgressOverlay assignmentOverlay;
	private AssignmentDashboardState.View publishedDashboard;
	private String dashboardAccount;
	private void startDashboard(long session)
	{
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (!running || lifecycle != session) return;
			dashboardPanel = new HuntmasterPanel();
			dashboardNavigation = net.runelite.client.ui.NavigationButton.builder().tooltip("Huntmaster").priority(7)
				.icon(net.runelite.client.util.ImageUtil.resizeImage(net.runelite.client.util.ImageUtil.loadImageResource(HuntmasterPlugin.class,"panel_icon.png"),24,24))
				.panel(dashboardPanel).build();
			clientToolbar.addNavigation(dashboardNavigation);
			assignmentOverlay = new AssignmentProgressOverlay(this,client,dashboard,settings);
			overlayManager.add(assignmentOverlay);
			dashboardPanel.update(dashboard.view());
		});
	}
	private void stopDashboard()
	{
        collectorDiagnostics.reset();publishedDiagnostics=null;
		dashboardAccount=null; dashboard.reset(null,System.currentTimeMillis()); publishedDashboard=null;
		javax.swing.SwingUtilities.invokeLater(() -> {
			if(dashboardNavigation!=null)clientToolbar.removeNavigation(dashboardNavigation);
			if(assignmentOverlay!=null)overlayManager.remove(assignmentOverlay);
			dashboardNavigation=null; assignmentOverlay=null; dashboardPanel=null;
		});
	}
	private void dashboardAccount(String rsn)
	{
		if(!sameRsn(rsn,dashboardAccount)) { dashboardAccount=rsn;dashboard.reset(rsn,System.currentTimeMillis()); }
	}
	private void publishDashboard()
	{
        publishDiagnostics();
		if(client.getGameState()!=GameState.LOGGED_IN)dashboard.connection("Log in to RuneScape");
		else if(membershipDenied)dashboard.connection("Register your RSN in Bosscape");
		else if(!healthReachable)dashboard.connection(outage.getStartedAt()==0?"Connecting...":"Huntmaster unavailable");
		AssignmentDashboardState.View view=dashboard.view();
		if(view==publishedDashboard)return;
		publishedDashboard=view;long session=lifecycle;
		javax.swing.SwingUtilities.invokeLater(() -> { if(running && lifecycle==session && dashboardPanel!=null)dashboardPanel.update(view); });
	}
	@Provides BosscapeSettings provideBosscapeSettings(ConfigManager manager)
	{ return manager.getConfig(BosscapeSettings.class); }
	private final ServerVerification serverVerification = new ServerVerification();
    private final CollectorDiagnostics collectorDiagnostics=new CollectorDiagnostics();
    private String publishedDiagnostics;
    private void publishDiagnostics()
    {
        if(dashboardPanel==null)return;
        String state=client.getGameState()!=GameState.LOGGED_IN?"Log in to collect evidence"
            :membershipDenied?"Registration or membership required"
            :!healthReachable?"Disconnected; saved reports retained"
            :!registrationConfirmed||assignmentSyncRequired?"Checking registration and assignment"
            :collectionPolicy.updateRequired()?"Plugin update required through RuneLite"
            :!collectionPolicy.supported()?"Waiting for a compatible Huntmaster bot"
            :reportQueue.collectionBackpressured()?"Collection paused: report queue full"
            :trackingPaused?"Collection paused during connection recovery":"Evidence collection active";
        String summary=collectorDiagnostics.summary();
        String copy=collectorDiagnostics.copy(state,reportQueue.snapshot().length,collectionPolicy.revision(System.currentTimeMillis()));
        if(copy.equals(publishedDiagnostics))return;
        publishedDiagnostics=copy;long session=lifecycle;
        javax.swing.SwingUtilities.invokeLater(()->{if(running&&lifecycle==session&&dashboardPanel!=null)dashboardPanel.diagnostics(state,summary,copy);});
    }
    private final CollectionPolicy collectionPolicy = new CollectionPolicy();
    private String collectionSession = UUID.randomUUID().toString();
    private long nextCollectionHealthAt, collectionSignals, collectionCaptured, collectionDelivered;
    private long collectionDeliveryFailures, collectionCaptureFailures, lastCollectionSignalAt;
    private void updateCollectionHealth()
    {
        long now = System.currentTimeMillis();
        Player player = client.getLocalPlayer();
        if (!running || !collectionPolicy.supported() || !registrationConfirmed || !healthReachable
            || assignmentSyncRequired || player == null || client.getGameState() != GameState.LOGGED_IN
            || !sameRsn(player.getName(), assignmentRsn) || now < nextCollectionHealthAt) return;
        nextCollectionHealthAt = now + 60_000L;
        JsonObject body = new JsonObject();
        body.addProperty("rsn", player.getName()); body.addProperty("version", 2);
        body.addProperty("session", collectionSession);
        body.addProperty("state", reportQueue.collectionBackpressured() ? "backlog" : trackingPaused ? "paused" : "collecting");
        body.addProperty("signals", collectionSignals); body.addProperty("captured", collectionCaptured);
        body.addProperty("delivered", collectionDelivered); body.addProperty("deliveryFailures", collectionDeliveryFailures);
        body.addProperty("captureFailures", collectionCaptureFailures); body.addProperty("queued", reportQueue.snapshot().length);
        body.addProperty("lastSignalAt", lastCollectionSignalAt);
        enqueueRequest(new Request.Builder().url(HUNTMASTER_API_BASE_URL + "/api/runelite/collection-health")
            .post(RequestBody.create(MediaType.parse("application/json"), body.toString())).build(), (status, response) -> {}, false);
    }
	private void updateServerVerification()
	{
		Player player = client.getLocalPlayer();
		if (!running || !registrationConfirmed || !healthReachable || assignmentSyncRequired
			|| client.getGameState() != GameState.LOGGED_IN || player == null || assignmentId == null) return;
		ServerVerification.Poll poll = serverVerification.begin(player.getName(), assignmentId, System.currentTimeMillis());
		if (poll == null) return;
		HttpUrl url = HttpUrl.get(HUNTMASTER_API_BASE_URL + "/api/runelite/verification-status").newBuilder()
			.addQueryParameter("rsn", poll.rsn).addQueryParameter("session", poll.session).addQueryParameter("collectorVersion", "2").build();
		enqueueRequest(new Request.Builder().url(url).header("Cache-Control", "no-cache").build(), (status, body) ->
		{
			JsonObject response = null;
			try { if (status == 200 && body.length() <= 8192) response = gson.fromJson(body, JsonObject.class); }
			catch (RuntimeException ignored) { }
			if (!serverVerification.isCurrent(poll)) return;
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
				|| !sameRsn(client.getLocalPlayer().getName(), poll.rsn)) { serverVerification.release(); return; }
			if (!serverVerification.complete(poll, response, assignmentId)) { if (response == null) markConnectionFailure(); return; }
			boolean wasPaused = reliability.isPaused();
			reliability.restore(collectionPolicy.supported() ? 0 : serverVerification.failures());
			if (wasPaused != reliability.isPaused()) refreshDetectorBaselines();
			String notice = serverVerification.takeNotice();
			if (notice != null) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", notice, null);
		});
	}
	private long nextAccountSnapshotAt;
	private int accountSnapshotLoginTick;
	private boolean accountSnapshotInFlight;
	private final RecruitmentNotifications recruitmentNotifications = new RecruitmentNotifications();

	private void updateRecruitmentNotifications()
	{
		Player player = client.getLocalPlayer();
		if (!running || !encounterCommunicationEnabled() || client.getGameState() != GameState.LOGGED_IN || player == null)
		{ recruitmentNotifications.reset(); return; }
		Set<String> selected = new java.util.HashSet<>();
		for (RecruitmentActivity activity : RecruitmentActivity.values())
			if (Boolean.parseBoolean(configManager.getConfiguration(HUNTMASTER_CONFIG_GROUP, activity.configKey()))) selected.add(activity.id);
		RecruitmentNotifications.Poll poll = recruitmentNotifications.begin(player.getName(), selected, System.currentTimeMillis());
		if (poll == null) return;
		HttpUrl.Builder url = HttpUrl.get(HUNTMASTER_API_BASE_URL + "/api/runelite/recruitment").newBuilder()
			.addQueryParameter("rsn", poll.rsn);
		if (poll.cursor != null) url.addQueryParameter("cursor", poll.cursor);
		enqueueRequest(new Request.Builder().url(url.build()).header("Cache-Control", "no-cache").build(), (status, body) ->
		{
			JsonObject response = null;
			try { if (status == 200 && body.length() <= 65536) response = gson.fromJson(body, JsonObject.class); }
			catch (RuntimeException ignored) { /* A failed poll resets the live edge. */ }
			if (!recruitmentNotifications.isCurrent(poll)) return;
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
				|| !sameRsn(client.getLocalPlayer().getName(), poll.rsn)) { recruitmentNotifications.reset(); return; }
			for (String message : recruitmentNotifications.complete(poll, response, System.currentTimeMillis()))
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
		}, false);
	}

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
	private boolean registrationConfirmed;
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
	private long nextHealthCheckAt;
	private boolean healthInFlight;
	private boolean healthReachable;
	private boolean trackingPaused;
	private boolean backlogNoticeShown;
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
	private final EncounterReportQueue reportQueue = new EncounterReportQueue();
	private long savedReportRevision = -1;
	private long requestGeneration;
	private final EncounterCapture encounterCapture = new EncounterCapture(this::queueEncounterReport);
	private final DagannothComponentCollector dagannothCapture = new DagannothComponentCollector(this::queueEncounterReport);
	private boolean reportsEnabled;

	private final PluginLink pluginLink = new PluginLink();
	private boolean linkNoticeShown;
	private boolean membershipDenied;

	private static final String REPORT_QUEUE_KEY = "pendingEncounterReports";

	// ==================================================
	// SESSION STATE
	// ==================================================


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
		BossRegistry.validateDetectors(bossDetectors);
		recruitmentNotifications.reset();
		serverVerification.reset();
		nextHealthCheckAt = 0;
		backlogNoticeShown = false;
		membershipDenied = false;
		accountSnapshotInFlight = false;
		nextAccountSnapshotAt = System.currentTimeMillis() + 10_000L;
		accountSnapshotLoginTick = client.getTickCount();
		linkNoticeShown = false;
		connectionInitialized = false;
		if (HUNTMASTER_API_BASE_URL == null)
		{
			log.warn("Huntmaster bot HTTPS endpoint is not configured; public communication is unavailable");
		}
		baselineStore = createBaselineStore();
		observedBaselines.clear();
		baselineProfile = null;
		registrationConfirmed = false;
		running = true;
		long session = ++lifecycle;
		dashboardAccount=null;dashboard.reset(null,System.currentTimeMillis());publishedDashboard=null;
		startDashboard(session);
		httpClient = okHttpClient.newBuilder()
				.followRedirects(false)
				.followSslRedirects(false)
				.connectTimeout(HUNTMASTER_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(HUNTMASTER_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.callTimeout(HUNTMASTER_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.build();

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
			logoutObserved = client.getGameState() == GameState.LOGIN_SCREEN;
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
					diagnostic(this::updateRecruitmentNotifications);
					publishDashboard();
				}
			}), 0, HUNTMASTER_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
		});

	}

	// ==================================================
	// PLUGIN SHUTDOWN
	// ==================================================

	@Override
	protected void shutDown()
	{
		// Finalize and persist captures, but never start delivery during shutdown.
		running = false;
		recruitmentNotifications.reset();
		if (reportsEnabled) diagnostic(() -> interruptEncounterCaptures(client.getTickCount(), EncounterObservation.Reason.SHUTDOWN));
		saveEncounterReports();
		clearEncounterCaptures();
		reportsEnabled = false;
		if (baselineStore != null) { baselineStore.close(); baselineStore = null; }
		++lifecycle;
		stopDashboard();
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
		serverVerification.reset();
		connectionTask = null;
		connectionExecutor = null;
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
		publishDashboard();
		showLinkNotice();
        encounterCapture.windows(collectionPolicy.windows(System.currentTimeMillis()));
        encounterCapture.revision(collectionPolicy.revision(System.currentTimeMillis()));
        diagnostic(this::updateCollectionHealth);
		if (reportsEnabled) diagnostic(() -> { encounterCapture.advance(client.getTickCount()); dagannothCapture.advance(client.getTickCount()); });
		updateVerificationRecovery();
		diagnostic(this::updateServerVerification);
		updateOutageTracking();
		showOutageNotices();
		if (!reportQueue.collectionBackpressured()) backlogNoticeShown = false;
		else if (!backlogNoticeShown && client.getGameState() == GameState.LOGGED_IN)
		{
			backlogNoticeShown = true;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster: Evidence storage is full. New tracking is paused until saved reports can be delivered. Saved reports are preserved.", null);
		}
		if (!canTrackNewKills())
		{
			loadObservedBaselines();
			BossDetector pausedDetector = registeredDetector(assignedBoss);
			if (pausedDetector != null) refreshDetectorBaseline(pausedDetector, client.getGameState() == GameState.LOGGED_IN);
			return;
		}
  for(BossDetector detector:observationDetectors()) {
   int mask=ChestRunState.read(detector.getName(),client::getVarbitValue);
   if(mask>=0)chestStates.computeIfAbsent(detector.getName(),k->new ChestRunState()).sample(assignmentId+":"+detector.getName(),mask,client.getTickCount());
   if(detector.getCompletionVarpId()!=null)handleTotalGameTick(detector);
   else {
    Integer varp=SpecialEncounterTotals.varp(detector.getName());
    if(varp!=null) {
     int total=client.getVarpValue(varp);Integer previous=detector.getLastKc();
     if(previous!=null && total!=previous){observePrimary(detector,EncounterObservation.SignalKind.COMPLETION);observeCounter(detector,EncounterObservation.CounterSource.COMPLETION_VARP,previous,total);}
     detector.setLastKc(total);
    }
   }
  }
 }

	private void handleTotalGameTick(BossDetector detector)
	{
		Integer varp=detector.getCompletionVarpId();
		if (varp==null || client.getGameState()!=GameState.LOGGED_IN) return;
		int total=client.getVarpValue(varp);
		Integer previous=detector.getLastKc();
		if (previous!=null && total!=previous)
        {
            if (detector.getDefinition().isEvidenceOnly()) observePrimary(detector, EncounterObservation.SignalKind.COMPLETION);
            observeCounter(detector, EncounterObservation.CounterSource.COMPLETION_VARP, previous, total);
        }
		detector.setLastKc(total);
	}

@Subscribe
public void onRuneScapeProfileChanged(
		RuneScapeProfileChanged event)
{
    collectorDiagnostics.reset();publishedDiagnostics=null;publishDiagnostics();
	recruitmentNotifications.reset();
	loadObservedBaselines();
	for (BossDetector detector : observationDetectors())
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

		if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC) { observePrimary(detector, EncounterObservation.SignalKind.DEATH); encounterCapture.diagnostic("NPC_DEATH_ID",npc.getId(),client.getTickCount()); }
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

		if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC) { observeLoot(detector); encounterCapture.diagnostic("NPC_LOOT_ID",npc.getId(),client.getTickCount()); }
	}

	@Subscribe
	public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded event)
	{
		if (!canTrackNewKills()) return;
		BossDetector detector = null;
		for(BossDetector d:observationDetectors())if(ChestCompletionAdapter.rewardInventory(d.getName(),event.getGroupId())>=0){detector=d;break;}
		if (detector == null) return;
		chestDetector=detector;
		int inventory = ChestCompletionAdapter.rewardInventory(detector.getName(), event.getGroupId());
		if (inventory < 0) return;
		int[] chestState = chestStates.computeIfAbsent(detector.getName(),k->new ChestRunState()).consume(assignmentId + ":" + detector.getName(), ChestRunState.read(detector.getName(), client::getVarbitValue), client.getTickCount());
		chestRewardInventory = inventory;
		chestRewardTick = client.getTickCount();
		chestRewardAssignment = assignmentId;
		observePrimary(detector, EncounterObservation.SignalKind.COMPLETION);
		if (chestState != null && canObserve(detector)) diagnostic(() -> encounterCapture.chestState(chestState[0], chestState[1], client.getTickCount()));
		observeChestLoot(detector, client.getItemContainer(inventory));
	}

	@Subscribe
	public void onItemContainerChanged(net.runelite.api.events.ItemContainerChanged event)
	{
		if (!canTrackNewKills() || event.getContainerId() != chestRewardInventory
				|| !java.util.Objects.equals(assignmentId, chestRewardAssignment)
				|| client.getTickCount() < chestRewardTick || client.getTickCount() - chestRewardTick >= 10) return;
		BossDetector detector = chestDetector;
		if (detector != null) observeChestLoot(detector, event.getItemContainer());
	}

	private void observeChestLoot(BossDetector detector, net.runelite.api.ItemContainer container)
	{
		if (container == null) return;
		for (net.runelite.api.Item item : container.getItems())
			if (item.getId() >= 0 && item.getQuantity() > 0) { observeLoot(detector); return; }
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
		if (!CounterMessageEvidence.accepts(event.getType()))
		{
			return;
		}

		String message =
				event.getMessage();
		for(BossDetector d:observationDetectors())if(CounterMessageEvidence.related(message,d.getName()) && GenericKcRouter.parse(message,d.getName())==null)
			encounterCapture.notice(d,client.getLocalPlayer().getName(),assignmentId,client.getTickCount(),System.currentTimeMillis(),"UNPARSED_"+event.getType().name(),null);

		if (canCollectDagannoth() && dagannothCapture.counter(message, client.getLocalPlayer().getName(),
			assignmentId, client.getTickCount(), System.currentTimeMillis())) return;


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
			encounterCapture.diagnostic(event.getType().name(), GenericKcRouter.parse(message, detector.getName()), client.getTickCount());
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
				handleTotalKcMessage(detector);
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

	private void handleStandardNpcKcMessage(BossDetector detector, String message)
	{
		Integer total=GenericKcRouter.parse(message,detector.getName());
		if (total==null) return;
		observeCounter(detector, EncounterObservation.CounterSource.KC_MESSAGE, detector.getLastKc(), total);
		checkpointObservedKc(detector,total);
		detector.setLastKc(total);
	}

	private void handleTotalKcMessage(BossDetector detector)
	{
		observePrimary(detector, detector.getDetectorType()==BossDetectorType.ACTIVITY
			? EncounterObservation.SignalKind.ACTIVITY_COMPLETION : EncounterObservation.SignalKind.COMPLETION);
	}

 private BossDetector findDetectorByNpcName(String name) {
  for(BossDetector d:observationDetectors())if(BossNpcAliases.matches(d.getName(),name))return d;
  return null;
 }
 private final java.util.Map<String,BossDetector> collectionDetectors=new java.util.LinkedHashMap<>();
 private java.util.Collection<BossDetector> observationDetectors() {
  if(collectionDetectors.isEmpty()) {
   for(BossDetector d:bossDetectors)collectionDetectors.put(d.getName(),d);
   for(String name:SupportedEncounters.NAMES)if(!collectionDetectors.containsKey(name))collectionDetectors.put(name,
    SpecialEncounterTotals.varp(name)!=null?new BossDetector(BossDefinition.totalCounterCandidate(name,name.toLowerCase(java.util.Locale.ROOT))):new BossDetector(BossDefinition.betaCandidate(name,"The Nightmare".equals(name)?"nightmare":name.toLowerCase(java.util.Locale.ROOT),"Your "+name+" kill count is:")));
  }
  return collectionDetectors.values();
 }

 private BossDetector findDetectorByKcMessage(String message) {
  if(message==null)return null;
  for(BossDetector d:observationDetectors())if(GenericKcRouter.identifies(message,d.getName()))return d;
  return null;
 }

// ==================================================
// HANDLE VERIFIED KC
// ==================================================

	private void retryPendingKcEvents()
	{
		if (!encounterCommunicationEnabled()) return;
		Player player = client.getLocalPlayer();
		if (!registrationConfirmed || !healthReachable || assignmentSyncRequired
				|| client.getGameState() != GameState.LOGGED_IN || player == null
				|| !sameRsn(player.getName(), assignmentRsn)) return;
		if (
				pendingKcEvents.isEmpty()
		)
		{
			return;
		}

		if (pendingKcEvents.values().stream().anyMatch(entry -> entry.inFlight)) return;
		long now = System.currentTimeMillis();

		for (
				PendingKcEvent pendingEvent :
				pendingKcEvents.values()
		)
		{
			if (!sameRsn(player.getName(), pendingEvent.rsn)) continue;
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

			sendPendingKcEvent(pendingEvent);
			if (pendingEvent.inFlight) return;
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
				log.debug("Huntmaster KC event {} accepted", pendingEvent.eventId);
				refreshAssignment();
				recoverConnectionIfReady();
			}
			else if (HttpResponsePolicy.isPermanentRejection(status))
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
				log.debug("Huntmaster KC event {} rejected with HTTP {}", pendingEvent.eventId, status);
				refreshAssignment();
				recoverConnectionIfReady();
			}
			else
			{
				markConnectionFailure();
				schedulePendingKcRetry(pendingEvent, "HTTP " + status);
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

	// ==================================================
	// HUNTMASTER API CONNECTION TEST
	// ==================================================

	private void testHuntmasterApiConnection()
	{
		if (!encounterCommunicationEnabled() || client.getGameState() != GameState.LOGGED_IN
			|| client.getLocalPlayer() == null) return;
		if (healthInFlight || System.currentTimeMillis() < nextHealthCheckAt)
		{
			if (healthReachable && !healthInFlight) refreshAssignment();
			return;
		}
		healthInFlight = true;
		nextHealthCheckAt = System.currentTimeMillis() + (healthReachable ? 30_000L : HUNTMASTER_RETRY_DELAY_MS);
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
		enqueueRequest(request, completion, true);
	}

	private void enqueueRequest(Request request, BiConsumer<Integer, String> completion, boolean rejectAuthentication)
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
					finish(response.code(), HttpResponsePolicy.read(response.body()));
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
						if (rejectAuthentication && (status == 401 || status == 403)) { rejectLink(); return; }
						completion.accept(status, body);
					}
				});
			}
		});
	}

	private void markConnectionFailure()
	{
		nextHealthCheckAt = Math.min(nextHealthCheckAt, System.currentTimeMillis() + HUNTMASTER_RETRY_DELAY_MS);
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
		return client.getGameState() == GameState.LOGGED_IN && !membershipDenied
				&& !reportQueue.collectionBackpressured() && !trackingPaused && player != null && registrationConfirmed
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
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster RuneLite Plugin cannot reach the Huntmaster Discord Bot. Encounter evidence will be saved during the first 10 minutes of this outage for the bot to check after reconnection.", null);
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
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING) { dashboard.clearCompleted(); publishDashboard(); }
		if (event.getGameState() != GameState.LOGGED_IN) { recruitmentNotifications.reset(); serverVerification.release(); }
		if (event.getGameState() == GameState.LOGGED_IN) accountSnapshotLoginTick = client.getTickCount();
		nextAccountSnapshotAt = System.currentTimeMillis() + 10_000L;
		if (event.getGameState() == GameState.LOGIN_SCREEN) serverVerification.reset();
		if (running && event.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (reportsEnabled) diagnostic(() -> interruptEncounterCaptures(client.getTickCount(), EncounterObservation.Reason.LOGOUT));
			resetLinkRequests();
			logoutObserved = true;
		}
	}

	private void updateVerificationRecovery()
	{
		Player player = client.getLocalPlayer();
		if (!running || client.getGameState() != GameState.LOGGED_IN || player == null) return;
		if (!sameRsn(player.getName(), reliabilityRsn) || logoutObserved)
		{
			reliabilityRsn = player.getName();
			logoutObserved = false;
			reliability.restore(0);
			serverVerification.reset();
			refreshDetectorBaselines();
			assignmentSyncRequired = true;
			refreshAssignment();
		}
	}

	private void recoverConnectionIfReady()
	{
		// A healthy endpoint alone cannot reconcile undelivered events.
		if (outage.getStartedAt() == 0 || !healthReachable || assignmentSyncRequired || pendingKcEvents.values().stream().anyMatch(entry -> sameRsn(assignmentRsn, entry.rsn))
			|| hasPendingBetaCredit())
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
		dashboardAccount(rsn);
		assignmentInFlight = true;
		HttpUrl url = HttpUrl.get(HUNTMASTER_API_BASE_URL + "/api/runelite/assignment")
				.newBuilder().addQueryParameter("rsn", rsn).build();
		Request.Builder assignmentRequest=new Request.Builder().url(url).header("Cache-Control", "no-store");
		enqueueRequest(assignmentRequest.build(), (status, body) ->
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
                collectionPolicy.accept(state.has("collector") ? state.getAsJsonObject("collector") : null, System.currentTimeMillis());
				if (!"unregistered".equals(mode) && !"group_unsupported".equals(mode)
					&& !dashboard.accept(state,System.currentTimeMillis(),null,0)) return;
				String newId = null;
				String newBoss = null;
				if ("solo".equals(mode) || "group".equals(mode))
				{
					JsonObject task = state.getAsJsonObject("assignment");
					newId = task.get("id").getAsString();
					newBoss = task.get("boss").getAsString();
					UUID.fromString(newId);
					if (newBoss.trim().isEmpty() || newBoss.length() > 128)
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
				if (!registrationConfirmed) clearEncounterCaptures();
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
				publishDashboard();
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
		for (BossDetector detector : observationDetectors())
			if (detector.getDetectorType() == BossDetectorType.STANDARD_NPC) keys.add(detector.getProfileKey());
		baselineStore.load(profile, keys, totals -> clientThread.invokeLater(() -> {
			if (!running || lifecycle != session || !java.util.Objects.equals(profile, configManager.getRSProfileKey())) return;
			totals.forEach((key, total) -> observedBaselines.merge(key, total, KcBaselineStore::latest));
			for (BossDetector detector : observationDetectors())
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
		for (BossDetector detector : observationDetectors())
			refreshDetectorBaseline(detector, loggedIn);
	}

	private void refreshDetectorBaseline(BossDetector detector, boolean loggedIn)
	{
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
		return report.has("assignmentId") && !report.get("assignmentId").isJsonNull() && report.has("trackingMode") && ("beta_candidate".equals(report.get("trackingMode").getAsString())
			|| "server_observation".equals(report.get("trackingMode").getAsString()));
	}

	private boolean encounterCommunicationEnabled()
	{
		// Plugin enable/disable is the sole communication switch. Legacy consent keys are ignored.
		return running && HUNTMASTER_API_BASE_URL != null;
	}

	private void resetLinkRequests()
	{
        collectorDiagnostics.reset();publishedDiagnostics=null;
        collectionPolicy.accept(null, 0); nextCollectionHealthAt = 0;
        collectionSession = UUID.randomUUID().toString();
        collectionSignals = collectionCaptured = collectionDelivered = collectionDeliveryFailures = collectionCaptureFailures = lastCollectionSignalAt = 0;
		dashboardAccount=null;dashboard.reset(null,System.currentTimeMillis());publishedDashboard=null;
		serverVerification.release();
		nextHealthCheckAt = 0;
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
        publishDiagnostics();
	}
	private void rejectLink()
	{
		membershipDenied = true;
		// Membership rejection is retried through registration refresh; no credentials.
		resetLinkRequests();
		if (reportsEnabled) diagnostic(() -> interruptEncounterCaptures(client.getTickCount(), EncounterObservation.Reason.ASSIGNMENT_CHANGED));
		clearEncounterCaptures();
		saveEncounterReports();
		savePendingKcEvents();
	}
	private void showLinkNotice()
	{
		if (!running || HUNTMASTER_API_BASE_URL == null || !membershipDenied
			|| linkNoticeShown || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
		linkNoticeShown = true;
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Huntmaster could not confirm your Bosscape registration or membership right now. Tracking is paused; saved reports are preserved and the connection will retry. If this continues, check your registration in Bosscape Discord.", null);
	}

	private final java.util.Map<String,ChestRunState> chestStates=new java.util.HashMap<>();
	private BossDetector chestDetector;
	private boolean canCollectDagannoth()
	{
		return reportsEnabled && client.getLocalPlayer() != null;
	}
	private boolean collectDagannothNpc(String name, boolean loot)
	{
		return canCollectDagannoth() && dagannothCapture.npc(name, loot, client.getLocalPlayer().getName(),
			assignmentId, client.getTickCount(), System.currentTimeMillis());
	}
	private void interruptEncounterCaptures(int tick, EncounterObservation.Reason reason)
	{
		chestStates.clear(); chestDetector=null;
		encounterCapture.interrupt(tick, reason);
		dagannothCapture.interrupt(tick, reason);
	}
	private void clearEncounterCaptures()
	{
		chestStates.clear(); chestDetector=null;
		encounterCapture.clear();
		dagannothCapture.clear();
		chestRewardInventory = -1;
		chestRewardTick = -1;
		chestRewardAssignment = null;
	}
	private boolean canObserve(BossDetector detector)
	{
		return reportsEnabled && client.getLocalPlayer() != null && detector != null && detector.getDetectorType() != BossDetectorType.SPECIAL;
	}
	private void observePrimary(BossDetector detector, EncounterObservation.SignalKind kind)
	{
		if (canObserve(detector)) diagnostic(() -> encounterCapture.primary(detector, client.getLocalPlayer().getName(), assignmentId,
			client.getTickCount(), System.currentTimeMillis(), kind));
		observeProfileSnapshot(detector);
	}
	private void observeProfileSnapshot(BossDetector detector)
	{
		if (!canObserve(detector)) return;
		encounterCapture.select(detector);
		diagnostic(() -> {
			String key = "The Nightmare".equals(detector.getName()) ? "nightmare" : detector.getProfileKey();
			Integer total = configManager.getRSProfileConfiguration("killcount", key, int.class);
			encounterCapture.diagnostic("PROFILE_SNAPSHOT", total, client.getTickCount());
		});
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
		observeProfileSnapshot(detector);
	}

	private void queueEncounterReport(EncounterObservation.Snapshot snapshot)
	{
		if (!reportsEnabled) return;
        collectionCaptured++;
        collectionSignals += snapshot.signals.size(); lastCollectionSignalAt = snapshot.observedAt;
		try
		{
			JsonObject report = EncounterReportCodec.encodeObservation(snapshot);
			String payload = report.toString();
			boolean added = reportQueue.add(snapshot.reportId.toString(), payload, snapshot.observedAt,
				System.currentTimeMillis(), isBetaCredit(report));
			log.debug("Huntmaster encounter report {} finalized: {} (queued={})", snapshot.reportId, snapshot.outcome, added);
			if (added)
			{
                Player current=client.getLocalPlayer();
                if(current!=null && sameRsn(current.getName(),snapshot.rsn) && sameRsn(assignmentRsn,snapshot.rsn))
                    collectorDiagnostics.queued(snapshot.reportId.toString(),snapshot.policyRevision);
                publishDiagnostics();
				saveEncounterReports();
				// Use the same serialized queue, eligibility gates and retry path as the timer.
				updateEncounterReports();
			}
            else collectionCaptureFailures++;
		}
		catch (RuntimeException ex) { collectionCaptureFailures++; log.debug("Huntmaster diagnostic capture could not be queued", ex); }
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
		if (!running || !encounterCommunicationEnabled()) return;
		Player reportPlayer = client.getLocalPlayer();
		if (!reportsEnabled || client.getGameState() != GameState.LOGGED_IN
				|| !registrationConfirmed || assignmentSyncRequired || !healthReachable
				|| reportPlayer == null || !sameRsn(reportPlayer.getName(), assignmentRsn)) return;
		EncounterReportQueue.Entry entry = reportQueue.next(System.currentTimeMillis(), candidate ->
		{
			try { JsonObject queued = gson.fromJson(candidate.payload, JsonObject.class); return (!queued.has("collectorVersion") || collectionPolicy.supported()) && sameRsn(reportPlayer.getName(), queued.get("rsn").getAsString()); }
			catch (RuntimeException ex) { return true; } // Let the malformed-entry handler quarantine it.
		});
		saveEncounterReports();
		if (entry == null) return;
		String reportRoute;
		try
		{
			JsonObject report = gson.fromJson(entry.payload, JsonObject.class);
			reportRoute = EncounterReportCodec.deliveryRoute(report);
			if (!sameRsn(reportPlayer.getName(), report.get("rsn").getAsString()))
			{
				entry.nextAttemptAt = System.currentTimeMillis() + 60000;
				return;
			}
		}
		catch (RuntimeException ex) { reportQueue.acknowledge(entry.id); saveEncounterReports(); return; }
		entry.inFlight = true;
		Request request = new Request.Builder().url(HUNTMASTER_API_BASE_URL + reportRoute)
			.post(RequestBody.create(MediaType.parse("application/json"), entry.payload)).build();
		enqueueRequest(request, (status, response) ->
		{
			entry.inFlight = false;
			boolean accepted = false;
			boolean completed = false;
            JsonObject decision=null;
			try
			{
				if (status == 200)
				{
					JsonObject result = gson.fromJson(response, JsonObject.class);
					String resultStatus = result.get("status").getAsString();
					accepted = result.get("success").getAsBoolean() && entry.id.equals(result.get("reportId").getAsString())
						&& ("stored".equals(resultStatus) || "duplicate".equals(resultStatus));
					JsonObject credit = result.has("serverCredit") ? result.getAsJsonObject("serverCredit") : result.has("betaCredit") ? result.getAsJsonObject("betaCredit") : null;
                    decision=credit;
					completed = accepted && credit != null && credit.has("status") && "task_completed".equals(credit.get("status").getAsString());
					if(accepted && result.has("assignmentState"))
					{
						JsonObject report=gson.fromJson(entry.payload,JsonObject.class);
						boolean gained=credit!=null && credit.has("status") && "progress_updated".equals(credit.get("status").getAsString());
						dashboard.accept(result.getAsJsonObject("assignmentState"),System.currentTimeMillis(),gained && !report.get("assignmentId").isJsonNull()?report.get("assignmentId").getAsString():null,
							java.time.Instant.parse(report.get("observedAt").getAsString()).toEpochMilli());
						publishDashboard();
					}
				}
			}
			catch (RuntimeException ex) { log.debug("Huntmaster invalid diagnostic acknowledgement", ex); }
            if (accepted) collectionDelivered++; else collectionDeliveryFailures++;
            collectorDiagnostics.response(entry.id,accepted,status,decision);publishDiagnostics();
			// Preserve old-bot 404 compatibility; credit candidates remain durable.
			if (accepted || status == 400 || status == 409 || status == 413) reportQueue.acknowledge(entry.id);
			else
			{
				entry.nextAttemptAt = System.currentTimeMillis() + 60_000L;
				if (entry.creditCandidate) markConnectionFailure();
			}
			if (completed) { assignmentSyncRequired = true; refreshAssignment(); }
			log.debug("Huntmaster encounter report {} response {} accepted={}", entry.id, status, accepted);
			saveEncounterReports();
			recoverConnectionIfReady();
			// The five-second worker drains waiting reports without an acknowledgement-driven burst.
		});
	}


	private void diagnostic(Runnable action)
	{
		try { action.run(); }
		catch (RuntimeException ex) { collectionCaptureFailures++; log.debug("Huntmaster diagnostic capture failed without affecting KC", ex); }
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		final long session = lifecycle;
		if (HUNTMASTER_CONFIG_GROUP.equals(event.getGroup()) && event.getKey().startsWith("recruitment_"))
			clientThread.invokeLater(() -> { if (running && lifecycle == session) recruitmentNotifications.reset(); });
		if (!"killcount".equals(event.getGroup())) return;
		clientThread.invokeLater(() ->
		{
			if (!running || lifecycle != session || !canTrackNewKills()) return;
			for (BossDetector detector : observationDetectors())
			{
				if (detector.getDetectorType() != BossDetectorType.STANDARD_NPC
					|| !event.getKey().equals(detector.getProfileKey()) || !canObserve(detector)) continue;
				diagnostic(() ->
				{
					Integer total = configManager.getRSProfileConfiguration("killcount", detector.getProfileKey(), int.class);
					// Capture profile changes as supporting evidence, never as a new verification baseline.
					if (total != null) observeCounter(detector, EncounterObservation.CounterSource.RS_PROFILE, detector.getLastKc(), total);
                    encounterCapture.select(detector);
                    encounterCapture.diagnostic("PROFILE_CHANGED", total, client.getTickCount());
				});
			}
		});
	}


	private void observeEvidenceOnlyMessage(BossDetector detector, String message)
	{
		Integer total = GenericKcRouter.parse(message, detector.getName());
		if (total == null) return;
        if (detector.getDetectorType() == BossDetectorType.COMPLETION)
            observePrimary(detector, EncounterObservation.SignalKind.COMPLETION);
		checkpointObservedKc(detector, total);
		Integer previous = detector.getLastKc();
		observeCounter(detector, EncounterObservation.CounterSource.KC_MESSAGE, previous, total);
		if (previous != null && (long) total - previous > 1)
			diagnostic(() -> encounterCapture.uncertain(EncounterObservation.Outcome.AMBIGUOUS));
		detector.setLastKc(total);
	}
}
