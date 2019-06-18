/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.modules.objectstorage.aws.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.junit.jupiter.api.Test;

class AwsS3ConfigurationReaderTest {

    @Test
    void fromShouldThrowWhenEndpointIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        assertThatThrownBy(() -> AwsS3ConfigurationReader.from(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'endpoint' is mandatory");
    }

    @Test
    void fromShouldThrowWhenAccessKeyIdIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty(AwsS3ConfigurationReader.OBJECTSTORAGE_ENDPOINT, "myEndpoint");
        assertThatThrownBy(() -> AwsS3ConfigurationReader.from(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'accessKeyId' is mandatory");
    }

    @Test
    void fromShouldThrowWhenSecretKeyIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty(AwsS3ConfigurationReader.OBJECTSTORAGE_ENDPOINT, "myEndpoint");
        configuration.addProperty(AwsS3ConfigurationReader.OBJECTSTORAGE_ACCESKEYID, "myAccessKeyId");
        assertThatThrownBy(() -> AwsS3ConfigurationReader.from(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("'secretKey' is mandatory");
    }

    @Test
    void fromShouldWork() {
        Configuration configuration = new PropertiesConfiguration();
        String endpoint = "myEndpoint";
        configuration.addProperty(AwsS3ConfigurationReader.OBJECTSTORAGE_ENDPOINT, endpoint);
        String accessKeyId = "myAccessKeyId";
        configuration.addProperty(AwsS3ConfigurationReader.OBJECTSTORAGE_ACCESKEYID, accessKeyId);
        String secretKey = "mySecretKey";
        configuration.addProperty(AwsS3ConfigurationReader.OBJECTSTORAGE_SECRETKEY, secretKey);

        AwsS3AuthConfiguration expected = AwsS3AuthConfiguration.builder()
            .endpoint(endpoint)
            .accessKeyId(accessKeyId)
            .secretKey(secretKey)
            .build();
        AwsS3AuthConfiguration authConfiguration = AwsS3ConfigurationReader.from(configuration);
        assertThat(authConfiguration).isEqualTo(expected);
    }
}