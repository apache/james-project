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

import org.apache.james.core.Username;
import org.apache.james.jmap.model.mailbox.Rights;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mdn.action.mode.DispositionActionMode;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mdn.type.DispositionType;
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
        Username actual = testee.forParsing()
            .readValue("\"" + username + "\"", Username.class);

        assertThat(actual)
            .isEqualTo(Username.of(username));
    }

    @Test
    public void readValueShouldParseActionModeWhenAutomatic() throws Exception {
        DispositionActionMode actual = testee.forParsing()
            .readValue("\"" + DispositionActionMode.Automatic.getValue() + "\"",
                DispositionActionMode.class);

        assertThat(actual)
            .isEqualTo(DispositionActionMode.Automatic);
    }

    @Test
    public void readValueShouldParseActionModeWhenManual() throws Exception {
        DispositionActionMode actual = testee.forParsing()
            .readValue("\"" + DispositionActionMode.Manual.getValue() + "\"",
                DispositionActionMode.class);

        assertThat(actual)
            .isEqualTo(DispositionActionMode.Manual);
    }

    @Test
    public void readValueShouldFailOnInvalidActionMode() {
        assertThatThrownBy(() -> testee.forParsing()
            .readValue("\"illegal\"",
                DispositionActionMode.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unrecognized MDN Disposition action mode illegal. Should be one of [manual-action, automatic-action]");
    }

    @Test
    public void readValueShouldParseSendingModeWhenAutomatic() throws Exception {
        DispositionSendingMode actual = testee.forParsing()
            .readValue("\"" + DispositionSendingMode.Automatic.getValue() + "\"",
                DispositionSendingMode.class);

        assertThat(actual)
            .isEqualTo(DispositionSendingMode.Automatic);
    }

    @Test
    public void readValueShouldParseSendingModeWhenManual() throws Exception {
        DispositionSendingMode actual = testee.forParsing()
            .readValue("\"" + DispositionSendingMode.Manual.getValue() + "\"",
                DispositionSendingMode.class);

        assertThat(actual)
            .isEqualTo(DispositionSendingMode.Manual);
    }

    @Test
    public void readValueShouldFailOnInvalidSendingMode() {
        assertThatThrownBy(() -> testee.forParsing()
            .readValue("\"illegal\"",
                DispositionSendingMode.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unrecognized MDN Disposition sending mode illegal. Should be one of [MDN-sent-manually, MDN-sent-automatically]");
    }

    @Test
    public void readValueShouldParseSendingModeWhenDeleted() throws Exception {
        DispositionType actual = testee.forParsing()
            .readValue("\"" + DispositionType.Deleted.getValue() + "\"",
                DispositionType.class);

        assertThat(actual)
            .isEqualTo(DispositionType.Deleted);
    }

    @Test
    public void readValueShouldParseSendingModeWhenDispatched() throws Exception {
        DispositionType actual = testee.forParsing()
            .readValue("\"" + DispositionType.Dispatched.getValue() + "\"",
                DispositionType.class);

        assertThat(actual)
            .isEqualTo(DispositionType.Dispatched);
    }

    @Test
    public void readValueShouldParseSendingModeWhenDisplayed() throws Exception {
        DispositionType actual = testee.forParsing()
            .readValue("\"" + DispositionType.Displayed.getValue() + "\"",
                DispositionType.class);

        assertThat(actual)
            .isEqualTo(DispositionType.Displayed);
    }

    @Test
    public void readValueShouldParseSendingModeWhenProcessed() throws Exception {
        DispositionType actual = testee.forParsing()
            .readValue("\"" + DispositionType.Processed.getValue() + "\"",
                DispositionType.class);

        assertThat(actual)
            .isEqualTo(DispositionType.Processed);
    }

    @Test
    public void readValueShouldFailOnInvalidDispositionType() {
        assertThatThrownBy(() -> testee.forParsing()
            .readValue("\"illegal\"",
                DispositionType.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unrecognized MDN Disposition type illegal. Should be one of [deleted, dispatched, displayed, processed]");
    }

    @Test
    public void readValueShouldParseRightsObject() throws Exception {
        String username = "username";
        Rights actual = testee.forParsing()
            .readValue("{\"" + username + "\" : [\"a\", \"e\"]}", Rights.class);

        assertThat(actual)
            .isEqualTo(Rights.builder()
                .delegateTo(Username.of(username), Rights.Right.Administer, Rights.Right.Expunge)
                .build());
    }

    @Test
    public void readValueShouldRejectMultiCharacterRights() {
        assertThatThrownBy(() ->
            testee.forParsing()
                .readValue("\"ae\"", Rights.Right.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void readValueShouldRejectUnsupportedRights() {
        assertThatThrownBy(() ->
            testee.forParsing()
                .readValue("\"p\"", Rights.Right.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void readValueShouldRejectUnExistingRights() {
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
