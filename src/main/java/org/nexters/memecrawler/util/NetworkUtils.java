package org.nexters.memecrawler.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class NetworkUtils {
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int DEFAULT_TIMEOUT = 15000;

    private long lastRequestTime = 0;

    public void enforceRateLimit(int rateLimitMs) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;

        if (timeSinceLastRequest < rateLimitMs) {
            try {
                Thread.sleep(rateLimitMs - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    public Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(DEFAULT_USER_AGENT)
                .timeout(DEFAULT_TIMEOUT)
                .get();
    }

    public Document fetchDocument(String url, int timeout) throws IOException {
        return Jsoup.connect(url)
                .userAgent(DEFAULT_USER_AGENT)
                .timeout(timeout)
                .get();
    }
}