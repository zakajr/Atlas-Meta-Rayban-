package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object MockSceneGenerator {

    fun generateSceneBitmap(type: String): Bitmap {
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        // Draw background
        paint.color = when (type) {
            "argan_press" -> Color.parseColor("#EED7A1") // Sandy warm color
            "cafe_menu" -> Color.parseColor("#1C2321") // Dark slate chalkboard
            "price_sign" -> Color.parseColor("#F4E8C1") // Golden warm craft shop
            "melon_stand" -> Color.parseColor("#9CB380") // Green fruit market background
            else -> Color.parseColor("#222222")
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        when (type) {
            "argan_press" -> {
                // Stone Mill Circle
                paint.color = Color.parseColor("#7A7A7A")
                canvas.drawCircle(320f, 260f, 150f, paint)
                paint.color = Color.parseColor("#5F5F5F")
                canvas.drawCircle(320f, 260f, 120f, paint)

                // Golden Argan Oil stream
                paint.color = Color.parseColor("#E5A93B")
                canvas.drawRect(310f, 380f, 330f, 440f, paint)
                canvas.drawCircle(320f, 440f, 20f, paint)

                // Argan Kernels on side
                paint.color = Color.parseColor("#80593D")
                canvas.drawCircle(190f, 190f, 15f, paint)
                canvas.drawCircle(170f, 210f, 18f, paint)
                canvas.drawCircle(210f, 205f, 12f, paint)

                // Text labels for Gemini to notice
                paint.color = Color.BLACK
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("MOROCCAN TRADITIONAL ARGAN PRESS", 320f, 60f, paint)
                paint.textSize = 20f
                paint.color = Color.parseColor("#5D4037")
                canvas.drawText("[Rha stone grinder actively extracting pure oil]", 320f, 95f, paint)
            }
            "cafe_menu" -> {
                // Border
                paint.color = Color.parseColor("#8B5A2B") // Wood frame
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 20f
                canvas.drawRect(10f, 10f, width - 10f, height - 10f, paint)

                paint.style = Paint.Style.FILL
                // Title
                paint.color = Color.parseColor("#FFD700") // Golden Chalk
                paint.textSize = 34f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("SOUK CAFE - FRESH MOROCCAN CUISINE", 320f, 70f, paint)

                // Dividers
                paint.color = Color.WHITE
                paint.strokeWidth = 3f
                canvas.drawLine(100f, 95f, 540f, 95f, paint)

                // Menu items
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = 24f

                val items = listOf(
                    "1. Taktouka (Sweet Pepper & Tomato Dip) - 30 MAD",
                    "   [Mild spices, slow-roasted, served with bread]",
                    "2. Tagine de Poulet aux Citrons - 70 MAD",
                    "   [Chicken, preserved lemons, olives, saffron]",
                    "3. Traditional Mint Tea (Large Pot) - 20 MAD",
                    "   [Fresh mint leaves, gunpowder green tea, sweet]"
                )

                var yOffset = 145f
                for (item in items) {
                    if (item.startsWith("   ")) {
                        paint.color = Color.parseColor("#B0BEC5") // Grey helper detail
                        paint.textSize = 18f
                        canvas.drawText(item, 80f, yOffset, paint)
                        yOffset += 40f
                    } else {
                        paint.color = Color.WHITE
                        paint.textSize = 22f
                        canvas.drawText(item, 60f, yOffset, paint)
                        yOffset += 30f
                    }
                }
            }
            "price_sign" -> {
                // Large tag on a rug
                paint.color = Color.parseColor("#D32F2F") // Red rug backdrop
                canvas.drawRect(50f, 100f, 590f, 440f, paint)

                // Rug Pattern Lines
                paint.color = Color.parseColor("#FFD700")
                paint.strokeWidth = 5f
                paint.style = Paint.Style.STROKE
                canvas.drawRect(70f, 120f, 570f, 420f, paint)
                canvas.drawLine(70f, 270f, 570f, 270f, paint)

                // Hanging white price tag
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawRoundRect(200f, 150f, 440f, 350f, 30f, 30f, paint)

                // Hole in the tag
                paint.color = Color.parseColor("#D32F2F")
                canvas.drawCircle(320f, 180f, 10f, paint)

                // Price Text
                paint.color = Color.BLACK
                paint.textSize = 45f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("200 MAD", 320f, 260f, paint)

                paint.textSize = 18f
                paint.color = Color.GRAY
                canvas.drawText("Fixed Price", 320f, 295f, paint)
                paint.color = Color.parseColor("#D32F2F")
                canvas.drawText("Handwoven in Atlas", 320f, 325f, paint)

                // Title
                paint.color = Color.BLACK
                paint.textSize = 30f
                canvas.drawText("MOROCCAN ARTISAN CRAFTS COOPERATIVE", 320f, 50f, paint)
                paint.textSize = 18f
                canvas.drawText("[A colorful wool rug hanging on display with price tag]", 320f, 80f, paint)
            }
            "melon_stand" -> {
                // Wooden stand
                paint.color = Color.parseColor("#5D4037")
                canvas.drawRect(100f, 110f, 540f, 430f, paint)

                // Melon circle
                paint.color = Color.parseColor("#FED976") // Melon ripe color
                canvas.drawCircle(320f, 270f, 95f, paint)

                // Textured net skin pattern (crisscross)
                paint.style = Paint.Style.STROKE
                paint.color = Color.parseColor("#E3C063")
                paint.strokeWidth = 3f
                canvas.drawCircle(320f, 270f, 75f, paint)
                canvas.drawCircle(320f, 270f, 55f, paint)

                for (i in -4..4) {
                    val x = 320f + i * 15f
                    canvas.drawLine(x - 40f, 175f, x + 40f, 365f, paint)
                    canvas.drawLine(x + 40f, 175f, x - 40f, 365f, paint)
                }

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#8D6E63") // Light brown stem
                canvas.drawRect(315f, 140f, 325f, 180f, paint)
                paint.color = Color.parseColor("#E2F0D9") // Pale ripe halo
                canvas.drawCircle(320f, 175f, 15f, paint)

                // Stand sign label
                paint.color = Color.WHITE
                canvas.drawRect(150f, 30f, 490f, 100f, paint)
                paint.color = Color.BLACK
                paint.textSize = 21f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("SWEET CHARENTAIS CANTALOUPE MELONS", 320f, 60f, paint)
                paint.textSize = 15f
                canvas.drawText("[Dull matte skin, pale hue near stem indicates peak ripeness]", 320f, 85f, paint)
            }
        }

        return bitmap
    }
}
