package org.nexters.memecrawler.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nexters.memecrawler.config.CrawlerConfig;
import org.nexters.memecrawler.util.FileUtils;
import org.nexters.memecrawler.util.NetworkUtils;

import java.io.IOException;
import java.util.*;

public class BasicMemeCrawler {
    private final NetworkUtils networkUtils;

    public BasicMemeCrawler() {
        this.networkUtils = new NetworkUtils();
    }

    public List<String> crawlMemeLinks() throws IOException {
        networkUtils.enforceRateLimit(CrawlerConfig.RATE_LIMIT_MS);
        
        System.out.println("Crawling main page: " + CrawlerConfig.TARGET_URL);
        Document doc = networkUtils.fetchDocument(CrawlerConfig.TARGET_URL);

        List<String> memeLinks = new ArrayList<>();
        Elements links = doc.select("a[href^='/w/']");
        
        for (Element link : links) {
            String href = link.attr("href");
            if (isValidMemeLink(href)) {
                String fullUrl = CrawlerConfig.BASE_URL + href;
                if (!memeLinks.contains(fullUrl)) {
                    memeLinks.add(fullUrl);
                }
            }
        }
        
        System.out.println("Found " + memeLinks.size() + " meme links");
        return memeLinks;
    }

    public Map<String, Object> crawlRawMemeData(String url) throws IOException {
        networkUtils.enforceRateLimit(CrawlerConfig.RATE_LIMIT_MS);
        
        System.out.println("Crawling: " + url);
        Document doc = networkUtils.fetchDocument(url);

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
        FileUtils.saveJsonToFile(rawDataList, filename);
        System.out.println("Raw data saved to: " + filename);
    }

    private boolean isValidMemeLink(String href) {
        return href.startsWith("/w/") && 
               !href.equals("/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD");
    }
}