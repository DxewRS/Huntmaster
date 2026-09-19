package com.huntmaster;

import okhttp3.HttpUrl;
import okhttp3.Request;

/** Fixed-origin request guard. RSN registration and membership are checked by the bot. */
final class PluginLink
{
	Request authenticate(Request request, String endpoint)
	{
		HttpUrl origin = HttpUrl.parse(endpoint);
		HttpUrl target = request.url();
		if (origin == null || !origin.scheme().equals(target.scheme()) || !origin.host().equals(target.host())
			|| origin.port() != target.port()) throw new IllegalArgumentException("unexpected_bot_origin");
		Request.Builder builder = request.newBuilder().removeHeader("Authorization");
		return builder.build();
	}
}
