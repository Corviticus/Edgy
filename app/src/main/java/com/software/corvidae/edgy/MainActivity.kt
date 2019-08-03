package com.software.corvidae.edgy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.SeekBar
import android.widget.Toast
import kotlinx.android.synthetic.main.main_layout.*
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity(),
        TextureView.SurfaceTextureListener,
        Camera.PreviewCallback {

    companion object {

        private const val TAG = "MainActivity"

        private const val PERMISSIONS_REQUEST_CAMERA = 1

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV not loaded")
            } else {
                Log.d(TAG, "OpenCV loaded")
            }

            // load the C++ library we will use for image processor
            System.loadLibrary("ImageProcessing")
        }
    }

    private var mCameraOrientation: Int = 0
    private var mDeviceOrientation: Int = 0
    private var mPreviewSizeWidth: Int = 0
    private var mPreviewSizeHeight: Int = 0
    private var mIsProcessing = false
    private var mThresholdValue: Int = 0

    private lateinit var imageProcessJob: Job

    private var mCamera: Camera? = null
    private var mPreviewSize: Camera.Size? = null
    private var mTextureView: AutoFitTextureView? = null
    private var mDeviceOrientationListener: OrientationEventListener? = null

    /**
     * JNI function to take a camera image and Perform Canny Edge detection
     * on that image. The returned pixels are a char array of edges found.
     *
     * @param width - The "width" of the Byte Array from device camera as an Integer
     * @param height - The "height" of the Byte Array from the device camera as an Integer
     * @param lowThreshold - The Canny threshold value as an Integer
     * @param NV21FrameData - Byte array from a device camera capture
     * @param pixels - Char array from C++ Canny edge detection
     */
    private external fun ImageProcessing(width: Int, height: Int, lowThreshold: Int, NV21FrameData: ByteArray?, pixels: IntArray?): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // show the app's icon
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(R.mipmap.ic_launcher)

        // listener for device orientation changes
        mDeviceOrientationListener =
                object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
                    override fun onOrientationChanged(orientation: Int) {
                        mCameraOrientation = orientation
                    }
                }

        if (mDeviceOrientationListener?.canDetectOrientation() == true) {
            mDeviceOrientationListener?.enable()
        } else {
            mDeviceOrientationListener?.disable()
        }

        setContentView(R.layout.main_layout)
        mTextureView = cameraPreviewTextureView

        // a seekbar to change canny threshold settings
        mThresholdValue = cannyThresholdSeekBar.progress
        cannyThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mThresholdValue = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            // pop up a toast showing the slider value
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                showSeekBarToast()
            }
        })
    }

    override fun onResume() {
        super.onResume()

        try {
            if (!checkCameraPermissions()) { // API >= 6 hoop jumping...
                return
            } else {
                if (mCamera == null) {
                    mCamera = Camera.open()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate() - Camera could not be opened $e")
        }
    }

    /**
     * Called after onCreate()
     * @param savedInstanceState Bundle containing saved instance values
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // set this up late after camera is opened
        mTextureView?.surfaceTextureListener = this

        // default activity return value
        setResult(Activity.RESULT_CANCELED)
    }

    public override fun onPause() {
        super.onPause()

        // stop processing camera data
        if (::imageProcessJob.isInitialized) {
            imageProcessJob.cancel()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()

        mTextureView?.surfaceTextureListener = null

        mCamera?.also {
            it.stopPreview()
            it.setPreviewCallback(null)
            it.release()
            mCamera = null
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED, null)
        finish()
        super.onBackPressed()
    }

    /**
     * Called when the [TextureView] becomes available
     *
     * @param surface - the TextureView
     * @param width - Width of mTextureView as an Integer
     * @param height - Height of TextureView as an Integer
     */
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {

        // can't do anything without camera instance
        try {
            if (!checkCameraPermissions()) { // API >= 6 hoop jumping...
                return
            } else {
                if (mCamera == null) {
                    mCamera = Camera.open()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate() - Camera could not be opened $e")
        }

        // determine device AND sensor orientation - this may not work on all older phones...
        setCameraDisplayOrientation()

        // set up transform
        configureTransform(width, height)

        // finally set up camera outputs
        setUpCameraOutputs(width, height)

        // start 'er up!
        if (mCamera != null) {
            try {
                mCamera?.setPreviewTexture(surface)
                mCamera?.setPreviewCallback(this)
                mCamera?.startPreview()

            } catch (e: Exception) {
                Log.e(TAG, "Exception starting camera preview: " + e.message)
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // set up transform although we are not doing anything here...
        configureTransform(width, height)
    }

    /**
     * Called just before destroying the surfaceTexture
     *
     * @param surface - The surfaceTexture that was destroyed
     * @return True to indicate that release has been called
     */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // deallocate resources
        surface.release()
        return true
    }

    /**
     *
     * @param surface - The updated surface...
     */
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {

        if (null == mTextureView || null == mPreviewSize) {
            return
        }

        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize?.height?.toFloat()
                ?: 0f, mPreviewSize?.width?.toFloat() ?: 0f)

        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        val rotation = mDeviceOrientation
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / (mPreviewSize?.height ?: 1),
                    viewWidth.toFloat() / (mPreviewSize?.width ?: 1))
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        mTextureView?.setTransform(matrix)
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {

        try {
           // val metrics = Resources.getSystem().displayMetrics
            val sizes = mCamera?.parameters?.supportedPictureSizes
            sizes?.let {
                for (size in it) {
                    if (size.width <= width || size.height <= width) {
                        mCamera?.parameters?.setPictureSize(size.width, size.height)
                        Log.d(TAG, "Camera Picture Size Width = ${size.width}")
                        Log.d(TAG, "Camera Picture Size Height = ${size.height}")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

        try {

            mPreviewSize = mCamera?.parameters?.previewSize
            Log.d(TAG, "Camera Preview Size Width = " + mPreviewSize?.width)
            Log.d(TAG, "Camera Preview Size Height = " + mPreviewSize?.height)

            // set up OpenCV variables
            mPreviewSizeWidth = mPreviewSize?.width ?: 0
            mPreviewSizeHeight = mPreviewSize?.height ?: 0

            // fit the aspect ratio of TextureView to the size of preview
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mPreviewSize?.let { mTextureView?.setAspectRatio(it.width, it.height) }
            } else {
                mPreviewSize?.let { mTextureView?.setAspectRatio(it.height, it.width) }
            }

        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Shows a [Toast] on the UI thread.
     */
    private fun showSeekBarToast() {
        runOnUiThread {
            val toast = Toast.makeText(applicationContext,
                    "Low Threshold: $mThresholdValue", Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        }
    }

    // make sure we have the right image format for OpenCV processing
    override fun onPreviewFrame(data: ByteArray, camera: Camera) {

        if (mCamera?.parameters?.previewFormat == ImageFormat.NV21) {
            if (!mIsProcessing) {
                imageProcessJob = GlobalScope.launch(Dispatchers.Main) { doImageProcessing(data) }
            }
        } else {
            Log.e(TAG, "Wrong video format for OpenCV library")
        }
    }

    /**
     * Determines the device orientation and sets camera parameters accordingly
     */
    private fun setCameraDisplayOrientation() {

        val info = CameraInfo()
        Camera.getCameraInfo(CameraInfo.CAMERA_FACING_BACK, info)

        var degrees = 90 // back-facing

        // compensate for mirror
        when {
            mCameraOrientation < 45 || mCameraOrientation > 315 -> degrees = 0
            mCameraOrientation < 135 -> degrees = 90
            mCameraOrientation < 225 -> degrees = 180
        }

        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            mDeviceOrientation = (info.orientation + degrees) % 360
            mDeviceOrientation = (360 - mDeviceOrientation) % 360  // compensate for mirror
        } else {
            // back-facing
            mDeviceOrientation = (info.orientation - degrees + 360) % 360
        }

        if (mCamera != null) {
            mCamera?.setDisplayOrientation(mDeviceOrientation)
            mCamera?.parameters?.setRotation(mDeviceOrientation)
        }
    }

    /**
     * Rotates a bitmap by a specified amount
     *
     * @param source - The bitmap to be rotated
     * @param angle -  A float representing the angle to rotate
     * @return The rotated bitmap
     */
    private fun rotateBitmap(source: Bitmap?, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return source?.let { Bitmap.createBitmap(it, 0, 0, source.width, source.height, matrix, true) }
    }

    // runnable for performing the Canny Edge Detection
    private fun doImageProcessing(data: ByteArray) {

        // set 'processing flag' true
        mIsProcessing = true

        // the c++ code will populate this
        val outputArray = IntArray(mPreviewSizeWidth * mPreviewSizeHeight)

        try {
            ImageProcessing(
                    mPreviewSizeWidth,
                    mPreviewSizeHeight,
                    mThresholdValue,
                    data,           // IN - byte array from camera
                    outputArray)    // OUT - char array from c++ code
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val mTransformBitmap = Bitmap.createBitmap(mPreviewSizeWidth, mPreviewSizeHeight, Bitmap.Config.ARGB_8888)

        // load bitmap into image view, add some transparency so we can see the camera's preview and
        // rotate. A nicer method would be to return the canny image (mImagePixels) without black background
        mTransformBitmap?.setPixels(outputArray, 0, mPreviewSizeWidth, 0, 0, mPreviewSizeWidth, mPreviewSizeHeight)
        cameraPreviewImageView.alpha = .60f
        cameraPreviewImageView.setImageBitmap(rotateBitmap(mTransformBitmap, 90f))

        // done processing
        mIsProcessing = false
    }

    /**
     * Check device permissions to see if user has allowed use of camera hardware
     * This will end up calling onRequestPermissionsResult() to handle permissions for API >= 6
     *
     * @return True or False depending on what the user decided to do
     */
    private fun checkCameraPermissions(): Boolean {

        val granted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA)
        return when {
            granted != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.CAMERA), PERMISSIONS_REQUEST_CAMERA)
                false // cannot open camera at this time
            }
            else ->
                true // we have permission to open the camera
        }
    }

    /**
     * Called when a user has selected 'Deny' or 'Allow' from a permissions dialog
     *
     * @param requestCode  Integer representing the 'Request Code'
     * @param permissions  String array containing the requested permissions
     * @param grantResults Integer array containing the permissions results
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            PERMISSIONS_REQUEST_CAMERA -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mCamera == null) {
                        mCamera = Camera.open()
                    }
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                   // ...
                } else {
                    val snackbar = Snackbar.make(mainLayout,
                            resources.getString(R.string.message_no_camera_permissions), Snackbar.LENGTH_LONG)
                    snackbar.setAction(resources.getString(R.string.settings)) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                    snackbar.show()
                }
            }
        }
    }

}
