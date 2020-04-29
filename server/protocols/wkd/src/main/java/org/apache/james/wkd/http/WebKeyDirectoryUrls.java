package org.apache.james.wkd.http;

/**
 * URLs based on:
 * https://tools.ietf.org/id/draft-koch-openpgp-webkey-service-07.html#rfc.section.4
 * 
 * @author manuel
 *
 */
public interface WebKeyDirectoryUrls {
    String WELLKNOWN_DIRECT_PUB_KEY = "/.well-known/openpgpkey/hu";
    String WELLKNOWN_POLICY = "/.well-known/openpgpkey/policy";
    String WELLKNOWN_SUBMISSION_ADDRESS = "/.well-known/openpgpkey/submission-address";
}