package com.nearexpiry.manager.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-authorized Google Drive backup service. Every account receives a visible
 * "Near Expiry Backups" folder and the app is limited to files that it creates
 * through the drive.file OAuth scope.
 */
@Singleton
class GoogleDriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    data class DriveBackupFile(
        val id: String,
        val name: String,
        val createdTime: String,
        val sizeBytes: Long = 0L
    )

    data class DriveStatus(
        val accountEmail: String? = null,
        val backupEnabled: Boolean = false
    )

    /** The UI must launch [pendingIntent] to let the selected user grant Drive access. */
    sealed interface DriveAuthorizationState {
        data object Granted : DriveAuthorizationState
        data class ConsentRequired(val pendingIntent: PendingIntent) : DriveAuthorizationState
    }

    /** Raised from background work only when user interaction is required. */
    class DriveAuthorizationRequiredException : IllegalStateException("Google Drive permission is required")

    companion object {
        const val FOLDER_NAME = "Near Expiry Backups"
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val KEEP_COUNT = 14 // Seven days × noon and midnight.
    }

    private val signInOptions: GoogleSignInOptions
        get() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

    fun signInIntent(): Intent = GoogleSignIn.getClient(context, signInOptions).signInIntent

    fun isConnected(): Boolean = GoogleSignIn.getLastSignedInAccount(context)?.email != null

    suspend fun status(): DriveStatus = DriveStatus(
        accountEmail = GoogleSignIn.getLastSignedInAccount(context)?.email,
        backupEnabled = preferencesManager.isGoogleDriveBackupEnabled()
    )

    /**
     * Authentication identifies the selected account. Drive access is requested
     * separately, only for the optional backup feature, through AuthorizationClient.
     */
    suspend fun handleSignInResult(data: Intent?): String = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)
        val email = account.email ?: throw IllegalStateException("Google account email is unavailable")
        preferencesManager.setGoogleDriveAccountEmail(email)
        preferencesManager.setGoogleDriveBackupEnabled(true)
        email
    }

    /**
     * Requests the narrow Drive scope for the selected account. On a returning
     * account Google returns a short-lived token without UI; a new account gets
     * a PendingIntent that the screen launches to obtain explicit consent.
     */
    suspend fun requestDriveAuthorization(): DriveAuthorizationState = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Connect Google Drive first")
        val androidAccount = account.account
            ?: throw IllegalStateException("Google account is unavailable")
        val result = Tasks.await(
            Identity.getAuthorizationClient(context).authorize(authorizationRequest(androidAccount)),
            30,
            TimeUnit.SECONDS
        )
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: throw IllegalStateException("Google Drive permission could not be requested")
            DriveAuthorizationState.ConsentRequired(pendingIntent)
        } else {
            result.accessToken?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google Drive did not return an access token")
            preferencesManager.setGoogleDriveConsentRequired(false)
            DriveAuthorizationState.Granted
        }
    }

    /** Validates the Activity Result from the Google Drive consent screen. */
    suspend fun handleDriveAuthorizationResult(data: Intent?) = withContext(Dispatchers.IO) {
        if (data == null) throw IllegalStateException("Google Drive permission was not granted")
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive permission was not granted")
        preferencesManager.setGoogleDriveConsentRequired(false)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        // Signing out removes the remembered account for this optional feature;
        // existing user-owned Drive backup files remain untouched.
        Tasks.await(GoogleSignIn.getClient(context, signInOptions).signOut(), 10, TimeUnit.SECONDS)
        preferencesManager.clearGoogleDriveBackupSettings()
    }

    /** Signs out before a new account chooser is launched for a real switch. */
    suspend fun prepareAccountSwitch() = disconnect()

    suspend fun setBackupEnabled(enabled: Boolean) {
        if (enabled && !isConnected()) throw IllegalStateException("Connect Google Drive first")
        preferencesManager.setGoogleDriveBackupEnabled(enabled)
    }

    suspend fun uploadBackup(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val token = accessToken()
        val folderId = getOrCreateFolder(token)
        val boundary = "NearExpiry_${System.currentTimeMillis()}"
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", "application/json")
            put("parents", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive(folderId)) })
        }.toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toByteArray(StandardCharsets.UTF_8))
            write("\r\n--$boundary\r\n".toByteArray())
            write("Content-Type: application/json\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()

        request(
            url = "$DRIVE_UPLOAD_API/files?uploadType=multipart&fields=id,name,createdTime",
            method = "POST",
            token = token,
            contentType = "multipart/related; boundary=$boundary",
            body = body
        )
        pruneOldBackups(token, folderId)
    }

    suspend fun listBackups(): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val token = accessToken()
        val folderId = getOrCreateFolder(token)
        listBackups(token, folderId)
    }

    suspend fun downloadBackup(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val token = accessToken()
        request(
            url = "$DRIVE_API/files/$fileId?alt=media",
            method = "GET",
            token = token
        )
    }

    private fun authorizationRequest(account: android.accounts.Account): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setAccount(account)
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

    private fun accessToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Connect Google Drive first")
        val androidAccount = account.account
            ?: throw IllegalStateException("Google account is unavailable")
        val result = Tasks.await(
            Identity.getAuthorizationClient(context).authorize(authorizationRequest(androidAccount)),
            30,
            TimeUnit.SECONDS
        )
        if (result.hasResolution()) throw DriveAuthorizationRequiredException()
        return result.accessToken?.takeIf { it.isNotBlank() }
            ?: throw DriveAuthorizationRequiredException()
    }

    private fun getOrCreateFolder(token: String): String {
        val escapedName = FOLDER_NAME.replace("'", "\\'")
        val query = "mimeType='application/vnd.google-apps.folder' and name='$escapedName' and trashed=false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val response = request(
            url = "$DRIVE_API/files?q=$encodedQuery&spaces=drive&fields=files(id,name)&pageSize=10",
            method = "GET",
            token = token
        )
        val files = json.parseToJsonElement(response.toString(StandardCharsets.UTF_8))
            .jsonObject["files"]?.jsonArray.orEmpty()
        files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.let { return it }

        val body = buildJsonObject {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toByteArray(StandardCharsets.UTF_8)
        val created = request(
            url = "$DRIVE_API/files?fields=id,name",
            method = "POST",
            token = token,
            contentType = "application/json; charset=UTF-8",
            body = body
        )
        return json.parseToJsonElement(created.toString(StandardCharsets.UTF_8))
            .jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Google Drive did not return a backup folder ID")
    }

    private fun listBackups(token: String, folderId: String): List<DriveBackupFile> {
        val query = "'$folderId' in parents and trashed=false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val response = request(
            url = "$DRIVE_API/files?q=$encodedQuery&fields=files(id,name,createdTime,size)&orderBy=createdTime%20desc&pageSize=100",
            method = "GET",
            token = token
        )
        return json.parseToJsonElement(response.toString(StandardCharsets.UTF_8))
            .jsonObject["files"]?.jsonArray.orEmpty()
            .mapNotNull { entry ->
                val item = entry.jsonObject
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                DriveBackupFile(
                    id = id,
                    name = name,
                    createdTime = item["createdTime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    sizeBytes = item["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                )
            }
    }

    private fun pruneOldBackups(token: String, folderId: String) {
        listBackups(token, folderId).drop(KEEP_COUNT).forEach { backup ->
            request(
                url = "$DRIVE_API/files/${backup.id}",
                method = "DELETE",
                token = token
            )
        }
    }

    private fun request(
        url: String,
        method: String,
        token: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() }
                ?: ByteArray(0)
            if (code !in 200..299) {
                val detail = response.toString(StandardCharsets.UTF_8).take(300)
                throw IllegalStateException("Google Drive request failed ($code): $detail")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
}
