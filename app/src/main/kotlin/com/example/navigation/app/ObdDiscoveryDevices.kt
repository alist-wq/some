package com.example.navigation.app

/** Keeps Bluetooth scan results unique when Android reports a device more than once. */
fun mergeNearbyObdDevice(existing: List<NearbyObdDevice>, candidate: NearbyObdDevice): List<NearbyObdDevice> =
    (existing.filterNot { it.address == candidate.address } + candidate).sortedBy { it.name }
