package it.liberheroum.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import it.liberheroum.config.AppUserConfig;
import it.liberheroum.exception.RecentAuthenticationRequiredException;
import it.liberheroum.model.Document;
import it.liberheroum.security.JwtInspector;
import it.liberheroum.security.RecentAuthentication;
import it.liberheroum.services.DocumentService;
import it.liberheroum.services.RichTextPdfRenderer;
import it.liberheroum.services.UserExport;
import it.liberheroum.services.UserExportService;
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
                new AppUserConfig(), documentService, JsonMapper.builder().build(), clock, new RichTextPdfRenderer());
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
    void writeUserExport_storedAndGeneratedFiles_writesEveryEntryInOrder() throws IOException {
        Document avatar = document("avatar.png");
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(invocation.<Document>getArgument(0).getName().getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(documentService).copyDocumentContent(any(Document.class), any(OutputStream.class));
        UserExport export = new UserExport("liber-heroum-2026-11-19.zip", List.of(
                new UserExport.GeneratedFile("LEGGIMI.txt", () -> "leggimi".getBytes(StandardCharsets.UTF_8)),
                new UserExport.StoredFile("profilo/avatar.png", avatar),
                new UserExport.GeneratedFile("pagamenti.csv", () -> "csv".getBytes(StandardCharsets.UTF_8))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.writeUserExport(export, out);

        Map<String, byte[]> entries = unzip(out.toByteArray());
        assertThat(entries.entrySet()).extracting(entry -> entry.getKey() + "=" + new String(entry.getValue(), StandardCharsets.UTF_8))
                .containsExactly("LEGGIMI.txt=leggimi", "profilo/avatar.png=avatar.png", "pagamenti.csv=csv");
    }
}
