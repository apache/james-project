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

package org.apache.james.mailbox.cassandra.mail.utils;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.junit.Test;

public class SimpleMailboxACLJsonConverterTest {

    public class ACLMapBuilder {
        private Map<SimpleMailboxACL.MailboxACLEntryKey, MailboxACL.MailboxACLRights> map;

        public ACLMapBuilder() {
            map = new HashMap<>();
        }

        public ACLMapBuilder addSingleUserEntryToMap() {
            SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(false, true, true, true, false, true, false, true, true, true, true);
            SimpleMailboxACL.MailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("user", MailboxACL.NameType.user, true);
            map.put(key, rights);
            return this;
        }

        public ACLMapBuilder addSingleSpecialEntryToMap() {
            SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(false, false, true, true, false, true, false, true, false, true, true);
            SimpleMailboxACL.MailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("special", MailboxACL.NameType.special, true);
            map.put(key, rights);
            return this;
        }

        public ACLMapBuilder addSingleGroupEntryToMap() {
            SimpleMailboxACL.Rfc4314Rights rights = new SimpleMailboxACL.Rfc4314Rights(false, false, true, true, false, true, false, true, true, true, true);
            SimpleMailboxACL.MailboxACLEntryKey key = new SimpleMailboxACL.SimpleMailboxACLEntryKey("group", MailboxACL.NameType.group, true);
            map.put(key, rights);
            return this;
        }

        public MailboxACL buildAsACL() {
            return new SimpleMailboxACL(new HashMap<>(map));
        }

    }

    @Test
    public void emptyACLShouldBeWellSerialized() throws Exception {
        assertThatJson(SimpleMailboxACLJsonConverter.toJson(SimpleMailboxACL.EMPTY))
            .isEqualTo("{\"entries\":{}}")
            .when(IGNORING_ARRAY_ORDER);
    }

    @Test
    public void singleUserEntryACLShouldBeWellSerialized() throws Exception {
        assertThatJson(SimpleMailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleUserEntryToMap().buildAsACL()))
            .isEqualTo("{\"entries\":{\"-user\":2040}}")
            .when(IGNORING_ARRAY_ORDER);
    }

    @Test
    public void singleGroupEntryACLShouldBeWellSerialized() throws Exception {
        assertThatJson(SimpleMailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleGroupEntryToMap().buildAsACL()))
            .isEqualTo("{\"entries\":{\"-$group\":2032}}")
            .when(IGNORING_ARRAY_ORDER);
    }

    @Test
    public void singleSpecialEntryACLShouldBeWellSerialized() throws Exception {
        assertThatJson(SimpleMailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleSpecialEntryToMap().buildAsACL()))
            .isEqualTo("{\"entries\":{\"-special\":1968}}")
            .when(IGNORING_ARRAY_ORDER);
    }

    @Test
    public void multipleEntriesACLShouldBeWellSerialized() throws Exception {
        assertThatJson(SimpleMailboxACLJsonConverter.toJson(new ACLMapBuilder().addSingleUserEntryToMap().addSingleGroupEntryToMap().buildAsACL()))
            .isEqualTo("{\"entries\":{\"-user\":2040,\"-$group\":2032}}")
            .when(IGNORING_ARRAY_ORDER);
    }

    @Test
    public void emptyACLShouldBeWellDeSerialized() throws Exception {
        assertThat(SimpleMailboxACLJsonConverter.toACL("{\"entries\":{}}")).isEqualTo(SimpleMailboxACL.EMPTY);
    }

    @Test
    public void singleUserEntryACLShouldBeWellDeSerialized() throws Exception {
        assertThat(SimpleMailboxACLJsonConverter.toACL("{\"entries\":{\"-user\":2040}}"))
            .isEqualTo(new ACLMapBuilder().addSingleUserEntryToMap().buildAsACL());
    }

    @Test
    public void singleGroupEntryACLShouldBeWellDeSerialized() throws Exception {
        assertThat(SimpleMailboxACLJsonConverter.toACL("{\"entries\":{\"-$group\":2032}}"))
            .isEqualTo(new ACLMapBuilder().addSingleGroupEntryToMap().buildAsACL());
    }

    @Test
    public void multipleEntriesACLShouldBeWellDeSerialized() throws Exception {
        assertThat(SimpleMailboxACLJsonConverter.toACL("{\"entries\":{\"-user\":2040,\"-$group\":2032}}"))
            .isEqualTo(new ACLMapBuilder().addSingleUserEntryToMap().addSingleGroupEntryToMap().buildAsACL());
    }

}
