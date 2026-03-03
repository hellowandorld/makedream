package org.example.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

/*** 梦境解析核心控制器
 * 此时后端只负责接收文字并调用 AI 逻辑*/
@RestController
@RequestMapping("/api/dream")
@CrossOrigin(origins = "*")
public class DreamController {

    // 提示：Gemini API Key 申请地址 https://aistudio.google.com/
    private final String GEMINI_API_KEY = "AIzaSyBo6BUHQGC_0JBfQSHfHgx3h7K_Y_dGOWo";

    /*** 测试接口*/
    @GetMapping("/test")
    public String test() {
        return "后端已准备就绪，等待接收小程序识别出的文字。";
    }

    /*** 分析接口：接收小程序插件识别好的文字*/
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDream(@RequestBody Map<String, String> request) {
        String userDream = request.get("content"); // 这是插件转好的文字

        System.out.println("收到识别后的梦境文本: " + userDream);

        // 模拟 AI 响应
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("dream_analysis", "（AI 模拟回复）你的梦境中出现了：" + userDream + "。这通常预示着近期生活中的变动。");
        mockResponse.put("image_prompt", "A digital art piece representing " + userDream);

        return ResponseEntity.ok(mockResponse);
    }
}