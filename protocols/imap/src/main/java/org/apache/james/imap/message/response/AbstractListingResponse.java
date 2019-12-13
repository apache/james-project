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

package org.apache.james.imap.message.response;

import java.util.Objects;

import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.mailbox.model.MailboxMetaData;

import com.google.common.base.MoreObjects;

public abstract class AbstractListingResponse {
    private final MailboxMetaData.Children children;
    private final MailboxMetaData.Selectability selectability;
    private final String name;
    private final char hierarchyDelimiter;
    private final MailboxType type;

    public AbstractListingResponse(MailboxMetaData.Children children,
                                   MailboxMetaData.Selectability selectability,
                                   String name, char hierarchyDelimiter, MailboxType type) {
        this.children = children;
        this.selectability = selectability;
        this.name = name;
        this.hierarchyDelimiter = hierarchyDelimiter;
        this.type = type;
    }

    /**
     * Gets hierarchy delimiter.
     * 
     * @return hierarchy delimiter, or Character.UNASSIGNED if no hierarchy exists
     */
    public final char getHierarchyDelimiter() {
        return hierarchyDelimiter;
    }

    /**
     * Gets the listed name.
     * 
     * @return name of the listed mailbox, not null
     */
    public final String getName() {
        return name;
    }

    public MailboxType getType() {
        return type;
    }

    public MailboxMetaData.Children getChildren() {
        return children;
    }

    public MailboxMetaData.Selectability getSelectability() {
        return selectability;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof AbstractListingResponse) {
            AbstractListingResponse that = (AbstractListingResponse) other;

            return Objects.equals(this.children, that.children) &&
                Objects.equals(this.selectability, that.selectability) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.hierarchyDelimiter, that.hierarchyDelimiter) &&
                Objects.equals(this.type, that.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(children, selectability, name, hierarchyDelimiter, type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("children", children)
            .add("selectability", selectability)
            .add("name", name)
            .add("hierarchyDelimiter", hierarchyDelimiter)
            .add("type", type)
            .toString();
    }
}