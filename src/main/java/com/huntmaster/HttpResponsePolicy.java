package com.huntmaster;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import okhttp3.ResponseBody;

/** Bounded response handling and conservative retry classification. */
final class HttpResponsePolicy
{
    static final int MAX_RESPONSE_BYTES = 65536;
    static final long MAX_RETRY_AFTER_MS = 60_000L;
    private HttpResponsePolicy() { }
    static String read(ResponseBody body) throws IOException
    {
        if (body == null) return "";
        if (body.contentLength() > MAX_RESPONSE_BYTES) throw new IOException("Oversized Huntmaster response");
        try (InputStream input = body.byteStream())
        {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("Oversized Huntmaster response");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    static boolean isPermanentRejection(int status)
    {
        return status == 400 || status == 404 || status == 409 || status == 413 || status == 422;
    }
    /** Outages and rate limits say nothing about the report that was being sent. */
    static boolean isTemporary(int status)
    {
        return status <= 0 || status == 408 || status == 429 || status >= 500;
    }
    /** Delay asked for by a Retry-After header, seconds or HTTP date. Unreadable means none; a long wait is capped. */
    static long retryAfterMs(String header, long now)
    {
        if (header == null) return 0;
        long delay;
        try { delay = Long.parseLong(header.trim()) * 1000L; }
        catch (NumberFormatException notSeconds)
        {
            try { delay = ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now; }
            catch (DateTimeParseException unreadable) { return 0; }
        }
        return Math.max(0, Math.min(delay, MAX_RETRY_AFTER_MS));
    }
}
