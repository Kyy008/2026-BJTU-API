package com.bjtu.offerbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtu.offerbot.command.CommandExecutor;
import com.bjtu.offerbot.domain.CommandLog;
import com.bjtu.offerbot.domain.WxUser;
import com.bjtu.offerbot.mapper.CommandLogMapper;
import com.bjtu.offerbot.mapper.WxUserMapper;
import com.bjtu.offerbot.service.WxUserService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CommandExecutorTests {

    private static final Pattern ID_PATTERN = Pattern.compile("编号：(\\d+)");

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    private WxUserService wxUserService;

    @Autowired
    private CommandLogMapper commandLogMapper;

    @Autowired
    private WxUserMapper wxUserMapper;

    @Test
    void executesCrudCommandsWithChineseForms() {
        String createReply = commandExecutor.execute(
                "openid-1",
                "上传 公司=字节跳动 城市=北京 岗位=后端实习 薪资=200/天 学历=本科 行业=互联网 类型=实习");
        assertThat(createReply).contains("上传成功").contains("编号：");
        long id = extractId(createReply);

        String queryReply = commandExecutor.execute("openid-1", "查Offer 关键词=字节 公司类型=互联网 岗位类型=实习 页码=1 每页=5");
        assertThat(queryReply)
                .contains("编号：" + id + " ｜ 公司：字节跳动｜城市：北京｜岗位：后端实习｜薪资：200/天 ｜ 学历要求：本科 ｜ 公司类型：互联网 ｜ 岗位类型：实习生");

        String detailReply = commandExecutor.execute("openid-1", "详情 编号=" + id);
        assertThat(detailReply).contains("公司：字节跳动").contains("公司类型：互联网").contains("岗位类型：实习生");

        String updateReply = commandExecutor.execute("openid-1", "更新 编号=" + id + " 薪资=250/天");
        assertThat(updateReply).contains("更新成功");

        String replaceReply = commandExecutor.execute(
                "openid-1",
                "替换 编号=" + id + " 公司=腾讯 城市=深圳 岗位=Java后端 薪资=20k*15 学历=本科 公司类型=互联网 岗位类型=校招");
        assertThat(replaceReply).contains("替换成功").contains("腾讯");

        String deleteReply = commandExecutor.execute("openid-1", "删除 编号=" + id);
        assertThat(deleteReply).contains("删除成功");
    }

    @Test
    void executesBatchCreateAndLogsFailures() {
        String reply = commandExecutor.execute("openid-2", """
                批量上传
                字节跳动,北京,后端实习,200/天,本科,互联网,实习
                腾讯,深圳,-,20k*15,本科,互联网,校招
                """);

        assertThat(reply).contains("批量上传完成").contains("成功：1 条").contains("失败：1 条");
        CommandLog latest = commandLogMapper.selectOne(new LambdaQueryWrapper<CommandLog>()
                .eq(CommandLog::getOpenid, "openid-2")
                .orderByDesc(CommandLog::getId)
                .last("LIMIT 1"));
        assertThat(latest.getStatus()).isEqualTo("SUCCESS");
        assertThat(latest.getParsedAction()).isEqualTo("BATCH_CREATE");
    }

    @Test
    void appliesPermissionRules() {
        commandExecutor.execute("openid-3", "帮助");
        wxUserService.updateState("openid-3", WxUserService.STATE_SPIDER);
        assertThat(commandExecutor.execute("openid-3", "查Offer 关键词=后端")).contains("访问被限制");

        wxUserService.updateState("openid-3", WxUserService.STATE_BANNED);
        assertThat(commandExecutor.execute("openid-3", "上传 公司=字节跳动 城市=北京 岗位=后端实习 薪资=200/天"))
                .contains("上传被拒绝");
    }

    @Test
    void preventsUsersFromMutatingOffersUploadedByOthers() {
        String createReply = commandExecutor.execute(
                "owner-openid",
                "上传 公司=字节跳动 城市=北京 岗位=后端实习 薪资=200/天 学历=本科 公司类型=互联网 岗位类型=实习");
        long id = extractId(createReply);

        assertThat(commandExecutor.execute("other-openid", "更新 编号=" + id + " 薪资=250/天"))
                .contains("只能修改或删除自己上传的 Offer");
        assertThat(commandExecutor.execute("other-openid", "删除 编号=" + id))
                .contains("只能修改或删除自己上传的 Offer");

        commandExecutor.execute("admin-openid", "帮助");
        WxUser admin = wxUserService.findByOpenid("admin-openid");
        admin.setRole(WxUserService.ROLE_ADMIN);
        wxUserMapper.updateById(admin);

        assertThat(commandExecutor.execute("admin-openid", "更新 编号=" + id + " 薪资=300/天"))
                .contains("更新成功");
    }

    @Test
    void repliesWithNumberedHelpMenu() {
        assertThat(commandExecutor.execute("openid-help", "帮助"))
                .contains("欢迎来到 008 的 OfferBot 小站喵～")
                .contains("想获取详情命令格式，请回复“帮助 ” + “你要查询功能前的数字”")
                .contains("1. 查Offer")
                .contains("例如：帮助 1");
    }

    @Test
    void repliesWithDetailedHelpByNumber() {
        assertThat(commandExecutor.execute("openid-help-detail", "帮助 1"))
                .contains("查Offer格式")
                .contains("“查Offer 关键词= 城市= 公司类型= 岗位类型= 页码= 每页=“")
                .contains("所有字段均为选填")
                .contains("示例：”查Offer 关键词=字节 岗位类型=实习“");
        assertThat(commandExecutor.execute("openid-help-detail", "帮助 2"))
                .contains("上传格式")
                .contains("“上传 公司= 城市= 岗位= 薪资= 学历= 公司类型= 岗位类型=“")
                .contains("公司、城市、岗位、薪资为必填")
                .contains("示例：”上传 公司=字节跳动 城市=北京 岗位=后端开发实习生 薪资=300/天 岗位类型=实习“");
        assertThat(commandExecutor.execute("openid-help-detail", "帮助 3"))
                .contains("详情格式")
                .contains("“详情 编号=“")
                .contains("示例：”详情 编号=1“");
        assertThat(commandExecutor.execute("openid-help-detail", "帮助 4"))
                .contains("更新格式")
                .contains("“更新 编号= 公司= 城市= 岗位= 薪资= 学历= 公司类型= 岗位类型=“")
                .contains("示例：”更新 编号=1 薪资=350/天 岗位类型=实习“");
        assertThat(commandExecutor.execute("openid-help-detail", "帮助 5"))
                .contains("删除格式")
                .contains("“删除 编号=“")
                .contains("示例：”删除 编号=1“");
        assertThat(commandExecutor.execute("openid-help-detail", "帮助 上传"))
                .contains("上传格式")
                .contains("公司、城市、岗位、薪资为必填")
                .doesNotContain("英文")
                .doesNotContain("offer create");
    }

    @Test
    void rejectsEnglishCommandsAndFields() {
        assertThat(commandExecutor.execute("openid-english", "offer query keyword=字节")).contains("暂时看不懂");
        assertThat(commandExecutor.execute("openid-english", "查Offer keyword=字节")).contains("无法识别字段");
    }

    private long extractId(String reply) {
        Matcher matcher = ID_PATTERN.matcher(reply);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
