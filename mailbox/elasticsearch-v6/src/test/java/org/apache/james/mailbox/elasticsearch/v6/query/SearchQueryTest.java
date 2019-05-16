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

package org.apache.james.mailbox.elasticsearch.v6.query;

import java.util.Date;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SearchQueryTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void sentDateOnShouldThrowOnNullDate() {
        expectedException.expect(NullPointerException.class);

        SearchQuery.sentDateOn(null, DateResolution.Day);
    }

    @Test
    public void sentDateOnShouldThrowOnNullResolution() {
        expectedException.expect(NullPointerException.class);

        SearchQuery.sentDateOn(new Date(), null);
    }

    @Test
    public void sentDateAfterShouldThrowOnNullDate() {
        expectedException.expect(NullPointerException.class);

        SearchQuery.sentDateAfter(null, DateResolution.Day);
    }

    @Test
    public void sentDateAfterShouldThrowOnNullResolution() {
        expectedException.expect(NullPointerException.class);

        SearchQuery.sentDateAfter(new Date(), null);
    }

    @Test
    public void sentDateBeforeShouldThrowOnNullDate() {
        expectedException.expect(NullPointerException.class);

        SearchQuery.sentDateBefore(null, DateResolution.Day);
    }

    @Test
    public void sentDateBeforeShouldThrowOnNullResolution() {
        expectedException.expect(NullPointerException.class);

        SearchQuery.sentDateOn(new Date(), null);
    }

}
