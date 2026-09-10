package com.example.navigation.routing

/**
 * The public SSH Ed25519 verifier is release-distributed, but it is trusted only when its
 * SHA-256 fingerprint matches this compiled anchor. The private signing key is never present
 * in RENAV source, build outputs, or package storage.
 */
object BrouterTrustAnchor {
    const val SSH_ED25519_SHA256_FINGERPRINT = "SHA256:ou3kmrFldZu4294D28WQKE2x2IE8/drYMBJnVEzfU5A"
    const val SIGNATURE_NAMESPACE = "renav-brouter-manifest"
}
