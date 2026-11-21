package pl.formbridge.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.formbridge.app.Samples;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "formbridge.security.enabled=false",
        "formbridge.llm.enabled=false",
        "formbridge.upload.max-file-size=1KB"
})
class UploadLimitTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("413 – plik większy niż formbridge.upload.max-file-size")
    void rejectsTooLargeFile() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert")
                        .file(new MockMultipartFile("file", "wniosek.docx", null, Samples.formDocx())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }
}
