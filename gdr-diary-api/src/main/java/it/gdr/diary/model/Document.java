package it.gdr.diary.model;

import io.ebean.annotation.Index;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Index(name = "ix_document_bucket_object_key", columnNames = {"bucket", "object_key"})
@Entity
@Table(name = "document")
public class Document extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    @Index
    private String name;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "bucket", nullable = false, length = 63)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;
}
