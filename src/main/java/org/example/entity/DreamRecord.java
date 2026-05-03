package org.example.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("dream_records")
public class DreamRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String originalContent;
    private String title;       // 动态标题字段
    private String analysisJson;
    private String imageUrl;
    private String openid;      // 新增 openid 字段
    private String emotionScores;
    private String elements;    // 【新增字段】用于存储核心象征
    private LocalDateTime createTime;
    @TableField("abstract") // 确保 MyBatis-Plus 能识别到新加的列
    private String abstractText;
    private String themeMatch;   // 主题匹配结果 JSON

    // Getter 和 Setter 方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public void setOriginalContent(String originalContent) {
        this.originalContent = originalContent;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAnalysisJson() {
        return analysisJson;
    }

    public void setAnalysisJson(String analysisJson) {
        this.analysisJson = analysisJson;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getEmotionScores() {
        return emotionScores;
    }

    public void setEmotionScores(String emotionScores) {
        this.emotionScores = emotionScores;
    }

    // 新增 elements 的 getter 和 setter
    public String getElements() {
        return elements;
    }

    public void setElements(String elements) {
        this.elements = elements;
    }
    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }
    public String getThemeMatch() {
        return themeMatch;
    }
    public void setThemeMatch(String themeMatch) {
        this.themeMatch = themeMatch;
    }
}