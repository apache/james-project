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

package org.apache.james.protocols.smtp;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.protocols.api.AbstractResponse;

/**
 * Contains an SMTP result
 */
public class SMTPResponse extends AbstractResponse {
    /**
     * Construct a new SMTPResponse. The given code and description can not be null, if null an IllegalArgumentException
     * get thrown
     * 
     * @param code the returnCode
     * @param description the description 
     */
    public SMTPResponse(String code, CharSequence description) {
        super(code, description);
    }

    @Override
    public List<CharSequence> getLines() {
        List<CharSequence> responseList = new ArrayList<>();

        for (int k = 0; k < lines.size(); k++) {
            StringBuilder respBuff = new StringBuilder(256);
            respBuff.append(getRetCode());
            if (k == lines.size() - 1) {
                respBuff.append(" ");
                respBuff.append(lines.get(k));

            } else {
                respBuff.append("-");
                respBuff.append(lines.get(k));

            }
            responseList.add(respBuff.toString());
        }

        return responseList;
    }
}
