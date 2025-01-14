package ru.tech.imageresizershrinker.presentation.draw_screen.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.PorterDuff
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.smarttoolfactory.gesture.MotionEvent
import com.smarttoolfactory.gesture.pointerMotionEvents
import com.smarttoolfactory.image.util.update
import com.smarttoolfactory.image.zoom.animatedZoom
import com.smarttoolfactory.image.zoom.rememberAnimatedZoomState
import kotlinx.coroutines.launch
import ru.tech.imageresizershrinker.presentation.erase_background_screen.components.PathPaint
import ru.tech.imageresizershrinker.presentation.erase_background_screen.components.transparencyChecker
import ru.tech.imageresizershrinker.presentation.root.theme.outlineVariant


@Composable
fun BitmapDrawer(
    imageBitmap: ImageBitmap,
    paths: List<PathPaint>,
    blurRadius: Float,
    onAddPath: (PathPaint) -> Unit,
    strokeWidth: Float,
    isEraserOn: Boolean,
    drawMode: DrawMode,
    modifier: Modifier,
    onDraw: (Bitmap) -> Unit,
    backgroundColor: Color,
    zoomEnabled: Boolean,
    drawColor: Color
) {
    val zoomState = rememberAnimatedZoomState(maxZoom = 30f)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (zoomEnabled) {
                    Modifier.animatedZoom(animatedZoomState = zoomState)
                } else {
                    Modifier.graphicsLayer {
                        update(zoomState)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(modifier) {

            var motionEvent by remember { mutableStateOf(MotionEvent.Idle) }
            // This is our motion event we get from touch motion
            var currentPosition by remember { mutableStateOf(Offset.Unspecified) }
            // This is previous motion event before next touch is saved into this current position
            var previousPosition by remember { mutableStateOf(Offset.Unspecified) }

            val imageWidth = constraints.maxWidth
            val imageHeight = constraints.maxHeight


            val drawImageBitmap = remember {
                Bitmap.createScaledBitmap(
                    imageBitmap.asAndroidBitmap(),
                    imageWidth,
                    imageHeight,
                    false
                ).apply {
                    val canvas = android.graphics.Canvas(this)
                    val paint = android.graphics.Paint().apply {
                        color = backgroundColor.toArgb()
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }.asImageBitmap()
            }

            val drawBitmap: ImageBitmap = remember {
                Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }

            SideEffect {
                onDraw(drawImageBitmap.overlay(drawBitmap).asAndroidBitmap())
            }

            val canvas: Canvas = remember {
                Canvas(drawBitmap)
            }

            val drawPaint = remember(strokeWidth, isEraserOn, drawColor, blurRadius, drawMode) {
                Paint().apply {
                    blendMode = if (!isEraserOn) blendMode else BlendMode.Clear
                    style = PaintingStyle.Stroke
                    strokeCap =
                        if (drawMode is DrawMode.Highlighter) StrokeCap.Square else StrokeCap.Round
                    color = drawColor
                    alpha = drawColor.alpha
                    this.strokeWidth = strokeWidth
                    strokeJoin = StrokeJoin.Round
                    isAntiAlias = true
                }.asFrameworkPaint().apply {
                    if (drawMode is DrawMode.Neon && !isEraserOn) {
                        this.color = Color.White.toArgb()
                        setShadowLayer(
                            blurRadius,
                            0f,
                            0f,
                            drawColor
                                .copy(alpha = .8f)
                                .toArgb()
                        )
                    } else if (blurRadius > 0f) {
                        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                }
            }

            var drawPath by remember { mutableStateOf(Path()) }

            canvas.apply {
                when (motionEvent) {

                    MotionEvent.Down -> {
                        drawPath.moveTo(currentPosition.x, currentPosition.y)
                        previousPosition = currentPosition
                    }

                    MotionEvent.Move -> {
                        drawPath.quadraticBezierTo(
                            previousPosition.x,
                            previousPosition.y,
                            (previousPosition.x + currentPosition.x) / 2,
                            (previousPosition.y + currentPosition.y) / 2
                        )
                        previousPosition = currentPosition
                    }

                    MotionEvent.Up -> {
                        drawPath.lineTo(currentPosition.x, currentPosition.y)
                        currentPosition = Offset.Unspecified
                        previousPosition = currentPosition
                        motionEvent = MotionEvent.Idle
                        onAddPath(
                            PathPaint(
                                path = drawPath,
                                strokeWidth = strokeWidth,
                                blurRadius = blurRadius,
                                drawColor = drawColor,
                                isErasing = isEraserOn,
                                drawMode = drawMode
                            )
                        )
                        scope.launch {
                            drawPath = Path()
                        }
                    }

                    else -> Unit
                }

                with(canvas.nativeCanvas) {
                    drawColor(Color.Transparent.toArgb(), PorterDuff.Mode.CLEAR)
                    drawColor(backgroundColor.toArgb())


                    paths.forEach { (path, stroke, radius, drawColor, isErasing, effect) ->
                        this.drawPath(
                            path.asAndroidPath(),
                            Paint().apply {
                                blendMode = if (!isErasing) blendMode else BlendMode.Clear
                                style = PaintingStyle.Stroke
                                strokeCap =
                                    if (effect is DrawMode.Highlighter) StrokeCap.Square else StrokeCap.Round
                                this.strokeWidth = stroke
                                strokeJoin = StrokeJoin.Round
                                isAntiAlias = true
                                color = drawColor
                                alpha = drawColor.alpha
                            }.asFrameworkPaint().apply {
                                if (effect is DrawMode.Neon && !isErasing) {
                                    this.color = Color.White.toArgb()
                                    setShadowLayer(
                                        radius,
                                        0f,
                                        0f,
                                        drawColor
                                            .copy(alpha = .8f)
                                            .toArgb()
                                    )
                                } else if (radius > 0f) {
                                    maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                                }
                            }
                        )
                    }

                    this.drawPath(
                        drawPath.asAndroidPath(),
                        drawPaint
                    )
                }
            }

            val canvasModifier = Modifier.pointerMotionEvents(
                onDown = { pointerInputChange ->
                    motionEvent = MotionEvent.Down
                    currentPosition = pointerInputChange.position
                    pointerInputChange.consume()
                },
                onMove = { pointerInputChange ->
                    motionEvent = MotionEvent.Move
                    currentPosition = pointerInputChange.position
                    pointerInputChange.consume()
                },
                onUp = { pointerInputChange ->
                    motionEvent = MotionEvent.Up
                    pointerInputChange.consume()
                },
                delayAfterDownInMillis = 20
            )

            Image(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (!zoomEnabled) canvasModifier
                        else Modifier
                    )
                    .clip(RoundedCornerShape(2.dp))
                    .transparencyChecker()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant(),
                        RoundedCornerShape(2.dp)
                    ),
                bitmap = drawImageBitmap.overlay(drawBitmap),
                contentDescription = null,
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

private fun ImageBitmap.overlay(overlay: ImageBitmap): ImageBitmap {
    val image = this.asAndroidBitmap()
    val finalBitmap = Bitmap.createBitmap(image.width, image.height, image.config)
    val canvas = android.graphics.Canvas(finalBitmap)
    canvas.drawBitmap(image, Matrix(), null)
    canvas.drawBitmap(overlay.asAndroidBitmap(), 0f, 0f, null)
    return finalBitmap.asImageBitmap()
}