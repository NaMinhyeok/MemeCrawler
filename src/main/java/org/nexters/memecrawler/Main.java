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
            System.out.println("Starting to crawl " + memeLinks.size() + " meme pages...");
            
            for (int i = 0; i < memeLinks.size(); i++) {
                String link = memeLinks.get(i);
                try {
                    System.out.println("Progress: " + (i + 1) + "/" + memeLinks.size() + " - " + link);
                    Map<String, Object> rawData = crawler.crawlRawMemeData(link);
                    rawDataList.add(rawData);
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