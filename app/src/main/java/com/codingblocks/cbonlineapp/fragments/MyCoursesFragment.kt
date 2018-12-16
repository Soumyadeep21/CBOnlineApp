package com.codingblocks.cbonlineapp.fragments


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.Utils.retrofitCallback
import com.codingblocks.cbonlineapp.adapters.MyCoursesDataAdapter
import com.codingblocks.cbonlineapp.database.AppDatabase
import com.codingblocks.cbonlineapp.database.Course
import com.codingblocks.cbonlineapp.database.CourseWithInstructor
import com.codingblocks.cbonlineapp.database.Instructor
import com.codingblocks.cbonlineapp.ui.AllCourseFragmentUi
import com.codingblocks.onlineapi.Clients
import com.codingblocks.onlineapi.models.MyCourse
import com.ethanhua.skeleton.Skeleton
import com.ethanhua.skeleton.SkeletonScreen
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.ctx
import kotlin.concurrent.thread


class MyCoursesFragment : Fragment(), AnkoLogger {

    val ui = AllCourseFragmentUi<Fragment>()
    private lateinit var courseDataAdapter: MyCoursesDataAdapter
    private lateinit var skeletonScreen: SkeletonScreen

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context!!)
    }

    private val courseDao by lazy {
        database.courseDao()
    }
    private val courseWithInstructorDao by lazy {
        database.courseWithInstructorDao()
    }
    private val instructorDao by lazy {
        database.instructorDao()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ui.createView(AnkoContext.create(ctx, this))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui.titleText.text = "My Courses"
        courseDataAdapter = MyCoursesDataAdapter(ArrayList(), activity!!)

        ui.rvCourses.layoutManager = LinearLayoutManager(ctx)
        ui.rvCourses.adapter = courseDataAdapter

        skeletonScreen = Skeleton.bind(ui.rvCourses)
                .adapter(courseDataAdapter)
                .shimmer(true)
                .angle(20)
                .frozen(true)
                .duration(1200)
                .count(4)
                .load(R.layout.item_skeleton_course_card)
                .show()
        courseDao.getMyCourse().observe(this, Observer<List<Course>> {
            if (it.isNotEmpty()) {
                skeletonScreen.hide()
            }
            courseDataAdapter.setData(it as ArrayList<Course>)

        })
        fetchAllCourses()
    }

    private fun fetchAllCourses() {

        Clients.onlineV2JsonApi.getMyCourses().enqueue(retrofitCallback { t, resp ->
            resp?.body()?.let {
                info { it.toString() }
                for (myCourses in it) {

                    //Add Course Progress to Course Object
                    Clients.api.getMyCourseProgress(myCourses.run_attempts?.get(0)?.id.toString()).enqueue(retrofitCallback { t, progressResponse ->
                        progressResponse?.body().let { map ->
                            val progress = map!!["percent"] as Double
                            val course = myCourses.course?.run {
                                Course(
                                        id ?: "",
                                        title ?: "",
                                        subtitle ?: "",
                                        logo ?: "",
                                        summary ?: "",
                                        promoVideo ?: "",
                                        difficulty ?: "",
                                        reviewCount ?: 0,
                                        rating ?: 0f,
                                        slug ?: "",
                                        coverImage ?: "",
                                        myCourses.run_attempts?.get(0)?.id ?: "",
                                        updatedAt,
                                        progress,
                                        myCourses.description ?: ""
                                )
                            }
                            thread {
                                courseDao.update(course!!)
                                //fetch CourseInstructors

                                myCourses.course?.instructors?.forEachIndexed { index, it ->
                                    Clients.onlineV2JsonApi.instructorsById(it.id!!).enqueue(retrofitCallback { throwable, response ->

                                        response?.body().let { instructor ->
                                            thread {
                                                instructorDao.insert(Instructor(instructor?.id
                                                        ?: "", instructor?.name ?: "",
                                                        instructor?.description
                                                                ?: "", instructor?.photo ?: "",
                                                        "", myCourses.run_attempts!![0].id!!, myCourses.course!!.id))
                                                try {
                                                    Log.e("TAG", "ID : ${instructor?.id}  Name : ${instructor?.name}")
                                                    insertCourseAndInstructor(myCourses.course!!, instructor!!)
                                                } catch (e: Exception) {
                                                    Log.e("TAG", "gfdsgdsg" + instructor?.id + myCourses.course?.id, e)
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    })
                }
            }
        })
    }

    private fun insertCourseAndInstructor(course: MyCourse, instructor: com.codingblocks.onlineapi.models.Instructor) {

        thread {
            courseWithInstructorDao.insert(CourseWithInstructor(course.id!!, instructor.id!!))
        }
    }

}
