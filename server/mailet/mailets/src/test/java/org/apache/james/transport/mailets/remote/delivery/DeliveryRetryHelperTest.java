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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.transport.mailets.remote.delivery.DeliveryRetriesHelper;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Test;

public class DeliveryRetryHelperTest {

    private static final Attribute INVALID_ATTRIBUTE = new Attribute(DeliveryRetriesHelper.DELIVERY_RETRY_COUNT, AttributeValue.of("invalid"));

    @Test
    public void retrieveRetriesShouldBeZeroByDefault() throws Exception {
        assertThat(DeliveryRetriesHelper.retrieveRetries(FakeMail.defaultFakeMail()))
            .isEqualTo(0);
    }

    @Test
    public void retrieveRetriesShouldBeZeroAfterInit() throws Exception {
        FakeMail mail = FakeMail.defaultFakeMail();

        DeliveryRetriesHelper.initRetries(mail);

        assertThat(DeliveryRetriesHelper.retrieveRetries(mail))
            .isEqualTo(0);
    }

    @Test
    public void retrieveRetriesShouldBeOneAfterIncrement() throws Exception {
        FakeMail mail = FakeMail.defaultFakeMail();

        DeliveryRetriesHelper.initRetries(mail);
        DeliveryRetriesHelper.incrementRetries(mail);

        assertThat(DeliveryRetriesHelper.retrieveRetries(mail))
            .isEqualTo(1);
    }

    @Test
    public void incrementRetriesShouldWorkOnNonInitializedMails() throws Exception {
        FakeMail mail = FakeMail.defaultFakeMail();

        DeliveryRetriesHelper.incrementRetries(mail);

        assertThat(DeliveryRetriesHelper.retrieveRetries(mail))
            .isEqualTo(1);
    }

    @Test
    public void retrieveRetriesShouldBeZeroOnInvalidValue() throws Exception {
        FakeMail mail = FakeMail.builder().name("name").attribute(INVALID_ATTRIBUTE).build();

        assertThat(DeliveryRetriesHelper.retrieveRetries(mail))
            .isEqualTo(0);
    }

    @Test
    public void incrementRetriesShouldWorkOnInvalidMails() throws Exception {
        FakeMail mail = FakeMail.builder().name("name").attribute(INVALID_ATTRIBUTE).build();

        DeliveryRetriesHelper.incrementRetries(mail);

        assertThat(DeliveryRetriesHelper.retrieveRetries(mail))
            .isEqualTo(1);
    }
}
