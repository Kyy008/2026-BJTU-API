package com.bjtu.offerbot.service;

import com.bjtu.offerbot.config.WechatProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WechatSignatureService {

    private final WechatProperties wechatProperties;

    public WechatSignatureService(WechatProperties wechatProperties) {
        this.wechatProperties = wechatProperties;
    }

    public boolean isValid(String signature, String timestamp, String nonce) {
        if (!StringUtils.hasText(signature)
                || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(wechatProperties.getToken())) {
            return false;
        }

        return signature.equals(sha1Sorted(wechatProperties.getToken(), timestamp, nonce));
    }

    public String sha1Sorted(String token, String timestamp, String nonce) {
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        return sha1(String.join("", parts));
    }

    private String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm is not available", e);
        }
    }
}
