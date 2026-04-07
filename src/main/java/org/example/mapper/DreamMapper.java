package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.entity.DreamRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DreamMapper extends BaseMapper<DreamRecord> {
    // 基础的插入、查询功能已经由 BaseMapper 自动提供了
}