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

package org.apache.james.core;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class MaybeSender {
    public static MaybeSender nullSender() {
        return new MaybeSender(Optional.empty());
    }

    public static MaybeSender of(MailAddress mailAddress) {
        return new MaybeSender(Optional.ofNullable(mailAddress)
            .filter(address -> !address.isNullSender()));
    }

    private final Optional<MailAddress> mailAddress;

    private MaybeSender(Optional<MailAddress> mailAddress) {
        this.mailAddress = mailAddress;
    }

    public Optional<MailAddress> asOptional() {
        return mailAddress;
    }

    public Stream<MailAddress> asStream() {
        return mailAddress.map(Stream::of).orElse(Stream.of());
    }

    public ImmutableList<MailAddress> asList() {
        return mailAddress.map(ImmutableList::of).orElse(ImmutableList.of());
    }

    public MailAddress get() throws NoSuchElementException {
        return mailAddress.get();
    }

    public String asString() {
        return asString(MailAddress.NULL_SENDER_AS_STRING);
    }

    public String asPrettyString() {
        return mailAddress.map(MailAddress::asPrettyString).orElse(MailAddress.NULL_SENDER_AS_STRING);
    }

    public boolean isNullSender() {
        return !mailAddress.isPresent();
    }

    public String asString(String forNullValue) {
        return mailAddress.map(MailAddress::asString).orElse(forNullValue);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MaybeSender) {
            MaybeSender that = (MaybeSender) o;

            return Objects.equals(this.mailAddress, that.mailAddress);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailAddress);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailAddress", mailAddress)
            .toString();
    }
}
