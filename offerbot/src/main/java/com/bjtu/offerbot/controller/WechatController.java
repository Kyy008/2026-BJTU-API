package com.bjtu.offerbot.controller;

import com.bjtu.offerbot.service.WechatSignatureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WechatController {

    private final WechatSignatureService signatureService;

    public WechatController(WechatSignatureService signatureService) {
        this.signatureService = signatureService;
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
}
