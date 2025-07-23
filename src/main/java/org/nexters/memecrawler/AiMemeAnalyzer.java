package org.nexters.memecrawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AiMemeAnalyzer {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY");

    private static final int TEST_LIMIT = 10;

    private static final int THREAD_POOL_SIZE = 10;

    // JSON 결과를 저장할 스레드 세이프 큐
    private static final ConcurrentLinkedQueue<String> jsonResults = new ConcurrentLinkedQueue<>();
    
    // 통계 추적을 위한 카운터들
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger apiRetryCount = new AtomicInteger(0);

    private static final String ANALYSIS_PROMPT =
        """
            다음 내용을 분석하여 아래 JSON 스키마에 맞게 정확히 응답해주세요.
            반드시 valid JSON 형식으로만 응답하고, 다른 설명이나 마크다운은 포함하지 마세요.
            
            {
              "title": "밈 제목",
              "description": "밈 설명",
              "origin": "밈의 기원이나 출처",
              "popularity_score": "유행 정도 (1-5점)",
              "popularity_period": "유행 시기 (YYYY.MM 또는 YYYY.MM-YYYY.MM 또는 YYYY.MM-현재 형식 하지만 해당 형식으로 표현 하지 못할 경우 YYYY 형식으로 작성)",
              "popularity_region": "유행 지역 (국내/해외/글로벌 등)",
              "related_memes": "관련된 또는 파생된 밈들",
              "keywords": "검색 키워드 (배열에 담는다)",
              "hashtags": "해시태그 (배열에 담는다)",
              "category": "밈 카테고리 (배열에 담는다)",
              "source_url": "나무위키 URL",
              "media_urls": "관련 이미지/동영상 URL (있는 경우)"
            }
            
            분석할 내용:
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
        
            밈 유행도 점수 기준 (매우 엄격하게 적용):
        
            **1점 (틈새 밈)**:\s
            - 특정 소규모 커뮤니티에서만 사용 (회원 수 1만명 이하)
            - 일반인이 전혀 모르는 밈
            - 사용 기간 1개월 미만
            - 예: 특정 게임의 매니아층에서만 쓰이는 밈

            **2점 (커뮤니티 밈)**:
            - 특정 대형 커뮤니티에서 유행 (디시 특정 갤러리, 특정 유튜버 팬덤 등)
            - 해당 분야에 관심 있는 사람들은 알지만 일반인은 모름
            - 사용 기간 1-3개월
            - 예: 특정 게임 갤러리에서 유행한 밈, 특정 스트리머 방송에서 나온 밈

            **3점 (온라인 밈)**:
            - 여러 온라인 커뮤니티에서 확산 (디시 여러 갤러리, 레딧, 트위터 등)
            - 인터넷을 자주 하는 사람들은 대부분 알고 있음
            - 사용 기간 3-6개월, 패러디나 변형 버전 존재
            - 예: 인터넷 밈으로 자리잡았지만 오프라인까지는 안 간 것들

            **4점 (대중 밈)**:
            - TV, 라디오 등 주류 미디어에서 언급
            - 중장년층도 어느 정도 인지
            - 연예인들이 사용하거나 광고에 활용
            - 사용 기간 6개월 이상, 지속적인 변형과 재생산
            - 예: "무야호", "극혐", "갓벽" 등

            **5점 (사회 현상급)**:
            - 전 연령층이 알고 있음 (부모님도 아는 수준)
            - 뉴스에서 다룰 정도로 사회적 이슈가 됨
            - 교육 현장, 정치, 광고 등에서 광범위하게 사용
            - 사전에 등재되거나 학술적 연구 대상이 됨
            - 사용 기간 1년 이상, 문화적 현상으로 정착
            - 예: "대박", "헐", "ㅋㅋㅋ" 등 (극소수만 해당)

            **중요**: 대부분의 밈은 1-3점 사이입니다. 4-5점은 정말 예외적인 경우에만 사용하세요.
    """;

    /**
     * 기존 JSON 파일들로부터 CSV를 재생성하는 독립 실행 메서드
     */
    public static void regenerateCsvFromExistingJson() {
        try {
            System.out.println("=== CSV 재생성 시작 ===");
            
            Path jsonDir = Paths.get("analyzed_meme_data_json");
            if (!Files.exists(jsonDir)) {
                System.err.println("analyzed_meme_data_json 디렉토리가 존재하지 않습니다.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            StringBuilder csvContent = new StringBuilder();
            
            // CSV 헤더 작성
            csvContent.append("title,description,origin,popularity_score,popularity_period,popularity_region,related_memes,keywords,hashtags,category,source_url,media_urls\n");

            int successCount = 0;
            int failCount = 0;
            
            // JSON 디렉토리의 모든 JSON 파일 처리
            try (Stream<Path> paths = Files.walk(jsonDir)) {
                for (Path jsonFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                    
                    try {
                        String jsonContent = Files.readString(jsonFile);
                        JsonNode node = mapper.readTree(jsonContent);

                        String[] fields = {
                            escapeCSV(getFieldValue(node, "title")),
                            escapeCSV(getFieldValue(node, "description")),
                            escapeCSV(getFieldValue(node, "origin")),
                            escapeCSV(getFieldValue(node, "popularity_score")),
                            escapeCSV(getFieldValue(node, "popularity_period")),
                            escapeCSV(getFieldValue(node, "popularity_region")),
                            escapeCSV(getFieldValue(node, "related_memes")),
                            escapeCSV(getFieldValue(node, "keywords")),
                            escapeCSV(getFieldValue(node, "hashtags")),
                            escapeCSV(getFieldValue(node, "category")),
                            escapeCSV(getFieldValue(node, "source_url")),
                            escapeCSV(getFieldValue(node, "media_urls"))
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

            // CSV 파일 저장
            Path csvFile = Paths.get("meme_analysis_results_regenerated.csv");
            Files.write(csvFile, csvContent.toString().getBytes());

            System.out.println("\n=== CSV 재생성 완료 ===");
            System.out.printf("✅ 성공: %d개%n", successCount);
            System.out.printf("❌ 실패: %d개%n", failCount);
            System.out.printf("📄 출력 파일: %s%n", csvFile.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("CSV 재생성 오류: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // 인자에 따라 다른 동작 수행
        if (args.length > 0 && "regenerate-csv".equals(args[0])) {
            regenerateCsvFromExistingJson();
            return;
        }
        
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

            System.out.println("총 " + total.get() + "개의 txt 파일을 발견했습니다. (동시 스레드 " + THREAD_POOL_SIZE + "개)");

            // 커스텀 ForkJoinPool로 스레드 풀 크기 제한하여 병렬 처리
            ForkJoinPool customThreadPool = new ForkJoinPool(THREAD_POOL_SIZE);
            try {
                customThreadPool.submit(() -> {
                    try (Stream<Path> paths = Files.walk(cleanTextDir)) {
                        paths
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".txt"))
                            .parallel() // 병렬 스트림 (커스텀 스레드 풀 사용)
                            .forEach(path -> {
                                int current = counter.incrementAndGet();
                                System.out.println("[" + current + "/" + total.get() + "] 분석 시작: " + path.getFileName() + " (Thread: " + Thread.currentThread().getName() + ")");

                                try {
                                    // 파일 내용 읽기
                                    String content = Files.readString(path);


                                    // AI로 분석 (제한된 병렬 처리)
                                    String rawResult = analyzeMeme(content);
                                    
                                    // 마크다운 코드 블록 제거 및 JSON 추출
                                    String jsonResult = extractJsonFromResponse(rawResult);

                                    // JSON 결과를 큐에 저장
                                    jsonResults.offer(jsonResult);

                                    // 개별 JSON 파일로도 저장 (정제된 JSON 저장)
                                    saveJsonAnalysis(path.getFileName().toString(), jsonResult);
                                    
                                    successCount.incrementAndGet();
                                    System.out.printf("✅ [%d/%d] 완료: %s (성공:%d, 실패:%d, 재시도:%d)%n", 
                                        current, total.get(), path.getFileName(), 
                                        successCount.get(), failureCount.get(), apiRetryCount.get());

                                } catch (Exception e) {
                                    failureCount.incrementAndGet();
                                    System.err.printf("❌ [%d/%d] 실패: %s - %s (성공:%d, 실패:%d, 재시도:%d)%n", 
                                        current, total.get(), path.getFileName(), e.getMessage(),
                                        successCount.get(), failureCount.get(), apiRetryCount.get());
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

            // 최종 통계 출력
            System.out.println("\n=== 분석 완료 ===");
            System.out.printf("총 처리: %d개 파일%n", total.get());
            System.out.printf("✅ 성공: %d개 (%.1f%%)%n", successCount.get(), 
                (double)successCount.get() / total.get() * 100);
            System.out.printf("❌ 실패: %d개 (%.1f%%)%n", failureCount.get(), 
                (double)failureCount.get() / total.get() * 100);
            System.out.printf("🔄 총 재시도 횟수: %d회%n", apiRetryCount.get());

            // 모든 JSON 결과를 CSV로 변환
            generateCsvFromJson();

        } catch (Exception e) {
            System.err.println("전체 처리 오류: " + e.getMessage());
        }
    }


    private static synchronized void saveJsonAnalysis(String originalFileName, String jsonResult) throws IOException {
        // analyzed_meme_data 디렉토리 생성
        Path outputDir = Paths.get("analyzed_meme_data_json");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        String newFileName = originalFileName.replace(".txt", ".json");
        Path outputFile = outputDir.resolve(newFileName);

        Files.write(outputFile, jsonResult.getBytes());
    }

    private static void generateCsvFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder csvContent = new StringBuilder();

            // CSV 헤더 작성
            csvContent.append("title,description,origin,popularity_score,popularity_period,popularity_region,related_memes,keywords,hashtags,category,source_url,media_urls\n");

            // 각 JSON 결과를 CSV 로우로 변환
            for (String jsonResult : jsonResults) {
                try {
                    JsonNode node = mapper.readTree(jsonResult);

                    String[] fields = {
                        escapeCSV(getFieldValue(node, "title")),
                        escapeCSV(getFieldValue(node, "description")),
                        escapeCSV(getFieldValue(node, "origin")),
                        escapeCSV(getFieldValue(node, "popularity_score")),
                        escapeCSV(getFieldValue(node, "popularity_period")),
                        escapeCSV(getFieldValue(node, "popularity_region")),
                        escapeCSV(getFieldValue(node, "related_memes")),
                        escapeCSV(getFieldValue(node, "keywords")),
                        escapeCSV(getFieldValue(node, "hashtags")),
                        escapeCSV(getFieldValue(node, "category")),
                        escapeCSV(getFieldValue(node, "source_url")),
                        escapeCSV(getFieldValue(node, "media_urls"))
                    };

                    csvContent.append(String.join(",", fields)).append("\n");

                } catch (Exception e) {
                    System.err.println("JSON 파싱 오류: " + e.getMessage());
                    System.err.println("JSON 내용: " + jsonResult);
                }
            }

            // CSV 파일 저장
            Path csvFile = Paths.get("meme_analysis_results.csv");
            Files.write(csvFile, csvContent.toString().getBytes());

            System.out.println("CSV 파일 생성 완료: " + csvFile.toAbsolutePath());
            System.out.println("총 " + jsonResults.size() + "개의 밈 데이터가 CSV로 변환되었습니다.");

        } catch (Exception e) {
            System.err.println("CSV 생성 오류: " + e.getMessage());
        }
    }

    /**
     * JSON 노드에서 필드 값을 안전하게 추출 (배열인 경우 문자열로 변환)
     */
    private static String getFieldValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return "정보 없음";
        }
        
        if (fieldNode.isArray()) {
            // 배열인 경우 쉼표로 구분된 문자열로 변환
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fieldNode.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(fieldNode.get(i).asText());
            }
            return sb.toString();
        }
        
        String value = fieldNode.asText();
        return value.isEmpty() ? "정보 없음" : value;
    }
    
    private static String escapeCSV(String value) {
        if (value == null || value.isEmpty()) return "정보 없음";

        // 따옴표, 쉽표, 줄바꿈이 있으면 따옴표로 감싸고 내부 따옴표를 이스케이프
        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Gemini 응답에서 JSON 마크다운 코드 블록을 제거하고 순수 JSON만 추출
     */
    private static String extractJsonFromResponse(String response) {
        if (response == null) {
            return "{}";
        }
        
        // 마크다운 코드 블록 제거
        String cleaned = response
            .replaceAll("```json\\s*", "") // ```json 제거
            .replaceAll("```\\s*$", "")     // 마지막 ``` 제거
            .trim();
        
        // JSON 오브젝트를 찾아서 추출
        int startIndex = cleaned.indexOf('{');
        int endIndex = cleaned.lastIndexOf('}');
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            String extracted = cleaned.substring(startIndex, endIndex + 1);
            System.out.println("추출된 JSON: " + extracted.substring(0, Math.min(100, extracted.length())) + "...");
            return extracted;
        }
        
        // JSON을 찾을 수 없는 경우 전체 응답 반환
        System.err.println("JSON 추출 실패, 원본 응답 사용: " + cleaned.substring(0, Math.min(200, cleaned.length())));
        return cleaned;
    }

    private static String analyzeMeme(String memeContent) {
        int maxRetries = 3;
        long retryDelayMs = 2000; // 2초 대기
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Client client = Client.builder().apiKey(API_KEY).build()) {
                if (attempt > 1) {
                    System.out.println("🔄 API 호출 재시도 " + attempt + "/" + maxRetries + " (Thread: " + Thread.currentThread().getName() + ")");
                }
                
                GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.5-flash",
                    ANALYSIS_PROMPT + memeContent,
                    GenerateContentConfig.builder()
                        .temperature(0.7f)
                        .systemInstruction(Content.builder()
                            .parts(List.of(
                                Part.builder()
                                    .text(SYSTEM_INSTRUCTION)
                                    .build()))
                            .build())
                        .build());

                return response.text();
                
            } catch (Exception e) {
                apiRetryCount.incrementAndGet();
                System.err.println("🔄 API 호출 실패 (시도 " + attempt + "/" + maxRetries + "): " + e.getMessage());
                
                if (attempt == maxRetries) {
                    System.err.println("❌ 모든 재시도 실패, fallback JSON 반환");
                    return createFallbackJson(memeContent);
                }
                
                try {
                    System.out.println("⏳ " + (retryDelayMs * attempt / 1000) + "초 대기 후 재시도...");
                    Thread.sleep(retryDelayMs * attempt); // 지수 백오프
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createFallbackJson(memeContent);
                }
            }
        }
        
        return createFallbackJson(memeContent);
    }
    
    /**
     * API 호출 실패 시 기본적인 fallback JSON 생성
     */
    private static String createFallbackJson(String memeContent) {
        // 내용에서 제목 추출 시도 (첫 번째 줄 또는 첫 번째 문장)
        String title = "알 수 없는 밈";
        if (memeContent != null && !memeContent.trim().isEmpty()) {
            String[] lines = memeContent.split("\n");
            if (lines.length > 0) {
                String firstLine = lines[0].trim();
                if (!firstLine.isEmpty() && firstLine.length() < 100) {
                    title = firstLine;
                }
            }
        }
        
        return String.format("""
            {
              "title": "%s",
              "description": "API 호출 실패로 인해 자동 분석을 수행할 수 없었습니다.",
              "origin": "정보 없음",
              "popularity_score": "1",
              "popularity_period": "정보 없음",
              "popularity_region": "정보 없음",
              "related_memes": "정보 없음",
              "keywords": "정보 없음",
              "hashtags": "정보 없음",
              "category": "분석 실패",
              "source_url": "정보 없음",
              "media_urls": "정보 없음"
            }
            """, title.replace("\"", "\\\""));
    }
}
