package com.example.nonton_aja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nonton_aja.data.HomeRepository
import com.example.nonton_aja.data.HomeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository = HomeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getHome()
                _uiState.value = _uiState.value.copy(
                    hero = response.hero,
                    trending = response.trending,
                    popularMovies = response.popularMovies,
                    popularTv = response.popularTv,
                    newReleases = response.newReleases,
                    topRated = response.topRated,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false,
                )
            }
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val hero: com.example.nonton_aja.data.SearchItem? = null,
    val trending: List<com.example.nonton_aja.data.SearchItem> = emptyList(),
    val popularMovies: List<com.example.nonton_aja.data.SearchItem> = emptyList(),
    val popularTv: List<com.example.nonton_aja.data.SearchItem> = emptyList(),
    val newReleases: List<com.example.nonton_aja.data.SearchItem> = emptyList(),
    val topRated: List<com.example.nonton_aja.data.SearchItem> = emptyList(),
)
