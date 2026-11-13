package it.gdr.diary.services;

import io.ebean.Database;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import it.gdr.diary.config.StorageConfig;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int HEADER_SIZE = 16;
    private static final int MAX_NAME_LENGTH = 255;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<String> PDF_TYPES = Set.of("application/pdf");
    private static final Map<String, List<String>> EXTENSIONS = Map.of(
            "image/png", List.of("png"),
            "image/jpeg", List.of("jpg", "jpeg"),
            "image/webp", List.of("webp"),
            "application/pdf", List.of("pdf"));

    private final Database database;
    private final MinioClient minioClient;
    private final StorageConfig config;

    public Document createImageDocument(MultipartFile file) {
        return createDocument(file, IMAGE_TYPES, config.getMaxFileSize(), config.getBucket());
    }

    public Document createImageDocument(MultipartFile file, String bucket) {
        return createDocument(file, IMAGE_TYPES, config.getMaxFileSize(), bucket);
    }

    public Document createPdfDocument(MultipartFile file) {
        return createDocument(file, PDF_TYPES, config.getMaxPdfFileSize(), config.getBucket());
    }

    public void deleteDocumentRecord(Document document) {
        if (document != null) {
            database.delete(document);
        }
    }

    public void deleteDocumentFile(Document document) {
        if (document != null) {
            removeObject(document);
        }
    }

    private Document createDocument(MultipartFile file, Set<String> kindTypes, DataSize maxSize, String bucket) {
        if (file.isEmpty()) {
            throw new ServiceException("Empty file");
        }
        if (file.getSize() > maxSize.toBytes()) {
            throw new ServiceException("File too large");
        }
        String name = getFileName(file);
        String mimeType = detectMimeType(readHeader(file));
        if (mimeType == null || !kindTypes.contains(mimeType) || !config.getAllowedMimeTypes().contains(mimeType)) {
            throw new ServiceException("Unsupported file type");
        }
        String declaredType = StringUtils.lowerCase(StringUtils.substringBefore(StringUtils.defaultString(file.getContentType()), ";").trim());
        if (!declaredType.equals(mimeType)) {
            throw new ServiceException("Declared content type is missing or does not match the file content");
        }
        String extension = StringUtils.lowerCase(StringUtils.substringAfterLast(name, "."));
        if (!EXTENSIONS.getOrDefault(mimeType, List.of()).contains(extension)) {
            throw new ServiceException("File extension does not match the file content");
        }
        Document document = new Document();
        document.setName(truncateFileName(name));
        document.setSize(file.getSize());
        document.setMimeType(mimeType);
        document.setBucket(bucket);
        document.setObjectKey(UUID.randomUUID().toString());
        putObject(document, file);
        try {
            database.save(document);
        } catch (RuntimeException e) {
            removeObject(document);
            throw e;
        }
        return document;
    }

    public String getDocumentUrl(Document document) {
        return presignedUrl(document, Map.of());
    }

    public String getDocumentDownloadUrl(Document document) {
        if (document == null) {
            return null;
        }
        String encodedName = URLEncoder.encode(document.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return presignedUrl(document, Map.of("response-content-disposition", "attachment; filename*=UTF-8''" + encodedName));
    }

    private String presignedUrl(Document document, Map<String, String> queryParams) {
        if (document == null) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(document.getBucket())
                    .object(document.getObjectKey())
                    .expiry((int) config.getUrlExpiry().toSeconds())
                    .extraQueryParams(queryParams)
                    .build());
        } catch (Exception e) {
            log.error("Cannot generate the URL of document {}", document.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Storage not available");
        }
    }

    private String getFileName(MultipartFile file) {
        String original = StringUtils.defaultString(file.getOriginalFilename()).replace('\\', '/').replace("\0", "");
        return StringUtils.substringAfterLast("/" + original, "/");
    }

    private String truncateFileName(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        String extension = StringUtils.substringAfterLast(name, ".");
        String suffix = extension.isEmpty() || extension.length() >= MAX_NAME_LENGTH ? "" : "." + extension;
        return StringUtils.left(name, MAX_NAME_LENGTH - suffix.length()) + suffix;
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_SIZE);
        } catch (IOException e) {
            throw new ServiceException("Cannot read the file", e);
        }
    }

    private String detectMimeType(byte[] header) {
        if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (matches(header, 0, 'R', 'I', 'F', 'F') && matches(header, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        if (matches(header, 0, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        return null;
    }

    private boolean matches(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private void putObject(Document document, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(document.getBucket())
                    .object(document.getObjectKey())
                    .stream(in, file.getSize(), -1L)
                    .contentType(document.getMimeType())
                    .build());
        } catch (Exception e) {
            log.error("Cannot store the file of document {}", document.getObjectKey(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Storage not available");
        }
    }

    private void removeObject(Document document) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(document.getBucket())
                    .object(document.getObjectKey())
                    .build());
        } catch (Exception e) {
            log.warn("Cannot remove the orphan file {}", document.getObjectKey(), e);
        }
    }
}
