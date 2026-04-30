package org.example.utils;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class SensitiveWordEngine {

    private Map<Object, Object> sensitiveWordMap;
    private static final String IS_END = "isEnd";

    @PostConstruct
    public void init() {
        Set<String> sensitiveWordSet = new HashSet<>();
        try {
            InputStreamReader read = new InputStreamReader(
                    this.getClass().getClassLoader().getResourceAsStream("sensitive_words.txt"),
                    StandardCharsets.UTF_8
            );

            if (read != null) {
                BufferedReader bufferedReader = new BufferedReader(read);
                String txt = null;
                while ((txt = bufferedReader.readLine()) != null) {
                    // 忽略空行和前后空格
                    String word = txt.trim();
                    if (!word.isEmpty()) {
                        sensitiveWordSet.add(word);
                    }
                }
                read.close();
            }
        } catch (Exception e) {
            System.err.println("读取敏感词库文件失败: " + e.getMessage());
            // 容错处理：如果文件读取失败，至少保留几个核心词防止裸奔
            sensitiveWordSet.add("违法词");
        }

        // 初始化 DFA 字典树
        initSensitiveWordMap(sensitiveWordSet);
        System.out.println("敏感词库初始化完成。成功加载词数: " + sensitiveWordSet.size());
    }

    private void initSensitiveWordMap(Set<String> sensitiveWordSet) {
        sensitiveWordMap = new HashMap<>(sensitiveWordSet.size());
        String key = null;
        Map nowMap = null;
        Map<String, String> newWorMap = null;

        Iterator<String> iterator = sensitiveWordSet.iterator();
        while (iterator.hasNext()) {
            key = iterator.next();
            nowMap = sensitiveWordMap;
            for (int i = 0; i < key.length(); i++) {
                char keyChar = key.charAt(i);
                Object wordMap = nowMap.get(keyChar);
                if (wordMap != null) {
                    nowMap = (Map) wordMap;
                } else {
                    newWorMap = new HashMap<>();
                    newWorMap.put("isEnd", "0");
                    nowMap.put(keyChar, newWorMap);
                    nowMap = newWorMap;
                }
                if (i == key.length() - 1) {
                    nowMap.put("isEnd", "1");
                }
            }
        }
    }

    /**
     * 判断文字是否包含敏感词
     */
    public boolean containsSensitiveWord(String txt) {
        for (int i = 0; i < txt.length(); i++) {
            int matchFlag = checkSensitiveWord(txt, i);
            if (matchFlag > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查文字中是否包含敏感词及其长度
     */
    private int checkSensitiveWord(String txt, int beginIndex) {
        boolean flag = false;
        int matchFlag = 0;
        char word = 0;
        Map nowMap = sensitiveWordMap;
        for (int i = beginIndex; i < txt.length(); i++) {
            word = txt.charAt(i);
            nowMap = (Map) nowMap.get(word);
            if (nowMap != null) {
                matchFlag++;
                if ("1".equals(nowMap.get("isEnd"))) {
                    flag = true;
                }
            } else {
                break;
            }
        }
        if (matchFlag < 2 || !flag) {
            matchFlag = 0;
        }
        return matchFlag;
    }
}