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
package org.apache.james.mailbox;

import java.io.Serializable;
import java.util.Comparator;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;

/**
 * Orders by name with INBOX first.
 */
public class StandardMailboxMetaDataComparator implements Comparator<MailboxMetaData>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Static comparison.
     * 
     * @param one
     *            possibly null
     * @param two
     *            possibly null
     * @return {@link Comparator#compare(Object, Object)}
     */
    public static int order(MailboxMetaData one, MailboxMetaData two) {
        final String nameTwo = two.getPath().getName();
        final int result;
        final String nameOne = one.getPath().getName();
        if (MailboxConstants.INBOX.equals(nameOne)) {
            result = MailboxConstants.INBOX.equals(nameTwo) ? 0 : -1;
        } else if (MailboxConstants.INBOX.equals(nameTwo)) {
            result = 1;
        } else if (nameOne == null) {
            result = nameTwo == null ? 0 : 1;
        } else if (nameTwo == null) {
            result = -1;
        } else {
            result = nameOne.compareTo(nameTwo);
        }
        return result;
    }

    /**
     * @see Comparator#compare(Object, Object)
     */
    public int compare(MailboxMetaData one, MailboxMetaData two) {
        return order(one, two);
    }

}
