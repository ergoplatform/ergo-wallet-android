package org.ergoplatform.android.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import org.ergoplatform.ErgoAmount
import org.ergoplatform.android.R
import org.ergoplatform.android.databinding.EntryTransactionBoxBinding
import org.ergoplatform.android.databinding.EntryWalletTokenBinding
import org.ergoplatform.android.databinding.FragmentColdWalletSigningBinding
import org.ergoplatform.explorer.client.model.AssetInstanceInfo

/**
 * Scans cold wallet signing request qr codes, signs the transaction, presents a qr code to go back
 */
class ColdWalletSigningFragment : Fragment() {

    var _binding: FragmentColdWalletSigningBinding? = null
    val binding get() = _binding!!

    private val args: ColdWalletSigningFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColdWalletSigningBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewModel = ViewModelProvider(this).get(ColdWalletSigningViewModel::class.java)

        args.qrCode?.let { viewModel.addQrCodeChunk(it) }
        viewModel.setWalletId(args.walletId, requireContext())

        viewModel.reducedTx.observe(viewLifecycleOwner, {
            it?.let {
                binding.transactionInfo.visibility = View.VISIBLE

                binding.layoutInboxes.apply {
                    removeAllViews()

                    it.inputs.forEach { input ->
                        bindBoxView(this, input.value, input.address ?: input.boxId, input.assets)
                    }
                }

                binding.layoutOutboxes.apply {
                    removeAllViews()

                    it.outputs.forEach { output ->
                        bindBoxView(this, output.value, output.address, output.assets)
                    }
                }
            }
        })
    }

    private fun bindBoxView(
        container: ViewGroup,
        value: Long,
        address: String,
        assets: List<AssetInstanceInfo>?
    ) {
        val outboxBinding = EntryTransactionBoxBinding.inflate(layoutInflater, container, true)
        outboxBinding.boxErgAmount.text = getString(
            R.string.label_erg_amount,
            ErgoAmount(value).toStringTrimTrailingZeros()
        )
        outboxBinding.labelOutboxAddress.text = address

        outboxBinding.boxTokenEntries.apply {
            removeAllViews()
            visibility = View.GONE

            assets?.forEach {
                visibility = View.VISIBLE
                val tokenBinding =
                    EntryWalletTokenBinding.inflate(layoutInflater, this, true)
                // we use the token id here, we don't have the name in the cold wallet context
                tokenBinding.labelTokenName.text = it.tokenId
                tokenBinding.labelTokenVal.text = it.amount.toString()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}