package com.bjtu.offerbot.service.dto;

import java.util.List;

public record PagedResult<T>(
        List<T> records,
        long total,
        int page,
        int size,
        int totalPages) {
}
