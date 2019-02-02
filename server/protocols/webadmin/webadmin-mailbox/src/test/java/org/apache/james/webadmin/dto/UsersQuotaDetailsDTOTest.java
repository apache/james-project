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
package org.apache.james.webadmin.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.mail.internet.AddressException;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.model.Quota;
import org.junit.Test;

public class UsersQuotaDetailsDTOTest {

    @Test
    public void builderShouldThrowWhenUserIsNull() {
        assertThatThrownBy(() -> UsersQuotaDetailsDTO.builder().build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowWhenDetailIsNull() {
        assertThatThrownBy(() -> UsersQuotaDetailsDTO.builder()
                .user(User.fromUsername("user@domain.org"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldWork() throws AddressException {
        User user = User.fromUsername("user@domain.org");
        QuotaDetailsDTO quotaDetailsDTO = QuotaDetailsDTO.builder()
                .occupation(
                        Quota.<QuotaSize>builder().used(QuotaSize.size(1)).computedLimit(QuotaSize.size(12)).build(),
                        Quota.<QuotaCount>builder().used(QuotaCount.count(36)).computedLimit(QuotaCount.count(360)).build())
                .build();
        UsersQuotaDetailsDTO usersQuotaDetailsDTO = UsersQuotaDetailsDTO.builder()
                .user(user)
                .detail(quotaDetailsDTO)
                .build();

        assertThat(usersQuotaDetailsDTO.getUser()).isEqualTo(user.asString());
        assertThat(usersQuotaDetailsDTO.getDetail()).isEqualTo(quotaDetailsDTO);
    }
}
