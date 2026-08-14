package com.nearexpiry.manager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import com.nearexpiry.manager.domain.model.MergeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TransactionalRepositoryTest {

    private lateinit var database: ExpiryDatabase
    private lateinit var expiryRepository: ExpiryRepositoryImpl
    private lateinit var projectRepository: ProjectRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpiryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        expiryRepository = ExpiryRepositoryImpl(database)
        projectRepository = ProjectRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceProjectItemsArchivesOldRowsAndReplacesThemAtomically() = runBlocking {
        val projectId = createProject("Restore target")
        database.expiryItemDao().insert(item(projectId, barcode = "old", quantity = 2.0))

        expiryRepository.replaceProjectItems(
            projectId,
            listOf(item(projectId = 0, barcode = "new-a", quantity = 3.0), item(projectId = 0, barcode = "new-b", quantity = 4.0))
        )

        val restored = database.expiryItemDao().getAllItemsOnce(projectId)
        assertEquals(listOf("new-a", "new-b"), restored.map { it.barcode }.sorted())
        assertEquals(1, database.recycleBinDao().getAll().first().size)
    }

    @Test
    fun restoreItemsIntoNewProjectCreatesTargetAndMergesDuplicates() = runBlocking {
        val result = projectRepository.restoreItemsIntoProject(
            projectId = null,
            newProjectName = "Imported",
            colorHex = "#26C6DA",
            items = listOf(
                item(projectId = 0, barcode = "first", itemCode = "POS-1", quantity = 2.0),
                item(projectId = 0, barcode = "second", itemCode = "POS-1", quantity = 3.0)
            )
        )

        assertNotNull(database.projectDao().getProjectById(result.projectId))
        assertEquals(1, result.inserted)
        assertEquals(1, result.merged)
        assertEquals(5.0, result.quantityAdded, 0.0)
        assertEquals(5.0, database.expiryItemDao().getAllItemsOnce(result.projectId).single().quantity, 0.0)
    }

    @Test
    fun deleteProjectArchivesItemsThenRemovesProjectAsOneOperation() = runBlocking {
        val projectA = createProject("Project A")
        createProject("Project B")
        database.expiryItemDao().insert(item(projectA, barcode = "delete-me"))

        assertTrue(projectRepository.deleteProject(projectA))

        assertNull(database.projectDao().getProjectById(projectA))
        assertTrue(database.expiryItemDao().getAllItemsOnce(projectA).isEmpty())
        assertEquals(1, database.recycleBinDao().getAll().first().size)
    }

    @Test
    fun cloneProjectCopiesEveryItemIntoTheNewProject() = runBlocking {
        val source = createProject("Source")
        database.expiryItemDao().insert(item(source, barcode = "clone-a", quantity = 1.0))
        database.expiryItemDao().insert(item(source, barcode = "clone-b", quantity = 2.0))

        val clone = projectRepository.cloneProject(source, "Clone", "#123456")

        assertNotNull(database.projectDao().getProjectById(clone))
        assertEquals(2, database.expiryItemDao().getAllItemsOnce(clone).size)
        assertTrue(database.expiryItemDao().getAllItemsOnce(clone).all { it.projectId == clone })
    }

    @Test
    fun moveItemsMergesThenRemovesSourceRows() = runBlocking {
        val source = createProject("Source")
        val target = createProject("Target")
        val sourceItemId = database.expiryItemDao().insert(
            item(source, barcode = "111", itemCode = "POS-1", quantity = 2.0)
        )
        database.expiryItemDao().insert(
            item(target, barcode = "222", itemCode = "POS-1", quantity = 3.0)
        )

        val merged = projectRepository.moveItemsToProject(listOf(sourceItemId), target, MergeMode.ADD)

        assertEquals(1, merged)
        assertNull(database.expiryItemDao().getItemById(sourceItemId))
        val targetItems = database.expiryItemDao().getAllItemsOnce(target)
        assertEquals(1, targetItems.size)
        assertEquals(5.0, targetItems.single().quantity, 0.0)
    }

    private suspend fun createProject(name: String): Long =
        database.projectDao().insert(
            ProjectEntity(name = name, colorHex = "#26C6DA", createdAt = System.currentTimeMillis())
        )

    private fun item(
        projectId: Long,
        barcode: String,
        itemCode: String? = null,
        quantity: Double = 1.0
    ): ExpiryItemEntity = ExpiryItemEntity(
        barcode = barcode,
        itemCode = itemCode,
        expiryDate = "2030-01-01",
        quantity = quantity,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        projectId = projectId
    )
}
