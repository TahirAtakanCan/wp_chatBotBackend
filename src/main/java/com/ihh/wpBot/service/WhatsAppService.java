package com.ihh.wpBot.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WhatsAppService {

    private WebDriver driver;
    private WebDriverWait wait;

    public void initialize() {
        if (driver == null) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("user-data-dir=" + System.getProperty("user.home") + "/WhatsAppBotProfile"); 
            
            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(60));
            
            driver.get("https://web.whatsapp.com");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pane-side")));
        }
    }

    public void sendMessage(String phoneNumber, String message) throws Exception {
        driver.get("https://web.whatsapp.com/send?phone=" + phoneNumber);
        
        // HATA YÖNETİMİ: Sohbet ekranını VEYA Hata pop-up'ını aynı anda bekle
        long startTime = System.currentTimeMillis();
        boolean isChatOpened = false;
        boolean isInvalidNumber = false;
        
        By messageBoxLocator = By.xpath("//*[@id='main']//footer//div[@contenteditable='true']");
        // Ekranda beliren uyarı pop-up'ı (dialog) içindeki herhangi bir buton ("Tamam" vs.)
        By errorDialogButtonLocator = By.xpath("//div[@role='dialog']//button"); 

        // En fazla 30 saniye boyunca saniyede bir kontrol et
        while (System.currentTimeMillis() - startTime < 30000) { 
            // 1. Durum: Mesaj kutusu geldi mi? (Başarılı)
            if (!driver.findElements(messageBoxLocator).isEmpty()) {
                isChatOpened = true;
                break;
            }
            // 2. Durum: Hata pop-up'ı geldi mi? (Başarısız numara)
            if (!driver.findElements(errorDialogButtonLocator).isEmpty()) {
                isInvalidNumber = true;
                break;
            }
            Thread.sleep(1000); // 1 saniye bekle, tekrar bak
        }

        // Eğer numara WhatsApp'a kayıtlı değilse:
        if (isInvalidNumber) {
            try {
                // Sonraki numaralara engel olmaması için ekrandaki hata pop-up'ındaki "Tamam" butonuna basarak kapat
                driver.findElement(errorDialogButtonLocator).click();
                Thread.sleep(500);
            } catch (Exception ignored) {} // Butona basamazsa bile yoluna devam etsin
            
            // İşlemi iptal et ve özel hata fırlat
            throw new Exception("Numara WhatsApp'a kayıtlı değil.");
        }

        // Ne mesaj kutusu ne hata geldi (İnternet kopmuş olabilir vs.)
        if (!isChatOpened) {
            throw new Exception("Sohbet ekranı açılamadı (Zaman Aşımı).");
        }

        // Her şey yolunda, mesajı yaz ve fırlat
        WebElement messageBox = driver.findElement(messageBoxLocator);
        messageBox.click();
        messageBox.sendKeys(message);
        Thread.sleep(500); 
        messageBox.sendKeys(Keys.ENTER);
        Thread.sleep(2000); 
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}