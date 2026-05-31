package com.ihh.wpBot.config;

import com.ihh.wpBot.model.AutoReply;
import com.ihh.wpBot.repository.AutoReplyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * İlk çalıştırmada hazır anahtar kelime + yanıt kategorileri ekler.
 * Tablo boşsa çalışır, mevcut veriler korunur.
 *
 * NOT: IBAN ve linkleri Tahsin abi gerçek bilgilerle güncellesin.
 */
@Component
@Order(2)
public class AutoReplySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AutoReplySeeder.class);

    private final AutoReplyRepository repository;

    public AutoReplySeeder(AutoReplyRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("Auto-reply seeds already exist, skipping");
            return;
        }

        log.info("Seeding auto-reply categories...");

        save("Selam",
                "selam, merhaba, selamun aleykum, esselamu aleykum, slm, mrb, iyi gunler, iyi aksamlar, hello, hi",
                "Aleyküm Selam 🤲\nİHH Seydişehir Temsilciliği'ne hoş geldiniz.\n\n" +
                "Nasıl yardımcı olabiliriz?\n\n📞 0536 322 83 03\n📍 https://maps.app.goo.gl/buXk8mJS3wUwsaFN9",
                10);

        save("Bağış",
                "bagis, bagislamak, bagislamak istiyorum, iban, hesap, banka, nasil bagis, nasil bagislayabilirim, bedel, online, para gondermek",
                "💰 *Bağış Bilgileri*\n\n🏦 *IBAN:*\nTR00 0000 0000 0000 0000 0000 00\nİHH İnsani Yardım Vakfı\n\n" +
                "💳 *Online Bağış:*\nhttps://www.ihh.org.tr/bagis\n\n☎️ Detaylı bilgi: 0536 322 83 03",
                20);

        save("Konum",
                "konum, adres, nerede, nerdesiniz, nerdesin, lokasyon, yer, ofis, neredeyiz",
                "📍 *Konumumuz*\n\nİHH İnsani Yardım Vakfı\nSeydişehir Temsilciliği\n\n" +
                "🗺 Harita: https://maps.app.goo.gl/buXk8mJS3wUwsaFN9\n\n☎️ 0536 322 83 03",
                30);

        save("İletişim",
                "iletisim, telefon, ara, arayabilirim, numara, tel, gsm, hangi numara",
                "📞 *İletişim*\n\n*Tel:* 0536 322 83 03\n\nMesai saatlerinde ulaşabilirsiniz:\n" +
                "🕘 09:00 - 18:00\n\nWhatsApp üzerinden de yazabilirsiniz, en kısa sürede dönüş yaparız 🙏",
                40);

        save("Kurban",
                "kurban, kac tl, ne kadar, bedel, fiyat, ucret, kac para, kurban bedeli, hisse",
                "🐏 *Kurban Bedeli: 11.500 TL*\n\n🌍 34 yıldır kurbanlarınızı dünyanın 61 ülkesinde mazlum kardeşlerimize ulaştırıyoruz.\n\n" +
                "☎️ Bağış için: 0536 322 83 03\n💳 Online: https://www.ihh.org.tr/bagis\n\n" +
                "Paylaştıkça çoğalan bu iyiliğe sen de ortak ol 🤲",
                50);

        save("Teşekkür",
                "tesekkur, tesekkurler, sagol, sagolun, allah razi olsun, eyvallah, cok sagol",
                "Bizleri tercih ettiğiniz için biz teşekkür ederiz 🤲\n\n" +
                "Duanız bizimle olsun. Allah hayırlı günler nasip etsin 🌙",
                60);

        log.info("Auto-reply seeds inserted: {} categories", repository.count());
    }

    private void save(String category, String keywords, String replyText, int priority) {
        AutoReply r = new AutoReply();
        r.setCategory(category);
        r.setKeywords(keywords);
        r.setReplyText(replyText);
        r.setPriority(priority);
        r.setActive(true);
        repository.save(r);
    }
}
