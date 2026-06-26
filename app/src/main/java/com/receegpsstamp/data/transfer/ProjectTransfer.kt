package com.receegpsstamp.data.transfer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.receegpsstamp.data.local.LocalStore
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.Shop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Self-contained snapshot of one project — data + relative photo paths — written into the .rgsproj zip. */
data class ProjectBundle(
    val company: Company? = null,
    val distributor: Distributor = Distributor(),
    val shops: List<Shop> = emptyList(),
    val recces: List<RecceEntry> = emptyList(),
)

/**
 * Exports / imports a single project as a portable `.rgsproj` file (a ZIP of `project.json` + `photos/`).
 * Fully offline — share the file by any channel; importing on another device recreates the whole
 * project (shops, recces, media and photos) with fresh local ids so it never clashes with existing data.
 */
@Singleton
class ProjectTransfer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localStore: LocalStore,
) {
    private val gson = Gson()
    private val outDir by lazy { File(context.cacheDir, "exports").also { it.mkdirs() } }
    private val photosDir by lazy { File(context.filesDir, "imported").also { it.mkdirs() } }

    /** Builds a `.rgsproj` for [distributorId]; returns a shareable Uri (null if the project is gone). */
    fun exportProject(distributorId: String): Uri? {
        val db = localStore.db.value
        val dist = db.distributors.find { it.id == distributorId } ?: return null
        val company = db.companies.find { it.id == dist.companyId }
        val shops = db.shops.filter { it.distributorId == distributorId }
        val recces = db.recces.filter { it.distributorId == distributorId }

        // Map each real photo file → a unique entry name inside the zip.
        val photos = (recces.flatMap { it.shopPhotos } + recces.flatMap { r -> r.media.flatMap { it.photos } })
            .filter { it.isNotBlank() && File(it).exists() }.distinct()
        val entryOf = HashMap<String, String>()
        photos.forEachIndexed { i, p -> entryOf[p] = "photos/${i}_${File(p).name}" }
        fun rel(paths: List<String>) = paths.mapNotNull { entryOf[it] }

        val bundle = ProjectBundle(
            company = company,
            distributor = dist,
            shops = shops,
            recces = recces.map { r ->
                r.copy(shopPhotos = rel(r.shopPhotos), media = r.media.map { m -> m.copy(photos = rel(m.photos)) })
            },
        )

        val file = File(outDir, "${safe(dist.name)}_${System.currentTimeMillis()}.rgsproj")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(gson.toJson(bundle).toByteArray())
            zip.closeEntry()
            photos.forEach { p ->
                zip.putNextEntry(ZipEntry(entryOf[p]))
                File(p).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Reads a `.rgsproj` from [uri], extracts its photos and merges the project in. Returns its name. */
    fun importProject(uri: Uri): String? {
        var json: String? = null
        val extracted = HashMap<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "project.json") {
                        json = zip.readBytes().decodeToString()
                    } else if (name.startsWith("photos/")) {
                        val out = File(photosDir, "${System.nanoTime()}_${File(name).name}")
                        out.outputStream().use { zip.copyTo(it) }
                        extracted[name] = out.absolutePath
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        val bundle = json?.let { runCatching { gson.fromJson(it, ProjectBundle::class.java) }.getOrNull() } ?: return null
        if (bundle.distributor.name.isBlank()) return null
        fun abs(rel: List<String>) = rel.mapNotNull { extracted[it] }
        val recces = bundle.recces.map { r ->
            r.copy(shopPhotos = abs(r.shopPhotos), media = r.media.map { m -> m.copy(photos = abs(m.photos)) })
        }
        localStore.importProject(bundle.company, bundle.distributor, bundle.shops, recces)
        return bundle.distributor.name
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Project" }
}
