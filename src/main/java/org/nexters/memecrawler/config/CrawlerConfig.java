package org.nexters.memecrawler.config;

public class CrawlerConfig {
    public static final String BASE_URL = "https://namu.wiki";
    public static final String TARGET_URL = "https://namu.wiki/w/%EB%B0%88(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EC%9A%A9%EC%96%B4)/%EB%8C%80%ED%95%9C%EB%AF%BC%EA%B5%AD";
    
    public static final int RATE_LIMIT_MS = 2000;
    public static final int FAST_RATE_LIMIT_MS = 500;
    public static final int NETWORK_TIMEOUT_MS = 15000;
    
    public static final String RAW_DATA_FILE = "raw_meme_data.json";
    public static final String DETAILED_DATA_DIR = "detailed_meme_data";
    public static final String CLEAN_TEXT_DIR = "clean_text_data";
    public static final String ANALYZED_DATA_DIR = "analyzed_meme_data_json";
    public static final String CSV_OUTPUT_FILE = "meme_analysis_results.csv";
    
    public static final int THREAD_POOL_SIZE = 10;
    public static final int MAX_API_RETRIES = 3;
    public static final long API_RETRY_DELAY_MS = 2000;
}