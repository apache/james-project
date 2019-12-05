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
package org.apache.james.mailbox.store.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.Test;

class SearchUtilTest {

    @Test
    void testSimpleSubject() {
        String subject = "This is my subject";
        assertThat(SearchUtil.getBaseSubject(subject)).isEqualTo(subject);
    }
    
    @Test
    void testReplaceSpacesAndTabsInSubject() {
        String subject = "This   is my\tsubject";
        assertThat(SearchUtil.getBaseSubject(subject)).isEqualTo("This is my subject");
    }
    
    @Test
    void testRemoveTrailingSpace() {
        String subject = "This is my subject ";
        assertThat(SearchUtil.getBaseSubject(subject)).isEqualTo("This is my subject");
    }
    
    
    @Test
    void testRemoveTrailingFwd() {
        String subject = "This is my subject (fwd)";
        assertThat(SearchUtil.getBaseSubject(subject)).isEqualTo("This is my subject");
    }
    

    @Test
    void testSimpleExtraction() {
        String expectedSubject = "Test";
        assertThat(SearchUtil.getBaseSubject("Re: Test")).isEqualTo(expectedSubject);
        assertThat(SearchUtil.getBaseSubject("re: Test")).isEqualTo(expectedSubject);
        assertThat(SearchUtil.getBaseSubject("Fwd: Test")).isEqualTo(expectedSubject);
        assertThat(SearchUtil.getBaseSubject("fwd: Test")).isEqualTo(expectedSubject);
        assertThat(SearchUtil.getBaseSubject("Fwd: Re: Test")).isEqualTo(expectedSubject);
        assertThat(SearchUtil.getBaseSubject("Fwd: Re: Test (fwd)")).isEqualTo(expectedSubject);
    }
  
    @Test
    void testComplexExtraction() {
        assertThat(SearchUtil.getBaseSubject("Re: re:re: fwd:[fwd: \t  Test]  (fwd)  (fwd)(fwd) ")).isEqualTo("Test");
    }
    
    @Test
    void getMessageIdIfSupportedByUnderlyingStorageOrNullForNullMessageIdShouldReturnNull() {
        //given
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.getMessageId())
            .thenReturn(null);
        
        //when
        String serialiazedMessageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message);
        
        //expect
        assertThat(serialiazedMessageId).isNull();
    }

    @Test
    void getSerializedMessageIdIfSupportedByUnderlyingStorageOrNullForMessageIdThatSerializeReturnNullShouldReturnNull() {
        //given
        MessageId invalidMessageIdThatReturnNull = mock(MessageId.class);
        when(invalidMessageIdThatReturnNull.serialize())
            .thenReturn(null);

        MailboxMessage message = mock(MailboxMessage.class);
        when(message.getMessageId())
            .thenReturn(invalidMessageIdThatReturnNull);

        //when
        String serialiazedMessageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message);

        //expect
        assertThat(serialiazedMessageId).isNull();
    }

    @Test
    void getSerializedMessageIdIfSupportedByUnderlyingStorageOrNullForValidMessageIdShouldReturnSerializedId() {
        //given
        String messageIdString = "http://www.labraxeenne.com/#/";
        MessageId messageId = mock(MessageId.class);
        when(messageId.serialize()).thenReturn(messageIdString);
        when(messageId.isSerializable()).thenReturn(true);

        MailboxMessage message = mock(MailboxMessage.class);
        when(message.getMessageId())
            .thenReturn(messageId);

        //when
        String serialiazedMessageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message);

        //expect
        assertThat(messageIdString).isEqualTo(serialiazedMessageId);
    }

    @Test
    void getSerializedMessageIdIfSupportedByUnderlyingStorageOrNullForValidMessageIdShouldReturnNullWhenNotSupported() {
        //given
        MessageId messageId = mock(MessageId.class);
        when(messageId.isSerializable()).thenReturn(false);

        MailboxMessage message = mock(MailboxMessage.class);
        when(message.getMessageId())
            .thenReturn(messageId);

        //when
        String serialiazedMessageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message);

        //expect
        assertThat(serialiazedMessageId).isNull();
    }

}
