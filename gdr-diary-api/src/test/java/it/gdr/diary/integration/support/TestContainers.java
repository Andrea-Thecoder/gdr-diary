package it.gdr.diary.integration.support;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.util.JsonSerialization;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestContainers {

    public static final String REALM = "gdr-diary-dev";
    public static final String FRONTEND_CLIENT = "gdr-diary-api";
    public static final String BUCKET = "diary-documents";
    public static final String STORAGE_ACCESS_KEY = "admin";
    public static final String STORAGE_SECRET_KEY = "secret";

    private static final Path REALM_FILE = Path.of("docker-compose/keycloak/gdr-diary-dev-realm.json");
    private static final Path PROVIDERS_DIR = Path.of("docker-compose/keycloak/providers");

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    public static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withEnv("AWS_ACCESS_KEY_ID", STORAGE_ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", STORAGE_SECRET_KEY)
            .withEnv("S3_BUCKET", BUCKET)
            .withExposedPorts(8333)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    public static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.7.0")
            .withFeaturesEnabled("update-email")
            .withProviderLibsFrom(providerJars());

    static {
        Startables.deepStart(POSTGRES, SEAWEEDFS, KEYCLOAK).join();
        importRealm();
        createBucket();
    }

    private TestContainers() {
    }

    public static String storageEndpoint() {
        return "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333);
    }

    public static String issuerUri() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    }

    private static List<File> providerJars() {
        try (var files = Files.list(PROVIDERS_DIR)) {
            return files.filter(file -> file.toString().endsWith(".jar")).map(Path::toFile).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the Keycloak providers in " + PROVIDERS_DIR, e);
        }
    }

    private static void importRealm() {
        try (InputStream in = Files.newInputStream(REALM_FILE)) {
            RealmRepresentation realm = JsonSerialization.readValue(in, RealmRepresentation.class);
            realm.getClients().stream()
                    .filter(client -> FRONTEND_CLIENT.equals(client.getClientId()))
                    .forEach(client -> client.setDirectAccessGrantsEnabled(true));
            KEYCLOAK.getKeycloakAdminClient().realms().create(realm);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the realm " + REALM_FILE, e);
        }
    }

    private static void createBucket() {
        MinioClient client = MinioClient.builder()
                .endpoint(storageEndpoint())
                .credentials(STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY)
                .region("us-east-1")
                .build();
        Exception last = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
                }
                return;
            } catch (Exception e) {
                last = e;
                sleepOneSecond();
            }
        }
        throw new IllegalStateException("The storage bucket " + BUCKET + " is not available", last);
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
