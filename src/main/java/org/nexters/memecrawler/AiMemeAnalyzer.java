package org.nexters.memecrawler;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;

public class AiMemeAnalyzer {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY");

    public static void main(String[] args) {
        try {
            Path cleanTextDir = Paths.get("clean_text_data");
            
            if (!Files.exists(cleanTextDir)) {
                System.err.println("clean_text_data 디렉토리가 존재하지 않습니다.");
                return;
            }
            
            AtomicInteger counter = new AtomicInteger(0);
            AtomicInteger total = new AtomicInteger(0);
            
            // 먼저 총 파일 개수 계산
            try (Stream<Path> paths = Files.walk(cleanTextDir)) {
                total.set((int) paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .count());
            }
            
            System.out.println("총 " + total.get() + "개의 txt 파일을 발견했습니다.");
            
            // Stream으로 파일 처리 - 메모리 효율적
            try (Stream<Path> paths = Files.walk(cleanTextDir)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> {
                        int current = counter.incrementAndGet();
                        System.out.println("[" + current + "/" + total.get() + "] 분석 중: " + path.getFileName());
                        
                        try {
                            // 파일 내용을 한 번에 읽지 말고 필요할 때만 읽기
                            String content = Files.readString(path);
                            
                            // AI로 분석
                            String analysis = analyzeMeme(content);
                            
                            // 결과 저장
                            saveAnalysis(path.getFileName().toString(), analysis);
                            
                            System.out.println("완료: " + path.getFileName());
                            
                            // Rate limiting (0.5초 대기)
                            Thread.sleep(500);
                            
                        } catch (Exception e) {
                            System.err.println("파일 처리 오류: " + path.getFileName() + " - " + e.getMessage());
                        }
                    });
            }
            
            System.out.println("모든 파일 분석이 완료되었습니다.");
            
        } catch (Exception e) {
            System.err.println("전체 처리 오류: " + e.getMessage());
        }
    }
    
    
    private static void saveAnalysis(String originalFileName, String analysis) throws IOException {
        // analyzed_meme_data 디렉토리 생성
        Path outputDir = Paths.get("analyzed_meme_data");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        // 파일명에서 .txt 제거하고 _analyzed.md로 변경
        String newFileName = originalFileName.replace(".txt", "_analyzed.md");
        Path outputFile = outputDir.resolve(newFileName);
        
        // 분석 결과 저장
        Files.write(outputFile, analysis.getBytes());
    }

    private static String analyzeMeme(String memeContent) throws Exception {
        Client client = Client.builder().apiKey(API_KEY).build();

        GenerateContentResponse response =
            client.models.generateContent(
                "gemini-2.5-flash",
                "해당 내용을 보고 분석해서 밈에 관한 내용으로 분석해줘 그리고 해당 결과는 우선 markdown으로 정리해줘" + memeContent,
                null);

        return response.text();
    }
}
