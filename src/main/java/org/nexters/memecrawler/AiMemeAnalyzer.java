package org.nexters.memecrawler;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AiMemeAnalyzer {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY");

    private static final int TEST_LIMIT = 1;

    private static final int THREAD_POOL_SIZE = 5;

    private static final String ANALYSIS_PROMPT =
        """
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
            - **해시태그**:
            - **밈 카테고리**
            - **출처**: 나무위키
            
            """;
    private static final String SYSTEM_INSTRUCTION =
        """
            # 시스템 지침
            당신은 한국 인터넷 밈 전문 분석가입니다.
            나무위키에서 추출한 밈 데이터를 정확하고 객관적으로 분석하여 구조화된 마크다운으로 정리하세요.
            주의사항:
            1. 모든 필드를 빠짐없이 채워주세요
            2. 유행 정도는 실제 영향력을 기준으로 객관적으로 평가하세요
            3. 시기 정보는 가능한 구체적으로 작성하세요
            4. 관련 키워드는 검색 최적화를 고려하여 포함하세요
            5. 불확실한 정보는 추측하지 말고 "정보 없음"으로 표기하세요
            6. 욕설이나 혐오 표현이 포함된 밈의 경우 우선 내용을 기재하되, 분석 결과에서 해당 내용에 욕설이나 혐오 표현이 포함되어 있음을 명시하세요.
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

            System.out.println("총 " + total.get() + "개의 txt 파일을 발견했습니다. (테스트로 " + TEST_LIMIT + "개만 처리, 동시 스레드 " + THREAD_POOL_SIZE + "개)");

            // 커스텀 ForkJoinPool로 스레드 풀 크기 제한하여 병렬 처리
            ForkJoinPool customThreadPool = new ForkJoinPool(THREAD_POOL_SIZE);
            try {
                customThreadPool.submit(() -> {
                    try (Stream<Path> paths = Files.walk(cleanTextDir)) {
                        paths
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".txt"))
                            .limit(TEST_LIMIT) // 테스트를 위해 10개로 제한
                            .parallel() // 병렬 스트림 (커스텀 스레드 풀 사용)
                            .forEach(path -> {
                                int current = counter.incrementAndGet();
                                System.out.println("[" + current + "/" + TEST_LIMIT + "] 분석 시작: " + path.getFileName() + " (Thread: " + Thread.currentThread().getName() + ")");

                                try {
                                    // 파일 내용 읽기
                                    String content = Files.readString(path);


                                    // AI로 분석 (제한된 병렬 처리)
                                    String analysis = analyzeMeme(content);
                                    // 결과 저장
                                    saveAnalysis(path.getFileName().toString(), analysis);

                                    System.out.println("완료: " + path.getFileName() + " (Thread: " + Thread.currentThread().getName() + ")");

                                } catch (Exception e) {
                                    System.err.println("파일 처리 오류: " + path.getFileName() + " - " + e.getMessage());
                                }
                            });
                    } catch (Exception e) {
                        System.err.println("스트림 처리 오류: " + e.getMessage());
                    }
                }).get(); // 작업 완료까지 대기
            } catch (Exception e) {
                System.err.println("스레드 풀 오류: " + e.getMessage());
            } finally {
                customThreadPool.shutdown(); // 스레드 풀 정리
            }

            System.out.println("모든 파일 분석이 완료되었습니다.");

        } catch (Exception e) {
            System.err.println("전체 처리 오류: " + e.getMessage());
        }
    }


    private static synchronized void saveAnalysis(String originalFileName, String analysis) throws IOException {
        // analyzed_meme_data 디렉토리 생성
        Path outputDir = Paths.get("analyzed_meme_data_test");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        String newFileName = originalFileName.replace(".txt", ".md");
        Path outputFile = outputDir.resolve(newFileName);

        Files.write(outputFile, analysis.getBytes());
    }

    private static String analyzeMeme(String memeContent) {
        Client client = Client.builder().apiKey(API_KEY).build();

        GenerateContentResponse response =
            client.models.generateContent(
                "gemini-2.5-flash",
                ANALYSIS_PROMPT + memeContent,
                GenerateContentConfig.builder()
                    .temperature(0.7f)
                    .systemInstruction(Content.builder()
                        .parts(List.of(
                            Part.builder()
                                .text(
                                    SYSTEM_INSTRUCTION
                                )
                                .build()))
                        .build())
                    .build());

        return response.text();
    }
}
