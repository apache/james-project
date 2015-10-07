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

import java.util.Locale;

/**
 * A representation of an email address.
 * 
 * <p>This class encapsulates functionality to access different
 * parts of an email address without dealing with its parsing.</p>
 *
 * <p>A MailAddress is an address specified in the MAIL FROM and
 * RCPT TO commands in SMTP sessions.  These are either passed by
 * an external server to the mailet-compliant SMTP server, or they
 * are created programmatically by the mailet-compliant server to
 * send to another (external) SMTP server.  Mailets and matchers
 * use the MailAddress for the purpose of evaluating the sender
 * and recipient(s) of a message.</p>
 *
 * <p>MailAddress parses an email address as defined in RFC 821
 * (SMTP) p. 30 and 31 where addresses are defined in BNF convention.
 * As the mailet API does not support the aged "SMTP-relayed mail"
 * addressing protocol, this leaves all addresses to be a {@code <mailbox>},
 * as per the spec. 
 *
 * <p>This class is a good way to validate email addresses as there are
 * some valid addresses which would fail with a simpler approach
 * to parsing address. It also removes the parsing burden from
 * mailets and matchers that might not realize the flexibility of an
 * SMTP address. For instance, "serge@home"@lokitech.com is a valid
 * SMTP address (the quoted text serge@home is the local-part and
 * lokitech.com is the domain). This means all current parsing to date
 * is incorrect as we just find the first '@' and use that to separate
 * local-part from domain.</p>
 *
 * <p>This parses an address as per the BNF specification for <mailbox>
 * from RFC 821 on page 30 and 31, section 4.1.2. COMMAND SYNTAX.
 * http://www.freesoft.org/CIE/RFC/821/15.htm</p>
 *
 * <strong>This version is copied from mailet-api with a few changes to not make it depend on javamail</strong>
 */
public class MailAddress {

    private final static char[] SPECIAL =
    {'<', '>', '(', ')', '[', ']', '\\', '.', ',', ';', ':', '@', '\"'};

    private String localPart = null;
    private String domain = null;

    private static final MailAddress NULL_SENDER = new MailAddress() {

        @Override
        public String getDomain() {
            return "";
        }

        @Override
        public String getLocalPart() {
            return "";
        }

        @Override
        public String toString() {
            return "";
        }

        @Override
        public boolean isNullSender() {
            return true;
        }
        
    };
    
    /**
     * Strips source routing. According to RFC-2821 it is a valid approach
     * to handle mails containing RFC-821 source-route information.
     * 
     * @param address the address to strip
     * @param pos current position
     * @return new pos
     */
    private int stripSourceRoute(String address, int pos) {
        if (pos < address.length()) {
            if (address.charAt(pos)=='@') { 
                int i = address.indexOf(':');
                if (i != -1) {
                    pos = i+1;
                }
            }
        }
        return pos;
    }
    
    public static MailAddress nullSender() {
        return NULL_SENDER;
    }
    
    private MailAddress() {
        
    }
    /**
     * Constructs a MailAddress by parsing the provided address.
     *
     * @param address the email address, compliant to the RFC2822 3.4.1. addr-spec specification
     * @throws MailAddressException if the parse failed
     */
    public MailAddress(String address) throws MailAddressException {
        address = address.trim();
        int pos = 0;
        
        // Test if mail address has source routing information (RFC-821) and get rid of it!!
        //must be called first!! (or at least prior to updating pos)
        stripSourceRoute(address, pos);

        StringBuffer localPartSB = new StringBuffer();
        StringBuffer domainSB = new StringBuffer();
        //Begin parsing
        //<mailbox> ::= <local-part> "@" <domain>

        try {
            //parse local-part
            //<local-part> ::= <dot-string> | <quoted-string>
            if (address.charAt(pos) == '\"') {
                pos = parseQuotedLocalPart(localPartSB, address, pos);
                if (localPartSB.toString().length() == 2) {
                    throw new MailAddressException("No quoted local-part (user account) found at position " + (pos + 2) + " in '" + address + "'",address,pos+2);
                }
            } else {
                pos = parseUnquotedLocalPart(localPartSB, address, pos);
                if (localPartSB.toString().length() == 0) {
                    throw new MailAddressException("No local-part (user account) found at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                }
            }

            //find @
            if (pos >= address.length() || address.charAt(pos) != '@') {
                throw new MailAddressException("Did not find @ between local-part and domain at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
            }
            pos++;

            //parse domain
            //<domain> ::=  <element> | <element> "." <domain>
            //<element> ::= <name> | "#" <number> | "[" <dotnum> "]"
            while (true) {
                if (address.charAt(pos) == '#') {
                    pos = parseNumber(domainSB, address, pos);
                } else if (address.charAt(pos) == '[') {
                    pos = parseDomainLiteral(domainSB, address, pos);
                } else {
                    pos = parseDomain(domainSB, address, pos);
                }
                if (pos >= address.length()) {
                    break;
                }
                if (address.charAt(pos) == '.') {
                    domainSB.append('.');
                    pos++;
                    continue;
                }
                break;
            }

            if (domainSB.toString().length() == 0) {
                throw new MailAddressException("No domain found at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
            }
        } catch (IndexOutOfBoundsException ioobe) {
            throw new MailAddressException("Out of data at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
        }

        localPart = localPartSB.toString();
        domain = domainSB.toString();
    }

    /**
     * Constructs a MailAddress with the provided local part and domain.
     *
     * @param localPart the local-part portion. This is a domain dependent string.
     *        In addresses, it is simply interpreted on the particular host as a
     *        name of a particular mailbox. per RFC2822 3.4.1. addr-spec specification
     * @param domain the domain portion. This identifies the point to which the mail
     *        is delivered  per RFC2822 3.4.1. addr-spec specification
     * @throws AddressException if the parse failed
     */
    public MailAddress(String localPart, String domain) throws MailAddressException {
        this(localPart+"@"+domain);
    }


    
    /**
     * Returns the domain part per RFC2822 3.4.1. addr-spec specification.
     *
     * @return the domain part of this email address. If the domain is of
     * the domain-literal form  (e.g. [yyy.yyy.yyy.yyy]), the braces will
     * have been stripped returning the raw IP address.
     * 
     */
    public String getDomain() {
        if (!(domain.startsWith("[") && domain.endsWith("]"))) {
            return domain;
        } 
        return domain.substring(1, domain.length() -1);
    }


    
    /**
     * Returns the local-part per RFC2822 3.4.1. addr-spec specification.
     *
     * @return  the local-part of this email address as defined by the
     *          RFC2822 3.4.1. addr-spec specification. 
     *          The local-part portion is a domain dependent string.
     *          In addresses, it is simply interpreted on the particular
     *          host as a name of a particular mailbox
     *          (the part before the "@" character)
     *          
     * @since Mailet API 2.4
     */
    public String getLocalPart() {
        return localPart;
    }

    @Override
    public String toString() {
        StringBuffer addressBuffer =
            new StringBuffer(128)
                    .append(localPart)
                    .append("@")
                    .append(domain);
        return addressBuffer.toString();
    }
    
    
    /**
     * Indicates whether some other object is "equal to" this one.
     * 
     * Note that this implementation breaks the general contract of the
     * <code>equals</code> method by allowing an instance to equal to a
     * <code>String</code>. It is recommended that implementations avoid
     * relying on this design which may be removed in a future release.
     * 
     * @returns true if the given object is equal to this one, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj instanceof String) {
            String theString = (String)obj;
            return toString().equalsIgnoreCase(theString);
        } else if (obj instanceof MailAddress) {
            MailAddress addr = (MailAddress)obj;
            return getLocalPart().equalsIgnoreCase(addr.getLocalPart()) && getDomain().equalsIgnoreCase(addr.getDomain());
        }
        return false;
    }

    /**
     * Returns a hash code value for this object.
     * <p>
     * This method is implemented by returning the hash code of the canonical
     * string representation of this address, so that all instances representing
     * the same address will return an identical hash code.
     *
     * @return the hashcode.
     */
    @Override
    public int hashCode() {
        return toString().toLowerCase(Locale.US).hashCode();
    }

    private int parseQuotedLocalPart(StringBuffer lpSB, String address, int pos) throws MailAddressException {
        StringBuffer resultSB = new StringBuffer();
        resultSB.append('\"');
        pos++;
        //<quoted-string> ::=  """ <qtext> """
        //<qtext> ::=  "\" <x> | "\" <x> <qtext> | <q> | <q> <qtext>
        while (true) {
            if (address.charAt(pos) == '\"') {
                resultSB.append('\"');
                //end of quoted string... move forward
                pos++;
                break;
            }
            if (address.charAt(pos) == '\\') {
                resultSB.append('\\');
                pos++;
                //<x> ::= any one of the 128 ASCII characters (no exceptions)
                char x = address.charAt(pos);
                if (x < 0 || x > 127) {
                    throw new MailAddressException("Invalid \\ syntaxed character at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                }
                resultSB.append(x);
                pos++;
            } else {
                //<q> ::= any one of the 128 ASCII characters except <CR>,
                //<LF>, quote ("), or backslash (\)
                char q = address.charAt(pos);
                if (q <= 0 || q == '\n' || q == '\r' || q == '\"' || q == '\\') {
                    throw new MailAddressException("Unquoted local-part (user account) must be one of the 128 ASCI characters exception <CR>, <LF>, quote (\"), or backslash (\\) at position " + (pos + 1) + " in '" + address + "'", address, pos+1);
                }
                resultSB.append(q);
                pos++;
            }
        }
        lpSB.append(resultSB);
        return pos;
    }

    private int parseUnquotedLocalPart(StringBuffer lpSB, String address, int pos) throws MailAddressException {
        StringBuffer resultSB = new StringBuffer();
        //<dot-string> ::= <string> | <string> "." <dot-string>
        boolean lastCharDot = false;
        while (true) {
            //<string> ::= <char> | <char> <string>
            //<char> ::= <c> | "\" <x>
            if (address.charAt(pos) == '\\') {
                resultSB.append('\\');
                pos++;
                //<x> ::= any one of the 128 ASCII characters (no exceptions)
                char x = address.charAt(pos);
                if (x < 0 || x > 127) {
                    throw new MailAddressException("Invalid \\ syntaxed character at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                }
                resultSB.append(x);
                pos++;
                lastCharDot = false;
            } else if (address.charAt(pos) == '.') {
                resultSB.append('.');
                pos++;
                lastCharDot = true;
            } else if (address.charAt(pos) == '@') {
                //End of local-part
                break;
            } else {
                //<c> ::= any one of the 128 ASCII characters, but not any
                //    <special> or <SP>
                //<special> ::= "<" | ">" | "(" | ")" | "[" | "]" | "\" | "."
                //    | "," | ";" | ":" | "@"  """ | the control
                //    characters (ASCII codes 0 through 31 inclusive and
                //    127)
                //<SP> ::= the space character (ASCII code 32)
                char c = address.charAt(pos);
                if (c <= 31 || c >= 127 || c == ' ') {
                    throw new MailAddressException("Invalid character in local-part (user account) at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                }
                for (int i = 0; i < SPECIAL.length; i++) {
                    if (c == SPECIAL[i]) {
                        throw new MailAddressException("Invalid character in local-part (user account) at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                    }
                }
                resultSB.append(c);
                pos++;
                lastCharDot = false;
            }
        }
        if (lastCharDot) {
            throw new MailAddressException("local-part (user account) ended with a \".\", which is invalid in address '" + address + "'",address,pos);
        }
        lpSB.append(resultSB);
        return pos;
    }

    private int parseNumber(StringBuffer dSB, String address, int pos) throws MailAddressException {
        //<number> ::= <d> | <d> <number>

        StringBuffer resultSB = new StringBuffer();
        //We keep the position from the class level pos field
        while (true) {
            if (pos >= address.length()) {
                break;
            }
            //<d> ::= any one of the ten digits 0 through 9
            char d = address.charAt(pos);
            if (d == '.') {
                break;
            }
            if (d < '0' || d > '9') {
                throw new MailAddressException("In domain, did not find a number in # address at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
            }
            resultSB.append(d);
            pos++;
        }
        dSB.append(resultSB);
        return pos;
    }

    private int parseDomainLiteral(StringBuffer dSB, String address, int pos) throws MailAddressException {
        //throw away all irrelevant '\' they're not necessary for escaping of '.' or digits, and are illegal as part of the domain-literal
        while(address.indexOf("\\")>-1){
             address= address.substring(0,address.indexOf("\\")) + address.substring(address.indexOf("\\")+1);
        }
        StringBuffer resultSB = new StringBuffer();
        //we were passed the string with pos pointing the the [ char.
        // take the first char ([), put it in the result buffer and increment pos
        resultSB.append(address.charAt(pos));
        pos++;

        //<dotnum> ::= <snum> "." <snum> "." <snum> "." <snum>
        for (int octet = 0; octet < 4; octet++) {
            //<snum> ::= one, two, or three digits representing a decimal
            //                      integer value in the range 0 through 255
            //<d> ::= any one of the ten digits 0 through 9
            StringBuffer snumSB = new StringBuffer();
            for (int digits = 0; digits < 3; digits++) {
                char d = address.charAt(pos);
                if (d == '.') {
                    break;
                }
                if (d == ']') {
                    break;
                }
                if (d < '0' || d > '9') {
                    throw new MailAddressException("Invalid number at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                }
                snumSB.append(d);
                pos++;
            }
            if (snumSB.toString().length() == 0) {
                throw new MailAddressException("Number not found at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
            }
            try {
                int snum = Integer.parseInt(snumSB.toString());
                if (snum > 255) {
                    throw new MailAddressException("Invalid number at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
                }
            } catch (NumberFormatException nfe) {
                throw new MailAddressException("Invalid number at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
            }
            resultSB.append(snumSB.toString());
            if (address.charAt(pos) == ']') {
                if (octet < 3) {
                    throw new MailAddressException("End of number reached too quickly at " + (pos + 1) + " in '" + address + "'",address,pos+1);
                } 
                break;
            }
            if (address.charAt(pos) == '.') {
                resultSB.append('.');
                pos++;
            }
        }
        if (address.charAt(pos) != ']') {
            throw new MailAddressException("Did not find closing bracket \"]\" in domain at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
        }
        resultSB.append(']');
        pos++;
        dSB.append(resultSB);
        return pos;
    }

    private int parseDomain(StringBuffer dSB, String address, int pos) throws MailAddressException {
        StringBuffer resultSB = new StringBuffer();
        //<name> ::= <a> <ldh-str> <let-dig>
        //<ldh-str> ::= <let-dig-hyp> | <let-dig-hyp> <ldh-str>
        //<let-dig> ::= <a> | <d>
        //<let-dig-hyp> ::= <a> | <d> | "-"
        //<a> ::= any one of the 52 alphabetic characters A through Z
        //  in upper case and a through z in lower case
        //<d> ::= any one of the ten digits 0 through 9

        // basically, this is a series of letters, digits, and hyphens,
        // but it can't start with a digit or hypthen
        // and can't end with a hyphen

        // in practice though, we should relax this as domain names can start
        // with digits as well as letters.  So only check that doesn't start
        // or end with hyphen.
        while (true) {
            if (pos >= address.length()) {
                break;
            }
            char ch = address.charAt(pos);
            if ((ch >= '0' && ch <= '9') ||
                (ch >= 'a' && ch <= 'z') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch == '-')) {
                resultSB.append(ch);
                pos++;
                continue;
            }
            if (ch == '.') {
                break;
            }
            throw new MailAddressException("Invalid character at " + pos + " in '" + address + "'",address,pos);
        }
        String result = resultSB.toString();
        if (result.startsWith("-") || result.endsWith("-")) {
            throw new MailAddressException("Domain name cannot begin or end with a hyphen \"-\" at position " + (pos + 1) + " in '" + address + "'",address,pos+1);
        }
        dSB.append(result);
        return pos;
    }
    
    /**
     * Return <code>true</code> if the {@link MailAddress} should represent a null sender (<>)
     * 
     * @return nullsender
     */
    public boolean isNullSender() {
        return false;
    }
}
