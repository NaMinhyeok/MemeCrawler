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
import com.fasterxml.jackson.core.type.TypeReference;

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

    private String sanitizeFileName(String title) {
        // 파일명으로 사용할 수 없는 문자들을 제거하거나 대체
        return title.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }

    public void crawlDetailedMemePagesFromJson(String inputJsonFile) throws IOException {
        System.out.println("Reading raw meme data from: " + inputJsonFile);
        
        // raw_meme_data.json 파일 읽기
        File file = new File(inputJsonFile);
        List<Map<String, Object>> rawMemeList = objectMapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
        
        System.out.println("Found " + rawMemeList.size() + " memes to crawl in detail");
        
        // 개별 디렉토리 생성
        File outputDir = new File("detailed_meme_data");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        for (int i = 0; i < rawMemeList.size(); i++) {
            Map<String, Object> memeInfo = rawMemeList.get(i);
            String url = (String) memeInfo.get("url");
            String title = (String) memeInfo.get("title");
            
            if (url == null || title == null) {
                System.err.println("Skipping meme with missing url or title: " + memeInfo);
                continue;
            }
            
            try {
                System.out.println("Progress: " + (i + 1) + "/" + rawMemeList.size() + " - Crawling: " + title);
                
                enforceRateLimit();
                
                // 개별 페이지 크롤링
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get();
                
                // 모든 데이터 수집
                Map<String, Object> detailedData = new HashMap<>();
                detailedData.put("url", url);
                detailedData.put("original_title", title);
                detailedData.put("page_title", doc.title());
                
                // 메타 태그들
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
                detailedData.put("meta_tags", metaData);
                
                // 본문 내용
                Element content = doc.selectFirst(".wiki-content");
                if (content != null) {
                    detailedData.put("wiki_content_html", content.html());
                    detailedData.put("wiki_content_text", content.text());
                }
                
                // 모든 이미지
                Elements images = doc.select("img[src]");
                List<Map<String, String>> imageData = new ArrayList<>();
                for (Element img : images) {
                    Map<String, String> imgInfo = new HashMap<>();
                    imgInfo.put("src", img.attr("src"));
                    imgInfo.put("alt", img.attr("alt"));
                    imgInfo.put("title", img.attr("title"));
                    imageData.add(imgInfo);
                }
                detailedData.put("images", imageData);
                
                // 모든 링크
                Elements links = doc.select("a[href]");
                List<Map<String, String>> linkData = new ArrayList<>();
                for (Element link : links) {
                    Map<String, String> linkInfo = new HashMap<>();
                    linkInfo.put("href", link.attr("href"));
                    linkInfo.put("text", link.text());
                    linkInfo.put("title", link.attr("title"));
                    linkData.add(linkInfo);
                }
                detailedData.put("links", linkData);
                
                // 헤딩들
                Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
                List<Map<String, String>> headingData = new ArrayList<>();
                for (Element heading : headings) {
                    Map<String, String> headingInfo = new HashMap<>();
                    headingInfo.put("tag", heading.tagName());
                    headingInfo.put("text", heading.text());
                    headingInfo.put("id", heading.attr("id"));
                    headingData.add(headingInfo);
                }
                detailedData.put("headings", headingData);
                
                // 크롤링 시간 기록
                detailedData.put("crawled_at", new Date().toString());
                
                // 파일명 생성 및 저장
                String sanitizedTitle = sanitizeFileName(title);
                String fileName = "detailed_meme_data/" + sanitizedTitle + ".json";
                
                File outputFile = new File(fileName);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, detailedData);
                
                System.out.println("Saved detailed data to: " + fileName);
                
            } catch (Exception e) {
                System.err.println("Error crawling detailed data for " + title + " (" + url + "): " + e.getMessage());
            }
        }
        
        System.out.println("Detailed crawling completed!");
    }
}