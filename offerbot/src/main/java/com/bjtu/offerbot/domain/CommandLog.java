package com.bjtu.offerbot.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("command_log")
public class CommandLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String rawCommand;

    private String parsedAction;

    private String status;

    private String resultText;

    private String errorMessage;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getRawCommand() {
        return rawCommand;
    }

    public void setRawCommand(String rawCommand) {
        this.rawCommand = rawCommand;
    }

    public String getParsedAction() {
        return parsedAction;
    }

    public void setParsedAction(String parsedAction) {
        this.parsedAction = parsedAction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
