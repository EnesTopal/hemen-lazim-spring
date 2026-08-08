package com.tpl.hemen_lazim.config.ws;

import com.tpl.hemen_lazim.security.JwtGenerate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "hemen.chat.user-id";
    private final JwtGenerate jwtGenerate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtGenerate.validateToken(token)) {
            return false;
        }
        UUID userId = jwtGenerate.getUserIdFromToken(token);
        attributes.put(USER_ID_ATTR, userId.toString());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> headers = request.getHeaders().get("Authorization");
        if (headers != null) {
            for (String header : headers) {
                if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                    return header.substring(7);
                }
            }
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String paramToken = servletRequest.getServletRequest().getParameter("token");
            if (StringUtils.hasText(paramToken)) {
                return paramToken;
            }
        }
        return null;
    }
}

