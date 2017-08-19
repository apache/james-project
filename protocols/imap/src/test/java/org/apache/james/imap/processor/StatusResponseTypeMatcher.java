/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.imap.processor;

import org.apache.james.imap.api.message.response.StatusResponse;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * A matcher for {@link StatusResponse} objects, whereby only their
 * serverResponseType field is significant. is significant.
 * 
 */
public class StatusResponseTypeMatcher extends BaseMatcher<StatusResponse> {

    public static final StatusResponseTypeMatcher OK_RESPONSE_MATCHER = new StatusResponseTypeMatcher(StatusResponse.Type.OK);
    public static final StatusResponseTypeMatcher BAD_RESPONSE_MATCHER = new StatusResponseTypeMatcher(StatusResponse.Type.BAD);
    public static final StatusResponseTypeMatcher NO_RESPONSE_MATCHER = new StatusResponseTypeMatcher(StatusResponse.Type.NO);


    private final org.apache.james.imap.api.message.response.StatusResponse.Type serverResponseType;

    public StatusResponseTypeMatcher(org.apache.james.imap.api.message.response.StatusResponse.Type responseCode) {
        super();
        this.serverResponseType = responseCode;
    }

    /**
     * @see org.hamcrest.Matcher#matches(java.lang.Object)
     */
    public boolean matches(Object o) {
        if (o instanceof StatusResponse) {
            StatusResponse sr = (StatusResponse) o;
            return this.serverResponseType.equals(sr.getServerResponseType());
        }
        return false;
    }

    /**
     * @see org.hamcrest.SelfDescribing#describeTo(org.hamcrest.Description)
     */
    public void describeTo(Description d) {
        d.appendText(StatusResponse.class.getName());
        d.appendText(" with serverResponseType.equals(" + serverResponseType.name() + ")");

    }

}