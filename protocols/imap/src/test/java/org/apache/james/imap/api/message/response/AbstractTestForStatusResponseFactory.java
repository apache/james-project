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
import static org.junit.Assert.assertEquals;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractTestForStatusResponseFactory  {

    private static final String TAG = "ATAG";

    private static final HumanReadableText KEY = new HumanReadableText(
            "KEY", "TEXT");

    private static final StatusResponse.ResponseCode CODE = StatusResponse.ResponseCode
            .alert();

    private ImapCommand command;

    StatusResponseFactory factory;

    protected abstract StatusResponseFactory createInstance();

    @Before
    public void setUp() throws Exception {
        factory = createInstance();
        command = ImapCommand.anyStateCommand("Command");
    }

    @Test
    public void testTaggedOk() {
        StatusResponse response = factory.taggedOk(TAG, command, KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(command, response.getCommand());
        assertThat(response.getResponseCode()).isNull();
        response = factory.taggedOk(TAG, command, KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertEquals(command, response.getCommand());
    }

    @Test
    public void testTaggedNo() {
        StatusResponse response = factory.taggedNo(TAG, command, KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(command, response.getCommand());
        assertThat(response.getResponseCode()).isNull();
        response = factory.taggedNo(TAG, command, KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertEquals(command, response.getCommand());
    }
    
    @Test
    public void testTaggedBad() {
        StatusResponse response = factory.taggedBad(TAG, command, KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertThat(response.getResponseCode()).isNull();
        assertEquals(command, response.getCommand());
        response = factory.taggedBad(TAG, command, KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertEquals(command, response.getCommand());
    }

    @Test
    public void testUntaggedOk() {
        StatusResponse response = factory.untaggedOk(KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
        response = factory.untaggedOk(KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertThat(response.getCommand()).isNull();
    }


    @Test
    public void testUntaggedNo() {
        StatusResponse response = factory.untaggedNo(KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
        response = factory.untaggedNo(KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertThat(response.getCommand()).isNull();
    }

    @Test
    public void testUntaggedBad() {
        StatusResponse response = factory.untaggedBad(KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
        response = factory.untaggedBad(KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertThat(response.getCommand()).isNull();
    }

    @Test
    public void testPreauth() {
        StatusResponse response = factory.preauth(KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.PREAUTH, response
                .getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
        response = factory.preauth(KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.PREAUTH, response
                .getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertThat(response.getCommand()).isNull();
    }

    @Test
    public void testBye() {
        StatusResponse response = factory.bye(KEY);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.BYE, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertThat(response.getResponseCode()).isNull();
        assertThat(response.getCommand()).isNull();
        response = factory.bye(KEY, CODE);
        assertThat(response).isNotNull();
        assertEquals(StatusResponse.Type.BYE, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertThat(response.getCommand()).isNull();
    }

}
