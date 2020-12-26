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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.james.core.Username;
import org.junit.Test;

public class AttachmentAccessTokenTest {

    private static final String USERNAME = "username";
    private static final String BLOB_ID = "blobId";
    private static final String EXPIRATION_DATE_STRING = "2011-12-03T10:15:30+01:00";
    private static final ZonedDateTime EXPIRATION_DATE = ZonedDateTime.parse(EXPIRATION_DATE_STRING, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    private static final String SIGNATURE = "signature";

    @Test
    public void getAsStringShouldNotContainBlobId() {
        assertThat(new AttachmentAccessToken(USERNAME, BLOB_ID, EXPIRATION_DATE, SIGNATURE).serialize())
            .isEqualTo(USERNAME + AttachmentAccessToken.SEPARATOR + EXPIRATION_DATE_STRING + AttachmentAccessToken.SEPARATOR + SIGNATURE);
    }

    @Test
    public void fromShouldDeserializeAccessToken() {
        AttachmentAccessToken attachmentAccessToken = new AttachmentAccessToken(USERNAME, BLOB_ID, EXPIRATION_DATE, SIGNATURE);
        assertThat(AttachmentAccessToken.from(attachmentAccessToken.serialize(), BLOB_ID))
            .isEqualTo(attachmentAccessToken);
    }

    @Test
    public void extraSpacesShouldBeIgnored() {
        AttachmentAccessToken attachmentAccessToken = new AttachmentAccessToken(USERNAME, BLOB_ID, EXPIRATION_DATE, SIGNATURE);
        assertThat(AttachmentAccessToken.from(attachmentAccessToken.serialize() + " ", BLOB_ID))
            .isEqualTo(attachmentAccessToken);
    }

    @Test
    public void fromShouldAcceptUsernamesWithUnderscores() {
        Username failingUsername = Username.of("bad_separator@usage.screwed");
        AttachmentAccessToken attachmentAccessToken = new AttachmentAccessToken(failingUsername.asString(), BLOB_ID, EXPIRATION_DATE, SIGNATURE);
        assertThat(AttachmentAccessToken.from(attachmentAccessToken.serialize(), BLOB_ID))
            .isEqualTo(attachmentAccessToken);
    }

    @Test
    public void getPayloadShouldNotContainBlobId() {
        assertThat(new AttachmentAccessToken(USERNAME, BLOB_ID, EXPIRATION_DATE, SIGNATURE).getPayload())
            .isEqualTo(USERNAME + AttachmentAccessToken.SEPARATOR + EXPIRATION_DATE_STRING);
    }

    @Test
    public void getSignedContentShouldContainBlobId() {
        assertThat(new AttachmentAccessToken(USERNAME, BLOB_ID, EXPIRATION_DATE, SIGNATURE).getSignedContent())
            .isEqualTo(BLOB_ID + AttachmentAccessToken.SEPARATOR + USERNAME + AttachmentAccessToken.SEPARATOR + EXPIRATION_DATE_STRING);
    }

    @Test
    public void buildWithNullUsernameShouldThrow() {
        assertThatThrownBy(() -> AttachmentAccessToken.builder()
            .username(null)
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildWithNullBlobIdShouldThrow() {
        assertThatThrownBy(() -> AttachmentAccessToken.builder()
            .username(USERNAME)
            .blobId(null)
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildWithNullExpirationDateShouldThrow() {
        assertThatThrownBy(() -> AttachmentAccessToken.builder()
            .username(USERNAME)
            .blobId(BLOB_ID)
            .expirationDate(null)
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildWithNullSignatureShouldThrow() {
        assertThatThrownBy(() -> AttachmentAccessToken.builder()
            .username(USERNAME)
            .blobId(BLOB_ID)
            .expirationDate(EXPIRATION_DATE)
            .signature(null)
            .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void buildWithValidArgumentsShouldBuild() {
        AttachmentAccessToken expected = new AttachmentAccessToken(USERNAME, BLOB_ID, EXPIRATION_DATE, SIGNATURE);
        AttachmentAccessToken actual = AttachmentAccessToken.builder()
            .username(USERNAME)
            .blobId(BLOB_ID)
            .expirationDate(EXPIRATION_DATE)
            .signature(SIGNATURE)
            .build();
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }
}
