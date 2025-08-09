package org.nexters.memecrawler;

import org.nexters.memecrawler.analyzer.CsvGenerator;
import org.nexters.memecrawler.analyzer.GeminiApiClient;
import org.nexters.memecrawler.analyzer.JsonProcessor;
import org.nexters.memecrawler.config.CrawlerConfig;
import org.nexters.memecrawler.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AiMemeAnalyzer {
    private static final ConcurrentLinkedQueue<String> jsonResults = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger apiRetryCount = new AtomicInteger(0);
    
    private final GeminiApiClient geminiClient;

    public AiMemeAnalyzer() {
        this.geminiClient = new GeminiApiClient(apiRetryCount);
    }

    public static void main(String[] args) {
        if (args.length > 0 && "regenerate-csv".equals(args[0])) {
            CsvGenerator.regenerateCsvFromExistingJson();
            return;
        }
        
        AiMemeAnalyzer analyzer = new AiMemeAnalyzer();
        analyzer.processAllMemeFiles();
    }

    public void processAllMemeFiles() {
        try {
            Path cleanTextDir = Paths.get(CrawlerConfig.CLEAN_TEXT_DIR);

            if (!Files.exists(cleanTextDir)) {
                System.err.println(CrawlerConfig.CLEAN_TEXT_DIR + " 디렉토리가 존재하지 않습니다.");
                return;
            }

            AtomicInteger counter = new AtomicInteger(0);
            AtomicInteger total = new AtomicInteger(0);

            total.set(countTxtFiles(cleanTextDir));
            System.out.println("총 " + total.get() + "개의 txt 파일을 발견했습니다. (동시 스레드 " + CrawlerConfig.THREAD_POOL_SIZE + "개)");

            processFilesInParallel(cleanTextDir, counter, total);
            
            printFinalStatistics(total.get());
            CsvGenerator.generateCsvFromJsonQueue(jsonResults);

        } catch (Exception e) {
            System.err.println("전체 처리 오류: " + e.getMessage());
        }
    }

    private int countTxtFiles(Path cleanTextDir) throws IOException {
        try (Stream<Path> paths = Files.walk(cleanTextDir)) {
            return (int) paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .count();
        }
    }

    private void processFilesInParallel(Path cleanTextDir, AtomicInteger counter, AtomicInteger total) {
        ForkJoinPool customThreadPool = new ForkJoinPool(CrawlerConfig.THREAD_POOL_SIZE);
        try {
            customThreadPool.submit(() -> {
                try (Stream<Path> paths = Files.walk(cleanTextDir)) {
                    paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".txt"))
                        .parallel()
                        .forEach(path -> processIndividualFile(path, counter, total));
                } catch (Exception e) {
                    System.err.println("스트림 처리 오류: " + e.getMessage());
                }
            }).get();
        } catch (Exception e) {
            System.err.println("스레드 풀 오류: " + e.getMessage());
        } finally {
            customThreadPool.shutdown();
        }
    }

    private void processIndividualFile(Path path, AtomicInteger counter, AtomicInteger total) {
        int current = counter.incrementAndGet();
        System.out.println("[" + current + "/" + total.get() + "] 분석 시작: " + path.getFileName() + 
                          " (Thread: " + Thread.currentThread().getName() + ")");

        try {
            String content = Files.readString(path);
            String rawResult = geminiClient.analyzeMeme(content);
            String jsonResult = JsonProcessor.extractJsonFromResponse(rawResult);

            jsonResults.offer(jsonResult);
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
    }

    private synchronized void saveJsonAnalysis(String originalFileName, String jsonResult) throws IOException {
        FileUtils.ensureDirectoryExists(CrawlerConfig.ANALYZED_DATA_DIR);

        String newFileName = originalFileName.replace(".txt", ".json");
        String outputFile = CrawlerConfig.ANALYZED_DATA_DIR + "/" + newFileName;

        FileUtils.saveTextToFile(jsonResult, outputFile);
    }

    private void printFinalStatistics(int total) {
        System.out.println("\n=== 분석 완료 ===");
        System.out.printf("총 처리: %d개 파일%n", total);
        System.out.printf("✅ 성공: %d개 (%.1f%%)%n", successCount.get(), 
            (double)successCount.get() / total * 100);
        System.out.printf("❌ 실패: %d개 (%.1f%%)%n", failureCount.get(), 
            (double)failureCount.get() / total * 100);
        System.out.printf("🔄 총 재시도 횟수: %d회%n", apiRetryCount.get());
    }
}