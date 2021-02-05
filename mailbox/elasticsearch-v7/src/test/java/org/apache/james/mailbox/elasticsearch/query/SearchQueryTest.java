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

package org.apache.james.mailbox.elasticsearch.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void sentDateOnShouldThrowOnNullDate() {
        assertThatThrownBy(() -> SearchQuery.sentDateOn(null, DateResolution.Day))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sentDateOnShouldThrowOnNullResolution() {
        assertThatThrownBy(() -> SearchQuery.sentDateOn(new Date(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sentDateAfterShouldThrowOnNullDate() {
        assertThatThrownBy(() -> SearchQuery.sentDateAfter(null, DateResolution.Day))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sentDateAfterShouldThrowOnNullResolution() {
        assertThatThrownBy(() -> SearchQuery.sentDateAfter(new Date(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sentDateBeforeShouldThrowOnNullDate() {
        assertThatThrownBy(() -> SearchQuery.sentDateBefore(null, DateResolution.Day))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sentDateBeforeShouldThrowOnNullResolution() {
        assertThatThrownBy(() -> SearchQuery.sentDateOn(new Date(), null))
            .isInstanceOf(NullPointerException.class);
    }

}
