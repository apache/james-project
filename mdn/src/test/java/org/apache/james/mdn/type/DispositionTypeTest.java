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

package org.apache.james.mdn.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DispositionTypeTest {
    @Test
    public void fromStringShouldReturnEmptyWhenUnknown() {
        assertThat(DispositionType.fromString("unknown"))
            .isEmpty();
    }

    @Test
    public void fromStringShouldRetrieveDeleted() {
        assertThat(DispositionType.fromString(DispositionType.Deleted.getValue()))
            .contains(DispositionType.Deleted);
    }

    @Test
    public void fromStringShouldRetrieveDispatched() {
        assertThat(DispositionType.fromString(DispositionType.Dispatched.getValue()))
            .contains(DispositionType.Dispatched);
    }

    @Test
    public void fromStringShouldRetrieveDisplayed() {
        assertThat(DispositionType.fromString(DispositionType.Displayed.getValue()))
            .contains(DispositionType.Displayed);
    }

    @Test
    public void fromStringShouldRetrieveProcessed() {
        assertThat(DispositionType.fromString(DispositionType.Processed.getValue()))
            .contains(DispositionType.Processed);
    }

    @Test
    public void fromStringShouldNotBeCaseSensitive() {
        assertThat(DispositionType.fromString("Deleted"))
            .contains(DispositionType.Deleted);
    }
}
