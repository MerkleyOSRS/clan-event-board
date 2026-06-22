package com.clanevents;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Clan Event Board",
	description = "Shared clan event scheduling with RSVP, reminders, and Discord notifications",
	tags = {"clan", "events", "schedule", "rsvp", "social"}
)
public class ClanEventsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClanEventsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private JsonBinClient jsonBinClient;

	@Inject
	private DiscordWebhookClient discordWebhookClient;

	@Inject
	private ReminderManager reminderManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	private ClanEventsPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> pollFuture;
	private ScheduledFuture<?> reminderFuture;

	private volatile ClanEventsData eventsData = new ClanEventsData();
	// C1 fix: cache client-thread-only values so they can safely be read from EDT / OkHttp threads
	private volatile String cachedUsername = null;
	private volatile boolean cachedCanAdminDelete = false;

	@Override
	protected void startUp()
	{
		eventsData = new ClanEventsData(); // M3 fix: reset stale cache on re-enable
		panel = new ClanEventsPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Clan Event Board")
			.icon(buildIcon())
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		executor = Executors.newSingleThreadScheduledExecutor();

		// Populate the username/rank cache immediately if the player is already logged in.
		// onGameStateChanged(LOGGED_IN) won't fire again for an existing session.
		clientThread.invoke(this::cachePlayerInfo);

		if (isConfigured())
		{
			startPolling();
		}
		else
		{
			panel.showConfigRequired();
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);

		if (pollFuture != null)
		{
			pollFuture.cancel(false);
			pollFuture = null;
		}
		if (reminderFuture != null)
		{
			reminderFuture.cancel(false);
			reminderFuture = null;
		}
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}

		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			cachePlayerInfo(); // safe: event subscribers are dispatched on client thread
			reminderManager.clearFiredReminders();
			if (isConfigured() && pollFuture == null)
			{
				startPolling();
			}
		}
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		// Re-cache in case the local player's rank changed
		cachePlayerInfo();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("clan-event-board"))
		{
			return;
		}
		// Re-compute admin delete permission in case minAdminRank changed
		cachePlayerInfo();
		// M2 fix: start polling immediately when credentials are first entered
		if (isConfigured() && pollFuture == null)
		{
			startPolling();
			panel.showLoading();
		}
	}

	// Must only be called on the client thread
	private void cachePlayerInfo()
	{
		cachedUsername = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		cachedCanAdminDelete = computeCanAdminDelete();
	}

	private boolean computeCanAdminDelete()
	{
		ClanChannel channel = client.getClanChannel();
		if (channel == null || cachedUsername == null)
		{
			return false;
		}
		ClanChannelMember member = channel.findMember(cachedUsername);
		if (member == null)
		{
			return false;
		}
		return member.getRank().getRank() >= config.minAdminRank().getMinRankValue();
	}

	private void startPolling()
	{
		int intervalSeconds = Math.max(30, config.pollIntervalSeconds());
		pollFuture = executor.scheduleAtFixedRate(this::fetchEvents, 0, intervalSeconds, TimeUnit.SECONDS);
		reminderFuture = executor.scheduleAtFixedRate(reminderManager::checkReminders, 60, 60, TimeUnit.SECONDS);
	}

	private void fetchEvents()
	{
		if (!isConfigured())
		{
			return;
		}

		// Snapshot volatile fields before the async boundary so the callback closure captures consistent values
		final boolean canAdminDelete = cachedCanAdminDelete;

		jsonBinClient.fetchEvents(
			config.binId(),
			config.apiKey(),
			data ->
			{
				data.events.removeIf(e -> e.id == null || e.title == null || e.host == null); // H2 fix
				eventsData = data;
				long nowSeconds = Instant.now().getEpochSecond();

				List<ClanEvent> upcoming = data.events.stream()
					.filter(e -> e.timestampUtc > nowSeconds)
					.sorted(Comparator.comparingLong(e -> e.timestampUtc))
					.collect(Collectors.toList());

				reminderManager.updateEvents(upcoming);

				if (panel != null)
				{
					panel.setEvents(upcoming, cachedUsername, canAdminDelete);
				}
			},
			() ->
			{
				if (panel != null)
				{
					panel.showError("Failed to load events — check connection or credentials.");
				}
			}
		);
	}

	public void createEvent(ClanEvent event, Runnable onSuccess, Runnable onFailure)
	{
		if (!isConfigured())
		{
			onFailure.run();
			return;
		}

		ClanEventsData updated = new ClanEventsData();
		updated.events.addAll(eventsData.events);
		updated.events.add(event);

		// C2 fix: all post-create work lives inside the success callback so it only runs after PUT completes
		jsonBinClient.saveEvents(config.binId(), config.apiKey(), updated,
			() ->
			{
				eventsData = updated;
				fetchEvents();
				notifyCreated(event);
				onSuccess.run();
			},
			() ->
			{
				if (panel != null)
				{
					panel.showError("Failed to save event.");
				}
				onFailure.run();
			}
		);
	}

	public void rsvpEvent(ClanEvent event)
	{
		if (!isConfigured())
		{
			return;
		}
		final String username = cachedUsername;
		if (username == null)
		{
			return;
		}

		jsonBinClient.fetchEvents(config.binId(), config.apiKey(), data ->
		{
			data.events.removeIf(e -> e.id == null || e.title == null || e.host == null);
			for (ClanEvent e : data.events)
			{
				if (Objects.equals(e.id, event.id)) // H2 fix: null-safe ID comparison
				{
					if (e.rsvps.contains(username))
					{
						e.rsvps.remove(username);
					}
					else
					{
						e.rsvps.add(username);
					}
					break;
				}
			}
			// H1/H4 fix: panel update and cache write are inside save success; show error + re-sync on failure
			jsonBinClient.saveEvents(config.binId(), config.apiKey(), data,
				() ->
				{
					eventsData = data;
					fetchEvents();
				},
				() ->
				{
					log.warn("Failed to save RSVP for event {}", event.id);
					if (panel != null)
					{
						panel.showError("RSVP failed — please try again.");
					}
					fetchEvents();
				}
			);
		}, () ->
		{
			log.warn("Failed to fetch events for RSVP update");
			if (panel != null)
			{
				panel.showError("Failed to load events — check connection.");
			}
		});
	}

	public void deleteEvent(ClanEvent event)
	{
		if (!isConfigured())
		{
			return;
		}

		ClanEventsData updated = new ClanEventsData();
		for (ClanEvent e : eventsData.events)
		{
			if (!Objects.equals(e.id, event.id)) // H2 fix: null-safe ID comparison
			{
				updated.events.add(e);
			}
		}

		// H1/H4 fix: fetchEvents inside success callback so GET doesn't race the pending PUT
		jsonBinClient.saveEvents(config.binId(), config.apiKey(), updated,
			() ->
			{
				eventsData = updated;
				fetchEvents();
			},
			() ->
			{
				log.warn("Failed to delete event {}", event.id);
				if (panel != null)
				{
					panel.showError("Delete failed — please try again.");
				}
				fetchEvents();
			}
		);
	}

	public String getCurrentUsername()
	{
		return cachedUsername;
	}

	private void notifyCreated(ClanEvent event)
	{
		String msg = "[Clan Event Board] \"" + event.title + "\" has been scheduled by " + event.host + ".";
		clientThread.invoke(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(msg)
			.build()));

		String webhookUrl = config.discordWebhookUrl();
		if (webhookUrl != null && !webhookUrl.isEmpty())
		{
			discordWebhookClient.postEventCreated(webhookUrl, event);
		}
	}

	private boolean isConfigured()
	{
		return !config.binId().isEmpty() && !config.apiKey().isEmpty();
	}

	private static BufferedImage buildIcon()
	{
		BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x1ABC9C));
		g.fillRoundRect(0, 0, 24, 24, 6, 6);
		g.setColor(Color.WHITE);
		g.setFont(new Font("Arial", Font.BOLD, 10));
		FontMetrics fm = g.getFontMetrics();
		String text = "CE";
		g.drawString(text, (24 - fm.stringWidth(text)) / 2, 16);
		g.dispose();
		return img;
	}

	@Provides
	ClanEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanEventsConfig.class);
	}
}
