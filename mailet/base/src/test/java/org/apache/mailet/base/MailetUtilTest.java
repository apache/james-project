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

import javax.mail.MessagingException;

import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MailetUtilTest {

    private static final String A_PARAMETER = "aParameter";
    public static final int DEFAULT_VALUE = 36;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
    public void getInitParameterShouldReturnAbsentWhenNull() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .build();
        assertThat(MailetUtil.getInitParameter(mailetConfig, A_PARAMETER)).isEmpty();
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnEmptyString() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("");
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnNull() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger(null);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnInvalid() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("invalid");
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnNegativeNumber() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("-1");
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerShouldThrowOnZero() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("0");
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerShouldParseCorrectValue() throws Exception {
        assertThat(MailetUtil.getInitParameterAsStrictlyPositiveInteger("1"))
            .isEqualTo(1);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnEmptyString() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("", DEFAULT_VALUE);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldReturnDefaultValueOnNull() throws Exception {
        assertThat(MailetUtil.getInitParameterAsStrictlyPositiveInteger(null, DEFAULT_VALUE))
            .isEqualTo(DEFAULT_VALUE);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnInvalid() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("invalid", DEFAULT_VALUE);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnNegativeNumber() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("-1", DEFAULT_VALUE);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldThrowOnZero() throws Exception {
        expectedException.expect(MessagingException.class);

        MailetUtil.getInitParameterAsStrictlyPositiveInteger("0", DEFAULT_VALUE);
    }

    @Test
    public void getInitParameterAsStrictlyPositiveIntegerWithDefaultValueShouldParseCorrectValue() throws Exception {
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
