package com.clanevents;

import java.util.ArrayList;
import java.util.List;

public class ClanEvent
{
	public String id;
	public String title;
	public String host;
	public long timestampUtc;
	public List<String> rsvps = new ArrayList<>();
	public List<Integer> reminders = new ArrayList<>();
	public long createdAt;
}
