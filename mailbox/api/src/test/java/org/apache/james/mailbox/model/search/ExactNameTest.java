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

package org.apache.james.mailbox.model.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class ExactNameTest {

    public static final String NAME = "toto";

    @Test
    public void constructorShouldThrowOnNullName() {
        assertThatThrownBy(() -> new ExactName(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void isWildShouldReturnFalse() {
        assertThat(new ExactName(NAME).isWild())
            .isFalse();
    }

    @Test
    public void getCombinedNameShouldReturnName() {
        assertThat(new ExactName(NAME).getCombinedName())
            .isEqualTo(NAME);
    }

    @Test
    public void isExpressionMatchShouldReturnTrueWhenName() {
        assertThat(new ExactName(NAME).isExpressionMatch(NAME))
            .isTrue();
    }

    @Test
    public void isExpressionMatchShouldReturnFalseWhenOtherValue() {
        assertThat(new ExactName(NAME).isExpressionMatch("other"))
            .isFalse();
    }

    @Test
    public void isExpressionMatchShouldThrowOnNullValue() {
        assertThatThrownBy(() -> new ExactName(NAME).isExpressionMatch(null))
            .isInstanceOf(NullPointerException.class);
    }

}