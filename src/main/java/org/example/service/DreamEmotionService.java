package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.DreamTheme;
import org.example.mapper.DreamThemeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class DreamEmotionService {

    @Autowired
    private DreamThemeMapper dreamThemeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // IDF 缓存：关键字 → IDF 值
    private Map<String, Double> idfMap = new HashMap<>();

    @PostConstruct
    public void initIDF() {
        try {
            List<DreamTheme> allThemes = dreamThemeMapper.selectAllActive();
            int totalThemes = allThemes.size();
            if (totalThemes == 0) return;

            // 统计每个关键词出现在几个主题中
            Map<String, Integer> keywordDocFreq = new HashMap<>();
            for (DreamTheme theme : allThemes) {
                List<String> keywords = parseKeywords(theme.getKeywords());
                for (String kw : keywords) {
                    keywordDocFreq.merge(kw, 1, Integer::sum);
                }
            }

            // 计算 IDF = log(总主题数 / 该词出现的主题数)
            for (Map.Entry<String, Integer> entry : keywordDocFreq.entrySet()) {
                double idf = Math.log((double) totalThemes / entry.getValue());
                idfMap.put(entry.getKey(), Math.max(idf, 0.0));
            }
            System.out.println(">>> TF-IDF 词典初始化完成，IDF 词条数: " + idfMap.size());
        } catch (Exception e) {
            System.err.println(">>> IDF 计算失败，退化为纯 TF 模式: " + e.getMessage());
        }
    }

    /**
     * 计算各类情绪得分（TF × IDF × Weight）
     *
     * @param dreamText  梦境文本
     * @param dictionary 包含关键词和权重的字典列表
     * @return 各类情绪得分 Map
     */
    public Map<String, Integer> calculateEmotionScores(String dreamText, List<Map<String, Object>> dictionary) {
        Map<String, Integer> scores = new HashMap<>();
        String[] emotionTypes = {"焦虑型", "恐惧型", "孤独型", "压抑型", "治愈型", "愉悦型", "混合型"};
        for (String type : emotionTypes) {
            scores.put(type, 0);
        }

        // 遍历词典，计算 TF × IDF × Weight
        for (Map<String, Object> item : dictionary) {
            String keyword = (String) item.get("keyword");
            int weight = (Integer) item.get("weight");
            int frequency = countKeywordFrequency(dreamText, keyword);

            if (frequency > 0) {
                // TF = 词频, IDF = 逆文档频率, Weight = 词典预设权重
                double idf = idfMap.getOrDefault(keyword, idfMap.isEmpty() ? 1.0 : Math.log(7));
                double rawScore = frequency * idf * weight;

                String emotionType = mapKeywordToEmotionType(keyword);
                scores.merge(emotionType, (int) Math.round(rawScore), Integer::sum);
            }
        }

        return scores;
    }

    private int countKeywordFrequency(String text, String keyword) {
        if (text == null || keyword == null || text.isEmpty()) return 0;
        int count = 0, index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private String mapKeywordToEmotionType(String keyword) {
        List<String> anxietyWords = Arrays.asList("焦虑", "紧张", "窒息", "压抑", "压力", "压迫感", "紧绷", "不安", "沉重感");
        List<String> fearWords = Arrays.asList("恐惧", "害怕", "绝望", "无助", "震惊", "无力感");
        List<String> lonelyWords = Arrays.asList("孤独", "迷茫", "空虚", "失落");
        List<String> depressedWords = Arrays.asList("难过", "痛苦", "烦躁", "生气", "愤怒", "疲惫", "负担", "悲伤", "后悔", "羞愧", "羞耻", "内疚", "自责", "嫉妒", "厌恶", "讨厌", "冷漠", "无奈");
        List<String> healingWords = Arrays.asList("平静", "释怀", "温暖", "安心", "放松", "满足", "感动", "轻松", "怀念", "感恩", "感激", "期待");
        List<String> happyWords = Arrays.asList("狂喜", "喜悦", "激动", "开心", "快乐", "兴奋", "幸福", "希望", "自信", "愉悦", "可爱", "勇气", "勇敢", "决心", "骄傲");

        if (anxietyWords.contains(keyword)) return "焦虑型";
        if (fearWords.contains(keyword)) return "恐惧型";
        if (lonelyWords.contains(keyword)) return "孤独型";
        if (depressedWords.contains(keyword)) return "压抑型";
        if (healingWords.contains(keyword)) return "治愈型";
        if (happyWords.contains(keyword)) return "愉悦型";
        return "混合型";
    }

    private List<String> parseKeywords(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
