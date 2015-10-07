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

import org.apache.james.transport.mailets.AddFooter;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import junit.framework.TestCase;

/**
 * Test encoding issues
 * 
 * This test should also be run with the following JVM options to be sure it tests:
 * "-Dfile.encoding=ASCII -Dmail.mime.charset=ANSI_X3.4-1968"
 */
public class AddFooterTest extends TestCase {

    public AddFooterTest(String arg0) throws UnsupportedEncodingException {
        super(arg0);
        
        /*
        
        String encoding = (new InputStreamReader(System.in)).getEncoding();
        System.out.println("System Encoding: "+encoding);
        System.out.println("Default Java Charset:"+MimeUtility.getDefaultJavaCharset());
        System.out.println("---------");
        String a = "\u20AC\u00E0"; // euro char followed by an italian a with an accent System.out.println(debugString(a,"UTF-8"));
        System.out.println(debugString(a,"UTF8"));
        System.out.println(debugString(a,"UTF-16"));
        System.out.println(debugString(a,"UNICODE"));
        System.out.println(debugString(a,"ISO-8859-15"));
        System.out.println(debugString(a,"ISO-8859-1"));
        System.out.println(debugString(a,"CP1252"));
        System.out.println(debugString(a,"ANSI_X3.4-1968"));
         
         */
    }

    private final static char[] hexchars = { '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public String debugString(String a, String charset)
            throws UnsupportedEncodingException {
        byte[] bytes = a.getBytes(charset);
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0)
                res.append("-");
            res.append(hexchars[((bytes[i] + 256) % 256) / 16]);
            res.append(hexchars[((bytes[i] + 256) % 256) % 16]);
        }
        res.append(" (");
        res.append(MimeUtility.mimeCharset(charset));
        res.append(" / ");
        res.append(MimeUtility.javaCharset(charset));
        res.append(")");
        return res.toString();
    }

    /*
     * Class under test for String getSubject()
     */
    public void testAddFooterTextPlain() throws MessagingException, IOException {

        // quoted printable mimemessage text/plain
        String asciisource = "Subject: test\r\nContent-Type: text/plain; charset=ISO-8859-15\r\nMIME-Version: 1.0\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nTest=E0 and one\r\n";

        String iso885915qpheader = "------ my footer =E0/=A4 ------";
        String footer = "------ my footer \u00E0/\u20AC ------";

        String res = processAddFooter(asciisource, footer);

        assertEquals(asciisource + iso885915qpheader, res);

    }

    public void testUnsupportedEncoding() throws MessagingException, IOException {

        // quoted printable mimemessage text/plain
        String asciisource = "Subject: test\r\nContent-Type: text/plain; charset=UNSUPPORTED_ENCODING\r\nMIME-Version: 1.0\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nTest=E0 and one\r\n";

        String footer = "------ my footer \u00E0/\u20AC ------";

        try {
            String res = processAddFooter(asciisource, footer);
            assertEquals(asciisource, res);
        } catch (Exception e) {
            fail("should not throw an exception: "+e.getMessage());
        }


    }

    /*
     * Test for      JAMES-443
     * This should not add the header and should leave the multipart/mixed Content-Type intact
     */
    public void testAddFooterMimeNestedUnsupportedMultipart() throws MessagingException, IOException {

        // quoted printable mimemessage text/plain
        String asciisource = "MIME-Version: 1.0\r\n"
            +"Content-Type: multipart/mixed; boundary=\"===============0204599088==\"\r\n"
                +"\r\n"
                +"This is a cryptographically signed message in MIME format.\r\n"
                +"\r\n"
                +"--===============0204599088==\r\n"
                +"Content-Type: multipart/unsupported; boundary=\"------------ms050404020900070803030808\"\r\n"
                +"\r\n"
                +"--------------ms050404020900070803030808\r\n"
                +"Content-Type: text/plain; charset=ISO-8859-1\r\n"
                +"\r\n"
                +"test\r\n"
                +"\r\n"
                +"--------------ms050404020900070803030808--\r\n"
                +"\r\n"
                +"--===============0204599088==--\r\n";
        // String asciisource = "Subject: test\r\nContent-Type: multipart/mixed; boundary=\"===============0204599088==\"\r\nMIME-Version: 1.0\r\n\r\nThis is a cryptographically signed message in MIME format.\r\n\r\n--===============0204599088==\r\nContent-Type: text/plain\r\n\r\ntest\r\n--===============0204599088==\r\nContent-Type: text/plain; charset=\"us-ascii\"\r\nMIME-Version: 1.0\r\nContent-Transfer-Encoding: 7bit\r\nContent-Disposition: inline\r\n\r\ntest\r\n--===============0204599088==--\r\n";

        String footer = "------ my footer \u00E0/\u20AC ------";

        String res = processAddFooter(asciisource, footer);

        assertEquals(asciisource, res);

    }
    
    /*
     * Test for      JAMES-368
     * AddFooter couldn't process mails which MimeType is multipart/related
     */
    public void testAddFooterMultipartRelated() throws MessagingException, IOException {

        // quoted printable mimemessage text/plain
        String asciisource = "MIME-Version: 1.0\r\n"
            +"Subject: test\r\n"
            +"Content-Type: multipart/related;\r\n"
            +"  boundary=\"------------050206010102010306090507\"\r\n"
            +"\r\n"
            +"--------------050206010102010306090507\r\n"
            +"Content-Type: text/html; charset=ISO-8859-15\r\n"
            +"Content-Transfer-Encoding: quoted-printable\r\n"
            +"\r\n"
            +"<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\r\n"
            +"<html>\r\n"
            +"<head>\r\n"
            +"<meta content=3D\"text/html;charset=3DISO-8859-15\" http-equiv=3D\"Content-Typ=\r\n"
            +"e\">\r\n"
            +"</head>\r\n"
            +"<body bgcolor=3D\"#ffffff\" text=3D\"#000000\">\r\n"
            +"<br>\r\n"
            +"<div class=3D\"moz-signature\">-- <br>\r\n"
            +"<img src=3D\"cid:part1.02060605.123@zzz.com\" border=3D\"0\"></div>\r\n";
        String asciifoot = "</body>\r\n"
            +"</html>\r\n"
            +"\r\n"
            +"--------------050206010102010306090507\r\n"
            +"Content-Type: image/gif\r\n"
            +"Content-Transfer-Encoding: base64\r\n"
            +"Content-ID: <part1.02060605.123@zzz.com>\r\n"
            +"Content-Disposition: inline;\r\n"
            +"\r\n"
            +"YQ==\r\n"
            +"--------------050206010102010306090507--\r\n";

        // String asciisource = "Subject: test\r\nContent-Type: multipart/mixed; boundary=\"===============0204599088==\"\r\nMIME-Version: 1.0\r\n\r\nThis is a cryptographically signed message in MIME format.\r\n\r\n--===============0204599088==\r\nContent-Type: text/plain\r\n\r\ntest\r\n--===============0204599088==\r\nContent-Type: text/plain; charset=\"us-ascii\"\r\nMIME-Version: 1.0\r\nContent-Transfer-Encoding: 7bit\r\nContent-Disposition: inline\r\n\r\ntest\r\n--===============0204599088==--\r\n";

        String footer = "------ my footer \u00E0/\u20AC ------";
        String expectedFooter = "<br>------ my footer =E0/=A4 ------";

        String res = processAddFooter(asciisource+asciifoot, footer);

        assertEquals(asciisource+expectedFooter+asciifoot, res);

    }
    
    

    /*
     * Class under test for String getSubject()
     */
    public void testAddFooterTextPlainISO8859() throws MessagingException, IOException {

        // quoted printable mimemessage text/plain
        String asciisource = "Subject: test\r\nContent-Type: text/plain; charset=iso-8859-15\r\nMIME-Version: 1.0\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nTest=E0 and one =A4\r\n";

        String iso885915qpheader = "------ my footer =E0/=A4 ------";
        String footer = "------ my footer \u00E0/\u20AC ------";

        String res = processAddFooter(asciisource, footer);

        assertEquals(asciisource + iso885915qpheader, res);

    }

    /*
     * Class under test for String getSubject()
     */
    public void testAddFooterMultipartAlternative() throws MessagingException,
            IOException {

        String sep = "--==--";
        String head = "Subject: test\r\nContent-Type: multipart/alternative;\r\n    boundary=\""
                + sep
                + "\"\r\nMIME-Version: 1.0\r\n";
        String content1 = "Content-Type: text/plain;\r\n    charset=\"ISO-8859-15\"\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nTest=E0 and @=80";
        String c2h = "Content-Type: text/html;\r\n    charset=\"CP1252\"\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n";
        String c2pre = "<html><body>test =80 ss";
        String c2post = "</body></html>";

        StringBuilder asciisource = new StringBuilder();
        asciisource.append(head);
        asciisource.append("\r\n--");
        asciisource.append(sep);
        asciisource.append("\r\n");
        asciisource.append(content1);
        asciisource.append("\r\n--");
        asciisource.append(sep);
        asciisource.append("\r\n");
        asciisource.append(c2h);
        asciisource.append(c2pre);
        asciisource.append(c2post);
        asciisource.append("\r\n--");
        asciisource.append(sep);
        asciisource.append("--\r\n");

        String iso885915qpheader = "------ my footer =E0/=A4 ------";
        String cp1252qpfooter = "------ my footer =E0/=80 ------";
        String footer = "------ my footer \u00E0/\u20AC ------";

        StringBuilder expected = new StringBuilder();
        expected.append(head);
        expected.append("\r\n--");
        expected.append(sep);
        expected.append("\r\n");
        expected.append(content1);
        expected.append("\r\n");
        expected.append(iso885915qpheader);
        expected.append("\r\n--");
        expected.append(sep);
        expected.append("\r\n");
        expected.append(c2h);
        expected.append(c2pre);
        expected.append("<br>");
        expected.append(cp1252qpfooter);
        expected.append(c2post);
        expected.append("\r\n--");
        expected.append(sep);
        expected.append("--\r\n");
        
        String res = processAddFooter(asciisource.toString(), footer);

        assertEquals(expected.toString(), res);

    }

    private String processAddFooter(String asciisource, String footer)
            throws MessagingException, IOException {
        Mailet mailet = new AddFooter();

        FakeMailetConfig mci = new FakeMailetConfig("Test",new FakeMailContext());
        mci.setProperty("text",footer);

        mailet.init(mci);

        Mail mail = new FakeMail();
        mail.setMessage(new MimeMessage(Session
                .getDefaultInstance(new Properties()),
                new ByteArrayInputStream(asciisource.getBytes())));

        mailet.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(
                rawMessage,
                new String[] { "Bcc", "Content-Length", "Message-ID" });
        return rawMessage.toString();
    }

}
