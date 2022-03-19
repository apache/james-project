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

package org.apache.mailet.base;

import java.io.IOException;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Manages texts encoded as <code>text/plain; format=flowed</code>.</p>
 * <p>As a reference see:</p>
 * <ul>
 * <li><a href='http://www.rfc-editor.org/rfc/rfc2646.txt'>RFC2646</a></li>
 * <li><a href='http://www.rfc-editor.org/rfc/rfc3676.txt'>RFC3676</a> (new method with DelSP support).
 * </ul>
 * <h4>Note</h4>
 * <ul>
 * <li>In order to decode, the input text must belong to a mail with headers similar to:
 *   Content-Type: text/plain; charset="CHARSET"; [delsp="yes|no"; ]format="flowed"
 *   (the quotes around CHARSET are not mandatory).
 *   Furthermore the header Content-Transfer-Encoding MUST NOT BE Quoted-Printable
 *   (see RFC3676 paragraph 4.2).(In fact this happens often for non 7bit messages).
 * </li>
 * <li>When encoding the input text will be changed eliminating every space found before CRLF,
 *   otherwise it won't be possible to recognize hard breaks from soft breaks.
 *   In this scenario encoding and decoding a message will not return a message identical to 
 *   the original (lines with hard breaks will be trimmed)
 * </li>
 * </ul>
 */
public final class FlowedMessageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowedMessageUtils.class);

    public static final char RFC2646_SPACE = ' ';
    public static final char RFC2646_QUOTE = '>';
    public static final String RFC2646_SIGNATURE = "-- ";
    public static final String RFC2646_CRLF = "\r\n";
    public static final String RFC2646_FROM = "From ";
    public static final int RFC2646_WIDTH = 78;

    private static final char CR = '\r';
    private static final char LF = '\n';

    private FlowedMessageUtils() {
        // this class cannot be instantiated
    }
    
    /**
     * Decodes a text previously wrapped using "format=flowed".
     */
    public static String deflow(String text, boolean delSp) {
        String[] lines = text.split("\r\n|\n", -1);
        StringBuffer result = null;
        StringBuffer resultLine = new StringBuffer();
        int resultLineQuoteDepth = 0;
        boolean resultLineFlowed = false;
        // One more cycle, to close the last line
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : null;
            int actualQuoteDepth = 0;
            
            if (line != null) {
                if (line.equals(RFC2646_SIGNATURE)) {
                    // signature handling (the previous line is not flowed)
                    resultLineFlowed = false;
                } else if (line.length() > 0 && line.charAt(0) == RFC2646_QUOTE) {
                    // Quote
                    actualQuoteDepth = 1;
                    while (actualQuoteDepth < line.length() && line.charAt(actualQuoteDepth) == RFC2646_QUOTE) {
                        actualQuoteDepth++;
                    }
                    // if quote-depth changes wrt the previous line then this is not flowed
                    if (resultLineQuoteDepth != actualQuoteDepth) {
                        resultLineFlowed = false;
                    }
                    line = line.substring(actualQuoteDepth);
                    
                } else {
                    // if quote-depth changes wrt the first line then this is not flowed
                    if (resultLineQuoteDepth > 0) {
                        resultLineFlowed = false;
                    }
                }
                    
                if (line.length() > 0 && line.charAt(0) == RFC2646_SPACE) {
                    // Line space-stuffed
                    line = line.substring(1);
                }
                
            // if the previous was the last then it was not flowed
            } else {
                resultLineFlowed = false;
            }

            // Add the PREVIOUS line.
            // This often will find the flow looking for a space as the last char of the line.
            // With quote changes or signatures it could be the following line to void the flow.
            if (!resultLineFlowed && i > 0) {
                if (resultLineQuoteDepth > 0) {
                    resultLine.insert(0, RFC2646_SPACE);
                }
                for (int j = 0; j < resultLineQuoteDepth; j++) {
                    resultLine.insert(0, RFC2646_QUOTE);
                }
                if (result == null) {
                    result = new StringBuffer();
                } else {
                    result.append(RFC2646_CRLF);
                }
                result.append(resultLine.toString());
                resultLine = new StringBuffer();
                resultLineFlowed = false;
            }
            resultLineQuoteDepth = actualQuoteDepth;
            
            if (line != null) {
                if (!line.equals(RFC2646_SIGNATURE) && line.endsWith("" + RFC2646_SPACE) && i < lines.length - 1) {
                    // Line flowed (NOTE: for the split operation the line having i == lines.length is the last that does not end with RFC2646_CRLF)
                    if (delSp) {
                        line = line.substring(0, line.length() - 1);
                    }
                    resultLineFlowed = true;
                } else {
                    resultLineFlowed = false;
                }
                
                resultLine.append(line);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Obtains the content of the encoded message, if previously encoded as <code>format=flowed</code>.
     */
    public static String deflow(Message m) throws IOException, MessagingException {
        ContentType ct = new ContentType(m.getContentType());
        String format = ct.getParameter("format");
        if (ct.getBaseType().equals("text/plain") && format != null && format.equalsIgnoreCase("flowed")) {
            String delSp = ct.getParameter("delsp");
            return deflow((String) m.getContent(), delSp != null && delSp.equalsIgnoreCase("yes"));
            
        } else if (ct.getPrimaryType().equals("text")) {
            return (String) m.getContent();
        } else {
            return null;
        }
    }
    
    /**
     * If the message is <code>format=flowed</code> 
     * set the encoded version as message content.
     */
    public static void deflowMessage(Message m) throws MessagingException, IOException {
        ContentType ct = new ContentType(m.getContentType());
        String format = ct.getParameter("format");
        if (ct.getBaseType().equals("text/plain") && format != null && format.equalsIgnoreCase("flowed")) {
            String delSp = ct.getParameter("delsp");
            String deflowed = deflow((String) m.getContent(), delSp != null && delSp.equalsIgnoreCase("yes"));
            
            ct.getParameterList().remove("format");
            ct.getParameterList().remove("delsp");
            
            if (ct.toString().contains("flowed")) {
                LOGGER.error("FlowedMessageUtils dind't remove the flowed correctly");
            }

            m.setContent(deflowed, ct.toString());
            m.saveChanges();
        }
    }
    
    
    /**
     * Encodes a text (using standard with).
     */
    public static String flow(String text, boolean delSp) {
        return flow(text, delSp, RFC2646_WIDTH);
    }

    /**
     * Encodes a text.
     */
    public static String flow(String text, boolean delSp, int width) {
        int lastIndex = text.length() - 1;

        StringBuilder result = new StringBuilder();
        int lineStartIndex = 0;

        while (lineStartIndex <= lastIndex) {
            int quoteDepth = 0;
            while (lineStartIndex <= lastIndex && text.charAt(lineStartIndex) == RFC2646_QUOTE) {
                quoteDepth++;
                lineStartIndex++;
            }

            if (quoteDepth > 0 && lineStartIndex + 1 <= lastIndex && text.charAt(lineStartIndex) == RFC2646_SPACE) {
                lineStartIndex++;
            }

            // We support both LF and CRLF line endings. To cover both cases we search for LF.
            int lineFeedIndex = text.indexOf(LF, lineStartIndex);
            boolean lineBreakFound = lineFeedIndex != -1;
            int lineEndIndex = lineBreakFound ? lineFeedIndex : text.length();
            int nextLineStartIndex = lineBreakFound ? lineFeedIndex + 1 : text.length();

            if (lineBreakFound && lineEndIndex > 0 && text.charAt(lineEndIndex - 1) == CR) {
                lineEndIndex--;
            }

            // Special case: signature separator
            if (lineEndIndex - lineStartIndex == RFC2646_SIGNATURE.length() &&
                text.regionMatches(lineStartIndex, RFC2646_SIGNATURE, 0, RFC2646_SIGNATURE.length())
            ) {
                if (quoteDepth > 0) {
                    for (int i = 0; i < quoteDepth; i++) {
                        result.append(RFC2646_QUOTE);
                    }
                    result.append(RFC2646_SPACE);
                }
                result.append(RFC2646_SIGNATURE);
                result.append(RFC2646_CRLF);

                lineStartIndex = nextLineStartIndex;
                continue;
            }

            // Remove trailing spaces
            while (lineEndIndex > lineStartIndex && text.charAt(lineEndIndex - 1) == RFC2646_SPACE) {
                lineEndIndex--;
            }

            // Special case: a quoted line without any content
            if (lineStartIndex == lineEndIndex && quoteDepth > 0) {
                for (int i = 0; i < quoteDepth; i++) {
                    result.append(RFC2646_QUOTE);
                }
            }

            while (lineStartIndex < lineEndIndex) {
                int prefixLength = 0;
                if (quoteDepth == 0) {
                    if (text.charAt(lineStartIndex) == RFC2646_SPACE || text.charAt(lineStartIndex) == RFC2646_QUOTE ||
                        lineEndIndex - lineStartIndex >= RFC2646_FROM.length() &&
                            text.regionMatches(lineStartIndex, RFC2646_FROM, 0, RFC2646_FROM.length())
                    ) {
                        // This line needs space stuffing
                        result.append(RFC2646_SPACE);
                        prefixLength = 1;
                    }
                } else {
                    for (int i = 0; i < quoteDepth; i++) {
                        result.append(RFC2646_QUOTE);
                    }
                    result.append(RFC2646_SPACE);
                    prefixLength = quoteDepth + 1;
                }

                int remainingWidth = width - prefixLength - 1;
                if (delSp) {
                    remainingWidth--;
                }
                if (remainingWidth < 0) {
                    remainingWidth = 0;
                }

                int breakIndex = lineStartIndex + remainingWidth;
                if (breakIndex >= lineEndIndex) {
                    breakIndex = lineEndIndex;
                } else {
                    while (breakIndex >= lineStartIndex &&
                        (delSp && isAlphaChar(text, breakIndex) || !delSp && text.charAt(breakIndex) != RFC2646_SPACE)
                    ) {
                        breakIndex--;
                    }

                    if (breakIndex < lineStartIndex) {
                        // Not able to cut a word: skip to word end even if greater than the max width
                        breakIndex = lineStartIndex + remainingWidth;
                        while (breakIndex < lineEndIndex &&
                            ((delSp && isAlphaChar(text, breakIndex)) ||
                                (!delSp && text.charAt(breakIndex) != RFC2646_SPACE))
                        ) {
                            breakIndex++;
                        }
                    }

                    breakIndex++;

                    if (breakIndex >= lineEndIndex) {
                        breakIndex = lineEndIndex;
                    } else if (Character.isHighSurrogate(text.charAt(breakIndex - 1))) {
                        // Don't break surrogate pairs apart
                        breakIndex++;
                    }
                }

                result.append(text, lineStartIndex, breakIndex);

                if (breakIndex < lineEndIndex) {
                    if (delSp) {
                        result.append(RFC2646_SPACE);
                    }
                    result.append(RFC2646_CRLF);
                }

                lineStartIndex = breakIndex;
            }

            if (lineBreakFound) {
                result.append(RFC2646_CRLF);
            }

            lineStartIndex = nextLineStartIndex;
        }

        return result.toString();
    }
    
    /**
     * Encodes the input text and sets it as the new message content.
     */
    public static void setFlowedContent(Message m, String text, boolean delSp) throws MessagingException {
        setFlowedContent(m, text, delSp, RFC2646_WIDTH, true, null);
    }
    
    /**
     * Encodes the input text and sets it as the new message content.
     */
    public static void setFlowedContent(Message m, String text, boolean delSp, int width, boolean preserveCharset, String charset) throws MessagingException {
        String coded = flow(text, delSp, width);
        if (preserveCharset) {
            ContentType ct = new ContentType(m.getContentType());
            charset = ct.getParameter("charset");
        }
        ContentType ct = new ContentType();
        ct.setPrimaryType("text");
        ct.setSubType("plain");
        if (charset != null) {
            ct.setParameter("charset", charset);
        }
        ct.setParameter("format", "flowed");
        if (delSp) {
            ct.setParameter("delsp", "yes");
        }
        m.setContent(coded, ct.toString());
        m.saveChanges();
    }
    
    /**
     * Encodes the message content (if text/plain).
     */
    public static void flowMessage(Message m, boolean delSp) throws MessagingException, IOException {
        flowMessage(m, delSp, RFC2646_WIDTH);
    }

    /**
     * Encodes the message content (if text/plain).
     */
    public static void flowMessage(Message m, boolean delSp, int width) throws MessagingException, IOException {
        ContentType ct = new ContentType(m.getContentType());
        if (!ct.getBaseType().equals("text/plain")) {
            return;
        }
        String format = ct.getParameter("format");
        String text = format != null && format.equals("flowed") ? deflow(m) : (String) m.getContent();
        String coded = flow(text, delSp, width);
        ct.setParameter("format", "flowed");
        if (delSp) {
            ct.setParameter("delsp", "yes");
        }
        m.setContent(coded, ct.toString());
        m.saveChanges();
    }
    
    /**
     * Checks whether the char is part of a word.
     * <p>RFC assert a word cannot be splitted (even if the length is greater than the maximum length).
     */
    public static boolean isAlphaChar(String text, int index) {
        // Note: a list of chars is available here:
        // http://www.zvon.org/tmRFC/RFC2646/Output/index.html
        char c = text.charAt(index); 
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * Checks whether the input message is <code>format=flowed</code>.
     */
    public static boolean isFlowedTextMessage(Message m) throws MessagingException {
        ContentType ct = new ContentType(m.getContentType());
        String format = ct.getParameter("format");
        return ct.getBaseType().equals("text/plain") && format != null && format.equalsIgnoreCase("flowed");
    }
}
