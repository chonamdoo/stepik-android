package org.stepik.android.domain.course.interactor

import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles.zip
import io.reactivex.subjects.BehaviorSubject
import org.stepic.droid.model.CourseReviewSummary
import org.stepic.droid.util.safeDiv
import org.stepik.android.domain.course.model.CourseHeaderData
import org.stepik.android.domain.course.model.EnrollmentState
import org.stepik.android.domain.course.repository.CourseRepository
import org.stepik.android.domain.course.repository.CourseReviewRepository
import org.stepik.android.domain.progress.repository.ProgressRepository
import org.stepik.android.model.Course
import org.stepik.android.model.Progress
import org.stepik.android.view.injection.course.CourseScope
import javax.inject.Inject

@CourseScope
class CourseInteractor
@Inject
constructor(
    private val courseRepository: CourseRepository,
    private val courseReviewRepository: CourseReviewRepository,
    private val progressRepository: ProgressRepository,
    private val coursePublishSubject: BehaviorSubject<Course>
) {
    fun getCourseHeaderData(courseId: Long, canUseCache: Boolean = true): Maybe<CourseHeaderData> =
        courseRepository
            .getCourse(courseId, canUseCache)
            .doOnSuccess(coursePublishSubject::onNext)
            .flatMap(::obtainCourseHeaderData)

    /**
     * Trying to fetch DB data in first place as course object passed with intent could be obsolete
     */
    fun getCourseHeaderData(course: Course): Maybe<CourseHeaderData> =
        courseRepository
            .getCourse(course.id)
            .onErrorReturnItem(course)
            .doOnSuccess(coursePublishSubject::onNext)
            .flatMap(::obtainCourseHeaderData)

    private fun obtainCourseHeaderData(course: Course): Maybe<CourseHeaderData> =
        zip(
            courseReviewRepository.getCourseReview(course.reviewSummary).map(CourseReviewSummary::average).onErrorReturnItem(0.0),
            course.progress?.let(progressRepository::getProgress) ?: Single.just(Unit) // coroutines will handle it better
        )
            .map { (courseReview, courseProgress) ->
                CourseHeaderData(
                    courseId = course.id,
                    course = course,
                    title = course.title ?: "",
                    cover = course.cover ?: "",
                    learnersCount = course.learnersCount,

                    review = courseReview,
                    progress = (courseProgress as? Progress)?.let { (it.nStepsPassed * 100 safeDiv it.nSteps).coerceIn(0L..100L) },
                    readiness = course.readiness,
                    enrollmentState = if (course.enrollment > 0) EnrollmentState.ENROLLED else EnrollmentState.NOT_ENROLLED
                )
            }
            .toMaybe()

    fun restoreCourse(course: Course) =
        coursePublishSubject.onNext(course)
}