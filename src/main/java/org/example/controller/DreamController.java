package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.*;

@RestController
@RequestMapping("/api/dream")
@CrossOrigin(origins = "*")
public class DreamController {

    @Value("${google.gemini.api-key:}")
    private String geminiApiKey;

    private final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-09-2025:generateContent?key=";

    // --- 代理配置：如果你在中国大陆使用，必须配置此项 ---
    private final String PROXY_HOST = "127.0.0.1";
    private final int PROXY_PORT = 7890; // 这里改成你代理软件的端口(常用 7890, 1080, 10809)

    private RestTemplate getRestTemplate() {
        // 创建一个支持代理的请求工厂
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 只有当需要访问国外 API 且有代理时才开启
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT));
        factory.setProxy(proxy);

        // 设置超时时间，防止无限等待
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);

        return new RestTemplate(factory);
    }

    @GetMapping("/test")
    public String test() {
        return "后端运行中。API Key 状态：" + (geminiApiKey.isEmpty() ? "未配置" : "已加载");
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDream(@RequestBody Map<String, String> request) {
        String userDream = request.get("content");
        Map<String, Object> response = new HashMap<>();

        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            response.put("success", false);
            response.put("msg", "服务器未配置 AI 密钥");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        try {
            String aiResult = callGeminiAI(userDream);
            response.put("success", true);
            response.put("dream_analysis", aiResult);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // 在控制台打印具体的错误详情
            response.put("success", false);
            response.put("msg", "AI 服务连接超时，请检查后端网络环境");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String callGeminiAI(String dreamText) throws Exception {
        String fullUrl = BASE_URL + geminiApiKey;

        // 构造请求体
        Map<String, Object> payload = new HashMap<>();

        // 设定 AI 身份
        Map<String, Object> systemInstruction = new HashMap<>();
        systemInstruction.put("parts", Collections.singletonList(
                Collections.singletonMap("text", "你是一位精通心理学的解梦大师，请用温柔且专业的口吻为用户解析梦境，并给出 1 条建议。")
        ));
        payload.put("systemInstruction", systemInstruction);

        Map<String, Object> userPart = Collections.singletonMap("text", "我的梦境是：" + dreamText);
        Map<String, Object> content = Collections.singletonMap("parts", Collections.singletonList(userPart));
        payload.put("contents", Collections.singletonList(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        // 使用带代理的 RestTemplate 发送请求
        ResponseEntity<Map> aiResponse = getRestTemplate().postForEntity(fullUrl, entity, Map.class);

        if (aiResponse.getStatusCode() == HttpStatus.OK && aiResponse.getBody() != null) {
            // 逐层解析 JSON 数据
            List candidates = (List) aiResponse.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                // 1. 获取第一个候选结果 (Map 结构)
                Map firstCandidate = (Map) candidates.get(0);
                // 2. 从中提取 "content" 字段 (又是一个 Map 结构)
                Map contentObj = (Map) firstCandidate.get("content");
                // 3. 提取 "parts" 列表
                List<Map> parts = (List<Map>) contentObj.get("parts");
                // 4. 返回第一段文本内容
                return (String) parts.get(0).get("text");
            }
        }
        throw new RuntimeException("API 返回异常");
    }
}