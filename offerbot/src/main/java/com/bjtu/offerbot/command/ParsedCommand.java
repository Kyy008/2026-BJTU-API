package com.bjtu.offerbot.command;

import java.util.List;
import java.util.Map;

public record ParsedCommand(
        CommandAction action,
        Map<String, String> params,
        List<String> batchRows,
        String helpTopic) {
}
