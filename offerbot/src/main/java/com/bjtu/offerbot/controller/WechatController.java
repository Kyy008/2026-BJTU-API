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
