package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface DreamDictionaryMapper {

    // 查询所有启用的词典数据
    @Select("SELECT * FROM dream_dictionary WHERE status = 1")
    List<Map<String, Object>> selectAllActive();
}