package com.huntmaster;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import okhttp3.ResponseBody;

/** Bounded response handling and conservative retry classification. */
final class HttpResponsePolicy
{
    static final int MAX_RESPONSE_BYTES = 65536;
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
}
