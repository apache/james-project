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

package org.apache.james.jmap.utils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Message;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

public class SortToComparatorConvertor {

    private static final String SEPARATOR = " ";
    private static final String DESC_ORDERING = "desc";

    private SortToComparatorConvertor() {
    }

    @SuppressWarnings("rawtypes")
    private static final Map<String, Function<Message<?>, Comparable>> fieldsMessageFunctionMap = ImmutableMap.of(
            "date", Message::getInternalDate,
            "id", Message::getUid);

    public static <M extends Message<Id>, Id extends MailboxId> Comparator<M> comparatorFor(List<String> sort) {
        return sort.stream()
            .map(SortToComparatorConvertor::<M, Id> comparatorForField)
            .reduce(new EmptyComparator<M>(), (x, y) -> x.thenComparing(y));
    }

    @SuppressWarnings("unchecked")
    private static <M extends Message<Id>, Id extends MailboxId> Comparator<M> comparatorForField(String field) {
        List<String> splitToList = Splitter.on(SEPARATOR).splitToList(field);
        checkField(splitToList);
        Comparator<M> fieldComparator = Comparator.comparing(functionForField(splitToList.get(0)));
        if (splitToList.size() == 1 || splitToList.get(1).equals(DESC_ORDERING)) {
            return fieldComparator.reversed();
        }
        return fieldComparator;
    }

    @SuppressWarnings("rawtypes")
    private static Function<Message<?>, Comparable> functionForField(String field) {
        if (!fieldsMessageFunctionMap.containsKey(field)) {
            throw new IllegalArgumentException("Unknown sorting field");
        }
        return fieldsMessageFunctionMap.get(field);
    }

    private static void checkField(List<String> splitToList) {
        Preconditions.checkArgument(splitToList.size() >= 1 && splitToList.size() <= 2, "Bad sort field definition");
    }

    private static class EmptyComparator<Type> implements Comparator<Type> {

        @Override
        public int compare(Type o1, Type o2) {
            return 0;
        }
        
    }
}
