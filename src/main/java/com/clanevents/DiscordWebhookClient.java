package com.clanevents;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;

@Slf4j
public class DiscordWebhookClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	public void postEventCreated(String webhookUrl, ClanEvent event)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("title", "🗓️ " + event.title);
		embed.addProperty("color", 0x1ABC9C);

		JsonArray fields = new JsonArray();

		JsonObject hostField = new JsonObject();
		hostField.addProperty("name", "Host");
		hostField.addProperty("value", event.host);
		hostField.addProperty("inline", true);
		fields.add(hostField);

		// Discord native timestamp — renders in each viewer's local time automatically
		JsonObject timeField = new JsonObject();
		timeField.addProperty("name", "Time");
		timeField.addProperty("value", "<t:" + event.timestampUtc + ":F>");
		timeField.addProperty("inline", true);
		fields.add(timeField);

		if (!event.reminders.isEmpty())
		{
			StringBuilder reminderStr = new StringBuilder();
			for (int mins : event.reminders)
			{
				if (reminderStr.length() > 0)
				{
					reminderStr.append(", ");
				}
				reminderStr.append(formatMinutes(mins));
			}
			JsonObject reminderField = new JsonObject();
			reminderField.addProperty("name", "Reminders");
			reminderField.addProperty("value", reminderStr.toString());
			reminderField.addProperty("inline", false);
			fields.add(reminderField);
		}

		embed.add("fields", fields);

		JsonObject footer = new JsonObject();
		footer.addProperty("text", "RSVP in-game via the Clan Events plugin");
		embed.add("footer", footer);

		JsonArray embeds = new JsonArray();
		embeds.add(embed);

		JsonObject payload = new JsonObject();
		payload.addProperty("content", "A new clan event has been scheduled!");
		payload.add("embeds", embeds);

		post(webhookUrl, payload);
	}

	public void postReminder(String webhookUrl, ClanEvent event, int minutesBefore, boolean pingHere)
	{
		String ping = pingHere ? "@here " : "";
		String timeLabel = formatMinutes(minutesBefore);

		JsonObject payload = new JsonObject();
		payload.addProperty("content",
			ping + "Reminder: **" + event.title + "** starts in " + timeLabel + "! (<t:" + event.timestampUtc + ":F>)");

		post(webhookUrl, payload);
	}

	private void post(String webhookUrl, JsonObject payload)
	{
		Request request = new Request.Builder()
			.url(webhookUrl)
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Discord webhook POST failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
				if (!response.isSuccessful())
				{
					log.debug("Discord webhook returned {}", response.code());
				}
			}
		});
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
