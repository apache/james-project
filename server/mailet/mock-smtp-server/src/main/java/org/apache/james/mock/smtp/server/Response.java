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

package org.apache.james.mock.smtp.server;

import java.util.Objects;

import com.google.common.base.Preconditions;

class Response {
    static  class SMTPStatusCode {
        public static SMTPStatusCode of(int code) {
            return new SMTPStatusCode(code);
        }

        private final int code;

        private SMTPStatusCode(int code) {
            Preconditions.checkArgument(code >= 100 && code < 600, "statusCode needs to be within the 1xx - 5xx range");

            this.code = code;
        }

        public int getCode() {
            return code;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SMTPStatusCode) {
                SMTPStatusCode that = (SMTPStatusCode) o;

                return Objects.equals(this.code, that.code);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(code);
        }
    }

    public static Response serverReject(SMTPStatusCode code, String message) {
        return new Response(code, message, true);
    }

    public static Response serverAccept(SMTPStatusCode code, String message) {
        return new Response(code, message, false);
    }

    private final SMTPStatusCode code;
    private final String message;
    private final boolean serverRejected;

    private Response(SMTPStatusCode code, String message, boolean serverRejected) {
        Preconditions.checkNotNull(message);
        Preconditions.checkNotNull(code);

        this.code = code;
        this.message = message;
        this.serverRejected = serverRejected;
    }

    String asReplyString() {
        return code.getCode() + " " + message;
    }

    boolean isServerRejected() {
        return serverRejected;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Response) {
            Response response = (Response) o;

            return Objects.equals(this.serverRejected, response.serverRejected)
                && Objects.equals(this.code, response.code)
                && Objects.equals(this.message, response.message);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(code, message, serverRejected);
    }
}
