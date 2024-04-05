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

package org.apache.james.jmap.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

public class SentByJmapTest {

    private SentByJmap testee;

    @Before
    public void setUp() throws Exception {
        testee = new SentByJmap();
        testee.init(FakeMatcherConfig.builder().matcherName("matcherName")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
    }

    @Test
    public void matchShouldReturnRecipientsWhenUserAttributeIsPresent() throws Exception {
        MailAddress recipient = MailAddressFixture.ANY_AT_JAMES;
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipient(recipient)
            .attribute(new Attribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of("true")))
            .build();

        Collection<MailAddress> results =  testee.match(fakeMail);

        assertThat(results).containsOnly(recipient);
    }

    @Test
    public void matchShouldReturnEmptyCollectionWhenUserAttributeIsAbsent() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .build();

        Collection<MailAddress> results =  testee.match(fakeMail);

        assertThat(results).isEmpty();
    }

    @Test
    public void matchShouldReturnEmptyCollectionWhenUserAttributeIsAbsentAndThereIsNoRecipient() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipients()
            .build();

        Collection<MailAddress> results =  testee.match(fakeMail);

        assertThat(results).isEmpty();
    }

    @Test
    public void matchShouldReturnEmptyCollectionWhenUserAttributeIsPresentAndThereIsNoRecipient() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("name")
            .recipients()
            .attribute(new Attribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of("true")))
            .build();

        Collection<MailAddress> results =  testee.match(fakeMail);

        assertThat(results).isEmpty();
    }
}
