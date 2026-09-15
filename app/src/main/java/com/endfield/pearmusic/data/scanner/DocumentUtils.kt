package com.endfield.pearmusic.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Fast, low-bandwidth utilities for traversing Storage Access Framework (SAF) document trees.
 * Uses direct DocumentsContract queries to avoid DocumentFile Binder IPC overhead and
 * prevent downloading remote cloud media payloads during directory enumeration.
 */
object DocumentUtils {

    data class DocumentInfo(
        val uri: Uri,
        val id: String,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    /**
     * Lists all children of a directory document within a tree in a single database query.
     * Prevents downloading media file bodies over network/cloud providers like Google Drive.
     */
    fun listChildren(context: Context, treeUri: Uri, parentDocumentUri: Uri): List<DocumentInfo> {
        val children = mutableListOf<DocumentInfo>()
        val parentDocId = try {
            DocumentsContract.getDocumentId(parentDocumentUri)
        } catch (e: Exception) {
            Log.e("DocumentUtils", "Failed to get document ID for $parentDocumentUri", e)
            return emptyList()
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        try {
            // Queries content provider database rows only without opening file streams
            context.contentResolver.query(
                childrenUri,
                PROJECTION,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idIdx != -1) cursor.getString(idIdx) else null ?: continue
                    val mimeType = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""
                    val isDir = DocumentsContract.Document.MIME_TYPE_DIR == mimeType

                    children.add(
                        DocumentInfo(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            id = docId,
                            displayName = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Unknown" else "Unknown",
                            mimeType = mimeType,
                            size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L,
                            lastModified = if (modIdx != -1) cursor.getLong(modIdx) else 0L,
                            isDirectory = isDir
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentUtils", "Error querying children for $parentDocId", e)
        }

        return children
    }

    /**
     * Helper to find a specific file by name in a directory.
     */
    fun findChild(context: Context, treeUri: Uri, parentDocumentUri: Uri, displayName: String): DocumentInfo? {
        return listChildren(context, treeUri, parentDocumentUri).find {
            it.displayName.equals(displayName, ignoreCase = true)
        }
    }

    fun getDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "Unknown"
        try {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: "Unknown"
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentUtils", "Error getting display name for $uri", e)
        }
        return uri.lastPathSegment ?: "Unknown"
    }
}
