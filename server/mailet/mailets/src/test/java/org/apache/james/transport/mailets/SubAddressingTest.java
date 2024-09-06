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

package org.apache.james.transport.mailets;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.apache.james.transport.mailets.WithStorageDirectiveTest.NO_DOMAIN_LIST;
import static org.assertj.core.api.Assertions.assertThat;

public class SubAddressingTest {
    @Test
    void shouldAddStorageDirectiveMatchingDetails() throws Exception {
        SubAddressing testee = new SubAddressing(MemoryUsersRepository.withVirtualHosting(NO_DOMAIN_LIST));
        testee.init(FakeMailetConfig.builder()
            .build());

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipient("recipient1+any@localhost")
            .build();

        testee.service(mail);

        AttributeName recipient1 = AttributeName.of("DeliveryPaths_recipient1@localhost");
        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(recipient1, "any"));
    }

    Pair<AttributeName, String> unbox(Attribute attribute) {
        Collection<AttributeValue> collection = (Collection<AttributeValue>) attribute.getValue().getValue();
        return Pair.of(attribute.getName(), (String) collection.stream().findFirst().get().getValue());
    }
}
