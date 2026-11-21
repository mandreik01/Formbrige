package pl.formbridge.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.formbridge.app.Samples;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "formbridge.security.enabled=true",
        "formbridge.llm.enabled=false",
        "formbridge.jwt.secret=test-sekret-o-dlugosci-co-najmniej-32-bajtow",
        "formbridge.auth.client-id=coi",
        "formbridge.auth.client-secret=tajne-haslo"
})
class SecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("401 – żądanie bez tokenu, odpowiedź w formacie ProblemDetail")
    void rejectsRequestWithoutToken() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert")
                        .file(new MockMultipartFile("file", "wniosek.docx", null, Samples.formDocx())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("401 – token o niepoprawnym podpisie")
    void rejectsInvalidToken() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert")
                        .file(new MockMultipartFile("file", "wniosek.docx", null, Samples.formDocx()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer nie.jest.tokenem"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("401 – błędne dane klienta przy pobieraniu tokenu")
    void rejectsWrongCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"coi\",\"clientSecret\":\"zle\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("200 – token z /api/v1/auth/token otwiera dostęp do konwersji")
    void tokenGrantsAccess() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"coi\",\"clientSecret\":\"tajne-haslo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andReturn().getResponse().getContentAsString();

        Matcher m = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).isTrue();

        mvc.perform(multipart("/api/v1/forms/convert")
                        .file(new MockMultipartFile("file", "wniosek.docx", null, Samples.formDocx()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + m.group(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabs").isArray());
    }

    @Test
    @DisplayName("Otwarte bez tokenu: /actuator/health")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
