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

import javax.mail.MessagingException;
import java.util.StringTokenizer;

/**
 * This mailet will attach text to the end of the message (like a footer).  Right
 * now it only supports simple messages without multiple parts.
 */
public class AddFooter extends AbstractAddFooter {

    /**
     * This is the plain text version of the footer we are going to add
     */
    String text = "";
    
    /**
     * Initialize the mailet
     */
    public void init() throws MessagingException {
        text = getInitParameter("text");
    }

    /**
     * This is exposed as a method for easy subclassing to provide alternate ways
     * to get the footer text.
     *
     * @return the footer text
     */
    public String getFooterText() {
        return text;
    }

    /**
     * This is exposed as a method for easy subclassing to provide alternate ways
     * to get the footer text.  By default, this will take the footer text,
     * converting the linefeeds to &lt;br&gt; tags.
     *
     * @return the HTML version of the footer text
     */
    public String getFooterHTML() {
        String text = getFooterText();
        StringBuilder sb = new StringBuilder();
        StringTokenizer st = new StringTokenizer(text, "\r\n", true);
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.equals("\r")) {
                continue;
            }
            if (token.equals("\n")) {
                sb.append("<br />\n");
            } else {
                sb.append(token);
            }
        }
        return sb.toString();
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "AddFooter Mailet";
    } 
}
