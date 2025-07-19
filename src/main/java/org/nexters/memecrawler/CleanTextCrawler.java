package org.nexters.memecrawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * 핵심 정보만 추출하는 깔끔한 크롤러
 */
public class CleanTextCrawler {
    private static final int RATE_LIMIT_MS = 500;
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;

    public CleanTextCrawler() {
        this.objectMapper = new ObjectMapper();
    }

    private void enforceRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;

        if (timeSinceLastRequest < RATE_LIMIT_MS) {
            try {
                Thread.sleep(RATE_LIMIT_MS - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * raw_meme_data.json에서 URL들을 읽어와 핵심 텍스트만 추출
     */
    public void processRawMemeData(String inputJsonFile) throws IOException {
        System.out.println("Processing raw meme data from: " + inputJsonFile);
        
        File file = new File(inputJsonFile);
        List<Map<String, Object>> rawMemeList = objectMapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
        
        System.out.println("Found " + rawMemeList.size() + " memes to process");
        
        // 출력 디렉토리 생성
        File outputDir = new File("clean_text_data");
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
                System.out.println("Progress: " + (i + 1) + "/" + rawMemeList.size() + " - Processing: " + title);
                
                enforceRateLimit();
                
                String cleanText = crawlCleanText(url, title);
                
                // 파일명 생성 및 저장
                String sanitizedTitle = sanitizeFileName(title);
                String fileName = "clean_text_data/" + sanitizedTitle + ".txt";
                
                saveToFile(cleanText, fileName);
                System.out.println("Saved to: " + fileName);
                
            } catch (Exception e) {
                System.err.println("Error processing " + title + " (" + url + "): " + e.getMessage());
            }
        }
        
        System.out.println("Processing completed!");
    }

    /**
     * 핵심 정보만 추출
     */
    public String crawlCleanText(String url, String originalTitle) throws IOException {
        System.out.println("Crawling clean text from: " + url);
        
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();

        StringBuilder cleanText = new StringBuilder();
        
        // 제목
        cleanText.append("제목: ").append(originalTitle).append("\n");
        
        // 출처
        cleanText.append("출처: ").append(url).append("\n");
        
        // 크롤링 시간
        cleanText.append("크롤링 시간: ").append(new Date()).append("\n");
        cleanText.append("=" .repeat(80)).append("\n\n");
        
        // 페이지 제목
        String pageTitle = doc.title();
        if (pageTitle != null && !pageTitle.isEmpty()) {
            cleanText.append("페이지 제목: ").append(pageTitle).append("\n\n");
        }
        
        // 본문 내용만 추출
        Element body = doc.body();
        if (body != null) {
            // 불필요한 요소들 제거
            body.select("script, style, nav, header, footer, .advertisement, .ad, .wiki-nav, .wiki-category").remove();
            
            // 모든 텍스트 추출
            String bodyText = body.text();
            if (!bodyText.trim().isEmpty()) {
                cleanText.append("본문 내용:\n");
                cleanText.append(bodyText).append("\n");
            }
        }
        
        return cleanText.toString();
    }

    /**
     * 파일명 정리
     */
    private String sanitizeFileName(String title) {
        return title.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }

    /**
     * 파일 저장
     */
    private void saveToFile(String content, String fileName) throws IOException {
        try (FileWriter writer = new FileWriter(fileName, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) {
        CleanTextCrawler crawler = new CleanTextCrawler();
        
        try {
            System.out.println("Starting clean text crawling from raw_meme_data.json...");
            crawler.processRawMemeData("raw_meme_data.json");
            System.out.println("Crawling completed!");
            
        } catch (Exception e) {
            System.err.println("Error during crawling: " + e.getMessage());
            e.printStackTrace();
        }
    }
}