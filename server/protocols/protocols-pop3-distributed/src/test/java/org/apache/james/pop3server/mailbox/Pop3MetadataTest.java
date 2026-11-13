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

package org.apache.james.pop3server.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore.FullMetadata;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore.StatMetadata;
import org.junit.jupiter.api.Test;

class Pop3MetadataTest {
    @Test
    void statMetadataShouldNotEqualFullMetadataWithTheSameMessage() {
        StatMetadata stat = new StatMetadata(InMemoryMessageId.of(42), 12);
        FullMetadata full = new FullMetadata(InMemoryId.of(36), stat);

        assertThat(stat).isNotEqualTo(full);
        assertThat(full).isNotEqualTo(stat);
    }

    @Test
    void statMetadataShouldEqualStatMetadataWithTheSameValues() {
        assertThat(new StatMetadata(InMemoryMessageId.of(42), 12))
            .isEqualTo(new StatMetadata(InMemoryMessageId.of(42), 12));
    }
}
