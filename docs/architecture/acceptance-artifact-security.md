# Destination-index acceptance artifact security

The non-production acceptance variant is signed by the existing protected
`destination-index-release` environment. It is **not** a production update.
The repository is currently private, but GitHub Actions artifacts are
downloadable by repository readers; privacy of the repository alone is not an
acceptance-package access control.

The protected environment must hold `RENAV_ACCEPTANCE_ARTIFACT_KEY`: canonical
base64 encoding of 32 cryptographically random bytes. An authorized acceptance
operator retains the same key outside GitHub and outside this repository. Do
not put it in dispatch inputs, source, logs, PR comments or GitHub release
assets. If the key is absent or malformed, the job fails before signing. The
environment's existing branch restriction and signing-key protection remain
unchanged. Rotate the key if consumer access changes.

For `acceptance_only=true`, the workflow requires an `acceptance-` version,
builds a distinct deterministic payload, signs it, and encrypts the four
package files into `acceptance-package.tar.enc` using OpenSSL AES-256-CBC with
a random salt and PBKDF2 (600,000 iterations). Only that ciphertext is uploaded
as a seven-day Actions artifact. The operator decrypts it locally with the
enrolled key, then verifies the signed manifest and checksums using the
existing package verifier before installation. The encryption protects
confidentiality; the existing signature and checksums provide authenticity
and integrity. A corrupt or wrongly decrypted file must never be installed.

For `acceptance_only=false`, an `acceptance-` version is rejected before
signing. Only non-acceptance versions can reach R2 and GitHub release steps.
The acceptance branch cannot deploy through the protected environment until
the normal protected-branch review and merge requirements are satisfied. No
acceptance package should be dispatched until the protected key is enrolled
and the PR checks pass. Do not weaken the environment rule to test this PR.

Read-only PR checks run destination-index tests, including distribution-lane
and artifact-path assertions, without signing or acquiring the PBF. A separate
authorized post-merge acceptance dispatch is needed to prove the encrypted
artifact can be decrypted and verified by the intended operator; local/PR
contract tests alone are not that end-to-end proof.

PR #1 commit `a959661eabab156977b8a47353b180f86d1f155d` passed the
read-only [distribution-contract run 38732503449](https://github.com/alist-wq/some/actions/runs/38732503449):
11 tests, 0 failures, 0 skips. This verifies the code policy, not protected
signing, key enrollment, or Android replacement acceptance. Those remain
pending; RB-02 and RB-05.2 are IN PROGRESS and Gate B remains OPEN.

