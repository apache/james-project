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

package org.apache.james.jmap.model;

import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.apache.james.jmap.exceptions.MalformedContinuationTokenException;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class ContinuationToken implements SignedExpiringToken {

    public static final String SEPARATOR = "_";

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String username;
        private ZonedDateTime expirationDate;
        private String signature;

        private Builder() {

        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder expirationDate(ZonedDateTime expirationDate) {
            this.expirationDate = expirationDate;
            return this;
        }

        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public ContinuationToken build() {
            return new ContinuationToken(username, expirationDate, signature);
        }
    }
    
    public static ContinuationToken fromString(String serializedToken) throws MalformedContinuationTokenException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serializedToken), "Serialized continuation token should not be null or empty");
        LinkedList<String> tokenParts = Lists.newLinkedList(Splitter.on(SEPARATOR).split(serializedToken));
        try {
            return ContinuationToken.builder()
                    .signature(tokenParts.removeLast())
                    .expirationDate(ZonedDateTime.parse(tokenParts.removeLast(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .username(Joiner.on(SEPARATOR).join(tokenParts))
                    .build();
        } catch (NoSuchElementException | IllegalArgumentException e) {
            throw new MalformedContinuationTokenException("Token " + serializedToken + " does not have enough parts", e);
        } catch (DateTimeException e) {
            throw new MalformedContinuationTokenException("Token " + serializedToken + " as an incorrect date", e);
        }
    }

    private final String username;
    private final ZonedDateTime expirationDate;
    private final String signature;

    public ContinuationToken(String username, ZonedDateTime expirationDate, String signature) {
        Preconditions.checkNotNull(username);
        Preconditions.checkArgument(! username.isEmpty());
        Preconditions.checkNotNull(expirationDate);
        Preconditions.checkNotNull(signature);
        this.username = username;
        this.expirationDate = expirationDate;
        this.signature = signature;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public ZonedDateTime getExpirationDate() {
        return expirationDate;
    }

    @Override
    public String getSignature() {
        return signature;
    }

    public String serialize() {
        return getPayload()
            + SEPARATOR
            + signature;
    }
    
    @Override
    public String getPayload() {
        return username
            + SEPARATOR
            + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationDate);
    }

    @Override
    public String getSignedContent() {
        return getPayload();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ContinuationToken continuationToken = (ContinuationToken) other;
        return Objects.equals(username, continuationToken.username)
            && expirationDate.isEqual(continuationToken.expirationDate)
            && Objects.equals(signature, continuationToken.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, expirationDate, signature);
    }

    @Override
    public String toString() {
        return "ContinuationToken{" +
            "username='" + username + '\'' +
            ", expirationDate=" + expirationDate +
            ", signature='" + signature + '\'' +
            '}';
    }
}
