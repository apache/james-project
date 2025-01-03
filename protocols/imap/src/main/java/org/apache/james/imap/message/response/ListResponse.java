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


import static org.apache.james.imap.processor.ListProcessor.RETURN_NON_EXISTENT;
import static org.apache.james.imap.processor.ListProcessor.RETURN_SUBSCRIBED;

import java.util.EnumSet;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.collect.ImmutableSet;

/**
 * Values an IMAP4rev1 <code>LIST</code> response.
 */
public final class ListResponse extends AbstractListingResponse implements ImapResponseMessage {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MailboxMetaData.Children children;
        private MailboxMetaData.Selectability selectability;
        private String name;
        private char hierarchyDelimiter;
        private boolean returnSubscribed;
        private boolean returnNonExistent;
        private MailboxType mailboxType;
        private ImmutableSet.Builder<ChildInfo> childInfos;

        public Builder() {
            this.childInfos = ImmutableSet.builder();
        }

        public Builder children(MailboxMetaData.Children children) {
            this.children = children;
            return this;
        }

        public Builder selectability(MailboxMetaData.Selectability selectability) {
            this.selectability = selectability;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder hierarchyDelimiter(char hierarchyDelimiter) {
            this.hierarchyDelimiter = hierarchyDelimiter;
            return this;
        }

        public Builder returnSubscribed(boolean returnSubscribed) {
            this.returnSubscribed = returnSubscribed;
            return this;
        }

        public Builder returnNonExistent(boolean returnNonExistent) {
            this.returnNonExistent = returnNonExistent;
            return this;
        }

        public Builder childInfos(ChildInfo... childInfos) {
            this.childInfos.add(childInfos);
            return this;
        }

        public Builder mailboxType(MailboxType mailboxType) {
            this.mailboxType = mailboxType;
            return this;
        }

        public Builder forMetaData(MailboxMetaData mailboxMetaData) {
            return children(mailboxMetaData.inferiors())
                .selectability(mailboxMetaData.getSelectability())
                .hierarchyDelimiter(mailboxMetaData.getHierarchyDelimiter())
                .returnNonExistent(!RETURN_NON_EXISTENT);
        }

        public Builder nonExitingSubscribedMailbox(MailboxPath subscribedPath) {
            return name(subscribedPath.getName())
                .children(MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN)
                .selectability(MailboxMetaData.Selectability.NONE)
                .hierarchyDelimiter(MailboxConstants.FOLDER_DELIMITER)
                .returnSubscribed(RETURN_SUBSCRIBED)
                .returnNonExistent(RETURN_NON_EXISTENT)
                .mailboxType(MailboxType.OTHER);
        }

        private EnumSet<ChildInfo> buildChildInfos() {
            ImmutableSet<ChildInfo> childInfoImmutableSet = childInfos.build();
            if (childInfoImmutableSet.isEmpty()) {
                return EnumSet.noneOf(ChildInfo.class);
            } else {
                return EnumSet.copyOf(childInfoImmutableSet);
            }
        }

        public ListResponse build() {

            return new ListResponse(children, selectability, name, hierarchyDelimiter, returnSubscribed,
                returnNonExistent, buildChildInfos(), mailboxType);
        }
    }


    // https://www.rfc-editor.org/rfc/rfc5258.html
    public enum ChildInfo {
        SUBSCRIBED
    }

    private boolean returnSubscribed;
    private boolean returnNonExistent;

    private EnumSet<ChildInfo> childInfos;

    public ListResponse(MailboxMetaData.Children children, MailboxMetaData.Selectability selectability,
                        String name, char hierarchyDelimiter, boolean returnSubscribed, boolean returnNonExistent,
                        EnumSet<ChildInfo> childInfos, MailboxType mailboxType) {
        super(children, selectability, name, hierarchyDelimiter, mailboxType);
        this.returnSubscribed = returnSubscribed;
        this.returnNonExistent = returnNonExistent;
        this.childInfos = childInfos;
    }

    public boolean isReturnSubscribed() {
        return returnSubscribed;
    }

    public boolean isReturnNonExistent() {
        return returnNonExistent;
    }

    public EnumSet<ChildInfo> getChildInfos() {
        return childInfos;
    }

    @Override
    public String getTypeAsString() {
        return getType().getRfc6154attributeName();
    }
}
