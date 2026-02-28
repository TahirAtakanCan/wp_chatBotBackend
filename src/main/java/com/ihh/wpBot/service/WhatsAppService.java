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
            
            // Oturumu kaydetme ayarı
            options.addArguments("user-data-dir=" + System.getProperty("user.home") + "/WhatsAppBotProfile"); 
            
            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(60));
            
            driver.get("https://web.whatsapp.com");
            
            // Sol menü yüklenene kadar bekle
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pane-side")));
        }
    }

    public void sendMessage(String phoneNumber, String message) throws InterruptedException {
        // 1. URL'de metin göndermiyoruz, SADECE numaraya gidip sohbeti açıyoruz
        driver.get("https://web.whatsapp.com/send?phone=" + phoneNumber);
        
        // 2. Mesaj yazma kutusunu bul (WhatsApp'ın en stabil HTML elementidir)
        // main panelinin içindeki footer'da yer alan ve yazı yazılabilir (contenteditable) olan div.
        WebElement messageBox = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@id='main']//footer//div[@contenteditable='true']")
        ));
        
        // 3. İnsan gibi kutuya tıkla (odaklan)
        messageBox.click();
        
        // 4. Mesajı kutuya yazdır
        messageBox.sendKeys(message);
        
        // 5. WhatsApp'ın (React.js'in) yazıyı algılaması için çok kısa bir an bekle
        Thread.sleep(500); 
        
        // 6. Klavyeden "ENTER" tuşuna bas! (Buton arama derdi bitti)
        messageBox.sendKeys(Keys.ENTER);
        
        // Mesajın sunucuya iletilmesi (Tik olması) için bekle
        Thread.sleep(2000); 
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}