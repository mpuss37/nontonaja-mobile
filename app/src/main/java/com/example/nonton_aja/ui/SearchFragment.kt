package com.example.nonton_aja.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nonton_aja.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var filmAdapter: FilmAdapter
    private lateinit var suggestionAdapter: FilmAdapter

    private lateinit var genreContainer: ScrollView
    private lateinit var suggestionsRecycler: RecyclerView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var resultsRecycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var loadMoreProgress: ProgressBar
    private lateinit var searchInput: EditText

    var onFilmClick: ((com.example.nonton_aja.data.SearchItem) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.searchInput)
        genreContainer = view.findViewById(R.id.genreContainer)
        suggestionsRecycler = view.findViewById(R.id.suggestionsRecycler)
        resultsContainer = view.findViewById(R.id.resultsContainer)
        resultsRecycler = view.findViewById(R.id.resultsRecycler)
        progressBar = view.findViewById(R.id.progressBar)
        errorText = view.findViewById(R.id.errorText)
        loadMoreProgress = view.findViewById(R.id.loadMoreProgress)

        // Setup adapters
        filmAdapter = FilmAdapter { item -> onFilmClick?.invoke(item) }
        suggestionAdapter = FilmAdapter { item ->
            viewModel.searchFromSuggestion(item.title)
            searchInput.setText(item.title)
        }

        resultsRecycler.layoutManager = LinearLayoutManager(requireContext())
        resultsRecycler.adapter = filmAdapter

        suggestionsRecycler.layoutManager = LinearLayoutManager(requireContext())
        suggestionsRecycler.adapter = suggestionAdapter

        // Pagination
        resultsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 3
                    && !viewModel.uiState.value.isLoadingMore
                    && viewModel.uiState.value.hasMore
                    && !viewModel.uiState.value.isLoading
                ) {
                    viewModel.loadMore()
                }
            }
        })

        // Setup genre chips
        setupGenreChips(view)

        // Setup trending chips
        setupTrendingChips(view)

        // Search input
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onQueryChange(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && searchInput.text.isNotBlank()) {
                viewModel.search(reset = true)
                true
            } else false
        }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update views
                    genreContainer.isVisible = state.results.isEmpty() && state.suggestions.isEmpty() && state.query.isBlank()
                    suggestionsRecycler.isVisible = state.suggestions.isNotEmpty() && state.query.isNotBlank() && state.results.isEmpty()
                    resultsContainer.isVisible = state.results.isNotEmpty() || state.isLoading

                    suggestionAdapter.submitList(state.suggestions)
                    filmAdapter.submitList(state.results)
                    progressBar.isVisible = state.isLoading && state.results.isEmpty()
                    errorText.isVisible = state.error != null && state.results.isEmpty()
                    errorText.text = state.error ?: ""
                    loadMoreProgress.isVisible = state.isLoadingMore
                }
            }
        }

        // Auto-focus
        searchInput.requestFocus()
    }

    private fun setupGenreChips(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.genreChipGroup)
        chipGroup.removeAllViews()
        for (genre in viewModel.genres) {
            val chip = Chip(requireContext()).apply {
                text = genre
                isCheckable = false
                setOnClickListener {
                    searchInput.setText(genre)
                    viewModel.searchFromSuggestion(genre)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupTrendingChips(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.trendingChipGroup)
        chipGroup.removeAllViews()
        for (term in viewModel.trendingSearches) {
            val chip = Chip(requireContext()).apply {
                text = term
                isCheckable = false
                setOnClickListener {
                    searchInput.setText(term)
                    viewModel.searchFromSuggestion(term)
                }
            }
            chipGroup.addView(chip)
        }
    }
}
