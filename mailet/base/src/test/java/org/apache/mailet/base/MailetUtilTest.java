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

package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

public class MailetUtilTest {

    private static final String A_PARAMETER = "aParameter";

    FakeMailetConfig config;

    @Before
    public void setUp() throws Exception {
        config = new FakeMailetConfig();
    }

    @Test
    public void getInitParameterShouldReturnTrueWhenIsValueTrueLowerCase() {
        assertThat(getParameterValued("true", false)).isTrue();
    }

    @Test
    public void getInitParameterShouldReturnTrueWhenIsValueTrueUpperCase() {
        assertThat(getParameterValued("TRUE", false)).isTrue();
    }

    @Test
    public void getInitParameterShouldReturnTrueWhenIsValueTrueMixedCase() {
        assertThat(getParameterValued("trUE", false)).isTrue();
    }

    @Test
    public void getInitParameterShouldReturnFalseWhenIsValueFalseLowerCase() {
        assertThat(getParameterValued("false", true)).isFalse();
    }

    @Test
    public void getInitParameterShouldReturnFalseWhenIsValueFalseUpperCase() {
        assertThat(getParameterValued("FALSE", true)).isFalse();
    }

    @Test
    public void getInitParameterShouldReturnFalseWhenIsValueFalseMixedCase() {
        assertThat(getParameterValued("fALSe", true)).isFalse();
    }

    @Test
    public void getInitParameterShouldReturnDefaultValueAsTrueWhenBadValue() {
        assertThat(getParameterValued("fals", true)).isTrue();
        assertThat(getParameterValued("TRU", true)).isTrue();
        assertThat(getParameterValued("FALSEest", true)).isTrue();
        assertThat(getParameterValued("", true)).isTrue();
        assertThat(getParameterValued("gubbins", true)).isTrue();
    }

    @Test
    public void getInitParameterShouldReturnDefaultValueAsFalseWhenBadValue() {
        assertThat(getParameterValued("fals", false)).isFalse();
        assertThat(getParameterValued("TRU", false)).isFalse();
        assertThat(getParameterValued("FALSEest", false)).isFalse();
        assertThat(getParameterValued("", false)).isFalse();
        assertThat(getParameterValued("gubbins", false)).isFalse();
    }

    @Test
    public void getInitParameterShouldReturnDefaultValueWhenNull() {
        assertThat(MailetUtil.getInitParameter(config, A_PARAMETER, false)).isFalse();
        assertThat(MailetUtil.getInitParameter(config, A_PARAMETER, true)).isTrue();
    }

    private boolean getParameterValued(String value, boolean defaultValue) {
        config.clear();
        config.setProperty(A_PARAMETER, value);
        return MailetUtil.getInitParameter(config, A_PARAMETER, defaultValue);
    }
}
