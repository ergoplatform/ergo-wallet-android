package org.ergoplatform.uilogic

import org.ergoplatform.persistance.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object TestUiWallet {
    const val firstAddress = "address"
    internal val token = WalletToken(
        1,
        firstAddress,
        firstAddress,
        "74251ce2cb4eb2024a1a155e19ad1d1f58ff8b9e6eb034a3bb1fd58802757d23",
        10,
        0,
        "testtoken"
    )
    internal val singularToken = WalletToken(
        2,
        firstAddress,
        firstAddress,
        "ba5856162d6342d2a0072f464a5a8b62b4ac4dd77195bec18c6bf268c2def831",
        1,
        0,
        "nft"
    )
    private val wallet = Wallet(
        WalletConfig(1, "test", firstAddress, 0, null, false),
        listOf(WalletState(firstAddress, firstAddress, 1000L * 1000 * 1000, 0)),
        listOf(token, singularToken),
        emptyList()
    )

    suspend fun getWalletDbProvider(walletId: Int): WalletDbProvider {
        val walletDbProvider = mock<WalletDbProvider> {
        }
        whenever(walletDbProvider.loadWalletWithStateById(walletId)).thenReturn(wallet)
        return walletDbProvider
    }
}