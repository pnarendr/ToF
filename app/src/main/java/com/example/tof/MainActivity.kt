package com.example.tof

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tof.MainActivity

/*  This is an example of getting and processing ToF data.

    This example will only work (correctly) on a device with a front-facing depth camera
    with output in DEPTH16. The constants can be adjusted but are made assuming this
    is being run on a Samsung S10 5G device.
 */
class MainActivity : AppCompatActivity(), DepthFrameVisualizer {
    private var rawDataView: TextureView? = null
    private var noiseReductionView: TextureView? = null
    private var movingAverageView: TextureView? = null
    private var blurredAverageView: TextureView? = null
    private var defaultBitmapTransform: Matrix? = null
    private var camera: Camera? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rawDataView = findViewById(R.id.rawData)
        noiseReductionView = findViewById(R.id.noiseReduction)
        movingAverageView = findViewById(R.id.movingAverage)
        blurredAverageView = findViewById(R.id.blurredAverage)
        checkCamPermissions()
        camera = Camera(this, this)
        camera?.listCameras()

        //todo revert later
        // camera?.openFrontDepthCamera()
    }

    private fun checkCamPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAM_PERMISSIONS_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onRawDataAvailable(bitmap: Bitmap) {
        renderBitmapToTextureView(bitmap, rawDataView)
    }

    override fun onNoiseReductionAvailable(bitmap: Bitmap) {
        renderBitmapToTextureView(bitmap, noiseReductionView)
    }

    override fun onMovingAverageAvailable(bitmap: Bitmap) {
        renderBitmapToTextureView(bitmap, movingAverageView)
    }

    override fun onBlurredMovingAverageAvailable(bitmap: Bitmap) {
        renderBitmapToTextureView(bitmap, blurredAverageView)
    }

    /* We don't want a direct camera preview since we want to get the frames of data directly
        from the camera and process.

        This takes a converted bitmap and renders it onto the surface, with a basic rotation
        applied.
     */
    private fun renderBitmapToTextureView(bitmap: Bitmap, textureView: TextureView?) {
        val canvas = textureView!!.lockCanvas()
        canvas!!.drawBitmap(bitmap, defaultBitmapTransform(textureView)!!, null)
        textureView.unlockCanvasAndPost(canvas)
    }

    private fun defaultBitmapTransform(view: TextureView?): Matrix? {
        if (defaultBitmapTransform == null || view!!.width == 0 || view.height == 0) {
            val matrix = Matrix()
            val centerX = view!!.width / 2
            val centerY = view.height / 2
            val bufferWidth = DepthFrameAvailableListener.WIDTH
            val bufferHeight = DepthFrameAvailableListener.HEIGHT
            val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
            val viewRect = RectF(
                0f, 0f, view.width.toFloat(), view.height
                    .toFloat()
            )
            matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER)
            matrix.postRotate(270f, centerX.toFloat(), centerY.toFloat())
            defaultBitmapTransform = matrix
        }
        return defaultBitmapTransform
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val CAM_PERMISSIONS_REQUEST = 0
    }
}