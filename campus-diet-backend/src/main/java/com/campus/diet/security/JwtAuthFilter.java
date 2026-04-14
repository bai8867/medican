package com.campus.diet.security;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.ErrorCodes;
import com.campus.diet.entity.SysUser;
import com.campus.diet.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, SysUserMapper sysUserMapper, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    /** 允许携带过期身份访问的认证接口（禁用账号须能重新登录/注册，不在此设置 LoginUserHolder）。 */
    private static boolean isAuthWhitelist(String uri) {
        return uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/register");
    }

    private void writeJson(HttpServletResponse response, ApiResponse<Void> body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String uri = request.getRequestURI();
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                if (!token.isEmpty()) {
                    Optional<LoginUser> parsed = jwtService.tryParse(token);
                    if (parsed.isPresent()) {
                        LoginUser lu = parsed.get();
                        SysUser user = sysUserMapper.selectById(lu.getUserId());
                        if (user == null) {
                            if (!isAuthWhitelist(uri)) {
                                writeJson(response, ApiResponse.fail(401, "登录已失效，请重新登录"));
                                return;
                            }
                        } else if (user.getStatus() != null && user.getStatus() == 0) {
                            if (!isAuthWhitelist(uri)) {
                                writeJson(
                                        response,
                                        ApiResponse.fail(
                                                ErrorCodes.ACCOUNT_DISABLED, "账号已被禁用，请重新注册"));
                                return;
                            }
                        } else {
                            LoginUserHolder.set(lu);
                        }
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            LoginUserHolder.clear();
        }
    }
}
