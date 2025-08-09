package org.nexters.memecrawler.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nexters.memecrawler.config.CrawlerConfig;
import org.nexters.memecrawler.util.FileUtils;
import org.nexters.memecrawler.util.NetworkUtils;

import java.io.IOException;
import java.util.*;

public class DetailedMemeCrawler {
    private final NetworkUtils networkUtils;

    public DetailedMemeCrawler() {
        this.networkUtils = new NetworkUtils();
    }

    public void crawlDetailedMemePagesFromJson(String inputJsonFile) throws IOException {
        System.out.println("Reading raw meme data from: " + inputJsonFile);
        
        List<Map<String, Object>> rawMemeList = FileUtils.loadJsonListFromFile(inputJsonFile);
        System.out.println("Found " + rawMemeList.size() + " memes to crawl in detail");
        
        FileUtils.ensureDirectoryExists(CrawlerConfig.DETAILED_DATA_DIR);
        
        for (int i = 0; i < rawMemeList.size(); i++) {
            processMemeData(rawMemeList.get(i), i + 1, rawMemeList.size());
        }
        
        System.out.println("Detailed crawling completed!");
    }

    private void processMemeData(Map<String, Object> memeInfo, int current, int total) {
        String url = (String) memeInfo.get("url");
        String title = (String) memeInfo.get("title");
        
        if (url == null || title == null) {
            System.err.println("Skipping meme with missing url or title: " + memeInfo);
            return;
        }
        
        try {
            System.out.println("Progress: " + current + "/" + total + " - Crawling: " + title);
            
            networkUtils.enforceRateLimit(CrawlerConfig.RATE_LIMIT_MS);
            Document doc = networkUtils.fetchDocument(url, CrawlerConfig.NETWORK_TIMEOUT_MS);
            
            Map<String, Object> detailedData = extractDetailedData(doc, url, title);
            saveDetailedData(detailedData, title);
            
            System.out.println("Saved detailed data for: " + title);
            
        } catch (Exception e) {
            System.err.println("Error crawling detailed data for " + title + " (" + url + "): " + e.getMessage());
        }
    }

    private Map<String, Object> extractDetailedData(Document doc, String url, String originalTitle) {
        Map<String, Object> detailedData = new HashMap<>();
        detailedData.put("url", url);
        detailedData.put("original_title", originalTitle);
        detailedData.put("page_title", doc.title());
        
        detailedData.put("meta_tags", extractMetaTags(doc));
        detailedData.put("wiki_content_html", extractWikiContentHtml(doc));
        detailedData.put("wiki_content_text", extractWikiContentText(doc));
        detailedData.put("images", extractImageData(doc));
        detailedData.put("links", extractLinkData(doc));
        detailedData.put("headings", extractHeadingData(doc));
        detailedData.put("crawled_at", new Date().toString());
        
        return detailedData;
    }

    private Map<String, String> extractMetaTags(Document doc) {
        Elements metaTags = doc.select("meta");
        Map<String, String> metaData = new HashMap<>();
        
        for (Element meta : metaTags) {
            String name = meta.attr("name");
            String property = meta.attr("property");
            String content = meta.attr("content");
            
            if (!name.isEmpty()) {
                metaData.put("meta_" + name, content);
            }
            if (!property.isEmpty()) {
                metaData.put("meta_" + property, content);
            }
        }
        return metaData;
    }

    private String extractWikiContentHtml(Document doc) {
        Element content = doc.selectFirst(".wiki-content");
        return content != null ? content.html() : "";
    }

    private String extractWikiContentText(Document doc) {
        Element content = doc.selectFirst(".wiki-content");
        return content != null ? content.text() : "";
    }

    private List<Map<String, String>> extractImageData(Document doc) {
        Elements images = doc.select("img[src]");
        List<Map<String, String>> imageData = new ArrayList<>();
        
        for (Element img : images) {
            Map<String, String> imgInfo = new HashMap<>();
            imgInfo.put("src", img.attr("src"));
            imgInfo.put("alt", img.attr("alt"));
            imgInfo.put("title", img.attr("title"));
            imageData.add(imgInfo);
        }
        return imageData;
    }

    private List<Map<String, String>> extractLinkData(Document doc) {
        Elements links = doc.select("a[href]");
        List<Map<String, String>> linkData = new ArrayList<>();
        
        for (Element link : links) {
            Map<String, String> linkInfo = new HashMap<>();
            linkInfo.put("href", link.attr("href"));
            linkInfo.put("text", link.text());
            linkInfo.put("title", link.attr("title"));
            linkData.add(linkInfo);
        }
        return linkData;
    }

    private List<Map<String, String>> extractHeadingData(Document doc) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        List<Map<String, String>> headingData = new ArrayList<>();
        
        for (Element heading : headings) {
            Map<String, String> headingInfo = new HashMap<>();
            headingInfo.put("tag", heading.tagName());
            headingInfo.put("text", heading.text());
            headingInfo.put("id", heading.attr("id"));
            headingData.add(headingInfo);
        }
        return headingData;
    }

    private void saveDetailedData(Map<String, Object> detailedData, String title) throws IOException {
        String sanitizedTitle = FileUtils.sanitizeFileName(title);
        String fileName = CrawlerConfig.DETAILED_DATA_DIR + "/" + sanitizedTitle + ".json";
        FileUtils.saveJsonToFile(detailedData, fileName);
    }
}