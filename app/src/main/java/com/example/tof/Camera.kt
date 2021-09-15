package com.example.tof

import android.Manifest
import android.content.Context
import com.example.tof.DepthFrameVisualizer
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import com.example.tof.DepthFrameAvailableListener
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.SizeF
import android.hardware.camera2.CameraAccessException
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.graphics.ImageFormat
import android.media.ImageReader
import android.util.Log
import android.util.Range
import java.lang.IllegalStateException
import java.util.*

class Camera(private val context: Context, depthFrameVisualizer: DepthFrameVisualizer?) :
    CameraDevice.StateCallback() {
    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val previewReader: ImageReader = ImageReader.newInstance(
        DepthFrameAvailableListener.WIDTH,
        DepthFrameAvailableListener.HEIGHT, ImageFormat.DEPTH16, 2
    )
    private var previewBuilder: CaptureRequest.Builder? = null
    private val imageAvailableListener: DepthFrameAvailableListener = DepthFrameAvailableListener(depthFrameVisualizer)

    fun listCameras(){
        cameraManager.cameraIdList.forEach {camera ->
            val char = cameraManager.getCameraCharacteristics(camera)

            Log.d(TAG, "cameras ${char.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)}")
        }
    }

    // Open the front depth camera and start sending frames
    fun openFrontDepthCamera() {
        val cameraId = frontDepthCameraID
        openCamera(cameraId)
    }

    // Note that the sensor size is much larger than the available capture size
    private val frontDepthCameraID:

    // Since sensor size doesn't actually match capture size and because it is
    // reporting an extremely wide aspect ratio, this FoV is bogus
            String?
        get() {
            try {
                for (camera in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(camera)
                    val capabilities =
                        chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    val facingFront =
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
                    var depthCapable = false
                    for (capability in capabilities!!) {
                        val capable =
                            capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT
                        depthCapable = depthCapable || capable
                    }
                    if (depthCapable && facingFront) {
                        // Note that the sensor size is much larger than the available capture size
                        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        Log.i(TAG, "Sensor size: $sensorSize")

                        // Since sensor size doesn't actually match capture size and because it is
                        // reporting an extremely wide aspect ratio, this FoV is bogus
                        val focalLengths =
                            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        if (focalLengths?.isNotEmpty() == true) {
                            val focalLength = focalLengths[0]
                            val fov =
                                2 * Math.atan((sensorSize!!.width / (2 * focalLength)).toDouble())
                            Log.i(TAG, "Calculated FoV: $fov")
                        }
                        return camera
                    }
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Could not initialize Camera Cache")
                e.printStackTrace()
            }
            return null
        }

    private fun openCamera(cameraId: String?) {
        if (cameraId != null) {
            try {
                val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                if (PackageManager.PERMISSION_GRANTED == permission) {
                    cameraManager.openCamera(cameraId, this, null)
                } else {
                    Log.e(TAG, "Permission not available to open camera")
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Opening Camera has an Exception $e")
                e.printStackTrace()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Opening Camera has an Exception $e")
                e.printStackTrace()
            } catch (e: SecurityException) {
                Log.e(TAG, "Opening Camera has an Exception $e")
                e.printStackTrace()
            }
        }

    }

    override fun onOpened(camera: CameraDevice) {
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewBuilder?.set(CaptureRequest.JPEG_ORIENTATION, 0)
            val fpsRange = Range(FPS_MIN, FPS_MAX)
            previewBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            previewBuilder?.addTarget(previewReader.surface)
            val targetSurfaces = Arrays.asList(previewReader.surface)
            camera.createCaptureSession(targetSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        onCaptureSessionConfigured(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "!!! Creating Capture Session failed due to internal error ")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun onCaptureSessionConfigured(session: CameraCaptureSession) {
        Log.i(TAG, "Capture Session created")
        previewBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            session.setRepeatingRequest(previewBuilder!!.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onDisconnected(camera: CameraDevice) {}
    override fun onError(camera: CameraDevice, error: Int) {}

    companion object {
        private val TAG = Camera::class.java.simpleName
        private const val FPS_MIN = 15
        private const val FPS_MAX = 30
    }

    init {
        previewReader.setOnImageAvailableListener(imageAvailableListener, null)
    }
}