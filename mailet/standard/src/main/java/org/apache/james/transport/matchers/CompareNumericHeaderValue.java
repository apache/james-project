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



package org.apache.james.transport.matchers;

import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import java.lang.NumberFormatException;

import java.util.Collection;
import java.util.StringTokenizer;

/**
 * <P>Matches mails containing a header with a numeric value whose comparison with the specified value is true.
 * If the header is missing in the message, there will be <I>no match</I></P>
 * <P>Configuration string: The headerName, a comparison operator and the numeric headerValue
 * to compare with, <I>space or tab delimited</I>.</P>
 * <P>The comparison operators are: <CODE>&lt, &lt=, ==, &gt=, &gt</CODE>;
 * another set of operators is: <CODE>LT, LE, EQ, GE, GT</CODE>.
 * Also the following operators are accepted: <CODE>=&lt, =, =&gt</CODE>.</P>
 * <P>Example:</P>
 * <PRE><CODE>
 *    &lt;mailet match="CompareNumericHeaderValue=X-MessageIsSpamProbability > 0.9" class="ToProcessor"&gt;
 *       &lt;processor&gt; spam &lt;/processor&gt;
 *    &lt;/mailet&gt;
 * </CODE></PRE>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class CompareNumericHeaderValue extends GenericMatcher {

    private String headerName = null;
    
    private int comparisonOperator;
    private final static int LT = -2;
    private final static int LE = -1;
    private final static int EQ =  0;
    private final static int GE = +1;
    private final static int GT = +2;
    
    private Double headerValue;

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init() throws MessagingException {
        StringTokenizer st = new StringTokenizer(getCondition(), " \t", false);
        if (st.hasMoreTokens()) {
            headerName = st.nextToken().trim();
        }
        else {
            throw new MessagingException("Missing headerName");
        }
        if (st.hasMoreTokens()) {
            String comparisonOperatorString = st.nextToken().trim();
            if (comparisonOperatorString.equals("<")
                || comparisonOperatorString.equals("LT")) {
                comparisonOperator = LT;
            }
            else if (comparisonOperatorString.equals("<=")
                     || comparisonOperatorString.equals("=<")
                     || comparisonOperatorString.equals("LE")) {
                comparisonOperator = LE;
            }
            else if (comparisonOperatorString.equals("==")
                     || comparisonOperatorString.equals("=")
                     || comparisonOperatorString.equals("EQ")) {
                comparisonOperator = EQ;
            }
            else if (comparisonOperatorString.equals(">=")
                     || comparisonOperatorString.equals("=>")
                     || comparisonOperatorString.equals("GE")) {
                comparisonOperator = GE;
            }
            else if (comparisonOperatorString.equals(">")
                     || comparisonOperatorString.equals("GT")) {
                comparisonOperator = GT;
            }
            else {
                throw new MessagingException("Bad comparisonOperator: \"" + comparisonOperatorString + "\"");
            }
        }
        else {
            throw new MessagingException("Missing comparisonOperator");
        }
        if (st.hasMoreTokens()) {
            String headerValueString = st.nextToken().trim();
            try {
                headerValue = Double.valueOf(headerValueString);
            }
            catch (NumberFormatException nfe) {
                throw new MessagingException("Bad header comparison value: \""
                                             + headerValueString + "\"", nfe);
            }
        }
        else {
            throw new MessagingException("Missing headerValue threshold");
        }
    }


    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#match(org.apache.mailet.Mail)
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (headerName == null) {
            // should never get here
            throw new IllegalStateException("Null headerName");
        }
        
        MimeMessage message = mail.getMessage();
        
        String [] headerArray = message.getHeader(headerName);
        if (headerArray != null && headerArray.length > 0) {
            try {
                int comparison = Double.valueOf(headerArray[0].trim()).compareTo(headerValue);
                switch (comparisonOperator) {
                    case LT:
                        if (comparison < 0) {
                            return mail.getRecipients();
                        }
                        break;
                    case LE:
                        if (comparison <= 0) {
                            return mail.getRecipients();
                        }
                        break;
                    case EQ:
                        if (comparison == 0) {
                            return mail.getRecipients();
                        }
                        break;
                    case GE:
                        if (comparison >= 0) {
                            return mail.getRecipients();
                        }
                        break;
                    case GT:
                        if (comparison > 0) {
                            return mail.getRecipients();
                        }
                        break;
                    default:
                        // should never get here
                        throw new IllegalStateException("Unknown comparisonOperator" + comparisonOperator);
                }
            }
            catch (NumberFormatException nfe) {
                throw new MessagingException("Bad header value found in message: \"" + headerArray[0] + "\"", nfe);
            }
        }
        
        return null;
    }
}
