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

package org.apache.james.mailbox.store.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mailbox.model.MailboxACL.Right.CreateMailbox;
import static org.apache.james.mailbox.model.MailboxACL.Right.DeleteMailbox;
import static org.apache.james.mailbox.model.MailboxACL.Right.DeleteMessages;
import static org.apache.james.mailbox.model.MailboxACL.Right.Lookup;
import static org.apache.james.mailbox.model.MailboxACL.Right.Post;
import static org.apache.james.mailbox.model.MailboxACL.Right.Read;
import static org.apache.james.mailbox.model.MailboxACL.Right.Write;
import static org.apache.james.mailbox.model.MailboxACL.Right.WriteSeenFlag;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.junit.jupiter.api.Test;

import net.javacrumbs.jsonunit.core.Option;

class MailboxACLJsonConverterTest {

    class ACLMapBuilder {
        final Map<EntryKey, Rfc4314Rights> map;

        ACLMapBuilder() {
            map = new LinkedHashMap<>();
        }

        ACLMapBuilder addSingleUserEntryToMap() {
            Rfc4314Rights rights = new Rfc4314Rights(CreateMailbox, DeleteMailbox, DeleteMessages, Lookup, Post, Read, WriteSeenFlag, Write);
            EntryKey key = new EntryKey("user", NameType.user, true);
            map.put(key, rights);
            return this;
        }

        ACLMapBuilder addSingleSpecialEntryToMap() {
            Rfc4314Rights rights = new Rfc4314Rights(DeleteMailbox, DeleteMessages, Lookup, Post, Write, WriteSeenFlag);
            EntryKey key = new EntryKey("special", NameType.special, true);
            map.put(key, rights);
            return this;
        }

        ACLMapBuilder addSingleGroupEntryToMap() {
            Rfc4314Rights rights = new Rfc4314Rights(DeleteMailbox, DeleteMessages, Lookup, Post, Read, Write, WriteSeenFlag);
            EntryKey key = new EntryKey("group", NameType.group, true);
            map.put(key, rights);
            return this;
        }

        MailboxACL buildAsACL() {
            return new MailboxACL(new HashMap<>(map));
        }

    }

    @Test
    void emptyACLShouldBeWellSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toJson(MailboxACL.EMPTY))
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{\"entries\":{}}");
    }

    @Test
    void singleUserEntryACLShouldBeWellSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleUserEntryToMap().buildAsACL()))
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{\"entries\":{\"-user\":\"klprstwx\"}}");
    }

    @Test
    void singleGroupEntryACLShouldBeWellSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleGroupEntryToMap().buildAsACL()))
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{\"entries\":{\"-$group\":\"lprstwx\"}}");
    }

    @Test
    void singleSpecialEntryACLShouldBeWellSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleSpecialEntryToMap().buildAsACL()))
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{\"entries\":{\"-special\":\"lpstwx\"}}");
    }

    @Test
    void multipleEntriesACLShouldBeWellSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleUserEntryToMap().addSingleGroupEntryToMap().buildAsACL()))
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("{\"entries\":{\"-user\":\"klprstwx\",\"-$group\":\"lprstwx\"}}");
    }

    @Test
    void emptyACLShouldBeWellDeSerialized() throws Exception {
        assertThat(MailboxACLJsonConverter.toACL("{\"entries\":{}}")).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void singleUserEntryACLShouldBeWellDeSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toACL("{\"entries\":{\"-user\":\"klprstwx\"}}"))
            .isEqualTo(new ACLMapBuilder().addSingleUserEntryToMap().buildAsACL());
    }

    @Test
    void singleGroupEntryACLShouldBeWellDeSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toACL("{\"entries\":{\"-$group\":\"lprstwx\"}}"))
            .isEqualTo(new ACLMapBuilder().addSingleGroupEntryToMap().buildAsACL());
    }

    @Test
    void multipleEntriesACLShouldBeWellDeSerialized() throws Exception {
        assertThatJson(MailboxACLJsonConverter.toACL("{\"entries\":{\"-user\":\"klprstwx\",\"-$group\":\"lprstwx\"}}"))
            .isEqualTo(new ACLMapBuilder().addSingleUserEntryToMap().addSingleGroupEntryToMap().buildAsACL());
    }

}
