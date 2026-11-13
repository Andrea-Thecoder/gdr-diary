package it.gdr.diary.services;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageBucketInitializer implements ApplicationRunner {

    private final MinioClient minioClient;
    private final StorageConfig storageConfig;
    private final FeedbackConfig feedbackConfig;

    @Override
    public void run(ApplicationArguments args) {
        if (!storageConfig.isCreateBuckets()) {
            return;
        }
        Stream.of(storageConfig.getBucket(), feedbackConfig.getBucket())
                .distinct()
                .forEach(this::createBucketIfMissing);
    }

    private void createBucketIfMissing(String bucket) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket {} created", bucket);
            }
        } catch (Exception e) {
            log.warn("Cannot check or create the bucket {}", bucket, e);
        }
    }
}
