package com.clanevents;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
	private ChatMessageManager chatMessageManager;

	private ClanEventsPanel panel;
	private NavigationButton navButton;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> pollFuture;
	private ScheduledFuture<?> reminderFuture;

	// In-memory cache of current events
	private volatile ClanEventsData eventsData = new ClanEventsData();

	@Override
	protected void startUp()
	{
		panel = new ClanEventsPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Clan Events")
			.icon(buildIcon())
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		executor = Executors.newSingleThreadScheduledExecutor();

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
			// Re-check reminders after login so stale ones don't fire
			reminderManager.clearFiredReminders();
			if (isConfigured() && pollFuture == null)
			{
				startPolling();
			}
		}
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

		jsonBinClient.fetchEvents(
			config.binId(),
			config.apiKey(),
			data ->
			{
				eventsData = data;
				long nowSeconds = Instant.now().getEpochSecond();

				List<ClanEvent> upcoming = data.events.stream()
					.filter(e -> e.timestampUtc > nowSeconds)
					.sorted(Comparator.comparingLong(e -> e.timestampUtc))
					.collect(Collectors.toList());

				reminderManager.updateEvents(upcoming);

				if (panel != null)
				{
					panel.setEvents(upcoming, getCurrentUsername());
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

		jsonBinClient.saveEvents(config.binId(), config.apiKey(), updated,
			() ->
			{
				panel.showError("Failed to save event.");
				onFailure.run();
			}
		);

		// Optimistically update local cache and panel
		eventsData = updated;
		fetchEvents();

		notifyCreated(event);
		onSuccess.run();
	}

	public void rsvpEvent(ClanEvent event)
	{
		if (!isConfigured())
		{
			return;
		}
		String username = getCurrentUsername();
		if (username == null)
		{
			return;
		}

		// Read-modify-write: fetch fresh copy, toggle RSVP, save back
		jsonBinClient.fetchEvents(config.binId(), config.apiKey(), data ->
		{
			for (ClanEvent e : data.events)
			{
				if (e.id.equals(event.id))
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
			jsonBinClient.saveEvents(config.binId(), config.apiKey(), data, () ->
				log.debug("Failed to save RSVP for event {}", event.id));

			eventsData = data;
			long nowSeconds = Instant.now().getEpochSecond();
			List<ClanEvent> upcoming = data.events.stream()
				.filter(e -> e.timestampUtc > nowSeconds)
				.sorted(Comparator.comparingLong(e -> e.timestampUtc))
				.collect(Collectors.toList());

			if (panel != null)
			{
				panel.setEvents(upcoming, username);
			}
		}, () -> log.debug("Failed to fetch events for RSVP update"));
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
			if (!e.id.equals(event.id))
			{
				updated.events.add(e);
			}
		}

		jsonBinClient.saveEvents(config.binId(), config.apiKey(), updated, () ->
			log.debug("Failed to delete event {}", event.id));

		eventsData = updated;
		fetchEvents();
	}

	public boolean currentUserCanAdminDelete()
	{
		ClanChannel channel = client.getClanChannel();
		if (channel == null)
		{
			return false;
		}
		String username = getCurrentUsername();
		if (username == null)
		{
			return false;
		}
		ClanChannelMember member = channel.findMember(username);
		if (member == null)
		{
			return false;
		}
		return member.getRank().getRank() >= config.minAdminRank().getMinRankValue();
	}

	public String getCurrentUsername()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}
		return client.getLocalPlayer().getName();
	}

	private void notifyCreated(ClanEvent event)
	{
		String msg = "[Clan Events] \"" + event.title + "\" has been scheduled by " + event.host + ".";
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(msg)
			.build());

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
