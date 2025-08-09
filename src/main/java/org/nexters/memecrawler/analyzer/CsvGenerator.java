package org.nexters.memecrawler.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nexters.memecrawler.config.CrawlerConfig;
import org.nexters.memecrawler.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

public class CsvGenerator {
    
    public static void generateCsvFromJsonQueue(ConcurrentLinkedQueue<String> jsonResults) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder csvContent = new StringBuilder();

            csvContent.append("title,origin,usageContext,trendPeriod,imgUrl,hashtags\n");

            for (String jsonResult : jsonResults) {
                try {
                    JsonNode node = mapper.readTree(jsonResult);

                    String[] fields = {
                        JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "title")),
                        JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "origin")),
                        JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "usageContext")),
                        JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "trendPeriod")),
                        JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "imgUrl")),
                        JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "hashtags"))
                    };

                    csvContent.append(String.join(",", fields)).append("\n");

                } catch (Exception e) {
                    System.err.println("JSON 파싱 오류: " + e.getMessage());
                    System.err.println("JSON 내용: " + jsonResult);
                }
            }

            FileUtils.saveTextToFile(csvContent.toString(), CrawlerConfig.CSV_OUTPUT_FILE);

            System.out.println("CSV 파일 생성 완료: " + CrawlerConfig.CSV_OUTPUT_FILE);
            System.out.println("총 " + jsonResults.size() + "개의 밈 데이터가 CSV로 변환되었습니다.");

        } catch (Exception e) {
            System.err.println("CSV 생성 오류: " + e.getMessage());
        }
    }

    public static void regenerateCsvFromExistingJson() {
        try {
            System.out.println("=== CSV 재생성 시작 ===");
            
            Path jsonDir = Paths.get(CrawlerConfig.ANALYZED_DATA_DIR);
            if (!Files.exists(jsonDir)) {
                System.err.println(CrawlerConfig.ANALYZED_DATA_DIR + " 디렉토리가 존재하지 않습니다.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("title,origin,usageContext,trendPeriod,imgUrl,hashtags\n");

            int successCount = 0;
            int failCount = 0;
            
            try (Stream<Path> paths = Files.walk(jsonDir)) {
                for (Path jsonFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                    
                    try {
                        String jsonContent = Files.readString(jsonFile);
                        JsonNode node = mapper.readTree(jsonContent);

                        String[] fields = {
                            JsonProcessor.escapeCSV(JsonProcessor.mapFieldValue(node, "title", "name")),
                            JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "origin")),
                            JsonProcessor.escapeCSV(JsonProcessor.mapFieldValue(node, "usageContext", "meaning")),
                            JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "trendPeriod")),
                            JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "imgUrl")),
                            JsonProcessor.escapeCSV(JsonProcessor.getFieldValue(node, "hashtags"))
                        };

                        csvContent.append(String.join(",", fields)).append("\n");
                        successCount++;
                        
                        System.out.println("✅ 처리 완료: " + jsonFile.getFileName());

                    } catch (Exception e) {
                        failCount++;
                        System.err.println("❌ 처리 실패: " + jsonFile.getFileName() + " - " + e.getMessage());
                    }
                }
            }

            String regeneratedCsvFile = "meme_analysis_results_regenerated.csv";
            FileUtils.saveTextToFile(csvContent.toString(), regeneratedCsvFile);

            System.out.println("\n=== CSV 재생성 완료 ===");
            System.out.printf("✅ 성공: %d개%n", successCount);
            System.out.printf("❌ 실패: %d개%n", failCount);
            System.out.printf("📄 출력 파일: %s%n", regeneratedCsvFile);

        } catch (Exception e) {
            System.err.println("CSV 재생성 오류: " + e.getMessage());
        }
    }
}