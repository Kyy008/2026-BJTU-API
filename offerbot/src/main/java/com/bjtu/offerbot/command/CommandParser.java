package com.bjtu.offerbot.command;

import com.bjtu.offerbot.service.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommandParser {

    private static final Map<String, String> FIELD_ALIASES = new HashMap<>();

    static {
        FIELD_ALIASES.put("编号", "id");
        FIELD_ALIASES.put("公司", "company");
        FIELD_ALIASES.put("城市", "city");
        FIELD_ALIASES.put("岗位", "position");
        FIELD_ALIASES.put("薪资", "salary");
        FIELD_ALIASES.put("学历", "education");
        FIELD_ALIASES.put("行业", "industry");
        FIELD_ALIASES.put("公司类型", "industry");
        FIELD_ALIASES.put("类型", "type");
        FIELD_ALIASES.put("岗位类型", "type");
        FIELD_ALIASES.put("关键词", "keyword");
        FIELD_ALIASES.put("页码", "page");
        FIELD_ALIASES.put("每页", "size");
    }

    public ParsedCommand parse(String rawCommand) {
        if (!StringUtils.hasText(rawCommand)) {
            throw new BusinessException("暂时看不懂这条命令。\n发送“帮助”查看可用命令。");
        }

        String normalized = rawCommand.strip().replace('\u3000', ' ');
        List<String> lines = normalized.lines().map(String::strip).toList();
        String firstLine = lines.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new BusinessException("暂时看不懂这条命令。\n发送“帮助”查看可用命令。"));

        if ("批量上传".equals(firstLine)) {
            return new ParsedCommand(CommandAction.BATCH_CREATE, Map.of(), batchRows(lines, firstLine), null);
        }

        if (firstLine.equals("帮助")) {
            return new ParsedCommand(CommandAction.HELP, Map.of(), List.of(), null);
        }
        if (firstLine.startsWith("帮助 ")) {
            return new ParsedCommand(CommandAction.HELP, Map.of(), List.of(), firstLine.substring(3).trim());
        }

        return parseChinese(firstLine);
    }

    private ParsedCommand parseChinese(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0];
        String rest = parts.length == 2 ? parts[1] : "";
        if ("查Offer".equals(command) || "查offer".equals(command)) {
            return new ParsedCommand(CommandAction.QUERY, parseParams(rest), List.of(), null);
        }
        CommandAction action = switch (command) {
            case "上传" -> CommandAction.CREATE;
            case "详情" -> CommandAction.GET;
            case "列表" -> CommandAction.LIST;
            case "查询", "查薪资" -> CommandAction.QUERY;
            case "找名企" -> CommandAction.FAMOUS_QUERY;
            case "更新" -> CommandAction.UPDATE;
            case "替换" -> CommandAction.REPLACE;
            case "删除" -> CommandAction.DELETE;
            default -> throw unknownCommand();
        };
        return new ParsedCommand(action, parseParams(rest), List.of(), null);
    }

    private Map<String, String> parseParams(String rest) {
        Map<String, String> params = new LinkedHashMap<>();
        if (!StringUtils.hasText(rest)) {
            return params;
        }

        for (String token : rest.strip().split("\\s+")) {
            int index = token.indexOf('=');
            if (index <= 0 || index == token.length() - 1) {
                throw new BusinessException("参数格式错误：" + token + "\n请使用 字段=值，例如：城市=北京");
            }

            String rawKey = token.substring(0, index);
            String key = FIELD_ALIASES.get(rawKey);
            if (key == null) {
                throw new BusinessException("无法识别字段：" + rawKey);
            }
            params.put(key, token.substring(index + 1).trim());
        }
        return params;
    }

    private List<String> batchRows(List<String> lines, String firstLine) {
        List<String> rows = new ArrayList<>();
        boolean afterHeader = false;
        for (String line : lines) {
            if (!afterHeader) {
                afterHeader = line.equals(firstLine);
                continue;
            }
            if (StringUtils.hasText(line)) {
                rows.add(line);
            }
        }
        return rows;
    }

    private BusinessException unknownCommand() {
        return new BusinessException("暂时看不懂这条命令。\n发送“帮助”查看可用命令。");
    }
}
