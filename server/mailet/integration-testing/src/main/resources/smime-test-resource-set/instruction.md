These resources were created by openssl tool:

```
openssl genrsa -out rootCA.private.key

openssl req -x509 -key rootCA.private.key -days 100000 -out rootCA.crt

openssl genrsa -out private.key

openssl req -new -key private.key -out request.csr

openssl x509 -req -in request.csr -CA rootCA.crt -CAkey rootCA.private.key -CAcreateserial -out certificate.crt -days 100000

keytool -import -alias testalias -file rootCA.crt -keystore trusted_cert_keystore -deststoretype PKCS12
```

trusted_cert_keystore file is located in the parent directory

mail_with_signature.eml was based on the result of the following command:
```
openssl smime -sign -in message -out signed-message -signer certificate.crt  -inkey private.key -text
```

mail_with_signaturemail_with_signature_and_content_type_xpkcs7mime.eml was based on the result of the following command:
```
openssl smime -sign -in message -out signed-message -signer certificate.crt  -inkey private.key -text -nodetach
```

mail-with-signature-and-multi-certs.eml was based on the result of the following command:
```
openssl smime -sign -in message -out signed-message-multi-cert -signer certificate.crt -inkey private.key -certfile rootCA.crt -nodetach
```

For more detail: https://certificate.nikhef.nl/info/smime-manual.html

password for everything: secret
