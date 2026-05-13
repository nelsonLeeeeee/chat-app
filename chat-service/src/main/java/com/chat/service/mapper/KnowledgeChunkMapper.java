package com.chat.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chat.common.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识分块数据访问接口
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 按关键词搜索分块，按匹配度排序
     */
    @Select("<script>"
            + "SELECT kc.*, COUNT(*) AS match_count FROM knowledge_chunk kc WHERE "
            + "<foreach collection='keywords' item='kw' separator=' OR '>"
            + "kc.content LIKE CONCAT('%', #{kw}, '%')"
            + "</foreach>"
            + " GROUP BY kc.id ORDER BY match_count DESC LIMIT #{limit}"
            + "</script>")
    List<KnowledgeChunk> searchChunks(@Param("keywords") List<String> keywords, @Param("limit") int limit);
}
