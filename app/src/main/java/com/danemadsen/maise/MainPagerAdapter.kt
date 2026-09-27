package com.danemadsen.maise

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.danemadsen.maise.asr.AsrFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 1
    override fun createFragment(position: Int): Fragment = when (position) {
        0    -> AsrFragment()
        else -> AsrFragment()
    }
}
