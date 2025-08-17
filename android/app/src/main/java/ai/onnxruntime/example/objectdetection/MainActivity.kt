package ai.onnxruntime.example.objectdetection

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.io.InputStream


class MainActivity : AppCompatActivity() {
    private lateinit var bitmap: Bitmap
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private lateinit var ortSession: OrtSession
    private lateinit var inputImage: ImageView
    private lateinit var outputImage: ImageView
    private lateinit var objectDetectionButton: Button
    private lateinit var chooseImageButton: Button
    private lateinit var classes: List<String>
    private lateinit var modelPath: String
    private lateinit var labelPath: String
    private lateinit var imagePath: String
    private lateinit var outputImagePath: String
    private lateinit var objectsPath: String
    private lateinit var printPath: String

    private fun loadBitmap(path: String): Bitmap {
        return BitmapFactory.decodeFile(path)
    }

    private fun saveBitmap(bitmap: Bitmap, path: String) {
        val file = File(path)
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    }

    private fun processNoUI(): Boolean {
        if (imagePath.isNotEmpty()) {
            bitmap = loadBitmap(imagePath)

            try {
                if (printPath.isNotEmpty()) File(printPath).appendText("Perform ObjectDetection.\n")
                performObjectDetection(ortSession, true)
                if (printPath.isNotEmpty()) File(printPath).appendText("ObjectDetection performed!\n")
                //Toast.makeText(baseContext, "ObjectDetection performed!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Exception caught when perform ObjectDetection", e)
                Toast.makeText(baseContext, "Failed to perform ObjectDetection", Toast.LENGTH_SHORT)
                    .show()
            }

            return true
        }
        return false
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = intent
        modelPath = intent.getStringExtra("arg1") ?: ""
        if (modelPath.isEmpty()) {
            modelPath = Environment.getExternalStorageDirectory().path + "/Download/model.bin"
        }
        labelPath = intent.getStringExtra("arg2") ?: ""
        if (labelPath.isEmpty()) {
            labelPath = Environment.getExternalStorageDirectory().path + "/Download/label.txt"
        }
        imagePath = intent.getStringExtra("arg3") ?: ""
        outputImagePath = intent.getStringExtra("arg4") ?: ""
        objectsPath = intent.getStringExtra("arg5") ?: ""
        printPath = intent.getStringExtra("arg6") ?: ""

        classes = readClasses();
        // Initialize Ort Session and register the onnxruntime extensions package that contains the custom operators.
        // Note: These are used to decode the input image into the format the original model requires,
        // and to encode the model output into png format
        val sessionOptions: OrtSession.SessionOptions = OrtSession.SessionOptions()
        sessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
        val model = readModel()
        if (model == null) {
            finish()
            return
        }
        ortSession = ortEnv.createSession(model, sessionOptions)
        if (printPath.isNotEmpty()) File(printPath).appendText("createSession")
        
        if (processNoUI()) return

        inputImage = findViewById(R.id.imageView1)
        outputImage = findViewById(R.id.imageView2)
        objectDetectionButton = findViewById(R.id.object_detection_button)
        chooseImageButton = findViewById(R.id.choose_image_button)

        bitmap = BitmapFactory.decodeStream(readInputImage())
        inputImage.setImageBitmap(bitmap)

//        val getContent =
//            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
//                bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
//                hasBitmap = true;
//                inputImage.setImageBitmap(bitmap)
//            }

        chooseImageButton.setOnClickListener {
//            getContent.launch("image/*")
            // calling intent on below line.
            val intent = Intent()
            intent.setAction(Intent.ACTION_GET_CONTENT)
            intent.setType("image/*")
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), 1);
        }

        objectDetectionButton.setOnClickListener {
            try {
                performObjectDetection(ortSession, false)
                Toast.makeText(baseContext, "ObjectDetection performed!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Exception caught when perform ObjectDetection", e)
                Toast.makeText(baseContext, "Failed to perform ObjectDetection", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode === RESULT_OK) {
            // compare the resultCode with the
            // constant
            if (requestCode === 1) {
                // Get the url of the image from data
                val selectedImageUri: Uri = data?.data!!
                if (null != selectedImageUri) {
                    // update the image view in the layout
                    bitmap =
                        MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImageUri)
                    inputImage.setImageBitmap(bitmap)
//                    inputImage.setImageURI(selectedImageUri)

                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ortEnv.close()
        ortSession.close()
    }

    private fun updateUI(result: Result, noUI: Boolean) {
        var draw = false
        if (outputImagePath.isNotEmpty() || !noUI) draw = true
        lateinit var mutableBitmap: Bitmap
        lateinit var canvas: Canvas
        val paint = Paint()
        if (draw) {
            mutableBitmap = result.outputBitmap.copy(Bitmap.Config.ARGB_8888, true)
            canvas = Canvas(mutableBitmap)
            paint.color = Color.WHITE // Text Color
            paint.textSize = 28f // Text Size
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER) // Text Overlapping Pattern
            canvas.drawBitmap(mutableBitmap, 0.0f, 0.0f, paint)
        }
        var boxit = result.outputBox.iterator()
        val sb = StringBuilder()
        // x, y, w, h, rate, label
        while (boxit.hasNext()) {
            var box_info = boxit.next()
            if (draw) canvas.drawText(
                "%s:%.2f".format(classes[box_info[5].toInt()], box_info[4]),
                box_info[0] - box_info[2] / 2,
                box_info[1] - box_info[3] / 2,
                paint
            )
            sb.append(box_info[0])
            sb.append(' ')
            sb.append(box_info[1] - box_info[3] / 2)
            sb.append(' ')
            sb.append(box_info[0])
            sb.append(' ')
            sb.append(box_info[1] + box_info[3] / 2)
            sb.append(' ')
            sb.append(classes[box_info[5].toInt()])
            sb.appendLine()
        }
        val fileContent = sb.toString()
        if (objectsPath.isNotEmpty()) File(objectsPath).writeText(fileContent)
        if (outputImagePath.isNotEmpty()) saveBitmap(mutableBitmap, outputImagePath)
        if (!noUI) outputImage.setImageBitmap(mutableBitmap)
        else {
            setResult(RESULT_OK)
            finish()
        }
    }
    
    private fun readModel(): ByteArray? {
        if (printPath.isNotEmpty()) File(printPath).appendText("Read model from $modelPath\n")
        try {
            val data = File(modelPath).inputStream().readBytes()
            if (data.isEmpty()) {
                if (printPath.isNotEmpty()) File(printPath).appendText("modelPath empty: $modelPath\n")
                //Toast.makeText(this, "modelPath empty: $modelPath", Toast.LENGTH_SHORT).show()
                return null
            }
            if (printPath.isNotEmpty()) File(printPath).appendText("Model read!\n")
            return data
//        val modelID = R.raw.yolov8n_with_pre_post_processing
//        return resources.openRawResource(modelID).readBytes()
        } catch (e: IOException) {
            // Handle the IOException (e.g., file not found, permission denied)
            if (printPath.isNotEmpty()) File(printPath).appendText("Error reading the model file: ${e.message}")
            return null // Return null to indicate an error
        } catch (e: Exception) {
            // Catch any other unexpected exceptions
            if (printPath.isNotEmpty()) File(printPath).appendText("An unexpected error occurred: ${e.message}")
            return null
        }
    }

    private fun readClasses(): List<String> {
        // https://www.baeldung.com/kotlin/read-file
        val data = File(labelPath).bufferedReader().readLines()
        if (data.isEmpty()) {
            Toast.makeText(this, "label empty: $labelPath", Toast.LENGTH_SHORT).show()
        }
        return data
//        return resources.openRawResource(R.raw.classes).bufferedReader().readLines()
    }

    private fun bitmap2InputStream(bm: Bitmap): InputStream {
        val baos = ByteArrayOutputStream()
        bm.compress(CompressFormat.JPEG, 100, baos)
        val ist: InputStream = ByteArrayInputStream(baos.toByteArray())
        return ist
    }

    private fun readInputImage(): InputStream {
        return assets.open("test_object_detection_${0}.jpg")
    }

    private fun performObjectDetection(ortSession: OrtSession, noUI: Boolean) {
        var objDetector = ObjectDetector()
        if (!noUI) inputImage.setImageBitmap(bitmap)
        var result = objDetector.detect(bitmap, ortEnv, ortSession)
        updateUI(result, noUI);
    }

    companion object {
        const val TAG = "ORTObjectDetection"
    }
}
