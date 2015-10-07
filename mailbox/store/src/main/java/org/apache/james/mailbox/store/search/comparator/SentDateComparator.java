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
package org.apache.james.mailbox.store.search.comparator;

import java.io.StringReader;
import java.util.Comparator;
import java.util.Date;

import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.james.mime4j.field.datetime.parser.DateTimeParser;
import org.apache.james.mime4j.field.datetime.parser.ParseException;

/**
 * {@link Comparator} which works like stated in RFC5256 2.2 Sent Date
 *
 */
public class SentDateComparator extends AbstractHeaderComparator {



    private final static Comparator<Message<?>> SENTDATE = new SentDateComparator(false);
    private final static Comparator<Message<?>> REVERSE_SENTDATE = new ReverseComparator(new SentDateComparator(true));
    
    private final boolean reverse;

    public SentDateComparator(boolean reverse) {
        this.reverse = reverse;
    }
    
    @Override
    public int compare(Message<?> o1, Message<?> o2) {
        Date date1 = getSentDate(o1);
        Date date2 = getSentDate(o2);
        int i = date1.compareTo(date2);
        
        // sent date was the same so use the uid as tie-breaker
        if (i == 0) {
            return UidComparator.uid(reverse).compare(o1, o2);
        }
        return 0;
    }
    
    private Date getSentDate(Message<?> message) {
        final String value = getHeaderValue("Date", message);
        final StringReader reader = new StringReader(value);
        try {
            DateTime dateTime = new DateTimeParser(reader).parseAll();
            return dateTime.getDate();
        } catch (ParseException e) {
            // if we can not parse the date header we should use the internaldate as fallback
            return message.getInternalDate();
        }
    }
    
    public static Comparator<Message<?>> sentDate(boolean reverse){
        if (reverse) {
            return REVERSE_SENTDATE;
        } else {
            return SENTDATE;
        }
    }

}
