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

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericMailetTest {

    private static class TestingMailet extends GenericMailet {

        @Override
        public void service(Mail mail) throws MessagingException {
        }
    }

    TestingMailet testee;

    @BeforeEach
    void setup() {
        testee = new TestingMailet();
    }

    @Test
    void getBooleanParameterShouldReturnFalseWhenValueNullAndDefaultFalse() throws Exception {
        String value = null;
        boolean actual = testee.getBooleanParameter(value, false);
        assertThat(actual).isFalse();
    }
    
    @Test
    void getBooleanParameterShouldReturnTrueWhenValueTrueAndDefaultFalse() throws Exception {
        String value = "true";
        boolean actual = testee.getBooleanParameter(value, false);
        assertThat(actual).isTrue();
    }
    
    @Test
    void getBooleanParameterShouldReturnTrueWhenValueYesAndDefaultFalse() throws Exception {
        String value = "yes";
        boolean actual = testee.getBooleanParameter(value, false);
        assertThat(actual).isTrue();
    }

    @Test
    void getBooleanParameterShouldReturnFalseWhenValueOtherAndDefaultFalse() throws Exception {
        String value = "other";
        boolean actual = testee.getBooleanParameter(value, false);
        assertThat(actual).isFalse();
    }

    @Test
    void getBooleanParameterShouldReturnTrueWhenValueNullAndDefaultTrue() throws Exception {
        String value = null;
        boolean actual = testee.getBooleanParameter(value, true);
        assertThat(actual).isTrue();
    }

    @Test
    void getBooleanParameterShouldReturnFalseWhenValueNoAndDefaultTrue() throws Exception {
        String value = "no";
        boolean actual = testee.getBooleanParameter(value, true);
        assertThat(actual).isFalse();
    }

    @Test
    void getBooleanParameterShouldReturnFalseWhenValueFalseAndDefaultTrue() throws Exception {
        String value = "false";
        boolean actual = testee.getBooleanParameter(value, true);
        assertThat(actual).isFalse();
    }

    @Test
    void getBooleanParameterShouldReturnTrueWhenValueOtherAndDefaultTrue() throws Exception {
        String value = "other";
        boolean actual = testee.getBooleanParameter(value, true);
        assertThat(actual).isTrue();
    }

}
