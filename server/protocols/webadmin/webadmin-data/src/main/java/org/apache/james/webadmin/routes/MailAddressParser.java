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

package org.apache.james.webadmin.routes;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MailAddressParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailAddressParser.class);

    static MailAddress parseMailAddress(String address, String addressType) {
        try {
            String decodedAddress = URLDecoder.decode(address, StandardCharsets.UTF_8.displayName());
            return new MailAddress(decodedAddress);
        } catch (AddressException e) {
            LOGGER.error("The " + addressType + " " + address + " is not an email address");
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("The %s is not an email address", addressType)
                .cause(e)
                .haltError();
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("UTF-8 should be a valid encoding");
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("Internal server error - Something went bad on the server side.")
                .cause(e)
                .haltError();
        }
    }

}
