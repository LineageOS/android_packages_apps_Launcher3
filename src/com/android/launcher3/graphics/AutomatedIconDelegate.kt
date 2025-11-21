/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.core.animation.LinearInterpolator
import androidx.core.animation.ValueAnimator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import com.android.launcher3.R
import com.android.launcher3.Utilities.dpToPx
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawableDelegate
import com.android.launcher3.icons.GraphicsUtils.resizeToContentSize
import com.android.launcher3.icons.GraphicsUtils.transformed
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.ItemInfoWithIcon

class AutomatedIconDelegate(
    private val strokeWidthPx: Float,
    private val glowRadiusPx: Float,
    private val glowPadding: Int,
    private val outlineStartColor: Int,
    private val outlineMiddleColor: Int,
    private val outlineEndColor: Int,
    private val iconShape: IconShape,
    private val host: FastBitmapDrawable,
    private val parentDelegate: FastBitmapDrawableDelegate,
) : FastBitmapDrawableDelegate by parentDelegate {

    private val fixedDelegateBounds =
        Rect(0, 0, iconShape.pathSize, iconShape.pathSize).also {
            parentDelegate.onBoundsChange(it)
        }

    private val colors = intArrayOf(outlineStartColor, outlineMiddleColor, outlineEndColor)

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }

    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }

    private val platePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            alpha = (51).toInt() // 20% alpha
        }

    private val shaderMatrix = Matrix()

    private val glowNode =
        RenderNode("innerGlow").apply {
            val nodeWidth = iconShape.pathSize + (glowPadding * 2)
            val nodeHeight = iconShape.pathSize + (glowPadding * 2)
            setPosition(0, 0, nodeWidth, nodeHeight)
            val blurEffect =
                RenderEffect.createBlurEffect(glowRadiusPx, glowRadiusPx, Shader.TileMode.MIRROR)
            setRenderEffect(blurEffect)
        }

    private var currentRotation = 0f

    private val rotationAnimator =
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ROTATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()

            addUpdateListener { animator ->
                currentRotation = (animator as ValueAnimator).animatedValue as Float
                if (!host.isVisible || host.callback == null) {
                    animator.cancel()
                    return@addUpdateListener
                }
                host.invalidateSelf()
            }
        }

    init {
        updateShaders()
    }

    private fun updateShaders() {
        val center = iconShape.pathSize / 2f

        val shader =
            LinearGradient(
                0f,
                0f,
                iconShape.pathSize.toFloat(),
                iconShape.pathSize.toFloat(),
                colors,
                positions,
                Shader.TileMode.CLAMP,
            )
        val matrix = Matrix()
        matrix.setRotate(START_ANGLE, center, center)
        shader.setLocalMatrix(matrix)

        strokePaint.shader = shader
        glowPaint.shader = shader
    }

    override fun drawContent(
        info: BitmapInfo,
        iconShape: IconShape,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {

        canvas.resizeToContentSize(bounds, iconShape.pathSize.toFloat()) {
            drawPath(iconShape.path, platePaint)
            val center = iconShape.pathSize / 2f
            transformed {
                scale(SMALL_ICON_SCALE, SMALL_ICON_SCALE, center, center)
                parentDelegate.drawContent(info, iconShape, canvas, fixedDelegateBounds, paint)
            }
            shaderMatrix.setRotate(currentRotation, center, center)
            strokePaint.shader?.setLocalMatrix(shaderMatrix)
            glowPaint.shader?.setLocalMatrix(shaderMatrix)

            if (canvas.isHardwareAccelerated) {
                val recordingCanvas = glowNode.beginRecording()
                try {
                    recordingCanvas.translate(glowPadding.toFloat(), glowPadding.toFloat())
                    recordingCanvas.drawPath(iconShape.path, glowPaint)
                } finally {
                    glowNode.endRecording()
                }
                canvas.withClip(iconShape.path) {
                    canvas.translate(-glowPadding.toFloat(), -glowPadding.toFloat())
                    canvas.drawRenderNode(glowNode)
                }
            }
            canvas.drawPath(iconShape.path, strokePaint)
        }
        if (!rotationAnimator.isRunning) {
            rotationAnimator.start()
        }
    }

    companion object {
        private const val SMALL_ICON_SCALE = 24 / 30f
        private const val START_ANGLE = 13f
        private const val ROTATION_DURATION = 3000L
        private val positions = floatArrayOf(0.2f, 0.5f, 0.8f)

        @JvmStatic
        fun newAutomatedIcon(
            context: Context,
            info: ItemInfoWithIcon,
            @DrawableCreationFlags creationFlags: Int = 0,
        ): FastBitmapDrawable {
            val originalState = info.newIcon(context, creationFlags).constantState

            val strokeWidthPx: Float = dpToPx(2f, context).toFloat()
            val glowRadiusPx: Float = dpToPx(2f, context).toFloat()
            val glowPadding = dpToPx(6f, context)

            val outlineStartColor =
                boostChroma(context.getColor(R.color.materialColorTertiaryContainer))
            val outlineMiddleColor =
                boostChroma(context.getColor(R.color.materialColorPrimaryFixedDim))
            val outlineEndColor = boostChroma(context.getColor(R.color.materialColorPrimary))

            val newState =
                originalState.copy(
                    delegateFactory =
                        AutomatedIconDelegateFactory(
                            strokeWidthPx,
                            glowRadiusPx,
                            glowPadding,
                            outlineStartColor,
                            outlineMiddleColor,
                            outlineEndColor,
                            parentFactory = originalState.delegateFactory,
                        )
                )
            return newState.newDrawable()
        }

        @JvmStatic
        fun boostChroma(color: Int): Int {
            val hctColor = FloatArray(3)
            ColorUtils.colorToM3HCT(color, hctColor)
            val chroma = hctColor[1]
            return if (chroma < 5) {
                color
            } else {
                ColorUtils.M3HCTToColor(hctColor[0], 70f, hctColor[2])
            }
        }
    }
}

class AutomatedIconDelegateFactory(
    private val strokeWidthPx: Float,
    private val glowRadiusPx: Float,
    private val glowPadding: Int,
    private val outlineStartColor: Int,
    private val outlineMiddleColor: Int,
    private val outlineEndColor: Int,
    private val parentFactory: FastBitmapDrawableDelegate.DelegateFactory,
) : FastBitmapDrawableDelegate.DelegateFactory {

    override fun newDelegate(
        bitmapInfo: BitmapInfo,
        iconShape: IconShape,
        paint: Paint,
        host: FastBitmapDrawable,
    ): FastBitmapDrawableDelegate {
        val parent = parentFactory.newDelegate(bitmapInfo, iconShape, paint, host)
        return AutomatedIconDelegate(
            strokeWidthPx,
            glowRadiusPx,
            glowPadding,
            outlineStartColor,
            outlineMiddleColor,
            outlineEndColor,
            iconShape,
            host,
            parent,
        )
    }
}
