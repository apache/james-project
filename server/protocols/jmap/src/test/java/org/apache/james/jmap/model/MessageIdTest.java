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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class MessageIdTest {
    @Test(expected=NullPointerException.class)
    public void ofShouldThrowWhenNull() {
        String messageId = null;
        MessageId.of(messageId);
    }

    @Test(expected=IllegalArgumentException.class)
    public void ofShouldThrowWhenEmpty() {
        String messageId = "";
        MessageId.of(messageId);
    }

    @Test(expected=IllegalArgumentException.class)
    public void ofShouldThrowWhenTwoPartsMissing() {
        String messageId = "username";
        MessageId.of(messageId);
    }

    @Test(expected=IllegalArgumentException.class)
    public void ofShouldThrowWhenOnePartMissing() {
        String messageId = "username|mailboxpath";
        MessageId.of(messageId);
    }

    @Test(expected=NumberFormatException.class)
    public void ofShouldWorkWhenThirdPartIsNotANumber() {
        String messageId = "username|mailboxpath|thirdPart";
        MessageId.of(messageId);
    }

    @Test
    public void ofShouldWorkWhenMessageIdIsWellFormated() {
        String messageId = "username|mailboxpath|321";
        MessageId expected = new MessageId("username", "mailboxpath", 321);
        
        MessageId actual = MessageId.of(messageId);
        
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void ofShouldWorkWhenOnePartContainsAHyphen() {
        String messageId = "user-name|mailboxpath|321";
        MessageId expected = new MessageId("user-name", "mailboxpath", 321);
        
        MessageId actual = MessageId.of(messageId);
        
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void ofShouldWorkWhenSecondPartContainsAHyphen() {
        String messageId = "username|mailbox-path|321";
        MessageId expected = new MessageId("username", "mailbox-path", 321);
        
        MessageId actual = MessageId.of(messageId);
        
        assertThat(actual).isEqualTo(expected);
    }

}
