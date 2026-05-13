package com.chat.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chat.common.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档数据访问接口
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
