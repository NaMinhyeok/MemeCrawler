package org.nexters.memecrawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.nexters.memecrawler.config.CrawlerConfig;
import org.nexters.memecrawler.util.FileUtils;
import org.nexters.memecrawler.util.NetworkUtils;

import java.io.IOException;
import java.util.*;

public class CleanTextCrawler {
    private final NetworkUtils networkUtils;

    public CleanTextCrawler() {
        this.networkUtils = new NetworkUtils();
    }

    public void processRawMemeData(String inputJsonFile) throws IOException {
        System.out.println("Processing raw meme data from: " + inputJsonFile);
        
        List<Map<String, Object>> rawMemeList = FileUtils.loadJsonListFromFile(inputJsonFile);
        System.out.println("Found " + rawMemeList.size() + " memes to process");
        
        FileUtils.ensureDirectoryExists(CrawlerConfig.CLEAN_TEXT_DIR);
        
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
                
                networkUtils.enforceRateLimit(CrawlerConfig.FAST_RATE_LIMIT_MS);
                
                String cleanText = crawlCleanText(url, title);
                
                String sanitizedTitle = FileUtils.sanitizeFileName(title);
                String fileName = CrawlerConfig.CLEAN_TEXT_DIR + "/" + sanitizedTitle + ".txt";
                
                FileUtils.saveTextToFile(cleanText, fileName);
                System.out.println("Saved to: " + fileName);
                
            } catch (Exception e) {
                System.err.println("Error processing " + title + " (" + url + "): " + e.getMessage());
            }
        }
        
        System.out.println("Processing completed!");
    }

    public String crawlCleanText(String url, String originalTitle) throws IOException {
        System.out.println("Crawling clean text from: " + url);
        
        Document doc = networkUtils.fetchDocument(url);

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

    public static void main(String[] args) {
        CleanTextCrawler crawler = new CleanTextCrawler();
        
        try {
            System.out.println("Starting clean text crawling from raw_meme_data.json...");
            crawler.processRawMemeData(CrawlerConfig.RAW_DATA_FILE);
            System.out.println("Crawling completed!");
            
        } catch (Exception e) {
            System.err.println("Error during crawling: " + e.getMessage());
            e.printStackTrace();
        }
    }
}