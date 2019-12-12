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

package org.apache.james.imap.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ImapCommandTest {
    static Stream<Arguments> validForStateShouldReturnTrue() {
        return Stream.of(
            Arguments.arguments(ImapCommand.anyStateCommand("command"), ImapSessionState.AUTHENTICATED),
            Arguments.arguments(ImapCommand.anyStateCommand("command"), ImapSessionState.SELECTED),
            Arguments.arguments(ImapCommand.anyStateCommand("command"), ImapSessionState.NON_AUTHENTICATED),
            Arguments.arguments(ImapCommand.selectedStateCommand("command"), ImapSessionState.SELECTED),
            Arguments.arguments(ImapCommand.authenticatedStateCommand("command"), ImapSessionState.AUTHENTICATED),
            Arguments.arguments(ImapCommand.authenticatedStateCommand("command"), ImapSessionState.SELECTED),
            Arguments.arguments(ImapCommand.nonAuthenticatedStateCommand("command"), ImapSessionState.NON_AUTHENTICATED));
    }

    @ParameterizedTest
    @MethodSource
    void validForStateShouldReturnTrue(ImapCommand command, ImapSessionState state) {
        assertThat(command.validForState(state)).isTrue();
    }

    static Stream<Arguments> validForStateShouldReturnFalse() {
        return Stream.of(
            Arguments.arguments(ImapCommand.anyStateCommand("command"), ImapSessionState.LOGOUT),
            Arguments.arguments(ImapCommand.selectedStateCommand("command"), ImapSessionState.LOGOUT),
            Arguments.arguments(ImapCommand.authenticatedStateCommand("command"), ImapSessionState.LOGOUT),
            Arguments.arguments(ImapCommand.nonAuthenticatedStateCommand("command"), ImapSessionState.LOGOUT),
            Arguments.arguments(ImapCommand.selectedStateCommand("command"), ImapSessionState.NON_AUTHENTICATED),
            Arguments.arguments(ImapCommand.selectedStateCommand("command"), ImapSessionState.AUTHENTICATED),
            Arguments.arguments(ImapCommand.authenticatedStateCommand("command"), ImapSessionState.NON_AUTHENTICATED),
            Arguments.arguments(ImapCommand.nonAuthenticatedStateCommand("command"), ImapSessionState.AUTHENTICATED),
            Arguments.arguments(ImapCommand.nonAuthenticatedStateCommand("command"), ImapSessionState.SELECTED));
    }

    @ParameterizedTest
    @MethodSource
    void validForStateShouldReturnFalse(ImapCommand command, ImapSessionState state) {
        assertThat(command.validForState(state)).isFalse();
    }
}