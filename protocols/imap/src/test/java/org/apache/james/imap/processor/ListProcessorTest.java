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

package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.SimpleMailboxMetaData;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;

public class ListProcessorTest  {

    MailboxPath inboxPath = new MailboxPath("", "", "INBOX");
    ListProcessor processor;

    @Before
    public void setUp() throws Exception {
        StatusResponseFactory serverResponseFactory = null;
        ImapProcessor next = null;
        MailboxManager manager = null;
        processor = new ListProcessor(next, manager, serverResponseFactory, new NoopMetricFactory());
    }

    @Test
    public void convertHasChildrenShouldHaveHasChildrenFlagOnly() throws Exception {
        ListResponse actual = processor.convertMetadataToListResponse(false,
            new SimpleMailboxMetaData(inboxPath, null, '.', MailboxMetaData.Children.HAS_CHILDREN, MailboxMetaData.Selectability.NONE),
            MailboxType.OTHER);
        assertThat(actual).isEqualTo(
            new ListResponse(
                false,
                false,
                false,
                false,
                true,
                false,
                inboxPath.getFullName('.'),
                '.')
        );
    }

    @Test
    public void convertHasNoChildrenShouldHaveHasNoChildrenFlagOnly() throws Exception {
        ListResponse actual = processor.convertMetadataToListResponse(false,
            new SimpleMailboxMetaData(inboxPath, null, '.', MailboxMetaData.Children.HAS_NO_CHILDREN, MailboxMetaData.Selectability.NONE),
            MailboxType.OTHER);
        assertThat(actual).isEqualTo(
            new ListResponse(
                false,
                false,
                false,
                false,
                false,
                true,
                inboxPath.getFullName('.'),
                '.')
        );
    }
    
    @Test
    public void convertNoInferiorShouldHaveNoInferiorFlagOnly() throws Exception {
        ListResponse actual = processor.convertMetadataToListResponse(false,
            new SimpleMailboxMetaData(inboxPath, null, '.', MailboxMetaData.Children.NO_INFERIORS, MailboxMetaData.Selectability.NONE),
            MailboxType.OTHER);
        assertThat(actual).isEqualTo(
            new ListResponse(
                true,
                false,
                false,
                false,
                false,
                false,
                inboxPath.getFullName('.'),
                '.')
        );
    }

    @Test
    public void convertNoSelectUnknownChildrenShouldHaveNoSelectFlagOnly() throws Exception {
        ListResponse actual = processor.convertMetadataToListResponse(false,
            new SimpleMailboxMetaData(inboxPath, null, '.', MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.NOSELECT),
            MailboxType.OTHER);
        assertThat(actual).isEqualTo(
            new ListResponse(
                false,
                true,
                false,
                false,
                false,
                false,
                inboxPath.getFullName('.'),
                '.')
        );
    }

    @Test
    public void convertUnmarkedUnknownChildrenShouldHaveUnmarkedFlagOnly() throws Exception {
        ListResponse actual = processor.convertMetadataToListResponse(false,
            new SimpleMailboxMetaData(inboxPath, null, '.', MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.UNMARKED),
            MailboxType.OTHER);
        assertThat(actual).isEqualTo(
            new ListResponse(
                false,
                false,
                false,
                true,
                false,
                false,
                inboxPath.getFullName('.'),
                '.')
        );
    }

    @Test
    public void convertMarkedUnknownChildrenShouldHaveMarkedFlagOnly() throws Exception {
        ListResponse actual = processor.convertMetadataToListResponse(false,
            new SimpleMailboxMetaData(inboxPath, null, '.', MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN, MailboxMetaData.Selectability.MARKED),
            MailboxType.OTHER);
        assertThat(actual).isEqualTo(
            new ListResponse(
                false,
                false,
                true,
                false,
                false,
                false,
                inboxPath.getFullName('.'),
                '.')
        );
    }
}
