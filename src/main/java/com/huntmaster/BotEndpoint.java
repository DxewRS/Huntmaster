package com.huntmaster;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/** A packaged bot address; players cannot select another server. */
final class BotEndpoint
{
	static String resolve(boolean development, String publicAddress)
	{
		if (development) return "http://127.0.0.1:8787";
		if (publicAddress == null || publicAddress.trim().isEmpty()) return null;
		try
		{
			URI uri = new URI(publicAddress.trim());
			String host = uri.getHost();
			if (!"https".equals(uri.getScheme()) || host == null || host.indexOf('.') < 0
				|| "localhost".equalsIgnoreCase(host) || host.matches("[0-9.]+")
				|| host.indexOf(':') >= 0 || uri.getUserInfo() != null || uri.getPort() != -1
				|| uri.getQuery() != null || uri.getFragment() != null
				|| !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) return null;
			return "https://" + host;
		}
		catch (URISyntaxException ex) { return null; }
	}

	static String load(boolean development)
	{
		if (development) return resolve(true, null);
		Properties properties = new Properties();
		try (InputStream input = BotEndpoint.class.getResourceAsStream("/huntmaster-api.properties"))
		{
			if (input != null) properties.load(input);
			if (!Boolean.parseBoolean(properties.getProperty("publicReady", "false"))) return null;
			return resolve(false, properties.getProperty("botUrl"));
		}
		catch (IOException ex) { return null; }
	}
}
