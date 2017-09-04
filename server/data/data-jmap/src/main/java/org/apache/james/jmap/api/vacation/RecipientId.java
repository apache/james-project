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

package org.apache.james.jmap.api.vacation;

import java.util.Objects;

import org.apache.james.core.MailAddress;

import com.google.common.base.Preconditions;

public class RecipientId {

    public static RecipientId fromMailAddress(MailAddress mailAddress) {
        Preconditions.checkNotNull(mailAddress, "RecipientId mailAddress should not be null");
        return new RecipientId(mailAddress);
    }

    private final MailAddress mailAddress;

    private RecipientId(MailAddress mailAddress) {
        this.mailAddress = mailAddress;
    }

    public MailAddress getMailAddress() {
        return mailAddress;
    }

    public String getAsString() {
        return mailAddress.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RecipientId accountId = (RecipientId) o;

        return Objects.equals(this.mailAddress, accountId.mailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mailAddress);
    }
}
