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

    public void sendMessage(String phoneNumber, String message, List<String> mediaPaths) throws Exception {
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
            throw new Exception("Numara WhatsApp'ta yok.");
        }
        if (!isChatOpened) {
            throw new Exception("Sohbet açılamadı (Zaman Aşımı).");
        }

        // ==========================================
        // 1. ADIM: ÖNCE SADECE MEDYAYI GÖNDER
        // ==========================================
        if (mediaPaths != null && !mediaPaths.isEmpty()) {
            
            // Gizli dosya yükleme elementini bul
            WebElement fileInput = driver.findElement(By.xpath("//input[@type='file' and contains(@accept, 'image')]"));
            
            // Dosyaları yükle
            String combinedPaths = String.join("\n", mediaPaths);
            fileInput.sendKeys(combinedPaths); 
            
            // Önizleme ekranının açılması için biraz daha uzun bekle
            Thread.sleep(2500); 
            
            // Açıklama yazmadan, direkt önizleme ekranındaki gönder butonuna bas
            WebElement sendMediaBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[@data-icon='send']")
            ));
            sendMediaBtn.click();
            
            // Resmin yola çıkması ve ana ekrana dönülmesi için bekle
            Thread.sleep(3000); 
        }

        // ==========================================
        // 2. ADIM: SONRA METNİ GÖNDER (Ayrı baloncuk)
        // ==========================================
        if (message != null && !message.trim().isEmpty()) {
            // Ana sohbet ekranındaki yazı kutusunu tekrar bul
            WebElement messageBox = wait.until(ExpectedConditions.elementToBeClickable(messageBoxLocator));
            messageBox.click();
            
            // Yazıyı yaz
            messageBox.sendKeys(message);
            Thread.sleep(500); 
            
            // İnsan gibi Enter'a bas
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