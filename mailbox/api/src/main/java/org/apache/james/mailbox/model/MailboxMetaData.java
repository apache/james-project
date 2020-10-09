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

package org.apache.james.mailbox.model;

import java.util.Comparator;
import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * Returned by the list method of MailboxRepository and others
 */
public class MailboxMetaData implements Comparable<MailboxMetaData> {
    /** RFC3501 Selectability flag */
    public enum Selectability {
        NONE, MARKED, UNMARKED, NOSELECT
    }

    /**
     * Indicates whether this mailbox allows children and - if so - whether it
     * has any.
     *
     * See <code>\Noinferiors</code> as per RFC3501.
     */
    public enum Children {
        /**
         * No children allowed.
         */
        NO_INFERIORS,
        /**
         * Children allowed by this mailbox but it is unknown whether this
         * mailbox has children.
         */
        CHILDREN_ALLOWED_BUT_UNKNOWN,
        /**
         * Indicates that this mailbox has children.
         */
        HAS_CHILDREN,
        /**
         * Indicates that this mailbox allows interiors but currently has no
         * children.
         */
        HAS_NO_CHILDREN
    }

    public static  final Comparator<MailboxMetaData> COMPARATOR = Comparator
        .<MailboxMetaData, Boolean>comparing(metadata -> metadata.getPath().isInbox()).reversed()
        .thenComparing(metadata -> metadata.getPath().getName());

    private final MailboxPath path;
    private final char delimiter;
    private final Children inferiors;
    private final Selectability selectability;
    private final MailboxId mailboxId;
    private final MailboxACL resolvedAcls;
    private final MailboxCounters counters;

    public MailboxMetaData(MailboxPath path, MailboxId mailboxId, char delimiter, Children inferiors, Selectability selectability, MailboxACL resolvedAcls, MailboxCounters counters) {
        this.path = path;
        this.mailboxId = mailboxId;
        this.delimiter = delimiter;
        this.inferiors = inferiors;
        this.selectability = selectability;
        this.resolvedAcls = resolvedAcls;
        this.counters = counters;
    }

    public MailboxCounters getCounters() {
        return counters;
    }

    public MailboxACL getResolvedAcls() {
        return resolvedAcls;
    }

    /**
     * Gets the inferiors status of this mailbox.
     *
     * Is this mailbox <code>\Noinferiors</code> as per RFC3501.
     *
     * @return not null
     */
    public final Children inferiors() {
        return inferiors;
    }

    /**
     * Gets the RFC3501 Selectability flag.
     */
    public final Selectability getSelectability() {
        return selectability;
    }

    /**
     * Return the delimiter
     */
    public char getHierarchyDelimiter() {
        return delimiter;
    }


    /**
     * Return the MailboxPath
     */
    public MailboxPath getPath() {
        return path;
    }

    public MailboxId getId() {
        return mailboxId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("path", path)
            .toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxMetaData) {
            MailboxMetaData that = (MailboxMetaData) o;

            return Objects.equals(this.path, that.path);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public int compareTo(MailboxMetaData o) {
        return COMPARATOR.compare(this, o);
    }
}
