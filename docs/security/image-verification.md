# Verifying Container Image Signatures

Starting with the **13.1.0** release, all official Airsonic-Pulse container images published to `ghcr.io/airsonic-pulse/airsonic-pulse` are signed using [Cosign](https://github.com/sigstore/cosign) keyless signing via GitHub Actions OIDC. This page documents how downstream users verify image provenance before deploying.

Images from **13.0.x and earlier are not signed** — verification only applies to 13.1.0 and later.

---

## Prerequisites

Install the `cosign` CLI. Follow the official installation instructions:

- https://docs.sigstore.dev/cosign/system_config/installation/

A minimum of cosign v2.x is required; v3.x is recommended (matches the version we sign with).

---

## Verifying an image

The image digest (or any tag pointing to a signed digest) can be verified with:

```bash
cosign verify \
  --certificate-identity-regexp "https://github.com/Airsonic-Pulse/airsonic-pulse/\.github/workflows/release\.yml@refs/tags/.*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  ghcr.io/airsonic-pulse/airsonic-pulse:<tag>
```

Replace `<tag>` with the version you want to verify (for example `13.1.0`, `13.1.0-rc.2`, or `latest`).

### What success looks like

`cosign verify` prints a summary block including the certificate subject (the workflow file URL with the tag ref), the issuer (`https://token.actions.githubusercontent.com`), and the Rekor log entry index. Example abridged output:

```
Verification for ghcr.io/airsonic-pulse/airsonic-pulse:13.1.0 --
The following checks were performed on each of these signatures:
  - The cosign claims were validated
  - Existence of the claims in the transparency log was verified offline
  - The code-signing certificate was verified using trusted certificate authority certificates
[
  {
    "critical": {...},
    "optional": {
      "Subject": "https://github.com/Airsonic-Pulse/airsonic-pulse/.github/workflows/release.yml@refs/tags/v13.1.0",
      "Issuer": "https://token.actions.githubusercontent.com",
      ...
    }
  }
]
```

If you see that block, the image is signed and authentic.

### What failure looks like

- **`no matching signatures`** — the image has no Cosign signature attached (likely an unsigned 13.0.x image or a third-party copy of the image).
- **`certificate identity does not match`** — a signature exists but was produced by a different workflow / repo / ref than the regex matches. Treat as a tampering signal.
- **`no matching entries found in transparency log`** — the signature is not recorded in Rekor. Treat as a tampering signal.

In all three cases, do **not** deploy the image.

---

## How verification works

Airsonic-Pulse uses Sigstore's keyless signing model. There are no long-lived keys to manage or rotate. The trust chain rests on three components:

**Fulcio** is Sigstore's certificate authority. During the release workflow, GitHub Actions issues a short-lived OIDC token that proves the signing context — specifically, the repository, workflow file, and tag ref that produced the build. Fulcio exchanges that token for an ephemeral signing certificate whose subject is bound to that exact workflow identity. The certificate expires after a few minutes; the signature it produces is permanent.

**Rekor** is Sigstore's public transparency log. Every signature Cosign produces is also recorded in Rekor along with a hash of the signed artifact. The log is append-only and publicly auditable, which means a tampered or unauthorized signature cannot be made to look legitimate without leaving an entry that contradicts the legitimate one.

`cosign verify` performs two checks: the certificate's subject must match the expected workflow identity (the `--certificate-identity-regexp` argument), and the signature must be recorded in Rekor (verified against an offline copy of the log). Both checks must pass; if either fails, the verification fails. There is no fallback that trusts the image based on the registry alone.

---

## Multi-architecture note

Each release pushes both `linux/amd64` and `linux/arm64` images as a single multi-arch manifest. The signature is attached to the **manifest list digest**, which covers both platform images and every tag pointing to that digest. A single verification call validates the image for the architecture your client will pull.

---

## Reporting verification failures

If `cosign verify` fails for an image that should be signed, please open a security advisory on the GitHub repository rather than a public issue — see the security policy in [`.github/SECURITY.md`](../../.github/SECURITY.md).
