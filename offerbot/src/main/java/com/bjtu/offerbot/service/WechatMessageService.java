package com.bjtu.offerbot.service;

import java.io.StringReader;
import java.time.Instant;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

@Service
public class WechatMessageService {

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

        String content = message.content().trim();
        if (!StringUtils.hasText(content)) {
            return "我收到了一条空消息。发送“帮助”查看可用指令。";
        }

        if (isHelpCommand(content)) {
            return """
                    OfferBot 已连接。
                    发送“你好”测试连通。
                    发送“需求”查看当前作业方向。
                    后续会接入岗位、公司和投递记录查询。""";
        }

        if ("你好".equals(content) || "hi".equalsIgnoreCase(content) || "hello".equalsIgnoreCase(content)) {
            return "你好，我是 OfferBot。公众号消息链路已经打通，发送“帮助”查看可用指令。";
        }

        if ("需求".equals(content) || "作业".equals(content)) {
            return "当前阶段先完成公众号接入、服务器验证和基础消息回复；下一阶段会根据作业需求扩展问答和数据查询能力。";
        }

        return "收到：" + content + "\n发送“帮助”查看可用指令。";
    }

    private boolean isHelpCommand(String content) {
        return "帮助".equals(content)
                || "help".equalsIgnoreCase(content)
                || "menu".equalsIgnoreCase(content);
    }

    private WechatInboundMessage parse(String requestBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
