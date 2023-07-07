package org.apache.james.blob.objectstorage.aws;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.TestBlobId;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class S3HealthCheckTest {

    private S3HealthCheck s3HealthCheck;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3) {
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
                .endpoint(dockerAwsS3.getEndpoint())
                .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
                .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
                .build();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
                .authConfiguration(authConfiguration)
                .region(dockerAwsS3.dockerAwsS3().region())
                .build();

        S3BlobStoreDAO s3BlobStoreDAO = new S3BlobStoreDAO(s3Configuration, new TestBlobId.Factory());
        s3HealthCheck = new S3HealthCheck(s3BlobStoreDAO);
    }

    @AfterEach
    void reset(DockerAwsS3Container dockerAwsS3) {
        if (dockerAwsS3.isPaused()) {
            dockerAwsS3.unpause();
        }
    }

    @Test
    void checkShouldReturnHealthyWhenS3IsRunning() {
        Result check = s3HealthCheck.check().block();
        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenS3IsNotRunning(DockerAwsS3Container dockerAwsS3) {
        dockerAwsS3.pause();
        Result check = s3HealthCheck.check().block();
        assertThat(check.isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldDetectWhenCassandraRecovered(DockerAwsS3Container dockerAwsS3) {
        dockerAwsS3.pause();
        dockerAwsS3.unpause();
        Result check = s3HealthCheck.check().block();
        assertThat(check.isHealthy()).isTrue();
    }
}
