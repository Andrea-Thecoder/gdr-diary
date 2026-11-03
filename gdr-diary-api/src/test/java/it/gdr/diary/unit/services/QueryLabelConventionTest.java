package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class QueryLabelConventionTest {

    private static final Path SERVICES = Path.of("src/main/java/it/gdr/diary/services");
    private static final Pattern FIND = Pattern.compile("database\\.find\\([^)]*\\)");
    private static final Pattern LABEL = Pattern.compile("\\A\\s*\\.setLabel\\(\"[^\"]+\"\\)");

    private record Occurrence(Path file, String source, MatchResult match) {

        boolean labelled() {
            return LABEL.matcher(source.substring(match.end())).find();
        }

        String location() {
            long line = source.substring(0, match.start()).lines().count();
            return file.getFileName() + ":" + line;
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> serviceFiles() {
        try (Stream<Path> files = Files.list(SERVICES)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Occurrence> findCalls() {
        return serviceFiles().stream()
                .flatMap(file -> {
                    String source = read(file);
                    return FIND.matcher(source).results().map(match -> new Occurrence(file, source, match));
                })
                .toList();
    }

    @Test
    void services_anySource_containDatabaseFindCalls() {
        List<Occurrence> calls = findCalls();

        assertThat(calls).isNotEmpty();
    }

    @Test
    void services_everyDatabaseFind_isFollowedBySetLabel() {
        List<String> unlabelled = findCalls().stream()
                .filter(occurrence -> !occurrence.labelled())
                .map(Occurrence::location)
                .toList();

        assertThat(unlabelled).isEmpty();
    }
}
