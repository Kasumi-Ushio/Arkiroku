package org.kasumi321.ushio.phitracker.data.api

import org.kasumi321.ushio.phitracker.domain.model.Server

object TapTapConstants {
    fun baseUrl(server: Server): String = when (server) {
        Server.CN -> "https://rak3ffdi.cloud.tds1.tapapis.cn/1.1"
        Server.GLOBAL -> "https://kviehlel.cloud.ap-sg.tapapis.com/1.1"
    }

    const val LC_ID = "rAK3FfdieFob2Nn8Am"
    const val LC_KEY = "Qr9AEqtuoSVS3zeD6iVbM4ZC0AtkJcQ89tywVyi0"
    const val LC_ID_GLOBAL = "kviehleldgxsagpozb"
    const val LC_KEY_GLOBAL = "tG9CTm0LDD736k9HMM9lBZrbeBGRmUkjSfNLDNib"

    fun oauthCodeUrl(server: Server): String = when (server) {
        Server.CN -> "https://accounts.tapapis.cn/oauth2/v1/device/code"
        Server.GLOBAL -> "https://accounts.tapapis.com/oauth2/v1/device/code"
    }

    fun oauthTokenUrl(server: Server): String = when (server) {
        Server.CN -> "https://accounts.tapapis.cn/oauth2/v1/token"
        Server.GLOBAL -> "https://accounts.tapapis.com/oauth2/v1/token"
    }

    fun profileUrl(server: Server): String = when (server) {
        Server.CN -> "https://open.tapapis.cn/account/profile/v1?client_id=$LC_ID"
        Server.GLOBAL -> "https://open.tapapis.com/account/profile/v1?client_id=$LC_ID_GLOBAL"
    }

    fun lcUsersUrl(server: Server): String = when (server) {
        Server.CN -> "https://rak3ffdi.cloud.tds1.tapapis.cn/1.1/users"
        Server.GLOBAL -> "https://kviehlel.cloud.ap-sg.tapapis.com/1.1/users"
    }

    fun lcAppKey(server: Server): String = when (server) {
        Server.CN -> LC_KEY
        Server.GLOBAL -> LC_KEY_GLOBAL
    }

    fun lcClientId(server: Server): String = when (server) {
        Server.CN -> LC_ID
        Server.GLOBAL -> LC_ID_GLOBAL
    }

    object Endpoints {
        const val USERS_ME = "/users/me"
        const val GAME_SAVE = "/classes/_GameSave"
        fun refreshSessionToken(userObjectId: String) = "/users/$userObjectId/refreshSessionToken"
    }
}

object CryptoConstants {
    val AES_KEY = byteArrayOf(
        0xe8.toByte(), 0x96.toByte(), 0x9a.toByte(), 0xd2.toByte(),
        0xa5.toByte(), 0x40.toByte(), 0x25.toByte(), 0x9b.toByte(),
        0x97.toByte(), 0x91.toByte(), 0x90.toByte(), 0x8b.toByte(),
        0x88.toByte(), 0xe6.toByte(), 0xbf.toByte(), 0x03.toByte(),
        0x1e.toByte(), 0x6d.toByte(), 0x21.toByte(), 0x95.toByte(),
        0x6e.toByte(), 0xfa.toByte(), 0xd6.toByte(), 0x8a.toByte(),
        0x50.toByte(), 0xdd.toByte(), 0x55.toByte(), 0xd6.toByte(),
        0x7a.toByte(), 0xb0.toByte(), 0x92.toByte(), 0x4b.toByte()
    )

    val AES_IV = byteArrayOf(
        0x2a.toByte(), 0x4f.toByte(), 0xf0.toByte(), 0x8a.toByte(),
        0xc8.toByte(), 0x0d.toByte(), 0x63.toByte(), 0x07.toByte(),
        0x00.toByte(), 0x57.toByte(), 0xc5.toByte(), 0x95.toByte(),
        0x18.toByte(), 0xc8.toByte(), 0x32.toByte(), 0x53.toByte()
    )
}
