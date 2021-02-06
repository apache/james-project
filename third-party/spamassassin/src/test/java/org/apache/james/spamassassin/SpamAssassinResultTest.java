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
package org.apache.james.spamassassin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public class SpamAssassinResultTest {
    @Test
    void buildShouldThrowWhenHitsIsNotGiven() {
        assertThatThrownBy(() -> SpamAssassinResult.asSpam()
                .requiredHits("4.0")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenRequiredHitsIsNotGiven() {
        assertThatThrownBy(() -> SpamAssassinResult.asSpam()
                .hits("4.0")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldWork() {
        String hits = "1.1";
        String requiredHits = "5.0";

        SpamAssassinResult spamAssassinResult = SpamAssassinResult.asSpam()
            .hits(hits)
            .requiredHits(requiredHits)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(spamAssassinResult.getHits()).isEqualTo(hits);
            softly.assertThat(spamAssassinResult.getRequiredHits()).isEqualTo(requiredHits);
            softly.assertThat(spamAssassinResult.getHeadersAsAttributes())
                .containsOnly(
                    new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("YES")),
                    new Attribute(SpamAssassinResult.STATUS_MAIL, AttributeValue.of("Yes, hits=1.1 required=5.0")));
        });
    }

    @Test
    void headersAsAttributeShouldContainSpamHeaderWithYESValueWhenBuiltAsSpam() {
        String hits = "1.1";
        String requiredHits = "5.0";

        SpamAssassinResult spamAssassinResult = SpamAssassinResult.asSpam()
            .hits(hits)
            .requiredHits(requiredHits)
            .build();

        assertThat(spamAssassinResult.getHeadersAsAttributes())
            .contains(new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("YES")));
    }

    @Test
    void headersAsAttributeShouldContainSpamHeaderWithNOValueWhenBuiltAsHam() {
        String hits = "1.1";
        String requiredHits = "5.0";

        SpamAssassinResult spamAssassinResult = SpamAssassinResult.asHam()
            .hits(hits)
            .requiredHits(requiredHits)
            .build();

        assertThat(spamAssassinResult.getHeadersAsAttributes())
            .contains(new Attribute(SpamAssassinResult.FLAG_MAIL, AttributeValue.of("NO")));
    }
}
