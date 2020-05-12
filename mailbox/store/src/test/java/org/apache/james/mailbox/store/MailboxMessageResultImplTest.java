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

import java.util.Date;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailboxMessageResultImplTest {
    private MessageResultImpl msgResultA;
    private MessageResultImpl msgResultACopy;
    private MessageResultImpl msgResultB;
    private MessageResultImpl msgResultC;

    /**
     * Initialize name instances
     */
    @BeforeEach
    void initNames() throws Exception {
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
        return new MessageBuilder()
            .uid(uid)
            .internalDate(aDate)
            .build();
    }


    @Test
    void testEqualsNull() {
        assertThat(msgResultA).isNotNull();
    }


    @Test
    void testEqualsReflexive() {
        assertThat(msgResultA).isEqualTo(msgResultA);
    }


    @Test
    @SuppressWarnings("SelfComparison")
    void testCompareToReflexive() {
        assertThat(msgResultA.compareTo(msgResultA)).isEqualTo(0);
    }


    @Test
    void testHashCodeReflexive() {
        assertThat(msgResultA.hashCode()).isEqualTo(msgResultA.hashCode());
    }


    @Test
    void testEqualsSymmetric() {
        assertThat(msgResultACopy).isEqualTo(msgResultA);
        assertThat(msgResultA).isEqualTo(msgResultACopy);
    }


    @Test
    void testHashCodeSymmetric() {
        assertThat(msgResultACopy.hashCode()).isEqualTo(msgResultA.hashCode());
        assertThat(msgResultA.hashCode()).isEqualTo(msgResultACopy.hashCode());
    }


    @Test
    void testEqualsTransitive() {
        assertThat(msgResultACopy).isEqualTo(msgResultA);
        assertThat(msgResultB).isEqualTo(msgResultACopy);
        assertThat(msgResultB).isEqualTo(msgResultA);
    }


    @Test
    void testCompareToTransitive() {
        assertThat(msgResultA.compareTo(msgResultACopy)).isEqualTo(0);
        assertThat(msgResultACopy.compareTo(msgResultB)).isEqualTo(0);
        assertThat(msgResultA.compareTo(msgResultB)).isEqualTo(0);
    }


    @Test
    void testHashCodeTransitive() {
        assertThat(msgResultACopy.hashCode()).isEqualTo(msgResultA.hashCode());
        assertThat(msgResultB.hashCode()).isEqualTo(msgResultACopy.hashCode());
        assertThat(msgResultB.hashCode()).isEqualTo(msgResultA.hashCode());
    }


    @Test
    void testNotEqualDiffValue() {
        assertThat(msgResultA).isNotEqualTo(msgResultC);
        assertThat(msgResultC).isNotEqualTo(msgResultA);
    }

    @Test
    void testShouldReturnPositiveWhenFirstGreaterThanSecond() {
        assertThat(msgResultC.compareTo(msgResultB) > 0).isTrue();
    }

    @Test
    void testShouldReturnNegativeWhenFirstLessThanSecond() {
        assertThat(msgResultB.compareTo(msgResultC) < 0).isTrue();
    }
}