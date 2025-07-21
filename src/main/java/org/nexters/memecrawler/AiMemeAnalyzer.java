package org.nexters.memecrawler;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.github.cdimascio.dotenv.Dotenv;

public class AiMemeAnalyzer {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY");

    public static void main(String[] args) {
        Client client = Client.builder().apiKey(API_KEY).build();

        GenerateContentResponse response =
            client.models.generateContent(
                "gemini-2.5-flash",
                "Explain how AI works in a few words",
                null);

        System.out.println("response = " + response.text());
    }
}
