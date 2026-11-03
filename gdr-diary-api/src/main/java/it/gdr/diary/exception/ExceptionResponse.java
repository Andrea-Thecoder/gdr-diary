package it.gdr.diary.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExceptionResponse {

    String title;
    Integer status;
    List<Violation> violations;

    public ExceptionResponse(Integer status, String title, String message) {
        this.title = title;
        this.status = status;
        this.addViolation(message);
    }

    public void addViolation(String message) {
        if (violations == null) violations = new ArrayList<>();
        this.violations.add(new Violation(message));
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Violation {
        String field;
        String message;

        public Violation(String message) {
            this.message = message;
        }
    }
}
