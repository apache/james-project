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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.junit.Before;
import org.junit.Test;

abstract public class AbstractTestForStatusResponseFactory  {

    private static final String TAG = "ATAG";

    private static final HumanReadableText KEY = new HumanReadableText(
            "KEY", "TEXT");

    private static final StatusResponse.ResponseCode CODE = StatusResponse.ResponseCode
            .alert();

    private ImapCommand command;

    StatusResponseFactory factory;

    abstract protected StatusResponseFactory createInstance();

    @Before
    public void setUp() throws Exception {
        factory = createInstance();
        command = ImapCommand.anyStateCommand("Command");
    }

    @Test
    public void testTaggedOk() {
        StatusResponse response = factory.taggedOk(TAG, command, KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(command, response.getCommand());
        assertNull(response.getResponseCode());
        response = factory.taggedOk(TAG, command, KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertEquals(command, response.getCommand());
    }

    @Test
    public void testTaggedNo() {
        StatusResponse response = factory.taggedNo(TAG, command, KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(command, response.getCommand());
        assertNull(response.getResponseCode());
        response = factory.taggedNo(TAG, command, KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertEquals(command, response.getCommand());
    }
    
    @Test
    public void testTaggedBad() {
        StatusResponse response = factory.taggedBad(TAG, command, KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertNull(response.getResponseCode());
        assertEquals(command, response.getCommand());
        response = factory.taggedBad(TAG, command, KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(TAG, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertEquals(command, response.getCommand());
    }

    @Test
    public void testUntaggedOk() {
        StatusResponse response = factory.untaggedOk(KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertNull(response.getResponseCode());
        assertNull(response.getCommand());
        response = factory.untaggedOk(KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.OK, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertNull(response.getCommand());
    }


    @Test
    public void testUntaggedNo() {
        StatusResponse response = factory.untaggedNo(KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertNull(response.getResponseCode());
        assertNull(response.getCommand());
        response = factory.untaggedNo(KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.NO, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertNull(response.getCommand());
    }

    @Test
    public void testUntaggedBad() {
        StatusResponse response = factory.untaggedBad(KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertNull(response.getResponseCode());
        assertNull(response.getCommand());
        response = factory.untaggedBad(KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.BAD, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertNull(response.getCommand());
    }

    @Test
    public void testPreauth() {
        StatusResponse response = factory.preauth(KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.PREAUTH, response
                .getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertNull(response.getResponseCode());
        assertNull(response.getCommand());
        response = factory.preauth(KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.PREAUTH, response
                .getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertNull(response.getCommand());
    }

    @Test
    public void testBye() {
        StatusResponse response = factory.bye(KEY);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.BYE, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertNull(response.getResponseCode());
        assertNull(response.getCommand());
        response = factory.bye(KEY, CODE);
        assertNotNull(response);
        assertEquals(StatusResponse.Type.BYE, response.getServerResponseType());
        assertEquals(null, response.getTag());
        assertEquals(KEY, response.getTextKey());
        assertEquals(CODE, response.getResponseCode());
        assertNull(response.getCommand());
    }

}
