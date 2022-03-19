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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class MailetUtilTest {

    static final String A_PARAMETER = "aParameter";
    static final int DEFAULT_VALUE = 36;

    @Test
    void getInitParameterShouldReturnTrueWhenIsValueTrueLowerCase() {
        assertThat(getParameterValued("true", false)).isTrue();
    }

    @Test
    void getInitParameterShouldReturnTrueWhenIsValueTrueUpperCase() {
        assertThat(getParameterValued("TRUE", false)).isTrue();
    }

    @Test
    void getInitParameterShouldReturnTrueWhenIsValueTrueMixedCase() {
        assertThat(getParameterValued("trUE", false)).isTrue();
    }

    @Test
    void getInitParameterShouldReturnFalseWhenIsValueFalseLowerCase() {
        assertThat(getParameterValued("false", true)).isFalse();
    }

    @Test
    void getInitParameterShouldReturnFalseWhenIsValueFalseUpperCase() {
        assertThat(getParameterValued("FALSE", true)).isFalse();
    }

    @Test
    void getInitParameterShouldReturnFalseWhenIsValueFalseMixedCase() {
        assertThat(getParameterValued("fALSe", true)).isFalse();
    }

    @Test
    void getInitParameterShouldReturnDefaultValueAsTrueWhenBadValue() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(getParameterValued("fals", true)).isTrue();
            softly.assertThat(getParameterValued("TRU", true)).isTrue();
            softly.assertThat(getParameterValued("FALSEest", true)).isTrue();
            softly.assertThat(getParameterValued("", true)).isTrue();
            softly.assertThat(getParameterValued("gubbins", true)).isTrue();
        });

    }

    @Test
    void getInitParameterShouldReturnDefaultValueAsFalseWhenBadValue() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(getParameterValued("fals", false)).isFalse();
            softly.assertThat(getParameterValued("TRU", false)).isFalse();
            softly.assertThat(getParameterValued("FALSEest", false)).isFalse();
            softly.assertThat(getParameterValued("", false)).isFalse();
            softly.assertThat(getParameterValued("gubbins", false)).isFalse();
        });
    }

    @Test
    void getInitParameterShouldReturnAbsentWhenNull() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .build();

        assertThat(MailetUtil.getInitParameter(mailetConfig, A_PARAMETER)).isEmpty();
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnEmptyString() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger(""))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnNull() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger(null))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnInvalid() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("invalid"))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnNegativeNumber() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("-1"))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnZero() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("0"))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerShouldParseCorrectValue() throws Exception {
        assertThat(MailetUtil.getInitParameterAsStrictlyPositiveInteger("1"))
            .isEqualTo(1);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnEmptyString() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("", DEFAULT_VALUE))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldReturnDefaultValueOnNull() throws Exception {
        assertThat(MailetUtil.getInitParameterAsStrictlyPositiveInteger(null, DEFAULT_VALUE))
            .isEqualTo(DEFAULT_VALUE);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnInvalid() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("invalid", DEFAULT_VALUE))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnNegativeNumber() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("-1", DEFAULT_VALUE))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnZero() {
        assertThatThrownBy(() -> MailetUtil.getInitParameterAsStrictlyPositiveInteger("0", DEFAULT_VALUE))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldParseCorrectValue() throws Exception {
        assertThat(MailetUtil.getInitParameterAsStrictlyPositiveInteger("1", DEFAULT_VALUE))
            .isEqualTo(1);
    }

    private boolean getParameterValued(String value, boolean defaultValue) {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(A_PARAMETER, value)
            .build();
        return MailetUtil.getInitParameter(mailetConfig, A_PARAMETER).orElse(defaultValue);
    }
}
