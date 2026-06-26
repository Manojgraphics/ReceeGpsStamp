package com.receegpsstamp

import com.receegpsstamp.data.export.PptxBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Smoke test: the hand-rolled .pptx must be a non-trivial ZIP that opens (validated separately in PowerPoint). */
class PptxBuilderTest {
    @Test
    fun generatesValidPptx() {
        val jpeg = ByteArray(256) { it.toByte() }   // placeholder media bytes — only the packaging is under test
        val b = PptxBuilder(842f, 595f)
        b.slide {
            text(16f, 30f, 810f, 30f, "DINESH AGENCIES", 19f, "181818", bold = true, align = "ctr")
            rect(16f, 70f, 810f, 0.9f, fill = "CDCDD2")
            rect(16f, 90f, 96f, 22f, fill = "EEEEF0", line = "CDCDD2")
            text(24f, 90f, 84f, 22f, "NAME", 9f, "181818", bold = true)
            picture(300f, 150f, 240f, 180f, jpeg)
        }
        b.slide {
            rect(16f, 40f, 390f, 16f, fill = "212124")
            text(16f, 40f, 390f, 16f, "BEFORE", 10f, "FFFFFF", bold = true, align = "ctr")
            picture(16f, 60f, 390f, 260f, jpeg)
        }
        val out = File.createTempFile("rgs_pptx", ".pptx")
        try {
            b.build(out)
            assertTrue("pptx not written", out.exists() && out.length() > 1000)
        } finally {
            out.delete()
        }
    }
}
