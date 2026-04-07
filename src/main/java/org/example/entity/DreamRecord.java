package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dream_records") // 对应数据库表名
public class DreamRecord {
    @TableId(type = IdType.AUTO) // 主键自增
    private Long id;

    private String originalContent; // MyBatis-Plus 会自动对应 original_content
    private String title;
    private String analysisJson;
    private String imageUrl;
    private LocalDateTime createTime;
}