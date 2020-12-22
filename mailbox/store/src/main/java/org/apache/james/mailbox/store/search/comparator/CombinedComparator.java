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
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;


/**
 * {@link Comparator} which takes a Array of other {@link Comparator}'s and use them to compare two {@link MailboxMessage} instances till one of them
 * return <> 0
 */
public class CombinedComparator implements Comparator<MailboxMessage> {

    public static CombinedComparator create(List<Sort> sorts) {
        Preconditions.checkNotNull(sorts);
        Preconditions.checkArgument(!sorts.isEmpty());
        return new CombinedComparator(sorts.stream()
            .map(toComparator())
            .collect(Guavate.toImmutableList()));
    }

    private static Function<Sort, Comparator<MailboxMessage>> toComparator() {
        return sort -> optionalResverse(toComparator(sort), sort.isReverse());
    }

    private static Comparator<MailboxMessage> toComparator(Sort sort) {
        switch (sort.getSortClause()) {
            case Arrival:
                return MessageComparators.INTERNAL_DATE_COMPARATOR;
            case MailboxCc:
                return HeaderMailboxComparator.CC_COMPARATOR;
            case MailboxFrom:
                return HeaderMailboxComparator.FROM_COMPARATOR;
            case Size:
                return MessageComparators.SIZE_COMPARATOR;
            case BaseSubject:
                return BaseSubjectComparator.BASESUBJECT;
            case MailboxTo:
                return HeaderMailboxComparator.TO_COMPARATOR;
            case Uid:
                return MessageComparators.UID_COMPARATOR;
            case SentDate:
                return SentDateComparator.SENTDATE;
            case Id:
                return MessageComparators.MESSAGE_ID_COMPARATOR;
            default:
                throw new NotImplementedException("Combined comparator does not support sort " + sort.getSortClause());
        }
    }

    private static Comparator<MailboxMessage> optionalResverse(Comparator<MailboxMessage> comparator, boolean isReverse) {
        if (isReverse) {
            return comparator.reversed();
        }
        return comparator;
    }

    private final List<Comparator<MailboxMessage>> comparators;

    public List<Comparator<MailboxMessage>> getComparators() {
        return comparators;
    }

    private CombinedComparator(List<Comparator<MailboxMessage>> comparators) {
        this.comparators = comparators;
    }

    @Override
    public int compare(MailboxMessage o1, MailboxMessage o2) {
        return comparators.stream()
            .map(comparator -> comparator.compare(o1, o2))
            .filter(result -> result != 0)
            .findFirst()
            .orElse(0);
    }

}
