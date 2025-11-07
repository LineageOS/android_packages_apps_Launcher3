/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.net.Uri.Builder
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.launcher3.ConstantItem
import com.android.launcher3.DefaultLayoutParser
import com.android.launcher3.EncryptionType.ENCRYPTED
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.Utilities
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.GridMigrationOption.Companion.from
import com.android.launcher3.model.GridSizeMigrationDBController.needsToMigrate
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import com.android.launcher3.provider.LauncherDbUtils.queryIntArray
import com.android.launcher3.provider.LauncherDbUtils.tableExists
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.IntArray
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.wm.shell.Flags
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Provider

/**
 * Utility class which maintains an instance of Launcher database and provides utility methods
 * around it.
 */
@LauncherAppSingleton
class ModelDbController
@Inject
internal constructor(
    @ApplicationContext private val context: Context,
    private val idp: InvariantDeviceProfile,
    private val prefs: LauncherPrefs,
    private val userCache: UserCache,
    private val layoutParserFactory: LayoutParserFactory,
    private val migrationLogicFactory: Provider<GridSizeMigrationLogic>,
) {
    private var openHelper: DatabaseHelper? = null

    @Synchronized
    private fun createDbIfNotExists() {
        if (openHelper == null) {
            // Initialize the restore task before opening the DB
            val restoreTask = RestoreDbTask.createRestoreTask(context)
            val dbFile = prefs.get(LauncherPrefs.DB_FILE).ifEmpty { idp.dbFile }
            openHelper = createDatabaseHelper(false, /* forMigration */ dbFile)
            restoreTask.accept(this)
        }
    }

    protected fun createDatabaseHelper(forMigration: Boolean, dbFile: String): DatabaseHelper {
        // Set the flag for empty DB
        val onEmptyDbCreateCallback =
            if (forMigration) Runnable {}
            else Runnable { prefs.putSync(getEmptyDbCreatedKey(dbFile).to(true)) }

        val databaseHelper = DatabaseHelper(context, dbFile, onEmptyDbCreateCallback)
        // Table creation sometimes fails silently, which leads to a crash loop.
        // This way, we will try to create a table every time after crash, so the device
        // would eventually be able to recover.
        if (!tableExists(databaseHelper.readableDatabase, Favorites.TABLE_NAME)) {
            Log.e(TAG, "Tables are missing after onCreate has been called. Trying to recreate")
            // This operation is a no-op if the table already exists.
            Favorites.addTableToDb(
                databaseHelper.writableDatabase,
                getSerialNumberForUser(Process.myUserHandle()),
                true, /* optional */
            )
        }
        databaseHelper.mHotseatRestoreTableExists =
            tableExists(databaseHelper.readableDatabase, Favorites.HYBRID_HOTSEAT_BACKUP_TABLE)

        databaseHelper.initIds()
        return databaseHelper
    }

    /** Refer [SQLiteDatabase.query] */
    @WorkerThread
    fun query(
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?,
    ): Cursor {
        createDbIfNotExists()
        return openHelper!!
            .writableDatabase
            .query(
                Favorites.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder,
            )
            .apply {
                extras = Bundle().apply { putString(EXTRA_DB_NAME, openHelper!!.databaseName) }
            }
    }

    /** Refer [SQLiteDatabase.insert] */
    @WorkerThread
    fun insert(initialValues: ContentValues?): Int {
        createDbIfNotExists()

        val db = openHelper!!.writableDatabase
        val rowId = openHelper!!.dbInsertAndCheck(db, Favorites.TABLE_NAME, initialValues)
        if (rowId >= 0) {
            onAddOrDeleteOp(db)
        }
        return rowId
    }

    /** Refer [SQLiteDatabase.delete] */
    @WorkerThread
    fun delete(selection: String?, selectionArgs: Array<String?>?): Int {
        createDbIfNotExists()
        val db = openHelper!!.writableDatabase

        val count = db.delete(Favorites.TABLE_NAME, selection, selectionArgs)
        if (count > 0) {
            onAddOrDeleteOp(db)
        }
        return count
    }

    /** Refer [SQLiteDatabase.update] */
    @WorkerThread
    fun update(values: ContentValues?, selection: String?, selectionArgs: Array<String?>?): Int {
        createDbIfNotExists()
        return openHelper!!.writableDatabase.update(TABLE_NAME, values, selection, selectionArgs)
    }

    /** Clears a previously set flag corresponding to empty db creation */
    @WorkerThread
    fun clearEmptyDbFlag() {
        createDbIfNotExists()
        clearFlagEmptyDbCreated()
    }

    /** Generates an id to be used for new item in the favorites table */
    @WorkerThread
    fun generateNewItemId(): Int {
        createDbIfNotExists()
        return openHelper!!.generateNewItemId()
    }

    /** Generates an id to be used for new workspace screen */
    @get:WorkerThread
    val newScreenId: Int
        get() {
            createDbIfNotExists()
            return openHelper!!.newScreenId
        }

    /** Creates an empty DB clearing all existing data */
    @WorkerThread
    fun createEmptyDB() {
        createDbIfNotExists()
        openHelper!!.createEmptyDB(openHelper!!.writableDatabase)
        prefs.putSync(emptyDbCreatedKey.to(true))
    }

    /** Removes any widget which are present in the framework, but not in out internal DB */
    @WorkerThread
    fun removeGhostWidgets() {
        createDbIfNotExists()
        openHelper!!.removeGhostWidgets(openHelper!!.writableDatabase)
    }

    /** Returns a new [SQLiteTransaction] */
    @WorkerThread
    fun newTransaction(): SQLiteTransaction {
        createDbIfNotExists()
        return SQLiteTransaction(openHelper!!.writableDatabase)
    }

    /** Refreshes the internal state corresponding to presence of hotseat table */
    @WorkerThread
    fun refreshHotseatRestoreTable() {
        createDbIfNotExists()
        openHelper!!.mHotseatRestoreTableExists =
            tableExists(openHelper!!.readableDatabase, Favorites.HYBRID_HOTSEAT_BACKUP_TABLE)
    }

    /** Resets the launcher DB if we should reset it. */
    private fun resetLauncherDb(restoreEventLogger: LauncherRestoreEventLogger?) {
        if (restoreEventLogger != null) {
            sendMetricsForFailedMigration(restoreEventLogger, db)
        }
        FileLog.d(TAG, "resetLauncherDb: Migration failed: resetting launcher database")
        createEmptyDB()
        prefs.putSync(getEmptyDbCreatedKey(openHelper!!.databaseName).to(true))

        // Write the grid state to avoid another migration
        DeviceGridState(idp).writeToPrefs(context)
    }

    /** Determines if we should reset the DB. */
    private fun shouldResetDb(): Boolean {
        // If we already have a new DB, ignore migration
        if (prefs.get(emptyDbCreatedKey)) {
            FileLog.d(TAG, "isThereExistingDb: new DB already created, skipping migration")
            return true
        }
        if (!needsToMigrate(context, idp)) {
            FileLog.d(TAG, "isGridMigrationNecessary: no grid migration needed")
            return false
        }
        val targetDbName = DeviceGridState(idp).dbFile
        val currentDbName = openHelper!!.databaseName
        if (TextUtils.equals(targetDbName, currentDbName)) {
            FileLog.e(
                TAG,
                "isCurrentDbSameAsTarget: target db is same as current current db: $currentDbName target db: $targetDbName",
            )
            return true
        }
        return false
    }

    /** Migrates the DB. If the migration failed, it clears the DB. */
    @Throws(Exception::class)
    fun attemptMigrateDb(
        restoreEventLogger: LauncherRestoreEventLogger?,
        modelDelegate: ModelDelegate,
    ) {
        createDbIfNotExists()
        if (shouldResetDb()) {
            resetLauncherDb(restoreEventLogger)
            return
        }

        val oldHelper = openHelper!!

        // We save the existing db's before creating the destination db helper so we know what logic
        // to run in grid migration based on if that grid already existed before migration or not.
        val existingDBs =
            LauncherFiles.GRID_DB_FILES.stream()
                .filter { dbName: String? -> context.getDatabasePath(dbName).exists() }
                .collect(Collectors.toList())

        try {
            // This is the current grid we have, given by the mContext
            val srcDeviceState = DeviceGridState(context)
            // This is the state we want to migrate to that is given by the idp
            val destDeviceState = DeviceGridState(idp)

            val isDestNewDb = !existingDBs.contains(destDeviceState.dbFile)

            val isAfterRestore = get(context).get(LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE)
            val gridSizeMigrationLogic = migrationLogicFactory.get()

            // Check if the migration path from source to destination is valid before migrating.
            val sourceGridMigrationOption = from(srcDeviceState.columns, srcDeviceState.rows)
            val destinationGridMigrationOption = from(destDeviceState.columns, destDeviceState.rows)
            if (
                sourceGridMigrationOption != null &&
                    destinationGridMigrationOption != null &&
                    sourceGridMigrationOption.canMigrate(
                        destinationGridMigrationOption,
                        isAfterRestore,
                    )
            ) {
                openHelper = createDatabaseHelper(true, DeviceGridState(idp).dbFile)
                gridSizeMigrationLogic.migrateGrid(
                    srcDeviceState,
                    destDeviceState,
                    openHelper!!,
                    oldHelper.writableDatabase,
                    isDestNewDb,
                    modelDelegate,
                )
            } else {
                FileLog.e(
                    TAG,
                    ("Cannot migrate from source: " +
                        srcDeviceState +
                        " to destination: " +
                        destDeviceState),
                )
            }
        } catch (e: Exception) {
            resetLauncherDb(restoreEventLogger)
            throw Exception("attemptMigrateDb: Failed to migrate grid", e)
        } finally {
            if (openHelper !== oldHelper) {
                oldHelper.close()
            }
        }
    }

    /**
     * In case of migration failure, report metrics for the count of each itemType in the DB.
     *
     * @param restoreEventLogger logger used to report Launcher restore metrics
     */
    private fun sendMetricsForFailedMigration(
        restoreEventLogger: LauncherRestoreEventLogger,
        db: SQLiteDatabase,
    ) {
        try {
            db.rawQuery("SELECT itemType, COUNT(*) AS count FROM favorites GROUP BY itemType", null)
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        do {
                            restoreEventLogger.logFavoritesItemsRestoreFailed(
                                cursor.getInt(cursor.getColumnIndexOrThrow(Favorites.ITEM_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow("count")),
                                RestoreError.GRID_MIGRATION_FAILURE,
                            )
                        } while (cursor.moveToNext())
                    }
                }
        } catch (e: Exception) {
            FileLog.e(TAG, "sendMetricsForFailedDb: Error reading from database", e)
        }
    }

    /** Returns the underlying model database */
    val db: SQLiteDatabase
        get() {
            createDbIfNotExists()
            return openHelper!!.writableDatabase
        }

    private fun onAddOrDeleteOp(db: SQLiteDatabase) {
        openHelper!!.onAddOrDeleteOp(db)
    }

    private fun deleteItemsBasedOnItemIdQuery(selection: String): IntArray? {
        createDbIfNotExists()
        val db = openHelper!!.writableDatabase
        try {
            SQLiteTransaction(db).use { t ->
                val itemIds = queryIntArray(false, db, TABLE_NAME, _ID, selection, null, null)
                if (!itemIds.isEmpty) {
                    db.delete(TABLE_NAME, Utilities.createDbSelectionQuery(_ID, itemIds), null)
                }
                t.commit()
                return itemIds
            }
        } catch (ex: SQLException) {
            Log.e(TAG, ex.message, ex)
            return null
        }
    }

    /**
     * Deletes any empty folder from the DB.
     *
     * @return Ids of deleted folders.
     */
    @WorkerThread
    fun deleteEmptyFolders(): IntArray? =
        deleteItemsBasedOnItemIdQuery(
            "$ITEM_TYPE = $ITEM_TYPE_FOLDER AND $_ID NOT IN (SELECT $CONTAINER FROM $TABLE_NAME)"
        )

    /**
     * Deletes any app group that contains an illegal number of member apps.
     *
     * @return Ids of deleted app groups.
     */
    @WorkerThread
    fun deleteBadAppPairs(): IntArray? =
        deleteItemsBasedOnItemIdQuery(
            if (Flags.enable2x1Split()) {
                "$ITEM_TYPE = $ITEM_TYPE_APP_GROUP AND $_ID NOT IN (SELECT $CONTAINER FROM $TABLE_NAME GROUP BY $CONTAINER HAVING COUNT BETWEEN ${AppPairInfo.MIN_ITEMS} AND ${AppPairInfo.MAX_ITEMS})"
            } else {
                "$ITEM_TYPE = $ITEM_TYPE_APP_GROUP AND $_ID NOT IN (SELECT $CONTAINER FROM $TABLE_NAME GROUP BY $CONTAINER HAVING COUNT(*) = 2)"
            }
        )

    /**
     * Deletes any app with a container id that doesn't exist.
     *
     * @return Ids of deleted apps.
     */
    @WorkerThread
    fun deleteUnparentedApps(): IntArray? =
        deleteItemsBasedOnItemIdQuery(
            "$CONTAINER >= 0 AND $CONTAINER NOT IN (SELECT $_ID FROM $TABLE_NAME )"
        )

    private fun clearFlagEmptyDbCreated() {
        prefs.removeSync(emptyDbCreatedKey)
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     * 1) From the app restrictions
     * 2) From a package provided by play store
     * 3) From a partner configuration APK, already in the system image
     * 4) The default configuration for the particular device
     *
     * Returns true if default favorites was loaded, false if a valid data already exists
     */
    @WorkerThread
    @Synchronized
    fun loadDefaultFavoritesIfNecessary(): Boolean {
        createDbIfNotExists()

        if (prefs.get(emptyDbCreatedKey)) {
            Log.d(TAG, "loading default workspace")

            val widgetHolder = openHelper!!.newLauncherWidgetHolder()
            try {
                var loader =
                    layoutParserFactory.createExternalLayoutParser(widgetHolder, openHelper!!)

                val usingExternallyProvidedLayout = loader != null
                if (loader == null) {
                    loader = getDefaultLayoutParser(widgetHolder)
                }

                // There might be some partially restored DB items, due to buggy restore logic in
                // previous versions of launcher.
                openHelper!!.createEmptyDB(openHelper!!.writableDatabase)
                // Populate favorites table with initial favorites
                if (
                    (openHelper!!.loadFavorites(openHelper!!.writableDatabase, loader) <= 0) &&
                        usingExternallyProvidedLayout
                ) {
                    // Unable to load external layout. Cleanup and load the internal layout.
                    openHelper!!.createEmptyDB(openHelper!!.writableDatabase)
                    openHelper!!.loadFavorites(
                        openHelper!!.writableDatabase,
                        getDefaultLayoutParser(widgetHolder),
                    )
                }
                clearFlagEmptyDbCreated()
                return true
            } finally {
                widgetHolder.destroy()
            }
        }
        return false
    }

    private fun getDefaultLayoutParser(widgetHolder: LauncherWidgetHolder): DefaultLayoutParser {
        return DefaultLayoutParser(
            context,
            widgetHolder,
            openHelper,
            context.resources,
            idp.defaultLayoutId,
        )
    }

    private val emptyDbCreatedKey: ConstantItem<Boolean>
        get() = getEmptyDbCreatedKey(openHelper!!.databaseName)

    /**
     * Re-composite given key in respect to database. If the current db is
     * [LauncherFiles.LAUNCHER_DB], return the key as-is. Otherwise append the db name to given key.
     * e.g. consider key="EMPTY_DATABASE_CREATED", dbName="minimal.db", the returning string will be
     * "EMPTY_DATABASE_CREATED@minimal.db".
     */
    private fun getEmptyDbCreatedKey(dbName: String): ConstantItem<Boolean> {
        val key =
            if (TextUtils.equals(dbName, LauncherFiles.LAUNCHER_DB)) EMPTY_DATABASE_CREATED
            else "$EMPTY_DATABASE_CREATED@$dbName"
        return backedUpItem(key, false, /* default value */ ENCRYPTED)
    }

    /** Returns the serial number for the provided user */
    fun getSerialNumberForUser(user: UserHandle): Long {
        return userCache.getSerialNumberForUser(user)
    }

    companion object {
        private const val TAG = "ModelDbController"

        private const val EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED"
        const val EXTRA_DB_NAME: String = "db_name"

        fun getLayoutUri(authority: String?, ctx: Context): Uri {
            val grid = getIDP(ctx)
            return Builder()
                .scheme("content")
                .authority(authority)
                .path("launcher_layout")
                .appendQueryParameter("version", "1")
                .appendQueryParameter("gridWidth", grid.numColumns.toString())
                .appendQueryParameter("gridHeight", grid.numRows.toString())
                .appendQueryParameter("hotseatSize", grid.numDatabaseHotseatIcons.toString())
                .build()
        }
    }
}
