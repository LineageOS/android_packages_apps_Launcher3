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
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ValueAnimator
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.animation.InterpolatorsAndroidX
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.graphics.AnimationState.ENTER
import com.android.launcher3.graphics.AnimationState.EXIT
import com.android.launcher3.graphics.AnimationState.NORMAL
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

    private var animationState: AnimationState = ENTER
    private var currentRotation = 0f
    private val currentIconScale: FloatValueHolder = FloatValueHolder(MAX_ICON_SCALE)

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

    private val platePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            alpha = PLATE_ALPHA
        }

    private val shaderMatrix = Matrix()

    private val glowNode =
        RenderNode("glow").apply {
            alpha = GLOW_ALPHA
            val nodeWidth = iconShape.pathSize + (glowPadding * 2)
            val nodeHeight = iconShape.pathSize + (glowPadding * 2)
            setPosition(0, 0, nodeWidth, nodeHeight)
            val blurEffect =
                RenderEffect.createBlurEffect(glowRadiusPx, glowRadiusPx, Shader.TileMode.MIRROR)
            setRenderEffect(blurEffect)
        }

    private val rotationAnimator =
        ValueAnimator.ofFloat(MIN_ROTATION, MAX_ROTATION).apply {
            duration = ROTATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = InterpolatorsAndroidX.LINEAR
            addUpdateListener { animator ->
                currentRotation = (animator as ValueAnimator).animatedValue as Float
                if (!host.isVisible || host.callback == null) {
                    animator.cancel()
                    return@addUpdateListener
                }
                host.invalidateSelf()
            }
        }

    private val firstRotationAnimator =
        ValueAnimator.ofFloat(MIN_ROTATION, MAX_ROTATION).apply {
            duration = FIRST_ROTATION_DURATION
            interpolator = InterpolatorsAndroidX.EMPHASIZED_DECELERATE
            repeatCount = 0
            addUpdateListener { animator ->
                currentRotation = (animator as ValueAnimator).animatedValue as Float
                if (!host.isVisible || host.callback == null) {
                    animator.cancel()
                    return@addUpdateListener
                }
                host.invalidateSelf()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationStart(animation: Animator) {
                        cancelled = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            startNormalAnimation()
                        }
                    }
                }
            )
        }

    private val expressiveDefaultSpatialSpring =
        SpringForce().apply {
            dampingRatio = EXPRESSIVE_DEFAULT_SPATIAL_DAMPING
            stiffness = EXPRESSIVE_DEFAULT_SPATIAL_STIFFNESS
        }

    private val scaleAnimation: SpringAnimation =
        SpringAnimation(currentIconScale).apply {
            spring = expressiveDefaultSpatialSpring
            setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
            addUpdateListener { animation, _, _ ->
                if (!host.isVisible || host.callback == null) {
                    animation.cancel()
                    return@addUpdateListener
                }
                host.invalidateSelf()
            }
        }

    init {
        updateShaders()
        if (animationState == ENTER) startEnterAnimation()
    }

    private fun startEnterAnimation() {
        animationState = ENTER
        cancelAllAnimations()
        scaleAnimation.spring.finalPosition = MIN_ICON_SCALE_WITH_STROKE
        firstRotationAnimator.start()
        scaleAnimation.start()
    }

    private fun startNormalAnimation() {
        animationState = NORMAL
        cancelAllAnimations()
        rotationAnimator.start()
    }

    fun startExitAnimation(onEnd: Runnable? = null) {
        animationState = EXIT
        cancelAllAnimations()
        scaleAnimation.apply {
            val listener =
                object : DynamicAnimation.OnAnimationEndListener {
                    override fun onAnimationEnd(
                        animation: DynamicAnimation<*>?,
                        canceled: Boolean,
                        value: Float,
                        velocity: Float,
                    ) {
                        onEnd?.run()
                        removeEndListener(this)
                    }
                }
            addEndListener(listener)
            spring.finalPosition = MAX_ICON_SCALE
            start()
        }
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
                gradientPositions,
                Shader.TileMode.CLAMP,
            )
        val matrix = Matrix()
        matrix.setRotate(START_ANGLE, center, center)
        shader.setLocalMatrix(matrix)
        strokePaint.shader = shader
    }

    override fun drawContent(
        info: BitmapInfo,
        iconShape: IconShape,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {

        canvas.resizeToContentSize(bounds, iconShape.pathSize.toFloat()) {
            val center = iconShape.pathSize / 2f
            shaderMatrix.setRotate(currentRotation, center, center)
            strokePaint.shader?.setLocalMatrix(shaderMatrix)
            drawPath(iconShape.path, platePaint)
            transformed {
                scale(currentIconScale.value, currentIconScale.value, center, center)
                parentDelegate.drawContent(info, iconShape, canvas, fixedDelegateBounds, paint)
            }
            if (canvas.isHardwareAccelerated && !Flags.disableAppAutomationBlur()) {
                val recordingCanvas = glowNode.beginRecording()
                try {
                    recordingCanvas.translate(glowPadding.toFloat(), glowPadding.toFloat())
                    recordingCanvas.drawPath(iconShape.path, strokePaint)
                } finally {
                    glowNode.endRecording()
                }
                transformed {
                    translate(-glowPadding.toFloat(), -glowPadding.toFloat())
                    drawRenderNode(glowNode)
                }
            }
            canvas.drawPath(iconShape.path, strokePaint)
        }
    }

    override fun onVisibilityChanged(isVisible: Boolean) {
        super.onVisibilityChanged(isVisible)
        if (isVisible) {
            when (animationState) {
                ENTER ->
                    if (!firstRotationAnimator.isRunning && !scaleAnimation.isRunning) {
                        startEnterAnimation()
                    }
                NORMAL ->
                    if (!rotationAnimator.isRunning) {
                        startNormalAnimation()
                    }
                EXIT -> {
                    // no-op
                }
            }
        } else {
            cancelAllAnimations()
        }
    }

    private fun cancelAllAnimations() {
        firstRotationAnimator.cancel()
        rotationAnimator.cancel()
        scaleAnimation.cancel()
    }

    companion object {
        private const val START_ANGLE = 13f
        private const val ROTATION_DURATION = 5000L
        private const val FIRST_ROTATION_DURATION = 1000L
        private const val EXPRESSIVE_DEFAULT_SPATIAL_DAMPING = 0.8f
        private const val EXPRESSIVE_DEFAULT_SPATIAL_STIFFNESS = 380f
        private const val MIN_ICON_SCALE_WITH_STROKE = 0.85f
        private const val MAX_ICON_SCALE = 1f
        private const val MIN_ROTATION = 0f
        private const val MAX_ROTATION = 360f
        private const val GLOW_ALPHA = 0.5f
        private const val PLATE_ALPHA = (76.5).toInt() // 30% alpha

        private val gradientPositions = floatArrayOf(0.2f, 0.5f, 0.8f)

        @JvmStatic
        fun newAutomatedIcon(
            context: Context,
            info: ItemInfoWithIcon,
            @DrawableCreationFlags creationFlags: Int = 0,
        ): FastBitmapDrawable {
            val originalState = info.newIcon(context, creationFlags).constantState
            val resources = context.resources

            val strokeWidthPx: Float = resources.getDimension(R.dimen.automated_icon_stroke_width)
            val glowRadiusPx: Float =
                resources.getDimensionPixelSize(R.dimen.automated_icon_glow_radius).toFloat()
            val glowPadding = resources.getDimension(R.dimen.automated_icon_glow_padding).toInt()

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
                            originalState.delegateFactory,
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

private enum class AnimationState {
    ENTER,
    EXIT,
    NORMAL,
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
