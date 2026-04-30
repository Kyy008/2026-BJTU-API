package com.bjtu.offerbot.service;

import com.bjtu.offerbot.command.CommandExecutor;
import java.io.StringReader;
import java.time.Instant;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

@Service
public class WechatMessageService {

    private final CommandExecutor commandExecutor;

    public WechatMessageService(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * 解析微信 XML、执行业务命令，并重新封装为微信要求的 XML 文本消息。
     */
    public String reply(String requestBody) {
        WechatInboundMessage message = parse(requestBody);
        String replyContent = buildReplyContent(message);
        return textReply(message.fromUserName(), message.toUserName(), replyContent);
    }

    private String buildReplyContent(WechatInboundMessage message) {
        if ("event".equalsIgnoreCase(message.msgType())) {
            if ("subscribe".equalsIgnoreCase(message.event())) {
                return "感谢关注 OfferBot。发送“帮助”查看可用指令。";
            }
            return "事件已收到。发送“帮助”查看可用指令。";
        }

        if (!"text".equalsIgnoreCase(message.msgType())) {
            return "当前先支持文字消息。发送“帮助”查看可用指令。";
        }

        return commandExecutor.execute(message.fromUserName(), message.content());
    }

    private WechatInboundMessage parse(String requestBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 微信消息来自外部 XML 输入，关闭 DTD 和外部实体以避免 XXE 风险。
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(requestBody)));
            document.getDocumentElement().normalize();

            return new WechatInboundMessage(
                    readText(document, "ToUserName"),
                    readText(document, "FromUserName"),
                    readText(document, "MsgType"),
                    readText(document, "Content"),
                    readText(document, "Event"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WeChat XML message", e);
        }
    }

    private String readText(Document document, String tagName) {
        if (document.getElementsByTagName(tagName).getLength() == 0) {
            return "";
        }

        String text = document.getElementsByTagName(tagName).item(0).getTextContent();
        return text == null ? "" : text;
    }

    private String textReply(String toUserName, String fromUserName, String content) {
        return """
                <xml>
                  <ToUserName><![CDATA[%s]]></ToUserName>
                  <FromUserName><![CDATA[%s]]></FromUserName>
                  <CreateTime>%d</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[%s]]></Content>
                </xml>"""
                .formatted(
                        cdataSafe(toUserName),
                        cdataSafe(fromUserName),
                        Instant.now().getEpochSecond(),
                        cdataSafe(content));
    }

    private String cdataSafe(String value) {
        // 微信回复字段用 CDATA 包裹，这里拆开非法的 CDATA 结束符，避免生成畸形 XML。
        return value == null ? "" : value.replace("]]>", "]]]]><![CDATA[>");
    }

    private record WechatInboundMessage(
            String toUserName,
            String fromUserName,
            String msgType,
            String content,
            String event) {
    }
}
