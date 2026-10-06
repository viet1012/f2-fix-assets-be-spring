package com.spc.fixedasset.auth;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** @Import into a @WebMvcTest so every request carries a logged-in session (account ACCOUNT, name NAME). */
@TestConfiguration
public class LoggedInMockMvc {

    public static final String ACCOUNT = "E001";
    public static final String NAME = "Nguyễn Văn A";

    @Bean
    MockMvcBuilderCustomizer loggedInSession() {
        return builder -> builder.defaultRequest(get("/").sessionAttr(AuthSession.ACCOUNT, ACCOUNT)
                .sessionAttr(AuthSession.USER, new CurrentUser(ACCOUNT, NAME, null, null)));
    }
}
