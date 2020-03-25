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

package org.apache.james.smtpserver.fastfail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.james.smtpserver.TLDLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class URIScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(URIScanner.class);

    // These regular expressions "inspired" by Spamassassin
    private static final String reserved = ";/?:@&=+$,[]\\#|";

    private static final String reservedNoColon = ";/?@&=+$,[]\\#|";

    private static final String mark = "-_.!~*'()";

    private static final String unreserved = "A-Za-z0-9" + escape(mark) + "\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f";

    private static final String uricSet = escape(reserved) + unreserved + "%";

    private static final String uricNoColon = escape(reservedNoColon) + unreserved + "%";

    private static final String schemeRE = "(?-xism:(?:https?|ftp|mailto|javascript|file))";

    private static final String schemelessRE = "(?-xism:(?<![.=])(?:(?i)www\\d*\\.|(?i)ftp\\.))";

    private static final String uriRE = "(?-xism:\\b(?:" + schemeRE + ":[" + uricNoColon + "]|" + schemelessRE + ")[" + uricSet + "#]*)";

    /** Pre-compiled pattern that matches URIs */
    private static final Pattern uriPattern = Pattern.compile(uriRE);

    /** Pre-compiled pattern that matches URI scheme strings */
    private static final Pattern schemePattern = Pattern.compile("^" + schemeRE + ":");

    /** Pre-compiled pattern used to cleanup a found URI string */
    private static final Pattern uriCleanup = Pattern.compile("^<(.*)>$");

    /** Pre-compiled pattern used to cleanup a found URI string */
    private static final Pattern uriCleanup2 = Pattern.compile("[\\]\\)>#]$");

    /** Pre-compile pattern for identifying "mailto" patterns */
    private static final Pattern uriCleanup3 = Pattern.compile("^(?i)mailto:([^\\/]{2})(.*)$");

    // These regular expressions also "inspired" by Spamassassin
    private static final String esc = "\\\\";

    private static final String period = "\\.";

    private static final String space = "\\040";

    private static final String open_br = "\\[";

    private static final String close_br = "\\]";

    private static final String nonASCII = "\\x80-\\xff";

    private static final String ctrl = "\\000-\\037";

    private static final String cr_list = "\\n\\r";

    private static final String qtext = "[^" + esc + nonASCII + cr_list + "\"]";

    private static final String dtext = "[^" + esc + nonASCII + cr_list + open_br + close_br + "]";

    private static final String quoted_pair = esc + "[^" + nonASCII + "]";

    private static final String atom_char = "[^(" + space + ")<>@,;:\"." + esc + open_br + close_br + ctrl + nonASCII + "]";

    private static final String atom = "(?>" + atom_char + "+)";

    private static final String quoted_str = "\"" + qtext + "*(?:" + quoted_pair + qtext + "*)*\"";

    private static final String word = "(?:" + atom + "|" + quoted_str + ")";

    private static final String local_part = word + "(?:" + period + word + ")*";

    private static final String label = "[A-Za-z\\d](?:[A-Za-z\\d-]*[A-Za-z\\d])?";

    private static final String domain_ref = label + "(?:" + period + label + ")*";

    private static final String domain_lit = open_br + "(?:" + dtext + "|" + quoted_pair + ")*" + close_br;

    private static final String domain = "(?:" + domain_ref + "|" + domain_lit + ")";

    private static final String Addr_spec_re = "(?-xism:" + local_part + "\\s*\\@\\s*" + domain + ")";

    /** Pre-compiled pattern for matching "schemeless" mailto strings */
    private static final Pattern emailAddrPattern = Pattern.compile(Addr_spec_re);

    /** Simple reqular expression to match an octet part of an IP address */
    private static final String octet = "(?:[1-2][0-9][0-9])|(?:[1-9][0-9])|(?:[0-9])";

    /**
     * Simple regular expression to match a part of a domain string in the
     * TLDLookup cache.
     */
    private static final String tld = "[A-Za-z0-9\\-]*";

    /** Simple regular expression that matches a two-part TLD */
    private static final String tld2 = tld + "\\." + tld;

    /** Simple regular expression that matches a three-part TLD */
    private static final String tld3 = tld + "\\." + tld + "\\." + tld;

    /**
     * Regular expression that matches and captures parts of a possible one-part
     * TLD domain string
     */
    private static final String tldCap = "(" + tld + "\\.(" + tld + "))$";

    /**
     * Regular expression that matches and captures parts of a possible two-part
     * TLD domain string
     */
    private static final String tld2Cap = "(" + tld + "\\.(" + tld2 + "))$";

    /**
     * Regular expression that matches and captures parts of a possible
     * three-part TLD domain string
     */
    private static final String tld3Cap = "(" + tld + "\\.(" + tld3 + "))$";

    /** Regular expression that matches and captures parts of an IP address */
    private static final String ipCap = "((" + octet + ")\\.(" + octet + ")\\.(" + octet + ")\\.(" + octet + "))$";

    /** Pre-compiled pattern that matches IP addresses */
    private static final Pattern ipCapPattern = Pattern.compile(ipCap);

    /**
     * Pre-compiled pattern that matches domain string that is possibly
     * contained in a one-part TLD
     */
    private static final Pattern tldCapPattern = Pattern.compile(tldCap);

    /**
     * Pre-compiled pattern that matches domain string that is possibly
     * contained in a two-part TLD
     */
    private static final Pattern tld2CapPattern = Pattern.compile(tld2Cap);

    /**
     * Pre-compiled pattern that matches domain string that is possibly
     * contained in a three-part TLD
     */
    private static final Pattern tld3CapPattern = Pattern.compile(tld3Cap);

    /**
     * <p>
     * Scans a character sequence for URIs. Then add all unique domain strings
     * derived from those found URIs to the supplied HashSet.
     * </p>
     * <p>
     * This function calls scanContentForHosts() to grab all the host strings.
     * Then it calls domainFromHost() on each host string found to distill them
     * to their basic "registrar" domains.
     * </p>
     * 
     * @param domains
     *            a HashSet to be populated with all domain strings found in the
     *            content
     * @param content
     *            a character sequence to be scanned for URIs
     * @return newDomains the domains which were extracted
     */
    public static HashSet<String> scanContentForDomains(HashSet<String> domains, CharSequence content) {
        HashSet<String> hosts = scanContentForHosts(content);
        return hosts.stream()
            .map(URIScanner::domainFromHost)
            .filter(Objects::nonNull)
            .filter(domain -> !domains.contains(domain))
            .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Scans a character sequence for URIs. Then returns all unique host strings
     * derived from those found URIs in a HashSet
     * 
     * @param content
     *            a character sequence to be scanned for URIs
     * @return a HashSet containing host strings
     */
    protected static HashSet<String> scanContentForHosts(CharSequence content) {
        HashSet<String> set = new HashSet<>();

        // look for URIs
        Matcher mat = uriPattern.matcher(content);
        while (mat.find()) {
            String found = mat.group();
            Matcher cleanMat = uriCleanup.matcher(found);
            if (cleanMat.find()) {
                found = cleanMat.group(1);
            }

            cleanMat = uriCleanup2.matcher(found);
            if (cleanMat.find()) {
                found = cleanMat.replaceAll("");
            }

            cleanMat = uriCleanup3.matcher(found);
            if (cleanMat.find()) {
                found = "mailto://" + cleanMat.group(1) + cleanMat.group(2);
            }

            cleanMat = schemePattern.matcher(found);
            if (!cleanMat.find()) {
                if (found.matches("^(?i)www\\d*\\..*")) {
                    found = "http://" + found;
                } else if (found.matches("^(?i)ftp\\..*")) {
                    found = "ftp://" + found;
                }
            }

            String host = hostFromUriStr(found);
            if (null != host) {
                host = host.toLowerCase(Locale.US);
                set.add(host);
            }
        }

        // look for "schemeless" email addresses, too
        mat = emailAddrPattern.matcher(content);
        while (mat.find()) {
            String found = mat.group();
            LOGGER.debug("******** mailfound=\"{}\"", found);
            found = "mailto://" + found;
            LOGGER.debug("*******6 mailfoundfound=\"{}\" after cleanup 6", found);

            String host = hostFromUriStr(found);
            if (null != host) {

                host = host.toLowerCase(Locale.US);
                set.add(host);
            }
        }
        return set;
    }

    /**
     * Extracts and returns the host portion of URI string.
     * 
     * This function uses java.net.URI.
     * 
     * @param uriStr
     *            a string containing a URI
     * @return the host portion of the supplied URI, null if no host string
     *         could be found
     */
    protected static String hostFromUriStr(String uriStr) {
        LOGGER.debug("hostFromUriStr(\"{}\")", uriStr);
        String host = null;
        URI uri;
        try {
            uri = new URI(uriStr);
            host = uri.getHost();
        } catch (URISyntaxException e) {
            LOGGER.error("Caught exception", e);
        }
        return host;
    }

    /**
     * Extracts and returns the registrar domain portion of a host string. This
     * funtion checks all known multi-part TLDs to make sure that registrar
     * domain is complete. For example, if the supplied host string is
     * "subdomain.example.co.uk", the TLD is "co.uk" and not "uk". Therefore,
     * the correct registrar domain is not "co.uk", but "example.co.uk". If the
     * domain string is an IP address, then the octets are returned in reverse
     * order.
     * 
     * @param host
     *            a string containing a host name
     * @return the registrar domain portion of the supplied host string
     */
    protected static String domainFromHost(String host) {
        LOGGER.debug("domainFromHost(\"{}\")", host);
        String domain = null;
        Matcher mat;

        // IP addrs
        mat = ipCapPattern.matcher(host);
        if (mat.find()) {
            // reverse the octets now
            domain = mat.group(5) + "." + mat.group(4) + "." + mat.group(3) + "." + mat.group(2);
            LOGGER.debug("domain=\"{}\"", domain);
            return domain;
        }

        // 3-part TLDs
        mat = tld3CapPattern.matcher(host);
        if (mat.find()) {
            String tld = mat.group(2);
            if (TLDLookup.isThreePartTLD(tld)) {
                domain = mat.group(1);
                LOGGER.debug("domain=\"{}, tld=\"{}\"", domain, tld);
                return domain;
            }
        }

        // 2-part TLDs
        mat = tld2CapPattern.matcher(host);
        if (mat.find()) {
            String tld = mat.group(2);
            if (TLDLookup.isTwoPartTLD(tld)) {
                domain = mat.group(1);
                LOGGER.debug("domain=\"{}, tld=\"{}\"", domain, tld);
                return domain;
            }
        }

        // 1-part TLDs
        mat = tldCapPattern.matcher(host);
        if (mat.find()) {
            String tld = mat.group(2);
            domain = mat.group(1);
            LOGGER.debug("domain=\"{}, tld=\"{}\"", domain, tld);
            return domain;
        }
        return domain;
    }

    /**
     * A utility function that "escapes" special characters in a string.
     * 
     * @param str
     *            a string to be processed
     * @return modified "escaped" string
     */
    private static String escape(String str) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isDigit(ch) || Character.isUpperCase(ch) || Character.isLowerCase(ch) || ch == '_') {
                buffer.append(ch);
            } else {
                buffer.append("\\");
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }
}
