package org.stepik.android.view.course_info.ui.adapter.delegates

import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.view_course_info_video.view.*
import org.stepic.droid.R
import org.stepik.android.view.course_info.ui.adapter.CourseInfoAdapter
import org.stepik.android.view.course_info.model.CourseInfoItem
import org.stepic.droid.ui.custom.adapter_delegates.AdapterDelegate
import org.stepik.android.model.Video

class CourseInfoVideoBlockDelegate(
        adapter: CourseInfoAdapter,
        private val onVideoClicked: ((Video) -> Unit)?
) : AdapterDelegate<CourseInfoItem, CourseInfoAdapter.ViewHolder>(adapter) {
    override fun onCreateViewHolder(parent: ViewGroup) =
            ViewHolder(createView(parent, R.layout.view_course_info_video))

    override fun isForViewType(position: Int): Boolean =
            getItemAtPosition(position) is CourseInfoItem.VideoBlock

    inner class ViewHolder(root: View) : CourseInfoAdapter.ViewHolder(root) {
        private val videoThumbnail = root.videoThumbnail

        init {
            root.setOnClickListener {
                (itemData as? CourseInfoItem.VideoBlock)?.let { item ->
                    onVideoClicked?.invoke(item.video)
                }
            }
        }

        override fun onBind(data: CourseInfoItem) {
            data as CourseInfoItem.VideoBlock
            Glide.with(videoThumbnail.context)
                    .load(data.video.thumbnail)
                    .placeholder(R.drawable.general_placeholder)
                    .into(videoThumbnail)
        }
    }
}