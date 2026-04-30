package org.example.controller;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.aiart.v20221229.AiartClient;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageRequest;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageResponse;
import org.example.mapper.DreamMapper;
import org.example.entity.DreamRecord;
import org.example.utils.SensitiveWordEngine;
import org.example.controller.WeChatSecurityService;
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

    @Autowired
    private WeChatSecurityService weChatSecurityService;

    @Autowired
    private SensitiveWordEngine sensitiveWordEngine;

    @Value("${tencent.cloud.secret-id}")
    private String tencentSecretId;

    @Value("${tencent.cloud.secret-key}")
    private String tencentSecretKey;

    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    private final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";

    /**
     * 1、调用 DeepSeek 模型进行梦境结构化解析
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDream(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String openid = request.get("openid");
        Map<String, Object> response = new HashMap<>();

        if (content == null || content.trim().length() < 2) {
            response.put("success", false);
            response.put("msg", "梦境描述太短啦，再多说一点吧");
            return ResponseEntity.ok(response);
        }

        // 本地敏感词检测
        if (sensitiveWordEngine.containsSensitiveWord(content)) {
            response.put("success", false);
            response.put("msg", "内容包含不当词汇，请文明交流。");
            return ResponseEntity.ok(response);
        }

        // 微信安全检测
        if (!weChatSecurityService.checkText(content, openid)) {
            response.put("success", false);
            response.put("msg", "内容未通过系统安全评估。");
            return ResponseEntity.ok(response);
        }

        try {
            System.out.println(">>> 正在请求 DeepSeek 解析梦境...");
            String aiResult = callDeepSeekAI(content);

            // 检查 AI 返回是否包含错误信息
            if (aiResult.contains("\"error\"")) {
                response.put("success", false);
                response.put("msg", "大师陷入了沉思，请稍后再试");
            } else {
                response.put("success", true);
                response.put("dreamAnalysis", aiResult);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("DeepSeek 调用异常: " + e.getMessage());
            response.put("success", false);
            response.put("msg", "系统繁忙，解梦服务稍后回来");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 2、调用腾讯云混元生图接口
     */
    @PostMapping("/generate-image")
    public ResponseEntity<Map<String, Object>> generateDreamImage(@RequestBody Map<String, String> request) {
        String visualPrompt = request.get("content");
        String originalDream = request.get("originalDream");
        String analysisJson = request.get("analysisJson");

        Map<String, Object> response = new HashMap<>();

        if (visualPrompt == null || visualPrompt.isEmpty()) {
            response.put("success", false);
            response.put("msg", "无法提取梦境画面关键词");
            return ResponseEntity.ok(response);
        }

        try {
            Credential cred = new Credential(tencentSecretId, tencentSecretKey);
            AiartClient client = new AiartClient(cred, "ap-guangzhou");

            TextToImageRequest req = new TextToImageRequest();
            req.setPrompt(visualPrompt);
            req.setRspImgType("url");
            req.setLogoAdd(0L);

            System.out.println(">>> 正在生成梦境画面，Prompt: " + visualPrompt);
            TextToImageResponse resp = client.TextToImage(req);

            String imageUrl = resp.getResultImage();

            // 关键修复：判断返回的图片链接是否为空
            if (imageUrl == null || imageUrl.isEmpty()) {
                response.put("success", false);
                response.put("msg", "生成图片失败：腾讯云未返回有效链接");
                return ResponseEntity.ok(response);
            }

            // 存入数据库
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
            System.err.println("腾讯云 API 异常: " + e.getErrorCode() + " - " + e.getMessage());
            response.put("success", false);
            if (e.getErrorCode().contains("Sensitive")) {
                response.put("msg", "梦境画面包含敏感因素，无法绘图");
            } else if (e.getErrorCode().contains("BalanceInsufficient")) {
                response.put("msg", "画室资源已耗尽，请联系管理员");
            } else {
                response.put("msg", "生图失败: " + e.getErrorCode());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("msg", "系统生成画面时遇到一点小麻烦");
            return ResponseEntity.ok(response);
        }
    }

    private String callDeepSeekAI(String dreamText) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey);

        String systemPrompt = "你是一位专业的心理学解梦大师。请根据用户梦境进行深度解析。\n" +
                "【重要要求】：\n" +
                "1. 严格以 JSON 格式输出，不包含 Markdown 代码块标签。\n" +
                "2. 'elements' 字段必须是纯净的字符串数组，例如 [\"蛇\", \"森林\", \"追逐\"]，严禁包含编号（如 1.xxx）或冒号解释。\n" +
                "3. 'visual_prompt' 必须是 3-5 个英文关键词，用逗号分隔。\n\n" +
                "【输出模板参考】：\n" +
                "{\n" +
                "  \"title\": \"迷雾中的追逐\",\n" +
                "  \"abstract\": \"一段关于自我探索与逃避的梦境。\",\n" +
                "  \"elements\": \n" +
                "  \"atmosphere\": \n" +
                "  \"interpretation\": \"这里的解析内容...\",\n" +
                "  \"advice\": \n" +
                "  \"visual_prompt\": \n" +
                "  \"disclaimer\": \"本建议仅供参考。\"\n" +
                "}";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "我的梦境内容是：" + dreamText));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "deepseek-chat");
        payload.put("messages", messages);
        payload.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(DEEPSEEK_URL, entity, Map.class);
            Map body = response.getBody();

            // 修复空指针的关键：层层判断
            if (body != null && body.containsKey("choices")) {
                List choices = (List) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map firstChoice = (Map) choices.get(0);
                    Map messageObj = (Map) firstChoice.get("message");
                    if (messageObj != null && messageObj.containsKey("content")) {
                        String content = (String) messageObj.get("content");
                        return content.replaceAll("```json|```", "").trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("请求 DeepSeek 出错: " + e.getMessage());
        }

        return "{\"error\": \"大师暂时无法看透此梦\"}";
    }
}