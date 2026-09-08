package com.example.ai_borsa_engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class BorsaVeriTest {
    public static void main(String[] args) {
        try {
            // Alpha Vantage Global Quote API endpoint (Örnek hisse: IBM)
            String sembol = "IBM";
            String apiKey = "demo"; // Test anahtarı
            String url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=" + sembol + "&apikey=" + apiKey;

            System.out.println("Canlı piyasa verisi çekiliyor...");

            // Java 11+ standart HttpClient kullanımı
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Gelen JSON verisini parse etme
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.body());
            JsonNode quoteNode = rootNode.path("Global Quote");

            if (!quoteNode.isMissingNode()) {
                String fiyat = quoteNode.path("05. price").asText();
                String hacim = quoteNode.path("06. volume").asText();

                System.out.println("Hisse: " + sembol);
                System.out.println("Güncel Fiyat: " + fiyat + " USD");
                System.out.println("İşlem Hacmi: " + hacim);
            } else {
                System.out.println("API Yanıtı Beklenmeyen Format: " + response.body());
            }

        } catch (Exception e) {
            System.out.println("Veri çekilirken hata oluştu: " + e.getMessage());
        }
    }
}