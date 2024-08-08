These resources were created by openssl tool:

```
openssl genpkey -algorithm RSA -out private.key

openssl req -new -key private.key -out request.csr

openssl x509 -req -days 100000 -in request.csr -signkey private.key -out certificate.crt

keytool -import -alias testalias -file certificate.crt -keystore smime_cert_keystore -deststoretype PKCS12
```

smime_cert_keystore file is located in the parent directory

mail_with_signature.eml was based on the result of the following command:
```
openssl smime -sign -in message -out signed-message -signer certificate.crt  -inkey private.key -text
```

For more detail: https://certificate.nikhef.nl/info/smime-manual.html

password for everything: secret
