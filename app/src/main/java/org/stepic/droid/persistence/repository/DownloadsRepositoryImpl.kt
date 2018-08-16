package org.stepic.droid.persistence.repository

import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables.zip
import io.reactivex.rxkotlin.toObservable
import org.stepic.droid.persistence.di.PersistenceScope
import org.stepic.droid.persistence.downloads.resolvers.DownloadTitleResolver
import org.stepic.droid.persistence.files.ExternalStorageManager
import org.stepic.droid.persistence.model.*
import org.stepic.droid.persistence.storage.PersistentStateManager
import org.stepic.droid.persistence.storage.dao.SystemDownloadsDao
import org.stepic.droid.persistence.storage.dao.PersistentItemDao
import org.stepic.droid.storage.repositories.Repository
import org.stepic.droid.util.plus
import org.stepik.android.model.Step
import org.stepik.android.model.Video
import java.io.File
import javax.inject.Inject
import kotlin.math.max

@PersistenceScope
class DownloadsRepositoryImpl
@Inject
constructor(
        private val updatesObservable: Observable<Structure>,
        private val intervalUpdatesObservable: Observable<kotlin.Unit>,

        private val systemDownloadsDao: SystemDownloadsDao,
        private val persistentItemDao: PersistentItemDao,
        private val persistentStateManager: PersistentStateManager,
        private val stepRepository: Repository<Step>,

        private val downloadTitleResolver: DownloadTitleResolver,
        private val externalStorageManager: ExternalStorageManager
): DownloadsRepository {
    override fun getDownloads(): Observable<DownloadItem> = (
                intervalUpdatesObservable.startWith(kotlin.Unit)
                    .flatMap {
                        (
                                persistentItemDao.getItemsByStatus(PersistentItem.Status.COMPLETED) +
                                persistentItemDao.getItemsByStatus(PersistentItem.Status.IN_PROGRESS) +
                                persistentItemDao.getItemsByStatus(PersistentItem.Status.FILE_TRANSFER)
                        ).reduce { a, b -> a + b }.toObservable()
                    } // get all downloads with interval
                    .flatMap {
                        it.groupBy { item -> item.task.structure.step }.map { (a, b) -> a to b }.toObservable()
                    }
                +
                updatesObservable
                    .flatMap { structure -> persistentItemDao.getItemsByStep(structure.step).map { structure.step to it } }
            )
            .map { (stepId, items) ->
                return@map if (persistentStateManager.getState(stepId, PersistentState.Type.STEP) == PersistentState.State.CACHED) {
                    stepId to items
                } else {
                    stepId to emptyList()
                }
            }
            .flatMap { (stepId, items) -> resolveStep(stepId, items) }

    private fun resolveStep(stepId: Long, items: List<PersistentItem>): Observable<DownloadItem> = Observable
            .fromCallable { stepRepository.getObject(stepId) }
            .flatMap { step ->
                zip(
                        downloadTitleResolver.resolveTitle(step.lesson, step.id).toObservable(),
                        getStepVideo(step),
                        getStorageRecords(items)
                )
            }.map { (title, video, records) ->
                resolveDownloadItem(stepId, title, video, items, records)
            }

    private fun getStorageRecords(items: List<PersistentItem>) = Observable
            .fromCallable { items.filter { it.status == PersistentItem.Status.IN_PROGRESS || it.status == PersistentItem.Status.FILE_TRANSFER } }
            .flatMap { systemDownloadsDao.get(*it.map(PersistentItem::downloadId).toLongArray()) }

    private fun getStepVideo(step: Step) =
            step.block?.video?.let { Observable.just(it) } ?: Observable.empty<Video>()

    private fun resolveDownloadItem(stepId: Long, title: String, video: Video, items: List<PersistentItem>, records: List<SystemDownloadRecord>): DownloadItem {
        var bytesDownloaded = 0L
        var bytesTotal = 0L

        val linksMap = mutableMapOf<String, String>()

        var hasItemsInProgress = false
        var hasUndownloadedItems = items.isEmpty()

        items.forEach { item ->
            when(item.status) {
                PersistentItem.Status.COMPLETED -> {
                    val filePath = externalStorageManager.resolvePathForPersistentItem(item)
                    if (filePath == null) {
                        hasUndownloadedItems = true
                        return@forEach
                    } else {
                        val fileSize = File(filePath).length()
                        bytesDownloaded += fileSize
                        bytesTotal += fileSize

                        linksMap[item.task.originalPath] = filePath
                    }
                }

                PersistentItem.Status.IN_PROGRESS,
                PersistentItem.Status.FILE_TRANSFER -> {
                    val record = records.find { it.id == item.downloadId }
                    if (record == null) {
                        hasUndownloadedItems = true
                        return@forEach
                    } else {
                        bytesDownloaded += record.bytesDownloaded
                        bytesTotal += max(record.bytesDownloaded, record.bytesTotal) // total could be 0
                        hasItemsInProgress = true
                    }
                }

                else -> {
                    hasUndownloadedItems = true
                    return@forEach
                }
            }
        }

        val stepVideo = injectVideo(video, linksMap)

        val status = when {
            hasUndownloadedItems -> {
                DownloadProgress.Status.NotCached
            }

            hasItemsInProgress -> {
                val progress = if (bytesTotal <= 0) 0f else bytesDownloaded.toFloat() / bytesTotal
                DownloadProgress.Status.InProgress(progress)
            }

            else -> {
                DownloadProgress.Status.Cached
            }
        }

        return DownloadItem(stepId, title, stepVideo, bytesDownloaded, bytesTotal, status)
    }

    private fun injectVideo(video: Video, linksMap: Map<String, String>): Video {
        val thumbnail = linksMap[video.thumbnail]
        val urls = video.urls?.mapNotNull { videoUrl ->
            linksMap[videoUrl.url]?.let { videoUrl.copy(url = it) }
        }
        return video.copy(thumbnail = thumbnail, urls = urls)
    }
}