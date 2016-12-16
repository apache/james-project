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

package org.apache.james.mailbox.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;

public class MailboxEventDispatcherTest {
    private static final int sessionId = 10;

    private MailboxEventDispatcher dispatcher;

    private EventCollector collector;

    private MessageResult result;

    private Mailbox mailbox;

    private MailboxSession session = new MockMailboxSession("test") {
        @Override
        public long getSessionId() {
            return sessionId;
        }
    };

    @Before
    public void setUp() throws Exception {
        collector = new EventCollector();

        dispatcher = MailboxEventDispatcher.ofListener(collector);
        result = mock(MessageResult.class);
        mailbox = mock(Mailbox.class);

        when(result.getUid()).thenReturn(MessageUid.of(23));
        when(mailbox.getNamespace()).thenReturn("namespace");
        when(mailbox.getUser()).thenReturn("user");
        when(mailbox.getName()).thenReturn("name");
    }


    @Test
    public void testShouldReturnNoChangesWhenSystemFlagsUnchanged() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1,  new Flags(
                Flags.Flag.DELETED), new Flags(Flags.Flag.DELETED))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowAnsweredAdded() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(),
                new Flags(Flags.Flag.ANSWERED))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.ANSWERED, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowAnsweredRemoved() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(
                Flags.Flag.ANSWERED), new Flags())));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.ANSWERED, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowDeletedAdded() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(),
                new Flags(Flags.Flag.DELETED))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.DELETED, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowDeletedRemoved() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(
                Flags.Flag.DELETED), new Flags())));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.DELETED, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowDraftAdded() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(),
                new Flags(Flags.Flag.DRAFT))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.DRAFT, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowDraftRemoved() {
        dispatcher.flagsUpdated(session,Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(
                Flags.Flag.DRAFT), new Flags())));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.DRAFT, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowFlaggedAdded() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(),
                new Flags(Flags.Flag.FLAGGED))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.FLAGGED, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowFlaggedRemoved() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(
                Flags.Flag.FLAGGED), new Flags())));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.FLAGGED, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowRecentAdded() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(),
                new Flags(Flags.Flag.RECENT))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.RECENT, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowRecentRemoved() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(
                Flags.Flag.RECENT), new Flags())));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.RECENT, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowSeenAdded() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(),
                new Flags(Flags.Flag.SEEN))));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.SEEN, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowSeenRemoved() {
        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, new Flags(
                Flags.Flag.SEEN), new Flags())));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.SEEN, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testShouldShowMixedChanges() {
        Flags originals = new Flags();
        originals.add(Flags.Flag.DRAFT);
        originals.add(Flags.Flag.RECENT);
        Flags updated = new Flags();
        updated.add(Flags.Flag.ANSWERED);
        updated.add(Flags.Flag.DRAFT);
        updated.add(Flags.Flag.SEEN);

        dispatcher.flagsUpdated(session, Arrays.asList(result.getUid()), mailbox, Arrays.asList(new UpdatedFlags(result.getUid(), -1, originals, updated)));
        assertEquals(1, collector.getEvents().size());
        assertTrue(collector.getEvents().get(0) instanceof MailboxListener.FlagsUpdated);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        Iterator<Flags.Flag> iterator = event.getUpdatedFlags().get(0).systemFlagIterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.ANSWERED, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.RECENT, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(Flags.Flag.SEEN, iterator.next());
        assertFalse(iterator.hasNext());
    }
}
