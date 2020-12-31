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

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.junit.jupiter.api.Test;

class UsersQuotaDetailsDTOTest {

    @Test
    void builderShouldThrowWhenUserIsNull() {
        assertThatThrownBy(() -> UsersQuotaDetailsDTO.builder().build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowWhenDetailIsNull() {
        assertThatThrownBy(() -> UsersQuotaDetailsDTO.builder()
                .user(Username.of("user@domain.org"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldWork() {
        Username username = Username.of("user@domain.org");
        QuotaDetailsDTO quotaDetailsDTO = QuotaDetailsDTO.builder()
                .occupation(
                        Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(1)).computedLimit(QuotaSizeLimit.size(12)).build(),
                        Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(36)).computedLimit(QuotaCountLimit.count(360)).build())
                .build();
        UsersQuotaDetailsDTO usersQuotaDetailsDTO = UsersQuotaDetailsDTO.builder()
                .user(username)
                .detail(quotaDetailsDTO)
                .build();

        assertThat(usersQuotaDetailsDTO.getUsername()).isEqualTo(username.asString());
        assertThat(usersQuotaDetailsDTO.getDetail()).isEqualTo(quotaDetailsDTO);
    }
}
