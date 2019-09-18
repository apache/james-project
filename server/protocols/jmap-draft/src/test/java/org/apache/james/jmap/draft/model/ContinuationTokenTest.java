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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.james.jmap.draft.exceptions.MalformedContinuationTokenException;
import org.junit.Before;
import org.junit.Test;

public class ContinuationTokenTest {

    private static final String USER = "user";
    private static final String EXPIRATION_DATE_STRING = "2011-12-03T10:15:30+01:00";
    public static final String SIGNATURE = "signature";

    private ZonedDateTime expirationDate;

    @Before
    public void setUp() {
        expirationDate = ZonedDateTime.parse(EXPIRATION_DATE_STRING, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Test
    public void continuationTokenShouldBeRetrievedFromString() throws Exception {
        assertThat(ContinuationToken.fromString(USER + ContinuationToken.SEPARATOR + EXPIRATION_DATE_STRING + ContinuationToken.SEPARATOR + SIGNATURE))
            .isEqualTo(new ContinuationToken(USER, expirationDate, SIGNATURE));
    }

    @Test
    public void usernameShouldBeAllowedToContainSeparator() throws Exception {
        String username = "user" + ContinuationToken.SEPARATOR + "using" + ContinuationToken.SEPARATOR + "separator";
        assertThat(ContinuationToken.fromString(username + ContinuationToken.SEPARATOR + EXPIRATION_DATE_STRING + ContinuationToken.SEPARATOR + SIGNATURE))
            .isEqualTo(new ContinuationToken(username, expirationDate, SIGNATURE));
    }

    @Test(expected = MalformedContinuationTokenException.class)
    public void continuationTokenThatMissPartsShouldThrow() throws Exception {
        ContinuationToken.fromString(EXPIRATION_DATE_STRING + ContinuationToken.SEPARATOR + SIGNATURE);
    }

    @Test(expected = MalformedContinuationTokenException.class)
    public void continuationTokenWithMalformedDatesShouldThrow() throws Exception {
        ContinuationToken.fromString(USER + ContinuationToken.SEPARATOR + "2011-25-03T10:15:30+01:00" + ContinuationToken.SEPARATOR + SIGNATURE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullContinuationTokenShouldThrow() throws Exception {
        ContinuationToken.fromString(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyContinuationTokenShouldThrow() throws Exception {
        ContinuationToken.fromString("");
    }

    @Test
    public void getAsStringShouldReturnACorrectResult() throws Exception {
        assertThat(new ContinuationToken(USER, expirationDate, SIGNATURE).serialize())
            .isEqualTo(USER + ContinuationToken.SEPARATOR + EXPIRATION_DATE_STRING + ContinuationToken.SEPARATOR + SIGNATURE);
    }

    @Test(expected = NullPointerException.class)
    public void newContinuationTokenWithNullUsernameShouldThrow() {
        new ContinuationToken(null, expirationDate, SIGNATURE);
    }

    @Test(expected = NullPointerException.class)
    public void newContinuationTokenWithNullExpirationDateShouldThrow() {
        new ContinuationToken(USER, null, SIGNATURE);
    }

    @Test(expected = NullPointerException.class)
    public void newContinuationTokenWithNullSignatureShouldThrow() {
        new ContinuationToken(USER, expirationDate, null);
    }
}
