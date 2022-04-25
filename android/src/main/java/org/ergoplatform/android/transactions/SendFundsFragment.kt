package org.ergoplatform.android.transactions

import android.animation.LayoutTransition
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.integration.android.IntentIntegrator
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import org.ergoplatform.*
import org.ergoplatform.android.Preferences
import org.ergoplatform.android.R
import org.ergoplatform.android.databinding.FragmentSendFundsBinding
import org.ergoplatform.android.databinding.FragmentSendFundsTokenItemBinding
import org.ergoplatform.android.ui.*
import org.ergoplatform.persistance.TokenPrice
import org.ergoplatform.persistance.WalletToken
import org.ergoplatform.tokens.isSingularToken
import org.ergoplatform.utils.formatFiatToString
import org.ergoplatform.utils.formatTokenPriceToString
import org.ergoplatform.wallet.addresses.getAddressLabel
import org.ergoplatform.wallet.getNumOfAddresses


/**
 * Here's the place to send transactions
 */
class SendFundsFragment : SubmitTransactionFragment() {
    private var _binding: FragmentSendFundsBinding? = null
    private val binding get() = _binding!!
    override lateinit var viewModel: SendFundsViewModel
    private val args: SendFundsFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel =
            ViewModelProvider(this).get(SendFundsViewModel::class.java)

        // Inflate the layout for this fragment
        _binding = FragmentSendFundsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initWallet(
            requireContext(),
            args.walletId,
            args.derivationIdx,
            args.paymentRequest
        )

        // Add observers
        viewModel.walletName.observe(viewLifecycleOwner, {
            // when wallet is loaded, wallet name is set. we can init everything wallet specific here
            binding.walletName.text = getString(R.string.label_send_from, it)
            binding.hintReadonly.visibility =
                if (viewModel.uiLogic.wallet!!.walletConfig.secretStorage == null) View.VISIBLE else View.GONE
            enableLayoutChangeAnimations()
        })
        viewModel.address.observe(viewLifecycleOwner, {
            binding.addressLabel.text =
                it?.getAddressLabel(AndroidStringProvider(requireContext()))
                    ?: getString(
                        R.string.label_all_addresses,
                        viewModel.uiLogic.wallet?.getNumOfAddresses()
                    )
        })
        viewModel.walletBalance.observe(viewLifecycleOwner, {
            binding.tvBalance.text = getString(
                R.string.label_wallet_balance,
                it.toStringRoundToDecimals()
            )
        })
        viewModel.grossAmount.observe(viewLifecycleOwner) { grossAmount ->
            binding.tvFee.text =
                viewModel.uiLogic.getFeeDescriptionLabel(AndroidStringProvider(requireContext()))
            binding.grossAmount.setAmount(grossAmount.toBigDecimal())
            val nodeConnector = WalletStateSyncManager.getInstance()
            binding.tvFiat.visibility =
                if (nodeConnector.fiatCurrency.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvFiat.setText(
                getString(
                    R.string.label_fiat_amount,
                    formatFiatToString(
                        viewModel.uiLogic.amountToSend.toDouble() * nodeConnector.fiatValue.value.toDouble(),
                        nodeConnector.fiatCurrency, AndroidStringProvider(requireContext())
                    ),
                )
            )
        }
        viewModel.tokensChosenLiveData.observe(viewLifecycleOwner, {
            refreshTokensList()
        })
        viewModel.errorMessageLiveData.observe(viewLifecycleOwner, {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(it)
                .setPositiveButton(R.string.zxing_button_ok, null)
                .show()
        })
        viewModel.txId.observe(viewLifecycleOwner, {
            it?.let {
                binding.cardviewTxEdit.visibility = View.GONE
                binding.cardviewTxDone.visibility = View.VISIBLE
                binding.labelTxId.text = it
            }
        })

        // Add click listeners
        binding.addressLabel.setOnClickListener {
            showChooseAddressList(true)
        }
        binding.buttonShareTx.setOnClickListener {
            val txUrl = getExplorerTxUrl(binding.labelTxId.text.toString())
            shareText(txUrl)
        }
        binding.buttonDismiss.setOnClickListener {
            val succeeded = findNavController().popBackStack()
            // back stack might be empty when coming from a deep link
            if (!succeeded) {
                findNavController().navigate(R.id.navigation_wallet)
            }
        }

        binding.buttonSend.setOnClickListener {
            startPayment()
        }

        binding.buttonAddToken.setOnClickListener {
            ChooseTokenListDialogFragment().show(childFragmentManager, null)
        }
        binding.amount.setEndIconOnClickListener {
            setAmountEdittext(viewModel.uiLogic.getMaxPossibleAmountToSend())
        }
        binding.tiMessage.setEndIconOnClickListener {
            showPurposeMessageInformation()
        }
        binding.hintReadonly.setOnClickListener {
            openUrlWithBrowser(requireContext(), URL_COLD_WALLET_HELP)
        }
        binding.tvFee.setOnClickListener {
            ChooseFeeDialogFragment().show(childFragmentManager, null)
        }

        // Init other stuff
        binding.tvReceiver.editText?.setText(viewModel.uiLogic.receiverAddress)
        binding.tiMessage.editText?.setText(viewModel.uiLogic.message)
        if (viewModel.uiLogic.amountToSend.nanoErgs > 0) {
            setAmountEdittext(viewModel.uiLogic.amountToSend)
        }

        binding.amount.editText?.addTextChangedListener(MyTextWatcher(binding.amount))
        binding.tvReceiver.editText?.addTextChangedListener(MyTextWatcher(binding.tvReceiver))
        binding.tiMessage.editText?.addTextChangedListener(MyTextWatcher(binding.tiMessage))

        // this triggers an automatic scroll so the amount field is visible when soft keyboard is
        // opened or when amount edittext gets focus
        binding.amount.editText?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus)
                ensureAmountVisibleDelayed()
        }
        KeyboardVisibilityEvent.setEventListener(
            requireActivity(),
            viewLifecycleOwner,
            { keyboardOpen ->
                if (keyboardOpen && binding.amount.editText?.hasFocus() == true) {
                    ensureAmountVisibleDelayed()
                }
            })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_send_funds, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_scan_qr) {
            IntentIntegrator.forSupportFragment(this).initiateScan(setOf(IntentIntegrator.QR_CODE))
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    private fun enableLayoutChangeAnimations() {
        // set layout change animations. they are not set in the xml to avoid animations for the first
        // time the layout is displayed, and enabling them is delayed due to the same reason
        postDelayed(200) {
            _binding?.let { binding ->
                binding.container.layoutTransition = LayoutTransition()
                binding.container.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            }
        }
    }

    private fun ensureAmountVisibleDelayed() {
        // delay 200 to make sure that smart keyboard is already open
        postDelayed(200) { _binding?.let { it.scrollView.smoothScrollTo(0, it.amount.top) } }
    }

    private fun refreshTokensList() {
        val tokensAvail = viewModel.uiLogic.tokensAvail
        val tokensChosen = viewModel.uiLogic.tokensChosen

        binding.buttonAddToken.visibility =
            if (tokensAvail.size > tokensChosen.size) View.VISIBLE else View.INVISIBLE
        binding.labelTokenAmountError.visibility = View.GONE
        binding.tokensList.apply {
            this.visibility =
                if (tokensChosen.isNotEmpty()) View.VISIBLE else View.GONE
            this.removeAllViews()
            val walletStateSyncManager = WalletStateSyncManager.getInstance()
            tokensChosen.forEach {
                val ergoId = it.key
                tokensAvail.firstOrNull { it.tokenId.equals(ergoId) }?.let { tokenDbEntity ->
                    val itemBinding =
                        FragmentSendFundsTokenItemBinding.inflate(layoutInflater, this, true)
                    itemBinding.tvTokenName.text =
                        tokenDbEntity.name ?: getString(R.string.label_unnamed_token)

                    val amountChosen = it.value.value
                    val tokenPrice = walletStateSyncManager.tokenPrices[tokenDbEntity.tokenId!!]
                    val isSingular =
                        tokenDbEntity.isSingularToken() && amountChosen == 1L && tokenPrice == null

                    if (isSingular) {
                        itemBinding.inputTokenAmount.visibility = View.GONE
                        itemBinding.labelTokenBalance.visibility = View.GONE
                        itemBinding.labelBalanceValue.visibility = View.GONE
                        itemBinding.labelTokenBalance.visibility = View.GONE
                    } else {
                        itemBinding.labelTokenBalance.text =
                            tokenDbEntity.toTokenAmount().toStringPrettified()
                        itemBinding.inputTokenAmount.inputType =
                            if (tokenDbEntity.decimals > 0) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            else InputType.TYPE_CLASS_NUMBER
                        itemBinding.inputTokenAmount.addTextChangedListener(
                            TokenAmountWatcher(
                                tokenDbEntity,
                                tokenPrice,
                                itemBinding
                            )
                        )
                        itemBinding.labelBalanceValue.visibility =
                            if (tokenPrice == null) View.GONE else View.VISIBLE
                        itemBinding.inputTokenAmount.setText(
                            viewModel.uiLogic.tokenAmountToText(
                                amountChosen,
                                tokenDbEntity.decimals
                            )
                        )
                        itemBinding.labelTokenBalance.setOnClickListener {
                            itemBinding.inputTokenAmount.setText(
                                viewModel.uiLogic.tokenAmountToText(
                                    tokenDbEntity.amount!!,
                                    tokenDbEntity.decimals
                                )
                            )
                        }
                    }

                    itemBinding.buttonTokenRemove.setOnClickListener {
                        if (isSingular || itemBinding.inputTokenAmount.text.isEmpty()) {
                            viewModel.uiLogic.removeToken(ergoId)
                        } else {
                            itemBinding.inputTokenAmount.text = null
                            itemBinding.inputTokenAmount.requestFocus()
                        }
                    }
                }
            }

            setFocusToEmptyTokenAmountInput()
        }

        showPaymentRequestWarnings()
    }

    private fun setFocusToEmptyTokenAmountInput() {
        binding.tokensList.descendants.firstOrNull {
            it is EditText && it.text.isEmpty() && it.isEnabled && it.visibility == View.VISIBLE
        }?.requestFocus()
    }

    private fun setAmountEdittext(amountToSend: ErgoAmount) {
        binding.amount.editText?.setText(amountToSend.toStringTrimTrailingZeros())
    }

    private fun startPayment() {
        val checkResponse = viewModel.uiLogic.checkCanMakePayment(Preferences(requireContext()))

        if (checkResponse.receiverError) {
            binding.tvReceiver.error = getString(R.string.error_receiver_address)
            binding.tvReceiver.editText?.requestFocus()
        }
        if (checkResponse.messageError) {
            binding.tiMessage.error = getString(R.string.error_purpose_message)
            showPurposeMessageInformation(true)
        } else {
            binding.tiMessage.error = null
        }
        if (checkResponse.amountError) {
            binding.amount.error = getString(R.string.error_amount)
            if (!checkResponse.receiverError) binding.amount.editText?.requestFocus()
        }
        if (checkResponse.tokenError) {
            binding.labelTokenAmountError.visibility = View.VISIBLE
            setFocusToEmptyTokenAmountInput()
        }

        if (checkResponse.canPay) {
            startAuthFlow()
        }
    }

    private fun showPurposeMessageInformation(startPayment: Boolean = false) {
        val context = requireContext()
        val prefs = Preferences(context)
        MaterialAlertDialogBuilder(context)
            .setMessage(R.string.info_purpose_message)
            .setPositiveButton(R.string.info_purpose_message_accept) { _, _ ->
                prefs.sendTxMessages = true
                if (startPayment) startPayment()
            }
            .setNegativeButton(R.string.info_purpose_message_decline) { _, _ ->
                prefs.sendTxMessages = false
            }
            .show()
    }

    override fun showBiometricPrompt() {
        hideForcedSoftKeyboard(requireContext(), binding.amount.editText!!)
        super.showBiometricPrompt()
    }

    private fun inputChangesToViewModel() {
        val uiLogic = viewModel.uiLogic
        uiLogic.receiverAddress = binding.tvReceiver.editText?.text?.toString() ?: ""
        uiLogic.message = binding.tiMessage.editText?.text?.toString() ?: ""

        val amountStr = binding.amount.editText?.text.toString()
        val ergoAmount = amountStr.toErgoAmount()
        uiLogic.amountToSend = ergoAmount ?: ErgoAmount.ZERO
        if (ergoAmount == null) {
            // conversion error, too many decimals or too big for long
            binding.amount.error = getString(R.string.error_amount)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            result.contents?.let { qrCodeScanned(it) }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun qrCodeScanned(qrCode: String) {
        viewModel.uiLogic.qrCodeScanned(
            qrCode,
            AndroidStringProvider(requireContext()),
            { data, walletId ->
                findNavController().navigate(
                    SendFundsFragmentDirections
                        .actionSendFundsFragmentToColdWalletSigningFragment(
                            data,
                            walletId
                        )
                )
            },
            { ergoPayRequest ->
                findNavController().navigateSafe(
                    SendFundsFragmentDirections.actionSendFundsFragmentToErgoPaySigningFragment(
                        ergoPayRequest, args.walletId, viewModel.uiLogic.derivedAddressIdx ?: -1
                    )
                )
            },
            setPaymentRequestDataToUi = { address, amount, message ->
                binding.tvReceiver.editText?.setText(address)
                amount?.let { setAmountEdittext(amount) }
                message?.let { binding.tiMessage.editText?.setText(message) }
            })
    }

    private fun showPaymentRequestWarnings() {
        viewModel.uiLogic.getPaymentRequestWarnings(AndroidStringProvider(requireContext()))?.let {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(it)
                .setPositiveButton(R.string.zxing_button_ok, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class MyTextWatcher(private val textInputLayout: TextInputLayout) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }

        override fun afterTextChanged(s: Editable?) {
            textInputLayout.error = null
            inputChangesToViewModel()
        }

    }

    inner class TokenAmountWatcher(
        private val token: WalletToken,
        private val tokenPrice: TokenPrice?,
        private val itemBinding: FragmentSendFundsTokenItemBinding
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }

        override fun afterTextChanged(s: Editable?) {
            val tokenId = token.tokenId!!
            val amount =
                s?.toString()?.toTokenAmount(token.decimals) ?: TokenAmount(0, token.decimals)
            viewModel.uiLogic.setTokenAmount(tokenId, amount)
            tokenPrice?.let {
                itemBinding.labelBalanceValue.text = getString(
                    R.string.label_fiat_amount, formatTokenPriceToString(
                        amount,
                        it.ergValue,
                        WalletStateSyncManager.getInstance(),
                        AndroidStringProvider(requireContext())
                    )
                )
            }
            binding.labelTokenAmountError.visibility = View.GONE
        }

    }
}