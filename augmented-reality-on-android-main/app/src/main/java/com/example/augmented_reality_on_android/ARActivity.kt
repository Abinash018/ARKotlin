package com.example.augmented_reality_on_android

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.RangeSlider
import com.google.android.material.switchmaterial.SwitchMaterial
import org.opencv.android.CameraActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.Utils.bitmapToMat
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import java.io.File
import java.util.*


class ARActivity : CameraActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private val cameraView by lazy { findViewById<JavaCameraView>(R.id.cameraView) }
    private val menu by lazy { findViewById<LinearLayout>(R.id.menu) }
    private val expandButton by lazy { findViewById<ImageButton>(R.id.expand_button) }
    private val refImg by lazy { findViewById<ImageView>(R.id.ref_img) }
    private val sizeX by lazy { findViewById<RangeSlider>(R.id.size_x) }
    private val sizeY by lazy { findViewById<RangeSlider>(R.id.size_y) }
    private val sizeZ by lazy { findViewById<RangeSlider>(R.id.size_z) }
    private val imageView by lazy { findViewById<ImageView>(R.id.imageView) }
    private lateinit var imageMat: Mat
    private lateinit var arCore: ARCore
    private var isPause: Boolean = false
    private var menuHidden: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ar_activity)

        getPermission()

        cameraView.setCvCameraViewListener(this)
        cameraView.visibility = SurfaceView.VISIBLE

        var reference_image = Mat()
        val refImgResource = intent.getIntExtra("reference_image_int", -1)
        val refImgFilename = intent.getStringExtra("reference_image_str")
        if (refImgResource != -1) {
            reference_image =
                org.opencv.android.Utils.loadResource(
                    this,
                    refImgResource,
                    CvType.CV_8UC4
                )
            refImg.setImageResource(refImgResource)
        } else if (refImgFilename != null) {
            val file = File(filesDir, refImgFilename)
            if (file.exists()) {
                val bm = BitmapFactory.decodeFile(file.absolutePath)
                reference_image = Mat()
                bitmapToMat(bm, reference_image)
                refImg.setImageBitmap(bm)
            }
        }
        if (reference_image.empty()){
            reference_image =
                org.opencv.android.Utils.loadResource(
                    this,
                    R.drawable.book1_reference,
                    CvType.CV_8UC4
                )
            refImg.setImageResource(refImgResource)
        }

        arCore = ARCore(reference_image)

        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener { view ->
            if (isPause) {
                isPause = false
                cameraView.enableView()
                fab.setImageResource(R.drawable.ic_pause)
            } else {
                isPause = true
                cameraView.disableView()
                fab.setImageResource(R.drawable.ic_play)
            }
        }

        sizeX.addOnChangeListener { slider, value, fromUser ->
            arCore.changeX(reference_image.size(1) / 100.0 * value)
        }
        sizeY.addOnChangeListener { slider, value, fromUser ->
            arCore.changeY(reference_image.size(0) / 100.0 * value)
        }
        sizeZ.addOnChangeListener { slider, value, fromUser ->
            arCore.changeZ(value.toDouble()*5)
        }
        val colorToggle: SwitchMaterial = findViewById(R.id.toggle_color)
        colorToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                arCore.setEdgeColors(Scalar(255.0, 255.0, 255.0))
            } else {
                arCore.setEdgeColors(Scalar(0.0, 0.0, 0.0))
            }
        }
        val frameToggle: SwitchMaterial = findViewById(R.id.toggle_frame)
        frameToggle.setOnCheckedChangeListener { _, isChecked ->
            arCore.toggleFrame(isChecked)
        }
        val detectorToggle: SwitchMaterial = findViewById(R.id.toggle_detector)
        detectorToggle.setOnCheckedChangeListener { _, isChecked ->
            cameraView.disableView() // Avoid errors inbetween changing the feature detector
            arCore.toggleDetector(isChecked)
            cameraView.enableView()
        }
        val menuButton: ImageButton = findViewById(R.id.expand_button)
        menuButton.setOnClickListener {
            if (menuHidden) {
                expandButton.setImageResource(R.drawable.ic_expand_more)
                expandButton.setBackgroundColor(Color.parseColor("#a0ffffff"))
                menu.animate().translationY(-menu.height.toFloat())
                menuHidden = false
            } else {
                expandButton.setImageResource(R.drawable.ic_expand_less)
                expandButton.setBackgroundColor(Color.TRANSPARENT)
                menu.animate().translationY(0f)
                menuHidden = true
            }
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        imageMat = Mat(width, height, CvType.CV_8UC4)
        val ratio = resources.displayMetrics.widthPixels / height
        imageView.layoutParams = ViewGroup.LayoutParams(height * ratio, width * ratio)
    }

    override fun onCameraViewStopped() {
        imageMat.release()
    }
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        imageMat = inputFrame!!.rgba()
        imageMat = arCore.android_ar(imageMat)
        Core.flip(imageMat, imageMat, 1);
        val imageMat2 = imageMat.t()
        Core.flip(imageMat2, imageMat2, -1);
        val bitmap =
            Bitmap.createBitmap(imageMat2.cols(), imageMat2.rows(), Bitmap.Config.RGB_565)
        matToBitmap(imageMat2, bitmap)
        runOnUiThread {
            imageView.setImageBitmap(bitmap)
        }
        return imageMat
    }

    override fun getCameraViewList(): MutableList<out CameraBridgeViewBase> {
        return Collections.singletonList(cameraView)
    }

    override fun onResume() {
        super.onResume()
        cameraView.enableView()
    }

    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }

    private fun getPermission() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 102)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty()) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                getPermission()
            }
        }
    }
}