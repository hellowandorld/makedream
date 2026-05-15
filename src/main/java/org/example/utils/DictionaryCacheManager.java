package org.example.utils;

import org.example.mapper.DreamDictionaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DictionaryCacheManager implements CommandLineRunner {

    @Autowired
    private DreamDictionaryMapper dreamDictionaryMapper;

    // 内存中的全局缓存池
    private static List<Map<String, Object>> dictionaryCache = new ArrayList<>();

    // 项目启动时执行
    @Override
    public void run(String... args) throws Exception {
        loadDictionary();
    }

     //加载/刷新词典缓存
    public synchronized void loadDictionary() {
        dictionaryCache.clear();
        List<Map<String, Object>> activeItems = dreamDictionaryMapper.selectAllActive();
        dictionaryCache.addAll(activeItems);
        System.out.println(">>> 梦境字典缓存加载完毕，当前词条总数: " + dictionaryCache.size());
    }


     //获取全局词典缓存 (原始 List 结构)
    public static List<Map<String, Object>> getDictionaryCache() {
        return dictionaryCache;
    }

    public static Map<String, List<String>> getCache() {
        Map<String, List<String>> resultMap = new HashMap<>();

        for (Map<String, Object> item : dictionaryCache) {
            String category = (String) item.get("category");
            String keyword = (String) item.get("keyword");

            resultMap.computeIfAbsent(category, k -> new ArrayList<>()).add(keyword);
        }

        return resultMap;
    }
}