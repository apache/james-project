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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.objectstorage.AESPayloadCodec;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.modules.SwiftBlobStoreExtension;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.apache.james.modules.objectstorage.swift.DockerSwiftTestRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class WithEncryptedSwiftImmutableTest implements JmapJamesServerContract, JamesServerContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = CassandraRabbitMQJamesServerFixture.baseExtensionBuilder()
        .extension(new SwiftBlobStoreExtension(PayloadCodecFactory.AES256))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void encryptedPayloadShouldBeConfiguredWhenProvidingEncryptedPayloadConfiguration(GuiceJamesServer jamesServer) {
        PayloadCodec payloadCodec = jamesServer.getProbe(DockerSwiftTestRule.TestSwiftBlobStoreProbe.class)
            .getSwiftPayloadCodec();

        assertThat(payloadCodec)
            .isInstanceOf(AESPayloadCodec.class);
    }

}
