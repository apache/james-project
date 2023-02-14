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

import static org.apache.james.imap.api.ImapConstants.LIST_COMMAND;
import static org.apache.james.imap.api.ImapConstants.XLIST_COMMAND;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.imap.message.response.XListResponse;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxMetaData.Children;
import org.apache.james.mailbox.model.MailboxMetaData.Selectability;
import org.junit.jupiter.api.Test;

public class ListingEncodingUtilsTest  {

    final String nameParameter = "mailbox";
    
    private ByteImapResponseWriter writer = new ByteImapResponseWriter();
    private ImapResponseComposer composer = new ImapResponseComposerImpl(writer);

    @Test
    void encodeShouldWriteNilDelimiterWhenUnassigned() throws Exception {
        ListResponse input = new ListResponse(Children.HAS_CHILDREN, Selectability.NONE, nameParameter, ((char) Character.UNASSIGNED), false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\HasChildren) NIL \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldWriteAnyDelimiter() throws Exception {
        ListResponse input = new ListResponse(Children.HAS_CHILDREN, Selectability.NONE, nameParameter, '#', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\HasChildren) \"#\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldNotIncludeAttributeWhenNone() throws Exception {
        ListResponse input = new ListResponse(Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.NONE, nameParameter, '.', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST () \".\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldAddHasChildrenToAttributes() throws Exception {
        ListResponse input = new ListResponse(Children.HAS_CHILDREN, Selectability.NONE, nameParameter, '.', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\HasChildren) \".\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldAddHasNoChildrenToAttributes() throws Exception {
        ListResponse input = new ListResponse(Children.HAS_NO_CHILDREN, Selectability.NONE, nameParameter, '.', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\HasNoChildren) \".\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldAddSeveralAttributes() throws Exception {
        ListResponse input = new ListResponse(Children.NO_INFERIORS, Selectability.NOSELECT, nameParameter, '.', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\Noselect \\Noinferiors) \".\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldAddMarkedAttribute() throws Exception {
        ListResponse input = new ListResponse(Children.CHILDREN_ALLOWED_BUT_UNKNOWN, Selectability.MARKED, nameParameter, '.', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\Marked) \".\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldAddUnmarkedAttribute() throws Exception {
        ListResponse input = new ListResponse(Children.CHILDREN_ALLOWED_BUT_UNKNOWN, Selectability.UNMARKED, nameParameter, '.', false, false, EnumSet.noneOf(ListResponse.ChildInfo.class), MailboxType.OTHER);

        ListingEncodingUtils.encodeListingResponse(LIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* LIST (\\Unmarked) \".\" \"mailbox\"\r\n");
    }

    @Test
    void encodeShouldAddXListAttributeWhenTypeIsInbox() throws Exception {
        XListResponse input = new XListResponse(Children.HAS_CHILDREN, Selectability.NONE, nameParameter, '.', MailboxType.INBOX);

        ListingEncodingUtils.encodeListingResponse(XLIST_COMMAND, composer, input);
        composer.flush();
        assertThat(writer.getString()).isEqualTo("* XLIST (\\HasChildren \\Inbox) \".\" \"mailbox\"\r\n");
    }
}
