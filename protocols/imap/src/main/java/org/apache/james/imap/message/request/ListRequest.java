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
package org.apache.james.imap.message.request;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.StatusDataItems;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class ListRequest extends AbstractImapRequest {

    // https://www.rfc-editor.org/rfc/rfc5258.html
    public enum ListSelectOption {
        REMOTE,
        RECURSIVEMATCH,
        SUBSCRIBED,
        // https://www.rfc-editor.org/rfc/rfc6154.html
        SPECIAL_USE
    }

    // https://www.rfc-editor.org/rfc/rfc5258.html
    public enum ListReturnOption {
        CHILDREN,
        SUBSCRIBED,
        // https://www.rfc-editor.org/rfc/rfc5819.html LIST STATUS
        STATUS,
        // https://www.rfc-editor.org/rfc/rfc8440.html
        MYRIGHTS,
        // https://www.rfc-editor.org/rfc/rfc6154.html
        SPECIAL_USE
    }

    private final String baseReferenceName;
    private final List<String> mailboxPatterns;
    private final EnumSet<ListSelectOption> selectOptions;
    private final EnumSet<ListReturnOption> returnOptions;
    private final Optional<StatusDataItems> statusDataItems;

    public ListRequest(String referenceName, String mailboxPattern, Tag tag) {
        this(ImapConstants.LIST_COMMAND, referenceName, mailboxPattern, tag, EnumSet.noneOf(ListSelectOption.class), EnumSet.noneOf(ListReturnOption.class), Optional.empty());
    }

    public ListRequest(ImapCommand imapCommand, String referenceName, String mailboxPattern, Tag tag,
                       EnumSet<ListSelectOption> selectOptions, EnumSet<ListReturnOption> returnOptions, Optional<StatusDataItems> statusDataItems) {
        this(imapCommand, referenceName, ImmutableList.of(mailboxPattern), tag, selectOptions, returnOptions, statusDataItems);
    }

    /**
     * @param mailboxPatterns the mailbox patterns, as specified by RFC 5258: a mailbox is listed when it matches at least one of them.
     */
    public ListRequest(ImapCommand imapCommand, String referenceName, List<String> mailboxPatterns, Tag tag,
                       EnumSet<ListSelectOption> selectOptions, EnumSet<ListReturnOption> returnOptions, Optional<StatusDataItems> statusDataItems) {
        super(tag, imapCommand);
        this.baseReferenceName = referenceName;
        this.mailboxPatterns = ImmutableList.copyOf(mailboxPatterns);
        this.selectOptions = selectOptions;
        this.returnOptions = returnOptions;
        this.statusDataItems = statusDataItems;
    }

    public final String getBaseReferenceName() {
        return baseReferenceName;
    }

    /**
     * @return the first mailbox pattern, for callers only supporting a single pattern. See {@link #getMailboxPatterns()}.
     */
    public final String getMailboxPattern() {
        return mailboxPatterns.stream().findFirst().orElse("");
    }

    public final List<String> getMailboxPatterns() {
        return mailboxPatterns;
    }

    /**
     * @return true for the special request of the hierarchy delimiter and the root name of the reference (RFC 3501 section 6.3.8):
     * a single empty mailbox pattern.
     */
    public final boolean isHierarchyDelimiterRequest() {
        return mailboxPatterns.size() == 1 && mailboxPatterns.get(0).isEmpty();
    }

    public EnumSet<ListSelectOption> getSelectOptions() {
        return selectOptions;
    }

    public EnumSet<ListReturnOption> getReturnOptions() {
        return returnOptions;
    }

    public boolean selectSubscribed() {
        return getSelectOptions().contains(ListSelectOption.SUBSCRIBED);
    }

    public boolean selectRemote() {
        return getSelectOptions().contains(ListSelectOption.REMOTE);
    }

    public Optional<StatusDataItems> getStatusDataItems() {
        return statusDataItems;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("baseReferenceName", baseReferenceName)
            .add("mailboxPatterns", mailboxPatterns)
            .add("selectOptions", selectOptions)
            .add("returnOptions", returnOptions)
            .add("statusDataItem", statusDataItems.orElse(null))
            .toString();
    }
}
