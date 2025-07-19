package org.nexters.memecrawler;

import org.nexters.memecrawler.crawler.MemeCrawler;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        MemeCrawler crawler = new MemeCrawler();
        
        try {
            System.out.println("Starting MemeCrawler...");
            
            // 1단계: 메인 페이지에서 하이퍼링크 크롤링
            List<String> memeLinks = crawler.crawlMemeLinks();
            
            // 2단계: 각 링크의 원시 데이터 크롤링
            List<Map<String, Object>> rawDataList = new ArrayList<>();
            for (String link : memeLinks) {
                try {
                    Map<String, Object> rawData = crawler.crawlRawMemeData(link);
                    rawDataList.add(rawData);
                    
                    // 너무 많은 데이터를 한 번에 크롤링하지 않도록 제한 (테스트용)
                    if (rawDataList.size() >= 10) {
                        System.out.println("Limited to first 10 memes for testing");
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("Error crawling " + link + ": " + e.getMessage());
                }
            }
            
            // 3단계: 원시 데이터를 JSON으로 저장
            crawler.saveRawDataToJson(rawDataList, "raw_meme_data.json");
            
            System.out.println("Crawling completed successfully!");
            System.out.println("Next step: Use Google Vertex AI to analyze and structure the data");
            
        } catch (Exception e) {
            System.err.println("Error during crawling: " + e.getMessage());
            e.printStackTrace();
        }
    }
}