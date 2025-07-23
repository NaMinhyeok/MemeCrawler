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
    
    private static final int TEST_LIMIT = 10;
    
    private static final String ANALYSIS_PROMPT =
        """
        다음 텍스트는 나무위키에서 추출한 밈 정보입니다.
        이를 분석하여 다음 정보를 Markdown 형식으로 정리해주세요:
        
        # 밈 분석 결과
        
        ## 기본 정보
        - **밈 제목**: 
        - **밈 설명**: 
        - **밈 기원**: 
        
        ## 유행 정보
        - **유행 정도**: (1-5점 점수와 설명)
        - **유행 시기**: 
        - **유행 지역**: (한국, 글로벌 등)
        
        ## 추가 정보
        - **패러디/변형된 밈**: 
        - **관련 키워드**: 
        - **출처**: 나무위키
        - 밈의 원본 소스가 되는 이미지(jpg, png, gif 등)나 동영상(mp4 등)의 URL이 있다면, 해당 URL을 포함해주세요.
        - 밈의 원본 소스가 되는 이미지나 동영상이 없다면, "원본 소스 없음"이라고 명시해주세요.
        
        """;

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
            
            // 병렬 스트림으로 파일 처리 - 테스트를 위해 10개로 제한
            try (Stream<Path> paths = Files.walk(cleanTextDir)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .limit(TEST_LIMIT) // 테스트를 위해 10개로 제한
                    .parallel() // 병렬 스트림으로 동시 처리
                    .forEach(path -> {
                        int current = counter.incrementAndGet();
                        System.out.println("[" + current + "/" + total.get() + "] 분석 시작: " + path.getFileName() + " (Thread: " + Thread.currentThread().getName() + ")");
                        
                        try {
                            // 파일 내용 읽기
                            String content = Files.readString(path);
                            
                            // AI로 분석 (병렬 처리)
                            String analysis = analyzeMeme(content);
                            
                            // 결과 저장
                            saveAnalysis(path.getFileName().toString(), analysis);
                            
                            System.out.println("완료: " + path.getFileName() + " (Thread: " + Thread.currentThread().getName() + ")");
                            
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
    
    
    private static synchronized void saveAnalysis(String originalFileName, String analysis) throws IOException {
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

    private static String analyzeMeme(String memeContent) {
        Client client = Client.builder().apiKey(API_KEY).build();

        GenerateContentResponse response =
            client.models.generateContent(
                "gemini-2.5-flash",
                ANALYSIS_PROMPT + memeContent,
                null);

        return response.text();
    }
}
