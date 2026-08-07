# Android signing

`marnock.jks` is the project signing key used for debug and release builds so
in-app updates from GitHub Releases install over previous builds.

Default credentials (override with env vars):

| Env | Default |
|-----|---------|
| `MARNOCK_KEYSTORE_PASSWORD` | `marnock` |
| `MARNOCK_KEY_PASSWORD` | `marnock` |
| `MARNOCK_KEY_ALIAS` | `marnock` |

APKs published before this keystore cannot be updated in place — uninstall once, then install the new release.
