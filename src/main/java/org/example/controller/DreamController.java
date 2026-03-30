package org.example.controller;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.aiart.v20221229.AiartClient;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageRequest;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/dream")
@CrossOrigin(origins = "*")
//读取指定API文件
@PropertySource("classpath:application-api.properties")
public class DreamController {

    // 读取腾讯云的 SecretId
    @Value("${tencent.cloud.secret-id}")
    private String tencentSecretId;

    // 读取腾讯云的 SecretKey
    @Value("${tencent.cloud.secret-key}")
    private String tencentSecretKey;

    // 读取 DeepSeek API Key
    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    private final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";

    //**接口：AI 解梦，接收用户描述，返回解析文本

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDream(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        Map<String, Object> response = new HashMap<>();

        // 安全检查：确保 API Key 已加载
        if (deepseekApiKey == null || deepseekApiKey.isEmpty() || deepseekApiKey.contains("xxxx")) {
            response.put("success", false);
            response.put("msg", "后端未检测到有效的 DeepSeek 密钥，请检查 application-api.properties");
            return ResponseEntity.status(500).body(response);
        }

        try {
            System.out.println("====== [开始解梦请求] ======");
            String aiResult = callDeepSeekAI(content);

            response.put("success", true);
            // 兼容性处理：同时返回下划线和驼峰命名，防止前端读取不到
            response.put("dream_analysis", aiResult);
            response.put("dreamAnalysis", aiResult);

            System.out.println("解梦完成");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("msg", "解梦失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    //**调用腾讯云混元大模型生成梦境图片（还没成功！！！）
    @PostMapping("/generate-image")
    public ResponseEntity<Map<String, Object>> generateDreamImage(@RequestBody Map<String, String> request) {
        String analysisText = request.get("content");
        Map<String, Object> response = new HashMap<>();

        try {
            System.out.println("====== [开始生图请求] ======");
            Credential cred = new Credential(tencentSecretId, tencentSecretKey);
            AiartClient client = new AiartClient(cred, "ap-guangzhou");

            TextToImageRequest req = new TextToImageRequest();

            // 构建 Prompt：加入艺术风格描述
            String cleanText = analysisText != null ? analysisText.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ") : "Dream";
            String prompt = "Surrealism art style, dreamlike scene, soft lighting, " +
                    (cleanText.length() > 80 ? cleanText.substring(0, 80) : cleanText);

            req.setPrompt(prompt);
            req.setRspImgType("url");
            req.setLogoAdd(0L); // 不添加腾讯云 Logo

            TextToImageResponse resp = client.TextToImage(req);

            response.put("success", true);
            response.put("imageUrl", resp.getResultImage());
            System.out.println("生图完成: " + resp.getResultImage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("msg", "生图失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    //调用 DeepSeek API
    private String callDeepSeekAI(String dreamText) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey);

        List<Map<String, String>> messages = new ArrayList<>();

        // 系统角色设定
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一位精通心理学的解梦大师，擅长结合弗洛伊德与荣格理论解析梦境。请用温暖、治愈且充满哲理的口吻回答。");
        messages.add(systemMsg);

        // 用户梦境内容
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "我的梦境是：" + dreamText);
        messages.add(userMsg);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "deepseek-chat");
        payload.put("messages", messages);
        payload.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        // 发起请求
        ResponseEntity<Map> response = restTemplate.postForEntity(DEEPSEEK_URL, entity, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("choices")) {
            List choices = (List) response.getBody().get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map messageObj = (Map) firstChoice.get("message");
            return (String) messageObj.get("content");
        }
        return "梦境过于晦涩难懂，大师暂时无法看透，请稍后再试。";
    }
}