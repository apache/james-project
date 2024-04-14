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

import java.util.Arrays;
import java.util.Optional;

import org.apache.james.jmap.model.Property;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public enum MailboxProperty implements Property {
    ID("id"),
    NAME("name"),
    PARENT_ID("parentId"),
    ROLE("role"),
    SORT_ORDER("sortOrder"),
    MUST_BE_ONLY_MAILBOX("mustBeOnlyMailbox"),
    MAY_READ_ITEMS("mayReadItems"),
    MAY_ADD_ITEMS("mayAddItems"),
    MAY_REMOVE_ITEMS("mayRemoveItems"),
    MAY_CREATE_CHILD("mayCreateChild"),
    MAY_RENAME("mayRename"),
    MAY_DELETE("mayDelete"),
    TOTAL_MESSAGES("totalMessages"),
    UNREAD_MESSAGES("unreadMessages"),
    TOTAL_THREADS("totalThreads"),
    UNREAD_THREADS("unreadThreads");

    private final String fieldName;

    MailboxProperty(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String asFieldName() {
        return fieldName;
    }

    public static Optional<MailboxProperty> findProperty(String value) {
        Preconditions.checkNotNull(value);
        return Arrays.stream(values())
            .filter(element -> element.fieldName.equals(value))
            .findAny();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("fieldName", fieldName)
            .toString();
    }
}
