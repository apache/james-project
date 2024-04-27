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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.events.EventBus;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.metrics.api.MetricFactory;
import org.junit.Before;
import org.junit.Test;

public class AbstractSelectionProcessorTest {
    private SelectProcessor testee;

    @Before
    public void setUp() {
        MailboxManager mailboxManager = null;
        EventBus eventBus = null;
        StatusResponseFactory statusResponseFactory = null;
        MetricFactory metricFactory = null;
        testee = new SelectProcessor(mailboxManager, eventBus, statusResponseFactory, metricFactory);
    }

    @Test
    public void recomputeUidSetShouldNotFailWhenDataIsProvided() {
        IdRange knownSequences = new IdRange(1, 2);
        UidRange knownUids = new UidRange(MessageUid.of(1), MessageUid.of(2));
        UidRange uidSet = new UidRange(MessageUid.of(1), MessageUid.of(2));
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.uid(anyInt())).thenReturn(Optional.empty());

        testee.recomputeUidSet(asArray(knownSequences), asArray(knownUids), selectedMailbox, asArray(uidSet));
    }

    public static <T> T[] asArray(T... items) {
        return items;
    }

}