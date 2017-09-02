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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.assertj.core.data.MapEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MappingArgumentTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void parseShouldFailIfCalledWithNull() throws MessagingException {
        expectedException.expect(IllegalArgumentException.class);
        MappingArgument.parse(null);
    }

    @Test
    public void parseShouldFailIfCalledWhenMissingMappingParts() throws MessagingException {
        expectedException.expect(IllegalArgumentException.class);
        MappingArgument.parse("key1;value1,key2");
    }

    @Test
    public void parseShouldFailIfCalledWhenExtraMappingParts() throws MessagingException {
        expectedException.expect(IllegalArgumentException.class);
        MappingArgument.parse("key1;value1,key2;value1;value3");
    }

    @Test
    public void parseShouldWorkForEmptyMapping() throws MessagingException {
        assertThat(MappingArgument.parse(""))
            .isEmpty();
    }

    @Test
    public void parseShouldWorkForEmptyMappingWithSpace() throws MessagingException {
        assertThat(MappingArgument.parse("  "))
            .isEmpty();
    }

    @Test
    public void parseShouldWorkForValidParsingWithOnlyOneKey() throws MessagingException {
        assertThat(MappingArgument.parse("key1;value1"))
            .containsExactly(MapEntry.entry("key1", "value1"));
    }

    @Test
    public void parseShouldWorkForValidParsingWithMoreThanOneKey() throws MessagingException {
        assertThat(MappingArgument.parse("key1;value1,key2;value2"))
            .containsExactly(MapEntry.entry("key1", "value1"), MapEntry.entry("key2", "value2"));
    }

    @Test
    public void parserShouldTrimSpacesAroundSemiColon() throws MessagingException {
        assertThat(MappingArgument.parse("key1;    value1"))
            .containsExactly(MapEntry.entry("key1", "value1"));
    }

    @Test
    public void parserShouldTrimSpacesAroundComa() throws MessagingException {
        assertThat(MappingArgument.parse("key1;value1,  key2;value2"))
            .containsExactly(MapEntry.entry("key1", "value1"), MapEntry.entry("key2", "value2"));
    }

    @Test
    public void parserShouldNotFailWhenExtraComa() throws MessagingException {
        assertThat(MappingArgument.parse("key1;value1,"))
            .containsExactly(MapEntry.entry("key1", "value1"));
    }
}
