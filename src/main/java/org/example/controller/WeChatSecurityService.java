package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
public class WeChatSecurityService {

    @Value("${wechat.app-id}")
    private String appId;

    @Value("${wechat.app-secret}")
    private String appSecret;

    private String accessToken = "";
    private long expiryTime = 0;

    private String getAccessToken() {
        if (System.currentTimeMillis() < expiryTime && !accessToken.isEmpty()) {
            return accessToken;
        }

        // 修正后的纯净 URL，不要加任何方括号或括号
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appId + "&secret=" + appSecret;

        RestTemplate restTemplate = new RestTemplate();
        try {
            Map<String, Object> res = restTemplate.getForObject(url, Map.class);
            if (res != null && res.containsKey("access_token")) {
                this.accessToken = (String) res.get("access_token");
                this.expiryTime = System.currentTimeMillis() + (7000 * 1000);
                return accessToken;
            }
        } catch (Exception e) {
            System.err.println("获取微信 AccessToken 失败: " + e.getMessage());
        }
        return null;
    }

    public boolean checkText(String content, String openid) {
        String token = getAccessToken();
        if (token == null) return true;

        String url = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=" + token;

        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("version", 2);
        payload.put("scene", 1);
        payload.put("openid", openid != null ? openid : "system_admin");

        RestTemplate restTemplate = new RestTemplate();
        try {
            Map<String, Object> response = restTemplate.postForObject(url, payload, Map.class);
            if (response != null && response.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                String suggest = (String) result.get("suggest");
                return "pass".equals(suggest);
            }
        } catch (Exception e) {
            System.err.println("微信内容审核异常: " + e.getMessage());
        }
        return true;
    }
}