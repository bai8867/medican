package com.campus.diet.security;

import com.campus.diet.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expireMs;

    public JwtService(
            @Value("${campus.jwt.secret}") String secret,
            @Value("${campus.jwt.expire-minutes:10080}") long expireMinutes) {
        String normalized = secret.length() >= 32 ? secret : (secret + "-campus-diet-jwt-pad-32b-").substring(0, 32);
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireMinutes * 60_000L;
    }

    public String createToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 过滤器内使用：非法或过期的 token 不抛异常，视为未登录。
     */
    public Optional<LoginUser> tryParse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            return Optional.of(new LoginUser(userId, username, role));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 业务侧需要强校验 token 时使用 */
    public LoginUser parseRequired(String token) {
        return tryParse(token).orElseThrow(() -> new BizException(401, "登录已失效，请重新登录"));
    }
}
