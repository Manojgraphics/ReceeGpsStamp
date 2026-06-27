package com.receegpsstamp.feature.annotate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import com.receegpsstamp.ui.theme.  AppYellow
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.RgsIcons
import com.receegpsstamp.ui.theme.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

// Mark types + Select/Pan (navigate & edit existing marks).
//  Select  · move/resize an existing mark
//  Box     · rectangle outline
//  RRect   · round shape (ellipse / circle, fits the corners' bounding box)
//  Arrow   · line with arrowhead
//  Line    · straight line (no arrowhead)
//  Text    · floating text label
//  TextBox · text inside a rectangle
//  Pen      · freehand drawing
//  PenLine  · pen with straight connected segments
//  PenShape · pen with auto-closed shape (last point joins the first)
//  Size     · rectangle + W×H size label
enum class Tool { Select, Box, RRect, Arrow, Line, Text, TextBox, Pen, PenLine, PenShape, Size }

// Marks store image-relative (0..1) coordinates so they stay aligned at any zoom and at full-res flatten.
private data class Mark(
    val id: Long,
    val tool: Tool,
    val pts: List<Offset>,   // Rect family: 4 free corners [TL,TR,BR,BL] (vector box); Arrow/Line: [tail,head]; Text: [anchor]; Pen: path
    val text: String = "",
    val colorArgb: Long = MARK_PALETTE[0],   // raw ARGB so the mark always uses exactly the chosen colour
    val rotation: Float = 0f,   // degrees — rotates Box/RRect/TextBox/Size around their centre
    val weight: Float = 1f,     // stroke-width multiplier (see WEIGHT_LEVELS)
    val fillAlpha: Float = 0f,  // 0 = no fill (outline only) until the user adds a fill
    val fillColorArgb: Long = MARK_PALETTE[0],  // fill colour — independent of the outline colour
)

// Line-thickness multipliers — applied to the base stroke width for every mark.
private val WEIGHT_LEVELS = listOf(0.5f, 1f, 1.75f, 2.75f, 4f)

// Ready-made text labels for quick one-tap annotation.
private val QUICK_LABELS = listOf("Board here", "Board damage", "Need bracket", "Replace", "New site", "Existing", "Check")

// Rectangle corner-edit mode (used by the Rectangle Properties pane).
private enum class CornerMode { Dual, Single }

// Perpendicular distance of point p from the line a→b.
private fun perpDistance(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (len == 0f) return (p - a).getDistance()
    return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / len
}

// Douglas–Peucker: reduce a wobbly freehand path to clean straight segments.
private fun simplifyPath(points: List<Offset>, epsilon: Float): List<Offset> {
    if (points.size < 3) return points
    var maxDist = 0f; var index = 0
    val first = points.first(); val last = points.last()
    for (i in 1 until points.size - 1) {
        val d = perpDistance(points[i], first, last)
        if (d > maxDist) { maxDist = d; index = i }
    }
    return if (maxDist > epsilon) {
        val left = simplifyPath(points.subList(0, index + 1), epsilon)
        val right = simplifyPath(points.subList(index, points.size), epsilon)
        left.dropLast(1) + right
    } else {
        listOf(first, last)
    }
}

// Average (centroid) of a list of points.
private fun centroid(pts: List<Offset>): Offset {
    if (pts.isEmpty()) return Offset.Zero
    var sx = 0f; var sy = 0f
    pts.forEach { sx += it.x; sy += it.y }
    return Offset(sx / pts.size, sy / pts.size)
}

// Rotate a screen-space point around a centre by the given degrees.
private fun rotateAround(p: Offset, c: Offset, deg: Float): Offset {
    if (deg == 0f) return p
    val r = Math.toRadians(deg.toDouble())
    val cos = kotlin.math.cos(r).toFloat(); val sin = kotlin.math.sin(r).toFloat()
    val dx = p.x - c.x; val dy = p.y - c.y
    return Offset(c.x + dx * cos - dy * sin, c.y + dx * sin + dy * cos)
}

// Is this a rectangle-family tool (supports rotation + 4-corner editing)?
private fun Tool.isRect() = this == Tool.Box || this == Tool.RRect || this == Tool.TextBox || this == Tool.Size

// Any of the freehand pen variants.
private fun Tool.isPen() = this == Tool.Pen || this == Tool.PenLine || this == Tool.PenShape

// 12 mark colours — yellow first (brand default), then a rounded set covering common annotation needs.
private val MARK_PALETTE = listOf(
    0xFFFFC400L,  // brand yellow
    0xFFFFFFFFL,  // white
    0xFF000000L,  // black
    0xFFE53935L,  // red
    0xFFFB8C00L,  // orange
    0xFF43A047L,  // green
    0xFF1E88E5L,  // blue
    0xFF00ACC1L,  // teal
    0xFF8E24AAL,  // purple
    0xFFEC407AL,  // pink
    0xFF795548L,  // brown
    0xFF607D8BL,  // grey-blue
)

@Composable
fun AnnotateScreen(photoPath: String?, saveCleanCopy: Boolean = false, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load the photo bitmap once (full-res — also used for the flatten).
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    // Crop mode — when true, draws a draggable crop rectangle overlay; "Apply crop" replaces bitmap.
    var cropMode by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    androidx.compose.runtime.LaunchedEffect(photoPath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { if (photoPath != null) BitmapFactory.decodeFile(photoPath) else null }.getOrNull()
        }
        loadError = bitmap == null
    }

    val marks = remember { mutableStateListOf<Mark>() }
    var tool by remember { mutableStateOf(Tool.Box) }
    var markColor by remember { mutableStateOf(MARK_PALETTE[0]) }
    var markWeight by remember { mutableStateOf(1f) }
    var colorMenuOpen by remember { mutableStateOf(false) }
    var weightMenuOpen by remember { mutableStateOf(false) }
    var shapesMenuOpen by remember { mutableStateOf(false) }
    var textMenuOpen by remember { mutableStateOf(false) }
    var penMenuOpen by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var nextId by remember { mutableStateOf(1L) }
    var busy by remember { mutableStateOf(false) }
    // Rectangle Properties pane state.
    var cornerMode by remember { mutableStateOf(CornerMode.Dual) }
    // Magnet snap guides — shown while a shape's centre aligns with the image centre.
    var snapV by remember { mutableStateOf(false) }   // vertical centre line (x = 0.5)
    var snapH by remember { mutableStateOf(false) }   // horizontal centre line (y = 0.5)
    // Magnifier loupe — screen position being dragged (corner/endpoint); shows a zoomed view so
    // the finger doesn't hide the exact point.
    var loupeAt by remember { mutableStateOf<Offset?>(null) }
    // The currently-selected rectangle mark (drives the properties pane visibility).
    val selectedRect = marks.firstOrNull { it.id == selectedId && it.tool.isRect() }

    // ── Undo / redo — snapshots of the marks list (capped) ──
    val undoStack = remember { mutableStateListOf<List<Mark>>() }
    val redoStack = remember { mutableStateListOf<List<Mark>>() }
    fun pushHistory(before: List<Mark>) {
        undoStack.add(before)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
    }
    fun restore(snapshot: List<Mark>) { marks.clear(); marks.addAll(snapshot); selectedId = null }
    fun doUndo() { if (undoStack.isNotEmpty()) { redoStack.add(marks.toList()); restore(undoStack.removeAt(undoStack.lastIndex)) } }
    fun doRedo() { if (redoStack.isNotEmpty()) { undoStack.add(marks.toList()); restore(redoStack.removeAt(redoStack.lastIndex)) } }

    // ── Image adjust (brightness / contrast) — live preview + baked on flatten ──
    var adjustMode by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(0f) }   // -100..100
    var contrast by remember { mutableStateOf(0f) }     // -100..100
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Zoom / pan
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Text input dialog (Text callout & Size label)
    var textDialog by remember { mutableStateOf<Mark?>(null) }
    var textValue by remember { mutableStateOf("") }

    val bmp = bitmap
    // Fit rect (image displayed "contain" inside the canvas) at scale 1.
    fun fit(): Pair<Offset, Size> {
        val b = bmp ?: return Offset.Zero to Size.Zero
        val cw = canvasSize.width.toFloat(); val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f) return Offset.Zero to Size.Zero
        val s = minOf(cw / b.width, ch / b.height)
        val w = b.width * s; val h = b.height * s
        return Offset((cw - w) / 2f, (ch - h) / 2f) to Size(w, h)
    }
    fun fracToScreen(f: Offset): Offset {
        val (base, sz) = fit()
        val p1 = Offset(base.x + f.x * sz.width, base.y + f.y * sz.height)
        return p1 * scale + pan
    }
    fun screenToFrac(s: Offset): Offset {
        val (base, sz) = fit()
        if (sz.width <= 0f) return Offset.Zero
        val p1 = (s - pan) / scale
        return Offset((p1.x - base.x) / sz.width, (p1.y - base.y) / sz.height)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF121212))) {

        // ── Canvas: photo + marks ──
        Box(
            Modifier.fillMaxSize()
                .pointerInput(tool, bmp) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var twoFinger = false
                            val startScreen = down.position
                            val historyBefore = marks.toList()   // snapshot for undo/redo
                            var draftId: Long? = null
                            var moveMarkId: Long? = null
                            var moveStartPts: List<Offset>? = null
                            var resizeIdx = -1
                            var moveStartFrac = screenToFrac(down.position)
                            var moved = false

                            // Crop mode: drag any corner of the crop rectangle.
                            var cropCorner = -1   // 0=TL, 1=TR, 2=BL, 3=BR
                            if (cropMode) {
                                val r = cropRect
                                if (r != null) {
                                    val corners = listOf(
                                        fracToScreen(Offset(r.left, r.top)),
                                        fracToScreen(Offset(r.right, r.top)),
                                        fracToScreen(Offset(r.left, r.bottom)),
                                        fracToScreen(Offset(r.right, r.bottom)),
                                    )
                                    corners.forEachIndexed { i, c -> if ((c - down.position).getDistance() < 60f) cropCorner = i }
                                }
                            } else
                            // For Select tool: hit-test on down.
                            if (tool == Tool.Select) {
                                val hit = hitTest(marks, down.position, ::fracToScreen)
                                selectedId = hit?.id
                                if (hit != null) {
                                    moveMarkId = hit.id
                                    moveStartPts = hit.pts
                                    resizeIdx = cornerHit(hit, down.position, ::fracToScreen)
                                    // Rect family moves ONLY from the centre grip; corners resize;
                                    // tapping elsewhere just selects (no accidental drag).
                                    if (hit.tool.isRect()) {
                                        val ctr = centroid(hit.pts.map { fracToScreen(it) })
                                        val nearCentre = (down.position - ctr).getDistance() < 36f
                                        if (nearCentre) resizeIdx = -1
                                        else if (resizeIdx < 0) moveMarkId = null
                                    }
                                }
                            } else if (tool != Tool.Text) {
                                // Start a new draft mark (Box/Arrow/Pen/Size). Text places on tap (on up).
                                val f = screenToFrac(down.position)
                                val id = nextId++
                                draftId = id
                                val pts = when {
                                    tool.isRect() -> listOf(f, f, f, f)    // vector box: 4 free corners
                                    tool == Tool.Pen || tool == Tool.PenShape -> listOf(f)  // freehand: append points
                                    else -> listOf(f, f)                   // PenLine + Arrow/Line: two-point
                                }
                                marks.add(Mark(id, tool, pts, colorArgb = markColor, weight = markWeight))
                            }

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    // Two-finger zoom / pan — cancel any in-progress draft.
                                    if (draftId != null) { marks.removeAll { it.id == draftId }; draftId = null }
                                    twoFinger = true
                                    val zoom = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid()
                                    if (zoom != 1f) {
                                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                                        pan = centroid - (centroid - pan) * (newScale / scale) + panChange
                                        scale = newScale
                                    } else {
                                        pan += panChange
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (!twoFinger && pressed.size == 1) {
                                    val cur = pressed.first().position
                                    if ((cur - startScreen).getDistance() > 6f) moved = true
                                    val f = screenToFrac(cur)
                                    when {
                                        cropMode && cropCorner >= 0 -> {
                                            val r = cropRect ?: androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f)
                                            val fx = f.x.coerceIn(0f, 1f); val fy = f.y.coerceIn(0f, 1f)
                                            val nr = when (cropCorner) {
                                                0 -> androidx.compose.ui.geometry.Rect(minOf(fx, r.right - 0.05f), minOf(fy, r.bottom - 0.05f), r.right, r.bottom)
                                                1 -> androidx.compose.ui.geometry.Rect(r.left, minOf(fy, r.bottom - 0.05f), maxOf(fx, r.left + 0.05f), r.bottom)
                                                2 -> androidx.compose.ui.geometry.Rect(minOf(fx, r.right - 0.05f), r.top, r.right, maxOf(fy, r.top + 0.05f))
                                                else -> androidx.compose.ui.geometry.Rect(r.left, r.top, maxOf(fx, r.left + 0.05f), maxOf(fy, r.top + 0.05f))
                                            }
                                            cropRect = nr
                                        }
                                        draftId != null -> {
                                            val m = marks.firstOrNull { it.id == draftId }
                                            if (m != null) {
                                                val np = when {
                                                    m.tool.isRect() -> {
                                                        // Build an axis-aligned rect from the fixed start corner to the cursor.
                                                        val s = m.pts[0]
                                                        listOf(s, Offset(f.x, s.y), f, Offset(s.x, f.y))
                                                    }
                                                    m.tool == Tool.Pen || m.tool == Tool.PenShape -> m.pts + f
                                                    else -> listOf(m.pts[0], f)
                                                }
                                                replace(marks, m.copy(pts = np))
                                            }
                                        }
                                        moveMarkId != null && resizeIdx >= 0 && moveStartPts != null -> {
                                            val m = marks.firstOrNull { it.id == moveMarkId } ?: return@awaitPointerEventScope
                                            loupeAt = cur   // show the magnifier while editing a corner
                                            if (m.tool.isRect()) {
                                                // Vector box: move ONLY the dragged corner — any of the 4 corners
                                                // can go anywhere, forming an arbitrary quadrilateral. The drag
                                                // point is inverse-rotated into the shape's local space so it
                                                // works at any rotation.
                                                val c = centroid(m.pts)
                                                val lf = rotateAround(f, c, -m.rotation)
                                                val np = m.pts.toMutableList()
                                                if (resizeIdx in np.indices) np[resizeIdx] = lf
                                                replace(marks, m.copy(pts = np))
                                            } else {
                                                val np = m.pts.toMutableList(); np[resizeIdx] = f
                                                replace(marks, m.copy(pts = np))
                                            }
                                        }
                                        moveMarkId != null && moveStartPts != null -> {
                                            var d = f - moveStartFrac
                                            val m = marks.firstOrNull { it.id == moveMarkId } ?: return@awaitPointerEventScope
                                            // Magnet snap: pull the shape's centre onto the image centre lines.
                                            if (m.tool.isRect()) {
                                                val base = centroid(moveStartPts!!)
                                                val nc = base + d
                                                val th = 0.018f
                                                val sV = abs(nc.x - 0.5f) < th
                                                val sH = abs(nc.y - 0.5f) < th
                                                if (sV) d = Offset(0.5f - base.x, d.y)
                                                if (sH) d = Offset(d.x, 0.5f - base.y)
                                                if ((sV && !snapV) || (sH && !snapH)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                snapV = sV; snapH = sH
                                            }
                                            replace(marks, m.copy(pts = moveStartPts!!.map { it + d }))
                                        }
                                    }
                                    pressed.first().consume()
                                }
                                if (event.changes.all { !it.pressed }) break
                            }

                            // Gesture end
                            if (!twoFinger) {
                                if (tool == Tool.Text && !moved) {
                                    // Place a text callout on tap, then drop into Edit mode.
                                    val id = nextId++
                                    val m = Mark(id, Tool.Text, listOf(screenToFrac(startScreen)), text = "", colorArgb = markColor, weight = markWeight)
                                    textValue = ""
                                    textDialog = m
                                    tool = Tool.Select
                                } else if (draftId != null) {
                                    var m = marks.firstOrNull { it.id == draftId }
                                    // Closed shape: simplify the freehand loop into clean straight segments.
                                    if (m != null && m.tool == Tool.PenShape) {
                                        val simplified = simplifyPath(m.pts, 0.022f)
                                        if (simplified.size >= 3) { replace(marks, m.copy(pts = simplified)); m = marks.firstOrNull { it.id == draftId } }
                                        else { marks.removeAll { it.id == draftId }; m = null }
                                    }
                                    // Discard tiny accidental shapes/lines.
                                    if (m != null && !m.tool.isPen()) {
                                        val len = if (m.tool.isRect()) (m.pts[2] - m.pts[0]).getDistance()
                                            else (m.pts[1] - m.pts[0]).getDistance()
                                        if (len < 0.02f) { marks.removeAll { it.id == draftId }; m = null }
                                    }
                                    if (m != null && m.tool == Tool.PenLine && (m.pts.last() - m.pts.first()).getDistance() < 0.02f) {
                                        marks.removeAll { it.id == draftId }; m = null
                                    }
                                    if (m != null && (m.tool == Tool.Size || m.tool == Tool.TextBox)) { textValue = ""; textDialog = m }
                                    // Auto-switch to Edit after a discrete shape so the next touch edits/moves
                                    // instead of drawing another. Free pen keeps drawing continuously.
                                    if (m != null && m.tool != Tool.Pen) { selectedId = draftId; tool = Tool.Select }
                                } else if (tool == Tool.Select && moveMarkId != null && !moved) {
                                    // Tap on a Text/Size mark → edit its label.
                                    val m = marks.firstOrNull { it.id == moveMarkId }
                                    if (m != null && (m.tool == Tool.Text || m.tool == Tool.Size || m.tool == Tool.TextBox)) {
                                        textValue = m.text; textDialog = m
                                    }
                                }
                                // Commit one undo entry if this gesture actually changed the marks.
                                if (!cropMode && marks.toList() != historyBefore) pushHistory(historyBefore)
                            }
                            snapV = false; snapH = false   // clear magnet guides
                            loupeAt = null                  // hide the magnifier
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                val b = bmp ?: return@Canvas
                val (base, sz) = run {
                    val cw = size.width; val ch = size.height
                    val s = minOf(cw / b.width, ch / b.height)
                    Offset((cw - b.width * s) / 2f, (ch - b.height * s) / 2f) to Size(b.width * s, b.height * s)
                }
                val topLeft = base * scale + pan
                drawImage(
                    b.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = IntSize((sz.width * scale).toInt(), (sz.height * scale).toInt()),
                    colorFilter = adjustFilter(brightness, contrast),
                )
                marks.forEach { drawMark(it, it.id == selectedId) { f -> fracToScreen(f) } }
                // Magnet snap guide lines (image centre).
                if (snapV) {
                    val x = fracToScreen(Offset(0.5f, 0.5f)).x
                    drawLine(AppYellow, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                }
                if (snapH) {
                    val y = fracToScreen(Offset(0.5f, 0.5f)).y
                    drawLine(AppYellow, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                }
                // ── Magnifier loupe: zoomed view of the point under the finger ──
                loupeAt?.let { lc ->
                    val rad = 140f; val mag = 2.3f
                    // Always in the upper area (clear of the top bar and the bottom panels) for both
                    // top and bottom handles.
                    val pos = Offset(size.width / 2f, size.height * 0.24f + rad - 50f)
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(pos.x - rad, pos.y - rad, pos.x + rad, pos.y + rad)) }
                    clipPath(circle) {
                        drawRect(Color(0xFF101010), topLeft = Offset(pos.x - rad, pos.y - rad), size = Size(rad * 2, rad * 2))
                        // Magnified bitmap (lc maps to pos, scaled by mag).
                        val mTopLeft = pos + (topLeft - lc) * mag
                        drawImage(
                            b.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(mTopLeft.x.toInt(), mTopLeft.y.toInt()),
                            dstSize = IntSize((sz.width * scale * mag).toInt(), (sz.height * scale * mag).toInt()),
                            colorFilter = adjustFilter(brightness, contrast),
                        )
                        // Magnified marks (no handles).
                        marks.forEach { mk -> drawMark(mk, false) { f -> pos + (fracToScreen(f) - lc) * mag } }
                    }
                    // Ring + crosshair at the exact point.
                    drawCircle(Color.White, radius = rad, center = pos, style = Stroke(3f))
                    drawCircle(Color.Black.copy(0.5f), radius = rad + 2f, center = pos, style = Stroke(1.5f))
                    drawLine(AppYellow, Offset(pos.x - 16f, pos.y), Offset(pos.x + 16f, pos.y), strokeWidth = 2f)
                    drawLine(AppYellow, Offset(pos.x, pos.y - 16f), Offset(pos.x, pos.y + 16f), strokeWidth = 2f)
                }
                // Crop rectangle overlay (drag corners to resize).
                cropRect?.let { r ->
                    val tl = fracToScreen(Offset(r.left, r.top))
                    val br = fracToScreen(Offset(r.right, r.bottom))
                    val l = minOf(tl.x, br.x); val ri = maxOf(tl.x, br.x)
                    val t = minOf(tl.y, br.y); val bo = maxOf(tl.y, br.y)
                    // Dim outside the crop
                    val dim = Color(0xAA000000)
                    drawRect(dim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, t))
                    drawRect(dim, topLeft = Offset(0f, bo), size = androidx.compose.ui.geometry.Size(size.width, size.height - bo))
                    drawRect(dim, topLeft = Offset(0f, t), size = androidx.compose.ui.geometry.Size(l, bo - t))
                    drawRect(dim, topLeft = Offset(ri, t), size = androidx.compose.ui.geometry.Size(size.width - ri, bo - t))
                    // Border + corner handles
                    drawRect(AppYellow, topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(ri - l, bo - t), style = Stroke(width = 4f))
                    listOf(Offset(l, t), Offset(ri, t), Offset(l, bo), Offset(ri, bo)).forEach { drawCircle(AppYellow, radius = 14f, center = it) }
                }
            }
        }

        // ── Top bar ──
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xF21C1C1E))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(RgsIcons.Back) { onDone() }
            Spacer(Modifier.weight(1f))
            // Transform + history actions grouped in a subtle pill.
            Row(
                Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // Rotate 90° clockwise — rebuilds the bitmap and saves the rotated JPEG.
            TopAction(RgsIcons.Rotate, enabled = !busy && bmp != null) {
                val src = bmp ?: return@TopAction
                busy = true
                scope.launch {
                    val rotated = withContext(Dispatchers.IO) {
                        val m = android.graphics.Matrix().apply { postRotate(90f) }
                        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                        if (photoPath != null) {
                            writeJpegAtomic(photoPath, out)
                            mirrorEditToGallery(context, File(photoPath))
                        }
                        out
                    }
                    bitmap = rotated
                    marks.clear(); selectedId = null  // marks were image-relative — clearing avoids misalignment
                    busy = false
                }
            }
            Spacer(Modifier.width(4.dp))
            // Crop — toggle a draggable rectangle on the photo. Tap again to apply.
            TopAction(RgsIcons.Crop, enabled = !busy && bmp != null) {
                if (!cropMode) {
                    cropMode = true
                    cropRect = androidx.compose.ui.geometry.Rect(0.15f, 0.15f, 0.85f, 0.85f)
                } else {
                    val src = bmp; val r = cropRect
                    if (src != null && r != null) {
                        busy = true
                        scope.launch {
                            val cropped = withContext(Dispatchers.IO) {
                                val x = (r.left * src.width).toInt().coerceIn(0, src.width - 1)
                                val y = (r.top * src.height).toInt().coerceIn(0, src.height - 1)
                                val w = ((r.right - r.left) * src.width).toInt().coerceAtLeast(1).coerceAtMost(src.width - x)
                                val h = ((r.bottom - r.top) * src.height).toInt().coerceAtLeast(1).coerceAtMost(src.height - y)
                                val out = Bitmap.createBitmap(src, x, y, w, h)
                                if (photoPath != null) {
                                    writeJpegAtomic(photoPath, out)
                                    mirrorEditToGallery(context, File(photoPath))
                                }
                                out
                            }
                            bitmap = cropped
                            marks.clear(); selectedId = null
                            cropMode = false; cropRect = null
                            busy = false
                        }
                    } else { cropMode = false; cropRect = null }
                }
            }
            Spacer(Modifier.width(4.dp))
            // Brightness / contrast adjust toggle.
            TopAction(RgsIcons.Adjust, enabled = !busy && bmp != null) { adjustMode = !adjustMode; selectedId = null }
            }   // transform pill
            Spacer(Modifier.width(8.dp))
            TopAction(RgsIcons.Share, enabled = !busy && bmp != null) {
                busy = true
                scope.launch { flatten(photoPath, marks.toList(), share = true, context = context, brightness = brightness, contrast = contrast, saveCleanCopy = saveCleanCopy); busy = false }
            }
            Spacer(Modifier.width(6.dp))
            // Finish = flatten + overwrite the original file (primary action).
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(AppYellow).clickable(enabled = !busy && bmp != null) {
                    busy = true
                    scope.launch {
                        val ok = flatten(photoPath, marks.toList(), share = false, context = context, brightness = brightness, contrast = contrast, saveCleanCopy = saveCleanCopy)
                        busy = false
                        if (ok) onDone()
                    }
                }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(if (busy) "Saving…" else "Done", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        }

        // ── Floating undo / redo on the photo — only when no pane is open (it carries its own pair) ──
        if (selectedRect == null && !adjustMode) {
            Row(
                Modifier.align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 58.dp, end = 10.dp)
                    .clip(RoundedCornerShape(11.dp)).background(Color(0x80000000)).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(RgsIcons.Undo, enabled = undoStack.isNotEmpty()) { doUndo(); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                TopAction(RgsIcons.Redo, enabled = redoStack.isNotEmpty()) { doRedo(); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            }
        }

        if (loadError) {
            Text("Could not open the photo.", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // ── Bottom stack: contextual pane (adjust / rectangle) + Tools panel ──
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {

        // Brightness / contrast adjust pane.
        androidx.compose.animation.AnimatedVisibility(visible = adjustMode) {
            AdjustPane(
                brightness = brightness, contrast = contrast,
                onBrightness = { brightness = it }, onContrast = { contrast = it },
                onReset = { brightness = 0f; contrast = 0f },
                onDone = { adjustMode = false },
            )
        }

        // Rectangle Properties pane — single unified pane for corner / rotation editing.
        if (!adjustMode) selectedRect?.let { rect ->
            RectanglePropertiesPane(
                rotation = rect.rotation,
                fillAlpha = rect.fillAlpha,
                fillColor = rect.fillColorArgb,
                cornerMode = cornerMode,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = { doUndo(); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                onRedo = { doRedo(); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                onCornerMode = { cornerMode = it },
                onRotation = { deg -> replace(marks, rect.copy(rotation = deg)) },
                onFill = { a -> replace(marks, rect.copy(fillAlpha = a)) },
                onFillColor = { hex -> replace(marks, rect.copy(fillColorArgb = hex, fillAlpha = if (rect.fillAlpha == 0f) 0.3f else rect.fillAlpha)) },
                onReset = { replace(marks, rect.copy(rotation = 0f, fillAlpha = 0f)) },
                onApply = { selectedId = null },
                onSave = {
                    busy = true
                    scope.launch {
                        val ok = flatten(photoPath, marks.toList(), share = false, context = context, brightness = brightness, contrast = contrast, saveCleanCopy = saveCleanCopy)
                        busy = false
                        if (ok) onDone()
                    }
                },
            )
        }

        // ── "Tools" panel — rounded surface with a header showing the active tool ──
        val activeToolName = when (tool) {
            Tool.Select -> "Edit"
            Tool.Box -> "Rectangle"
            Tool.RRect -> "Round shape"
            Tool.Arrow -> "Arrow"
            Tool.Line -> "Line"
            Tool.Text -> "Text"
            Tool.TextBox -> "Text box"
            Tool.Size -> "Size label"
            Tool.Pen -> "Free pen"
            Tool.PenLine -> "Straight line"
            Tool.PenShape -> "Closed shape"
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color(0xF21C1C1E))
                .navigationBarsPadding(),
        ) {
            // Header: TOOLS label + the active tool name on the right.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TOOLS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.5f), letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(50)).background(AppYellow.copy(0.18f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(activeToolName, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = AppYellow)
                }
            }
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolBtn(RgsIcons.Move, "Edit", tool == Tool.Select) { tool = Tool.Select }
            // ── Shapes sub-menu: Rectangle / Round / Arrow / Line ──
            val shapeTools = listOf(Tool.Box, Tool.RRect, Tool.Arrow, Tool.Line)
            val shapeIcon = when (tool) {
                Tool.RRect -> RgsIcons.RoundShape
                Tool.Arrow -> RgsIcons.ArrowLine
                Tool.Line -> RgsIcons.LineH
                else -> RgsIcons.Rectangle
            }
            Box {
                ToolBtn(shapeIcon, "Shapes", tool in shapeTools) { shapesMenuOpen = true }
                androidx.compose.material3.DropdownMenu(
                    expanded = shapesMenuOpen,
                    onDismissRequest = { shapesMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A)),
                ) {
                    ShapeMenuRow(RgsIcons.Rectangle, "Rectangle", tool == Tool.Box) { tool = Tool.Box; shapesMenuOpen = false }
                    ShapeMenuRow(RgsIcons.RoundShape, "Round shape", tool == Tool.RRect) { tool = Tool.RRect; shapesMenuOpen = false }
                    ShapeMenuRow(RgsIcons.ArrowLine, "Arrow", tool == Tool.Arrow) { tool = Tool.Arrow; shapesMenuOpen = false }
                    ShapeMenuRow(RgsIcons.LineH, "Line", tool == Tool.Line) { tool = Tool.Line; shapesMenuOpen = false }
                }
            }
            // ── Text sub-menu: Text / Text box / Size label ──
            val textTools = listOf(Tool.Text, Tool.TextBox, Tool.Size)
            val textIcon = when (tool) {
                Tool.TextBox -> RgsIcons.TextBoxTool
                Tool.Size -> RgsIcons.Ruler
                else -> RgsIcons.TextTool
            }
            Box {
                ToolBtn(textIcon, "Text", tool in textTools) { textMenuOpen = true }
                androidx.compose.material3.DropdownMenu(
                    expanded = textMenuOpen,
                    onDismissRequest = { textMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A)),
                ) {
                    ShapeMenuRow(RgsIcons.TextTool, "Text", tool == Tool.Text) { tool = Tool.Text; textMenuOpen = false }
                    ShapeMenuRow(RgsIcons.TextBoxTool, "Text box", tool == Tool.TextBox) { tool = Tool.TextBox; textMenuOpen = false }
                    ShapeMenuRow(RgsIcons.Ruler, "Size label", tool == Tool.Size) { tool = Tool.Size; textMenuOpen = false }
                }
            }
            // ── Pen sub-menu: Free pen / Straight line ──
            val penTools = listOf(Tool.Pen, Tool.PenLine)
            val penIcon = when (tool) {
                Tool.PenLine -> RgsIcons.PenLine
                else -> RgsIcons.Pen
            }
            Box {
                ToolBtn(penIcon, "Pen", tool in penTools) { penMenuOpen = true }
                androidx.compose.material3.DropdownMenu(
                    expanded = penMenuOpen,
                    onDismissRequest = { penMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A)),
                ) {
                    ShapeMenuRow(RgsIcons.Pen, "Free pen", tool == Tool.Pen) { tool = Tool.Pen; penMenuOpen = false }
                    ShapeMenuRow(RgsIcons.PenLine, "Straight line", tool == Tool.PenLine) { tool = Tool.PenLine; penMenuOpen = false }
                }
            }
            // ── Line-thickness (weight) selector ──
            Box {
                ToolBtn(RgsIcons.Weight, "Weight", false) { weightMenuOpen = true }
                androidx.compose.material3.DropdownMenu(
                    expanded = weightMenuOpen,
                    onDismissRequest = { weightMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A)).padding(vertical = 4.dp),
                ) {
                    WEIGHT_LEVELS.forEachIndexed { i, wt ->
                        val sel = wt == markWeight
                        Row(
                            Modifier.width(150.dp).clickable {
                                markWeight = wt
                                // Also apply to the currently-selected mark, if any.
                                selectedId?.let { id -> marks.firstOrNull { it.id == id }?.let { pushHistory(marks.toList()); replace(marks, it.copy(weight = wt)) } }
                                weightMenuOpen = false
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Preview line of this thickness.
                            Box(
                                Modifier.weight(1f).height((2 + i * 3).dp)
                                    .background(if (sel) AppYellow else Color.White, RoundedCornerShape(50)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(listOf("XS", "S", "M", "L", "XL")[i], fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AppYellow else Color.White.copy(0.7f))
                        }
                    }
                }
            }
            // Colour swatch — tap opens a 12-colour palette sub-menu.
            Box {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(50))
                        .background(Color(markColor))
                        .border(2.dp, Color.White, RoundedCornerShape(50))
                        .clickable { colorMenuOpen = true },
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = colorMenuOpen,
                    onDismissRequest = { colorMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A)).padding(8.dp),
                ) {
                    // 4 columns × 3 rows grid of colour swatches.
                    val cols = 4
                    MARK_PALETTE.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            row.forEach { hex ->
                                val sel = hex == markColor
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(50))
                                        .background(Color(hex))
                                        .border(if (sel) 3.dp else 1.dp, if (sel) AppYellow else Color.White.copy(0.5f), RoundedCornerShape(50))
                                        .clickable {
                                            markColor = hex
                                            // Also recolour the currently-selected mark, if any.
                                            selectedId?.let { id -> marks.firstOrNull { it.id == id }?.let { pushHistory(marks.toList()); replace(marks, it.copy(colorArgb = hex)) } }
                                            colorMenuOpen = false
                                        },
                                )
                            }
                        }
                    }
                }
            }
            // Delete selected
            ToolBtn(RgsIcons.Delete, "Del", false, tint = if (selectedId != null) StatusError else NeutralTextSoft) {
                selectedId?.let { id -> pushHistory(marks.toList()); marks.removeAll { it.id == id }; selectedId = null }
            }
        }
        }   // Tools Column
        }   // bottom stack Column
    }

    // ── Text / Size label dialog — dark themed with quick-label chips ──
    textDialog?.let { pending ->
        val isSize = pending.tool == Tool.Size
        fun commit() {
            val txt = textValue.trim()
            val existing = marks.firstOrNull { it.id == pending.id }
            pushHistory(marks.toList())
            if (existing != null) {
                if (txt.isEmpty() && existing.tool == Tool.Text) marks.removeAll { it.id == pending.id }
                else replace(marks, existing.copy(text = txt))
            } else if (txt.isNotEmpty()) {
                marks.add(pending.copy(text = txt)); selectedId = pending.id
            }
            textDialog = null
        }
        AlertDialog(
            onDismissRequest = { textDialog = null },
            containerColor = Color(0xFF1F1F22),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(if (isSize) "Size label" else "Add text", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (!isSize) {
                        Text("QUICK LABELS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.45f), letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QUICK_LABELS.forEach { lbl ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2C2C30))
                                        .clickable { textValue = lbl }.padding(horizontal = 12.dp, vertical = 7.dp),
                                ) { Text(lbl, fontSize = 12.sp, color = Color.White) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (isSize) {
                        Text("STANDARD SIZES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.45f), letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(24 to 48, 30 to 60, 36 to 72).forEach { (w, h) ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2C2C30))
                                        .clickable { textValue = "$w × $h in" }.padding(horizontal = 12.dp, vertical = 7.dp),
                                ) { Text("$w × $h", fontSize = 12.sp, color = Color.White) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = textValue, onValueChange = { textValue = it },
                        placeholder = { Text(if (isSize) "e.g. 4 ft × 3 ft" else "Type or pick a label", color = Color.White.copy(0.4f)) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppYellow, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = AppYellow, unfocusedBorderColor = Color.White.copy(0.3f),
                        ),
                    )
                }
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(AppYellow).clickable { commit() }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) { Text("Add", fontWeight = FontWeight.Bold, color = Color.Black) }
            },
            dismissButton = {
                Text("Cancel", color = Color.White.copy(0.7f), modifier = Modifier.clickable { textDialog = null }.padding(8.dp))
            },
        )
    }
}

@Composable
private fun TopAction(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(21.dp)) }
}

@Composable
private fun ToolBtn(icon: ImageVector, label: String, active: Boolean, tint: Color = Color.White, onClick: () -> Unit) {
    val accent = if (active) AppYellow else tint
    val haptics = LocalHapticFeedback.current
    // Subtle press/active scale for an interactive feel.
    val targetScale = if (active) 1.06f else 1f
    val sc by androidx.compose.animation.core.animateFloatAsState(targetScale, label = "toolScale")
    Column(
        Modifier.widthIn(min = 54.dp)
            .graphicsLayer(scaleX = sc, scaleY = sc)
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) AppYellow.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) AppYellow else accent.copy(alpha = 0.85f), maxLines = 1)
    }
}

// ── Brightness / contrast adjust pane ──
@Composable
private fun AdjustPane(
    brightness: Float, contrast: Float,
    onBrightness: (Float) -> Unit, onContrast: (Float) -> Unit,
    onReset: () -> Unit, onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xF2222226)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RgsIcons.Adjust, null, tint = AppYellow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Adjust", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Text("Reset", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(0.6f), modifier = Modifier.clickable { onReset() }.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        AdjustSlider("Brightness", brightness, onBrightness)
        AdjustSlider("Contrast", contrast, onContrast)
        Spacer(Modifier.height(8.dp))
        PaneBtn("Done", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onDone)
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.5.sp, color = Color.White.copy(0.8f), modifier = Modifier.width(76.dp))
        RgsSlider(value = value, onValueChange = onChange, valueRange = -100f..100f, modifier = Modifier.weight(1f))
        Text("${value.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppYellow, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// Shared slider — one consistent app-themed look (yellow accent) for every adjust/fill/rotate control.
@Composable
private fun RgsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange,
        modifier = modifier,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = AppYellow,
            activeTrackColor = AppYellow,
            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
        ),
    )
}

// ── Single unified pane to edit a selected rectangle (corners + rotation) ──
@Composable
private fun RectanglePropertiesPane(
    rotation: Float,
    fillAlpha: Float,
    fillColor: Long,
    cornerMode: CornerMode,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCornerMode: (CornerMode) -> Unit,
    onRotation: (Float) -> Unit,
    onFill: (Float) -> Unit,
    onFillColor: (Long) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
) {
    var fillColorMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Color(0xF21A1A1A)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // ── Header: shape name + degree ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RgsIcons.Rectangle, null, tint = AppYellow, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Shape", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (cornerMode == CornerMode.Single) {
                val degInt = rotation.toInt()
                Spacer(Modifier.width(6.dp))
                Text("${if (degInt > 0) "+" else ""}$degInt°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellow)
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── One action line: Undo · Redo · Reset · Apply · Save ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaneIconBtn(RgsIcons.Undo, enabled = canUndo, onClick = onUndo)
            Spacer(Modifier.width(4.dp))
            PaneIconBtn(RgsIcons.Redo, enabled = canRedo, onClick = onRedo)
            Spacer(Modifier.weight(1f))
            PaneBtn("Reset", filled = false, onClick = onReset)
            Spacer(Modifier.width(6.dp))
            PaneBtn("Apply", filled = false, onClick = onApply)
            Spacer(Modifier.width(6.dp))
            PaneBtn("Save", filled = true, onClick = onSave)
        }
        Spacer(Modifier.height(10.dp))

        // Mode toggle: Corners (reshape + fill) / Rotate (degree only)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegBtn("Corners · reshape", cornerMode == CornerMode.Dual, Modifier.weight(1f)) { onCornerMode(CornerMode.Dual) }
            SegBtn("Rotate · degree", cornerMode == CornerMode.Single, Modifier.weight(1f)) { onCornerMode(CornerMode.Single) }
        }
        Spacer(Modifier.height(8.dp))

        if (cornerMode == CornerMode.Dual) {
            // Fill — own colour swatch + opacity (shown only here to keep Rotate clean).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fill", fontSize = 11.5.sp, color = Color.White.copy(0.8f), modifier = Modifier.width(30.dp))
                Box {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                            .background(Color(fillColor))
                            .border(2.dp, Color.White.copy(0.7f), RoundedCornerShape(7.dp))
                            .clickable { fillColorMenu = true },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = fillColorMenu, onDismissRequest = { fillColorMenu = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A)).padding(8.dp),
                    ) {
                        MARK_PALETTE.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                row.forEach { hex ->
                                    Box(
                                        Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color(hex))
                                            .border(if (hex == fillColor) 3.dp else 1.dp, if (hex == fillColor) AppYellow else Color.White.copy(0.5f), RoundedCornerShape(50))
                                            .clickable { onFillColor(hex); fillColorMenu = false },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                RgsSlider(value = fillAlpha, onValueChange = onFill, valueRange = 0f..0.8f, modifier = Modifier.weight(1f))
                Text("${(fillAlpha / 0.8f * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppYellow, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        } else {
            // Rotate: only the centred degree slider — drag left = anticlockwise, right = clockwise.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
                        .clickable { onRotation((rotation - 1f).coerceIn(-180f, 180f)) },
                    contentAlignment = Alignment.Center,
                ) { Text("−", fontSize = 18.sp, color = Color.White) }
                RgsSlider(
                    value = rotation, onValueChange = { onRotation(it) }, valueRange = -180f..180f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
                        .clickable { onRotation((rotation + 1f).coerceIn(-180f, 180f)) },
                    contentAlignment = Alignment.Center,
                ) { Text("+", fontSize = 16.sp, color = Color.White) }
            }
        }
    }
}

// One row inside the Shapes sub-menu.
@Composable
private fun ShapeMenuRow(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) AppYellow else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) AppYellow else Color.White)
    }
}

@Composable
private fun SegBtn(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) AppYellow else Color(0xFF2A2A2A))
            .clickable { onClick() }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.Black else Color.White.copy(0.8f)) }
}

// Compact icon button used inside the properties pane action line (undo / redo).
@Composable
private fun PaneIconBtn(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Color.White else Color(0x44FFFFFF), modifier = Modifier.size(18.dp)) }
}

@Composable
private fun PaneBtn(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (filled) AppYellow else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(9.dp)))
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else Color.White) }
}

// android.graphics.Path version (used by flatten) — closed polygon, optional rounded corners.
private fun androidPolyPath(pts: List<Offset>, rounded: Boolean): android.graphics.Path {
    val path = android.graphics.Path()
    if (pts.size < 3) return path
    if (!rounded) {
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        return path
    }
    val n = pts.size
    for (i in 0 until n) {
        val curr = pts[i]; val prev = pts[(i - 1 + n) % n]; val next = pts[(i + 1) % n]
        val lenPrev = (prev - curr).getDistance().coerceAtLeast(1f)
        val lenNext = (next - curr).getDistance().coerceAtLeast(1f)
        // Corner radius capped at 28px so larger boxes don't look over-rounded.
        val r = (minOf(lenPrev, lenNext) * 0.12f).coerceAtMost(28f)
        val a = curr + (prev - curr) / lenPrev * r
        val b = curr + (next - curr) / lenNext * r
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadTo(curr.x, curr.y, b.x, b.y)
    }
    path.close()
    return path
}

// Build a closed polygon Path through the points; optionally round each corner.
private fun polyPath(pts: List<Offset>, rounded: Boolean): Path {
    val path = Path()
    if (pts.size < 3) return path
    if (!rounded) {
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        return path
    }
    val n = pts.size
    for (i in 0 until n) {
        val curr = pts[i]
        val prev = pts[(i - 1 + n) % n]
        val next = pts[(i + 1) % n]
        val lenPrev = (prev - curr).getDistance().coerceAtLeast(1f)
        val lenNext = (next - curr).getDistance().coerceAtLeast(1f)
        // Subtle rounding (12% of the shorter edge, capped) — not a big curve.
        val r = (minOf(lenPrev, lenNext) * 0.12f).coerceAtMost(40f)
        val a = curr + (prev - curr) / lenPrev * r
        val b = curr + (next - curr) / lenNext * r
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticBezierTo(curr.x, curr.y, b.x, b.y)
    }
    path.close()
    return path
}

// Brightness/contrast as a 4x5 colour-matrix array (shared by live preview + flatten).
// brightness/contrast are -100..100; 0 = no change.
private fun adjustMatrixArray(brightness: Float, contrast: Float): FloatArray {
    val cScale = 1f + contrast / 100f                      // 0..2
    val translate = 127.5f * (1f - cScale) + brightness / 100f * 127f
    return floatArrayOf(
        cScale, 0f, 0f, 0f, translate,
        0f, cScale, 0f, 0f, translate,
        0f, 0f, cScale, 0f, translate,
        0f, 0f, 0f, 1f, 0f,
    )
}

// Compose ColorFilter for the live canvas (null when no adjustment, so it's free).
private fun adjustFilter(brightness: Float, contrast: Float): androidx.compose.ui.graphics.ColorFilter? {
    if (brightness == 0f && contrast == 0f) return null
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(adjustMatrixArray(brightness, contrast)),
    )
}

// ── Drawing marks on the live Compose canvas ──
// Shadow tint = the mark's own colour pushed toward black — a dark, on-brand halo.
private fun shadowOf(col: Color): Color = Color(col.red * 0.22f, col.green * 0.22f, col.blue * 0.22f, 0.6f)

private fun DrawScope.drawMark(m: Mark, selected: Boolean, toScreen: (Offset) -> Offset) {
    val col = Color(m.colorArgb)
    val sw = 4f * m.weight
    when (m.tool) {
        Tool.Box, Tool.Size, Tool.RRect, Tool.TextBox -> {
            // Vector box: an arbitrary quadrilateral through the 4 free corner points.
            val scr = m.pts.map { toScreen(it) }
            val center = centroid(scr)
            // Bounding box of the corners (text anchors + the round shape's ellipse).
            val minX = scr.minOf { it.x }; val minY = scr.minOf { it.y }
            val maxX = scr.maxOf { it.x }; val maxY = scr.maxOf { it.y }
            rotate(m.rotation, pivot = center) {
                if (m.tool == Tool.RRect) {
                    // Round shape: ellipse fitting the bounding box.
                    val tl = Offset(minX, minY); val ovSize = Size(maxX - minX, maxY - minY)
                    if (m.fillAlpha > 0f) drawOval(Color(m.fillColorArgb).copy(alpha = m.fillAlpha), topLeft = tl, size = ovSize)
                    drawOval(shadowOf(col), topLeft = tl, size = ovSize, style = Stroke(width = sw + 3f))
                    drawOval(col, topLeft = tl, size = ovSize, style = Stroke(width = sw))
                } else {
                    val path = polyPath(scr, rounded = false)
                    if (m.fillAlpha > 0f) drawPath(path, Color(m.fillColorArgb).copy(alpha = m.fillAlpha))
                    drawPath(path, shadowOf(col), style = Stroke(width = sw + 3f))
                    drawPath(path, col, style = Stroke(width = sw))
                }
                if (m.tool == Tool.Size && m.text.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        m.text, minX, minY - 8f,
                        android.graphics.Paint().apply { color = col.toArgb(); textSize = 34f * m.weight; isAntiAlias = true; isFakeBoldText = true; setShadowLayer(4f, 0f, 2f, shadowOf(col).toArgb()) },
                    )
                }
                if (m.tool == Tool.TextBox && m.text.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        m.text, minX + 8f, (minY + maxY) / 2f + 12f,
                        android.graphics.Paint().apply { color = col.toArgb(); textSize = 34f * m.weight; isAntiAlias = true; isFakeBoldText = true; setShadowLayer(4f, 0f, 2f, shadowOf(col).toArgb()) },
                    )
                }
                // Handles when selected — 4 corner dots + a centre "move" grip.
                if (selected) {
                    scr.forEach {
                        drawCircle(AppYellow, radius = 14f, center = it)
                        drawCircle(Color.Black, radius = 14f, center = it, style = Stroke(2.5f))
                    }
                    // Centre move handle: dark disc, yellow ring + a 4-way move cross.
                    drawCircle(Color(0xCC101010), radius = 26f, center = center)
                    drawCircle(AppYellow, radius = 26f, center = center, style = Stroke(3f))
                    val arm = 11f
                    drawLine(AppYellow, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 3.5f)
                    drawLine(AppYellow, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 3.5f)
                }
            }
        }
        Tool.Arrow, Tool.Line -> {
            val a = toScreen(m.pts[0]); val b = toScreen(m.pts[1])
            drawLine(shadowOf(col), a, b, strokeWidth = sw + 3f)
            drawLine(col, a, b, strokeWidth = sw)
            if (m.tool == Tool.Arrow) {
                // Two barbs from the tip, pointing BACK along the line (± a spread angle).
                val ang = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                val headLen = (sw * 4.5f).coerceIn(22f, 70f)
                val spread = 0.5   // ~28°
                for (sgn in listOf(1.0, -1.0)) {
                    val dir = ang + Math.PI + sgn * spread
                    val tip = Offset((b.x + headLen * kotlin.math.cos(dir)).toFloat(), (b.y + headLen * kotlin.math.sin(dir)).toFloat())
                    drawLine(shadowOf(col), b, tip, strokeWidth = sw + 3f)
                    drawLine(col, b, tip, strokeWidth = sw)
                }
            }
        }
        Tool.Pen, Tool.PenLine, Tool.PenShape -> {
            val pts = m.pts.map { toScreen(it) }
            // Two passes: dark halo behind, colour on top.
            listOf(shadowOf(col) to (sw + 3f), col to sw).forEach { (c, w) ->
                if (m.tool == Tool.PenLine && pts.size >= 2) {
                    drawLine(c, pts.first(), pts.last(), strokeWidth = w)
                } else {
                    for (i in 1 until pts.size) drawLine(c, pts[i - 1], pts[i], strokeWidth = w)
                    if (m.tool == Tool.PenShape && pts.size >= 3) drawLine(c, pts.last(), pts.first(), strokeWidth = w)
                }
            }
        }
        Tool.Text -> {
            val p = toScreen(m.pts[0])
            drawContext.canvas.nativeCanvas.drawText(
                m.text.ifEmpty { "…" }, p.x, p.y,
                android.graphics.Paint().apply {
                    color = col.toArgb(); textSize = 40f * m.weight; isAntiAlias = true; isFakeBoldText = true
                    setShadowLayer(4f, 0f, 2f, shadowOf(col).toArgb())
                },
            )
        }
        Tool.Select -> {}
    }
    // Arrow/Line endpoint handles (rectangles draw their own 4 corner handles above).
    if (selected && (m.tool == Tool.Arrow || m.tool == Tool.Line)) {
        m.pts.forEach { drawCircle(AppYellow, radius = 9f, center = toScreen(it)) }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun replace(list: MutableList<Mark>, m: Mark) {
    val i = list.indexOfFirst { it.id == m.id }
    if (i >= 0) list[i] = m
}

private fun hitTest(marks: List<Mark>, screen: Offset, toScreen: (Offset) -> Offset): Mark? {
    for (m in marks.asReversed()) {
        when (m.tool) {
            Tool.Box, Tool.Size, Tool.RRect, Tool.TextBox -> {
                // Bounding box of the 4 rotated corners (with padding) — good enough for selection.
                val corners = rectCornersScreen(m, toScreen)
                val l = corners.minOf { it.x } - 20; val r = corners.maxOf { it.x } + 20
                val t = corners.minOf { it.y } - 20; val bo = corners.maxOf { it.y } + 20
                if (screen.x in l..r && screen.y in t..bo) return m
            }
            Tool.Arrow, Tool.Line -> { if (m.pts.any { (toScreen(it) - screen).getDistance() < 40 }) return m }
            Tool.Text -> { if ((toScreen(m.pts[0]) - screen).getDistance() < 60) return m }
            Tool.Pen, Tool.PenLine, Tool.PenShape -> { if (m.pts.any { (toScreen(it) - screen).getDistance() < 30 }) return m }
            Tool.Select -> {}
        }
    }
    return null
}

// The 4 corner positions of a vector box in screen space (already rotated around the centroid).
private fun rectCornersScreen(m: Mark, toScreen: (Offset) -> Offset): List<Offset> {
    val scr = m.pts.map { toScreen(it) }
    val c = centroid(scr)
    return scr.map { rotateAround(it, c, m.rotation) }
}

private fun cornerHit(m: Mark, screen: Offset, toScreen: (Offset) -> Offset): Int {
    if (m.tool.isRect()) {
        rectCornersScreen(m, toScreen).forEachIndexed { i, c -> if ((c - screen).getDistance() < 40) return i }
        return -1
    }
    if (m.tool == Tool.Arrow || m.tool == Tool.Line) {
        m.pts.forEachIndexed { i, p -> if ((toScreen(p) - screen).getDistance() < 36) return i }
    }
    return -1
}

// Crash-safe JPEG write: temp → fsync → atomic rename, so a mid-write kill never leaves a
// truncated/zero photo — the original survives until the new file is fully written.
private fun writeJpegAtomic(path: String, bmp: Bitmap, quality: Int = 95) {
    val tmp = File("$path.tmp")
    FileOutputStream(tmp).use { fos ->
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        fos.flush()
        runCatching { fos.fd.sync() }   // best-effort durability
    }
    val dest = File(path)
    if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
}

// ── Flatten marks onto the full-res bitmap, overwrite the file, optionally share ──
private suspend fun flatten(
    path: String?, marks: List<Mark>, share: Boolean, context: android.content.Context,
    brightness: Float = 0f, contrast: Float = 0f, saveCleanCopy: Boolean = false,
): Boolean =
    withContext(Dispatchers.IO) {
        path ?: return@withContext false
        try {
            val src = BitmapFactory.decodeFile(path) ?: return@withContext false
            // Optionally keep the un-marked version in the gallery before the marks are baked in.
            if (saveCleanCopy && marks.isNotEmpty()) saveCleanCopyToGallery(context, File(path), src)
            val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            // Bake brightness/contrast into the base image (no-op when both are 0).
            val basePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                if (brightness != 0f || contrast != 0f) {
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(adjustMatrixArray(brightness, contrast)))
                }
            }
            c.drawBitmap(src, 0f, 0f, basePaint)
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            val unit = minOf(w, h)
            val baseSw = (unit * 0.006f).coerceAtLeast(3f)
            fun px(f: Offset) = Offset(f.x * w, f.y * h)
            fun paint(argb: Long, weight: Float) = android.graphics.Paint().apply {
                color = argb.toInt()
                strokeWidth = baseSw * weight; style = android.graphics.Paint.Style.STROKE; isAntiAlias = true
                strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
            }
            // Subtle drop shadow tinted with each mark's own colour (pushed toward black).
            val shadowR = baseSw * 0.6f; val shadowD = baseSw * 0.25f
            fun shadowArgbOf(argb: Long): Int {
                val base = argb.toInt()
                return android.graphics.Color.argb(
                    165,
                    (android.graphics.Color.red(base) * 0.22f).toInt(),
                    (android.graphics.Color.green(base) * 0.22f).toInt(),
                    (android.graphics.Color.blue(base) * 0.22f).toInt(),
                )
            }
            marks.forEach { m ->
                val sw = baseSw * m.weight
                val sArgb = shadowArgbOf(m.colorArgb)
                val p = paint(m.colorArgb, m.weight).apply { setShadowLayer(shadowR, shadowD, shadowD, sArgb) }
                val tp = android.graphics.Paint(p).apply { style = android.graphics.Paint.Style.FILL; textSize = unit * 0.04f * m.weight; isFakeBoldText = true; setShadowLayer(shadowR, shadowD, shadowD, sArgb) }
                when (m.tool) {
                    Tool.Box, Tool.Size, Tool.RRect, Tool.TextBox -> {
                        // Vector box: arbitrary quadrilateral through the 4 corners.
                        val corners = m.pts.map { px(it) }
                        val ctr = centroid(corners)
                        val minX = corners.minOf { it.x }; val minY = corners.minOf { it.y }
                        val maxX = corners.maxOf { it.x }; val maxY = corners.maxOf { it.y }
                        val saved = c.save()
                        if (m.rotation != 0f) c.rotate(m.rotation, ctr.x, ctr.y)
                        val fillPaint = if (m.fillAlpha > 0f) android.graphics.Paint().apply {
                            val base = m.fillColorArgb.toInt()
                            color = android.graphics.Color.argb((m.fillAlpha * 255).toInt(), android.graphics.Color.red(base), android.graphics.Color.green(base), android.graphics.Color.blue(base))
                            style = android.graphics.Paint.Style.FILL; isAntiAlias = true
                        } else null
                        if (m.tool == Tool.RRect) {
                            // Round shape: ellipse in the bounding box.
                            val oval = android.graphics.RectF(minX, minY, maxX, maxY)
                            fillPaint?.let { c.drawOval(oval, it) }
                            c.drawOval(oval, p)
                        } else {
                            val path = androidPolyPath(corners, false)
                            fillPaint?.let { c.drawPath(path, it) }
                            c.drawPath(path, p)
                        }
                        if (m.tool == Tool.Size && m.text.isNotEmpty()) c.drawText(m.text, minX, minY - sw * 2, tp)
                        if (m.tool == Tool.TextBox && m.text.isNotEmpty()) c.drawText(m.text, minX + sw * 2, (minY + maxY) / 2f + tp.textSize / 3, tp)
                        c.restoreToCount(saved)
                    }
                    Tool.Arrow, Tool.Line -> {
                        val a = px(m.pts[0]); val b = px(m.pts[1])
                        c.drawLine(a.x, a.y, b.x, b.y, p)
                        if (m.tool == Tool.Arrow) {
                            // Barbs from the tip, pointing back along the line.
                            val ang = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                            val headLen = (sw * 4.5f).coerceAtLeast(unit * 0.03f)
                            val spread = 0.5
                            for (sgn in listOf(1.0, -1.0)) {
                                val dir = ang + Math.PI + sgn * spread
                                c.drawLine(b.x, b.y, (b.x + headLen * kotlin.math.cos(dir)).toFloat(), (b.y + headLen * kotlin.math.sin(dir)).toFloat(), p)
                            }
                        }
                    }
                    Tool.Pen, Tool.PenLine, Tool.PenShape -> {
                        val pts = m.pts.map { px(it) }
                        if (m.tool == Tool.PenLine && pts.size >= 2) {
                            c.drawLine(pts.first().x, pts.first().y, pts.last().x, pts.last().y, p)
                        } else {
                            for (i in 1 until pts.size) c.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, p)
                            if (m.tool == Tool.PenShape && pts.size >= 3) c.drawLine(pts.last().x, pts.last().y, pts.first().x, pts.first().y, p)
                        }
                    }
                    Tool.Text -> { if (m.text.isNotEmpty()) c.drawText(m.text, px(m.pts[0]).x, px(m.pts[0]).y, tp) }
                    Tool.Select -> {}
                }
            }
            writeJpegAtomic(path, bmp)
            mirrorEditToGallery(context, File(path))   // keep the phone-gallery copy in sync with the edit
            if (share) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/*"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) { context.startActivity(android.content.Intent.createChooser(intent, "Share marked photo")) }
            }
            true
        } catch (_: Throwable) { false }
    }

/**
 * After an edit overwrites the app-private photo, push the same bytes into its device-gallery copy
 * so the phone gallery shows the marked-up version (not the original). Matches the gallery entry by
 * filename (the same name written at capture); inserts a fresh entry if none exists. Best-effort.
 */
private fun mirrorEditToGallery(context: android.content.Context, file: File) {
    try {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        var uri: android.net.Uri? = null
        resolver.query(
            collection, arrayOf(android.provider.MediaStore.Images.Media._ID),
            "${android.provider.MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(file.name), null,
        )?.use { cur -> if (cur.moveToFirst()) uri = android.content.ContentUris.withAppendedId(collection, cur.getLong(0)) }
        if (uri != null) {
            resolver.openOutputStream(uri!!, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
        } else {
            val subFolder = file.parentFile?.name ?: "General"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        "${android.os.Environment.DIRECTORY_PICTURES}/ReceeGpsStamp/$subFolder",
                    )
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val newUri = resolver.insert(collection, values)
            if (newUri != null) {
                resolver.openOutputStream(newUri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.clear(); values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(newUri, values, null, null)
                }
            }
        }
    } catch (_: Throwable) { }
}

/**
 * Saves the un-marked image as a separate "<name>_clean.jpg" entry in the device gallery (gallery-only,
 * like the no-watermark copy). Overwrites that clean entry if it already exists. Best-effort.
 */
private fun saveCleanCopyToGallery(context: android.content.Context, file: File, src: Bitmap) {
    try {
        val name = "${file.nameWithoutExtension}_clean.jpg"
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        var uri: android.net.Uri? = null
        resolver.query(
            collection, arrayOf(android.provider.MediaStore.Images.Media._ID),
            "${android.provider.MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(name), null,
        )?.use { cur -> if (cur.moveToFirst()) uri = android.content.ContentUris.withAppendedId(collection, cur.getLong(0)) }
        val inserting = uri == null
        if (inserting) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        "${android.os.Environment.DIRECTORY_PICTURES}/ReceeGpsStamp/${file.parentFile?.name ?: "General"}",
                    )
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            uri = resolver.insert(collection, values)
        }
        val u = uri ?: return
        resolver.openOutputStream(u, "wt")?.use { src.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (inserting && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val v = android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(u, v, null, null)
        }
    } catch (_: Throwable) { }
}

private fun Offset.getDistance(): Float = hypot(x, y)
