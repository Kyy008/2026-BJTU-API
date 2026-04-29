package com.bjtu.offerbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bjtu.offerbot.service.WechatSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "wechat.token=bjtu2026apitoken")
class WechatSignatureServiceTests {

    @Autowired
    private WechatSignatureService signatureService;

    @Test
    void validSignaturePasses() {
        String timestamp = "1714380000";
        String nonce = "offerbot";
        String signature = signatureService.sha1Sorted("bjtu2026apitoken", timestamp, nonce);

        assertThat(signatureService.isValid(signature, timestamp, nonce)).isTrue();
    }

    @Test
    void missingOrInvalidSignatureFails() {
        assertThat(signatureService.isValid("", "1714380000", "offerbot")).isFalse();
        assertThat(signatureService.isValid("bad-signature", "1714380000", "offerbot")).isFalse();
    }
}
