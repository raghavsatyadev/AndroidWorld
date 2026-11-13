package io.github.raghavsatyadev.androidworld.desktop.ui.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitMatrix =
        remember(text) {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.MARGIN to 2,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                ),
            )
        }

    Canvas(
        modifier =
            modifier.semantics {
                contentDescription = "Wi-Fi pairing QR code"
            },
    ) {
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val cell = size.minDimension / matrixWidth
        val totalWidth = cell * matrixWidth
        val totalHeight = cell * matrixHeight
        val leftOffset = (size.width - totalWidth) / 2f
        val topOffset = (size.height - totalHeight) / 2f

        drawRect(color = Color.White)

        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                if (bitMatrix.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(leftOffset + x * cell, topOffset + y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
