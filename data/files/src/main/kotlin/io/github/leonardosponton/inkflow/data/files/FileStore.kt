package io.github.leonardosponton.inkflow.data.files

import java.io.File

/**
 * App-private file layout (`filesDir/`):
 * - `thumbnails/<projectId>.png`
 * - `assets/<projectId>/<assetId>` (images placed on canvases)
 */
class FileStore(private val root: File) {
    private val thumbnails = File(root, "thumbnails")
    private val assets = File(root, "assets")

    fun thumbnailFile(projectId: String) = File(thumbnails, "$projectId.png")

    fun writeThumbnail(projectId: String, png: ByteArray) = writeAtomically(thumbnailFile(projectId), png)

    fun assetDir(projectId: String) = File(assets, projectId)

    fun assetFile(projectId: String, assetId: String) = File(assetDir(projectId), assetId)

    fun writeAsset(projectId: String, assetId: String, bytes: ByteArray): File =
        assetFile(projectId, assetId).also { writeAtomically(it, bytes) }

    fun deleteProjectFiles(projectId: String) {
        thumbnailFile(projectId).delete()
        assetDir(projectId).deleteRecursively()
    }

    /** Write to a temp file and rename, so a crash never leaves a half-written file. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}
