package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.config.StorageConfig;
import it.gdr.diary.services.StorageBucketInitializer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StorageBucketInitializerTest {

    private static final String DIARY_BUCKET = "diary-documents";
    private static final String FEEDBACK_BUCKET = "feedback-documents";

    @Mock
    private MinioClient minioClient;

    private final StorageConfig storageConfig = new StorageConfig();
    private final FeedbackConfig feedbackConfig = new FeedbackConfig();

    private StorageBucketInitializer initializer;

    @BeforeEach
    void setUp() {
        storageConfig.setBucket(DIARY_BUCKET);
        feedbackConfig.setBucket(FEEDBACK_BUCKET);
        initializer = new StorageBucketInitializer(minioClient, storageConfig, feedbackConfig);
    }

    private void run() {
        initializer.run(new DefaultApplicationArguments());
    }

    private List<String> createdBuckets() throws Exception {
        ArgumentCaptor<MakeBucketArgs> args = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient, times(2)).makeBucket(args.capture());
        return args.getAllValues().stream().map(MakeBucketArgs::bucket).toList();
    }

    @Test
    void run_missingBuckets_createsDiaryAndFeedbackBuckets() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        run();

        assertThat(createdBuckets()).containsExactly(DIARY_BUCKET, FEEDBACK_BUCKET);
    }

    @Test
    void run_existingBuckets_createsNothing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        run();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void run_sameBucketForBoth_checksItOnce() throws Exception {
        feedbackConfig.setBucket(DIARY_BUCKET);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        run();

        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void run_createBucketsDisabled_doesNotTouchTheStorage() {
        storageConfig.setCreateBuckets(false);

        run();

        verifyNoInteractions(minioClient);
    }

    @Test
    void run_storageUnavailable_doesNotStopTheStartup() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new MinioException("connection refused"));

        assertThatCode(this::run).doesNotThrowAnyException();
    }

    @Test
    void run_storageUnavailable_stillTriesTheOtherBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new MinioException("connection refused"));

        run();

        verify(minioClient, times(2)).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void run_storageUnavailable_logsTheBucket(CapturedOutput output) throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new MinioException("connection refused"));

        run();

        assertThat(output.getAll()).contains("Cannot check or create the bucket " + FEEDBACK_BUCKET);
    }
}
