package com.example.vaanivideo.data.db

import android.content.Context
import androidx.room.Room
import com.example.vaanivideo.data.model.NarrationFileItem
import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.data.model.ProjectEntity
import com.example.vaanivideo.data.model.TimelineJsonExport
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectRepository(private val context: Context) {
    private val database: VaaniDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            VaaniDatabase::class.java,
            "vaani_video_database"
        ).fallbackToDestructiveMigration().build()
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val pageListType = Types.newParameterizedType(List::class.java, PageTurnItem::class.java)
    private val narrationListType = Types.newParameterizedType(List::class.java, NarrationFileItem::class.java)

    private val pageListAdapter = moshi.adapter<List<PageTurnItem>>(pageListType)
    private val narrationListAdapter = moshi.adapter<List<NarrationFileItem>>(narrationListType)
    private val timelineExportAdapter = moshi.adapter(TimelineJsonExport::class.java)

    val allProjects: Flow<List<ProjectEntity>> = database.projectDao().getAllProjects()

    suspend fun getProjectById(id: Long): ProjectEntity? = withContext(Dispatchers.IO) {
        database.projectDao().getProjectById(id)
    }

    suspend fun saveProject(project: ProjectEntity): Long = withContext(Dispatchers.IO) {
        database.projectDao().insertProject(project)
    }

    suspend fun updateProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        database.projectDao().updateProject(project)
    }

    suspend fun deleteProjectById(id: Long) = withContext(Dispatchers.IO) {
        database.projectDao().deleteProjectById(id)
    }

    fun serializePages(pages: List<PageTurnItem>): String {
        return pageListAdapter.toJson(pages)
    }

    fun deserializePages(json: String): List<PageTurnItem> {
        return try {
            pageListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeNarrations(narrations: List<NarrationFileItem>): String {
        return narrationListAdapter.toJson(narrations)
    }

    fun deserializeNarrations(json: String): List<NarrationFileItem> {
        return try {
            narrationListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun exportTimelineJson(export: TimelineJsonExport): String {
        return timelineExportAdapter.indent("  ").toJson(export)
    }

    fun importTimelineJson(json: String): TimelineJsonExport? {
        return try {
            timelineExportAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
