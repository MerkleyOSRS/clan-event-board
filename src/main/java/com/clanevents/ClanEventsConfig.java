package com.clanevents;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("clan-event-board")
public interface ClanEventsConfig extends Config
{
	@ConfigSection(
		name = "JSONBin Backend",
		description = "Settings for the shared JSONBin event store",
		position = 0
	)
	String backendSection = "backend";

	@ConfigItem(
		keyName = "binId",
		name = "Bin ID",
		description = "Your clan's JSONBin bin ID (from jsonbin.io)",
		section = backendSection,
		position = 0,
		warning = "This feature submits data to a 3rd-party server (jsonbin.io) not controlled or verified by RuneLite developers"
	)
	default String binId()
	{
		return "";
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "API Key",
		description = "Your JSONBin master API key",
		section = backendSection,
		position = 1,
		secret = true,
		warning = "This feature submits data to a 3rd-party server (jsonbin.io) not controlled or verified by RuneLite developers"
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pollIntervalSeconds",
		name = "Refresh interval (seconds)",
		description = "How often to refresh the event list from JSONBin",
		section = backendSection,
		position = 2
	)
	default int pollIntervalSeconds()
	{
		return 60;
	}

	@ConfigSection(
		name = "Discord",
		description = "Optional Discord webhook notifications",
		position = 1
	)
	String discordSection = "discord";

	@ConfigItem(
		keyName = "discordWebhookUrl",
		name = "Webhook URL",
		description = "Discord webhook URL for event and reminder notifications (leave blank to disable)",
		section = discordSection,
		position = 0,
		warning = "This feature submits data to a 3rd-party server (discord.com) not controlled or verified by RuneLite developers"
	)
	default String discordWebhookUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "discordPingHere",
		name = "Use @here on reminders",
		description = "Prefix Discord reminder messages with @here to notify online members",
		section = discordSection,
		position = 1
	)
	default boolean discordPingHere()
	{
		return false;
	}

	@ConfigSection(
		name = "Permissions",
		description = "Role-based access settings",
		position = 2
	)
	String permissionsSection = "permissions";

	@ConfigItem(
		keyName = "minAdminRank",
		name = "Min rank to delete others' events",
		description = "Minimum clan rank required to delete an event you did not create",
		section = permissionsSection,
		position = 0
	)
	default AdminRankOption minAdminRank()
	{
		return AdminRankOption.ADMINISTRATOR;
	}
}
