package com.example.nonton_aja.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nonton_aja.data.SearchItem
import com.example.nonton_aja.data.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SearchRepository()
    private val prefs = application.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var currentPage = 1

    val genres = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Horror",
        "Sci-Fi", "Thriller", "Romance", "Animation", "Crime",
        "Fantasy", "Mystery", "Documentary", "Family", "Biography"
    )

    val trendingSearches = listOf(
        "Avengers", "Spider-Man", "Batman", "One Piece",
        "Squid Game", "Naruto", "Breaking Bad"
    )

    init {
        loadRecentSearches()
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        if (query.length >= 2) {
            searchWithDebounce(query)
        } else {
            _uiState.value = _uiState.value.copy(suggestions = emptyList())
        }
    }

    private fun searchWithDebounce(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val response = repository.search(query, 1, 10, "lk21")
                _uiState.value = _uiState.value.copy(suggestions = response.results)
            } catch (_: Exception) { }
        }
    }

    fun search(reset: Boolean = true) {
        val query = _uiState.value.query
        if (query.isBlank()) return

        searchJob?.cancel()
        if (reset) {
            currentPage = 1
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, suggestions = emptyList())
            saveRecentSearch(query)
        } else {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
        }

        searchJob = viewModelScope.launch {
            try {
                val response = repository.search(query, currentPage, 20, "lk21")
                val current = if (reset) emptyList() else _uiState.value.results
                _uiState.value = _uiState.value.copy(
                    results = current + response.results,
                    hasMore = response.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false,
                    isLoadingMore = false
                )
            }
        }
    }

    fun loadMore() {
        currentPage++
        search(reset = false)
    }

    fun clearSuggestions() {
        _uiState.value = _uiState.value.copy(suggestions = emptyList())
    }

    // Recent searches
    private fun loadRecentSearches() {
        val json = prefs.getString("recent_searches", null)
        if (json != null) {
            val list = json.split("|||").filter { it.isNotBlank() }
            _uiState.value = _uiState.value.copy(recentSearches = list)
        }
    }

    private fun saveRecentSearch(query: String) {
        val current = _uiState.value.recentSearches.toMutableList()
        current.remove(query)
        current.add(0, query)
        val trimmed = current.take(10)
        prefs.edit().putString("recent_searches", trimmed.joinToString("|||")).apply()
        _uiState.value = _uiState.value.copy(recentSearches = trimmed)
    }

    fun removeRecentSearch(query: String) {
        val current = _uiState.value.recentSearches.toMutableList()
        current.remove(query)
        prefs.edit().putString("recent_searches", current.joinToString("|||")).apply()
        _uiState.value = _uiState.value.copy(recentSearches = current)
    }

    fun clearAllRecentSearches() {
        prefs.edit().remove("recent_searches").apply()
        _uiState.value = _uiState.value.copy(recentSearches = emptyList())
    }

    fun searchFromSuggestion(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        search(reset = true)
    }
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchItem> = emptyList(),
    val suggestions: List<SearchItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)
