package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chat.common.entity.SysUser;
import com.chat.service.mapper.UserMapper;
import com.chat.service.service.AIChatService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class AIChatServiceImpl implements AIChatService {

    @Resource
    private UserMapper userMapper;

    private volatile Long cachedAiUserId;

    @Override
    public Long getAiUserId() {
        if (cachedAiUserId == null) {
            synchronized (this) {
                if (cachedAiUserId == null) {
                    SysUser ai = userMapper.selectOne(
                            new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getUsername, "ai_assistant")
                    );
                    if (ai == null) {
                        throw new RuntimeException("AI助手用户未初始化，请先执行数据库迁移脚本");
                    }
                    cachedAiUserId = ai.getId();
                }
            }
        }
        return cachedAiUserId;
    }

    @Override
    public String generateResponse(String userMessage) {
        String msg = userMessage.trim();
        if (msg.contains("你好") || msg.contains("hello") || msg.contains("嗨") || msg.contains("hi")) {
            return "您好！我是智能客服小助，当前暂无人工客服在线，我会尽力为您解答。请问有什么可以帮您？";
        }
        if (msg.contains("帮助") || msg.contains("help") || msg.contains("功能")) {
            return "我可以帮您解答常见问题：\n1. 订单查询与跟踪\n2. 产品使用帮助\n3. 售后服务与退换货\n4. 账户与积分问题\n\n直接输入您的问题即可，如需人工客服，请稍后再试。";
        }
        if (msg.contains("订单") || msg.contains("order")) {
            return "关于订单的问题，建议您提供订单编号，方便我们为您查询。您也可以输入\"人工客服\"尝试转接人工服务。";
        }
        if (msg.contains("人工") || msg.contains("真人") || msg.contains("转接")) {
            return "好的，我理解您需要人工客服。目前暂无人工客服在线，您可以稍后再试。如果问题紧急，建议您留下联系方式，客服上线后会优先联系您。";
        }
        if (msg.contains("谢谢") || msg.contains("thank") || msg.contains("感谢")) {
            return "不客气！很高兴能帮到您。还有其他问题吗？";
        }
        return "收到您的消息了。您能再详细描述一下遇到的问题吗？我会尽力帮您解答。";
    }
}
