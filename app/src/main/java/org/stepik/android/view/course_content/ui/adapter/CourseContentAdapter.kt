package org.stepik.android.view.course_content.ui.adapter

import android.support.v4.util.LongSparseArray
import android.support.v7.util.DiffUtil
import org.stepik.android.view.course_content.ui.adapter.delegates.control_bar.CourseContentControlBarDelegate
import org.stepik.android.view.course_content.ui.adapter.delegates.unit.CourseContentUnitClickListener
import org.stepik.android.view.course_content.ui.adapter.delegates.unit.CourseContentUnitDelegate
import org.stepik.android.view.course_content.ui.adapter.delegates.section.CourseContentSectionDelegate
import org.stepik.android.view.course_content.model.CourseContentItem
import org.stepic.droid.persistence.model.DownloadProgress
import org.stepic.droid.ui.custom.adapter_delegates.DelegateViewHolder
import org.stepic.droid.ui.custom.adapter_delegates.DelegateAdapter
import org.stepik.android.view.course_content.ui.adapter.delegates.unit.CourseContentUnitPlaceholderDelegate

class CourseContentAdapter(
        unitClickListener: CourseContentUnitClickListener
) : DelegateAdapter<CourseContentItem, DelegateViewHolder<CourseContentItem>>() {
    private val headers = listOf(CourseContentItem.ControlBar)

    private val sectionDownloadStatuses = LongSparseArray<DownloadProgress.Status>()
    private val unitDownloadStatuses = LongSparseArray<DownloadProgress.Status>()

    var items: List<CourseContentItem> = emptyList()
        set(value) {
            DiffUtil.calculateDiff(
                CourseContentDiffCallback(
                    field,
                    value
                )
            ).dispatchUpdatesTo(this)
            field = value
        }

    init {
        addDelegate(CourseContentControlBarDelegate(this))
        addDelegate(CourseContentSectionDelegate(this, sectionDownloadStatuses))
        addDelegate(CourseContentUnitDelegate(this, unitClickListener, unitDownloadStatuses))
        addDelegate(CourseContentUnitPlaceholderDelegate(this))
    }

    fun updateSectionDownloadProgress(downloadProgress: DownloadProgress) {
        val sectionPosition = items
            .indexOfFirst { it is CourseContentItem.SectionItem && it.section.id == downloadProgress.id }
            .takeIf { it > 0 }
            ?: return

        sectionDownloadStatuses.append(downloadProgress.id, downloadProgress.status)
        notifyItemChanged(sectionPosition + headers.size)
    }

    fun updateUnitDownloadProgress(downloadProgress: DownloadProgress) {
        val unitPosition = items
            .indexOfFirst { it is CourseContentItem.UnitItem && it.unit.id == downloadProgress.id }
            .takeIf { it > 0 }
            ?: return

        unitDownloadStatuses.append(downloadProgress.id, downloadProgress.status)
        notifyItemChanged(unitPosition + headers.size)
    }

    override fun getItemAtPosition(position: Int): CourseContentItem =
            headers.getOrNull(position) ?: items[position - headers.size]

    override fun getItemCount(): Int =
        if (items.isNotEmpty()) headers.size + items.size else 0
}