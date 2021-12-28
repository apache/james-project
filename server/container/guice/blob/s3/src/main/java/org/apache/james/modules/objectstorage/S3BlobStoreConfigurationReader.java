/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.modules.objectstorage;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.modules.objectstorage.aws.s3.AwsS3ConfigurationReader;
import org.apache.james.util.DurationParser;

public class S3BlobStoreConfigurationReader {

    private static final String OBJECTSTORAGE_NAMESPACE = "objectstorage.namespace";
    private static final String OBJECTSTORAGE_BUCKET_PREFIX = "objectstorage.bucketPrefix";
    private static final String OBJECTSTORAGE_S3_REGION = "objectstorage.s3.region";
    private static final String OBJECTSTORAGE_S3_HTTP_CONCURRENCY = "objectstorage.s3.http.concurrency";
    private static final String OBJECTSTORAGE_S3_READ_TIMEOUT = "objectstorage.s3.read.timeout";
    private static final String OBJECTSTORAGE_S3_WRITE_TIMEOUT = "objectstorage.s3.write.timeout";
    private static final String OBJECTSTORAGE_S3_CONNECTION_TIMEOUT = "objectstorage.s3.connection.timeout";

    public static S3BlobStoreConfiguration from(Configuration configuration) throws ConfigurationException {
        Optional<Integer> httpConcurrency = Optional.ofNullable(configuration.getInteger(OBJECTSTORAGE_S3_HTTP_CONCURRENCY, null));
        Optional<String> namespace = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_NAMESPACE, null));
        Optional<String> bucketPrefix = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_BUCKET_PREFIX, null));
        Region region = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_S3_REGION, null))
            .map(Region::of)
            .orElseThrow(() -> new ConfigurationException("require a region (" + OBJECTSTORAGE_S3_REGION + " key)"));
        Optional<Duration> readTimeout = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_S3_READ_TIMEOUT, null))
            .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS));
        Optional<Duration> writeTimeout = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_S3_WRITE_TIMEOUT, null))
            .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS));
        Optional<Duration> connectionTimeout = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_S3_CONNECTION_TIMEOUT, null))
            .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS));

        return S3BlobStoreConfiguration.builder()
            .authConfiguration(AwsS3ConfigurationReader.from(configuration))
            .region(region)
            .defaultBucketName(namespace.map(BucketName::of))
            .bucketPrefix(bucketPrefix)
            .httpConcurrency(httpConcurrency)
            .readTimeout(readTimeout)
            .writeTimeout(writeTimeout)
            .connectionTimeout(connectionTimeout)
            .build();
    }

}
