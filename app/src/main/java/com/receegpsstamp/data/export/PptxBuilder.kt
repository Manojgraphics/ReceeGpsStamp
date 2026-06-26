package com.receegpsstamp.data.export

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal hand-rolled **.pptx** (PowerPoint / OOXML) writer — no third-party library.
 *
 * A .pptx is just a ZIP of XML parts + media. We emit a blank-themed deck and draw every slide
 * from three primitives — rectangles, text boxes and JPEG pictures — the same way the PDF report
 * is drawn. All public drawing is in POINTS (1 pt = 12700 EMU); origin is top-left like the PDF.
 *
 * Colours are passed as 6-digit hex ("181818"). Font sizes are in points.
 */
class PptxBuilder(private val slideWPt: Float, private val slideHPt: Float) {

    private fun emu(pt: Float): Long = (pt * 12700f).toLong()

    private class Slide {
        val body = StringBuilder()
        val images = mutableListOf<ByteArray>()   // index → rId (rId1 = layout, images start at rId2)
    }

    private val slides = mutableListOf<Slide>()

    /** Drawing surface for one slide. All coordinates/sizes are in points. */
    inner class SlideScope internal constructor(
        private val body: StringBuilder,
        private val images: MutableList<ByteArray>,
    ) {
        private var nextId = 1   // shape id 1 = group; real shapes start at 2
        private fun esc(t: String) = t
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        /** Filled and/or outlined rectangle. Pass null fill/line to skip that part. */
        fun rect(x: Float, y: Float, w: Float, h: Float, fill: String? = null, line: String? = null, lineWpt: Float = 0.8f) {
            val id = ++nextId
            val fillXml = if (fill != null) "<a:solidFill><a:srgbClr val=\"$fill\"/></a:solidFill>" else "<a:noFill/>"
            val lineXml = if (line != null) "<a:ln w=\"${emu(lineWpt)}\"><a:solidFill><a:srgbClr val=\"$line\"/></a:solidFill></a:ln>" else ""
            body.append(
                "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"r$id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                    "<p:spPr><a:xfrm><a:off x=\"${emu(x)}\" y=\"${emu(y)}\"/><a:ext cx=\"${emu(w)}\" cy=\"${emu(h)}\"/></a:xfrm>" +
                    "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>$fillXml$lineXml</p:spPr>" +
                    "<p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody></p:sp>",
            )
        }

        /** A single-line text run inside a box. align = l|ctr|r, anchor = t|ctr|b. */
        fun text(
            x: Float, y: Float, w: Float, h: Float, t: String, sizePt: Float, color: String,
            bold: Boolean = false, align: String = "l", anchor: String = "ctr",
        ) {
            if (t.isEmpty()) return
            val id = ++nextId
            val b = if (bold) "1" else "0"
            body.append(
                "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"t$id\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
                    "<p:spPr><a:xfrm><a:off x=\"${emu(x)}\" y=\"${emu(y)}\"/><a:ext cx=\"${emu(w)}\" cy=\"${emu(h)}\"/></a:xfrm>" +
                    "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>" +
                    "<p:txBody><a:bodyPr wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" anchor=\"$anchor\"/><a:lstStyle/>" +
                    "<a:p><a:pPr algn=\"$align\"/><a:r><a:rPr lang=\"en-US\" sz=\"${(sizePt * 100).toInt()}\" b=\"$b\">" +
                    "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:latin typeface=\"Ubuntu\"/></a:rPr>" +
                    "<a:t>${esc(t)}</a:t></a:r></a:p></p:txBody></p:sp>",
            )
        }

        /** Embed a JPEG and draw it at the given box (caller fits aspect ratio). */
        fun picture(x: Float, y: Float, w: Float, h: Float, jpeg: ByteArray) {
            images.add(jpeg)
            val rId = "rId${images.size + 1}"   // rId1 = slideLayout; images start at rId2
            val id = ++nextId
            body.append(
                "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"p$id\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                    "<p:blipFill><a:blip r:embed=\"$rId\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                    "<p:spPr><a:xfrm><a:off x=\"${emu(x)}\" y=\"${emu(y)}\"/><a:ext cx=\"${emu(w)}\" cy=\"${emu(h)}\"/></a:xfrm>" +
                    "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>",
            )
        }
    }

    fun slide(block: SlideScope.() -> Unit) {
        val s = Slide()
        SlideScope(s.body, s.images).block()
        slides.add(s)
    }

    fun build(out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            fun putBytes(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }

            val n = slides.size
            // Global media numbering across all slides.
            var mediaCount = 0

            // [Content_Types].xml
            val ct = StringBuilder(XML_DECL)
            ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            ct.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            ct.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            ct.append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
            ct.append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
            ct.append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
            ct.append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
            ct.append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
            for (i in 1..n) ct.append("<Override PartName=\"/ppt/slides/slide$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())

            // _rels/.rels
            put(
                "_rels/.rels",
                XML_DECL + "<Relationships xmlns=\"$REL_NS\">" +
                    "<Relationship Id=\"rId1\" Type=\"$OD/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>",
            )

            // ppt/presentation.xml + rels
            val pres = StringBuilder(XML_DECL)
            pres.append("<p:presentation xmlns:a=\"$A_NS\" xmlns:r=\"$R_NS\" xmlns:p=\"$P_NS\">")
            pres.append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>")
            pres.append("<p:sldIdLst>")
            for (i in 0 until n) pres.append("<p:sldId id=\"${256 + i}\" r:id=\"rId${i + 2}\"/>")
            pres.append("</p:sldIdLst>")
            pres.append("<p:sldSz cx=\"${emu(slideWPt)}\" cy=\"${emu(slideHPt)}\"/>")
            pres.append("<p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>")
            put("ppt/presentation.xml", pres.toString())

            val presRels = StringBuilder(XML_DECL)
            presRels.append("<Relationships xmlns=\"$REL_NS\">")
            presRels.append("<Relationship Id=\"rId1\" Type=\"$OD/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
            for (i in 1..n) presRels.append("<Relationship Id=\"rId${i + 1}\" Type=\"$OD/slide\" Target=\"slides/slide$i.xml\"/>")
            presRels.append("<Relationship Id=\"rId${n + 2}\" Type=\"$OD/theme\" Target=\"theme/theme1.xml\"/>")
            presRels.append("</Relationships>")
            put("ppt/_rels/presentation.xml.rels", presRels.toString())

            // Boilerplate parts.
            put("ppt/theme/theme1.xml", THEME)
            put("ppt/slideMasters/slideMaster1.xml", SLIDE_MASTER)
            put(
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                XML_DECL + "<Relationships xmlns=\"$REL_NS\">" +
                    "<Relationship Id=\"rId1\" Type=\"$OD/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"$OD/theme\" Target=\"../theme/theme1.xml\"/></Relationships>",
            )
            put("ppt/slideLayouts/slideLayout1.xml", SLIDE_LAYOUT)
            put(
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                XML_DECL + "<Relationships xmlns=\"$REL_NS\">" +
                    "<Relationship Id=\"rId1\" Type=\"$OD/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>",
            )

            // Slides + their media + rels.
            slides.forEachIndexed { idx, s ->
                val k = idx + 1
                put(
                    "ppt/slides/slide$k.xml",
                    XML_DECL + "<p:sld xmlns:a=\"$A_NS\" xmlns:r=\"$R_NS\" xmlns:p=\"$P_NS\"><p:cSld><p:spTree>" +
                        "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
                        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
                        s.body + "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>",
                )
                val rels = StringBuilder(XML_DECL)
                rels.append("<Relationships xmlns=\"$REL_NS\">")
                rels.append("<Relationship Id=\"rId1\" Type=\"$OD/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>")
                s.images.forEachIndexed { i, bytes ->
                    mediaCount++
                    putBytes("ppt/media/image$mediaCount.jpg", bytes)
                    rels.append("<Relationship Id=\"rId${i + 2}\" Type=\"$OD/image\" Target=\"../media/image$mediaCount.jpg\"/>")
                }
                rels.append("</Relationships>")
                put("ppt/slides/_rels/slide$k.xml.rels", rels.toString())
            }
        }
    }

    private companion object {
        const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        const val REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
        const val OD = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"

        val THEME = XML_DECL +
            "<a:theme xmlns:a=\"$A_NS\" name=\"Office\"><a:themeElements>" +
            "<a:clrScheme name=\"Office\">" +
            "<a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>" +
            "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>" +
            "<a:dk2><a:srgbClr val=\"44546A\"/></a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>" +
            "<a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1><a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2>" +
            "<a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3><a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>" +
            "<a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5><a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6>" +
            "<a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme>" +
            "<a:fontScheme name=\"Office\">" +
            "<a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
            "<a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
            "<a:fmtScheme name=\"Office\">" +
            "<a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>" +
            "<a:lnStyleLst>" +
            "<a:ln w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln>" +
            "<a:ln w=\"12700\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln>" +
            "<a:ln w=\"19050\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln></a:lnStyleLst>" +
            "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>" +
            "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst>" +
            "</a:fmtScheme></a:themeElements></a:theme>"

        val SLIDE_MASTER = XML_DECL +
            "<p:sldMaster xmlns:a=\"$A_NS\" xmlns:r=\"$R_NS\" xmlns:p=\"$P_NS\"><p:cSld><p:spTree>" +
            "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
            "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
            "</p:spTree></p:cSld>" +
            "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
            "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>"

        val SLIDE_LAYOUT = XML_DECL +
            "<p:sldLayout xmlns:a=\"$A_NS\" xmlns:r=\"$R_NS\" xmlns:p=\"$P_NS\" type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>" +
            "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
            "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
            "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
    }
}
