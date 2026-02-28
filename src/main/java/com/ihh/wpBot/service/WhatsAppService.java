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
        // MEDYA GÖNDERİMİ
        // ==========================================
        if (mediaPaths != null && !mediaPaths.isEmpty()) {
            
            // 1. WhatsApp'ın gizli resim/video yükleme alanını (input type=file) bul
            WebElement fileInput = driver.findElement(By.xpath("//input[@type='file' and contains(@accept, 'image')]"));
            
            // Birden fazla dosyayı Selenium ile tek seferde yüklemek için yolları \n ile birleştiriyoruz
            String combinedPaths = String.join("\n", mediaPaths);
            fileInput.sendKeys(combinedPaths); // Fiziksel yolları browser'a bas
            
            // 2. Önizleme penceresinin açılmasını bekle
            Thread.sleep(2000); 
            
            // 3. Mesaj varsa, resmin altındaki açıklama (caption) kutusunu bul ve yaz
            if (message != null && !message.isEmpty()) {
                WebElement captionBox = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//div[@role='textbox' or @contenteditable='true']")
                ));
                captionBox.click();
                captionBox.sendKeys(message);
                Thread.sleep(500);
            }
            
            // 4. "Gönder" butonuna bas (Önizleme ekranındaki gönder butonu)
            WebElement sendBtn = driver.findElement(By.xpath("//span[@data-icon='send']"));
            sendBtn.click();
            Thread.sleep(3000); // Yüklenip gitmesi için bekle
            
        } 
        // ==========================================
        // SADECE METİN GÖNDERİMİ (Medya yoksa)
        // ==========================================
        else {
            WebElement messageBox = driver.findElement(messageBoxLocator);
            messageBox.click();
            messageBox.sendKeys(message);
            Thread.sleep(500); 
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