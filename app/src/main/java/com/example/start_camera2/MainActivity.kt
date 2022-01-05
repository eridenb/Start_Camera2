package com.example.start_camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.icu.text.SimpleDateFormat
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity() , ActivityCompat.OnRequestPermissionsResultCallback{
    private lateinit var mTextureView: TextureView
    private lateinit var mCameraManager:CameraManager
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var captuerRecorderBuilder: CaptureRequest.Builder
    private lateinit var recordRequestBuilder : CaptureRequest.Builder
    private lateinit var previewRequest: CaptureRequest
    private lateinit var captureRequest: CaptureRequest
    private var mCaptureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private val previewSize: Size = Size(300, 300)
    private var mImageReader: ImageReader? = null
    private var midRec : MediaRecorder? = null
    private var backgroundThread: HandlerThread? = null
    private var mCameraHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTextureView = findViewById(R.id.textureView)
        mTextureView.surfaceTextureListener = surfaceTextureListener
        startBackgroundThread()

        var takePicBtn = findViewById<ImageButton>(R.id.btnTakePic)
        takePicBtn.setOnClickListener(ButtonCelickListener())

    }
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

        }

        override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

        }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
            return false
        }
    }

    /**
     * カメラを開く
     */
    private fun openCamera(){
        mCameraManager = getSystemService(Context.CAMERA_SERVICE)as CameraManager
        mImageReader = ImageReader.newInstance(640,480,ImageFormat.JPEG,1)
        mImageReader!!.setOnImageAvailableListener(imageAvailable(),null)
        try {
            var camerId: String = mCameraManager.getCameraIdList()[0]

            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission()
                return
            }
            mCameraManager.openCamera(camerId, stateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
    }
    private val stateCallback=object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            finish()
        }

    }

    /**
     * カメラビューを表示する
     */
    private fun createCameraPreviewSession(){
        val mTexture = mTextureView.surfaceTexture
        val defaultBufferSize =mTexture?.setDefaultBufferSize(previewSize.width,previewSize.height)
        val surface = Surface(mTexture)
        previewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder?.addTarget(surface)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        mCameraDevice?.createCaptureSession(Arrays.asList(surface, mImageReader?.surface), object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {
                mCaptureSession = session
                previewRequest = previewRequestBuilder!!.build()
                session.setRepeatingRequest(previewRequest,mCaptureCallBack, backgroundThread?.looper?.let { Handler(it) })
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {

            }

        }, null)
    }

    private val mCaptureCallBack = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            val mTexture = mTextureView.surfaceTexture
            val surface = Surface(mTexture)
            var captuerRecorderBuilder1 = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captuerRecorderBuilder1?.addTarget(surface)
            captureRequest = captuerRecorderBuilder1.build()
            mCaptureSession?.setRepeatingRequest(captureRequest,null,null)

        }

        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            super.onCaptureFailed(session, request, failure)

        }
    }
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        mCameraHandler = backgroundThread?.looper?.let { Handler(it) }
    }
    /**
     * 撮影ボタン押下
     */
    private inner class ButtonCelickListener: View.OnClickListener{
        override fun onClick(v: View?) {
            var imageReader = ImageReader.newInstance(640,480,ImageFormat.JPEG,1)
            //リクストを作成する
            captuerRecorderBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)!!
            captuerRecorderBuilder.addTarget(imageReader!!.surface)
            //AF AE modes
            captuerRecorderBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captuerRecorderBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            captuerRecorderBuilder.set(CaptureRequest.JPEG_ORIENTATION,Surface.ROTATION_0)
            //
            previewRequest = previewRequestBuilder.build()
            //previewを止める
            mCaptureSession?.stopRepeating()

            mCaptureSession?.capture(previewRequest,mCaptureCallBack ,mCameraHandler)
        }
    }
    /**
     * 画像を保存
     */
    private inner class imageAvailable:ImageReader.OnImageAvailableListener{
        override fun onImageAvailable(reader: ImageReader?) {
            val path = Environment.getExternalStorageDirectory().path+"/camera2/"
            //val path  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = path + "IMG_$timeStamp.jpg"
            val fos = FileOutputStream(fileName)
            val image = mImageReader?.acquireLatestImage()
            val byetBuffer : ByteBuffer = image!!.planes[0].buffer
            val data = ByteArray(byetBuffer.remaining())
            byetBuffer.get(data)
            val bitmap : Bitmap = BitmapFactory.decodeByteArray(data,0,data.size)
            image?.close()

            bitmap.compress(Bitmap.CompressFormat.JPEG,100,fos)

        }
    }
}