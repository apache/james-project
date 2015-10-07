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

/**
 * Returned by the list method of MailboxRepository and others
 */
public interface MailboxMetaData {

    /** RFC3501 Selectability flag */
    public enum Selectability {
        NONE, MARKED, UNMARKED, NOSELECT
    }

    /**
     * Indicates whether this mailbox allows children and - if so - whether it
     * has any.
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

    /**
     * Gets the inferiors status of this mailbox.
     * 
     * @return not null
     */
    Children inferiors();

    /**
     * Gets the RFC3501 Selectability flag.
     */
    Selectability getSelectability();

    /**
     * Return the delimiter
     * 
     * @return delimiter
     */
    char getHierarchyDelimiter();

    /**
     * Return the MailboxPath
     * 
     * @return path
     */
    MailboxPath getPath();
}
