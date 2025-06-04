package com.example.bankcards.exception;

import com.example.bankcards.util.ErrorResponseUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ExceptionHandlerFilter extends OncePerRequestFilter {
    private final ErrorResponseUtil errorResponseUtil;
    public ExceptionHandlerFilter(ErrorResponseUtil errorResponseUtil) {
        this.errorResponseUtil = errorResponseUtil;
    }

	@Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException e) {
            errorResponseUtil.setErrorResponse(
                response,
                HttpStatus.UNAUTHORIZED,
                "Invalid JWT token"
            );
        } catch (ExpiredJwtException e) {
            errorResponseUtil.setErrorResponse(
                response,
                HttpStatus.UNAUTHORIZED,
                "JWT token has expired"
            );
        } catch (JwtException e) {
            errorResponseUtil.setErrorResponse(
                response,
                HttpStatus.UNAUTHORIZED,
                "JWT token error: " + e.getMessage()
            );
        }

    }
}
