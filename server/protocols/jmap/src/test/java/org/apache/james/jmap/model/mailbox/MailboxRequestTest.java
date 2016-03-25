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
package org.apache.james.jmap.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Test;

public class MailboxRequestTest {

    @Test
    public void builderShouldThrowOnNullRole() {
        assertThatThrownBy(() -> MailboxRequest.builder().role(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowWhenRoleDefine() {
        assertThatThrownBy(() -> MailboxRequest.builder().role(Role.ARCHIVE)).isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowOnNullId() {
        assertThatThrownBy(() -> MailboxRequest.builder().id(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowOnNullSortOrder() {
        assertThatThrownBy(() -> MailboxRequest.builder().sortOrder(null)).isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void builderShouldThrowOnSortOrderDefine() {
        assertThatThrownBy(() -> MailboxRequest.builder().sortOrder(SortOrder.of(123))).isInstanceOf(NotImplementedException.class);
    }    

    @Test
    public void builderShouldThrowOnNullParentId() {
        assertThatThrownBy(() -> MailboxRequest.builder().parentId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowOnNullName() {
        assertThatThrownBy(() -> MailboxRequest.builder().name(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldRequireName() {
        assertThatThrownBy(() -> MailboxRequest.builder().build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("name");
    }

    @Test
    public void builderShouldBuildWhenName() {
        MailboxRequest request = MailboxRequest.builder().name("foo").build();
        assertThat(request.getName()).isEqualTo("foo");
        assertThat(request.getId()).isEmpty();
        assertThat(request.getParentId()).isEmpty();
        assertThat(request.getSortOrder()).isEmpty();
        assertThat(request.getRole()).isEmpty();
    }
}
