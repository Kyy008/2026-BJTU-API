package com.bjtu.offerbot;

import com.bjtu.offerbot.config.WechatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WechatProperties.class)
public class OfferBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferBotApplication.class, args);
    }
}
