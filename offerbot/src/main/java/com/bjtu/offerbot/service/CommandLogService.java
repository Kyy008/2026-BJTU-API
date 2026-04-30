package com.bjtu.offerbot.service;

import com.bjtu.offerbot.domain.CommandLog;
import com.bjtu.offerbot.mapper.CommandLogMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandLogService {

    private final CommandLogMapper commandLogMapper;

    public CommandLogService(CommandLogMapper commandLogMapper) {
        this.commandLogMapper = commandLogMapper;
    }

    /**
     * 命令日志覆盖成功和失败两种路径，用于演示审计和排查用户输入问题。
     */
    @Transactional
    public void log(String openid, String rawCommand, String action, String status, String resultText, String errorMessage) {
        CommandLog commandLog = new CommandLog();
        commandLog.setOpenid(openid == null ? "" : openid);
        commandLog.setRawCommand(rawCommand == null ? "" : rawCommand);
        commandLog.setParsedAction(action);
        commandLog.setStatus(status);
        commandLog.setResultText(resultText);
        commandLog.setErrorMessage(errorMessage);
        commandLog.setCreatedAt(LocalDateTime.now());
        commandLogMapper.insert(commandLog);
    }
}
