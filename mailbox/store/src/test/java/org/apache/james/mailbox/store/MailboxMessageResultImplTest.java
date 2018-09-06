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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.Before;
import org.junit.Test;

public class MailboxMessageResultImplTest {
    private MessageResultImpl msgResultA;
    private MessageResultImpl msgResultACopy;
    private MessageResultImpl msgResultB;
    private MessageResultImpl msgResultC;

    /**
     * Initialize name instances
     */
    @Before
    public void initNames() throws Exception {
        Date dateAB = new Date();
        MailboxMessage msgA = buildMessage(MessageUid.of(100), dateAB);
        MailboxMessage msgB = buildMessage(MessageUid.of(100), dateAB);
        MailboxMessage msgC = buildMessage(MessageUid.of(200), new Date());
        
        msgResultA = new MessageResultImpl(msgA);
        msgResultACopy = new MessageResultImpl(msgA);
        msgResultB = new MessageResultImpl(msgB);
        msgResultC = new MessageResultImpl(msgC);
    }


    private MailboxMessage buildMessage(MessageUid uid, Date aDate) throws Exception {
        MessageBuilder builder = new MessageBuilder();
        builder.uid = uid;
        builder.internalDate = aDate;
        return builder.build();
    }


    @Test
    public void testEqualsNull() throws Exception {
        assertThat(msgResultA.equals(null)).isFalse();
    }


    @Test
    public void testEqualsReflexive() throws Exception {
        assertEquals(msgResultA, msgResultA);
    }


    @Test
    public void testCompareToReflexive() throws Exception {
        assertEquals(0, msgResultA.compareTo(msgResultA));
    }


    @Test
    public void testHashCodeReflexive() throws Exception {
        assertEquals(msgResultA.hashCode(), msgResultA.hashCode());
    }


    @Test
    public void testEqualsSymmetric() throws Exception {
        assertEquals(msgResultA, msgResultACopy);
        assertEquals(msgResultACopy, msgResultA);
    }


    @Test
    public void testHashCodeSymmetric() throws Exception {
        assertEquals(msgResultA.hashCode(), msgResultACopy.hashCode());
        assertEquals(msgResultACopy.hashCode(), msgResultA.hashCode());
    }


    @Test
    public void testEqualsTransitive() throws Exception {
        assertEquals(msgResultA, msgResultACopy);
        assertEquals(msgResultACopy, msgResultB);
        assertEquals(msgResultA, msgResultB);
    }


    @Test
    public void testCompareToTransitive() throws Exception {
        assertEquals(0, msgResultA.compareTo(msgResultACopy));
        assertEquals(0, msgResultACopy.compareTo(msgResultB));
        assertEquals(0, msgResultA.compareTo(msgResultB));
    }


    @Test
    public void testHashCodeTransitive() throws Exception {
        assertEquals(msgResultA.hashCode(), msgResultACopy.hashCode());
        assertEquals(msgResultACopy.hashCode(), msgResultB.hashCode());
        assertEquals(msgResultA.hashCode(), msgResultB.hashCode());
    }


    @Test
    public void testNotEqualDiffValue() throws Exception {
        assertThat(msgResultA.equals(msgResultC)).isFalse();
        assertThat(msgResultC.equals(msgResultA)).isFalse();
    }

    @Test
    public void testShouldReturnPositiveWhenFirstGreaterThanSecond()
            throws Exception {
        assertThat(msgResultC.compareTo(msgResultB) > 0).isTrue();
    }

    @Test
    public void testShouldReturnNegativeWhenFirstLessThanSecond()
            throws Exception {
        assertThat(msgResultB.compareTo(msgResultC) < 0).isTrue();
    }
}