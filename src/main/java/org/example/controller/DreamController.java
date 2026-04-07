package org.example.controller;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.aiart.v20221229.AiartClient;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageRequest;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageResponse;
import org.example.mapper.DreamMapper;
import org.example.entity.DreamRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/dream")
@CrossOrigin(origins = "*")
@PropertySource("classpath:application-api.properties")
public class DreamController {

    @Autowired
    private DreamMapper dreamMapper;

    @Value("${tencent.cloud.secret-id}")
    private String tencentSecretId;

    @Value("${tencent.cloud.secret-key}")
    private String tencentSecretKey;

    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    private final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";

     //1、调用 DeepSeek 模型进行梦境结构化解析

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDream(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        Map<String, Object> response = new HashMap<>();

        if (deepseekApiKey == null || deepseekApiKey.isEmpty() ) {
            response.put("success", false);
            response.put("msg", "Deepseek 密钥未正确配置");
            return ResponseEntity.status(500).body(response);
        }

        try {
            System.out.println(">>> 收到解梦请求，正在调用DeepSeek");
            String aiResult = callDeepSeekAI(content);

            response.put("success", true);
            response.put("dreamAnalysis", aiResult); // 对应小程序 result.js 的解析逻辑

            System.out.println(">>> 解梦文本生成成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("msg", "解梦失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


     //2、调用腾讯云混元生图接口
     @PostMapping("/generate-image")
     public ResponseEntity<Map<String, Object>> generateDreamImage(@RequestBody Map<String, String> request) {
         String visualPrompt = request.get("content"); // 这里前端传过来的是 AI 建议的视觉提示词
         String originalDream = request.get("originalDream"); // 建议前端同时传回原始梦境描述
         String analysisJson = request.get("analysisJson");   // 建议前端传回之前生成的 JSON 解析结果

         Map<String, Object> response = new HashMap<>();

         try {
             Credential cred = new Credential(tencentSecretId, tencentSecretKey);
             AiartClient client = new AiartClient(cred, "ap-guangzhou");

             TextToImageRequest req = new TextToImageRequest();
             // 建议：如果报错敏感，尝试简化 Prompt
             req.setPrompt(visualPrompt);
             req.setRspImgType("url");
             req.setLogoAdd(0L);

             TextToImageResponse resp = client.TextToImage(req);
             String imageUrl = resp.getResultImage();

             //存入数据库
             DreamRecord record = new DreamRecord();
             record.setOriginalContent(originalDream);
             record.setTitle("我的梦境解析");
             record.setAnalysisJson(analysisJson);
             record.setImageUrl(imageUrl);
             dreamMapper.insert(record);

             response.put("success", true);
             response.put("imageUrl", imageUrl);
             return ResponseEntity.ok(response);

         } catch (TencentCloudSDKException e) {
             System.err.println("腾讯云报错码: " + e.getErrorCode());
             response.put("success", false);
             // 针对敏感词做特殊提示
             if (e.getErrorCode().contains("Sensitive")) {
                 response.put("msg", "梦境太深奥，画师看不懂敏感词，换个说法试试");
             } else {
                 response.put("msg", "生图暂时不可用: " + e.getErrorCode());
             }
             return ResponseEntity.ok(response); // 这里改回 ok，避免 502
         } catch (Exception e) {
             e.printStackTrace();
             response.put("success", false);
             response.put("msg", "系统繁忙");
             return ResponseEntity.ok(response);
         }
     }

    private String callDeepSeekAI(String dreamText) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey);

        // 1. 极其严格的 System Prompt，确保老师要求的字段全部包含
        String systemPrompt = "你是一位专业的心理学解梦大师。请根据用户梦境进行深度解析。\n" +
                "输出要求：严格按照 JSON 格式回复，严禁包含任何 Markdown 标签（如 ```json）或多余解释。\n" +
                "JSON 必须包含以下字段：\n" +
                "1. title: 梦境名称\n" +
                "2. abstract: 一句话摘要\n" +
                "3. elements: 字符串数组，提取 3-5 个核心象征元素\n" +
                "4. atmosphere: 描述梦境的情绪氛围（如：焦虑、宁静、神秘）\n" +
                "5. interpretation: 温和的心理学深度解读\n" +
                "6. advice: 给梦者的启发性建议\n" +
                "7. visual_prompt: 描述梦境画面的英文关键词，用于绘图 AI\n" +
                "8. disclaimer: 免责声明（如：本解析仅供参考，不作为医疗诊断建议）";

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "我的梦境内容是：" + dreamText);
        messages.add(userMsg);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "deepseek-chat");
        payload.put("messages", messages);
        payload.put("temperature", 0.7); // 稍微降低一点随机性，确保 JSON 结构的稳定性

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(DEEPSEEK_URL, entity, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("choices")) {
            List choices = (List) response.getBody().get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map messageObj = (Map) firstChoice.get("message");
            String content = (String) messageObj.get("content");

            // 2. 增加一层防御：自动去除 AI 可能误加的 Markdown 标签
            if (content != null) {
                content = content.replace("```json", "").replace("```", "").trim();
            }
            return content; //
        }

        return "{\"error\": \"大师今天有些劳累，未能感悟此梦。\"}"; // 返回 JSON 格式的错误信息
    }
}