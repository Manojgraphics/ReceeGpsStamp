package com.receegpsstamp.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.location.Geocoder
import android.net.Uri
import androidx.core.content.FileProvider
import com.receegpsstamp.data.image.ImageCompressor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.MediaItem
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.data.util.MediaMath
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Professional offline exports for the current distributor's recce data:
 *  - PDF : a report with each shop's code, details and embedded GPS-stamped photos
 *  - CSV : structured data that opens directly in Excel
 */
@Singleton
class ReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressor: ImageCompressor,
    private val settingsStore: com.receegpsstamp.data.local.SettingsStore,
) {
    private val dir by lazy { File(context.cacheDir, "exports").also { it.mkdirs() } }
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    /** Loads a bundled Ubuntu TTF from a temp file so PdfBox subsets it (keeps the embedded font tiny). */
    private fun loadFont(doc: PDDocument, asset: String): PDType0Font {
        val tmp = File(context.cacheDir, asset.substringAfterLast('/'))
        if (!tmp.exists()) context.assets.open(asset).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return PDType0Font.load(doc, tmp)
    }

    private fun share(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Address for a recce: the value stored at recce time, else a best-effort reverse-geocode of its
     * coordinates (so older recces saved before addresses were stored still resolve). Cached per point;
     * blank when offline or coordinates are unknown.
     */
    @Suppress("DEPRECATION")
    private fun addressOf(r: RecceEntry, cache: MutableMap<String, String>): String {
        if (r.address.isNotBlank()) return r.address
        if (r.lat == 0.0 && r.lng == 0.0) return ""
        val key = "%.5f,%.5f".format(r.lat, r.lng)
        cache[key]?.let { return it }
        val a = try {
            geocoder.getFromLocation(r.lat, r.lng, 1)?.firstOrNull()?.getAddressLine(0).orEmpty()
        } catch (_: Exception) { "" }
        cache[key] = a
        return a
    }

    private fun rows(shops: List<Shop>, recces: List<RecceEntry>): List<Pair<Shop?, RecceEntry>> {
        val byId = shops.associateBy { it.id }
        return recces.sortedBy { byId[it.shopId]?.code ?: "" }.map { byId[it.shopId] to it }
    }

    // ───────────────────────── CSV (Excel) ─────────────────────────

    fun exportCsv(company: String, distributor: String, city: String, serial: Int, shops: List<Shop>, recces: List<RecceEntry>): Uri {
        val file = File(dir, "${reportName(company, distributor, city, serial)}.csv")
        file.bufferedWriter().use { w ->
            w.appendLine(
                "Shop ID,Shop Name,City,Address,Contact,Status,Media,Creative," +
                    "Width (in),Height (in),Qty,Sqft,Rate,Amount,Remark,Company Remark,Latitude,Longitude,Date,Map Link",
            )
            var row = 1   // header = row 1; data rows follow — drives the Amount = Sqft × Rate formula.
            val addrCache = HashMap<String, String>()
            rows(shops, recces).forEach { (shop, r) ->
                fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
                val mapLink = if (r.lat != 0.0 || r.lng != 0.0)
                    "https://maps.google.com/?q=${"%.6f".format(r.lat)},${"%.6f".format(r.lng)}" else ""
                // One row per media line-item so a shop with multiple media exports every one.
                fun line(m: MediaItem?): String {
                    row++
                    val sqft = if (m != null) MediaMath.areaSqFt(m.width.toFloat(), m.height.toFloat(), m.qty, m.unit) else 0f
                    return listOf(
                        q(shop?.code ?: ""), q(shop?.name ?: ""), q(shop?.city ?: ""), q(addressOf(r, addrCache)), q(shop?.contact ?: ""),
                        q(r.status),
                        q(m?.type ?: ""), q(m?.creative ?: ""),
                        if (m != null && m.width != 0.0) num(toInches(m.width, m.unit)) else "",   // Width (in)
                        if (m != null && m.height != 0.0) num(toInches(m.height, m.unit)) else "", // Height (in)
                        if (m != null) m.qty.toString() else "",
                        if (sqft > 0f) MediaMath.formatArea(sqft) else "",   // Sqft (total area incl. qty)
                        "",                                                  // Rate per sqft — fill in Excel
                        if (sqft > 0f) "=L$row*M$row" else "",               // Amount = Sqft × Rate
                        q(r.remark),
                        "",                                                  // Company Remark — fill in Excel
                        if (r.lat != 0.0) "%.6f".format(r.lat) else "",
                        if (r.lng != 0.0) "%.6f".format(r.lng) else "",
                        q(if (r.createdAt > 0) dateFmt.format(Date(r.createdAt)) else ""),
                        q(mapLink),
                    ).joinToString(",")
                }
                if (r.media.isEmpty()) w.appendLine(line(null))
                else r.media.forEach { w.appendLine(line(it)) }
            }
        }
        return share(file)
    }

    // ───────────────────────── Expenses (Expense Manager) ─────────────────────────

    private val expFmt = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private fun expFileName(scope: String): String =
        ("Expenses_" + scope.ifBlank { "All" } + "_" + SimpleDateFormat("ddMMyy", Locale.getDefault()).format(Date()))
            .replace("[^A-Za-z0-9_]".toRegex(), "_")

    fun exportExpensesCsv(scope: String, surveyor: String, expenses: List<Expense>): Uri {
        val file = File(dir, expFileName(scope) + ".csv")
        file.bufferedWriter().use { w ->
            w.appendLine("Date,Type,Category,Project,Note,Payment,Amount")
            fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
            expenses.sortedBy { it.date }.forEach { e ->
                w.appendLine(
                    listOf(
                        q(expFmt.format(Date(e.date))),
                        q(if (e.kind == "ADVANCE") "Advance" else "Expense"),
                        q(e.category), q(e.projectName.ifBlank { "General" }), q(e.note), q(e.paymentMode),
                        "%.0f".format(e.amount),
                    ).joinToString(","),
                )
            }
            val advance = expenses.filter { it.kind == "ADVANCE" }.sumOf { it.amount }
            val spentYou = expenses.filter { it.kind == "EXPENSE" && it.paymentMode != "Company" }.sumOf { it.amount }
            val companyPaid = expenses.filter { it.kind == "EXPENSE" && it.paymentMode == "Company" }.sumOf { it.amount }
            w.appendLine()
            w.appendLine(",,,,,Advance,${"%.0f".format(advance)}")
            w.appendLine(",,,,,Spent by you,${"%.0f".format(spentYou)}")
            w.appendLine(",,,,,Paid by company,${"%.0f".format(companyPaid)}")
            w.appendLine(",,,,,Balance,${"%.0f".format(advance - spentYou)}")
        }
        return share(file)
    }

    fun exportExpensesPdf(scope: String, surveyor: String, expenses: List<Expense>): Uri {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 36f
        fun paint(size: Float, bold: Boolean = false, col: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
            Paint().apply { textSize = size; isFakeBoldText = bold; color = col; textAlign = align; isAntiAlias = true }
        val pTitle = paint(18f, true)
        val pHead = paint(10f, false, Color.DKGRAY)
        val pCol = paint(9f, true, Color.DKGRAY)
        val pColR = paint(9f, true, Color.DKGRAY, Paint.Align.RIGHT)
        val pCell = paint(9f)
        val pCellR = paint(9f, align = Paint.Align.RIGHT)
        val pBold = paint(10f, true)
        val pBoldR = paint(10f, true, align = Paint.Align.RIGHT)
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        val xDate = margin; val xCat = margin + 80; val xProj = margin + 190; val xNote = margin + 290
        val xAmtR = pageW - margin; val rowH = 16f; val bottom = pageH - margin - 90

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var c = page.canvas
        var y = margin + 18f

        c.drawText("Expense Report", margin, y, pTitle); y += 22f
        c.drawText("Surveyor: ${surveyor.ifBlank { "—" }}     Scope: ${scope.ifBlank { "All" }}", margin, y, pHead); y += 14f
        c.drawText("Generated: ${dateFmt.format(Date())}", margin, y, pHead); y += 18f

        fun colHeader() {
            c.drawText("Date", xDate, y, pCol); c.drawText("Category", xCat, y, pCol)
            c.drawText("Project", xProj, y, pCol); c.drawText("Note", xNote, y, pCol)
            c.drawText("Amount", xAmtR, y, pColR)
            y += 5f; c.drawLine(margin, y, xAmtR, y, line); y += 12f
        }
        colHeader()

        fun ensureSpace() {
            if (y > bottom) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                c = page.canvas; y = margin + 18f; colHeader()
            }
        }
        fun trunc(s: String, max: Int) = if (s.length > max) s.take(max - 1) + "…" else s

        expenses.sortedByDescending { it.date }.forEach { e ->
            ensureSpace()
            c.drawText(expFmt.format(Date(e.date)), xDate, y, pCell)
            c.drawText(trunc(e.category, 16), xCat, y, pCell)
            c.drawText(trunc(e.projectName.ifBlank { "General" }, 14), xProj, y, pCell)
            c.drawText(trunc(e.note, 26), xNote, y, pCell)
            c.drawText((if (e.kind == "ADVANCE") "+ " else "- ") + "₹" + "%,.0f".format(e.amount), xAmtR, y, pCellR)
            y += rowH
        }

        val advance = expenses.filter { it.kind == "ADVANCE" }.sumOf { it.amount }
        val spentYou = expenses.filter { it.kind == "EXPENSE" && it.paymentMode != "Company" }.sumOf { it.amount }
        val companyPaid = expenses.filter { it.kind == "EXPENSE" && it.paymentMode == "Company" }.sumOf { it.amount }
        val balance = advance - spentYou
        y += 6f; ensureSpace(); c.drawLine(margin, y, xAmtR, y, line); y += 16f
        fun total(label: String, value: Double) {
            ensureSpace()
            c.drawText(label, xProj, y, pBold); c.drawText("₹" + "%,.0f".format(value), xAmtR, y, pBoldR); y += 18f
        }
        total("Advance taken", advance)
        total("Spent by you", spentYou)
        total(if (balance >= 0) "Balance to return" else "Overspent (company owes)", kotlin.math.abs(balance))
        if (companyPaid > 0) {
            total("Paid by company", companyPaid)
            total("Total project cost", spentYou + companyPaid)
        }

        y += 8f; ensureSpace(); c.drawText("By category", xDate, y, pBold); y += 16f
        expenses.filter { it.kind == "EXPENSE" }.groupBy { it.category }
            .toList().sortedByDescending { it.second.sumOf { e -> e.amount } }
            .forEach { (cat, items) ->
                ensureSpace()
                c.drawText(cat, xDate, y, pCell)
                c.drawText("₹" + "%,.0f".format(items.sumOf { it.amount }), xAmtR, y, pCellR)
                y += rowH
            }

        doc.finishPage(page)
        val file = File(dir, expFileName(scope) + ".pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return share(file)
    }

    // ───────────────────────── PDF ─────────────────────────

    // Landscape A4 (842 × 595 @72dpi), built with PdfBox so every photo embeds as a real JPEG
    // (DCTDecode) — small files at HD quality. Layout mirrors the manual PPTX recce report:
    //  cover · per shop: a FRONT-photo page, then one page per size (BEFORE/AFTER boxes + size table).
    fun exportPdf(company: String, distributor: String, city: String, serial: Int, shops: List<Shop>, recces: List<RecceEntry>): Uri {
        val pageW = 842f; val pageH = 595f; val margin = 16f
        val contentW = pageW - 2 * margin
        val mmPt = 72f / 25.4f; val padImg = 2f * mmPt; val headGap = 6f * mmPt
        val topMargin = 10f * mmPt   // content starts 10 mm below the top edge on every shop/size page
        // User-configurable report options (Settings → Report).
        val opts = settingsStore.settings.value
        val photoQuality = opts.reportJpegQuality.coerceIn(70, 100) / 100f
        val footerH = if (opts.reportPageNumbers || opts.reportLogoPath.isNotBlank()) 24f else 0f
        val ink = intArrayOf(24, 24, 27); val accent = intArrayOf(255, 196, 0); val soft = intArrayOf(120, 120, 128)
        val white = intArrayOf(255, 255, 255); val lblBg = intArrayOf(238, 238, 240); val stripBg = intArrayOf(33, 33, 36)
        val ruleC = intArrayOf(205, 205, 210); val frameC = intArrayOf(170, 170, 176)

        val doc = PDDocument()
        val file = File(dir, "${reportName(company, distributor, city, serial)}.pdf")
        val fontReg = loadFont(doc, "fonts/Ubuntu-Regular.ttf")
        val fontBold = loadFont(doc, "fonts/Ubuntu-Bold.ttf")

        // ── low-level draw helpers (top-left coords, flipped to PDF's bottom-left origin) ──
        fun cy(topY: Float) = pageH - topY
        // Drop glyphs the embedded font can't encode (avoids PdfBox crashes on e.g. Hindi shop names).
        fun safe(font: PDType0Font, raw: String): String {
            val sb = StringBuilder(raw.length)
            raw.forEach { ch -> try { font.getStringWidth(ch.toString()); sb.append(ch) } catch (_: Exception) { sb.append(' ') } }
            return sb.toString()
        }
        fun width(font: PDType0Font, size: Float, s: String) = font.getStringWidth(s) / 1000f * size
        fun ellipsize(font: PDType0Font, size: Float, raw: String, maxW: Float): String {
            val s = safe(font, raw)
            if (s.isEmpty() || width(font, size, s) <= maxW) return s
            var t = s
            while (t.isNotEmpty() && width(font, size, "$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }
        // Draw left-aligned text with its baseline at topY.
        fun text(cs: PDPageContentStream, font: PDType0Font, size: Float, rgb: IntArray, x: Float, topY: Float, raw: String) {
            val s = safe(font, raw); if (s.isEmpty()) return
            cs.beginText(); cs.setFont(font, size); cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
            cs.newLineAtOffset(x, cy(topY)); cs.showText(s); cs.endText()
        }
        // Draw text centred on cx.
        fun textCenter(cs: PDPageContentStream, font: PDType0Font, size: Float, rgb: IntArray, cx: Float, topY: Float, raw: String) {
            val s = safe(font, raw); text(cs, font, size, rgb, cx - width(font, size, s) / 2f, topY, s)
        }
        fun fillRect(cs: PDPageContentStream, rgb: IntArray, x: Float, topY: Float, w: Float, h: Float) {
            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]); cs.addRect(x, cy(topY + h), w, h); cs.fill()
        }
        fun strokeRect(cs: PDPageContentStream, rgb: IntArray, lw: Float, x: Float, topY: Float, w: Float, h: Float) {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]); cs.setLineWidth(lw); cs.addRect(x, cy(topY + h), w, h); cs.stroke()
        }
        fun hline(cs: PDPageContentStream, rgb: IntArray, lw: Float, x1: Float, x2: Float, topY: Float) {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]); cs.setLineWidth(lw); cs.moveTo(x1, cy(topY)); cs.lineTo(x2, cy(topY)); cs.stroke()
        }
        fun vline(cs: PDPageContentStream, rgb: IntArray, lw: Float, x: Float, topY1: Float, topY2: Float) {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]); cs.setLineWidth(lw); cs.moveTo(x, cy(topY1)); cs.lineTo(x, cy(topY2)); cs.stroke()
        }
        // Embed a bitmap as a JPEG (DCTDecode) and draw it; (x, topY) is the top-left corner.
        fun drawPhoto(cs: PDPageContentStream, bmp: Bitmap, x: Float, topY: Float, w: Float, h: Float) {
            cs.drawImage(JPEGFactory.createFromImage(doc, bmp, photoQuality), x, cy(topY + h), w, h)
        }
        // Start a new landscape page, run the drawing block, and close its content stream.
        fun page(block: (PDPageContentStream) -> Unit) {
            val pg = PDPage(PDRectangle(pageW, pageH)); doc.addPage(pg)
            val cs = PDPageContentStream(doc, pg)
            try { block(cs) } finally { cs.close() }
        }

        // ── layout helpers ──
        // Plain centred distributor title with a thin rule under it (matches the manual's slide title).
        fun titleRow(cs: PDPageContentStream, title: String): Float {
            textCenter(cs, fontBold, 19f, ink, pageW / 2f, topMargin + 18f, ellipsize(fontBold, 19f, title, contentW))
            hline(cs, ruleC, 0.8f, margin, pageW - margin, topMargin + 28f)
            return topMargin + 28f
        }
        // The NAME / CONTACT / ADDRESS info table — shaded label column, repeated on every page.
        fun infoTable(cs: PDPageContentStream, shop: Shop?, addr: String, topY: Float): Float {
            val rowH = 22f; val labelW = 96f; val left = margin; val right = pageW - margin
            val rowsData = listOf(
                "NAME" to (shop?.name?.ifBlank { "—" } ?: "—"),
                "CONTACT" to (shop?.contact?.ifBlank { "—" } ?: "—"),
                "ADDRESS" to addr.ifBlank { "—" },
            )
            var y = topY
            rowsData.forEach { (lab, value) ->
                fillRect(cs, lblBg, left, y, labelW, rowH)
                text(cs, fontBold, 9f, ink, left + 8f, y + 15f, lab)
                text(cs, fontReg, 11f, ink, left + labelW + 8f, y + 15f, ellipsize(fontReg, 11f, value, right - (left + labelW) - 16f))
                y += rowH
            }
            strokeRect(cs, ruleC, 0.8f, left, topY, right - left, y - topY)
            vline(cs, ruleC, 0.8f, left + labelW, topY, y)
            var ry = topY + rowH
            repeat(rowsData.size - 1) { hline(cs, ruleC, 0.8f, left, right, ry); ry += rowH }
            return y
        }
        // Pure math (unchanged): centre an image inside a cell, keeping aspect ratio.
        fun fitInto(bw: Int, bh: Int, cell: RectF): RectF {
            val sc = minOf(cell.width() / bw, cell.height() / bh)
            val w = bw * sc; val h = bh * sc
            val l = cell.centerX() - w / 2f; val t = cell.centerY() - h / 2f
            return RectF(l, t, l + w, t + h)
        }
        // Box size that hugs the image with a uniform 2 mm gap (+ an optional label strip on top).
        fun boxSize(path: String?, maxW: Float, maxH: Float, stripH: Float): Pair<Float, Float> {
            val availW = maxW - 2 * padImg
            val availH = maxH - stripH - 2 * padImg
            val (bw, bh) = if (path != null) imageBounds(path) else (0 to 0)
            if (bw <= 0 || bh <= 0) {
                val w = minOf(availW, availH * 4f / 3f)   // no image — fall back to a 4:3 area
                return (w + 2 * padImg) to (stripH + w * 3f / 4f + 2 * padImg)
            }
            val sc = minOf(availW / bw, availH / bh)
            return (bw * sc + 2 * padImg) to (stripH + bh * sc + 2 * padImg)
        }
        // Frame + optional BEFORE/AFTER strip + the image (JPEG-embedded) with a uniform 2 mm gap.
        fun photoCard(cs: PDPageContentStream, box: RectF, label: String?, path: String?) {
            strokeRect(cs, frameC, 0.8f, box.left, box.top, box.width(), box.height())
            var top = box.top
            if (label != null) {
                fillRect(cs, stripBg, box.left, box.top, box.width(), 16f)
                textCenter(cs, fontBold, 10f, white, box.centerX(), box.top + 11.5f, label)
                top = box.top + 16f
            }
            val area = RectF(box.left + padImg, top + padImg, box.right - padImg, box.bottom - padImg)
            if (path == null) { textCenter(cs, fontReg, 12f, soft, area.centerX(), area.centerY() + 4f, "—"); return }
            val bmp = imageCompressor.prepareImageForPdf(File(path), MAX_DIMENSION_PX).getOrNull() ?: return
            val rct = fitInto(bmp.width, bmp.height, area)
            drawPhoto(cs, bmp, rct.left, rct.top, rct.width(), rct.height())
            bmp.recycle()
        }
        fun sizeTable(cs: PDPageContentStream, sr: Int, m: MediaItem, topY: Float) {
            val labels = listOf("SR NO", "ACTIVITY", "CREATIVE", "WIDTH", "HEIGHT", "QTY")
            val weights = listOf(0.7f, 2.4f, 2.2f, 1.1f, 1.1f, 0.8f)
            val tot = weights.sum(); val rowH = 22f; val right = pageW - margin
            fillRect(cs, stripBg, margin, topY, right - margin, rowH)
            var x = margin
            labels.forEachIndexed { i, lab -> val cw = contentW * weights[i] / tot; text(cs, fontBold, 9f, white, x + 6f, topY + 14f, ellipsize(fontBold, 9f, lab, cw - 8f)); x += cw }
            val data = listOf(
                "%02d".format(sr), m.type.ifBlank { "—" }, m.creative.ifBlank { "—" },
                if (m.width != 0.0) num(toInches(m.width, m.unit)) else "—",
                if (m.height != 0.0) num(toInches(m.height, m.unit)) else "—",
                m.qty.toString(),
            )
            val dy = topY + rowH; x = margin
            data.forEachIndexed { i, d -> val cw = contentW * weights[i] / tot; text(cs, fontReg, 10f, ink, x + 6f, dy + 14f, ellipsize(fontReg, 10f, d, cw - 10f)); x += cw }
            strokeRect(cs, ruleC, 0.8f, margin, topY, right - margin, dy + rowH - topY)
            hline(cs, ruleC, 0.8f, margin, right, dy)
            x = margin
            weights.forEach { wgt -> x += contentW * wgt / tot; if (x < right) vline(cs, ruleC, 0.8f, x, topY, dy + rowH) }
            // REMARK — full-width row under the size table (label cell + value).
            val ry = dy + rowH; val lw = 96f
            fillRect(cs, lblBg, margin, ry, lw, rowH)
            text(cs, fontBold, 9f, ink, margin + 6f, ry + 14f, "REMARK")
            text(cs, fontReg, 10f, ink, margin + lw + 6f, ry + 14f, ellipsize(fontReg, 10f, m.remark.ifBlank { "—" }, right - (margin + lw) - 12f))
            strokeRect(cs, ruleC, 0.8f, margin, ry, right - margin, rowH)
            vline(cs, ruleC, 0.8f, margin + lw, ry, ry + rowH)
        }

        // ── Cover ──
        try {
            page { cs ->
                val cyMid = pageH / 2f
                textCenter(cs, fontBold, 12f, accent, pageW / 2f, cyMid - 56f, "DISTRIBUTOR")
                textCenter(cs, fontBold, 30f, ink, pageW / 2f, cyMid - 18f, ellipsize(fontBold, 30f, distributor.ifBlank { company }.ifBlank { "RECCE" }, contentW))
                if (company.isNotBlank() && distributor.isNotBlank()) textCenter(cs, fontReg, 13f, soft, pageW / 2f, cyMid + 8f, company)
                hline(cs, ruleC, 0.8f, pageW / 2f - 80f, pageW / 2f + 80f, cyMid + 26f)
                textCenter(cs, fontReg, 12f, ink, pageW / 2f, cyMid + 46f, "${recces.size} shops · ${dateFmt.format(Date())}")
            }

            val addrCache = HashMap<String, String>()
            rows(shops, recces).forEach { (shop, r) ->
                val title = distributor.ifBlank { company }.ifBlank { "RECCE" }
                val addr = addressOf(r, addrCache).ifBlank { shop?.city ?: "" }
                // shopPhotos holds EVERY captured photo (front + each size's), so the size photos must be
                // excluded here — otherwise they'd repeat as front pages. Mirrors the recce form's split.
                val mediaPhotos = r.media.flatMap { it.photos }.toSet()
                val frontPhotos = r.shopPhotos.filter { it !in mediaPhotos }
                // 1) FRONT photo page(s) — BEFORE (shop front) | AFTER (empty slot, filled after install).
                //    Two front photos fill both boxes; a lone photo leaves AFTER blank. Mirrors size pages.
                frontPhotos.chunked(2).forEach { pair ->
                    page { cs ->
                        val by = infoTable(cs, shop, addr, titleRow(cs, title) + 6f)
                        val top = by + headGap; val bottom = pageH - margin - footerH
                        val cg = 10f; val midX = pageW / 2f; val halfW = (contentW - cg) / 2f
                        val before = pair[0]; val after = pair.getOrNull(1)
                        val (bw, bh) = boxSize(before, halfW, bottom - top, 16f)   // BEFORE sizes both boxes
                        val leftBox = RectF(midX - cg / 2f - bw, top, midX - cg / 2f, top + bh)
                        val rightBox = RectF(midX + cg / 2f, top, midX + cg / 2f + bw, top + bh)
                        photoCard(cs, leftBox, "FRONT SHOP PHOTO 1", before)
                        photoCard(cs, rightBox, "FRONT SHOP PHOTO 2", after)
                    }
                }
                // 2) One page per size — BEFORE (recce photo) | AFTER (filled at install) + size table.
                r.media.forEachIndexed { idx, m ->
                    page { cs ->
                        val by = infoTable(cs, shop, addr, titleRow(cs, title) + 6f)
                        // Reserve the 3-row size table (66) + a 6 mm gap under the photo, so a tall photo still fits.
                        val top = by + headGap; val photoBottom = pageH - margin - 66f - headGap - footerH
                        val cg = 10f; val midX = pageW / 2f; val halfW = (contentW - cg) / 2f
                        val before = m.photos.firstOrNull()
                        val (bw, bh) = boxSize(before, halfW, photoBottom - top, 16f)   // BEFORE sizes both boxes
                        val leftBox = RectF(midX - cg / 2f - bw, top, midX - cg / 2f, top + bh)
                        val rightBox = RectF(midX + cg / 2f, top, midX + cg / 2f + bw, top + bh)
                        photoCard(cs, leftBox, "BEFORE", before)
                        photoCard(cs, rightBox, "AFTER", null)
                        sizeTable(cs, idx + 1, m, top + bh + headGap)   // size table sits 6 mm below the photo box
                    }
                }
                // 3) A shop with no photos & no media still gets one info page.
                if (frontPhotos.isEmpty() && r.media.isEmpty()) page { cs -> infoTable(cs, shop, addr, titleRow(cs, title) + 6f) }
            }
            // ── Footer pass: page numbers (bottom-centre) + company logo (bottom-right) on every page ──
            val total = doc.numberOfPages
            val logoBmp = opts.reportLogoPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            val logoImg = logoBmp?.let { runCatching { LosslessFactory.createFromImage(doc, it) }.getOrNull() }
            if (opts.reportPageNumbers || logoImg != null) {
                doc.pages.forEachIndexed { i, pg ->
                    PDPageContentStream(doc, pg, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        if (opts.reportPageNumbers) {
                            val label = "${i + 1} / $total"
                            val w = fontReg.getStringWidth(label) / 1000f * 8f
                            cs.beginText(); cs.setFont(fontReg, 8f); cs.setNonStrokingColor(soft[0], soft[1], soft[2])
                            cs.newLineAtOffset(pageW / 2f - w / 2f, 8f); cs.showText(label); cs.endText()
                        }
                        if (logoImg != null) {
                            val lh = 18f; val lw = lh * logoImg.width / logoImg.height
                            cs.drawImage(logoImg, pageW - margin - lw, 4f, lw, lh)
                        }
                    }
                }
            }
            logoBmp?.recycle()
            doc.save(file)
        } finally {
            doc.close()
        }
        return share(file)
    }

    // ───────────────────────── PPTX (PowerPoint) ─────────────────────────

    /** A photo prepared for embedding: JPEG bytes + the (scaled) pixel size for aspect fitting. */
    private class Pic(val bytes: ByteArray, val w: Int, val h: Int)

    private fun pic(path: String?, quality: Int): Pic? {
        if (path == null) return null
        val bmp = imageCompressor.prepareImageForPdf(File(path), MAX_DIMENSION_PX).getOrNull() ?: return null
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val p = Pic(baos.toByteArray(), bmp.width, bmp.height)
        bmp.recycle()
        return p
    }

    // Same landscape-A4 layout as the PDF, written as a real editable .pptx (PptxBuilder = no library).
    // Cover · per shop: a FRONT-photo slide, then one slide per size (BEFORE/AFTER boxes + size table).
    fun exportPptx(company: String, distributor: String, city: String, serial: Int, shops: List<Shop>, recces: List<RecceEntry>): Uri {
        val pageW = 842f; val pageH = 595f; val margin = 16f; val contentW = pageW - 2 * margin
        val mmPt = 72f / 25.4f; val padImg = 2f * mmPt; val headGap = 6f * mmPt; val topMargin = 10f * mmPt
        val quality = settingsStore.settings.value.reportJpegQuality.coerceIn(70, 100)
        val ink = "18181B"; val accent = "FFC400"; val soft = "787880"; val white = "FFFFFF"
        val lblBg = "EEEEF0"; val stripBg = "212124"; val ruleC = "CDCDD2"; val frameC = "AAAAB0"

        val ppt = PptxBuilder(pageW, pageH)
        val file = File(dir, "${reportName(company, distributor, city, serial)}.pptx")

        // No font metrics here — clip long text to roughly fit the box width (avg glyph ≈ 0.5em).
        fun fit(raw: String, boxWpt: Float, sizePt: Float): String {
            val maxChars = (boxWpt / (sizePt * 0.5f)).toInt().coerceAtLeast(1)
            return if (raw.length <= maxChars) raw else raw.take((maxChars - 1).coerceAtLeast(1)) + "…"
        }
        fun titleRow(s: PptxBuilder.SlideScope, title: String): Float {
            s.text(margin, topMargin + 4f, contentW, 26f, fit(title, contentW, 19f), 19f, ink, bold = true, align = "ctr")
            s.rect(margin, topMargin + 30f, contentW, 0.9f, fill = ruleC)
            return topMargin + 30f
        }
        fun infoTable(s: PptxBuilder.SlideScope, shop: Shop?, addr: String, topY: Float): Float {
            val rowH = 22f; val labelW = 96f; val left = margin; val valW = contentW - labelW
            val data = listOf(
                "NAME" to (shop?.name?.ifBlank { "—" } ?: "—"),
                "CONTACT" to (shop?.contact?.ifBlank { "—" } ?: "—"),
                "ADDRESS" to addr.ifBlank { "—" },
            )
            var y = topY
            data.forEach { (lab, value) ->
                s.rect(left, y, labelW, rowH, fill = lblBg, line = ruleC)
                s.text(left + 8f, y, labelW - 12f, rowH, lab, 9f, ink, bold = true)
                s.rect(left + labelW, y, valW, rowH, line = ruleC)
                s.text(left + labelW + 8f, y, valW - 16f, rowH, fit(value, valW - 16f, 11f), 11f, ink)
                y += rowH
            }
            return y
        }
        // Fixed photo box: frame + optional BEFORE/AFTER strip + the image centred (aspect kept).
        fun photoCard(s: PptxBuilder.SlideScope, l: Float, t: Float, w: Float, h: Float, label: String?, p: Pic?) {
            s.rect(l, t, w, h, line = frameC)
            var top = t
            if (label != null) {
                s.rect(l, t, w, 16f, fill = stripBg)
                s.text(l, t, w, 16f, label, 10f, white, bold = true, align = "ctr")
                top = t + 16f
            }
            val aL = l + padImg; val aT = top + padImg; val aW = w - 2 * padImg; val aH = (t + h) - top - 2 * padImg
            if (p == null) { s.text(aL, aT, aW, aH, "—", 12f, soft, align = "ctr"); return }
            val sc = minOf(aW / p.w, aH / p.h)
            val iw = p.w * sc; val ih = p.h * sc
            s.picture(aL + (aW - iw) / 2f, aT + (aH - ih) / 2f, iw, ih, p.bytes)
        }
        fun sizeTable(s: PptxBuilder.SlideScope, sr: Int, m: MediaItem, topY: Float) {
            val labels = listOf("SR NO", "ACTIVITY", "CREATIVE", "WIDTH", "HEIGHT", "QTY")
            val weights = listOf(0.7f, 2.4f, 2.2f, 1.1f, 1.1f, 0.8f); val tot = weights.sum(); val rowH = 22f
            val values = listOf(
                "%02d".format(sr), m.type.ifBlank { "—" }, m.creative.ifBlank { "—" },
                if (m.width != 0.0) num(toInches(m.width, m.unit)) else "—",
                if (m.height != 0.0) num(toInches(m.height, m.unit)) else "—",
                m.qty.toString(),
            )
            var x = margin
            labels.forEachIndexed { i, lab ->
                val cw = contentW * weights[i] / tot
                s.rect(x, topY, cw, rowH, fill = stripBg)
                s.text(x + 6f, topY, cw - 8f, rowH, fit(lab, cw - 8f, 9f), 9f, white, bold = true)
                s.rect(x, topY + rowH, cw, rowH, line = ruleC)
                s.text(x + 6f, topY + rowH, cw - 10f, rowH, fit(values[i], cw - 10f, 10f), 10f, ink)
                x += cw
            }
            // REMARK — full-width row under the table.
            val ry = topY + 2 * rowH; val lw = 96f
            s.rect(margin, ry, lw, rowH, fill = lblBg, line = ruleC)
            s.text(margin + 6f, ry, lw - 10f, rowH, "REMARK", 9f, ink, bold = true)
            s.rect(margin + lw, ry, contentW - lw, rowH, line = ruleC)
            s.text(margin + lw + 6f, ry, contentW - lw - 12f, rowH, fit(m.remark.ifBlank { "—" }, contentW - lw - 12f, 10f), 10f, ink)
        }

        // ── Cover ──
        ppt.slide {
            val mid = pageH / 2f
            text(margin, mid - 64f, contentW, 18f, "DISTRIBUTOR", 12f, accent, bold = true, align = "ctr")
            text(margin, mid - 40f, contentW, 40f, fit(distributor.ifBlank { company }.ifBlank { "RECCE" }, contentW, 30f), 30f, ink, bold = true, align = "ctr")
            if (company.isNotBlank() && distributor.isNotBlank()) text(margin, mid + 2f, contentW, 18f, company, 13f, soft, align = "ctr")
            rect(pageW / 2f - 80f, mid + 30f, 160f, 0.9f, fill = ruleC)
            text(margin, mid + 38f, contentW, 18f, "${recces.size} shops · ${dateFmt.format(Date())}", 12f, ink, align = "ctr")
        }

        val addrCache = HashMap<String, String>()
        rows(shops, recces).forEach { (shop, r) ->
            val title = distributor.ifBlank { company }.ifBlank { "RECCE" }
            val addr = addressOf(r, addrCache).ifBlank { shop?.city ?: "" }
            val mediaPhotos = r.media.flatMap { it.photos }.toSet()
            val frontPhotos = r.shopPhotos.filter { it !in mediaPhotos }
            // 1) FRONT photo slide(s) — BEFORE (shop front) | AFTER (empty slot, filled after install).
            frontPhotos.chunked(2).forEach { pair ->
                ppt.slide {
                    val by = infoTable(this, shop, addr, titleRow(this, title) + 6f)
                    val top = by + headGap; val boxH = pageH - margin - top
                    val cg = 10f; val halfW = (contentW - cg) / 2f
                    photoCard(this, margin, top, halfW, boxH, "FRONT SHOP PHOTO 1", pic(pair[0], quality))
                    photoCard(this, margin + halfW + cg, top, halfW, boxH, "FRONT SHOP PHOTO 2", pair.getOrNull(1)?.let { pic(it, quality) })
                }
            }
            // 2) One slide per size — BEFORE (recce photo) | AFTER (blank) + size table.
            r.media.forEachIndexed { idx, m ->
                ppt.slide {
                    val by = infoTable(this, shop, addr, titleRow(this, title) + 6f)
                    val top = by + headGap; val photoBottom = pageH - margin - 66f - headGap
                    val cg = 10f; val halfW = (contentW - cg) / 2f; val boxH = photoBottom - top
                    photoCard(this, margin, top, halfW, boxH, "BEFORE", pic(m.photos.firstOrNull(), quality))
                    photoCard(this, margin + halfW + cg, top, halfW, boxH, "AFTER", null)
                    sizeTable(this, idx + 1, m, top + boxH + headGap)
                }
            }
            // 3) Shop with no photos & no media still gets one info slide.
            if (frontPhotos.isEmpty() && r.media.isEmpty()) {
                ppt.slide { infoTable(this, shop, addr, titleRow(this, title) + 6f) }
            }
        }

        ppt.build(file)
        return share(file)
    }

    /** Original pixel dimensions of an image (without loading it) — drives the photo-box sizing. */
    private fun imageBounds(path: String): Pair<Int, Int> = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        o.outWidth to o.outHeight
    } catch (_: Throwable) { 0 to 0 }

    private fun stamp() = System.currentTimeMillis().toString()

    /** Clean number for Excel cells — drops a trailing ".0" (24.0 → "24", 24.5 → "24.5"). */
    private fun num(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /** Every dimension is exported in inches — feet are ×12, inches stay as-is. */
    private fun toInches(v: Double, unit: String): Double = if (unit.equals("ft", ignoreCase = true)) v * 12.0 else v

    /** Builds "Company_Distributor_City_<serial>" with each part made filename-safe; blanks dropped. */
    private fun reportName(company: String, distributor: String, city: String, serial: Int): String {
        fun part(s: String) = s.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return listOf(part(company), part(distributor), part(city), serial.toString())
            .filter { it.isNotBlank() }
            .joinToString("_")
            .ifBlank { "Recce_${stamp()}" }
    }

    private companion object {
        const val MAX_DIMENSION_PX = 2048          // WhatsApp-HD tier: clients can zoom into shop details
    }
}
