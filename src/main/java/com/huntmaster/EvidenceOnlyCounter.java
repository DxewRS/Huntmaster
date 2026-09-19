package com.huntmaster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

final class EvidenceOnlyCounter
{
	private static final Pattern TOTAL = Pattern.compile("^([0-9][0-9,]*)(?:\\.|\\s|$)");
	static Integer parse(String message, String prefix)
	{
		if (message == null || prefix == null || message.length() > 1024) return null;
		String plain = Text.removeTags(message);
		if (!plain.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
		Matcher matcher = TOTAL.matcher(plain.substring(prefix.length()).trim());
		if (!matcher.find()) return null;
		try { return Integer.valueOf(matcher.group(1).replace(",", "")); }
		catch (NumberFormatException ex) { return null; }
	}
	static boolean matchesPrefix(String message, String prefix)
	{
		if (message == null || prefix == null) return false;
		String plain = Text.removeTags(message);
		return plain.regionMatches(true, 0, prefix, 0, prefix.length());
	}
}
