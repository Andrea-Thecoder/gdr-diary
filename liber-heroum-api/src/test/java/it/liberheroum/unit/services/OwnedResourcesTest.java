package it.liberheroum.unit.services;

import static it.liberheroum.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.security.JwtInspector;
import it.liberheroum.services.OwnedResources;
import it.liberheroum.unit.support.QueryStub;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OwnedResourcesTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;

    private OwnedResources resources;

    @BeforeEach
    void setUp() {
        resources = new OwnedResources(database, jwtInspector);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
    }

    static Stream<Arguments> lookups() {
        return Stream.of(
                Arguments.of(GameCharacter.class, "owner.keycloakId", "Character not found",
                        (BiFunction<OwnedResources, UUID, Object>) OwnedResources::getGameCharacterByIdAndOwner),
                Arguments.of(GameCharacter.class, "owner.keycloakId", "Character not found",
                        (BiFunction<OwnedResources, UUID, Object>) OwnedResources::getGameCharacterWithAvatarByIdAndOwner),
                Arguments.of(Diary.class, "character.owner.keycloakId", "Diary not found",
                        (BiFunction<OwnedResources, UUID, Object>) OwnedResources::getDiaryByIdAndOwner),
                Arguments.of(Diary.class, "character.owner.keycloakId", "Diary not found",
                        (BiFunction<OwnedResources, UUID, Object>) OwnedResources::getDiaryWithCoverByIdAndOwner),
                Arguments.of(DiaryPage.class, "diary.character.owner.keycloakId", "Page not found",
                        (BiFunction<OwnedResources, UUID, Object>) OwnedResources::getDiaryPageByIdAndOwner),
                Arguments.of(DiaryPage.class, "diary.character.owner.keycloakId", "Page not found",
                        (BiFunction<OwnedResources, UUID, Object>) OwnedResources::getDiaryPageReferenceByIdAndOwner));
    }

    @ParameterizedTest
    @MethodSource("lookups")
    <T> void lookup_resourceOfTheUser_returnsItFilteringByIdAndSubject(Class<T> type, String ownerPath, String notFound,
                                                                      BiFunction<OwnedResources, UUID, Object> lookup) throws Exception {
        QueryStub<T> query = stubFind(database, type);
        T owned = type.getDeclaredConstructor().newInstance();
        when(query.where().findOne()).thenReturn(owned);

        Object found = lookup.apply(resources, ID);

        assertThat(found).isSameAs(owned);
        verify(query.where()).idEq(ID);
        verify(query.where()).eq(ownerPath, SUBJECT);
    }

    @ParameterizedTest
    @MethodSource("lookups")
    <T> void lookup_missingOrOfAnotherUser_throwsNotFoundWithMessage(Class<T> type, String ownerPath, String notFound,
                                                                    BiFunction<OwnedResources, UUID, Object> lookup) {
        QueryStub<T> query = stubFind(database, type);
        when(query.where().findOne()).thenReturn(null);

        Throwable thrown = catchThrowable(() -> lookup.apply(resources, ID));

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(
                List.of(e.getStatusCode(), e.getReason())).containsExactly(HttpStatus.NOT_FOUND, notFound));
    }
}
