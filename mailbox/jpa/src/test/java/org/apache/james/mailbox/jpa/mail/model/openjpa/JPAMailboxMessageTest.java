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
package org.apache.james.mailbox.jpa.mail.model.openjpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class JPAMailboxMessageTest {

    private static final byte[] EMPTY = new byte[]{};

    /**
     * Even though there should never be a null body, it does happen. See JAMES-2384
     */
    @Test
    void getFullContentShouldReturnOriginalContentWhenBodyFieldIsNull() throws Exception {

        // Prepare the message
        byte[] content = "Subject: the null message".getBytes(StandardCharsets.UTF_8);
        JPAMailboxMessage message = new JPAMailboxMessage(content, null);

        // Get and check
        assertThat(IOUtils.toByteArray(message.getFullContent())).containsExactly(content);

    }

    @Test
    void getAnyMessagePartThatIsNullShouldYieldEmptyArray() throws Exception {

        // Prepare the message
        JPAMailboxMessage message = new JPAMailboxMessage(null, null);
        assertThat(IOUtils.toByteArray(message.getHeaderContent())).containsExactly(EMPTY);
        assertThat(IOUtils.toByteArray(message.getBodyContent())).containsExactly(EMPTY);
        assertThat(IOUtils.toByteArray(message.getFullContent())).containsExactly(EMPTY);
    }

}
