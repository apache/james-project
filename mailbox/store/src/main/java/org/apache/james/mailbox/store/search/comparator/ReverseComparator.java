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
package org.apache.james.mailbox.store.search.comparator;

import java.util.Comparator;

import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.Objects;

/**
 * {@link Comparator} which wraps an other {@link Comparator} and reverse it
 */
public class ReverseComparator implements Comparator<MailboxMessage> {

    private final Comparator<MailboxMessage> comparator;

    public ReverseComparator(Comparator<MailboxMessage> comparator) {
        this.comparator = comparator;
    }

    @Override
    public int compare(MailboxMessage o1, MailboxMessage o2) {
        return comparator.compare(o2, o1);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ReverseComparator) {
            ReverseComparator that = (ReverseComparator) o;
            return Objects.equal(this.comparator, that.comparator);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(comparator);
    }
}
