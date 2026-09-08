package com.example.ai_borsa_engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class BorsaController {

    // 1. Ana Analiz ve Tarama Endpoint'i
    @GetMapping("/api/analiz")
    public Map<String, String> analizEt(@RequestParam(defaultValue = "TSLA") String sembol) {
        Map<String, String> sonuc = new HashMap<>();
        sembol = sembol.toUpperCase().trim();

        try {
            String apiKey = "SC9CE10UOQ7YEJG3";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            ObjectMapper mapper = new ObjectMapper();

            // Özel Komut: Halka Arzlar
            if (sembol.equals("HALKAARZ") || sembol.equals("IPO")) {
                sonuc.put("sembol", "BIST HALKA ARZ TAKVİMİ");
                sonuc.put("fiyat", "GÜNCEL LİSTE");
                sonuc.put("hacim", "Aktif Sermaye");
                sonuc.put("haber", "<b>Borsa İstanbul Güncel Halka Arz Durumu:</b><br>- SPK onaylı aktif talep toplama takvimi sistemde taranıyor.<br>- Şirketlerin fon kullanım yerleri ve taahhütnameleri konsolide ediliyor.");
                sonuc.put("yapayZekaKarari", "BEKLE - Halka arz modülü bir şirket hissesi değildir. Yatırım kararı için şirket izahnamelerini inceleyiniz.");
                return sonuc;
            }

            // Özel Komut: Alpha Screener
            if (sembol.equals("ENIYI") || sembol.equals("SCREENER") || sembol.equals("TOP")) {
                String[] taramaListesi = {"TSLA", "AAPL", "MSFT", "NVDA", "AMZN"};
                String enIyiSembol = "TSLA";
                double enIyiSkor = -999.0;
                StringBuilder screenerRaporu = new StringBuilder("<b>Alpha Screener - Portföy Tarama Raporu:</b><br>");

                for (String s : taramaListesi) {
                    try {
                        String tUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + s + "&apikey=" + apiKey;
                        HttpResponse<String> tResp = client.send(HttpRequest.newBuilder().uri(URI.create(tUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
                        JsonNode tNode = mapper.readTree(tResp.body()).path("Time Series (Daily)");

                        if (!tNode.isMissingNode() && !tNode.isEmpty()) {
                            List<Double> closes = new ArrayList<>();
                            var flds = tNode.fields();
                            int c = 0;
                            while (flds.hasNext() && c < 200) {
                                closes.add(flds.next().getValue().path("4. close").asDouble());
                                c++;
                            }

                            double p = closes.get(0);
                            double r = hesaplaRSI(closes, 14);
                            double s50 = hesaplaSMA(closes, 50);
                            double s200 = hesaplaSMA(closes, Math.min(closes.size(), 200));
                            boolean uptrend = p > s50 && s50 > s200;

                            double skor = (uptrend ? 50 : 0) + (r < 40 ? 30 : (r > 70 ? -30 : 10));
                            screenerRaporu.append("- <b>").append(s).append("</b> -> Fiyat: ").append(String.format("%.2f", p)).append(" USD | RSI: ").append(String.format("%.2f", r)).append(" | Trend: ").append(uptrend ? "Boğa" : "Ayı").append(" | Skor: ").append(skor).append("<br>");

                            if (skor > enIyiSkor) {
                                enIyiSkor = skor;
                                enIyiSembol = s;
                            }
                        }
                    } catch (Exception ex) {}
                }

                sonuc.put("sembol", "ALPHA-SCREENER");
                sonuc.put("fiyat", "Şampiyon: " + enIyiSembol);
                sonuc.put("hacim", "Optimum Sinyal");
                sonuc.put("haber", screenerRaporu.toString() + "<br><b>🏆 Seçilen Fırsat:</b> " + enIyiSembol);
                sonuc.put("yapayZekaKarari", "BEKLE - Screener taraması tamamlandı. Detaylı analiz için şampiyon varlığı aratabilirsiniz.");
                return sonuc;
            }

            // Özel Komut: Çoklu Sepet
            if (sembol.equals("SEPET") || sembol.equals("WATCHLIST")) {
                String[] takipListesi = {"TSLA", "AAPL", "MSFT", "NVDA", "AMZN"};
                StringBuilder sepetRaporu = new StringBuilder("<b>Küresel Portföy Sepet Taraması:</b><br>");

                for (String s : takipListesi) {
                    try {
                        String tUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + s + "&apikey=" + apiKey;
                        HttpResponse<String> tResp = client.send(HttpRequest.newBuilder().uri(URI.create(tUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
                        JsonNode tNode = mapper.readTree(tResp.body()).path("Time Series (Daily)");

                        if (!tNode.isMissingNode() && !tNode.isEmpty()) {
                            List<Double> closes = new ArrayList<>();
                            var flds = tNode.fields();
                            int c = 0;
                            while (flds.hasNext() && c < 30) {
                                closes.add(flds.next().getValue().path("4. close").asDouble());
                                c++;
                            }
                            double rsiVal = hesaplaRSI(closes, 14);
                            double priceVal = closes.get(0);
                            sepetRaporu.append("- <b>").append(s).append("</b> -> Fiyat: ").append(String.format("%.2f", priceVal)).append(" USD | RSI(14): ").append(String.format("%.2f", rsiVal)).append("<br>");
                        }
                    } catch (Exception ex) {}
                }

                sonuc.put("sembol", "MULTI-SEPET");
                sonuc.put("fiyat", "Sepet Özeti");
                sonuc.put("hacim", "Çoklu Akış");
                sonuc.put("haber", sepetRaporu.toString());
                sonuc.put("yapayZekaKarari", "BEKLE - Sepet taraması tamamlandı. Genel durum yukarıda listelenmiştir.");
                return sonuc;
            }

            // BIST Hisseleri Modülü
            boolean bistMi = sembol.endsWith(".IS") || sembol.equals("AKBNK") || sembol.equals("THYAO") || sembol.equals("GARAN") || sembol.equals("EREGL") || sembol.equals("ASELS") || sembol.equals("SASA");
            if (bistMi) {
                if (!sembol.endsWith(".IS")) sembol = sembol + ".IS";
                sonuc.put("sembol", sembol);
                sonuc.put("fiyat", "BIST Canlı");
                sonuc.put("hacim", "Piyasa Hacmi");
                sonuc.put("haber", "Borsa İstanbul şirketi için teknik veriler taranıyor.");

                ChatLanguageModel model = OllamaChatModel.builder().baseUrl("http://localhost:11434").modelName("llama3").temperature(0.0).timeout(Duration.ofMinutes(3)).build();
                sonuc.put("yapayZekaKarari", model.generate(String.format("Sen BIST kantitatif analistisin. Hedef: %s. Teknik yapıyı analiz et.", sembol)));
                return sonuc;
            }

            sonuc.put("sembol", sembol);

            // 1. MAKRO-EKONOMİK FARKINDALIK (S&P 500 ENDEKSİ ÇEKİLİYOR)
            String makroTrend = "BİLİNMİYOR";
            try {
                String spyUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=SPY&apikey=" + apiKey;
                HttpResponse<String> spyResp = client.send(HttpRequest.newBuilder().uri(URI.create(spyUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
                JsonNode spyNode = mapper.readTree(spyResp.body()).path("Time Series (Daily)");

                if (!spyNode.isMissingNode() && !spyNode.isEmpty()) {
                    List<Double> spyCloses = new ArrayList<>();
                    var spyFlds = spyNode.fields();
                    int c = 0;
                    while (spyFlds.hasNext() && c < 200) {
                        spyCloses.add(spyFlds.next().getValue().path("4. close").asDouble());
                        c++;
                    }
                    if (spyCloses.size() >= 50) {
                        double spySma50 = hesaplaSMA(spyCloses, 50);
                        double spySma200 = hesaplaSMA(spyCloses, Math.min(spyCloses.size(), 200));
                        makroTrend = (spyCloses.get(0) > spySma50 && spySma50 > spySma200) ? "BOĞA (BULL)" : "AYI (BEAR)";
                    }
                }
            } catch (Exception ex) {
                makroTrend = "Veri Çekilemedi";
            }

            // Küresel Hisse Zaman Serisi ve Teknik Veriler
            String tsUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + sembol + "&apikey=" + apiKey;
            HttpResponse<String> tsResp = client.send(HttpRequest.newBuilder().uri(URI.create(tsUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode timeSeriesNode = mapper.readTree(tsResp.body()).path("Time Series (Daily)");

            if (timeSeriesNode.isMissingNode() || timeSeriesNode.isEmpty()) {
                sonuc.put("fiyat", "N/A");
                sonuc.put("hacim", "0");
                sonuc.put("haber", "Geçmiş fiyat verisi bulunamadı veya API limiti aşıldı.");
                sonuc.put("yapayZekaKarari", "BEKLE - Teknik veri seti eksik.");
                return sonuc;
            }

            List<Double> kapanislar = new ArrayList<>();
            List<Double> yuksekler = new ArrayList<>();
            List<Double> dusukler = new ArrayList<>();
            double sonHacim = 0;
            int count = 0;

            var fields = timeSeriesNode.fields();
            while (fields.hasNext() && count < 250) {
                var entry = fields.next();
                JsonNode dayData = entry.getValue();
                kapanislar.add(dayData.path("4. close").asDouble());
                yuksekler.add(dayData.path("2. high").asDouble());
                dusukler.add(dayData.path("3. low").asDouble());
                if (count == 0) sonHacim = dayData.path("5. volume").asDouble();
                count++;
            }

            double guncelFiyat = kapanislar.get(0);

            // İndikatörler & Filtreler
            double rsi14 = hesaplaRSI(kapanislar, 14);
            double macdLine = hesaplaMACD(kapanislar);
            double[] bollinger = hesaplaBollingerBands(kapanislar, 20, 2.0);
            double lowerBand = bollinger[1];

            double sma50 = hesaplaSMA(kapanislar, 50);
            double sma200 = hesaplaSMA(kapanislar, Math.min(kapanislar.size(), 200));
            boolean anaTrendYukari = guncelFiyat > sma50 && sma50 > sma200;

            // Yapısal Destek & Direnç Tespiti (Son 100 Gün)
            double[] destekDirenc = hesaplaDestekDirenc(yuksekler, dusukler, 100);
            double majörDestek = destekDirenc[0];
            double majörDirenc = destekDirenc[1];

            double atr14 = hesaplaATR(yuksekler, dusukler, kapanislar, 14);
            double trailingStopLoss = guncelFiyat - (1.5 * atr14);
            double takeProfit = guncelFiyat + (3.0 * atr14);

            double uzunDonemAtr = hesaplaATR(yuksekler, dusukler, kapanislar, Math.min(yuksekler.size(), 50));
            boolean asiriVolatilite = atr14 > (uzunDonemAtr * 1.5);

            double riskMiktari = guncelFiyat - trailingStopLoss;
            double odulMiktari = takeProfit - guncelFiyat;
            double riskOdulOrani = (riskMiktari > 0) ? (odulMiktari / riskMiktari) : 0.0;
            double portfoyRiskYuzdesi = asiriVolatilite ? 0.25 : Math.min(2.0, Math.max(0.5, 1.0 / (atr14 / guncelFiyat * 100)));

            sonuc.put("fiyat", String.format("%.4f USD", guncelFiyat));
            sonuc.put("hacim", String.format("%.0f", sonHacim));

            // Haber Taraması
            String altiAyOnce = LocalDate.now().minusMonths(6).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "T0000";
            String aramaKriteri = sembol.equals("TSLA") ? "TSLA,Panasonic,CATL" : sembol;
            String newsUrl = "https://www.alphavantage.co/query?function=NEWS_SENTIMENT&tickers=" + aramaKriteri + "&time_from=" + altiAyOnce + "&limit=5&apikey=" + apiKey;
            HttpResponse<String> newsResp = client.send(HttpRequest.newBuilder().uri(URI.create(newsUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode feedNode = mapper.readTree(newsResp.body()).path("feed");
            StringBuilder haberlerMetni = new StringBuilder();

            if (feedNode.isArray() && feedNode.size() > 0) {
                int sayac = 1;
                for (JsonNode item : feedNode) {
                    if (sayac > 4) break;
                    haberlerMetni.append(sayac).append(". ").append(item.path("title").asText()).append("\n");
                    sayac++;
                }
            } else {
                haberlerMetni.append("Özel bir haber akışı bulunamadı.");
            }

            // Hafıza Okuma (Geçmiş İşlemler)
            String aiHafiza = sonIslemiGetir(sembol);

            // --- YÖNETİM KURULU (MULTI-AGENT) ASENKRON PARALEL MİMARİ ---
            ChatLanguageModel model = OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .modelName("llama3")
                    .temperature(0.0)
                    .timeout(Duration.ofMinutes(5))
                    .build();

            // AJAN 1: Kantitatif Analist (Destek/Direnç Verisiyle)
            String promptQuant = String.format("Sen Kantitatif Analistsin. Hisse: %s | Fiyat: %.4f | RSI: %.2f | MACD: %.2f | Trend: %s | Majör Destek: %.2f | Majör Direnç: %.2f. Fiyatın yapısal durumu ve matematiksel riski hakkında 2 cümle yaz.", sembol, guncelFiyat, rsi14, macdLine, anaTrendYukari, majörDestek, majörDirenc);
            CompletableFuture<String> quantFuture = CompletableFuture.supplyAsync(() -> model.generate(promptQuant));

            // AJAN 2: Haber Analisti
            String promptNews = String.format("Sen Haber Analistisin. %s haberi şirketi nasıl etkiler? (Sadece 2 cümle):\n%s", sembol, haberlerMetni.toString());
            CompletableFuture<String> newsFuture = CompletableFuture.supplyAsync(() -> model.generate(promptNews));

            // Ajanların Asenkron İşlemlerini Bekle (Paralel İşlem sayesinde süre yarıya iner)
            CompletableFuture.allOf(quantFuture, newsFuture).join();
            String quantRaporu = quantFuture.join();
            String newsRaporu = newsFuture.join();

            // AJAN 3 (CEO): Baş Risk Yöneticisi (Geçmiş Hafızalı ve Makro Endeks Farkındalıklı)
            String promptCEO = String.format("""
                    [SİSTEM ROLÜ]
                    Sen Wall Street baş kantitatif risk yöneticisisin (CRO). Altındaki raporları ve fonun geçmiş hafızasını inceleyerek NİHAİ fon kararını vereceksin.
                    
                    [FON GEÇMİŞ HAFIZASI (MEMORY)]
                    - Bu varlık için son sistem kararı: %s
                    
                    [MAKRO PİYASA DURUMU]
                    - S&P 500 Genel Trendi: %s
                    
                    [ALT AJAN RAPORLARI]
                    - Kantitatif Departman: %s
                    - Haber Departmanı: %s
                    
                    [RİSK METRİKLERİ]
                    - Risk/Ödül: %.2f | Volatilite: %s
                    
                    [ZORUNLU KURALLAR]
                    1. Kararın KESİNLİKLE 'AL', 'SAT' veya 'BEKLE' ile başlamalıdır.
                    2. Kararında sistemin geçmiş hafızasını, makro endeksi ve mevcut ajan raporlarını dikkate alarak tutarlı bir strateji belirt.
                    3. Risk/Ödül 1.5 altındaysa veya Volatilite 'Yüksek/Panik' ise kesinlikle 'BEKLE' de.
                    """, aiHafiza, makroTrend, quantRaporu, newsRaporu, riskOdulOrani, (asiriVolatilite ? "Yüksek/Panik" : "Normal"));

            String ceoKarari = model.generate(promptCEO);

            // 4. AJAN: Acımasız Denetçi (Auditor)
            String promptAuditor = String.format("""
                    Sen acımasız bir risk denetçisisin (Auditor). CEO şu kararı verdi: '%s'. 
                    Mevcut piyasa verileri: RSI %.2f, Hisse Trendi: %s, Volatilite: %s, Makro Piyasa (SPY): %s. 
                    Bu karardaki mantıksal açıkları, düşen bıçak risklerini veya FOMO ihtimalini 2 cümleyle acımasızca eleştir.
                    """, ceoKarari, rsi14, (anaTrendYukari ? "Boğa" : "Ayı"), (asiriVolatilite ? "Yüksek" : "Normal"), makroTrend);

            String auditorRaporu = model.generate(promptAuditor);

            sonuc.put("haber", String.format("<b>Hedge Fon Matrisi -></b> RSI: %.2f | MACD: %.2f | Trend: %s | <b>Makro SPY:</b> %s<br><b>Destek:</b> %.2f | <b>Direnç:</b> %.2f<br><b>Volatilite Rejimi:</b> %s | <b>Trailing Stop:</b> %.4f<br><b>Risk/Ödül:</b> %.2f | <b>Risk:</b> %.2f%%<br><hr style='border: 1px solid #333; margin: 15px 0;'><b style='color:#a0a0a0;'>[AI] KANTİTATİF AJAN RAPORU:</b><br>%s<br><br><b style='color:#a0a0a0;'>[AI] HABER VE MAKRO AJAN RAPORU:</b><br>%s<br><br><b style='color:#ff5555;'>[AI] DENETÇİ (AUDITOR) RAPORU:</b><br>%s",
                    rsi14, macdLine, (anaTrendYukari ? "Boğa" : "Ayı"), makroTrend, majörDestek, majörDirenc, (asiriVolatilite ? "⚠️ Yüksek Risk / Panik" : "Normal"), trailingStopLoss, riskOdulOrani, portfoyRiskYuzdesi, quantRaporu.replace("\n", "<br>"), newsRaporu.replace("\n", "<br>"), auditorRaporu.replace("\n", "<br>")));


            // Koruma Kuralları
            String nihaiKarar = ceoKarari;
            if (rsi14 > 75 && ceoKarari.toUpperCase().startsWith("AL")) {
                nihaiKarar = "BEKLE - Matematiksel Koruma Kuralı: RSI 75 üzerinde aşırı alım bölgesinde.";
            } else if (!anaTrendYukari && ceoKarari.toUpperCase().startsWith("AL")) {
                nihaiKarar = "BEKLE - Trend Filtresi: Ana piyasa yönü düşüş/yatay.";
            } else if (asiriVolatilite && ceoKarari.toUpperCase().startsWith("AL")) {
                nihaiKarar = "BEKLE - Volatilite Rejimi: Piyasa aşırı dalgalı/panik modunda.";
            } else if (riskOdulOrani < 1.5 && ceoKarari.toUpperCase().startsWith("AL")) {
                nihaiKarar = "BEKLE - Risk/Ödül Matrisi: Oran eşik değerin altında.";
            } else if (rsi14 < 25 && guncelFiyat <= lowerBand * 1.02 && anaTrendYukari && !asiriVolatilite) {
                nihaiKarar = "AL - Kurumsal Kantitatif Sinyal: Bollinger alt bandı ve dip RSI.";
            }

            sonuc.put("yapayZekaKarari", nihaiKarar);
            logKarariKaydet(sembol, guncelFiyat, rsi14, atr14, trailingStopLoss, takeProfit, nihaiKarar);

        } catch (Exception e) {
            sonuc.put("hata", e.getMessage());
            sonuc.put("yapayZekaKarari", "BEKLE - Sistem veri işleme hatası: " + e.getMessage());
        }
        return sonuc;
    }

    @GetMapping("/api/rapor")
    public Map<String, Object> performansRaporuGetir() {
        Map<String, Object> rapor = new HashMap<>();
        List<String> sonLoglar = new ArrayList<>();
        int toplamIslem = 0;
        int alSayisi = 0, satSayisi = 0, bekleSayisi = 0;

        try (BufferedReader br = new BufferedReader(new FileReader("quant_audit_log.csv"))) {
            String satir;
            while ((satir = br.readLine()) != null) {
                toplamIslem++;
                sonLoglar.add(satir);
                String upper = satir.toUpperCase();
                if (upper.contains(",AL") || upper.contains("\"AL") || upper.startsWith("AL")) alSayisi++;
                else if (upper.contains(",SAT") || upper.contains("\"SAT") || upper.startsWith("SAT")) satSayisi++;
                else bekleSayisi++;
            }
        } catch (Exception e) {
            rapor.put("durum", "Log bulunamadı.");
            return rapor;
        }

        rapor.put("toplamAnalizSayisi", toplamIslem);
        rapor.put("alSiniflari", alSayisi);
        rapor.put("satSiniflari", satSayisi);
        rapor.put("bekleSiniflari", bekleSayisi);
        rapor.put("sonLogKayitlari", sonLoglar.size() > 10 ? sonLoglar.subList(sonLoglar.size() - 10, sonLoglar.size()) : sonLoglar);

        return rapor;
    }

    private String sonIslemiGetir(String sembol) {
        String sonIslem = "Geçmiş işlem veya hafıza bulunmuyor.";
        try (BufferedReader br = new BufferedReader(new FileReader("quant_audit_log.csv"))) {
            String satir;
            while ((satir = br.readLine()) != null) {
                if (satir.contains("," + sembol + ",")) {
                    String[] parcalar = satir.split(",");
                    if (parcalar.length >= 8) {
                        sonIslem = String.format("Tarih: %s | Fiyat: %s | Karar: %s", parcalar[0], parcalar[2], parcalar[7]);
                    }
                }
            }
        } catch (Exception e) {}
        return sonIslem;
    }

    private double[] hesaplaDestekDirenc(List<Double> yuksekler, List<Double> dusukler, int periyot) {
        int limit = Math.min(yuksekler.size(), periyot);
        double direnc = 0;
        double destek = Double.MAX_VALUE;
        for (int i = 0; i < limit; i++) {
            if (yuksekler.get(i) > direnc) direnc = yuksekler.get(i);
            if (dusukler.get(i) < destek) destek = dusukler.get(i);
        }
        if (destek == Double.MAX_VALUE) destek = 0;
        return new double[]{destek, direnc};
    }

    private void logKarariKaydet(String sembol, double fiyat, double rsi, double atr, double sl, double tp, String karar) {
        try (FileWriter fw = new FileWriter("quant_audit_log.csv", true);
             PrintWriter pw = new PrintWriter(fw)) {
            String zaman = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            pw.println(zaman + "," + sembol + "," + fiyat + "," + rsi + "," + atr + "," + sl + "," + tp + ",\"" + karar.replace("\n", " ") + "\"");
        } catch (Exception e) {}
    }

    private double hesaplaATR(List<Double> yuksekler, List<Double> dusukler, List<Double> kapanislar, int periyot) {
        if (yuksekler.size() < periyot + 1) return 1.0;
        double trToplam = 0;
        for (int i = 0; i < periyot; i++) {
            double high = yuksekler.get(i);
            double low = dusukler.get(i);
            double prevClose = kapanislar.get(i + 1);
            trToplam += Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        return trToplam / periyot;
    }

    private double hesaplaSMA(List<Double> fiyatlar, int periyot) {
        if (fiyatlar.size() < periyot) periyot = fiyatlar.size();
        double toplam = 0;
        for (int i = 0; i < periyot; i++) toplam += fiyatlar.get(i);
        return toplam / periyot;
    }

    private double hesaplaRSI(List<Double> fiyatlar, int periyot) {
        if (fiyatlar.size() < periyot + 1) return 50.0;
        double kazanisToplam = 0, kayipToplam = 0;
        for (int i = 0; i < periyot; i++) {
            double fark = fiyatlar.get(i) - fiyatlar.get(i + 1);
            if (fark > 0) kazanisToplam += fark;
            else kayipToplam += Math.abs(fark);
        }
        if (kayipToplam / periyot == 0) return 100.0;
        return 100.0 - (100.0 / (1.0 + (kazanisToplam / periyot) / (kayipToplam / periyot)));
    }

    private double hesaplaMACD(List<Double> fiyatlar) {
        if (fiyatlar.size() < 26) return 0.0;
        return hesaplaEMA(fiyatlar, 12) - hesaplaEMA(fiyatlar, 26);
    }

    private double hesaplaEMA(List<Double> fiyatlar, int periyot) {
        double k = 2.0 / (periyot + 1);
        double ema = fiyatlar.get(fiyatlar.size() - 1);
        for (int i = fiyatlar.size() - 2; i >= 0; i--) {
            ema = (fiyatlar.get(i) * k) + (ema * (1 - k));
        }
        return ema;
    }

    private double[] hesaplaBollingerBands(List<Double> fiyatlar, int periyot, double sapmaCarpani) {
        if (fiyatlar.size() < periyot) periyot = fiyatlar.size();
        double sma = hesaplaSMA(fiyatlar, periyot);
        double varyansToplami = 0;
        for (int i = 0; i < periyot; i++) varyansToplami += Math.pow(fiyatlar.get(i) - sma, 2);
        double standartSapma = Math.sqrt(varyansToplami / periyot);
        return new double[]{sma + (standartSapma * sapmaCarpani), sma - (standartSapma * sapmaCarpani)};
    }
}