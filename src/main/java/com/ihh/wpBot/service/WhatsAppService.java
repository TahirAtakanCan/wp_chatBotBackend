package com.ihh.wpBot.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

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

    public void sendMessage(String phoneNumber, String message, List<String> mediaUrls) throws Exception {
        driver.get("https://web.whatsapp.com/send?phone=" + phoneNumber);
        
        long startTime = System.currentTimeMillis();
        boolean isChatOpened = false;
        boolean isInvalidNumber = false;
        
        By messageBoxLocator = By.xpath("//*[@id='main']//footer//div[@contenteditable='true']");
        By errorDialogButtonLocator = By.xpath("//div[@role='dialog']//button"); 

        while (System.currentTimeMillis() - startTime < 30000) { 
            if (!driver.findElements(messageBoxLocator).isEmpty()) {
                isChatOpened = true; break;
            }
            if (!driver.findElements(errorDialogButtonLocator).isEmpty()) {
                isInvalidNumber = true; break;
            }
            Thread.sleep(1000); 
        }

        if (isInvalidNumber) {
            try { driver.findElement(errorDialogButtonLocator).click(); Thread.sleep(500); } catch (Exception ignored) {}
            throw new Exception("Numara WhatsApp'ta kayıtlı değil.");
        }
        if (!isChatOpened) {
            throw new Exception("Sohbet ekranı açılamadı.");
        }

        // ==========================================
        // YENİ SİSTEM: LİNK ÖNİZLEME (URL PREVIEW)
        // ==========================================
        WebElement messageBox = wait.until(ExpectedConditions.elementToBeClickable(messageBoxLocator));
        messageBox.click();

        // 1. Varsa Medya Linklerini Metnin Sonuna Ekle
        String finalMessage = (message != null) ? message : "";
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            finalMessage += "\n\n"; 
            for (String url : mediaUrls) {
                finalMessage += url + "\n";
            }
        }

        // 2. Metni Paragraflı Şekilde Yaz
        if (!finalMessage.trim().isEmpty()) {
            String[] lines = finalMessage.split("\n");
            for (int i = 0; i < lines.length; i++) {
                messageBox.sendKeys(lines[i]); 
                if (i < lines.length - 1) {
                    messageBox.sendKeys(Keys.SHIFT, Keys.ENTER);
                }
            }
            
            // 3. ÇÖZÜMÜN KALBİ: WhatsApp'ın resmi çekip önizleme oluşturması için bekle!
            if (mediaUrls != null && !mediaUrls.isEmpty()) {
                Thread.sleep(5000); // İnternet hızına göre resmi çekmesi 3-5 sn sürer
            } else {
                Thread.sleep(500); // Sadece metinse beklemeye gerek yok
            }
            
            messageBox.sendKeys(Keys.ENTER); 
            Thread.sleep(2000); 
        }
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}