package com.chat.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chat.common.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话数据访问接口
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
