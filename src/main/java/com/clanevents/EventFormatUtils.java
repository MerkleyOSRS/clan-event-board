package com.clanevents;

class EventFormatUtils
{
	static String formatMinutes(int minutes)
	{
		if (minutes >= 60 && minutes % 60 == 0)
		{
			int hours = minutes / 60;
			return hours + " hour" + (hours > 1 ? "s" : "");
		}
		return minutes + " minute" + (minutes != 1 ? "s" : "");
	}
}
