package com.siddhesh.standby;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/** Small hardened HTTPS GET: host allow-list (re-checked after redirects), size cap, timeouts. */
final class Net {
    private Net() {}

    static boolean allowed(String host, String[] hosts) {
        if (host == null) return false;
        for (String h : hosts) if (h.equalsIgnoreCase(host)) return true;
        return false;
    }

    static byte[] get(String urlStr, int maxBytes, String[] hosts, String requiredTypePrefix) throws IOException {
        URL url = new URL(urlStr);
        if (!"https".equals(url.getProtocol()) || !allowed(url.getHost(), hosts)) throw new IOException("blocked url");
        URLConnection uc = url.openConnection();
        if (!(uc instanceof HttpsURLConnection)) throw new IOException("not https");
        HttpsURLConnection c = (HttpsURLConnection) uc;
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "StandBy-Android/1.2");
        try {
            int code = c.getResponseCode();
            URL fin = c.getURL();
            if (code != 200 || !"https".equals(fin.getProtocol()) || !allowed(fin.getHost(), hosts)) {
                throw new IOException("bad response");
            }
            String type = c.getContentType();
            if (requiredTypePrefix != null
                    && (type == null || !type.toLowerCase(Locale.US).startsWith(requiredTypePrefix))) {
                throw new IOException("unexpected content type");
            }
            if (c.getContentLengthLong() > maxBytes) throw new IOException("too large");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n, total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) throw new IOException("too large");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
