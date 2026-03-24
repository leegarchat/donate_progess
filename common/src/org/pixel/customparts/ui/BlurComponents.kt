package org.pixel.customparts.ui

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.lang.reflect.Method

/*
 * Shared Graphics Layer Blur Implementation
 * Moved from MainActivity.kt
 */

@Stable
data class GraphicsLayerRecordingState(
    val graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    val region: MutableState<Region>,
) {
    sealed class Region {
        data class Rectangle(val rect: Rect) : Region()
        data class Circle(val center: Offset, val size: Size) : Region()
    }

    fun setRectRegion(rect: Rect) {
        this.region.value = Region.Rectangle(rect = rect)
    }

    fun setCircleRegion(center: Offset, size: Size) {
        this.region.value = Region.Circle(center = center, size = size)
    }
}

@Composable
fun rememberGraphicsLayerRecordingState(
    regionState: MutableState<GraphicsLayerRecordingState.Region> = rememberGraphicsLayerRecordingRegion(),
): GraphicsLayerRecordingState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember {
        GraphicsLayerRecordingState(graphicsLayer = graphicsLayer, region = regionState)
    }
}

@Composable
private fun rememberGraphicsLayerRecordingRegion(): MutableState<GraphicsLayerRecordingState.Region> {
    return remember {
        mutableStateOf(GraphicsLayerRecordingState.Region.Rectangle(rect = Rect.Zero))
    }
}

fun Modifier.recordLayer(
    state: GraphicsLayerRecordingState,
): Modifier {
    return this.drawWithContent {
        // We capture the content into the GraphicsLayer
        when (val region = state.region.value) {
            is GraphicsLayerRecordingState.Region.Rectangle -> {
                recordRectangleLayer(graphicsLayer = state.graphicsLayer, rect = region.rect)
            }
            is GraphicsLayerRecordingState.Region.Circle -> {
                recordCircleLayer(
                    graphicsLayer = state.graphicsLayer,
                    center = region.center,
                    size = region.size
                )
            }
        }
        
        // And THEN we draw the content to the screen as usual.
        drawContent()
    }
}

private fun ContentDrawScope.recordRectangleLayer(
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    rect: Rect,
) {
    try {
        graphicsLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
             this@recordRectangleLayer.drawContent()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun ContentDrawScope.recordCircleLayer(
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    center: Offset,
    size: Size,
) {
    val rect = Rect(offset = center, size = size)
    val path = Path().apply { addOval(rect) }

    graphicsLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
        translate(left = -rect.left, top = -rect.top) {
            clipPath(path = path, clipOp = ClipOp.Intersect) {
                this@recordCircleLayer.drawContent()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.renderLayer(
    state: GraphicsLayerRecordingState,
    renderEffect: RenderEffect,
): Modifier {
    return this.renderLayer(graphicsLayer = state.graphicsLayer, renderEffect = renderEffect)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.renderLayer(
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    renderEffect: RenderEffect,
): Modifier = composed {
    val renderNode = remember { RenderNode("PixelPartsBlurNode") }
    val canvasHolder = remember { CanvasHolder() }

    this.drawBehind {
        renderNode.setRenderEffect(renderEffect)
        
        renderNode.setPosition(0, 0, size.width.toInt(), size.height.toInt())

        drawIntoCanvas { canvas ->
            val recordingCanvas = renderNode.beginRecording()
            canvasHolder.drawInto(recordingCanvas) {
                val originalCanvas = drawContext.canvas
                drawContext.canvas = this@drawInto
                drawLayerCompat(graphicsLayer)
                drawContext.canvas = originalCanvas
            }
            renderNode.endRecording()
            canvas.nativeCanvas.drawRenderNode(renderNode)
        }
    }
}

private fun DrawScope.drawLayerCompat(
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
) {
    drawIntoCanvas { canvas ->
        invokeGraphicsLayerDrawReflectively(
            sourceLayer = graphicsLayer,
            destinationCanvas = canvas,
            parentLayer = null 
        )
    }
}

@Volatile
private var cachedGraphicsLayerDrawMethod: Method? = null

private fun invokeGraphicsLayerDrawReflectively(
    sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    destinationCanvas: androidx.compose.ui.graphics.Canvas,
    parentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?
) {
    val method = cachedGraphicsLayerDrawMethod ?: resolveGraphicsLayerDrawMethod(sourceLayer)
    if (method != null) {
        try {
            method.invoke(sourceLayer, destinationCanvas, parentLayer)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

private fun resolveGraphicsLayerDrawMethod(
    sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
): Method? {
    cachedGraphicsLayerDrawMethod?.let { return it }

    return try {
        val method = sourceLayer::class.java.declaredMethods.firstOrNull { candidate ->
            val params = candidate.parameterTypes
            params.size == 2 &&
                params[0].name.startsWith("androidx.compose.ui.graphics") &&
                params[1].name.startsWith("androidx.compose.ui.graphics.layer")
        }
        method?.isAccessible = true
        cachedGraphicsLayerDrawMethod = method
        method
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun TopBarBlurOverlay(
    modifier: Modifier = Modifier,
    topBarHeight: Dp,
    blurState: GraphicsLayerRecordingState,
    isScrolled: Boolean = false,
) {
    val tintAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "blurTintAlpha"
    )
    val isDark = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val bgColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .height(topBarHeight)
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val renderEffect = remember {
                RenderEffect.createBlurEffect(80f, 80f, Shader.TileMode.CLAMP)
            }

            // Layer 0: Opaque background — fully hides the original unblurred list underneath.
            // Uses the same surfaceContainer as the LazyColumn so there is zero visible seam.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
            )

            // Layer 1: Blurred captured content on top of opaque background
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        clip = true
                    }
                    .onGloballyPositioned { layoutCoordinates ->
                        blurState.setRectRegion(layoutCoordinates.boundsInParent())
                    }
                    .renderLayer(state = blurState, renderEffect = renderEffect)
            )

            // Layer 2: Surface veil — dims blurred content for frosted glass look
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor.copy(alpha = 0.0f))
            )
        }

        // Layer 4: Scroll-based tint gradient — enhances frost when content is under the bar
        if (tintAlpha > 0f) {
            // val tintColor = if (isDark) Color.Black else Color.White
            val tintColor = surfaceColor
            val multiplier_top = if (isDark) 1.0f else 1.0f
            val multiplier_down = if (isDark) 0.0f else 0.0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                tintColor.copy(alpha = multiplier_top * tintAlpha),
                                tintColor.copy(alpha = multiplier_top * 0.3f * tintAlpha),
                                tintColor.copy(alpha = multiplier_down * tintAlpha)
                            )
                        )
                    )
            )
        }
    }
}
