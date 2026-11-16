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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.junit.jupiter.api.Test;

class MailboxExpressionBackwardCompatibilityTest {
    @Test
    void getPathLikeShouldReplaceWildcards() {
        MailboxQuery query = MailboxQuery.builder()
            .expression(new PrefixedRegex("", "a%b*", '.'))
            .build();

        assertThat(MailboxExpressionBackwardCompatibility.getPathLike(query)).isEqualTo("a%b%%");
    }

    @Test
    void getPathLikeShouldIgnoreTheCaseOfInbox() {
        MailboxQuery query = MailboxQuery.builder()
            .expression(new PrefixedRegex("", "inbox.sub*", '.'))
            .build();

        assertThat(MailboxExpressionBackwardCompatibility.getPathLike(query)).isEqualTo("_____.sub%%");
    }

    @Test
    void getPathLikeShouldKeepUppercaseInbox() {
        MailboxQuery query = MailboxQuery.builder()
            .expression(new PrefixedRegex("", "INBOX", '.'))
            .build();

        assertThat(MailboxExpressionBackwardCompatibility.getPathLike(query)).isEqualTo("INBOX%");
    }
}
