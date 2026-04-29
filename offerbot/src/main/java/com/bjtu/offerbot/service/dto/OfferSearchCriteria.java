package com.bjtu.offerbot.service.dto;

public record OfferSearchCriteria(
        String keyword,
        String company,
        String city,
        String position,
        String industry,
        String type,
        boolean famousOnly) {

    public static OfferSearchCriteria empty() {
        return new OfferSearchCriteria(null, null, null, null, null, null, false);
    }

    public OfferSearchCriteria withFamousOnly() {
        return new OfferSearchCriteria(keyword, company, city, position, industry, type, true);
    }
}
