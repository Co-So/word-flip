package com.wordflip.core.ui.image

import androidx.compose.ui.graphics.ColorMatrix

/**
 * 将 openapi ImageFilters 转为 Compose ColorMatrix（REQ-SNAP-5）。
 */
object ImageFilterMatrix {

    fun fromFilters(
        brightness: Float,
        contrast: Float,
        saturate: Float,
        grayscale: Float,
        sepia: Float,
    ): ColorMatrix {
        var matrix = ColorMatrix()
        matrix = multiply(matrix, brightnessMatrix(brightness / 100f))
        matrix = multiply(matrix, contrastMatrix(contrast / 100f))
        matrix = multiply(matrix, saturateMatrix(saturate / 100f))
        if (grayscale > 0f) {
            matrix = multiply(matrix, grayscaleMatrix(grayscale / 100f))
        }
        if (sepia > 0f) {
            matrix = multiply(matrix, sepiaMatrix(sepia / 100f))
        }
        return matrix
    }

    private fun brightnessMatrix(factor: Float): ColorMatrix {
        val delta = (factor - 1f) * 255f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, delta,
                0f, 1f, 0f, 0f, delta,
                0f, 0f, 1f, 0f, delta,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun contrastMatrix(factor: Float): ColorMatrix {
        val t = (1f - factor) * 128f
        return ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, t,
                0f, factor, 0f, 0f, t,
                0f, 0f, factor, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun saturateMatrix(factor: Float): ColorMatrix {
        val inv = 1f - factor
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        return ColorMatrix(
            floatArrayOf(
                r + factor, g, b, 0f, 0f,
                r, g + factor, b, 0f, 0f,
                r, g, b + factor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun grayscaleMatrix(amount: Float): ColorMatrix {
        val r = 0.2126f
        val g = 0.7152f
        val b = 0.0722f
        val inv = 1f - amount
        return ColorMatrix(
            floatArrayOf(
                r * amount + inv, g * amount, b * amount, 0f, 0f,
                r * amount, g * amount + inv, b * amount, 0f, 0f,
                r * amount, g * amount, b * amount + inv, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun sepiaMatrix(amount: Float): ColorMatrix {
        val inv = 1f - amount
        return ColorMatrix(
            floatArrayOf(
                0.393f * amount + inv, 0.769f * amount, 0.189f * amount, 0f, 0f,
                0.349f * amount, 0.686f * amount + inv, 0.168f * amount, 0f, 0f,
                0.272f * amount, 0.534f * amount, 0.131f * amount + inv, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun multiply(a: ColorMatrix, b: ColorMatrix): ColorMatrix {
        val av = a.values
        val bv = b.values
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += av[row * 5 + k] * bv[k * 5 + col]
                }
                if (col == 4) {
                    sum += av[row * 5 + 4]
                }
                out[row * 5 + col] = sum
            }
        }
        return ColorMatrix(out)
    }
}
