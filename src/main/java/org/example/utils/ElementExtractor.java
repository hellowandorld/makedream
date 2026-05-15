package org.example.utils;

import org.example.utils.DictionaryCacheManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElementExtractor {

     //提取梦境内容中的要素

    public static Map<String, String> extract(String text) {
        Map<String, String> resultMap = new HashMap<>();

        // 获取词典缓存
        Map<String, List<String>> dictionaryMap = DictionaryCacheManager.getCache();

        if (dictionaryMap == null || dictionaryMap.isEmpty()) {
            return resultMap;
        }

        // 遍历词典进行文本匹配
        for (Map.Entry<String, List<String>> entry : dictionaryMap.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();

            StringBuilder matched = new StringBuilder();
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    if (matched.length() > 0) {
                        matched.append(",");
                    }
                    matched.append(keyword);
                }
            }

            if (matched.length() > 0) {
                resultMap.put(category, matched.toString());
            }
        }

        return resultMap;
    }
}