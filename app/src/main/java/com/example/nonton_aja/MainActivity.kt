package com.example.nonton_aja

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.example.nonton_aja.ui.DetailFragment
import com.example.nonton_aja.ui.HomeFragment
import com.example.nonton_aja.ui.PlayerFragment
import com.example.nonton_aja.ui.SearchFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : FragmentActivity() {

    private var activeFragment: androidx.fragment.app.Fragment? = null
    private lateinit var bottomNav: BottomNavigationView

    private fun navigateToDetail(item: com.example.nonton_aja.data.SearchItem) {
        hideBottomNav()
        val detailFragment = DetailFragment.newInstance(item).apply {
            onPlay = { film ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, PlayerFragment.newInstance(film))
                    .addToBackStack(null)
                    .commit()
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                showBottomNav()
            } else {
                hideBottomNav()
            }
        }

        if (savedInstanceState == null) {
            showFragment(HomeFragment().apply {
                onFilmClick = { item -> navigateToDetail(item) }
                onSearchClick = { bottomNav.selectedItemId = R.id.nav_search }
            }, "home")
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    showFragment(HomeFragment().apply {
                        onFilmClick = { film -> navigateToDetail(film) }
                        onSearchClick = { bottomNav.selectedItemId = R.id.nav_search }
                    }, "home")
                    true
                }
                R.id.nav_search -> {
                    supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    showFragment(SearchFragment().apply {
                        onFilmClick = { film -> navigateToDetail(film) }
                    }, "search")
                    true
                }
                else -> false
            }
        }
    }

    private fun showBottomNav() {
        bottomNav.visibility = View.VISIBLE
    }

    private fun hideBottomNav() {
        bottomNav.visibility = View.GONE
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment, tag: String) {
        val transaction = supportFragmentManager.beginTransaction()
        activeFragment?.let { transaction.hide(it) }
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            transaction.show(existing)
        } else {
            transaction.add(R.id.fragmentContainer, fragment, tag)
        }
        transaction.commit()
        activeFragment = fragment
        showBottomNav()
    }
}
