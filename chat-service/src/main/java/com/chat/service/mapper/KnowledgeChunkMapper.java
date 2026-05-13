package com.chat.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chat.common.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("<script>"
            + "SELECT kc.*, COUNT(*) AS match_count FROM knowledge_chunk kc WHERE "
            + "<foreach collection='keywords' item='kw' separator=' OR '>"
            + "kc.content LIKE CONCAT('%', #{kw}, '%')"
            + "</foreach>"
            + " GROUP BY kc.id ORDER BY match_count DESC LIMIT #{limit}"
            + "</script>")
    List<KnowledgeChunk> searchChunks(@Param("keywords") List<String> keywords, @Param("limit") int limit);
}
