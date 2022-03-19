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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;

import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keep only the text part of a message.
 * <p>If the message is text only then it doesn't touch it, if it is a multipart it
 * transform it a in plain text message with the first text part found.<br>
 * - text/plain<br>
 * - text/html => with a conversion to text only<br>
 * - text/* as is.</p>
 */
@Experimental
public class OnlyText extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnlyText.class);

    private static final String PARAMETER_NAME_NOTEXT_PROCESSOR = "NoTextProcessor";

    private String optionsNotextProcessor = null;
    private final HashMap<String, String> charMap = new HashMap<>();

    @Override
    public String getMailetInfo() {
        return "OnlyText";
    }

    @Override
    public void init() throws MailetException {
        optionsNotextProcessor = getInitParameter(PARAMETER_NAME_NOTEXT_PROCESSOR);
        initEntityTable();
    }

    private int[] process(Mail mail, Multipart mp, int found, int htmlPart, int stringPart) throws MessagingException, IOException {
        for (int i = 0; found < 0 && i < mp.getCount(); i++) {
            Object content = null;
            try {
                content = mp.getBodyPart(i).getContent();
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("Caught error in a text/plain part, skipping...", e);
            }
            if (content != null) {
                if (mp.getBodyPart(i).isMimeType("text/plain")) {
                    setContentFromPart(mail.getMessage(), mp.getBodyPart(i), null, false);
                    found = 1;
                } else if (htmlPart == -1 && mp.getBodyPart(i).isMimeType("text/html")) {
                    htmlPart = i;
                } else if (stringPart == -1 && content instanceof String) {
                    stringPart = i;
                } else if (content instanceof Multipart) {
                    int[] res = process(mail, (Multipart) content, found, htmlPart, stringPart);
                    found = res[0];
                    htmlPart = res[1];
                    stringPart = res[2];
                }
            }
        }

        return new int[]{found, htmlPart, stringPart};

    }

    @Override
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


                if (found < 0 && optionsNotextProcessor != null) {
                    mail.setState(optionsNotextProcessor);
                }

            } else if (!(content instanceof String) && optionsNotextProcessor != null) {
                mail.setState(optionsNotextProcessor);
            } else if (mail.getMessage().isMimeType("text/html")) {
                setContentFromPart(mail.getMessage(), mail.getMessage(), html2Text((String) mail.getMessage().getContent()), true);
            }

        } catch (IOException | MessagingException e) {
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
        if (h != null && h.length > 0) {
            m.setHeader("Content-Transfer-Encoding", h[0]);
        }
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

            if (c == '&' && lastAmp == -1) {
                lastAmp = buffer.length();
            } else if (c == ';' && (lastAmp > -1)) { // && (lastAmp > (buffer.length() - 7))) { // max: &#xxxx;
                if (charMap.containsKey(buffer.toString())) {
                    res.append(charMap.get(buffer.toString()));
                } else {
                    res.append("&").append(buffer.toString()).append(";");
                }
                lastAmp = -1;
                buffer = new StringBuffer();
            } else if (lastAmp == -1) {
                res.append(c);
            } else {
                buffer.append(c);
            }
        }
        return res.toString();
    }

    private void initEntityTable() {
        for (int index = 11; index < 32; index++) {
            charMap.put("#0" + index, String.valueOf((char) index));
        }
        for (int index = 32; index < 128; index++) {
            charMap.put("#" + index, String.valueOf((char) index));
        }
        for (int index = 128; index < 256; index++) {
            charMap.put("#" + index, String.valueOf((char) index));
        }

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

        charMap.put("Ouml", "Ö");
        charMap.put("Oacute", "Ó");
        charMap.put("iquest", "¿");
        charMap.put("yuml", "ÿ");
        charMap.put("cent", "¢");
        charMap.put("deg", "°");
        charMap.put("aacute", "á");
        charMap.put("uuml", "ü");
        charMap.put("Otilde", "Õ");
        charMap.put("Iacute", "Í");
        charMap.put("frac12", "½");
        charMap.put("atilde", "ã");
        charMap.put("ordf", "ª");
        charMap.put("sup2", "²");
        charMap.put("sup3", "³");
        charMap.put("frac14", "¼");
        charMap.put("ucirc", "û");
        charMap.put("brvbar", "¦");
        charMap.put("reg", "®");
        charMap.put("sup1", "¹");
        charMap.put("THORN", "Þ");
        charMap.put("ordm", "º");
        charMap.put("eth", "ð");
        charMap.put("Acirc", "Â");
        charMap.put("aring", "å");
        charMap.put("Uacute", "Ú");
        charMap.put("oslash", "ø");
        charMap.put("eacute", "é");
        charMap.put("agrave", "à");
        charMap.put("Ecirc", "Ê");
        charMap.put("laquo", "«");
        charMap.put("Igrave", "Ì");
        charMap.put("Agrave", "À");
        charMap.put("macr", "¯");
        charMap.put("Ucirc", "Û");
        charMap.put("igrave", "ì");
        charMap.put("ouml", "ö");
        charMap.put("iexcl", "¡");
        charMap.put("otilde", "õ");
        charMap.put("ugrave", "ù");
        charMap.put("Aring", "Å");
        charMap.put("Ograve", "Ò");
        charMap.put("Ugrave", "Ù");
        charMap.put("ograve", "ò");
        charMap.put("acute", "´");
        charMap.put("ecirc", "ê");
        charMap.put("euro", "€");
        charMap.put("uacute", "ú");
        charMap.put("shy", "\\u00AD");
        charMap.put("cedil", "¸");
        charMap.put("raquo", "»");
        charMap.put("Atilde", "Ã");
        charMap.put("Iuml", "Ï");
        charMap.put("iacute", "í");
        charMap.put("ocirc", "ô");
        charMap.put("curren", "¤");
        charMap.put("frac34", "¾");
        charMap.put("Euml", "Ë");
        charMap.put("szlig", "ß");
        charMap.put("pound", "£");
        charMap.put("not", "¬");
        charMap.put("AElig", "Æ");
        charMap.put("times", "×");
        charMap.put("Aacute", "Á");
        charMap.put("Icirc", "Î");
        charMap.put("para", "¶");
        charMap.put("uml", "¨");
        charMap.put("oacute", "ó");
        charMap.put("copy", "©");
        charMap.put("Eacute", "É");
        charMap.put("Oslash", "Ø");
        charMap.put("divid", "÷");
        charMap.put("aelig", "æ");
        charMap.put("euml", "ë");
        charMap.put("Ocirc", "Ô");
        charMap.put("yen", "¥");
        charMap.put("ntilde", "ñ");
        charMap.put("Ntilde", "Ñ");
        charMap.put("thorn", "þ");
        charMap.put("yacute", "ý");
        charMap.put("Auml", "Ä");
        charMap.put("Yacute", "Ý");
        charMap.put("ccedil", "ç");
        charMap.put("micro", "µ");
        charMap.put("Ccedil", "Ç");
        charMap.put("sect", "§");
        charMap.put("icirc", "î");
        charMap.put("middot", "·");
        charMap.put("Uuml", "Ü");
        charMap.put("ETH", "Ð");
        charMap.put("egrave", "è");
        charMap.put("iuml", "ï");
        charMap.put("plusmn", "±");
        charMap.put("acirc", "â");
        charMap.put("auml", "ä");
        charMap.put("Egrave", "È");
    }
}
