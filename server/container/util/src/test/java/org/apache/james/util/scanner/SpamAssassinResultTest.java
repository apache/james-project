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

import org.assertj.core.data.MapEntry;
import org.junit.Test;

public class SpamAssassinResultTest {

    @Test
    public void buildShouldThrowWhenHitsIsNotGiven() {
        assertThatThrownBy(() -> SpamAssassinResult.builder()
                .build())
            .isInstanceOf(NullPointerException.class);
        
    }

    @Test
    public void buildShouldThrowWhenRequiredHitsIsNotGiven() {
        assertThatThrownBy(() -> SpamAssassinResult.builder()
                .hits("1.0")
                .build())
            .isInstanceOf(NullPointerException.class);
        
    }

    @Test
    public void buildShouldWork() {
        String hits = "1.1";
        String requiredHits = "5.0";
        String name = "header";
        String value = "value";
        String name2 = "header2";
        String value2 = "value2";
        SpamAssassinResult spamAssassinResult = SpamAssassinResult.builder()
            .hits(hits)
            .requiredHits(requiredHits)
            .putHeader(name, value)
            .putHeader(name2, value2)
            .build();

        assertThat(spamAssassinResult.getHits()).isEqualTo(hits);
        assertThat(spamAssassinResult.getRequiredHits()).isEqualTo(requiredHits);
        assertThat(spamAssassinResult.getHeadersAsAttribute()).containsOnly(
                MapEntry.entry(name, value),
                MapEntry.entry(name2, value2));
    }
}
