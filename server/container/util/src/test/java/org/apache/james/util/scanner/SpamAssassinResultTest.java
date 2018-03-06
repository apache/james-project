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
package org.apache.james.util.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class SpamAssassinResultTest {

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void buildShouldThrowWhenHitsIsNotGiven() {
        assertThatThrownBy(() -> SpamAssassinResult.asSpam()
                .requiredHits("4.0")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildShouldThrowWhenRequiredHitsIsNotGiven() {
        assertThatThrownBy(() -> SpamAssassinResult.asSpam()
                .hits("4.0")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildShouldWork() {
        String hits = "1.1";
        String requiredHits = "5.0";

        SpamAssassinResult spamAssassinResult = SpamAssassinResult.asSpam()
            .hits(hits)
            .requiredHits(requiredHits)
            .build();

        softly.assertThat(spamAssassinResult.getHits()).isEqualTo(hits);
        softly.assertThat(spamAssassinResult.getRequiredHits()).isEqualTo(requiredHits);
        softly.assertThat(spamAssassinResult.getHeadersAsAttribute())
            .containsAllEntriesOf(ImmutableMap.of(
                SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME, "YES",
                SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME, "Yes, hits=1.1 required=5.0"));
    }

    @Test
    public void headersAsAttributeShouldContainSpamHeaderWithYESValueWhenBuiltAsSpam() {
        String hits = "1.1";
        String requiredHits = "5.0";

        SpamAssassinResult spamAssassinResult = SpamAssassinResult.asSpam()
            .hits(hits)
            .requiredHits(requiredHits)
            .build();

        assertThat(spamAssassinResult.getHeadersAsAttribute())
            .containsEntry(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME, "YES");
    }

    @Test
    public void headersAsAttributeShouldContainSpamHeaderWithNOValueWhenBuiltAsHam() {
        String hits = "1.1";
        String requiredHits = "5.0";

        SpamAssassinResult spamAssassinResult = SpamAssassinResult.asHam()
            .hits(hits)
            .requiredHits(requiredHits)
            .build();

        assertThat(spamAssassinResult.getHeadersAsAttribute())
            .containsEntry(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME, "NO");
    }
}
