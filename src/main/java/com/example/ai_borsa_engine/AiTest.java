package com.example.ai_borsa_engine;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class AiTest {
    public static void main(String[] args) {
        // Arka planda çalışan Ollama sunucusuna bağlanıyoruz
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .temperature(0.0) // 0.0 matematiksel/duygusuz net cevaplar içindir
                .build();

        // Test verimiz
        String haber = "Şirket bugün yaptığı bildirimde 50 milyon dolarlık yeni bir altyapı ihalesi kazandığını duyurdu.";
        String prompt = """
        Sen sadece ham metin okuyan bir finansal veri filtresisin. 
        Görevin haberin içeriğindeki şirkete etkisini analiz etmektir. Asla piyasanın bunu önceden fiyatladığını (priced in) tahmin etmeye çalışma.
        
        KESİN KURALLAR:
        1. Cevabın SADECE "POZİTİF", "NEGATİF" veya "NÖTR" kelimesiyle başlamak ZORUNDA.
        2. Açıklamanı KESİNLİKLE SADECE TÜRKÇE dilinde yap.
        3. Sadece tek bir cümle kur.
        
        Haber: 
        """ + haber;
        System.out.println("Yapay zeka düşünüyor...");

        // Komutu gönder ve cevabı al
        String cevap = model.generate(prompt);
        System.out.println("Analiz Sonucu: \n" + cevap);
    }
}