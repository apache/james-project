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

package org.apache.james.imap.encode;

import java.io.IOException;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.message.response.AbstractListingResponse;
import org.apache.james.mailbox.model.MailboxMetaData;

import com.google.common.collect.ImmutableList;

public class ListingEncodingUtils {

    public static void encodeListingResponse(ImapCommand command, ImapResponseComposer composer, AbstractListingResponse response) throws IOException {
        composer.untagged();
        composer.message(command.getName());
        composer.openParen();
        for (String attribute : getNameAttributes(response)) {
            composer.message(attribute);
        }
        composer.closeParen();
        writeDelimiter(composer, response.getHierarchyDelimiter());
        composer.mailbox(response.getName());
        composer.end();
    }

    private static void writeDelimiter(ImapResponseComposer composer, char hierarchyDelimiter) throws IOException {
        if (hierarchyDelimiter == Character.UNASSIGNED) {
            composer.nil();
        } else {
            composer.quote(Character.toString(hierarchyDelimiter));
        }
    }

    private static ImmutableList<String> getNameAttributes(AbstractListingResponse response) {
        return ImmutableList
            .<String>builder()
            .addAll(selectabilityAsString(response.getSelectability()))
            .addAll(childrenAsString(response.getChildren()))
            .addAll(mailboxAttributeAsString(response.getType()))
            .build();
    }


    private static List<String> selectabilityAsString(MailboxMetaData.Selectability selectability) {
        switch (selectability) {
            case MARKED:
                return ImmutableList.of(ImapConstants.NAME_ATTRIBUTE_MARKED);
            case NOSELECT:
                return ImmutableList.of(ImapConstants.NAME_ATTRIBUTE_NOSELECT);
            case UNMARKED:
                return ImmutableList.of(ImapConstants.NAME_ATTRIBUTE_UNMARKED);
            default:
                return ImmutableList.of();
        }
    }

    private static ImmutableList<String> childrenAsString(MailboxMetaData.Children children) {
        switch (children) {
            case HAS_CHILDREN:
                return ImmutableList.of(ImapConstants.NAME_ATTRIBUTE_HAS_CHILDREN);
            case HAS_NO_CHILDREN:
                return ImmutableList.of(ImapConstants.NAME_ATTRIBUTE_HAS_NO_CHILDREN);
            case NO_INFERIORS:
                return ImmutableList.of(ImapConstants.NAME_ATTRIBUTE_NOINFERIORS);
            default:
                return ImmutableList.of();
        }
    }

    private static ImmutableList<String> mailboxAttributeAsString(MailboxType type) {
        String attributeName = type.getAttributeName();
        if (attributeName != null) {
            return ImmutableList.of(attributeName);
        }
        return ImmutableList.of();
    }

}
