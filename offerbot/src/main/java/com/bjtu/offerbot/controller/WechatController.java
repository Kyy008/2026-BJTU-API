package com.bjtu.offerbot.controller;

import com.bjtu.offerbot.service.WechatSignatureService;
import com.bjtu.offerbot.service.WechatMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WechatController {

    private final WechatSignatureService signatureService;
    private final WechatMessageService messageService;

    public WechatController(WechatSignatureService signatureService, WechatMessageService messageService) {
        this.signatureService = signatureService;
        this.messageService = messageService;
    }

    /**
     * 微信公众平台保存服务器配置时会调用该接口，并要求原样返回 echostr。
     */
    @GetMapping({"/wechat", "/wechat/"})
    public ResponseEntity<String> verifyServer(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestParam(required = false) String echostr) {
        if (signatureService.isValid(signature, timestamp, nonce)) {
            return ResponseEntity.ok(echostr == null ? "" : echostr);
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
    }

    /**
     * 微信消息推送入口。GET 和 POST 使用同一套签名参数，避免非微信来源直接调用。
     */
    @PostMapping(
            value = {"/wechat", "/wechat/"},
            consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> receiveMessage(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestBody String requestBody) {
        if (!signatureService.isValid(signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("invalid signature");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(messageService.reply(requestBody));
    }
}
