package com.example.nonton_aja.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.example.nonton_aja.data.SearchItem
import com.google.gson.Gson

class PlayerFragment : Fragment() {

    companion object {
        private const val ARG_ITEM_JSON = "item_json"

        fun newInstance(item: SearchItem): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM_JSON, Gson().toJson(item))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val json = arguments?.getString(ARG_ITEM_JSON) ?: return View(context)
        val item = Gson().fromJson(json, SearchItem::class.java)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PlayerScreen(
                    item = item,
                    onBack = {
                        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                )
            }
        }
    }
}
