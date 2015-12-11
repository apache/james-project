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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.james.jmap.exceptions.MalformedContinuationTokenException;

import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ContinuationToken {

    public static final String SEPARATOR = "_";

    private final String username;
    private final ZonedDateTime expirationDate;
    private final String signature;

    public ContinuationToken(String username, ZonedDateTime expirationDate, String signature) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(expirationDate);
        Preconditions.checkNotNull(signature);
        this.username = username;
        this.expirationDate = expirationDate;
        this.signature = signature;
    }

    public static ContinuationToken fromString(String serializedToken) throws MalformedContinuationTokenException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serializedToken), "Serialized continuation token should not be null or empty");
        ImmutableList<String> tokenParts = ImmutableList.copyOf(Splitter.on(SEPARATOR).split(serializedToken));
        if (tokenParts.size() < 3) {
            throw new MalformedContinuationTokenException("Token " + serializedToken + " does not have enough parts");
        }
        Iterator<String> tokenPartsReversedIterator = tokenParts.reverse().iterator();
        String signature = tokenPartsReversedIterator.next();
        String expirationDateString = tokenPartsReversedIterator.next();
        String username = retrieveUsername(tokenPartsReversedIterator);
        try {
            return new ContinuationToken(username,
                ZonedDateTime.parse(expirationDateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                signature);
        } catch(DateTimeException e) {
            throw new MalformedContinuationTokenException("Token " + serializedToken + " as an incorrect date", e);
        }
    }

    private static String retrieveUsername(Iterator<String> reversedIteratorOnUsernameParts) {
        List<String> usernamePart = ImmutableList.copyOf(Lists.newArrayList(reversedIteratorOnUsernameParts)).reverse();
        return Joiner.on(SEPARATOR).join(usernamePart);
    }

    public String getUsername() {
        return username;
    }

    public ZonedDateTime getExpirationDate() {
        return expirationDate;
    }

    public String getSignature() {
        return signature;
    }

    public String serialize() {
        return username
            + SEPARATOR
            + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expirationDate)
            + SEPARATOR
            + signature;
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
