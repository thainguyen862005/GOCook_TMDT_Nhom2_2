package com.james.LMS.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.util.PublicEndpointsValidatorUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;


@Component
@Slf4j
@RequiredArgsConstructor
public class LoggingFilter extends OncePerRequestFilter {
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

    try {
      long start = System.currentTimeMillis();
      String requestId = wrappedRequest.getHeader("x-request-id");
      MDC.put("requestId", requestId);
      wrappedResponse.setHeader("x-request-id", requestId);

      String path = wrappedRequest.getRequestURI();
      boolean isPublicEndPoints =
          PublicEndpointsValidatorUtil.isSwaggerUrl(path)
              || PublicEndpointsValidatorUtil.isPublicEndpoint(path);

      if (!isPublicEndPoints) {
        SecurityUserDetails principal =
            (SecurityUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email = principal.getUsername();
        MDC.put("email", email);
      }

      String requestBody = extractRequestBody(wrappedRequest);

      log.info(
          "Incoming Request: method={}, path={}, request={}",
          wrappedRequest.getMethod(),
          wrappedRequest.getRequestURI(),
          requestBody);

      filterChain.doFilter(wrappedRequest, wrappedResponse);

      long duration = System.currentTimeMillis() - start;
      String responseBody = extractResponseBody(wrappedResponse);

      log.info(
          "Outgoing Response: status={}, duration={}ms, response={}",
          wrappedResponse.getStatus(),
          duration,
          responseBody);
    } finally {
      wrappedResponse.copyBodyToResponse();
      MDC.clear();
    }
  }

  private String extractRequestBody(ContentCachingRequestWrapper request) {
    byte[] content = request.getContentAsByteArray();
    if (content.length == 0) {
      return "<empty>";
    }

    if (!isTextContent(request.getContentType())) {
      return "<non-text content>";
    }

    return formatBody(content);
  }

  private String extractResponseBody(ContentCachingResponseWrapper response) {
    byte[] content = response.getContentAsByteArray();
    if (content.length == 0) {
      return "<empty>";
    }

    if (!isTextContent(response.getContentType())) {
      return "<non-text content>";
    }

    return formatBody(content);
  }

  private boolean isTextContent(String contentType) {
    if (contentType == null) {
      return true;
    }

    return contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)
        || contentType.startsWith(MediaType.APPLICATION_XML_VALUE)
        || contentType.startsWith(MediaType.TEXT_PLAIN_VALUE)
        || contentType.startsWith(MediaType.TEXT_HTML_VALUE)
        || contentType.startsWith(MediaType.TEXT_XML_VALUE)
        || contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
  }

  private String formatBody(byte[] content) {
    String raw = new String(content, StandardCharsets.UTF_8);
    return raw;
  }
}
