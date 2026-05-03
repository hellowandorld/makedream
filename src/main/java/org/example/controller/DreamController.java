package org.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.aiart.v20221229.AiartClient;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageRequest;
import com.tencentcloudapi.aiart.v20221229.models.TextToImageResponse;
import org.example.mapper.DreamMapper;
import org.example.entity.DreamRecord;
import org.example.service.DreamEmotionService;
import org.example.controller.WeChatSecurityService;
import org.example.service.ThemeMatchService;
import org.example.utils.DictionaryCacheManager;
import org.example.utils.SensitiveWordEngine;
import org.example.utils.ElementExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private DreamEmotionService dreamEmotionService;

    @Autowired
    private ThemeMatchService themeMatchService;

    @Value("${tencent.cloud.secret-id}")
    private String tencentSecretId;

    @Value("${tencent.cloud.secret-key}")
    private String tencentSecretKey;

    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    private final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        if (sensitiveWordEngine.containsSensitiveWord(content)) {
            response.put("success", false);
            response.put("msg", "内容包含不当词汇，请文明交流。");
            return ResponseEntity.ok(response);
        }

        if (!weChatSecurityService.checkText(content, openid)) {
            response.put("success", false);
            response.put("msg", "内容未通过系统安全评估。");
            return ResponseEntity.ok(response);
        }

        try {
            Map<String, String> extractedElements = ElementExtractor.extract(content);
            StringBuilder elementsBuilder = new StringBuilder();
            extractedElements.forEach((category, value) -> {
                elementsBuilder.append(category).append(": ").append(value).append("; ");
            });
            String elementsStr = elementsBuilder.toString();

            String aiResult = callDeepSeekAI(content, elementsStr);

            if (aiResult.contains("\"error\"")) {
                response.put("success", false);
                response.put("msg", "大师陷入了沉思，请稍后再试");
            } else {
                response.put("success", true);
                response.put("dreamAnalysis", aiResult);

                List<Map<String, Object>> dictionary = DictionaryCacheManager.getDictionaryCache();
                Map<String, Integer> emotionScores = dreamEmotionService.calculateEmotionScores(content, dictionary);
                response.put("emotionScores", emotionScores);
                Map<String, Object> themeResult = themeMatchService.match(content, emotionScores);
                response.put("themeMatch", themeResult);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("msg", "系统繁忙，解梦服务稍后回来");
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/generate-image")
    public ResponseEntity<Map<String, Object>> generateDreamImage(@RequestBody Map<String, String> request) {
        String visualPrompt = request.get("content");
        String originalDream = request.get("originalDream");
        String analysisJson = request.get("analysisJson");
        String openid = request.get("openid");
        String emotionScoresJson = request.get("emotionScores");
        String themeMatchJson = request.get("themeMatch");

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

            TextToImageResponse resp = client.TextToImage(req);
            String imageUrl = resp.getResultImage();

            if (imageUrl == null || imageUrl.isEmpty()) {
                response.put("success", false);
                response.put("msg", "生成图片失败：腾讯云未返回有效链接");
                return ResponseEntity.ok(response);
            }

            DreamRecord record = new DreamRecord();
            record.setOriginalContent(originalDream);
            record.setAnalysisJson(analysisJson);
            record.setImageUrl(imageUrl);
            record.setOpenid(openid);
            record.setEmotionScores(emotionScoresJson);
            record.setThemeMatch(themeMatchJson);
            record.setCreateTime(java.time.LocalDateTime.now());

            try {
                String cleanJson = analysisJson.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1").trim();
                if (!cleanJson.startsWith("{")) {
                    cleanJson = cleanJson.substring(cleanJson.indexOf("{"));
                }

                JsonNode jsonNode = objectMapper.readTree(cleanJson);

                record.setTitle(jsonNode.path("title").asText("梦境纪实"));

                if (jsonNode.has("abstract") && !jsonNode.get("abstract").asText().isEmpty()) {
                    record.setAbstractText(jsonNode.get("abstract").asText());
                } else {
                    String interp = jsonNode.path("interpretation").asText("");
                    if (interp.isEmpty()) interp = jsonNode.path("analysis").asText("一段神秘的梦境...");
                    record.setAbstractText(interp.length() > 40 ? interp.substring(0, 40) + "..." : interp);
                }

                List<String> tags = new ArrayList<>();
                JsonNode elementsNode = jsonNode.path("elements");
                if (elementsNode.isArray()) {
                    elementsNode.forEach(n -> tags.add(n.asText()));
                }
                record.setElements(tags.isEmpty() ? "梦境意象" : String.join(",", tags));

            } catch (Exception e) {
                System.err.println("JSON 精细解析失败，启用最终保底方案: " + e.getMessage());
                record.setTitle("新梦境");
                record.setAbstractText(originalDream.length() > 30 ? originalDream.substring(0, 30) + "..." : originalDream);
                record.setElements("未分类");
            }

            int result = dreamMapper.insert(record);
            System.out.println(">>> 数据库插入结果: " + result + " | ID: " + record.getId() + " | Elements: " + record.getElements());

            response.put("success", true);
            response.put("imageUrl", imageUrl);
            return ResponseEntity.ok(response);

        } catch (TencentCloudSDKException e) {
            response.put("success", false);
            response.put("msg", e.getErrorCode().contains("Sensitive") ? "画面包含敏感因素" : "生图失败: " + e.getErrorCode());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("msg", "系统生成画面时遇到一点小麻烦");
            return ResponseEntity.ok(response);
        }
    }

    private String callDeepSeekAI(String dreamText, String elementsStr) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey);

        String systemPrompt =
                "你是一名“梦境叙事分析助手”，你的任务是基于用户描述的梦境与系统提取的特征要素，生成富有想象力、温和、启发性的梦境报告。你可以从情绪、象征、压力、关系、成长等角度进行分析。输出应具有神秘感和治愈感。\n" +
                "【重要要求】：\n" +
                "1. 严格以 JSON 格式输出，不要包含 Markdown 代码块标签。\n" +
                "2. interpretation: 深度心理学分析（200字左右）\n" +
                "3. 'elements' 字段必须是纯净的字符串数组，例如 [\"蛇\", \"森林\", \"追逐\"]。\n" +
                "4.必须包含 'abstract' 字段，字数控制在 20 字以内，作为梦境的精炼总结。\n "+
                "5. advice: 心理建设建议\n" +
                "6. 'visual_prompt' 必须是 3-5 个描述画面意境的英文关键词，用逗号分隔。\n\n" +
                "7. 请确保 'elements' 数组绝对不为空！\n"+
                "【输出格式】：\n" +
                "{\n" +
                "  \"title\": \"梦境标题\",\n" +
                "  \"abstract\": \"一句话摘要\",\n" +
                "  \"elements\": [\"要素1\", \"要素2\"],\n" +
                "  \"atmosphere\": \"氛围描述\",\n" +
                "  \"interpretation\": \"深度解析内容...\",\n" +
                "  \"advice\": \"给用户的建议\",\n" +
                "  \"visual_prompt\": \"keyword1, keyword2, keyword3\",\n" +
                "  \"disclaimer\": \"内容仅供娱乐参考。\"\n" +
                "}";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "我的梦境内容是：" + dreamText + "\n【提取到的要素】：\n" + elementsStr));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "deepseek-chat");
        payload.put("messages", messages);
        payload.put("temperature", 0.7);
        payload.put("response_format", Map.of("type", "json_object"));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(DEEPSEEK_URL, entity, Map.class);
            Map body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                String rawContent = (String) ((Map) ((Map) ((List) body.get("choices")).get(0)).get("message")).get("content");
                // 再次清洗标签
                return rawContent.replaceAll("```json|```", "").trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "{\"error\": \"AI响应失败\"}";
    }

    private String autoExtractKeywords(String originalContent) {
        try {
            Map<String, String> simpleMap = ElementExtractor.extract(originalContent);
            StringJoiner joiner = new StringJoiner("、");
            simpleMap.forEach((k, v) -> {
                if (v != null && !v.isEmpty() && !v.equals("无")) {
                    joiner.add(v);
                }
            });
            String result = joiner.toString();
            return result.isEmpty() ? "神秘意象" : result;
        } catch (Exception e) {
            return "梦境要素";
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistoryRecords(@RequestParam("openid") String openid) {
        if (openid == null || openid.isEmpty()) return ResponseEntity.badRequest().body("OpenID 不能为空");
        QueryWrapper<DreamRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("openid", openid).orderByDesc("create_time");
        return ResponseEntity.ok(dreamMapper.selectList(queryWrapper));
    }
}