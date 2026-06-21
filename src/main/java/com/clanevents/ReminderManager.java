package com.clanevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class ReminderManager
{
	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClanEventsConfig config;

	@Inject
	private DiscordWebhookClient discordWebhookClient;

	private volatile List<ClanEvent> events = Collections.emptyList();
	private final Set<String> firedReminders = new HashSet<>();

	public void updateEvents(List<ClanEvent> events)
	{
		this.events = events;
	}

	public void checkReminders()
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

	public void clearFiredReminders()
	{
		firedReminders.clear();
	}

	private void fireReminder(ClanEvent event, int minutesBefore)
	{
		String timeLabel = formatMinutes(minutesBefore);
		String message = "[Clan Events] Reminder: \"" + event.title + "\" starts in " + timeLabel + "!";

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());

		String webhookUrl = config.discordWebhookUrl();
		if (!webhookUrl.isEmpty())
		{
			discordWebhookClient.postReminder(webhookUrl, event, minutesBefore, config.discordPingHere());
		}

		log.debug("Fired reminder for event {} ({}m before)", event.title, minutesBefore);
	}

	private static String formatMinutes(int minutes)
	{
		if (minutes >= 60 && minutes % 60 == 0)
		{
			int hours = minutes / 60;
			return hours + " hour" + (hours > 1 ? "s" : "");
		}
		return minutes + " minute" + (minutes != 1 ? "s" : "");
	}
}
