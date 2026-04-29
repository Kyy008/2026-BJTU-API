package com.bjtu.offerbot.service.dto;

import java.util.List;

public record BatchCreateResult(int successCount, List<String> failures) {

    public int failureCount() {
        return failures.size();
    }
}
