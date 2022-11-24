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
import java.util.stream.Collectors;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;

import com.google.common.base.MoreObjects;

public class ListRequest extends AbstractImapRequest {

    // https://www.rfc-editor.org/rfc/rfc5258.html
    public enum ListSelectOption {
        REMOTE,
        RECURSIVEMATCH,
        SUBSCRIBED
    }

    // https://www.rfc-editor.org/rfc/rfc5258.html
    public enum ListReturnOption {
        CHILDREN,
        SUBSCRIBED
    }

    private final String baseReferenceName;
    private final String mailboxPattern;
    private final EnumSet<ListSelectOption> selectOptions;
    private final EnumSet<ListReturnOption> returnOptions;

    public ListRequest(String referenceName, String mailboxPattern, Tag tag) {
        this(ImapConstants.LIST_COMMAND, referenceName, mailboxPattern, tag, EnumSet.noneOf(ListSelectOption.class), EnumSet.noneOf(ListReturnOption.class));
    }

    public ListRequest(ImapCommand imapCommand, String referenceName, String mailboxPattern, Tag tag,
                       EnumSet<ListSelectOption> selectOptions, EnumSet<ListReturnOption> returnOptions) {
        super(tag, imapCommand);
        this.baseReferenceName = referenceName;
        this.mailboxPattern = mailboxPattern;
        this.selectOptions = selectOptions;
        this.returnOptions = returnOptions;
    }

    public final String getBaseReferenceName() {
        return baseReferenceName;
    }

    public final String getMailboxPattern() {
        return mailboxPattern;
    }

    public EnumSet<ListSelectOption> getSelectOptions() {
        return selectOptions;
    }

    public EnumSet<ListReturnOption> getReturnOptions() {
        return returnOptions;
    }

    public final boolean selectSubscribed() {
        return getSelectOptions().contains(ListSelectOption.SUBSCRIBED);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("baseReferenceName", baseReferenceName)
            .add("mailboxPattern", mailboxPattern)
            .add("selectOptions", selectOptions.stream().map(Enum::toString).collect(Collectors.joining(",")))
            .add("returnOptions", returnOptions.stream().map(Enum::toString).collect(Collectors.joining(",")))
            .toString();
    }
}
