package com.jizhi.videomid.sip.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sip.header.AuthorizationHeader;
import javax.sip.message.Request;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** GB28181 REGISTER Digest 鉴权（MD5） */
@Component
@ConditionalOnProperty(name = "gb28181.data-source", havingValue = "live")
public class Gb28181DigestAuth {

    private static final long NONCE_TTL_MS = 3600_000L;

    private final ConcurrentHashMap<String, Long> nonces = new ConcurrentHashMap<>();

    public String issueNonce() {
        purgeExpired();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        nonces.put(nonce, System.currentTimeMillis());
        return nonce;
    }

    public boolean verify(Request request, AuthorizationHeader auth, String password) {
        if (auth == null || password == null) {
            return false;
        }
        String nonce = auth.getNonce();
        if (nonce == null || !nonces.containsKey(nonce)) {
            return false;
        }
        String username = auth.getUsername();
        String realm = auth.getRealm();
        String uri = auth.getURI() == null ? "" : auth.getURI().toString();
        String response = auth.getResponse();
        if (username == null || realm == null || response == null) {
            return false;
        }
        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(request.getMethod() + ":" + uri);
        String expected = md5(ha1 + ":" + nonce + ":" + ha2);
        return expected.equalsIgnoreCase(response);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        nonces.entrySet().removeIf(e -> now - e.getValue() > NONCE_TTL_MS);
    }

    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.ISO_8859_1));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
