package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import it.gdr.diary.config.AppUserConfig;
import it.gdr.diary.exception.RecentAuthenticationRequiredException;
import it.gdr.diary.model.Document;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.security.RecentAuthentication;
import it.gdr.diary.services.DocumentService;
import it.gdr.diary.services.UserExport;
import it.gdr.diary.services.UserExportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UserExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private DocumentService documentService;

    private UserExportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new UserExportService(database, jwtInspector, new RecentAuthentication(jwtInspector, clock),
                new AppUserConfig(), documentService, JsonMapper.builder().build(), clock);
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private static Document document(String name) {
        Document document = new Document();
        document.setName(name);
        return document;
    }

    @Test
    void getUserExport_loginOlderThanMaxAge_throwsRecentAuthenticationRequiredAndReadsNothing() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(6))));

        Throwable thrown = catchThrowable(() -> service.getUserExport());

        assertThat(thrown).isInstanceOf(RecentAuthenticationRequiredException.class);
        verifyNoInteractions(database, documentService);
    }

    @Test
    void writeUserExport_exportWithFiles_writesDataReadmeAndEveryFileInOrder() throws IOException {
        Document avatar = document("avatar.png");
        Document sheet = document("sheet.pdf");
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(invocation.<Document>getArgument(0).getName().getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(documentService).copyDocumentContent(any(Document.class), any(OutputStream.class));
        UserExport.Data data = new UserExport.Data("diario-gdr-export", 1, NOW,
                new UserExport.Account("alira@example.com", false, Map.of("theme", "dark"), "profilo/avatar.png"), List.of(), List.of());
        UserExport export = new UserExport("diario-gdr-2026-11-19.zip", data,
                List.of(new UserExport.File("profilo/avatar.png", avatar), new UserExport.File("personaggi/01-alira/schede/01-scheda.pdf", sheet)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.writeUserExport(export, out);

        Map<String, byte[]> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).containsExactly("dati.json", "LEGGIMI.txt", "profilo/avatar.png", "personaggi/01-alira/schede/01-scheda.pdf");
        assertThat(List.of(
                new String(entries.get("dati.json"), StandardCharsets.UTF_8).contains("\"email\" : \"alira@example.com\""),
                new String(entries.get("profilo/avatar.png"), StandardCharsets.UTF_8),
                new String(entries.get("personaggi/01-alira/schede/01-scheda.pdf"), StandardCharsets.UTF_8)))
                .containsExactly(true, "avatar.png", "sheet.pdf");
    }
}
