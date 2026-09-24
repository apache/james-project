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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.junit.jupiter.api.Test;

class S3BlobStoreConfigurationReaderTest {

    private Configuration baseConfiguration() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstorage.s3.endPoint", "http://myEndpoint");
        configuration.addProperty("objectstorage.s3.accessKeyId", "myAccessKeyId");
        configuration.addProperty("objectstorage.s3.secretKey", "mySecretKey");
        configuration.addProperty("objectstorage.namespace", "myNamespace");
        configuration.addProperty("objectstorage.s3.region", "us-east-1");
        return configuration;
    }

    @Test
    void shouldDefaultIfNoneMatchToTrue() throws Exception {
        Configuration configuration = baseConfiguration();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfigurationReader.from(configuration);

        assertThat(s3Configuration.ifNoneMatchEnabled()).isTrue();
    }

    @Test
    void shouldRespectIfNoneMatchExplicitlyDisabled() throws Exception {
        Configuration configuration = baseConfiguration();
        configuration.addProperty("objectstorage.s3.ifNoneMatch.enable", false);

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfigurationReader.from(configuration);

        assertThat(s3Configuration.ifNoneMatchEnabled()).isFalse();
    }
}
