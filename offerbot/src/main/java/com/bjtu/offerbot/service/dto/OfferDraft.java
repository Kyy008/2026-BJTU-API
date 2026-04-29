package com.bjtu.offerbot.service.dto;

public record OfferDraft(
        String company,
        String city,
        String position,
        String salary,
        String education,
        String industry,
        String type) {
}
