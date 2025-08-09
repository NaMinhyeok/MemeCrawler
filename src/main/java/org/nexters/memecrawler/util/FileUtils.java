package org.nexters.memecrawler.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class FileUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_")
                      .replaceAll("\\s+", "_")
                      .trim();
    }

    public static void ensureDirectoryExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    public static void saveJsonToFile(Object data, String fileName) throws IOException {
        File file = new File(fileName);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
    }

    public static List<Map<String, Object>> loadJsonListFromFile(String fileName) throws IOException {
        File file = new File(fileName);
        return objectMapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
    }

    public static void saveTextToFile(String content, String fileName) throws IOException {
        try (FileWriter writer = new FileWriter(fileName, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}