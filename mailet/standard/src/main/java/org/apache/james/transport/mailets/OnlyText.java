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

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.ContentType;
import java.io.IOException;
import java.util.HashMap;

/**
 * Keep only the text part of a message.
 * <p>If the message is text only then it doesn't touch it, if it is a multipart it
 * transform it a in plain text message with the first text part found.<br>
 * - text/plain<br>
 * - text/html => with a conversion to text only<br>
 * - text/* as is.</p>
 */
public class OnlyText extends GenericMailet {
    private static final String PARAMETER_NAME_NOTEXT_PROCESSOR = "NoTextProcessor";

    private String optionsNotextProcessor = null;
    private final HashMap<String, String> charMap = new HashMap<String, String>();

    /**
     * returns a String describing this mailet.
     *
     * @return A desciption of this mailet
     */
    public String getMailetInfo() {
        return "OnlyText";
    }

    public void init() throws MailetException {
        optionsNotextProcessor = getInitParameter(PARAMETER_NAME_NOTEXT_PROCESSOR);
        initEntityTable();
    }

    private int[] process(Mail mail, Multipart mp, int found, int htmlPart, int stringPart) throws MessagingException, IOException {
        for (int i = 0; found < 0 && i < mp.getCount(); i++) {
            Object content = null;
            try {
                content = mp.getBodyPart(i).getContent();
            } catch (java.io.UnsupportedEncodingException e) {
                log("Caught error [" + e.getMessage() + "] in a text/plain part, skipping...");
            }
            if (content != null) {
                if (mp.getBodyPart(i).isMimeType("text/plain")) {
                    setContentFromPart(mail.getMessage(), mp.getBodyPart(i), null, false);
                    found = 1;
                } else if (htmlPart == -1 && mp.getBodyPart(i).isMimeType("text/html"))
                    htmlPart = i;

                else if (stringPart == -1 && content instanceof String)
                    stringPart = i;

                else if (content instanceof Multipart) {
                    int[] res = process(mail, (Multipart) content, found, htmlPart, stringPart);
                    found = res[0];
                    htmlPart = res[1];
                    stringPart = res[2];
                }
            }
        }

        return new int[]{found, htmlPart, stringPart};

    }

    public void service(Mail mail) throws MailetException {
        try {
            Object content = mail.getMessage().getContent();
            if (content instanceof Multipart) {
                Multipart mp = (Multipart) content;

                int found = -1;
                int htmlPart = -1;
                int stringPart = -1;
                int[] res = process(mail, (Multipart) content, found, htmlPart, stringPart);
                found = res[0];
                htmlPart = res[1];
                stringPart = res[2];

                if (found < 0 && htmlPart != -1) {
                    setContentFromPart(mail.getMessage(), mp.getBodyPart(htmlPart), html2Text((String) mp.getBodyPart(htmlPart).getContent()), true);
                    found = 1;
                }

                if (found < 0 && stringPart != -1) {
                    setContentFromPart(mail.getMessage(), mp.getBodyPart(htmlPart), null, false);
                    found = 1;
                }


                if (found < 0 && optionsNotextProcessor != null) mail.setState(optionsNotextProcessor);

            } else if (!(content instanceof String) && optionsNotextProcessor != null)
                mail.setState(optionsNotextProcessor);

            else if (mail.getMessage().isMimeType("text/html")) {
                setContentFromPart(mail.getMessage(), mail.getMessage(), html2Text((String) mail.getMessage().getContent()), true);
            }

        } catch (IOException e) {
            throw new MailetException("Failed fetching text part", e);

        } catch (MessagingException e) {
            throw new MailetException("Failed fetching text part", e);
        }
    }

    private static void setContentFromPart(Message m, Part p, String newText, boolean setTextPlain) throws MessagingException, IOException {
        String contentType = p.getContentType();
        if (setTextPlain) {
            ContentType ct = new ContentType(contentType);
            ct.setPrimaryType("text");
            ct.setSubType("plain");
            contentType = ct.toString();
        }
        m.setContent(newText != null ? newText : p.getContent(), contentType);
        String[] h = p.getHeader("Content-Transfer-Encoding");
        if (h != null && h.length > 0) m.setHeader("Content-Transfer-Encoding", h[0]);
        m.saveChanges();
    }

    public String html2Text(String html) {
        return decodeEntities(html
                .replaceAll("\\<([bB][rR]|[dD][lL])[ ]*[/]*[ ]*\\>", "\n")
                .replaceAll("\\</([pP]|[hH]5|[dD][tT]|[dD][dD]|[dD][iI][vV])[ ]*\\>", "\n")
                .replaceAll("\\<[lL][iI][ ]*[/]*[ ]*\\>", "\n* ")
                .replaceAll("\\<[dD][dD][ ]*[/]*[ ]*\\>", " - ")
                .replaceAll("\\<.*?\\>", ""));
    }

    public String decodeEntities(String data) {
        StringBuffer buffer = new StringBuffer();
        StringBuilder res = new StringBuilder();
        int lastAmp = -1;
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);

            if (c == '&' && lastAmp == -1) lastAmp = buffer.length();
            else if (c == ';' && (lastAmp > -1)) { // && (lastAmp > (buffer.length() - 7))) { // max: &#xxxx;
                if (charMap.containsKey(buffer.toString())) res.append(charMap.get(buffer.toString()));
                else res.append("&").append(buffer.toString()).append(";");
                lastAmp = -1;
                buffer = new StringBuffer();
            } else if (lastAmp == -1) res.append(c);
            else buffer.append(c);
        }
        return res.toString();
    }

    private void initEntityTable() {
        for (int index = 11; index < 32; index++) charMap.put("#0" + index, String.valueOf((char) index));
        for (int index = 32; index < 128; index++) charMap.put("#" + index, String.valueOf((char) index));
        for (int index = 128; index < 256; index++) charMap.put("#" + index, String.valueOf((char) index));

        // A complete reference is here:
        // http://en.wikipedia.org/wiki/List_of_XML_and_HTML_character_entity_references

        charMap.put("#09", "\t");
        charMap.put("#10", "\n");
        charMap.put("#13", "\r");
        charMap.put("#60", "<");
        charMap.put("#62", ">");

        charMap.put("lt", "<");
        charMap.put("gt", ">");
        charMap.put("amp", "&");
        charMap.put("nbsp", " ");
        charMap.put("quot", "\"");

        charMap.put("iexcl", "\u00A1");
        charMap.put("cent", "\u00A2");
        charMap.put("pound", "\u00A3");
        charMap.put("curren", "\u00A4");
        charMap.put("yen", "\u00A5");
        charMap.put("brvbar", "\u00A6");
        charMap.put("sect", "\u00A7");
        charMap.put("uml", "\u00A8");
        charMap.put("copy", "\u00A9");
        charMap.put("ordf", "\u00AA");
        charMap.put("laquo", "\u00AB");
        charMap.put("not", "\u00AC");
        charMap.put("shy", "\u00AD");
        charMap.put("reg", "\u00AE");
        charMap.put("macr", "\u00AF");
        charMap.put("deg", "\u00B0");
        charMap.put("plusmn", "\u00B1");
        charMap.put("sup2", "\u00B2");
        charMap.put("sup3", "\u00B3");

        charMap.put("acute", "\u00B4");
        charMap.put("micro", "\u00B5");
        charMap.put("para", "\u00B6");
        charMap.put("middot", "\u00B7");
        charMap.put("cedil", "\u00B8");
        charMap.put("sup1", "\u00B9");
        charMap.put("ordm", "\u00BA");
        charMap.put("raquo", "\u00BB");
        charMap.put("frac14", "\u00BC");
        charMap.put("frac12", "\u00BD");
        charMap.put("frac34", "\u00BE");
        charMap.put("iquest", "\u00BF");

        charMap.put("Agrave", "\u00C0");
        charMap.put("Aacute", "\u00C1");
        charMap.put("Acirc", "\u00C2");
        charMap.put("Atilde", "\u00C3");
        charMap.put("Auml", "\u00C4");
        charMap.put("Aring", "\u00C5");
        charMap.put("AElig", "\u00C6");
        charMap.put("Ccedil", "\u00C7");
        charMap.put("Egrave", "\u00C8");
        charMap.put("Eacute", "\u00C9");
        charMap.put("Ecirc", "\u00CA");
        charMap.put("Euml", "\u00CB");
        charMap.put("Igrave", "\u00CC");
        charMap.put("Iacute", "\u00CD");
        charMap.put("Icirc", "\u00CE");
        charMap.put("Iuml", "\u00CF");

        charMap.put("ETH", "\u00D0");
        charMap.put("Ntilde", "\u00D1");
        charMap.put("Ograve", "\u00D2");
        charMap.put("Oacute", "\u00D3");
        charMap.put("Ocirc", "\u00D4");
        charMap.put("Otilde", "\u00D5");
        charMap.put("Ouml", "\u00D6");
        charMap.put("times", "\u00D7");
        charMap.put("Oslash", "\u00D8");
        charMap.put("Ugrave", "\u00D9");
        charMap.put("Uacute", "\u00DA");
        charMap.put("Ucirc", "\u00DB");
        charMap.put("Uuml", "\u00DC");
        charMap.put("Yacute", "\u00DD");
        charMap.put("THORN", "\u00DE");
        charMap.put("szlig", "\u00DF");

        charMap.put("agrave", "\u00E0");
        charMap.put("aacute", "\u00E1");
        charMap.put("acirc", "\u00E2");
        charMap.put("atilde", "\u00E3");
        charMap.put("auml", "\u00E4");
        charMap.put("aring", "\u00E5");
        charMap.put("aelig", "\u00E6");
        charMap.put("ccedil", "\u00E7");
        charMap.put("egrave", "\u00E8");
        charMap.put("eacute", "\u00E9");
        charMap.put("ecirc", "\u00EA");
        charMap.put("euml", "\u00EB");
        charMap.put("igrave", "\u00EC");
        charMap.put("iacute", "\u00ED");
        charMap.put("icirc", "\u00EE");
        charMap.put("iuml", "\u00EF");

        charMap.put("eth", "\u00F0");
        charMap.put("ntilde", "\u00F1");
        charMap.put("ograve", "\u00F2");
        charMap.put("oacute", "\u00F3");
        charMap.put("ocirc", "\u00F4");
        charMap.put("otilde", "\u00F5");
        charMap.put("ouml", "\u00F6");
        charMap.put("divid", "\u00F7");
        charMap.put("oslash", "\u00F8");
        charMap.put("ugrave", "\u00F9");
        charMap.put("uacute", "\u00FA");
        charMap.put("ucirc", "\u00FB");
        charMap.put("uuml", "\u00FC");
        charMap.put("yacute", "\u00FD");
        charMap.put("thorn", "\u00FE");
        charMap.put("yuml", "\u00FF");
        charMap.put("euro", "\u0080");
    }
}
