package org.kasumi321.ushio.phitracker.data.avatar

import okio.ByteString.Companion.decodeBase64

/** Two complete 1x1 PNGs, rather than signature-only byte arrays. */
internal object AvatarTestFixtures {
    val pngOne: ByteArray = requireNotNull(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
            .decodeBase64()
    ).toByteArray()

    val pngTwo: ByteArray = requireNotNull(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR42mP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
            .decodeBase64()
    ).toByteArray()
}
