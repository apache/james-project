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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RepeatTest {

    public static final String ELEMENT = "a";
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void repeatShouldThrowOnNegativeTimes() {
        expectedException.expect(IllegalArgumentException.class);

        Repeat.repeat(new Object(), -1);
    }

    @Test
    public void repeatShouldReturnEmptyListOnZeroTimes() {
        assertThat(Repeat.repeat(new Object(), 0)).isEmpty();
    }

    @Test
    public void repeatShouldWorkWithOneElement() {
        assertThat(Repeat.repeat(ELEMENT, 1)).containsExactly(ELEMENT);
    }

    @Test
    public void repeatShouldWorkWithTwoElements() {
        assertThat(Repeat.repeat(ELEMENT, 2)).containsExactly(ELEMENT, ELEMENT);
    }

}
