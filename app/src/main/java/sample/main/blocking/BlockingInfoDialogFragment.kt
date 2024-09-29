package sample.main.blocking

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.FragmentBlockingBottomSheetBinding
import sample.main.MainViewModel

/**
 * Created by Edsuns@qq.com on 2021/2/27.
 */
class BlockingInfoDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentBlockingBottomSheetBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var recyclerViewAdapter: BlockedListAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlockingBottomSheetBinding.inflate(inflater, container, false)

        binding.dismissBtn.setOnClickListener { dismiss() }

        val recyclerView = binding.blockedList
        recyclerViewAdapter = BlockedListAdapter(inflater)
        recyclerView.adapter = recyclerViewAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        viewModel.blockingInfoMap.observe(viewLifecycleOwner, {
            val pageUrl = viewModel.currentPageUrl.value ?: return@observe
            val blockingInfo = it[pageUrl]
            if (blockingInfo != null) {
                val blockedUrlCount = blockingInfo.blockedUrlMap.size
                if (blockingInfo.allRequests > 0) {
                    binding.title.text =
                            "${getString(R.string.blocked)} $blockedUrlCount ${
                                getString(
                                        R.string.connections
                                )
                            }"
                    binding.titleDescription.text =
                            "${blockingInfo.blockedRequests} ${getString(R.string.times_blocked)} / ${blockingInfo.allRequests} ${
                                getString(R.string.requests)
                            }"
                } else {
                    binding.title.text = getString(R.string.empty)
                    binding.titleDescription.text = ""
                }
            }
            updateRecyclerView()
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateRecyclerView() {
        val blockedUrlMap =
                viewModel.blockingInfoMap.value?.get(viewModel.currentPageUrl.value)?.blockedUrlMap
        if (blockedUrlMap != null) {
            recyclerViewAdapter.data = blockedUrlMap
            recyclerViewAdapter.notifyDataSetChanged()
        }
    }

    companion object {

        fun newInstance(): BlockingInfoDialogFragment {
            val fragment = BlockingInfoDialogFragment()
            return fragment
        }
    }

}
