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

package org.apache.james.transport.mailets.remoteDelivery;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DelayTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void defaultConstructorShouldConstructDefaultDelay() {
        assertThat(new Delay())
            .isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, Delay.DEFAULT_DELAY_TIME));
    }

    @Test
    public void stringConstructorShouldWorkForNumbers() throws Exception {
        assertThat(Delay.from("36")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, 36));
    }

    @Test
    public void stringConstructorShouldWorkForZero() throws Exception {
        assertThat(Delay.from("0")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, 0));
    }

    @Test
    public void stringConstructorShouldThrowOnNegativeNumbers() throws Exception {
        expectedException.expect(NumberFormatException.class);
        assertThat(Delay.from("-1s")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, 0));
    }

    @Test
    public void stringConstructorShouldWorkForNumberAndSecond() throws Exception {
        assertThat(Delay.from("1s")).isEqualTo(new Delay(Delay.DEFAULT_ATTEMPTS, 1000));
    }

    @Test
    public void stringConstructorShouldWorkForNumberAndAttempts() throws Exception {
        assertThat(Delay.from("2*36")).isEqualTo(new Delay(2, 36));
    }

    @Test
    public void stringConstructorShouldWorkForNumberAndZeroAttempts() throws Exception {
        assertThat(Delay.from("0*36")).isEqualTo(new Delay(0, 36));
    }

    @Test
    public void stringConstructorShouldThrowOnNegativeAttempts() throws Exception {
        expectedException.expect(MessagingException.class);

        Delay.from("-1*36");
    }

    @Test
    public void stringConstructorShouldThrowWhenAttemptsOmitted() throws Exception {
        expectedException.expect(NumberFormatException.class);

        Delay.from("*36");
    }

    @Test
    public void stringConstructorShouldThrowWhenDelayOmitted() throws Exception {
        expectedException.expect(NumberFormatException.class);

        Delay.from("2*");
    }

    @Test
    public void stringConstructorShouldWorkForNumberAttemptsAndUnit() throws Exception {
        assertThat(Delay.from("2*36s")).isEqualTo(new Delay(2, 36000));
    }
    
    @Test
    public void stringConstructorShouldWorkForNumberAttemptsAndUnitWithSpaces() throws Exception {
    	assertThat(Delay.from("2 * 36 s")).isEqualTo(new Delay(2, 36000));
    }

    @Test
    public void stringConstructorShouldThrowOnInvalidInput() throws Exception {
        expectedException.expect(NumberFormatException.class);

        Delay.from("invalid");
    }

    @Test
    public void stringConstructorShouldThrowOnInvalidUnit() throws Exception {
        expectedException.expect(NumberFormatException.class);

        Delay.from("36invalid");
    }

    @Test
    public void stringConstructorShouldThrowOnEmptyString() throws Exception {
        expectedException.expect(NumberFormatException.class);

        Delay.from("");
    }
}
