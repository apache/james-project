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

import org.apache.james.mailbox.StandardMailboxMetaDataComparator;

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

    private final MailboxPath path;
    private final char delimiter;
    private final Children inferiors;
    private final Selectability selectability;
    private final MailboxId mailboxId;

    public MailboxMetaData(MailboxPath path, MailboxId mailboxId, char delimiter) {
        this(path, mailboxId, delimiter, Children.CHILDREN_ALLOWED_BUT_UNKNOWN, Selectability.NONE);
    }

    public MailboxMetaData(MailboxPath path, MailboxId mailboxId, char delimiter, Children inferiors, Selectability selectability) {
        super();
        this.path = path;
        this.mailboxId = mailboxId;
        this.delimiter = delimiter;
        this.inferiors = inferiors;
        this.selectability = selectability;
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
     * 
     * @return delimiter
     */
    public char getHierarchyDelimiter() {
        return delimiter;
    }


    /**
     * Return the MailboxPath
     * 
     * @return path
     */
    public MailboxPath getPath() {
        return path;
    }

    public MailboxId getId() {
        return mailboxId;
    }

    @Override
    public String toString() {
        return "ListResult: " + path;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((path == null) ? 0 : path.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MailboxMetaData other = (MailboxMetaData) obj;
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(MailboxMetaData o) {
        return StandardMailboxMetaDataComparator.order(this, o);
    }
}
