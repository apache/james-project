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

package org.apache.james.mailbox.store.search.mime;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;

public class EMailer implements SerializableMessage {

    private final Optional<String> name;
    private final String address;
    private final String domain;

    public EMailer(Optional<String> name, String address, String domain) {
        this.name = name;
        this.address = address;
        this.domain = removeTopDomain(domain);
    }

    String removeTopDomain(String s) {
        if (s == null) {
            return null;
        }
        if (s.contains(".")) {
            return s.substring(0, s.lastIndexOf('.'));
        }
        return s;
    }

    public Optional<String> getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public String serialize() {
        return Joiner.on(" ").join(name.orElse(" "), address);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EMailer) {
            EMailer otherEMailer = (EMailer) o;
            return Objects.equals(name, otherEMailer.name)
                && Objects.equals(address, otherEMailer.address)
                && Objects.equals(domain, otherEMailer.domain);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, address, domain);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("address", address)
            .add("domain", domain)
            .toString();
    }
}
