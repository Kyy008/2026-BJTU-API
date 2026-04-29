package com.bjtu.offerbot.command;

import com.bjtu.offerbot.domain.Offer;
import com.bjtu.offerbot.domain.WxUser;
import com.bjtu.offerbot.service.BusinessException;
import com.bjtu.offerbot.service.CommandLogService;
import com.bjtu.offerbot.service.OfferService;
import com.bjtu.offerbot.service.WxUserService;
import com.bjtu.offerbot.service.dto.BatchCreateResult;
import com.bjtu.offerbot.service.dto.OfferDraft;
import com.bjtu.offerbot.service.dto.OfferSearchCriteria;
import com.bjtu.offerbot.service.dto.PagedResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommandExecutor {

    private final CommandParser commandParser;
    private final OfferService offerService;
    private final WxUserService wxUserService;
    private final CommandLogService commandLogService;

    public CommandExecutor(
            CommandParser commandParser,
            OfferService offerService,
            WxUserService wxUserService,
            CommandLogService commandLogService) {
        this.commandParser = commandParser;
        this.offerService = offerService;
        this.wxUserService = wxUserService;
        this.commandLogService = commandLogService;
    }

    public String execute(String openid, String rawCommand) {
        ParsedCommand parsedCommand = null;
        String result = null;
        String error = null;
        try {
            WxUser user = wxUserService.recordRequest(openid);
            parsedCommand = commandParser.parse(rawCommand);
            checkPermission(user, parsedCommand.action());
            result = executeParsed(openid, parsedCommand);
            return result;
        } catch (BusinessException e) {
            error = e.getMessage();
            return error;
        } catch (RuntimeException e) {
            error = "系统暂时无法处理这条命令，请稍后再试。";
            return error;
        } finally {
            commandLogService.log(
                    openid,
                    rawCommand,
                    parsedCommand == null ? null : parsedCommand.action().name(),
                    error == null ? "SUCCESS" : "FAILED",
                    result,
                    error);
        }
    }

    private String executeParsed(String openid, ParsedCommand command) {
        return switch (command.action()) {
            case HELP -> help(command.helpTopic());
            case CREATE -> formatCreated(offerService.createOffer(toDraft(command.params()), openid));
            case GET -> formatOffer(offerService.getOffer(parseRequiredLong(command.params(), "id", "编号"))
                    .orElseThrow(() -> new BusinessException("没有找到编号=" + command.params().get("id") + " 的记录。")));
            case LIST -> formatPage(offerService.listOffers(OfferSearchCriteria.empty(), parseInt(command.params(), "page"), parseInt(command.params(), "size")), "列表");
            case QUERY -> formatPage(offerService.listOffers(toCriteria(command.params(), false), parseInt(command.params(), "page"), parseInt(command.params(), "size")), "查Offer");
            case FAMOUS_QUERY -> formatPage(offerService.listOffers(toCriteria(command.params(), true), parseInt(command.params(), "page"), parseInt(command.params(), "size")), "找名企");
            case UPDATE -> formatUpdated(offerService.updateOffer(parseRequiredLong(command.params(), "id", "编号"), updatePatch(command.params())));
            case REPLACE -> formatReplaced(offerService.replaceOffer(parseRequiredLong(command.params(), "id", "编号"), toDraft(command.params())));
            case DELETE -> formatDeleted(parseAndDelete(command.params()));
            case BATCH_CREATE -> formatBatch(offerService.batchCreateOffers(command.batchRows(), openid));
        };
    }

    private void checkPermission(WxUser user, CommandAction action) {
        if (WxUserService.STATE_SPIDER.equals(user.getState()) && isQueryAction(action)) {
            throw new BusinessException("访问被限制：检测到短时间内请求过于频繁。\n当前不能查询 Offer 信息，请稍后联系管理员处理。");
        }
        if (WxUserService.STATE_BANNED.equals(user.getState()) && isMutationAction(action)) {
            throw new BusinessException("上传被拒绝：当前账号没有上传或修改权限。");
        }
    }

    private boolean isQueryAction(CommandAction action) {
        return action == CommandAction.GET
                || action == CommandAction.LIST
                || action == CommandAction.QUERY
                || action == CommandAction.FAMOUS_QUERY;
    }

    private boolean isMutationAction(CommandAction action) {
        return action == CommandAction.CREATE
                || action == CommandAction.UPDATE
                || action == CommandAction.REPLACE
                || action == CommandAction.DELETE
                || action == CommandAction.BATCH_CREATE;
    }

    private OfferDraft toDraft(Map<String, String> params) {
        return new OfferDraft(
                params.get("company"),
                params.get("city"),
                params.get("position"),
                params.get("salary"),
                params.get("education"),
                params.get("industry"),
                params.get("type"));
    }

    private OfferSearchCriteria toCriteria(Map<String, String> params, boolean famousOnly) {
        return new OfferSearchCriteria(
                params.get("keyword"),
                params.get("company"),
                params.get("city"),
                params.get("position"),
                params.get("industry"),
                params.get("type"),
                famousOnly);
    }

    private Map<String, String> updatePatch(Map<String, String> params) {
        Map<String, String> patch = new LinkedHashMap<>(params);
        patch.remove("id");
        patch.remove("keyword");
        patch.remove("page");
        patch.remove("size");
        return patch;
    }

    private Long parseAndDelete(Map<String, String> params) {
        Long id = parseRequiredLong(params, "id", "编号");
        offerService.deleteOffer(id);
        return id;
    }

    private Long parseRequiredLong(Map<String, String> params, String key, String label) {
        String value = params.get(key);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("缺少必填字段：" + label);
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(label + "必须是大于 0 的整数。");
        }
    }

    private Integer parseInt(Map<String, String> params, String key) {
        String value = params.get(key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(("page".equals(key) ? "页码" : "每页") + "必须是大于等于 1 的整数。");
        }
    }

    private String help(String topic) {
        if ("1".equals(topic)
                || "查Offer".equalsIgnoreCase(topic)
                || "查薪资".equals(topic)
                || "查询".equals(topic)) {
            return """
                    查Offer格式：
                    “查Offer 关键词= 城市= 公司类型= 岗位类型= 页码= 每页=“

                    所有字段均为选填；页码默认为1，每页默认5条。

                    示例：”查Offer 关键词=字节 岗位类型=实习“""";
        }
        if ("找名企".equals(topic) || "famous".equalsIgnoreCase(topic)) {
            return """
                    找名企已经合并到查Offer。

                    推荐格式：
                    查Offer 关键词=后端 城市=北京 公司类型=互联网 岗位类型=实习""";
        }
        if ("2".equals(topic) || "上传".equals(topic)) {
            return """
                    上传格式：
                    “上传 公司= 城市= 岗位= 薪资= 学历= 公司类型= 岗位类型=“

                    公司、城市、岗位、薪资为必填；学历、公司类型、岗位类型为选填。

                    示例：”上传 公司=字节跳动 城市=北京 岗位=后端开发实习生 薪资=300/天 岗位类型=实习“""";
        }
        if ("3".equals(topic) || "详情".equals(topic)) {
            return """
                    详情格式：
                    “详情 编号=“

                    编号为必填。

                    示例：”详情 编号=1“""";
        }
        if ("4".equals(topic) || "更新".equals(topic)) {
            return """
                    更新格式：
                    “更新 编号= 公司= 城市= 岗位= 薪资= 学历= 公司类型= 岗位类型=“

                    编号为必填；其他字段均为选填，填哪个就更新哪个。

                    示例：”更新 编号=1 薪资=350/天 岗位类型=实习“""";
        }
        if ("5".equals(topic) || "删除".equals(topic)) {
            return """
                    删除格式：
                    “删除 编号=“

                    编号为必填。

                    示例：”删除 编号=1“""";
        }
        return """
                欢迎来到 008 的 OfferBot 小站喵～
                想获取详情命令格式，请回复“帮助 ” + “你要查询功能前的数字”
                1. 查Offer
                2. 上传
                3. 详情
                4. 更新
                5. 删除
                例如：帮助 1""";
    }

    private String formatCreated(Offer offer) {
        return "上传成功\n编号：" + offer.getId() + "\n" + oneLine(offer) + "\n查看详情：详情 编号=" + offer.getId();
    }

    private String formatUpdated(Offer offer) {
        return "更新成功\n编号：" + offer.getId() + "\n查看详情：详情 编号=" + offer.getId();
    }

    private String formatReplaced(Offer offer) {
        return "替换成功\n编号：" + offer.getId() + "\n" + oneLine(offer);
    }

    private String formatDeleted(Long id) {
        return "删除成功\n编号：" + id;
    }

    private String formatOffer(Offer offer) {
        return """
                编号：%d
                公司：%s
                城市：%s
                岗位：%s
                薪资：%s
                学历要求：%s
                公司类型：%s
                岗位类型：%s"""
                .formatted(
                        offer.getId(),
                        offer.getCompany(),
                        offer.getCity(),
                        offer.getPosition(),
                        offer.getSalary(),
                        display(offer.getEducation()),
                        display(offer.getIndustry()),
                        displayType(offer.getType()));
    }

    private String formatPage(PagedResult<Offer> page, String commandName) {
        if (page.records().isEmpty()) {
            return "没有找到匹配结果。\n你可以尝试放宽条件，例如：查Offer 关键词=后端";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("第 ")
                .append(page.page())
                .append("/")
                .append(page.totalPages())
                .append(" 页，共 ")
                .append(page.total())
                .append(" 条");
        for (int i = 0; i < page.records().size(); i++) {
            builder.append("\n")
                    .append(oneLine(page.records().get(i)));
        }
        if (page.page() < page.totalPages()) {
            builder.append("\n\n下一页：")
                    .append(commandName)
                    .append(" 页码=")
                    .append(page.page() + 1)
                    .append(" 每页=")
                    .append(page.size());
        }
        return builder.toString();
    }

    private String formatBatch(BatchCreateResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("批量上传完成\n成功：")
                .append(result.successCount())
                .append(" 条\n失败：")
                .append(result.failureCount())
                .append(" 条");
        if (!result.failures().isEmpty()) {
            builder.append("\n\n").append(String.join("\n", result.failures()));
        } else {
            builder.append("\n查看列表：列表 页码=1 每页=5");
        }
        return builder.toString();
    }

    private String oneLine(Offer offer) {
        return "编号：%d ｜ 公司：%s｜城市：%s｜岗位：%s｜薪资：%s ｜ 学历要求：%s ｜ 公司类型：%s ｜ 岗位类型：%s"
                .formatted(
                        offer.getId(),
                        offer.getCompany(),
                        offer.getCity(),
                        offer.getPosition(),
                        offer.getSalary(),
                        display(offer.getEducation()),
                        display(offer.getIndustry()),
                        displayType(offer.getType()));
    }

    private String display(String value) {
        return StringUtils.hasText(value) ? value : "未知";
    }

    private String displayType(String value) {
        if (!StringUtils.hasText(value)) {
            return "未知";
        }
        return switch (value) {
            case "internship", "实习" -> "实习生";
            case "campus" -> "校招";
            case "fulltime" -> "社招";
            default -> value;
        };
    }
}
