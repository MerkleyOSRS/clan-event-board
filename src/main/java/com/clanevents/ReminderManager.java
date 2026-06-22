package com.clanevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ReminderManager
{
	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClanEventsConfig config;

	@Inject
	private DiscordWebhookClient discordWebhookClient;

	private volatile List<ClanEvent> events = Collections.emptyList();
	// C4 fix: ConcurrentHashMap.newKeySet() is thread-safe; firedReminders is written from the executor
	// thread (checkReminders) and cleared from the client thread (clearFiredReminders via onGameStateChanged)
	private final Set<String> firedReminders = ConcurrentHashMap.newKeySet();

	public void updateEvents(List<ClanEvent> events)
	{
		this.events = events;
	}

	public void checkReminders()
	{
		try // C3 fix: uncaught exception from a ScheduledExecutorService task permanently cancels it
		{
			long nowSeconds = System.currentTimeMillis() / 1000;

			for (ClanEvent event : events)
			{
				for (int reminderMinutes : event.reminders)
				{
					String key = event.id + ":" + reminderMinutes;
					if (firedReminders.contains(key))
					{
						continue;
					}

					long reminderTime = event.timestampUtc - (reminderMinutes * 60L);

					// Already past (plus a 90s grace window) — mark fired silently so we don't
					// spam stale reminders on login
					if (nowSeconds > reminderTime + 90)
					{
						firedReminders.add(key);
						continue;
					}

					if (nowSeconds >= reminderTime)
					{
						firedReminders.add(key);
						fireReminder(event, reminderMinutes);
					}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Unexpected error in checkReminders", e);
		}
	}

	public void clearFiredReminders()
	{
		firedReminders.clear();
	}

	private void fireReminder(ClanEvent event, int minutesBefore)
	{
		String timeLabel = EventFormatUtils.formatMinutes(minutesBefore); // L2 fix: use shared utility
		String message = "[Clan Event Board] Reminder: \"" + event.title + "\" starts in " + timeLabel + "!";

		clientThread.invoke(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build()));

		String webhookUrl = config.discordWebhookUrl();
		if (webhookUrl != null && !webhookUrl.isEmpty()) // H3 fix: null-check before isEmpty
		{
			discordWebhookClient.postReminder(webhookUrl, event, minutesBefore, config.discordPingHere());
		}

		log.debug("Fired reminder for event {} ({}m before)", event.title, minutesBefore);
	}
}
