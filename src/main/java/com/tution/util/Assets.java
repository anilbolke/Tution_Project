package com.tution.util;

import java.io.File;

import javax.servlet.ServletContext;

/**
 * Cache-busting version stamp for the stylesheet.
 *
 * WHY THIS EXISTS. Tomcat serves /css/style.css with an ETag and a
 * Last-Modified but no Cache-Control, so a browser is free to hold its copy for
 * the rest of the session without revalidating. A CSS change then reaches the
 * server but not the person looking at the screen: the JSP updates, the styles
 * do not, and the page renders half-new. That is exactly what happened when the
 * shared button styles were added - the new button labels appeared while the
 * buttons themselves stayed unstyled.
 *
 * Appending the file's last-modified time to the URL makes every deploy a new
 * URL, so the browser fetches it once and then caches it hard until it actually
 * changes. No hard refresh, no "try Ctrl+F5", and no cache header that would
 * throw away caching altogether.
 */
public final class Assets {

    private Assets() { }

    /** Resolved once per file change, not once per request. */
    private static volatile String cachedVersion;
    private static volatile long   cachedStamp;

    /**
     * @return the stylesheet's last-modified time as a query-safe string.
     *         Falls back to a fixed value if the file cannot be read, which
     *         simply means caching behaves as it did before.
     */
    public static String version(ServletContext ctx) {
        if (ctx == null) return "1";
        try {
            String real = ctx.getRealPath("/css/style.css");
            if (real == null) return "1";
            long stamp = new File(real).lastModified();
            if (stamp <= 0) return "1";
            if (stamp != cachedStamp) {
                cachedStamp   = stamp;
                cachedVersion = Long.toString(stamp / 1000L);
            }
            return cachedVersion;
        } catch (RuntimeException e) {
            // A missing or unreadable file must never take a page down over a
            // cache hint.
            return "1";
        }
    }
}
