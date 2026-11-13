package com.movtery.zalithlauncher.ui.fragment.about

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentAboutInfoPageBinding
import com.movtery.zalithlauncher.ui.subassembly.about.AboutItemBean
import com.movtery.zalithlauncher.ui.subassembly.about.AboutRecyclerAdapter
import com.movtery.zalithlauncher.utils.ZHTools

class AboutInfoPageFragment() : Fragment(R.layout.fragment_about_info_page) {
    private lateinit var binding: FragmentAboutInfoPageBinding
    private val mAboutData: MutableList<AboutItemBean> = ArrayList()
    private var parentPager2: ViewPager2? = null

    constructor(parentPager: ViewPager2): this() { this.parentPager2 = parentPager }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAboutInfoPageBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadAboutData(requireContext().resources)
        val context = requireActivity()
        binding.apply {
            dec1.text = InfoCenter.replaceName(context, R.string.about_dec1)
            dec2.text = getString(R.string.about_dec2)
            dec3.text = InfoCenter.replaceName(context, R.string.about_dec3)
            githubButton.visibility = View.GONE
            qqGroupButton.visibility = View.GONE
            discordButton.visibility = View.GONE
            sponsor.visibility = View.GONE
            licenseButton.setOnClickListener { ZHTools.openLink(context, "https://www.gnu.org/licenses/gpl-3.0.html") }
            aboutRecycler.layoutManager = LinearLayoutManager(requireContext())
            aboutRecycler.adapter = AboutRecyclerAdapter(mAboutData)
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun loadAboutData(resources: Resources) {
        mAboutData.clear()
        // Credits remain visible; all upstream social, donation and promotional links are removed.
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.ic_pojav_full, requireContext().theme), "PojavLauncherTeam", getString(R.string.about_PojavLauncher_desc), null))
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.image_about_movtery, requireContext().theme), "MovTery", getString(R.string.about_MovTery_desc), null))
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.image_about_mcmod, requireContext().theme), "MC百科", getString(R.string.about_mcmod_desc), null))
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.image_about_verafirefly, requireContext().theme), "Vera-Firefly", getString(R.string.about_VeraFirefly_desc), null))
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.image_about_lingmuqiuzhu, requireContext().theme), "柃木湫竹", getString(R.string.about_LingMuQiuZhu_desc), null))
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.image_about_shirosakimio, requireContext().theme), "ShirosakiMio", getString(R.string.about_ShirosakiMio_desc), null))
        mAboutData.add(AboutItemBean(resources.getDrawable(R.drawable.image_about_bangbang93, requireContext().theme), "bangbang93", getString(R.string.about_bangbang93_desc), null))
    }
}
