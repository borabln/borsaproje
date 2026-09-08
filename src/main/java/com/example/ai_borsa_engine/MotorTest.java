package com.example.ai_borsa_engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class MotorTest {
    public static void main(String[] args) {
        try {
            // 1. Veri Çekme Aşaması (Alpha Vantage)
            String sembol = "IBM";
            String url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=" + sembol + "&apikey=demo";

            System.out.println("1. Canlı borsa verisi çekiliyor...");
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode quoteNode = mapper.readTree(response.body()).path("Global Quote");

            String fiyat = quoteNode.path("05. price").asText();
            String hacim = quoteNode.path("06. volume").asText();

            // 2. Yapay Zeka Analiz Aşaması (Ollama Llama 3)
            System.out.println("2. Yapay zeka motoru tetikleniyor...");
            ChatLanguageModel model = OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .modelName("llama3")
                    .temperature(0.0) // Duygusuz, net karar
                    .build();

            // Sisteme dışarıdan geldiğini varsaydığımız örnek bir haber
            String haber = "Şirketin bulut bilişim hizmetlerindeki pazar payını %15 artırdığı ve yeni bir devlet ihalesi kazandığı açıklandı.";

            // Verilerin birleştiği karar istemi (Prompt)
            String prompt = """
                    Sen acımasız ve rasyonel bir finansal analiz motorusun.
                    Hisse: %s
                    Anlık Fiyat: %s USD
                    İşlem Hacmi: %s
                    Son Haber: %s
                    
                    Görevin bu verileri değerlendirip hisse için sadece 'AL', 'SAT' veya 'BEKLE' kararı üretmektir.
                    
                    KESİN KURALLAR:
                    1. Cevabın SADECE "AL", "SAT" veya "BEKLE" kelimesiyle başlamak ZORUNDA.
                    2. Kararının sebebini anlık fiyatı, hacmi ve haberi dikkate alarak sadece tek bir Türkçe cümle ile açıkla.
                    """.formatted(sembol, fiyat, hacim, haber);

            String cevap = model.generate(prompt);
            System.out.println("\n--- MOTOR KARARI ---\n" + cevap);

        } catch (Exception e) {
            System.out.println("Sistem Hatası: " + e.getMessage());
        }
    }
}