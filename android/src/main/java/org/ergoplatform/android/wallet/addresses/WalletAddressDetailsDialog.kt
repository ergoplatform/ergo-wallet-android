package org.ergoplatform.android.wallet.addresses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.room.withTransaction
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.ergoplatform.android.AppDatabase
import org.ergoplatform.android.databinding.FragmentWalletAddressDetailsDialogBinding
import org.ergoplatform.android.getAddressDerivationPath
import org.ergoplatform.android.ui.copyStringToClipboard

/**
 * Wallet address detail bottom sheet to edit an address label or delete the address
 */
class WalletAddressDetailsDialog : BottomSheetDialogFragment() {
    private var _binding: FragmentWalletAddressDetailsDialogBinding? = null
    private val binding get() = _binding!!

    private val args: WalletAddressDetailsDialogArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentWalletAddressDetailsDialogBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.descriptiveLabel.editText?.setOnEditorActionListener { _, _, _ ->
            binding.buttonApply.callOnClick()
            true
        }

        val addrId = args.walletAddressId

        binding.buttonApply.setOnClickListener { saveLabel(addrId) }
        binding.buttonRemove.setOnClickListener { deleteAddress(addrId) }

        lifecycleScope.launch {
            val walletAddress =
                AppDatabase.getInstance(requireContext()).walletDao().loadWalletAddress(addrId)

            binding.publicAddress.text = walletAddress?.publicAddress
            binding.descriptiveLabel.editText?.setText(walletAddress?.label)
            binding.publicAddress.setOnClickListener {
                copyStringToClipboard(
                    walletAddress!!.publicAddress,
                    requireContext(), null
                )
            }
            binding.derivationPath.text =
                getAddressDerivationPath(walletAddress?.derivationIndex ?: 0)
        }
    }

    private fun deleteAddress(addrId: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getInstance(requireContext())
            val walletDao = database.walletDao()
            database.withTransaction {
                val dbEntity = walletDao.loadWalletAddress(addrId)
                dbEntity?.publicAddress?.let {
                    walletDao.deleteAddressState(it)
                    walletDao.deleteTokensByAddress(it)
                }
                walletDao.deleteWalletAddress(addrId)
            }
        }
        dismiss()
    }

    private fun saveLabel(addrId: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            val label = binding.descriptiveLabel.editText?.text?.toString()
            AppDatabase.getInstance(requireContext()).walletDao().updateWalletAddressLabel(
                addrId,
                if (label.isNullOrBlank()) null else label
            )
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}