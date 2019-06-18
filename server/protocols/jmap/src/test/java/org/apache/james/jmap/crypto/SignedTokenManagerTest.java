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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.james.jmap.FixedDateZonedDateTimeProvider;
import org.apache.james.jmap.api.SimpleTokenManager.TokenStatus;
import org.apache.james.jmap.model.AttachmentAccessToken;
import org.apache.james.jmap.model.ContinuationToken;
import org.junit.Before;
import org.junit.Test;

public class SignedTokenManagerTest {

    private static final String EXPIRATION_DATE_STRING = "2011-12-03T10:15:30+01:00";
    private static final String FAKE_SIGNATURE = "MeIFNei4p6vn085wCEw0pbEwJ+Oak5yEIRLZsDcRVzT9rWWOcLvDFUA3S6awi/bxPiFxqJFreVz6xqzehnUI4tUBupk3sIsqeXShhFWBpaV+m58mC41lT/A0RJa3GgCvg6kmweCRf3tOo0+gvwOQJdwCL2B21GjDCKqBHaiK+OHcsSjrQW0xuew5z84EAz3ErdH4MMNjITksxK5FG/cGQ9V6LQgwcPk0RrprVC4eY7FFHw/sQNlJpZKsSFLnn5igPQkQtjiQ4ay1/xoB7FU7aJLakxRhYOnTKgper/Ur7UWOZJaE+4EjcLwCFLF9GaCILwp9W+mf/f7j92PVEU50Vg==";
    private static final ZonedDateTime DATE = ZonedDateTime.parse(EXPIRATION_DATE_STRING, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    private SignedTokenManager tokenManager;
    private SignedTokenFactory tokenFactory;
    private FixedDateZonedDateTimeProvider zonedDateTimeProvider;

    @Before
    public void setUp() throws Exception {
        JamesSignatureHandler signatureHandler = JamesSignatureHandlerFixture.defaultSignatureHandler();
        signatureHandler.init();
        zonedDateTimeProvider = new FixedDateZonedDateTimeProvider();
        tokenManager = new SignedTokenManager(signatureHandler, zonedDateTimeProvider);
        tokenFactory = new SignedTokenFactory(signatureHandler, zonedDateTimeProvider);

    }

    @Test(expected = NullPointerException.class)
    public void isValidShouldThrowWhenTokenIsNull() throws Exception {
        tokenManager.isValid(null);
    }

    @Test
    public void isValidShouldRecognizeValidTokens() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        assertThat(
            tokenManager.isValid(
                tokenFactory.generateContinuationToken("user")))
            .isTrue();
    }

    @Test
    public void isValidShouldRecognizeTokenWhereUsernameIsModified() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken("pirate",
            continuationToken.getExpirationDate(),
            continuationToken.getSignature());
        assertThat(tokenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void isValidShouldRecognizeTokenWhereExpirationDateIsModified() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate().plusHours(1),
            continuationToken.getSignature());
        assertThat(tokenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void isValidShouldRecognizeTokenWhereSignatureIsModified() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate(),
            FAKE_SIGNATURE);
        assertThat(tokenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void isValidShouldReturnFalseWhenTokenIsOutdated() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        zonedDateTimeProvider.setFixedDateTime(DATE.plusHours(1));
        assertThat(tokenManager.isValid(continuationToken)).isFalse();
    }

    @Test
    public void isValidShouldReturnFalseOnNonValidSignatures() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken pirateContinuationToken = new ContinuationToken("user", DATE.plusMinutes(15), "fake");
        assertThat(tokenManager.isValid(pirateContinuationToken)).isFalse();
    }

    @Test
    public void getValidityShouldThrowWhenTokenIsNull() throws Exception {
        assertThatThrownBy(() -> tokenManager.getValidity(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getValidityShouldRecognizeValidTokens() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        assertThat(
            tokenManager.getValidity(
                tokenFactory.generateContinuationToken("user")))
            .isEqualTo(TokenStatus.OK);
    }

    @Test
    public void getValidityShouldRecognizeTokenWhereUsernameIsModified() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken("pirate",
            continuationToken.getExpirationDate(),
            continuationToken.getSignature());
        assertThat(tokenManager.getValidity(pirateContinuationToken)).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    public void getValidityhouldRecognizeTokenWhereExpirationDateIsModified() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate().plusHours(1),
            continuationToken.getSignature());
        assertThat(tokenManager.getValidity(pirateContinuationToken)).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    public void getValidityShouldRecognizeTokenWhereSignatureIsModified() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        ContinuationToken pirateContinuationToken = new ContinuationToken(continuationToken.getUsername(),
            continuationToken.getExpirationDate(),
            FAKE_SIGNATURE);
        assertThat(tokenManager.getValidity(pirateContinuationToken)).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    public void getValidityShouldReturnFalseWhenTokenIsOutdated() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken continuationToken = tokenFactory.generateContinuationToken("user");
        zonedDateTimeProvider.setFixedDateTime(DATE.plusHours(1));
        assertThat(tokenManager.getValidity(continuationToken)).isEqualTo(TokenStatus.EXPIRED);
    }

    @Test
    public void getValidityShouldReturnFalseOnNonValidSignatures() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        ContinuationToken pirateContinuationToken = new ContinuationToken("user", DATE.plusMinutes(15), "fake");
        assertThat(tokenManager.getValidity(pirateContinuationToken)).isEqualTo(TokenStatus.INVALID);
    }

    @Test
    public void signedAttachmentAccessTokenShouldBeValidated() {
        String blobId = "blobId";
        zonedDateTimeProvider.setFixedDateTime(DATE);
        String serializedToken = tokenFactory.generateAttachmentAccessToken("user", blobId).serialize();

        assertThat(tokenManager.isValid(AttachmentAccessToken.from(serializedToken, blobId))).isTrue();
    }
}
