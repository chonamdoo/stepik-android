package org.stepic.droid.features.course.ui.adapter

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import org.stepic.droid.R
import org.stepic.droid.features.course.ui.fragment.CourseContentFragment
import org.stepic.droid.features.course.ui.fragment.CourseInfoFragment
import org.stepik.android.model.Course

class CoursePagerAdapter(
        courseId: Long,
        context: Context,
        fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager) {
    private val fragments = listOf(
            { CourseInfoFragment.newInstance(courseId) }    to context.getString(R.string.course_tab_info),
            { CourseContentFragment.newInstance(courseId) } to context.getString(R.string.course_tab_modules)

//            ::Fragment to context.getString(R.string.course_tab_reviews),
    )

    override fun getItem(position: Int): Fragment =
            fragments[position].first.invoke()

    override fun getCount(): Int =
            fragments.size

    override fun getPageTitle(position: Int): CharSequence =
            fragments[position].second
}