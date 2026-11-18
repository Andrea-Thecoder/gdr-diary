package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import it.gdr.diary.config.StorageConfig;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Document;
import it.gdr.diary.services.DocumentService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DocumentServiceTest {

    private static final String BUCKET = "diary-documents";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    @Mock
    private Database database;
    @Mock
    private MinioClient minioClient;

    private final StorageConfig config = new StorageConfig();

    private DocumentService service;

    @BeforeEach
    void setUp() {
        config.setBucket(BUCKET);
        service = new DocumentService(database, minioClient, config);
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private Document storedDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setBucket(BUCKET);
        document.setObjectKey("3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
        return document;
    }

    static Stream<Arguments> validImages() {
        return Stream.of(
                Arguments.of("avatar.png", "image/png", PNG, "image/png"),
                Arguments.of("avatar.jpg", "image/jpeg", JPEG, "image/jpeg"),
                Arguments.of("avatar.jpeg", "image/jpeg", JPEG, "image/jpeg"),
                Arguments.of("AVATAR.JPG", "image/jpeg", JPEG, "image/jpeg"),
                Arguments.of("avatar.webp", "image/webp", WEBP, "image/webp"),
                Arguments.of("avatar.png", "IMAGE/PNG; charset=binary", PNG, "image/png"),
                Arguments.of("A.PNG", "Image/PNG; charset=x", PNG, "image/png"));
    }

    @ParameterizedTest
    @MethodSource("validImages")
    void createImageDocument_validImage_storesDetectedMimeType(String name, String contentType, byte[] content, String expected) {
        MultipartFile file = file(name, contentType, content);

        Document document = service.createImageDocument(file);

        assertThat(document.getMimeType()).isEqualTo(expected);
    }

    @Test
    void createImageDocument_validPng_uploadsFileAndSavesDocument() throws Exception {
        MultipartFile file = file("avatar.png", "image/png", PNG);
        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);

        Document document = service.createImageDocument(file);

        assertThat(document).extracting(Document::getName, Document::getSize, Document::getBucket)
                .containsExactly("avatar.png", (long) PNG.length, BUCKET);
        assertThat(UUID.fromString(document.getObjectKey())).isNotNull();
        verify(minioClient).putObject(args.capture());
        assertThat(args.getValue()).extracting(PutObjectArgs::bucket, PutObjectArgs::object)
                .containsExactly(BUCKET, document.getObjectKey());
        verify(database).save(document);
    }

    @Test
    void createImageDocument_otherBucket_uploadsToThatBucketAndStoresItOnDocument() throws Exception {
        MultipartFile file = file("screenshot.png", "image/png", PNG);
        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);

        Document document = service.createImageDocument(file, "feedback-documents");

        verify(minioClient).putObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo("feedback-documents");
        assertThat(document.getBucket()).isEqualTo("feedback-documents");
    }

    @Test
    void createImageDocument_otherBucketWithPdf_throwsServiceException() {
        MultipartFile file = file("doc.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.createImageDocument(file, "feedback-documents")).isInstanceOf(ServiceException.class);
    }

    static Stream<Arguments> fileNames() {
        return Stream.of(
                Arguments.of("C:\\fakepath\\avatar.png", "avatar.png"),
                Arguments.of("../../etc/avatar.png", "avatar.png"),
                Arguments.of("ava\u0000tar.png", "avatar.png"),
                Arguments.of("a".repeat(251) + ".png", "a".repeat(251) + ".png"));
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void createImageDocument_fileNameWithPathOrNulOrAtLimit_storesCleanName(String original, String expected) {
        MultipartFile file = file(original, "image/png", PNG);

        Document document = service.createImageDocument(file);

        assertThat(document.getName()).isEqualTo(expected);
    }

    @Test
    void createImageDocument_fileNameLongerThan255_isAcceptedAndTruncatedKeepingExtension() {
        MultipartFile file = file("a".repeat(300) + ".png", "image/png", PNG);

        Document document = service.createImageDocument(file);

        assertThat(document).satisfies(
                saved -> assertThat(saved.getMimeType()).isEqualTo("image/png"),
                saved -> assertThat(saved.getName()).hasSize(255).endsWith("aaa.png"));
    }

    @Test
    void createImageDocument_longNameWithEmojiAcrossTheCut_storesNoLoneSurrogate() {
        MultipartFile file = file("a".repeat(250) + "\uD83D\uDE00.png", "image/png", PNG);

        Document document = service.createImageDocument(file);

        assertThat(new String(document.getName().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(document.getName());
    }

    static Stream<Arguments> invalidImages() {
        byte[] wave = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
        return Stream.of(
                Arguments.of(new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]), "Empty file"),
                Arguments.of(new MockMultipartFile("file", "sheet.pdf", "application/pdf", PDF), "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "avatar.png", "image/png", "GIF89a....".getBytes()), "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "avatar.png", "image/png", "<svg xmlns=".getBytes()), "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "avatar.png", "image/png", "MZ\u0090\u0000".getBytes()), "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[]{'R', 'I', 'F', 'F', 0x24, 0}),
                        "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "avatar.webp", "image/webp", wave), "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "avatar.png", null, PNG),
                        "Declared content type is missing or does not match the file content"),
                Arguments.of(new MockMultipartFile("file", "avatar.png", "image/jpeg", PNG),
                        "Declared content type is missing or does not match the file content"),
                Arguments.of(new MockMultipartFile("file", "avatar.jpg", "image/png", PNG),
                        "File extension does not match the file content"),
                Arguments.of(new MockMultipartFile("file", "avatar", "image/png", PNG),
                        "File extension does not match the file content"));
    }

    @ParameterizedTest
    @MethodSource("invalidImages")
    void createImageDocument_invalidFile_throwsServiceExceptionAndTouchesNoStorage(MultipartFile file, String message) {
        Throwable thrown = catchThrowable(() -> service.createImageDocument(file));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage(message);
        verifyNoInteractions(minioClient, database);
    }

    @Test
    void createImageDocument_overMaxSize_throwsServiceException() {
        config.setMaxFileSize(DataSize.ofBytes(4));
        MultipartFile file = file("avatar.png", "image/png", PNG);

        assertThatThrownBy(() -> service.createImageDocument(file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("File too large");
    }

    @Test
    void createImageDocument_exactlyAtMaxSize_isAccepted() {
        config.setMaxFileSize(DataSize.ofBytes(PNG.length));
        MultipartFile file = file("avatar.png", "image/png", PNG);

        Document document = service.createImageDocument(file);

        assertThat(document.getSize()).isEqualTo(PNG.length);
    }

    @Test
    void createImageDocument_typeNotAllowedByConfig_throwsUnsupportedFileType() {
        config.setAllowedMimeTypes(List.of("image/png"));
        MultipartFile file = file("avatar.webp", "image/webp", WEBP);

        assertThatThrownBy(() -> service.createImageDocument(file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Unsupported file type");
    }

    @Test
    void createImageDocument_imageLargerThanPdfLimit_isAccepted() {
        config.setMaxPdfFileSize(DataSize.ofBytes(4));
        MultipartFile file = file("avatar.png", "image/png", PNG);

        Document document = service.createImageDocument(file);

        assertThat(document.getMimeType()).isEqualTo("image/png");
    }

    @Test
    void createImageDocument_unreadableFile_throwsServiceException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("avatar.png");
        when(file.getInputStream()).thenThrow(new IOException("broken stream"));

        assertThatThrownBy(() -> service.createImageDocument(file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Cannot read the file");
    }

    @Test
    void createImageDocument_storageUnavailable_throwsInternalServerErrorWithoutSavingDocument() throws Exception {
        MultipartFile file = file("avatar.png", "image/png", PNG);
        when(minioClient.putObject(any())).thenThrow(new MinioException("connection refused"));

        Throwable thrown = catchThrowable(() -> service.createImageDocument(file));

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(database, never()).save(any());
    }

    @Test
    void createImageDocument_saveFails_removesUploadedObjectAndRethrows() throws Exception {
        MultipartFile file = file("avatar.png", "image/png", PNG);
        doThrow(new IllegalStateException("db down")).when(database).save(any(Document.class));
        ArgumentCaptor<RemoveObjectArgs> args = ArgumentCaptor.forClass(RemoveObjectArgs.class);

        Throwable thrown = catchThrowable(() -> service.createImageDocument(file));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(minioClient).removeObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
    }

    @Test
    void deleteDocumentRecord_document_deletesRow() {
        Document document = storedDocument();

        service.deleteDocumentRecord(document);

        verify(database).delete(document);
    }

    @Test
    void deleteDocument_null_doesNothing() {
        service.deleteDocumentRecord(null);
        service.deleteDocumentFile(null);

        verifyNoInteractions(database, minioClient);
    }

    @Test
    void deleteDocumentFile_document_removesObjectFromStorage() throws Exception {
        Document document = storedDocument();
        ArgumentCaptor<RemoveObjectArgs> args = ArgumentCaptor.forClass(RemoveObjectArgs.class);

        service.deleteDocumentFile(document);

        verify(minioClient).removeObject(args.capture());
        assertThat(args.getValue()).extracting(RemoveObjectArgs::bucket, RemoveObjectArgs::object)
                .containsExactly(BUCKET, document.getObjectKey());
    }

    @Test
    void deleteDocumentFile_storageFails_doesNotThrowAndLogsWarningWithObjectKey(CapturedOutput output) throws Exception {
        Document document = storedDocument();
        doThrow(new MinioException("connection refused")).when(minioClient).removeObject(any());

        Throwable thrown = catchThrowable(() -> service.deleteDocumentFile(document));

        assertThat(thrown).isNull();
        assertThat(output.getAll()).contains("Cannot remove the orphan file " + document.getObjectKey());
    }

    @Test
    void getDocumentUrl_null_returnsNull() {
        assertThat(service.getDocumentUrl(null)).isNull();
    }

    @Test
    void getDocumentDownloadUrl_null_returnsNull() {
        assertThat(service.getDocumentDownloadUrl(null)).isNull();
    }

    @Test
    void getDocumentUrl_document_returnsPresignedUrlWithConfiguredExpiryAndNoDispositionOverride() throws Exception {
        config.setUrlExpiry(Duration.ofMinutes(2));
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("https://storage.example/signed");
        ArgumentCaptor<GetPresignedObjectUrlArgs> args = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);

        String url = service.getDocumentUrl(storedDocument());

        verify(minioClient).getPresignedObjectUrl(args.capture());
        assertThat(url).isEqualTo("https://storage.example/signed");
        assertThat(args.getValue().expiry()).isEqualTo(120);
        assertThat(String.valueOf(args.getValue().extraQueryParams())).doesNotContain("response-content-disposition");
    }

    @Test
    void getDocumentDownloadUrl_document_returnsPresignedUrlWithConfiguredExpiryAndAttachmentDisposition() throws Exception {
        config.setUrlExpiry(Duration.ofMinutes(2));
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("https://storage.example/download");
        ArgumentCaptor<GetPresignedObjectUrlArgs> args = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);

        String url = service.getDocumentDownloadUrl(namedDocument("sheet.pdf"));

        verify(minioClient).getPresignedObjectUrl(args.capture());
        assertThat(url).isEqualTo("https://storage.example/download");
        assertThat(args.getValue().expiry()).isEqualTo(120);
        assertThat(String.valueOf(args.getValue().extraQueryParams()))
                .contains("response-content-disposition", "attachment; filename*=UTF-8''sheet.pdf");
    }

    @Test
    void getDocumentDownloadUrl_nameWithSpacesAndAccents_encodesFileName() throws Exception {
        ArgumentCaptor<GetPresignedObjectUrlArgs> args = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);

        service.getDocumentDownloadUrl(namedDocument("Scheda di Alira \u00e8.pdf"));

        verify(minioClient).getPresignedObjectUrl(args.capture());
        assertThat(String.valueOf(args.getValue().extraQueryParams())).contains("filename*=UTF-8''Scheda%20di%20Alira%20%C3%A8.pdf");
    }

    @Test
    void getDocumentDownloadUrl_nameWithQuotesAndLineBreaks_putsNoRawControlCharactersInHeader() throws Exception {
        ArgumentCaptor<GetPresignedObjectUrlArgs> args = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);

        service.getDocumentDownloadUrl(namedDocument("evil\".pdf\r\nX-Injected: yes"));

        verify(minioClient).getPresignedObjectUrl(args.capture());
        assertThat(String.valueOf(args.getValue().extraQueryParams())).doesNotContain("\r", "\n", "\"");
    }

    static Stream<Arguments> urlMethods() {
        BiFunction<DocumentService, Document, String> url = DocumentService::getDocumentUrl;
        BiFunction<DocumentService, Document, String> downloadUrl = DocumentService::getDocumentDownloadUrl;
        return Stream.of(Arguments.of(url), Arguments.of(downloadUrl));
    }

    @ParameterizedTest
    @MethodSource("urlMethods")
    void getDocumentUrl_storageFails_throwsInternalServerError(BiFunction<DocumentService, Document, String> urlMethod) throws Exception {
        when(minioClient.getPresignedObjectUrl(any())).thenThrow(new MinioException("connection refused"));
        Document document = namedDocument("sheet.pdf");

        assertThatThrownBy(() -> urlMethod.apply(service, document))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private Document namedDocument(String name) {
        Document document = storedDocument();
        document.setName(name);
        return document;
    }

    @ParameterizedTest
    @ValueSource(strings = {"sheet.pdf", "SHEET.PDF"})
    void createPdfDocument_validPdf_storesMetadataUploadsFileAndSavesDocument(String name) throws Exception {
        MultipartFile file = file(name, "application/pdf", PDF);
        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);

        Document document = service.createPdfDocument(file);

        assertThat(document).extracting(Document::getName, Document::getMimeType, Document::getSize, Document::getBucket)
                .containsExactly(name, "application/pdf", (long) PDF.length, BUCKET);
        verify(minioClient).putObject(args.capture());
        assertThat(args.getValue()).extracting(PutObjectArgs::bucket, PutObjectArgs::object)
                .containsExactly(BUCKET, document.getObjectKey());
        verify(database).save(document);
    }

    static Stream<Arguments> invalidPdfs() {
        return Stream.of(
                Arguments.of(new MockMultipartFile("file", "sheet.pdf", "application/pdf", new byte[0]), "Empty file"),
                Arguments.of(new MockMultipartFile("file", "avatar.png", "image/png", PNG), "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "sheet.pdf", "application/pdf", "just some text".getBytes()),
                        "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "sheet.pdf", "application/pdf", "<html></html>".getBytes()),
                        "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "sheet.pdf", "application/pdf", "PK\u0003\u0004".getBytes()),
                        "Unsupported file type"),
                Arguments.of(new MockMultipartFile("file", "sheet.pdf", "application/octet-stream", PDF),
                        "Declared content type is missing or does not match the file content"),
                Arguments.of(new MockMultipartFile("file", "sheet.png", "application/pdf", PDF),
                        "File extension does not match the file content"));
    }

    @ParameterizedTest
    @MethodSource("invalidPdfs")
    void createPdfDocument_invalidFile_throwsServiceExceptionAndTouchesNoStorage(MultipartFile file, String message) {
        Throwable thrown = catchThrowable(() -> service.createPdfDocument(file));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage(message);
        verifyNoInteractions(minioClient, database);
    }

    @Test
    void createPdfDocument_overMaxPdfSize_throwsServiceException() {
        config.setMaxPdfFileSize(DataSize.ofBytes(PDF.length - 1));
        MultipartFile file = file("sheet.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.createPdfDocument(file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("File too large");
    }

    @Test
    void createPdfDocument_exactlyAtMaxPdfSize_isAccepted() {
        config.setMaxPdfFileSize(DataSize.ofBytes(PDF.length));
        MultipartFile file = file("sheet.pdf", "application/pdf", PDF);

        Document document = service.createPdfDocument(file);

        assertThat(document.getSize()).isEqualTo(PDF.length);
    }

    @Test
    void createPdfDocument_pdfLargerThanImageLimit_isAccepted() {
        config.setMaxFileSize(DataSize.ofBytes(4));
        MultipartFile file = file("sheet.pdf", "application/pdf", PDF);

        Document document = service.createPdfDocument(file);

        assertThat(document.getMimeType()).isEqualTo("application/pdf");
    }

    @Test
    void createPdfDocument_typeNotAllowedByConfig_throwsUnsupportedFileType() {
        config.setAllowedMimeTypes(List.of("image/png"));
        MultipartFile file = file("sheet.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.createPdfDocument(file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Unsupported file type");
    }
}
