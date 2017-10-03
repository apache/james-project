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
package org.apache.james.jmap.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.Map;

import org.apache.james.jmap.model.mailbox.Rights;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class ObjectMapperFactoryTest {
    
    private ObjectMapperFactory testee;

    @Before
    public void setup() {
        testee = new ObjectMapperFactory(new InMemoryId.Factory(), new InMemoryMessageId.Factory());
    }

    @Test
    public void mailboxIdShouldBeDeserializable() throws Exception {
        String json = "{ \"mailboxId\": \"123\"}";
        MailboxIdTestContainer expected = new MailboxIdTestContainer(InMemoryId.of(123));
        MailboxIdTestContainer actual = testee.forParsing().readValue(json, MailboxIdTestContainer.class);
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void mailboxIdShouldBeSerializable() throws Exception {
        MailboxIdTestContainer container = new MailboxIdTestContainer(InMemoryId.of(123));
        String expectedJson = "{\"mailboxId\":\"123\"}";
        String actual = testee.forWriting().writeValueAsString(container);
        assertThat(actual).isEqualTo(expectedJson);
    }

    @Test
    public void mailboxIdShouldBeDeserializableWhenKey() throws Exception {
        String json = "{ \"map\": {\"123\": \"value\"}}";
        MailboxIdKeyTestContainer expected = new MailboxIdKeyTestContainer(ImmutableMap.of(InMemoryId.of(123), "value"));
        MailboxIdKeyTestContainer actual = testee.forParsing().readValue(json, MailboxIdKeyTestContainer.class);
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void mailboxIdShouldBeSerializableWhenKeyWithoutToString() throws Exception {
        ObjectMapperFactory testeeWithoutToString = new ObjectMapperFactory(new KeyWithoutToString.Factory(), new InMemoryMessageId.Factory());
        MailboxIdKeyTestContainer container = new MailboxIdKeyTestContainer(ImmutableMap.of(new KeyWithoutToString("key"), "value"));
        String expectedJson = "{\"map\":{\"key\":\"value\"}}";
        String actual = testeeWithoutToString.forWriting().writeValueAsString(container);
        assertThat(actual).isEqualTo(expectedJson);
    }

    @Test
    public void readValueShouldParseRightObject() throws Exception {
        Rights.Right actual = testee.forParsing()
            .readValue("\"a\"", Rights.Right.class);

        assertThat(actual)
            .isEqualTo(Rights.Right.Administer);
    }

    @Test
    public void readValueShouldParseUsernameObject() throws Exception {
        String username = "username";
        Rights.Username actual = testee.forParsing()
            .readValue("\"" + username + "\"", Rights.Username.class);

        assertThat(actual)
            .isEqualTo(new Rights.Username(username));
    }

    @Test
    public void readValueShouldParseRightsObject() throws Exception {
        String username = "username";
        Rights actual = testee.forParsing()
            .readValue("{\"" + username + "\" : [\"a\", \"e\"]}", Rights.class);

        assertThat(actual)
            .isEqualTo(Rights.builder()
                .delegateTo(new Rights.Username(username), Rights.Right.Administer, Rights.Right.Expunge)
                .build());
    }

    @Test
    public void readValueShouldRejectMultiCharacterRights() throws Exception {
        assertThatThrownBy(() ->
            testee.forParsing()
                .readValue("\"ae\"", Rights.Right.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void readValueShouldRejectUnsupportedRights() throws Exception {
        assertThatThrownBy(() ->
            testee.forParsing()
                .readValue("\"p\"", Rights.Right.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void readValueShouldRejectUnExistingRights() throws Exception {
        assertThatThrownBy(() ->
            testee.forParsing()
                .readValue("\"z\"", Rights.Right.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    public static class MailboxIdTestContainer {
        public MailboxId mailboxId;

        public MailboxIdTestContainer() {
        }

        public MailboxIdTestContainer(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
        }
    }

    public static class MailboxIdKeyTestContainer {
        public Map<MailboxId, String> map;

        public MailboxIdKeyTestContainer() {
        }

        public MailboxIdKeyTestContainer(Map<MailboxId, String> map) {
            this.map = map;
        }
    }

    public static class KeyWithoutToString implements MailboxId, Serializable {
        private String value;

        public KeyWithoutToString(String value) {
            this.value = value;
        }

        @Override
        public String serialize() {
            return value;
        }

        public static class Factory implements MailboxId.Factory {

            @Override
            public MailboxId fromString(String serialized) {
                return new KeyWithoutToString(serialized);
            }
            
        }
    }
}
