package com.chat.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chat.common.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息数据访问接口
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
