package com.jizhi.videomid.config;

import com.jizhi.videomid.ptz.PtzWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PtzWebSocketHandler ptzWebSocketHandler;

    public WebSocketConfig(PtzWebSocketHandler ptzWebSocketHandler) {
        this.ptzWebSocketHandler = ptzWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 现场 edge-gateway 连接：ws://中台/ptz/gateway?id=小区编号
        registry.addHandler(ptzWebSocketHandler, "/ptz/gateway")
                .setAllowedOrigins("*");
    }
}
