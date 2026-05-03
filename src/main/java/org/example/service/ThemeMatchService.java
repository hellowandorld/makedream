package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.DreamTheme;
import org.example.mapper.DreamThemeMapper;
import org.example.utils.DictionaryCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ThemeMatchService {

    @Autowired
    private DreamThemeMapper dreamThemeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<DreamTheme> cachedThemes = new ArrayList<>();

    @PostConstruct
    public void init() {
        cachedThemes = dreamThemeMapper.selectAllActive();
    }

    /**
     * 主入口：返回主题匹配结果
     */
    public Map<String, Object> match(String dreamText, Map<String, Integer> emotionScores) {
        Map<String, Object> result = new HashMap<>();

        // 1. 算每个主题的原始匹配分
        Map<String, Double> rawScores = new LinkedHashMap<>();
        for (DreamTheme theme : cachedThemes) {
            List<String> keywords = parseKeywords(theme.getKeywords());
            int totalWeight = 0;
            int hitWeight = 0;

            for (String kw : keywords) {
                int freq = countOccurrences(dreamText, kw);
                int w = getKeywordWeight(kw);
                totalWeight += w;
                hitWeight += freq * w;
            }

            rawScores.put(theme.getName(), totalWeight > 0
                    ? (double) hitWeight / totalWeight
                    : 0.0);
        }

        // 2. 归一化百分比（总和为 100%）
        double sum = rawScores.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> percentMap = new LinkedHashMap<>();
        if (sum > 0) {
            for (Map.Entry<String, Double> e : rawScores.entrySet()) {
                percentMap.put(e.getKey(), Math.round(e.getValue() / sum * 1000.0) / 10.0);
            }
        }

        // 3. 主导主题（百分比最高的）
        String dominant = percentMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("混合型");

        // 4. 情绪倾向
        int positive = emotionScores.getOrDefault("治愈型", 0)
                + emotionScores.getOrDefault("愉悦型", 0);
        int negative = emotionScores.getOrDefault("焦虑型", 0)
                + emotionScores.getOrDefault("恐惧型", 0)
                + emotionScores.getOrDefault("孤独型", 0)
                + emotionScores.getOrDefault("压抑型", 0);
        String moodTendency;
        if (positive > negative * 1.5) moodTendency = "积极倾向";
        else if (negative > positive * 1.5) moodTendency = "消极倾向";
        else if (positive == 0 && negative == 0) moodTendency = "中性倾向";
        else moodTendency = "复杂倾向";

        // 5. 风格 = 主导主题对应的 style
        String style = cachedThemes.stream()
                .filter(t -> t.getName().equals(dominant))
                .findFirst()
                .map(DreamTheme::getStyle)
                .orElse("日常");

        result.put("dominant", dominant);
        result.put("percentages", percentMap);
        result.put("moodTendency", moodTendency);
        result.put("style", style);
        return result;
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }

    private int getKeywordWeight(String kw) {
        List<Map<String, Object>> dict = DictionaryCacheManager.getDictionaryCache();
        for (Map<String, Object> item : dict) {
            if (kw.equals(item.get("keyword"))) {
                return (Integer) item.get("weight");
            }
        }
        return 1; // 词典里没有的词默认权重1
    }

    private List<String> parseKeywords(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}