package org.example.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DreamEmotionService {

    /**
     * 计算各类情绪得分
     *
     * @param dreamText 梦境文本
     * @param dictionary 包含关键词和权重的字典列表
     * @return 各类情绪得分 Map
     */
    public Map<String, Integer> calculateEmotionScores(String dreamText, List<Map<String, Object>> dictionary) {
        Map<String, Integer> scores = new HashMap<>();

        // 初始化各个情绪类型得分
        String[] emotionTypes = {"焦虑型", "恐惧型", "孤独型", "压抑型", "治愈型", "愉悦型", "混合型"};
        for (String type : emotionTypes) {
            scores.put(type, 0);
        }

        // 遍历词典计算得分
        for (Map<String, Object> item : dictionary) {
            String keyword = (String) item.get("keyword");
            int weight = (Integer) item.get("weight");

            // 计算词语 w 在文本 D 中的出现频次
            int frequency = countKeywordFrequency(dreamText, keyword);

            if (frequency > 0) {
                // 根据关键词归类到对应情绪类型
                String emotionType = mapKeywordToEmotionType(keyword);
                int currentScore = scores.get(emotionType);

                // 累计得分
                scores.put(emotionType, currentScore + (frequency * weight));
            }
        }

        return scores;
    }

    /**
     * 统计关键词出现的频次
     */
    private int countKeywordFrequency(String text, String keyword) {
        if (text == null || keyword == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = text.indexOf(keyword);
        while (index != -1) {
            count++;
            index = text.indexOf(keyword, index + keyword.length());
        }
        return count;
    }

    /**
     * 关键词分类映射
     */
    private String mapKeywordToEmotionType(String keyword) {
        List<String> anxietyWords = Arrays.asList(
                "焦虑", "紧张", "窒息", "压抑", "压力", "压迫感", "紧绷", "不安",
                "沉重感"
        );

// 恐惧型
        List<String> fearWords = Arrays.asList(
                "恐惧", "害怕", "绝望", "无助", "震惊", "无力感"
        );

// 孤独型
        List<String> lonelyWords = Arrays.asList(
                "孤独", "迷茫", "空虚", "失落"
        );

// 压抑型
        List<String> depressedWords = Arrays.asList(
                "难过", "痛苦", "烦躁", "生气", "愤怒", "疲惫", "负担",
                "悲伤", "后悔", "羞愧", "羞耻", "内疚", "自责", "嫉妒",
                "厌恶", "讨厌", "冷漠", "无奈"
        );

// 治愈型
        List<String> healingWords = Arrays.asList(
                "平静", "释怀", "温暖", "安心", "放松", "满足", "感动", "轻松",
                "怀念", "感恩", "感激", "期待"
        );

// 愉悦型
        List<String> happyWords = Arrays.asList(
                "狂喜", "喜悦", "激动", "开心", "快乐", "兴奋", "幸福", "希望",
                "自信", "愉悦", "可爱", "勇气", "勇敢", "决心", "骄傲"
        );

        if (anxietyWords.contains(keyword)) return "焦虑型";
        if (fearWords.contains(keyword)) return "恐惧型";
        if (lonelyWords.contains(keyword)) return "孤独型";
        if (depressedWords.contains(keyword)) return "压抑型";
        if (healingWords.contains(keyword)) return "治愈型";
        if (happyWords.contains(keyword)) return "愉悦型";

        return "混合型";
    }
}