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

package org.apache.james.mailbox;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.james.mailbox.exception.MailboxException;

import com.google.common.base.MoreObjects;

public final class NullableMessageSequenceNumber {

    public interface HandleNoMessage<T> {
        T handle() throws MailboxException;
    }

    public interface HandleMessage<T> {
        T handle(MessageSequenceNumber sequenceNumber) throws MailboxException;
    }

    public static NullableMessageSequenceNumber noMessage() {
        return new NullableMessageSequenceNumber(Optional.empty());
    }

    public static NullableMessageSequenceNumber of(int msn) {
        return new NullableMessageSequenceNumber(Optional.of(MessageSequenceNumber.of(msn)));
    }

    private final Optional<MessageSequenceNumber> msn;

    private NullableMessageSequenceNumber(Optional<MessageSequenceNumber> msn) {
        this.msn = msn;
    }

    public void ifPresent(Consumer<MessageSequenceNumber> consumer) {
        msn.ifPresent(consumer);
    }

    public <T> T fold(HandleNoMessage<T> handleNoMessage, HandleMessage<T> handleMessage) throws MailboxException {
        if (msn.isPresent()) {
            return handleMessage.handle(msn.get());
        } else {
            return handleNoMessage.handle();
        }
    }

    public Optional<Integer> asInt() {
        return msn.map(MessageSequenceNumber::asInt);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NullableMessageSequenceNumber) {
            NullableMessageSequenceNumber that = (NullableMessageSequenceNumber) o;
            return Objects.equals(msn, that.msn);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(msn);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("msn", msn)
            .toString();
    }
}
