package com.bjtu.offerbot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bjtu.offerbot.service.WechatSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "wechat.token=bjtu2026apitoken")
class WechatControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WechatSignatureService signatureService;

    @Test
    void verifyServerReturnsEchoStringWhenSignatureIsValid() throws Exception {
        String timestamp = "1714380000";
        String nonce = "offerbot";
        String echostr = "hello-wechat";
        String signature = signatureService.sha1Sorted("bjtu2026apitoken", timestamp, nonce);

        mockMvc.perform(get("/wechat/")
                        .param("signature", signature)
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .param("echostr", echostr))
                .andExpect(status().isOk())
                .andExpect(content().string(echostr));
    }

    @Test
    void verifyServerRejectsInvalidSignature() throws Exception {
        mockMvc.perform(get("/wechat/")
                        .param("signature", "bad-signature")
                        .param("timestamp", "1714380000")
                        .param("nonce", "offerbot")
                        .param("echostr", "hello-wechat"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("invalid signature"));
    }
}
