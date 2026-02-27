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
            
            // Tahsin abinin her seferinde QR kod okutmaması için oturum verilerini klasöre kaydediyoruz
            options.addArguments("user-data-dir=C:/Temp/WhatsAppBotProfile"); 
            
            driver = new ChromeDriver(options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            
            driver.get("https://web.whatsapp.com");
        }
    }

    public void sendMessage(String phoneNumber, String message) throws InterruptedException {
        // WhatsApp API linki üzerinden direkt sohbete git
        driver.get("https://web.whatsapp.com/send?phone=" + phoneNumber + "&text=" + message);
        
        // Gönder butonunun ekranda belirmesini (yüklenmesini) bekle
        WebElement sendButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//span[@data-icon='send']")
        ));
        
        // Butona tıkla ve mesajı gönder
        sendButton.click();
        
        // Mesajın gitmesi ve tik olması için 2 saniye bekle
        Thread.sleep(2000); 
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
