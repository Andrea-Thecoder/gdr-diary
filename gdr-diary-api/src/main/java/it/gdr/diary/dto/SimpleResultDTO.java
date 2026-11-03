package it.gdr.diary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Builder
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Schema(description = "Generic single-item response envelope used by all non-paginated endpoints")
public class SimpleResultDTO<T> {

    @Schema(description = "The response payload; null when the operation returns no content (e.g. delete)")
    private T payload;

    @Schema(description = "Optional human-readable message accompanying the response", examples = {"Ship successfully deleted"})
    private String message;
}
