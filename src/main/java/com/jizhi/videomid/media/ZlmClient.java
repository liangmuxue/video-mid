package com.jizhi.videomid.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.videomid.config.ZlmProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * ZLMediaKit HTTP API 客户端（框架层仅保留连通性探测，业务 API 后续再加）。
 */
@Component
public class ZlmClient {

    private final ZlmProperties props;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ZlmClient(ZlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** 调用 getServerConfig，用于验证能连上 ZLM。 */
    public JsonNode getServerConfig() {
        String base = props.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = UriComponentsBuilder
                .fromHttpUrl(base + "/index/api/getServerConfig")
                .queryParam("secret", props.getSecret())
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("ZLM unreachable: " + e.getMessage(), e);
        }
    }

    public boolean ping() {
        try {
            JsonNode resp = getServerConfig();
            return resp != null && resp.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
