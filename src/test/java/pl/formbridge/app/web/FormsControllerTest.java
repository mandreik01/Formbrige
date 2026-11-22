package pl.formbridge.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import pl.formbridge.app.Samples;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "formbridge.security.enabled=false",
        "formbridge.llm.enabled=false",
        "formbridge.batch.max-files=3"
})
class FormsControllerTest {

    @Autowired
    private MockMvc mvc;

    private static MockMultipartFile file(String field, String name, byte[] content) {
        return new MockMultipartFile(field, name, null, content);
    }

    @Test
    @DisplayName("200 – poprawny formularz zwraca JSON i nagłówek z czasem przetwarzania")
    void convertsValidForm() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "wniosek-o-zaswiadczenie.docx", Samples.formDocx())))
                .andExpect(status().isOk())
                .andExpect(header().exists(FormsController.HEADER_PROCESSING_MS))
                .andExpect(jsonPath("$.tabs").isArray()).andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    @DisplayName("Arkusz XLSX trafia do konwertera jako wiele stron")
    void convertsSpreadsheet() throws Exception {
        byte[] xlsx = Samples.xlsx(new String[]{"Wniosek o wydanie zaswiadczenia", "Zalaczniki"});

        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "wniosek.xlsx", xlsx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabs").isArray());
    }

    @Test
    @DisplayName("400 – brak pliku w żądaniu")
    void missingFileIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("400 – pusty plik")
    void emptyFileIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "wniosek.pdf", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 – nazwa pliku z zabronionym znakiem")
    void forbiddenCharacterIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "wnio;sek.docx", Samples.formDocx())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("415 – nieobsługiwane rozszerzenie")
    void unsupportedExtension() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "wniosek.txt", Samples.formDocx())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("415 – MIME niezgodny z rozszerzeniem")
    void mismatchedMediaType() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "wniosek.pdf", Samples.png())))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("422 – plik bez treści formularza")
    void emptyContentIsUnprocessable() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert").file(file("file", "pusty.pdf", Samples.emptyPdf())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("Wsad – każdy plik rozliczany niezależnie")
    void batchReportsPerFile() throws Exception {
        mvc.perform(multipart("/api/v1/forms/convert-batch")
                        .file(file("files", "wniosek.docx", Samples.formDocx()))
                        .file(file("files", "zly.txt", Samples.png())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value(200))
                .andExpect(jsonPath("$[0].form").exists())
                .andExpect(jsonPath("$[0].form.tabs").isArray())
                .andExpect(jsonPath("$[1].status").value(415))
                .andExpect(jsonPath("$[1].error.status").value(415));
    }

    @Test
    @DisplayName("400 – przekroczony limit liczby plików we wsadzie")
    void batchLimitExceeded() throws Exception {
        byte[] docx = Samples.formDocx();
        mvc.perform(multipart("/api/v1/forms/convert-batch")
                        .file(file("files", "a.docx", docx))
                        .file(file("files", "b.docx", docx))
                        .file(file("files", "c.docx", docx))
                        .file(file("files", "d.docx", docx)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tryb demo – /api/v1/auth/token nie wydaje tokenu")
    void tokenUnavailableInDemoMode() throws Exception {
        mvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"a\",\"clientSecret\":\"b\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
    }
}
