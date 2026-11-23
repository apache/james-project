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

package org.apache.james.mailbox.store;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.apache.james.mailbox.model.MimeDescriptor;
import org.junit.jupiter.api.Test;

class MimeDescriptorImplTest {
    @Test
    void buildShouldStopDescribingDeeplyNestedEmbeddedMessages() throws Exception {
        byte[] message = "Content-Type: message/rfc822\r\n\r\n".repeat(10_000).getBytes(US_ASCII);

        MimeDescriptor descriptor = MimeDescriptorImpl.build(new ByteArrayInputStream(message));

        int depth = 0;
        while (descriptor.embeddedMessage() != null) {
            descriptor = descriptor.embeddedMessage();
            depth++;
        }
        assertThat(depth).isEqualTo(64);
    }

    @Test
    void buildShouldCountMultipartLevelsInTheDepthOfEmbeddedMessages() throws Exception {
        StringBuilder message = new StringBuilder();
        for (int level = 0; level < 100; level++) {
            for (int multipart = 0; multipart < 30; multipart++) {
                String boundary = "b" + level + "x" + multipart;
                message.append("Content-Type: multipart/mixed; boundary=").append(boundary).append("\r\n\r\n")
                    .append("--").append(boundary).append("\r\n");
            }
            message.append("Content-Type: message/rfc822\r\n\r\n");
        }

        MimeDescriptor descriptor = MimeDescriptorImpl.build(new ByteArrayInputStream(message.toString().getBytes(US_ASCII)));

        // 64 levels, plus the multipart levels of the last embedded message
        assertThat(depthOf(descriptor)).isLessThanOrEqualTo(64 + 30 + 1);
    }

    private static int depthOf(MimeDescriptor descriptor) {
        int depth = 0;
        MimeDescriptor current = descriptor;
        while (current != null) {
            depth++;
            if (current.embeddedMessage() != null) {
                current = current.embeddedMessage();
            } else if (current.parts().hasNext()) {
                current = current.parts().next();
            } else {
                current = null;
            }
        }
        return depth;
    }
}
