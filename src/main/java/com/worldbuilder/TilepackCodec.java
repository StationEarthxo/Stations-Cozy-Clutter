package com.worldbuilder;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class TilepackCodec
{
    static final String PREFIX = "WB1:";
    private static final int MAX_DECOMPRESSED_BYTES = 4 * 1024 * 1024;

    private TilepackCodec()
    {
    }

    static String encode(Gson gson, Tilepack tilepack) throws IOException
    {
        byte[] json = gson.toJson(tilepack).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output))
        {
            gzip.write(json);
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    static Tilepack decode(Gson gson, String code) throws IOException
    {
        if (code == null || !code.trim().startsWith(PREFIX))
        {
            throw new IllegalArgumentException("Not a Station's Cozy Clutter Tilepack code");
        }

        byte[] compressed;
        try
        {
            compressed = Base64.getUrlDecoder().decode(code.trim().substring(PREFIX.length()));
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("Tilepack contains invalid Base64", ex);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
        {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzip.read(buffer)) != -1)
            {
                if (output.size() + read > MAX_DECOMPRESSED_BYTES)
                {
                    throw new IllegalArgumentException("Tilepack is too large");
                }
                output.write(buffer, 0, read);
            }
        }

        Tilepack tilepack = gson.fromJson(new String(output.toByteArray(), StandardCharsets.UTF_8), Tilepack.class);
        if (tilepack == null || tilepack.version != Tilepack.CURRENT_VERSION || tilepack.props == null)
        {
            throw new IllegalArgumentException("Unsupported or empty Tilepack");
        }
        return tilepack;
    }
}
