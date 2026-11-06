package com.smarthome.smart_home_back.config;

import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.service.SesionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class AutorizacionConfig extends OncePerRequestFilter {
    public static final String USER_ATTRIBUTE = "usuarioAutenticado";
    private static final Set<String> FRONTENDS = Set.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "https://smarthome.aeropuertolosprimos.me",
            "https://prueba.aeropuertolosprimos.me");

    private final SesionService sesionService;

    public AutorizacionConfig(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return request.getMethod().equalsIgnoreCase("OPTIONS")
                || !path.startsWith(request.getContextPath() + "/api/")
                || path.equals(request.getContextPath() + "/api/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = sesionService.extraerToken(request.getHeader("Authorization"));
        Usuario usuario = sesionService.validarYRenovar(token).orElse(null);
        if (usuario == null) {
            addCorsHeaders(request, response);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"mensaje\":\"Sesión ausente, inválida o vencida\"}");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, usuario);
        filterChain.doFilter(request, response);
    }

    private static void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && FRONTENDS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Headers", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Vary", "Origin");
        }
    }
}
