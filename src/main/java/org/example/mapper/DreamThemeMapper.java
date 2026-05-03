package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.entity.DreamTheme;
import java.util.List;

@Mapper
public interface DreamThemeMapper extends BaseMapper<DreamTheme> {
    @Select("SELECT * FROM dream_themes WHERE status = 1")
    List<DreamTheme> selectAllActive();
}