package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

/**
 * 梦境解析核心控制器 - 安全加载配置版
 */
@RestController
@RequestMapping("/api/dream")
@CrossOrigin(origins = "*")
public class DreamController {

    // 1. 改为使用注解加载配置，冒号后面留空表示默认值为空字符串
    // 这样代码里就不再包含真实的 API Key 字符串了
    @Value("${google.gemini.api-key:}")
    private String geminiApiKey;

    private final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-09-2025:generateContent?key=";
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/test")
    public String test() {
        return "后端运行中。密钥加载状态：" + (geminiApiKey != null && !geminiApiKey.isEmpty() ? "已就绪" : "待配置");
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDream(@RequestBody Map<String, String> request) {
        String userDream = request.get("content");
        Map<String, Object> response = new HashMap<>();

        // 检查 Key 是否成功加载
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            response.put("success", false);
            response.put("msg", "服务器 API Key 配置缺失");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        try {
            String aiResult = callGeminiAI(userDream);
            response.put("success", true);
            response.put("dream_analysis", aiResult);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("msg", "AI 响应异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String callGeminiAI(String dreamText) throws Exception {
        String fullUrl = BASE_URL + geminiApiKey;

        // 构造请求体 (保持之前的结构)
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> systemInstruction = new HashMap<>();
        systemInstruction.put("parts", Collections.singletonList(Collections.singletonMap("text", "你是一位精通心理学的解梦大师。")));
        payload.put("systemInstruction", systemInstruction);

        Map<String, Object> content = Collections.singletonMap("parts", Collections.singletonList(Collections.singletonMap("text", "我的梦境是：" + dreamText)));
        payload.put("contents", Collections.singletonList(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> aiResponse = restTemplate.postForEntity(fullUrl, entity, Map.class);

        if (aiResponse.getStatusCode() == HttpStatus.OK) {
            List candidates = (List) aiResponse.getBody().get("candidates");
            Map contentObj = (Map) ((Map) candidates.get(0)).get("content");
            return (String) ((List<Map>) contentObj.get("parts")).get(0).get("text");
        }
        throw new RuntimeException("Gemini API 返回错误状态: " + aiResponse.getStatusCode());
    }
}