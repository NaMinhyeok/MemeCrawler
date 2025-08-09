package org.nexters.memecrawler.analyzer;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonProcessor {
    
    public static String extractJsonFromResponse(String response) {
        if (response == null) {
            return "{}";
        }
        
        String cleaned = response
            .replaceAll("```json\\s*", "")
            .replaceAll("```\\s*$", "")
            .trim();
        
        int startIndex = cleaned.indexOf('{');
        int endIndex = cleaned.lastIndexOf('}');
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            String extracted = cleaned.substring(startIndex, endIndex + 1);
            System.out.println("추출된 JSON: " + extracted.substring(0, Math.min(100, extracted.length())) + "...");
            return extracted;
        }
        
        System.err.println("JSON 추출 실패, 원본 응답 사용: " + cleaned.substring(0, Math.min(200, cleaned.length())));
        return cleaned;
    }

    public static String getFieldValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return "정보 없음";
        }
        
        String value = fieldNode.asText();
        return value.isEmpty() ? "정보 없음" : value;
    }

    public static String mapFieldValue(JsonNode node, String newFieldName, String oldFieldName) {
        JsonNode newField = node.path(newFieldName);
        if (!newField.isMissingNode() && !newField.isNull()) {
            String value = newField.asText();
            return value.isEmpty() ? "정보 없음" : value;
        }
        
        JsonNode oldField = node.path(oldFieldName);
        if (!oldField.isMissingNode() && !oldField.isNull()) {
            String value = oldField.asText();
            return value.isEmpty() ? "정보 없음" : value;
        }
        
        return "정보 없음";
    }

    public static String escapeCSV(String value) {
        if (value == null || value.isEmpty()) return "정보 없음";

        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}