package com.example.codeassistant.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import jakarta.servlet.ServletException;

import java.io.IOException;

/**
 * After a successful GitHub login, Spring Security has already created the
 * session cookie. We redirect back to the Next.js dashboard rather than
 * returning a JSON blob, since this is a browser navigation, not an API call.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public OAuth2LoginSuccessHandler(@Value("${app.frontend-url}") String frontendUrl) {
        setDefaultTargetUrl(frontendUrl + "/dashboard");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
