package com.example.navigation.maps

/**
 * Compiled trust anchor for the independently released destination index. The release-distributed
 * public key is accepted only when it matches this fingerprint; no private key is stored here.
 */
object DestinationIndexTrustAnchor {
    const val SSH_ED25519_SHA256_FINGERPRINT = "SHA256:SXfNV9xLocPsovQ18P9E289B8Tg9M5T4uRHIn42G3Dg"
    const val SIGNATURE_NAMESPACE = "renav-destination-index-manifest"
}
