package org.ergoplatform.desktop.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ergoplatform.Application
import org.ergoplatform.appkit.MultisigAddress
import org.ergoplatform.compose.settings.appTextFieldColors
import org.ergoplatform.compose.settings.primaryButtonColors
import org.ergoplatform.compose.settings.secondaryButtonColors
import org.ergoplatform.compose.wallet.MultisigInfoSection
import org.ergoplatform.desktop.ui.AppScrollingLayout
import org.ergoplatform.desktop.ui.copyToClipboard
import org.ergoplatform.desktop.ui.defaultMaxWidth
import org.ergoplatform.desktop.ui.defaultPadding
import org.ergoplatform.mosaik.labelStyle
import org.ergoplatform.mosaik.model.ui.text.LabelStyle
import org.ergoplatform.persistance.WalletConfig
import org.ergoplatform.uilogic.*
import org.ergoplatform.wallet.isMultisig
import org.ergoplatform.wallet.isReadOnly

@Composable
fun WalletConfigScreen(
    walletConfig: WalletConfig,
    scaffoldState: ScaffoldState?,
    onChangeName: suspend (String) -> Unit,
    onShowMnemonic: () -> Unit,
    onShowXpubKey: () -> Unit,
    onAddAddresses: () -> Unit,
    multiSigStateFlow: StateFlow<MultisigAddress?>,
) {
    val coroutineScope = rememberCoroutineScope()

    AppScrollingLayout {
        Card(
            Modifier.padding(defaultPadding).align(Alignment.Center)
                .defaultMinSize(400.dp, 200.dp)
                .widthIn(max = defaultMaxWidth)
        ) {
            Column(Modifier.padding(defaultPadding)) {
                Row {
                    Text(
                        walletConfig.firstAddress!!,
                        style = labelStyle(LabelStyle.HEADLINE2),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = {
                            walletConfig.firstAddress?.copyToClipboard()
                            coroutineScope.launch {
                                scaffoldState?.snackbarHostState?.showSnackbar(
                                    Application.texts.getString(STRING_LABEL_COPIED),
                                )
                            }
                        },
                        Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                    }
                }

                val nameTextSate =
                    remember { mutableStateOf(TextFieldValue(walletConfig.displayName ?: "")) }

                OutlinedTextField(
                    nameTextSate.value,
                    { textFieldValue ->
                        nameTextSate.value = textFieldValue
                    },
                    Modifier.padding(top = defaultPadding * 1.5f).fillMaxWidth(),
                    maxLines = 1,
                    singleLine = true,
                    label = { Text(remember { Application.texts.getString(STRING_LABEL_WALLET_NAME) }) },
                    colors = appTextFieldColors(),
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            onChangeName(nameTextSate.value.text)
                        }
                    },
                    colors = primaryButtonColors(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(remember { Application.texts.getString(STRING_BUTTON_APPLY) })
                }

                Spacer(Modifier.size(defaultPadding * 1.5f))

                if (walletConfig.isMultisig())
                    MultisigInfoSection(
                        multiSigStateFlow,
                        Application.texts,
                    )
                else
                    WalletInfoSection(
                        onAddAddresses,
                        onShowXpubKey,
                        walletConfig,
                        onShowMnemonic,
                    )

            }
        }
    }
}

@Composable
private fun ColumnScope.WalletInfoSection(
    onAddAddresses: () -> Unit,
    onShowXpubKey: () -> Unit,
    walletConfig: WalletConfig,
    onShowMnemonic: () -> Unit
) {
    Text(
        remember { Application.texts.getString(STRING_DESC_WALLET_ADDRESSES) },
        style = labelStyle(LabelStyle.BODY1)
    )
    Button(
        onClick = onAddAddresses,
        colors = secondaryButtonColors(),
        modifier = Modifier.align(Alignment.End).padding(top = defaultPadding / 2),
    ) {
        Text(remember { Application.texts.getString(STRING_TITLE_WALLET_ADDRESSES) })
    }



    Text(
        remember { Application.texts.getString(STRING_DESC_DISPLAY_XPUBKEY) },
        Modifier.padding(top = defaultPadding * 1.5f),
        style = labelStyle(LabelStyle.BODY1)
    )
    Button(
        onClick = onShowXpubKey,
        colors = secondaryButtonColors(),
        enabled = walletConfig.extendedPublicKey != null || !walletConfig.isReadOnly(),
        modifier = Modifier.align(Alignment.End).padding(top = defaultPadding / 2),
    ) {
        Text(remember { Application.texts.getString(STRING_BUTTON_DISPLAY_XPUBKEY) })
    }



    Text(
        remember { Application.texts.getString(STRING_DESC_DISPLAY_MNEMONIC) },
        Modifier.padding(top = defaultPadding * 1.5f),
        style = labelStyle(LabelStyle.BODY1)
    )
    Button(
        onClick = onShowMnemonic,
        colors = secondaryButtonColors(),
        enabled = !walletConfig.isReadOnly(),
        modifier = Modifier.align(Alignment.End).padding(top = defaultPadding / 2),
    ) {
        Text(remember { Application.texts.getString(STRING_BUTTON_DISPLAY_MNEMONIC) })
    }
}