package org.nexters.memecrawler;

import org.nexters.memecrawler.crawler.BasicMemeCrawler;
import org.nexters.memecrawler.config.CrawlerConfig;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        BasicMemeCrawler crawler = new BasicMemeCrawler();
        
        try {
            System.out.println("Starting MemeCrawler...");
            
            List<String> memeLinks = crawler.crawlMemeLinks();
            
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
            
            crawler.saveRawDataToJson(rawDataList, CrawlerConfig.RAW_DATA_FILE);
            
            System.out.println("Crawling completed successfully!");
            System.out.println("Next step: Use Google Vertex AI to analyze and structure the data");
            
        } catch (Exception e) {
            System.err.println("Error during crawling: " + e.getMessage());
            e.printStackTrace();
        }
    }
}