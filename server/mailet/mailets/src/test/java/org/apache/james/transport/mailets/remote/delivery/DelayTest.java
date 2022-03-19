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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.Test;

public class DelayTest {

    @Test
    void defaultConstructorShouldConstructDefaultDelay() {
        assertThat(new Delay())
            .isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, Delay.DEFAULT_DELAY_TIME));
    }

    @Test
    void stringConstructorShouldWorkForNumbers() throws Exception {
        assertThat(Delay.from("36")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, Duration.ofMillis(36)));
    }

    @Test
    void stringConstructorShouldWorkForZero() throws Exception {
        assertThat(Delay.from("0")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, Duration.ofMillis(0)));
    }

    @Test
    void stringConstructorShouldThrowOnNegativeNumbers() {
        assertThatThrownBy(() -> Delay.from("-1s"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Duration amount should be positive");
    }

    @Test
    void stringConstructorShouldWorkForNumberAndSecond() throws Exception {
        assertThat(Delay.from("1s")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, Duration.ofSeconds(1)));
    }

    @Test
    void stringConstructorShouldWorkForNumberAndAttempts() throws Exception {
        assertThat(Delay.from("2*36")).isEqualTo(new Delay(2, Duration.ofMillis(36)));
    }

    @Test
    void stringConstructorShouldWorkForNumberAndZeroAttempts() throws Exception {
        assertThat(Delay.from("0*36")).isEqualTo(new Delay(0, Duration.ofMillis(36)));
    }

    @Test
    void stringConstructorShouldThrowOnNegativeAttempts() {
        assertThatThrownBy(() -> Delay.from("-1*36"))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void stringConstructorShouldThrowWhenAttemptsOmitted() {
        assertThatThrownBy(() -> Delay.from("36*"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void stringConstructorShouldThrowWhenDelayOmitted() {
        assertThatThrownBy(() -> Delay.from("2*"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void stringConstructorShouldWorkForNumberAttemptsAndUnit() throws Exception {
        assertThat(Delay.from("2*36s")).isEqualTo(new Delay(2, Duration.ofSeconds(36)));
    }
    
    @Test
    void stringConstructorShouldWorkForNumberAttemptsAndUnitWithSpaces() throws Exception {
        assertThat(Delay.from("2 * 36 s")).isEqualTo(new Delay(2, Duration.ofSeconds(36)));
    }

    @Test
    void stringConstructorShouldThrowOnInvalidInput() {
        assertThatThrownBy(() -> Delay.from("invalid"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void stringConstructorShouldThrowOnInvalidUnit() {
        assertThatThrownBy(() -> Delay.from("36invalid"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void stringConstructorShouldThrowOnEmptyString() {
        assertThatThrownBy(() -> Delay.from(""))
            .isInstanceOf(NumberFormatException.class);
    }
}
