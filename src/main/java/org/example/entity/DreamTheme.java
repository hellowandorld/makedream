package org.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("dream_themes")
public class DreamTheme {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String keywords;
    private String style;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}