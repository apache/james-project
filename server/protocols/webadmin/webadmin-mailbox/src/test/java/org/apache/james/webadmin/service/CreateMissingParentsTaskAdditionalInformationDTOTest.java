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

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class CreateMissingParentsTaskAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final CreateMissingParentsTask.AdditionalInformation DOMAIN_OBJECT = new CreateMissingParentsTask.AdditionalInformation(
         INSTANT, ImmutableSet.of(TestId.of(1).serialize()), 1L, ImmutableSet.of(), 0L);

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(CreateMissingParentsTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/createMissingParents.additionalInformation.json"))
            .verify();
    }
}
