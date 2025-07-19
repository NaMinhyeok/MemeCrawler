package org.nexters.memecrawler.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nexters.memecrawler.model.MemeData;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MemeCrawler {
    private static final String BASE_URL = "https://namu.wiki";
    private static final String TARGET_URL = "https://namu.wiki/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD";
    private static final int RATE_LIMIT_SECONDS = 2;
    
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;

    public MemeCrawler() {
        this.objectMapper = new ObjectMapper();
    }

    private void enforceRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        long minimumWaitTime = RATE_LIMIT_SECONDS * 1000;

        if (timeSinceLastRequest < minimumWaitTime) {
            try {
                Thread.sleep(minimumWaitTime - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    public List<String> crawlMemeLinks() throws IOException {
        enforceRateLimit();
        
        System.out.println("Crawling main page: " + TARGET_URL);
        Document doc = Jsoup.connect(TARGET_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();

        List<String> memeLinks = new ArrayList<>();
        Elements links = doc.select("a[href^='/w/']");
        
        for (Element link : links) {
            String href = link.attr("href");
            if (href.startsWith("/w/") && !href.equals("/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD")) {
                String fullUrl = BASE_URL + href;
                if (!memeLinks.contains(fullUrl)) {
                    memeLinks.add(fullUrl);
                }
            }
        }
        
        System.out.println("Found " + memeLinks.size() + " meme links");
        return memeLinks;
    }

    public Map<String, Object> crawlRawMemeData(String url) throws IOException {
        enforceRateLimit();
        
        System.out.println("Crawling: " + url);
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();

        Map<String, Object> rawData = new HashMap<>();
        rawData.put("url", url);
        rawData.put("title", doc.title());
        
        Element content = doc.selectFirst(".wiki-content");
        if (content != null) {
            rawData.put("content", content.html());
        }
        
        Elements images = doc.select("img[src]");
        List<String> imageUrls = new ArrayList<>();
        for (Element img : images) {
            String src = img.attr("src");
            if (src.startsWith("http")) {
                imageUrls.add(src);
            }
        }
        rawData.put("images", imageUrls);
        
        return rawData;
    }

    public void saveRawDataToJson(List<Map<String, Object>> rawDataList, String filename) throws IOException {
        File file = new File(filename);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, rawDataList);
        System.out.println("Raw data saved to: " + filename);
    }

    public void saveMemeDataToJson(List<MemeData> memeDataList, String filename) throws IOException {
        File file = new File(filename);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, memeDataList);
        System.out.println("Structured meme data saved to: " + filename);
    }
}