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

package org.apache.james.jmap.crypto;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.api.ContinuationTokenManager.ContinuationTokenStatus;
import org.apache.james.jmap.model.ContinuationToken;
import org.apache.james.jmap.utils.ZonedDateTimeProvider;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SignedContinuationTokenManagerTest {

    private static final String EXPIRATION_DATE_STRING = "2011-12-03T10:15:30+01:00";
    private static final String FAKE_SIGNATURE = "MeIFNei4p6vn085wCEw0pbEwJ+Oak5yEIRLZsDcRVzT9rWWOcLvDFUA3S6awi/bxPiFxqJFreVz6xqzehnUI4tUBupk3sIsqeXShhFWBpaV+m58mC41lT/A0RJa3GgCvg6kmweCRf3tOo0+gvwOQJdwCL2B21GjDCKqBHaiK+OHcsSjrQW0xuew5z84EAz3ErdH4MMNjITksxK5FG/cGQ9V6LQgwcPk0RrprVC4eY7FFHw/sQNlJpZKsSFLnn5igPQkQtjiQ4ay1/xoB7FU7aJLakxRhYOnTKgper/Ur7UWOZJaE+4EjcLwCFLF9GaCILwp9W+mf/f7j92PVEU50Vg==";
    private static final ZonedDateTime DATE = ZonedDateTime.parse(EXPIRATION_DATE_STRING, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    private SignedContinuationTokenManager toKenManager;
    private ZonedDateTimeProvider zonedDateTimeProvider;

    @Before
    public void setUp() throws Exception {
        JamesSignatureHandler signatureHandler = new JamesSignatureHandlerProvider().provide();
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        toKenManager = new SignedContinuationTokenManager(signatureHandler, zonedDateTimeProvider);
    }

    @Test(expected = NullPointerException.class)
    public void isValidShouldThrowWhenTokenIsNull() throws Exception {
        toKenManager.isValid(null);
    }

    @Test
    public void isValidShouldRecognizeValidTokens() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        assertThat(
            toKenManager.isValid(
                toKenManager.generateToken("user")))
            .isTrue();
    }

    @Test
    public void isValidShouldRecognizeTokenWhereUsernameIsModified() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken("pirate",
            continuationToken.getExpirationDate(),
            continuationToken.getSignature());
        assertThat(toKenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void isValidShouldRecognizeTokenWhereExpirationDateIsModified() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate().plusHours(1),
            continuationToken.getSignature());
        assertThat(toKenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void isValidShouldRecognizeTokenWhereSignatureIsModified() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate(),
            FAKE_SIGNATURE);
        assertThat(toKenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void isValidShouldReturnFalseWhenTokenIsOutdated() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE.plusHours(1));
        assertThat(toKenManager.isValid(continuationToken)).isFalse();
    }

    @Test
    public void isValidShouldReturnFalseOnNonValidSignatures() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken pirateContinuationToken = new ContinuationToken("user", DATE.plusMinutes(15), "fake");
        assertThat(toKenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void getValidityShouldThrowWhenTokenIsNull() throws Exception {
        toKenManager.getValidity(null);
    }

    @Test
    public void getValidityShouldRecognizeValidTokens() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        assertThat(
            toKenManager.getValidity(
                toKenManager.generateToken("user")))
            .isEqualTo(ContinuationTokenStatus.OK);
    }

    @Test
    public void getValidityShouldRecognizeTokenWhereUsernameIsModified() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken("pirate",
            continuationToken.getExpirationDate(),
            continuationToken.getSignature());
        assertThat(toKenManager.getValidity(pirateContinuationToken)).isEqualTo(ContinuationTokenStatus.INVALID);
    }

    @Test
    public void getValidityhouldRecognizeTokenWhereExpirationDateIsModified() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate().plusHours(1),
            continuationToken.getSignature());
        assertThat(toKenManager.getValidity(pirateContinuationToken)).isEqualTo(ContinuationTokenStatus.INVALID);
    }

    @Test
    public void getValidityShouldRecognizeTokenWhereSignatureIsModified() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate(),
            FAKE_SIGNATURE);
        assertThat(toKenManager.getValidity(pirateContinuationToken)).isEqualTo(ContinuationTokenStatus.INVALID);
    }

    @Test
    public void getValidityShouldReturnFalseWhenTokenIsOutdated() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken continuationToken = toKenManager.generateToken("user");
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE.plusHours(1));
        assertThat(toKenManager.getValidity(continuationToken)).isEqualTo(ContinuationTokenStatus.EXPIRED);
    }

    @Test
    public void getValidityShouldReturnFalseOnNonValidSignatures() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        ContinuationToken pirateContinuationToken = new ContinuationToken("user", DATE.plusMinutes(15), "fake");
        assertThat(toKenManager.getValidity(pirateContinuationToken)).isEqualTo(ContinuationTokenStatus.INVALID);
    }

    @Test(expected = NullPointerException.class)
    public void generateTokenShouldThrowWhenUsernameIsNull() throws Exception {
        toKenManager.generateToken(null);
    }

    @Test
    public void generateTokenShouldHaveTheRightOutPut() throws Exception {
        when(zonedDateTimeProvider.provide()).thenAnswer(invocationOnMock -> DATE);
        assertThat(toKenManager.generateToken("user").serialize())
            .isEqualTo("user_2011-12-03T10:30:30+01:00_eOvOqTmV3dPrhIkbuQSj2sno3YJMxWl6J1sH1JhwYcaNgMX9twm98/WSF9uyDkvJgvBxFokDr53AbxQ3DsJysB2dAzCC0tUM4u8ZMvl/hQrFXhVCdpVMyHRvixKCxnHsVXAr9g3WMn2vbIVq5i3HPgA6/p9FB1+N4WA06B8ueoCrdxT2w1ITEm8p+QZvje3n1F344SgrqgIYqvt0yUvzxnB24f3ccjAKidlBj4wZkcXgUTMbZ7MdnCbDGbp10+tgJqxiv1S0rXZMeJLJ+vBt5TyqEhsJUmUQ84qctlB4yR5FS+ncbAOyZAxs2dWsHqiQjedb3IR77N7CASzqO2mmVw==");
    }

}
