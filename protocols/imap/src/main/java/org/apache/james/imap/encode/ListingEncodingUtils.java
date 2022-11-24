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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.apache.james.imap.api.ImapConstants.NAME_ATTRIBUTE_NON_EXISTENT;
import static org.apache.james.imap.api.ImapConstants.NAME_ATTRIBUTE_SUBSCRIBED;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.message.response.AbstractListingResponse;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.model.MailboxMetaData;

import com.google.common.collect.ImmutableList;

public class ListingEncodingUtils {
    public static final byte[] NAME_CHILDINFO_EXTENDED = "\"CHILDINFO\"".getBytes(US_ASCII);
    public static final byte[] NAME_CHILDINFO_SUBSCRIBED = "\"SUBSCRIBED\"".getBytes(US_ASCII);

    public static void encodeListingResponse(ImapCommand command, ImapResponseComposer composer, AbstractListingResponse response) throws IOException {
        composer.untagged();
        composer.message(command.getNameAsBytes());
        composer.openParen();
        for (byte[] attribute : getNameAttributes(response)) {
            composer.message(attribute);
        }
        composer.closeParen();
        writeDelimiter(composer, response.getHierarchyDelimiter());
        composer.mailbox(response.getName());

        if (response instanceof ListResponse) {
            writeChildInfos(returnChildInfosAsString(((ListResponse) response).getChildInfos()), composer);
        }
        composer.end();
    }

    private static void writeDelimiter(ImapResponseComposer composer, char hierarchyDelimiter) throws IOException {
        if (hierarchyDelimiter == Character.UNASSIGNED) {
            composer.nil();
        } else {
            composer.quote(hierarchyDelimiter);
        }
    }

    private static void writeChildInfos(ImmutableList<byte[]> childInfos, ImapResponseComposer composer) throws IOException {
        if (!childInfos.isEmpty()) {
            composer.openParen();
            composer.message(NAME_CHILDINFO_EXTENDED);
            composer.openParen();
            for (byte[] childInfo : childInfos) {
                composer.message(childInfo);
            }
            composer.closeParen();
            composer.closeParen();
        }
    }

    private static ImmutableList<byte[]> getNameAttributes(AbstractListingResponse response) {
        ImmutableList.Builder<byte[]> builder = ImmutableList.builder();

        selectabilityAsString(response.getSelectability(), builder);
        childrenAsString(response.getChildren(), builder);
        mailboxAttributeAsString(response.getType(), builder);

        if (response instanceof ListResponse) {
            ListResponse listResponse = (ListResponse) response;
            returnSubscribedAsString(listResponse.isReturnSubscribed(), builder);
            returnNonExistentAsString(listResponse.isReturnNonExistent(), builder);
        }

        return builder.build();
    }


    private static ImmutableList.Builder<byte[]> selectabilityAsString(MailboxMetaData.Selectability selectability, ImmutableList.Builder<byte[]> builder) {
        switch (selectability) {
            case MARKED:
                return builder.add(ImapConstants.NAME_ATTRIBUTE_MARKED);
            case NOSELECT:
                return builder.add(ImapConstants.NAME_ATTRIBUTE_NOSELECT);
            case UNMARKED:
                return builder.add(ImapConstants.NAME_ATTRIBUTE_UNMARKED);
            default:
                return builder;
        }
    }

    private static ImmutableList.Builder<byte[]> childrenAsString(MailboxMetaData.Children children, ImmutableList.Builder<byte[]> builder) {
        switch (children) {
            case HAS_CHILDREN:
                return builder.add(ImapConstants.NAME_ATTRIBUTE_HAS_CHILDREN);
            case HAS_NO_CHILDREN:
                return builder.add(ImapConstants.NAME_ATTRIBUTE_HAS_NO_CHILDREN);
            case NO_INFERIORS:
                return builder.add(ImapConstants.NAME_ATTRIBUTE_NOINFERIORS);
            default:
                return builder;
        }
    }

    private static ImmutableList.Builder<byte[]> mailboxAttributeAsString(MailboxType type, ImmutableList.Builder<byte[]> builder) {
        String attributeName = type.getAttributeName();
        if (attributeName != null) {
            return builder.add(attributeName.getBytes(StandardCharsets.US_ASCII));
        }
        return builder;
    }

    private static ImmutableList.Builder<byte[]> returnSubscribedAsString(boolean returnSubscribed, ImmutableList.Builder<byte[]> builder) {
        if (returnSubscribed) {
            return builder.add(NAME_ATTRIBUTE_SUBSCRIBED);
        }
        return builder;
    }

    private static ImmutableList.Builder<byte[]> returnNonExistentAsString(boolean returnNonExistent, ImmutableList.Builder<byte[]> builder) {
        if (returnNonExistent) {
            return builder.add(NAME_ATTRIBUTE_NON_EXISTENT);
        }
        return builder;
    }

    private static ImmutableList<byte[]> returnChildInfosAsString(EnumSet<ListResponse.ChildInfo> childInfos) {
        ImmutableList.Builder<byte[]> builder = ImmutableList.builder();
        if (childInfos != null && !childInfos.isEmpty()) {
            childInfos.forEach(childInfo -> {
                if (childInfo == ListResponse.ChildInfo.SUBSCRIBED) {
                    builder.add(NAME_CHILDINFO_SUBSCRIBED);
                }
            });
        }
        return builder.build();
    }
}
