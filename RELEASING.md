# Releasing

Push a version tag (`0.1.0`, no `v`) on `main`. That releases both SDKs at once:

- **iOS:** nothing to build. Swift Package Manager resolves the tag straight from this repo.
- **Android:** the Release workflow tests the library, publishes `io.appetise:shopper:<tag>` to
  Maven Central, and creates the GitHub release.

```bash
git tag 0.1.0 && git push origin 0.1.0
```

A version on Maven Central can never be replaced or deleted, so a broken release means a new
version. Check CI is green on the commit first.

## One-time setup

The Release workflow fails at its first step until these are done.

1. **Central Portal account.** Sign up at https://central.sonatype.com with a shared Appetise
   login, not a personal one.
2. **Namespace.** Under Namespaces, add `io.appetise`. Verify it with the DNS TXT record the portal
   gives you on `appetise.io`.
3. **User token.** Under Account › Generate User Token. Its username and password are the
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` secrets.
4. **Signing key.** Maven Central only accepts signed artifacts.
   ```bash
   gpg --full-generate-key                    # RSA 4096, a passphrase, no expiry or a long one
   gpg --list-secret-keys --keyid-format SHORT   # the 8-character ID after rsa4096/
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --export-secret-keys --armor <KEY_ID>  # the SIGNING_KEY secret, including the BEGIN/END lines
   ```
   Keep the key and passphrase in the team password manager. Losing them means a new key.
5. **Repository secrets.** In this repo's Settings › Secrets and variables › Actions:

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | User token username |
   | `MAVEN_CENTRAL_PASSWORD` | User token password |
   | `SIGNING_KEY` | The armored private key |
   | `SIGNING_KEY_PASSWORD` | Its passphrase |

## Trying a release locally

`cd android && ./gradlew :sdk:publishToMavenLocal -PVERSION_NAME=0.0.0-local` builds the artifacts
into `~/.m2` without signing or uploading.
