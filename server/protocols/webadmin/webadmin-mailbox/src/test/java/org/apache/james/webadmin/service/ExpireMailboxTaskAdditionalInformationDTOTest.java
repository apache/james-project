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

package org.apache.james.webadmin.service;

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class ExpireMailboxTaskAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    
    private static final ExpireMailboxTask.AdditionalInformation LEGACY_DOMAIN_OBJECT = new ExpireMailboxTask.AdditionalInformation(
        INSTANT, new ExpireMailboxService.RunningOptions(1, MailboxConstants.INBOX,  Optional.empty(), Optional.empty(), false, Optional.of("90d")), 5, 2, 10, 234);
    private static final ExpireMailboxTask.AdditionalInformation DOMAIN_OBJECT = new ExpireMailboxTask.AdditionalInformation(
        INSTANT, new ExpireMailboxService.RunningOptions(1, MailboxConstants.INBOX,  Optional.of(true), Optional.of("user"), false, Optional.of("90d")), 5, 2, 10, 234);
    private static final ExpireMailboxTask.AdditionalInformation DOMAIN_OBJECT_2 = new ExpireMailboxTask.AdditionalInformation(
        INSTANT, new ExpireMailboxService.RunningOptions(1, MailboxConstants.INBOX,  Optional.of(false), Optional.empty(), true, Optional.of("90d")), 5, 2, 10, 234);

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(ExpireMailboxAdditionalInformationDTO.module())
            .bean(DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/expireMailbox.additionalInformation.v2.json"))
            .verify();
    }
    
    @Test
    void shouldMatchJsonSerializationContract2() throws Exception {
        JsonSerializationVerifier.dtoModule(ExpireMailboxAdditionalInformationDTO.module())
            .bean(DOMAIN_OBJECT_2)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/expireMailbox.additionalInformation.v2.1.json"))
            .verify();
    }

    @Test
    void shouldDeserializeLegacy() throws Exception {
        ExpireMailboxTask.AdditionalInformation actual = JsonGenericSerializer
            .forModules(ExpireMailboxAdditionalInformationDTO.module())
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/expireMailbox.additionalInformation.json"));

        assertThat(actual)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(LEGACY_DOMAIN_OBJECT);
    }
}
