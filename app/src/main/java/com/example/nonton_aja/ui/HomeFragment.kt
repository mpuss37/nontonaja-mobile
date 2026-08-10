package com.example.nonton_aja.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    var onFilmClick: ((com.example.nonton_aja.data.SearchItem) -> Unit)? = null
    var onSearchClick: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HomeScreen(
                    onFilmClick = { item -> onFilmClick?.invoke(item) },
                    onSearchClick = { onSearchClick?.invoke() }
                )
            }
        }
    }
}
