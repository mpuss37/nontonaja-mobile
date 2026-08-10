package com.example.nonton_aja.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.example.nonton_aja.data.SearchItem
import com.google.gson.Gson

class DetailFragment : Fragment() {

    companion object {
        private const val ARG_ITEM_JSON = "item_json"

        fun newInstance(item: SearchItem): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM_JSON, Gson().toJson(item))
                }
            }
        }
    }

    var onPlay: ((SearchItem) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val json = arguments?.getString(ARG_ITEM_JSON) ?: return View(context)
        val item = Gson().fromJson(json, SearchItem::class.java)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DetailScreen(
                    item = item,
                    onBack = { requireActivity().supportFragmentManager.popBackStack() },
                    onPlay = { onPlay?.invoke(item) }
                )
            }
        }
    }
}
