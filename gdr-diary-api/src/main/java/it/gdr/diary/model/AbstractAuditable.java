package it.gdr.diary.model;

import java.time.Instant;

import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import io.ebean.annotation.WhoCreated;
import io.ebean.annotation.WhoModified;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@MappedSuperclass
public abstract class AbstractAuditable {

    @WhoCreated
    private String createdBy;

    @WhoModified
    private String updatedBy;

    @WhenCreated
    private Instant createdAt;

    @WhenModified
    private Instant updatedAt;

    @Version
    private Long version;
}
