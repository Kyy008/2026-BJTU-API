package com.bjtu.offerbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bjtu.offerbot.command.CommandAction;
import com.bjtu.offerbot.command.CommandParser;
import com.bjtu.offerbot.command.ParsedCommand;
import com.bjtu.offerbot.service.BusinessException;
import org.junit.jupiter.api.Test;

class CommandParserTests {

    private final CommandParser commandParser = new CommandParser();

    @Test
    void parsesChineseCreateCommand() {
        ParsedCommand command = commandParser.parse("上传 公司=字节跳动 城市=北京 岗位=后端实习 薪资=200/天");

        assertThat(command.action()).isEqualTo(CommandAction.CREATE);
        assertThat(command.params())
                .containsEntry("company", "字节跳动")
                .containsEntry("city", "北京")
                .containsEntry("position", "后端实习")
                .containsEntry("salary", "200/天");
    }

    @Test
    void rejectsEnglishCommand() {
        assertThatThrownBy(() -> commandParser.parse("offer query keyword=字节 city=北京"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂时看不懂");
    }

    @Test
    void rejectsEnglishFieldNames() {
        assertThatThrownBy(() -> commandParser.parse("查Offer keyword=字节 city=北京"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别字段");
    }

    @Test
    void parsesQueryOfferCommandWithBusinessFieldAliases() {
        ParsedCommand command = commandParser.parse("查Offer 关键词=后端 公司=字节跳动 城市=北京 公司类型=互联网 岗位类型=实习");

        assertThat(command.action()).isEqualTo(CommandAction.QUERY);
        assertThat(command.params())
                .containsEntry("keyword", "后端")
                .containsEntry("company", "字节跳动")
                .containsEntry("city", "北京")
                .containsEntry("industry", "互联网")
                .containsEntry("type", "实习");
    }

    @Test
    void parsesBatchCreateRows() {
        ParsedCommand command = commandParser.parse("""
                批量上传
                字节跳动,北京,后端实习,200/天,本科,互联网,实习
                腾讯,深圳,Java后端,20000/月,本科,互联网,校招
                """);

        assertThat(command.action()).isEqualTo(CommandAction.BATCH_CREATE);
        assertThat(command.batchRows()).hasSize(2);
    }

    @Test
    void rejectsUnknownField() {
        assertThatThrownBy(() -> commandParser.parse("上传 新资=200/天"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别字段");
    }
}
