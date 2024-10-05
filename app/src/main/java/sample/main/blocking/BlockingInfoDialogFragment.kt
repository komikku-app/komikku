package sample.main.blocking

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.FragmentBlockingBottomSheetBinding
import kotlinx.coroutines.launch
import sample.main.MainViewModel

/**
 * A fragment to show the list of blocked requests along with affective filter rule.
 *
 * Created by Edsuns@qq.com on 2021/2/27.
 */
class BlockingInfoDialogFragment : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentBlockingBottomSheetBinding

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var recyclerViewAdapter: BlockedListAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentBlockingBottomSheetBinding.inflate(inflater, container, false)

        binding.dismissBtn.setOnClickListener { dismiss() }

        val recyclerView = binding.blockedList
        recyclerViewAdapter = BlockedListAdapter(inflater)
        recyclerView.adapter = recyclerViewAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        /** Observe the [MainViewModel.blockingInfoMap] which contains info of blocked requests. */
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.blockingInfoMap.collect {
                    val pageUrl = viewModel.currentPageUrl.value
                    val blockingInfo = it[pageUrl]
                    if (blockingInfo != null) {
                        val blockedUrlCount = blockingInfo.blockedUrlMap.size
                        if (blockingInfo.allRequests > 0) {
                            binding.title.text =
                                "${getString(R.string.blocked)} $blockedUrlCount ${
                                    getString(
                                        R.string.connections,
                                    )
                                }"
                            binding.titleDescription.text =
                                "${blockingInfo.blockedRequests} ${getString(R.string.times_blocked)}" +
                                " / ${blockingInfo.allRequests} ${getString(R.string.requests)}"
                        } else {
                            binding.title.text = getString(R.string.empty)
                            binding.titleDescription.text = ""
                        }
                    }
                    updateRecyclerView()
                }
            }
        }

        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView() {
        val blockedUrlMap =
            viewModel.blockingInfoMap.value[viewModel.currentPageUrl.value]?.blockedUrlMap
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
