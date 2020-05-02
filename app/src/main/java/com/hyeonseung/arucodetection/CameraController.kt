package com.hyeonseung.arucodetection

import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import androidx.fragment.app.Fragment
import java.util.*

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
@TargetApi(21)

class CameraController : Fragment (){



private inner class CameraControllerViewBase : CameraBridgeViewBase {

    private var mImageReader: ImageReader? = null
    private val mPreviewImageFormat: Int = ImageFormat.YUV_420_888
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mCameraID: String? = null
    private var mPreviewSize = Size(-1, -1)
    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    constructor(context: Context?, cameraId: Int) : super(context, cameraId) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(
        context,
        attrs
    ) {
    }

    private fun startBackgroundThread() {
        Log.i(
            LOGTAG,
            "startBackgroundThread"
        )
        stopBackgroundThread()
        mBackgroundThread = HandlerThread("OpenCVCameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        Log.i(
            LOGTAG,
            "stopBackgroundThread"
        )
        if (mBackgroundThread == null) return
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(
                LOGTAG,
                "stopBackgroundThread",
                e
            )
        }
    }

    protected fun initializeCamera(): Boolean {
        Log.i(LOGTAG, "initializeCamera")
        val manager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val camList = manager.cameraIdList
            if (camList.size == 0) {
                Log.e(
                    LOGTAG,
                    "Error: camera isn't detected."
                )
                return false
            }
            if (mCameraIndex == CAMERA_ID_ANY) {
                mCameraID = camList[0]
            } else {
                for (cameraID in camList) {
                    val characteristics =
                        manager.getCameraCharacteristics(cameraID!!)
                    if (mCameraIndex == CAMERA_ID_BACK &&
                        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK ||
                        mCameraIndex == CAMERA_ID_FRONT &&
                        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                    ) {
                        mCameraID = cameraID
                        break
                    }
                }
            }
            if (mCameraID != null) {
                Log.i(
                    LOGTAG,
                    "Opening camera: $mCameraID"
                )
                manager.openCamera(mCameraID!!, mStateCallback, mBackgroundHandler)
            }
            return true
        } catch (e: CameraAccessException) {
            Log.e(
                LOGTAG,
                "OpenCamera - Camera Access Exception",
                e
            )
        } catch (e: IllegalArgumentException) {
            Log.e(
                LOGTAG,
                "OpenCamera - Illegal Argument Exception",
                e
            )
        } catch (e: SecurityException) {
            Log.e(
                LOGTAG,
                "OpenCamera - Security Exception",
                e
            )
        }
        return false
    }

    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraDevice.close()
            mCameraDevice = null
        }
    }

    private fun createCameraPreviewSession() {
        val w = mPreviewSize.width
        val h = mPreviewSize.height
        Log.i(
            LOGTAG,
            "createCameraPreviewSession(" + w + "x" + h + ")"
        )
        if (w < 0 || h < 0) return
        try {
            if (null == mCameraDevice) {
                Log.e(
                    LOGTAG,
                    "createCameraPreviewSession: camera isn't opened"
                )
                return
            }
            if (null != mCaptureSession) {
                Log.e(
                    LOGTAG,
                    "createCameraPreviewSession: mCaptureSession is already started"
                )
                return
            }
            mImageReader = ImageReader.newInstance(w, h, mPreviewImageFormat, 2)
            var imageReader: ImageReader= mImageReader as ImageReader
            imageReader.setOnImageAvailableListener(OnImageAvailableListener { reader ->
                val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

                // sanity checks - 3 planes
                val planes = image.planes
                assert(planes.size == 3)
                assert(image.format == mPreviewImageFormat)
                assert(planes[0].pixelStride == 1)
                assert(planes[1].pixelStride == 2)
                assert(planes[2].pixelStride == 2)
                val y_plane = planes[0].buffer
                val uv_plane = planes[1].buffer
                val y_mat = Mat(h, w, CvType.CV_8UC1, y_plane)
                val uv_mat = Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane)
                val tempFrame = Camera2Frame(y_mat, uv_mat, w, h)
                deliverAndDrawFrame(tempFrame)
                tempFrame.release()
                image.close()
            }, mBackgroundHandler)
            val surface = imageReader.getSurface()

            mPreviewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)
            mCameraDevice!!.createCaptureSession(
                Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        Log.i(
                            LOGTAG,
                            "createCaptureSession::onConfigured"
                        )
                        if (null == mCameraDevice) {
                            return  // camera is already closed
                        }
                        mCaptureSession = cameraCaptureSession
                        try {
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )
                            mCaptureSession!!.setRepeatingRequest(
                                mPreviewRequestBuilder!!.build(),
                                null,
                                mBackgroundHandler
                            )
                            Log.i(
                                LOGTAG,
                                "CameraPreviewSession has been started"
                            )
                        } catch (e: Exception) {
                            Log.e(
                                LOGTAG,
                                "createCaptureSession failed",
                                e
                            )
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(
                            LOGTAG,
                            "createCameraPreviewSession failed"
                        )
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(
                LOGTAG,
                "createCameraPreviewSession",
                e
            )
        }
    }

    override fun disconnectCamera() {
        Log.i(LOGTAG, "closeCamera")
        try {
            val c = mCameraDevice
            mCameraDevice = null
            if (null != mCaptureSession) {
                mCaptureSession!!.close()
                mCaptureSession = null
            }
            c?.close()
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
        } finally {
            stopBackgroundThread()
        }
    }

    fun calcPreviewSize(width: Int, height: Int): Boolean {
        Log.i(
            LOGTAG,
            "calcPreviewSize: " + width + "x" + height
        )
        if (mCameraID == null) {
            Log.e(
                LOGTAG,
                "Camera isn't initialized!"
            )
            return false
        }
        val manager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(mCameraID!!)
            val map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            var bestWidth = 0
            var bestHeight = 0
            val aspect = width.toFloat() / height
            val sizes =
                map!!.getOutputSizes(
                    ImageReader::class.java
                )
            bestWidth = sizes[0].width
            bestHeight = sizes[0].height
            for (sz in sizes) {
                val w = sz.width
                val h = sz.height
                Log.d(
                    LOGTAG,
                    "trying size: " + w + "x" + h
                )
                if (width >= w && height >= h && bestWidth <= w && bestHeight <= h && Math.abs(
                        aspect - w.toFloat() / h
                    ) < 0.2
                ) {
                    bestWidth = w
                    bestHeight = h
                }
            }
            Log.i(
                LOGTAG,
                "best size: " + bestWidth + "x" + bestHeight
            )
            assert(!(bestWidth == 0 || bestHeight == 0))
            return if (mPreviewSize.width == bestWidth && mPreviewSize.height == bestHeight) false else {
                mPreviewSize = Size(bestWidth, bestHeight)
                true
            }
        } catch (e: CameraAccessException) {
            Log.e(
                LOGTAG,
                "calcPreviewSize - Camera Access Exception",
                e
            )
        } catch (e: IllegalArgumentException) {
            Log.e(
                LOGTAG,
                "calcPreviewSize - Illegal Argument Exception",
                e
            )
        } catch (e: SecurityException) {
            Log.e(
                LOGTAG,
                "calcPreviewSize - Security Exception",
                e
            )
        }
        return false
    }

    override fun connectCamera(width: Int, height: Int): Boolean {
        Log.i(
            LOGTAG,
            "setCameraPreviewSize(" + width + "x" + height + ")"
        )
        startBackgroundThread()
        initializeCamera()
        try {
            val needReconfig = calcPreviewSize(width, height)
            mFrameWidth = mPreviewSize.width
            mFrameHeight = mPreviewSize.height
            mScale =
                if (layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT && layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT) Math.min(
                    height.toFloat() / mFrameHeight,
                    width.toFloat() / mFrameWidth
                ) else 0f
            AllocateCache()
            if (needReconfig) {
                if (null != mCaptureSession) {
                    Log.d(
                        LOGTAG,
                        "closing existing previewSession"
                    )
                    mCaptureSession!!.close()
                    mCaptureSession = null
                }
                createCameraPreviewSession()
            }
        } catch (e: RuntimeException) {
            throw RuntimeException("Interrupted while setCameraPreviewSize.", e)
        }
        return true
    }

    private inner class Camera2Frame : CvCameraViewFrame {
        override fun gray(): Mat {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth)
        }

        override fun rgba(): Mat {
            if (mPreviewImageFormat == ImageFormat.NV21) Imgproc.cvtColor(
                mYuvFrameData,
                mRgba,
                Imgproc.COLOR_YUV2RGBA_NV21,
                4
            ) else if (mPreviewImageFormat == ImageFormat.YV12) Imgproc.cvtColor(
                mYuvFrameData,
                mRgba,
                Imgproc.COLOR_YUV2RGB_I420,
                4
            ) // COLOR_YUV2RGBA_YV12 produces inverted colors
            else if (mPreviewImageFormat == ImageFormat.YUV_420_888) {
                assert(mUVFrameData != null)
                Imgproc.cvtColorTwoPlane(
                    mYuvFrameData,
                    mUVFrameData,
                    mRgba,
                    Imgproc.COLOR_YUV2RGBA_NV21
                )
            } else throw IllegalArgumentException("Preview Format can be NV21 or YV12")
            return mRgba
        }

        constructor(Yuv420sp: Mat, width: Int, height: Int) : super() {
            mWidth = width
            mHeight = height
            mYuvFrameData = Yuv420sp
            mUVFrameData = null
            mRgba = Mat()
        }

        constructor(Y: Mat, UV: Mat?, width: Int, height: Int) : super() {
            mWidth = width
            mHeight = height
            mYuvFrameData = Y
            mUVFrameData = UV
            mRgba = Mat()
        }

        fun release() {
            mRgba.release()
        }

        private var mYuvFrameData: Mat
        private var mUVFrameData: Mat?
        private var mRgba: Mat
        private var mWidth: Int
        private var mHeight: Int
    }


}

    companion object {
        private const val LOGTAG = "CameraController"

        fun newInstance(): CameraController = CameraController().apply{

        }
    }

}