package org.nexters.memecrawler.analyzer;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.github.cdimascio.dotenv.Dotenv;
import org.nexters.memecrawler.config.CrawlerConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GeminiApiClient {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY");
    
    private static final String ANALYSIS_PROMPT =
        """
            # Role
            당신은 밈의 역사와 문화적 맥락을 깊이 이해하는 **'밈 문화 연구가'**입니다. 사용자가 밈의 배경과 재미를 충분히 이해할 수 있도록 풍부하고 상세한 설명을 담아 지정된 JSON 형식으로 데이터를 정리해야 합니다.
            
            # Instruction
            주어진 밈에 대해 분석하고, 반드시 아래에 정의된 JSON 구조와 예시를 참고하여 응답해야 합니다. 다른 설명 없이 JSON 데이터만 응답해주세요.
            
            # JSON Structure
            {
              "title": "밈의 제목 (String)",
              "origin": "밈의 기원과 유래를 상세히 설명해주세요. (String) (최대 300자 이내)",
              "usageContext": "밈이 언제, 어떤 상황에서 사용되는지 맥락을 자세히 설명해주세요. (String) (최대 300자 이내)",
              "trendPeriod": "밈이 유행한 시기 (예: 2023,2024, 2000 와 같이 YYYY 형식으로 작성한다. 가장 유행했던 연도 하나만 기재)",
              "imgUrl": "관련 이미지 URL이 있다면 포함, 없으면 null (String)",
              "hashtags": "밈과 관련된 해시태그들을 JSON 배열 형태의 문자열로 저장 (String) (예: [\"#무한도전\", \"#무야호\", \"#최규재\", \"#알래스카\", \"#인터넷밈\", \"#유행어\", \"#환희\", \"#기쁨\", \"#감탄사\"] 형태로 작성한다.)",
            }
            
            # Example (for '무야호' meme)
            {
              "title": "무야호",
              "origin": "2010년 MBC 무한도전 알래스카 편에서 한 어르신이 '무한도전'을 '무야호'로 잘못 외친 장면에서 시작됐습니다. 이 순수한 외침이 10년이 지난 후 유튜브 알고리즘을 통해 재발견되어 폭발적으로 유행했습니다.",
              "usageContext": "예상치 못한 행운이나 큰 성취감에 벅차올라 터져 나오는 순수한 환희를 표현할 때 사용합니다. 월급날 통장을 확인했을 때, 시험 성적이 예상보다 좋을 때, 코딩한 프로그램이 에러 없이 실행될 때 등 기쁨과 놀라움이 섞인 상황에서 사용됩니다.",
              "trendPeriod": "2020년대",
              "imgUrl": null,
              "hashtags": "[\"#무한도전\", \"#무야호\", \"#최규재\", \"#알래스카\", \"#인터넷밈\", \"#유행어\", \"#환희\", \"#기쁨\", \"#감탄사\"]]"
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
        """;

    private final AtomicInteger apiRetryCount;

    public GeminiApiClient(AtomicInteger apiRetryCount) {
        this.apiRetryCount = apiRetryCount;
    }

    public String analyzeMeme(String memeContent) {
        for (int attempt = 1; attempt <= CrawlerConfig.MAX_API_RETRIES; attempt++) {
            try (Client client = Client.builder().apiKey(API_KEY).build()) {
                if (attempt > 1) {
                    System.out.println("🔄 API 호출 재시도 " + attempt + "/" + CrawlerConfig.MAX_API_RETRIES);
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
                return result != null ? result.trim() : "";
                
            } catch (Exception e) {
                apiRetryCount.incrementAndGet();
                System.err.println("🔄 API 호출 실패 (시도 " + attempt + "/" + CrawlerConfig.MAX_API_RETRIES + "): " + e.getMessage());
                
                if (attempt == CrawlerConfig.MAX_API_RETRIES) {
                    System.err.println("❌ 모든 재시도 실패, fallback JSON 반환");
                    return createFallbackJson(memeContent);
                }
                
                try {
                    System.out.println("⏳ " + (CrawlerConfig.API_RETRY_DELAY_MS * attempt / 1000) + "초 대기 후 재시도...");
                    Thread.sleep(CrawlerConfig.API_RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createFallbackJson(memeContent);
                }
            }
        }
        
        return createFallbackJson(memeContent);
    }

    private String createFallbackJson(String memeContent) {
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
              "origin": "API 호출 실패로 인해 자동 분석을 수행할 수 없었습니다.",
              "usageContext": "정보 없음",
              "trendPeriod": "정보 없음",
              "imgUrl": null,
              "hashtags": "[\"분석실패\", \"오류\"]"
            }
            """, title.replace("\"", "\\\""));
    }
}