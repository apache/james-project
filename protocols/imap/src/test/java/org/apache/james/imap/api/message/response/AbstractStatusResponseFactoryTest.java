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

package org.apache.james.imap.api.message.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.junit.jupiter.api.Test;

public interface AbstractStatusResponseFactoryTest {

    String TAG = "ATAG";
    HumanReadableText KEY = new HumanReadableText("KEY", "TEXT");
    StatusResponse.ResponseCode CODE = StatusResponse.ResponseCode.alert();
    ImapCommand COMMAND = ImapCommand.anyStateCommand("Command");

    StatusResponseFactory factory();
    
    @Test
    default void taggedOkShouldBuildCorrectResponse() {
        StatusResponse response = factory().taggedOk(TAG, COMMAND, KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.OK);
        assertThat(response.getTag()).isEqualTo(TAG);
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getCommand()).isEqualTo(COMMAND);
        assertThat(response.getResponseCode()).isNull();
    }

    @Test
    default void taggedOkWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().taggedOk(TAG, COMMAND, KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.OK);
        assertThat(response.getTag()).isEqualTo(TAG);
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isEqualTo(COMMAND);
    }

    @Test
    default void taggedNoShouldBuildCorrectResponse() {
        StatusResponse response = factory().taggedNo(TAG, COMMAND, KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.NO);
        assertThat(response.getTag()).isEqualTo(TAG);
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getCommand()).isEqualTo(COMMAND);
        assertThat(response.getResponseCode()).isNull();
    }

    @Test
    default void taggedNoWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().taggedNo(TAG, COMMAND, KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.NO);
        assertThat(response.getTag()).isEqualTo(TAG);
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isEqualTo(COMMAND);
    }

    @Test
    default void taggedBadShouldBuildCorrectResponse() {
        StatusResponse response = factory().taggedBad(TAG, COMMAND, KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.BAD);
        assertThat(response.getTag()).isEqualTo(TAG);
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isEqualTo(COMMAND);
    }

    @Test
    default void taggedBadWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().taggedBad(TAG, COMMAND, KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.BAD);
        assertThat(response.getTag()).isEqualTo(TAG);
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isEqualTo(COMMAND);
    }

    @Test
    default void untaggedOkShouldBuildCorrectResponse() {
        StatusResponse response = factory().untaggedOk(KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.OK);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void untaggedOkWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().untaggedOk(KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.OK);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isNull();
    }


    @Test
    default void untaggedNoShouldBuildCorrectResponse() {
        StatusResponse response = factory().untaggedNo(KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.NO);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void untaggedNoWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().untaggedNo(KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.NO);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void untaggedBadShouldBuildCorrectResponse() {
        StatusResponse response = factory().untaggedBad(KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.BAD);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void untaggedBadWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().untaggedBad(KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.BAD);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void preauthShouldBuildCorrectResponse() {
        StatusResponse response = factory().preauth(KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.PREAUTH);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void preauthWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().preauth(KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.PREAUTH);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void byeShouldBuildCorrectResponse() {
        StatusResponse response = factory().bye(KEY);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.BYE);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
    }

    @Test
    default void byeWithCodeShouldBuildCorrectResponse() {
        StatusResponse response = factory().bye(KEY, CODE);
        assertThat(response).isNotNull();
        assertThat(response.getServerResponseType()).isEqualTo(StatusResponse.Type.BYE);
        assertThat(response.getTag()).isNull();
        assertThat(response.getTextKey()).isEqualTo(KEY);
        assertThat(response.getResponseCode()).isEqualTo(CODE);
        assertThat(response.getCommand()).isNull();
    }

}
