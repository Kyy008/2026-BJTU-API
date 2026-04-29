package com.bjtu.offerbot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import com.bjtu.offerbot.service.WechatSignatureService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    @Test
    void receiveMessageRepliesToTextMessageWhenSignatureIsValid() throws Exception {
        String timestamp = "1714380000";
        String nonce = "offerbot";
        String signature = signatureService.sha1Sorted("bjtu2026apitoken", timestamp, nonce);
        String requestBody = """
                <xml>
                  <ToUserName><![CDATA[gh_test]]></ToUserName>
                  <FromUserName><![CDATA[user_openid]]></FromUserName>
                  <CreateTime>1714380000</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[帮助]]></Content>
                  <MsgId>1</MsgId>
                </xml>""";

        mockMvc.perform(post("/wechat/")
                        .param("signature", signature)
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/xml/ToUserName").string("user_openid"))
                .andExpect(xpath("/xml/FromUserName").string("gh_test"))
                .andExpect(xpath("/xml/MsgType").string("text"))
                .andExpect(xpath("/xml/Content").string(Matchers.containsString("OfferBot")));
    }

    @Test
    void receiveMessageRejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/wechat/")
                        .param("signature", "bad-signature")
                        .param("timestamp", "1714380000")
                        .param("nonce", "offerbot")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<xml></xml>"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("invalid signature"));
    }

    @Test
    void receiveMessageRepliesToSubscribeEvent() throws Exception {
        String timestamp = "1714380000";
        String nonce = "offerbot";
        String signature = signatureService.sha1Sorted("bjtu2026apitoken", timestamp, nonce);
        String requestBody = """
                <xml>
                  <ToUserName><![CDATA[gh_test]]></ToUserName>
                  <FromUserName><![CDATA[user_openid]]></FromUserName>
                  <CreateTime>1714380000</CreateTime>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[subscribe]]></Event>
                </xml>""";

        mockMvc.perform(post("/wechat/")
                        .param("signature", signature)
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(xpath("/xml/Content").string(Matchers.containsString("感谢关注")));
    }
}
