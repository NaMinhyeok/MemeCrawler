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
            # Role
            당신은 밈의 역사와 문화적 맥락을 깊이 이해하는 **'밈 문화 연구가'**입니다. 사용자가 밈의 배경과 재미를 충분히 이해할 수 있도록 풍부하고 상세한 설명을 담아 지정된 JSON 형식으로 데이터를 정리해야 합니다.
            
            # Instruction
            주어진 밈에 대해 분석하고, 반드시 아래에 정의된 JSON 구조와 예시를 참고하여 응답해야 합니다. 다른 설명 없이 JSON 데이터만 응답해주세요.
            
            # JSON Structure
            {
              "name": "밈의 공식 명칭 (String)",
              "meaning": "밈의 핵심 의미를 1~3 문장으로 설명하되, 어떤 감정이나 상황을 나타내는지 **뉘앙스를 포함하여 서술**해주세요. (String)",
              "usageExamples": [
                "단순한 문장 나열이 아닌, **어떤 상황에서 사용하면 재미있는지 맥락이 드러나는** 예시를 2~3개 작성해주세요. (String)",
                "예시 2",
                "예시 3"
              ],
              "origin": "최초 출처와 함께, **어떤 과정과 계기를 통해 유행하게 되었는지 간략한 스토리를 포함**하여 서술해주세요. (String)",
              "relatedMemes": [
                "관련/파생 밈 이름 (String)",
              ],
              "tags": [
                "밈의 특징과 카테고리를 잘 나타내는 키워드 5개 이상 (String)",
                "키워드2",
                "키워드3"
              ]
            }
            
            # Example (for '무야호' meme)
            {
              "name": "무야호",
              "meaning": "단순한 기쁨을 넘어, 예상치 못한 행운이나 큰 성취감에 벅차올라 터져 나오는 순수한 환희를 표현합니다. 약간의 어설픔이 더해져 유머러스한 느낌을 줍니다.",
              "usageExamples": [
                "월급날 통장 보고 소리 질렀다... 이것이 바로 '무야호'의 심정.",
                "친구가 노래방에서 최고점 찍고 '무야호' 외치는데 너무 웃겼어.",
                "밤새 코딩한 거 에러 없이 돌아갈 때의 그 기분? 무야호 그 자체."
              ],
              "origin": "2010년 MBC <무한도전> '알래스카' 편에서 한 어르신이 '무한도전'을 '무야호'로 잘못 외친 장면에서 시작됐습니다. 이 순수한 외침이 10년이 지난 후 유튜브 알고리즘을 통해 재발견되어 폭발적으로 유행했습니다.",
              "relatedMemes": [
                  "그만큼 신나시다는 거지"
              ],
              "tags": [
                "무한도전",
                "정형돈",
                "알래스카",
                "신남",
                "환호",
                "감탄사"
              ]
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
            6. 이미지 또는 영상으로 대표되는 밈인지 여부를 명확히 구분하고 표기하세요
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
            
            // CSV 헤더 작성 (새로운 JSON 구조에 맞게)
            csvContent.append("name,meaning,usageExamples,origin,relatedMemes,tags\n");

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
                            escapeCSV(mapFieldValue(node, "name", "title")),
                            escapeCSV(mapFieldValue(node, "meaning", "description")),
                            escapeCSV("정보 없음"), // usageExamples - 기존 데이터에 없음
                            escapeCSV(getFieldValue(node, "origin")),
                            escapeCSV(mapFieldValue(node, "relatedMemes", "related_memes")),
                            escapeCSV(mapArrayFieldValue(node, "tags", "keywords", "hashtags"))
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

                                    // 밈 분석 (모든 데이터를 새 JSON 구조로 처리)
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

            // CSV 헤더 작성 (새로운 JSON 구조에 맞게)
            csvContent.append("name,meaning,usageExamples,origin,relatedMemes,tags\n");

            // 각 JSON 결과를 CSV 로우로 변환
            for (String jsonResult : jsonResults) {
                try {
                    JsonNode node = mapper.readTree(jsonResult);

                    String[] fields = {
                        escapeCSV(getFieldValue(node, "name")),
                        escapeCSV(getFieldValue(node, "meaning")),
                        escapeCSV(getArrayFieldAsString(node, "usageExamples")),
                        escapeCSV(getFieldValue(node, "origin")),
                        escapeCSV(getArrayFieldAsString(node, "relatedMemes")),
                        escapeCSV(getArrayFieldAsString(node, "tags"))
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
     * 기존 스키마 → 새 스키마 필드 매핑 (단일 값)
     */
    private static String mapFieldValue(JsonNode node, String newFieldName, String oldFieldName) {
        // 먼저 새 필드명으로 시도
        JsonNode newField = node.path(newFieldName);
        if (!newField.isMissingNode() && !newField.isNull()) {
            String value = newField.asText();
            return value.isEmpty() ? "정보 없음" : value;
        }
        
        // 새 필드가 없으면 기존 필드명으로 시도
        JsonNode oldField = node.path(oldFieldName);
        if (!oldField.isMissingNode() && !oldField.isNull()) {
            String value = oldField.asText();
            return value.isEmpty() ? "정보 없음" : value;
        }
        
        return "정보 없음";
    }
    
    /**
     * 배열 필드 매핑 (keywords, hashtags, category 등을 tags로 통합)
     */
    private static String mapArrayFieldValue(JsonNode node, String newFieldName, String... oldFieldNames) {
        // 먼저 새 필드명으로 시도
        JsonNode newField = node.path(newFieldName);
        if (!newField.isMissingNode() && !newField.isNull() && newField.isArray() && !newField.isEmpty()) {
            return getArrayFieldAsString(node, newFieldName);
        }
        
        // 기존 필드들을 순서대로 시도하고 결합
        StringBuilder combined = new StringBuilder();
        for (String oldFieldName : oldFieldNames) {
            JsonNode oldField = node.path(oldFieldName);
            if (!oldField.isMissingNode() && !oldField.isNull()) {
                if (oldField.isArray() && !oldField.isEmpty()) {
                    if (combined.length() > 0) combined.append(" | ");
                    combined.append(getArrayFieldAsString(node, oldFieldName));
                }
            }
        }
        
        return combined.length() > 0 ? combined.toString() : "정보 없음";
    }

    /**
     * JSON 노드에서 필드 값을 안전하게 추출 (단일 값)
     */
    private static String getFieldValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return "정보 없음";
        }
        
        String value = fieldNode.asText();
        return value.isEmpty() ? "정보 없음" : value;
    }
    
    /**
     * 배열 필드를 문자열로 변환 (복합 객체 배열 포함)
     */
    private static String getArrayFieldAsString(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull() || !fieldNode.isArray()) {
            return "정보 없음";
        }
        
        if (fieldNode.size() == 0) {
            return "정보 없음";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldNode.size(); i++) {
            if (i > 0) sb.append(" | ");
            
            JsonNode item = fieldNode.get(i);
            // 모든 배열 필드를 단순 문자열로 처리 (relatedMemes도 이제 문자열 배열)
            sb.append(item.asText());
        }
        return sb.toString();
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


    /**
     * 밈 분석 (모든 데이터를 새 JSON 구조로 처리)
     * @return JSON 문자열
     */
    private static String analyzeMeme(String memeContent) {
        int maxRetries = 3;
        long retryDelayMs = 2000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Client client = Client.builder().apiKey(API_KEY).build()) {
                if (attempt > 1) {
                    System.out.println("🔄 API 호출 재시도 " + attempt + "/" + maxRetries);
                }
                
                GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.5-flash",
                    ANALYSIS_PROMPT + memeContent,
                    GenerateContentConfig.builder()
                        .temperature(0.3f)
                        .systemInstruction(Content.builder()
                            .parts(List.of(
                                Part.builder()
                                    .text(SYSTEM_INSTRUCTION)
                                    .build()))
                            .build())
                        .build());

                String result = response.text();
                if (result == null) result = "";
                return result.trim();
                
            } catch (Exception e) {
                apiRetryCount.incrementAndGet();
                System.err.println("🔄 API 호출 실패 (시도 " + attempt + "/" + maxRetries + "): " + e.getMessage());
                
                if (attempt == maxRetries) {
                    System.err.println("❌ 모든 재시도 실패, fallback JSON 반환");
                    return createFallbackJson(memeContent);
                }
                
                try {
                    System.out.println("⏳ " + (retryDelayMs * attempt / 1000) + "초 대기 후 재시도...");
                    Thread.sleep(retryDelayMs * attempt);
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
              "name": "%s",
              "meaning": "API 호출 실패로 인해 자동 분석을 수행할 수 없었습니다.",
              "usageExamples": [],
              "origin": "정보 없음",
              "relatedMemes": [],
              "tags": ["분석실패", "오류"]
            }
            """, title.replace("\"", "\\\""));
    }
}
