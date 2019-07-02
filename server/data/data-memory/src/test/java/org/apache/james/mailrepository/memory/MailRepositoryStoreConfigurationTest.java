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

package org.apache.james.mailrepository.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.mailrepository.api.Protocol;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MailRepositoryStoreConfigurationTest {
    @Test
    void defaultProtocolShouldReturnEmptyWhenEmpty() {
        Optional<Protocol> defaultProtocol = MailRepositoryStoreConfiguration.computeDefaultProtocol(ImmutableList.of());

        assertThat(defaultProtocol).isEmpty();
    }

    @Test
    void defaultProtocolShouldReturnEmptyWhenEmptyItems() {
        Optional<Protocol> defaultProtocol = MailRepositoryStoreConfiguration.computeDefaultProtocol(ImmutableList.of(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(),
                "class.fqdn",
                new BaseHierarchicalConfiguration())));

        assertThat(defaultProtocol).isEmpty();
    }

    @Test
    void defaultProtocolShouldReturnFirstConfiguredProtocol() {
        Optional<Protocol> defaultProtocol = MailRepositoryStoreConfiguration.computeDefaultProtocol(ImmutableList.of(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("1"), new Protocol("2")),
                "class.fqdn",
                new BaseHierarchicalConfiguration())));

        assertThat(defaultProtocol).contains(new Protocol("1"));
    }

    @Test
    void defaultProtocolShouldSkipItemsWithNoProtocols() {
        Optional<Protocol> defaultProtocol = MailRepositoryStoreConfiguration.computeDefaultProtocol(ImmutableList.of(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(),
                "class.fqdn",
                new BaseHierarchicalConfiguration()),
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("1")),
                "class.fqdn",
                new BaseHierarchicalConfiguration())));

        assertThat(defaultProtocol).contains(new Protocol("1"));
    }
}
