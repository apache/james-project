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

import org.apache.mailet.base.FlowedMessageUtils;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;

import javax.mail.MessagingException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Remove (best effort to) the hardcoded wrapping from a message.<br>
 * If the text is  "format=flowed" then deflows the text. Otherwise it forces a dewrap of the text.
 * </p>
 * <p>Parameters:<br> 
 * quotewidth - when we try to dewrap e quoted text it helps knowing the original
 * with, so we can reconstruct "wrapped wraps" created by multiple wrappings by clients with
 * different original width or simply to the add of the heading ">" that increase the line
 * length.<br>
 * The value should be "WIDTH+X" if the original length is known, "-X" otherwise.
 * In the latter case the length of the longer line will be used.
 * X is the tollerance needed for the quoting chars: if the original width is known the suggested
 * value for X is 2 (because of "> " prefix), otherwise it is suggested to increase it to a value 
 * like 10 (-10)</p>
 * 
 * <p>
 * In summary, if the original wrap is known (for example 76, for flowed messages)<br>
 *  <code>quotewidth = 78</code><br>
 * Otherwise<br>
 *  <code>quotewidth = -10</code>
 * </p>
 */
public class UnwrapText extends GenericMailet {
    public final static String PARAMETER_NAME_QUOTEWIDTH = "quotewidth";
    
    private int quotewidth;
    
    /**
     * returns a String describing this mailet.
     * 
     * @return A desciption of this mailet
     */
    public String getMailetInfo() {
        return "UnwrapText";
    }

    public void init() throws MailetException {
        quotewidth = Integer.parseInt(getInitParameter(PARAMETER_NAME_QUOTEWIDTH, "-10"));
    }

    public void service(Mail mail) throws MailetException {
        try {
            // TODO replace non standard quotes (at least "> " with ">", otherwise the widely used  "> > >" will not work.
            
            if (FlowedMessageUtils.isFlowedTextMessage(mail.getMessage()))
                FlowedMessageUtils.deflowMessage(mail.getMessage());
            
            else {
                Object o = mail.getMessage().getContent();
                if (o instanceof String) {
                    String unwrapped = unwrap((String) o, quotewidth);
                    mail.getMessage().setContent(unwrapped, mail.getMessage().getContentType());
                    mail.getMessage().saveChanges();
                }
            }
            
        } catch (MessagingException e) {
            throw new MailetException("Could not unwrap message", e);
            
        } catch (IOException e) {
            throw new MailetException("Could not unwrap message", e);
        }
        
    }
    
    public static String unwrap(String text) {
        return unwrap(text, - 10);
    }

    public static String unwrap(String text, int qwidth) {
        String[] lines = text.split("\r\n|\n", -1);
        
        //P1: Manage spaces without trims
        Pattern p1 = Pattern.compile("([> ]*)(.*[^ .?!][ ]*)$", 0);
        
        //P2: Quotation char at the begin of a line and the first word starts with a lowercase char or a number. The word ends with a space, a tab or a lineend. 
        Pattern p2 = Pattern.compile("^([> ]*)(([a-z\u00E0\u00E8\u00E9\u00EC\u00F2\u00F9][^ \t\r\n]*|[0-9][0-9,.]*)([ \t].*$|$))", 0);
        
        // Width computation
        int width = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            String l = lines[i].trim();
            if (l.length() > width) width = l.length();
        }
        
        if (width < 40) return text;
        if (qwidth < 0) qwidth = width - qwidth;
        
        StringBuilder result = new StringBuilder();
        int prevWrapped = 0;
        for (int i = 0; i < lines.length; i++) {
            if (prevWrapped != 0) {
                if (prevWrapped > 0 ) {
                    if (result.charAt(result.length() - 1) != ' ') result.append(" ");
                }
                else result.append("\r\n");
            }
            String l = lines[i];
            Matcher m1 = p1.matcher(l);
            Matcher m2 = i < lines.length - 1 ? p2.matcher(lines[i + 1]) : null;
            boolean b;
            int w;
            // if patterns match, the quote level are identical and if the line length added to the length of the following word is greater than width then it is a wrapped line.
            if (m1.matches() && i < lines.length - 1 && m2.matches() && (
                    // The following line has the same quoting of the previous.
                    ((b = m1.group(1).trim().equals(m2.group(1).trim())) && l.length() + m2.group(3).length() + 1 > width)
                    ||
                    // The following line has no quoting (while the previous yes)
                    (!b && m2.group(1).trim().equals("") && (w = l.length() + m2.group(2).trim().length() + 1) > width && w <= qwidth)
                )) {
                
                if (b) {
                    if (prevWrapped > 0 && m1.groupCount() >= 2) result.append(m1.group(2));
                    else result.append(l);
                    prevWrapped = 1;
                    
                } else {
                    lines[i + 1] = l + (l.charAt(l.length() - 1) != ' ' ? " " : "") + m2.group(2).trim();
                    // Revert the previous append
                    if (prevWrapped != 0) {
                        if (prevWrapped > 0) result.deleteCharAt(result.length() - 1);
                        else result.delete(result.length() - 2, result.length());
                    }
                }
                
            } else {
                Matcher m3 = p2.matcher(l);
                if (prevWrapped > 0 && m3.matches()) result.append(m3.group(2));
                else result.append(lines[i]);
                prevWrapped = -1;
            }
        }
        
        return result.toString();
    }
    
}
