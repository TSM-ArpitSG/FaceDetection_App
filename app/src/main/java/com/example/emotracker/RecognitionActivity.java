package com.example.emotracker;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.emotracker.face_recognition.FaceClassifier;
import com.example.emotracker.face_recognition.TFLiteFaceRecognition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

public class RecognitionActivity extends AppCompatActivity {
    CardView galleryCard, cameraCard;
    ImageView imageView;
    Uri image_uri;
    public static final int PERMISSION_CODE = 100;

    // High-accuracy landmark detection and face classification
    FaceDetectorOptions highAccuracyOpts =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build();
    FaceDetector detector;
    FaceClassifier faceClassifier;
    ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        image_uri = result.getData().getData();
                        Bitmap inputImage = uriToBitmap(image_uri);
                        Bitmap rotated = rotateBitmap(inputImage);
                        imageView.setImageBitmap(rotated);
                        performFaceDetection(rotated);
                    }
                }
            });

    ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Bitmap inputImage = uriToBitmap(image_uri);
                        Bitmap rotated = rotateBitmap(inputImage);
                        imageView.setImageBitmap(rotated);
                        performFaceDetection(rotated);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);
        galleryCard = findViewById(R.id.gallerycard);
        cameraCard = findViewById(R.id.cameracard);
        imageView = findViewById(R.id.imageView2);

        galleryCard.setOnClickListener(view -> {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryActivityResultLauncher.launch(galleryIntent);
        });


        cameraCard.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!allPermissionsGranted()) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_CODE);
                } else {
                    openCamera();
                }
            } else {
                openCamera();
            }
        });

        findViewById(R.id.button_video).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RecognitionActivity.this, VideoActivity.class);
                startActivity(intent);
            }
        });

        detector = FaceDetection.getClient(highAccuracyOpts);

        try {
            faceClassifier = TFLiteFaceRecognition.create(getAssets(),"facenet.tflite",160,false,getApplicationContext());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean allPermissionsGranted() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        cameraActivityResultLauncher.launch(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input) {
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
        }
        Log.d("tryOrientation", orientation + "");
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(orientation);
        Bitmap cropped = Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), rotationMatrix, true);
        return cropped;
    }
    Canvas canvas;
    public void performFaceDetection(Bitmap input){
        Bitmap mutableBmp=input.copy(Bitmap.Config.ARGB_8888,true);
        canvas = new Canvas(mutableBmp);
        InputImage image = InputImage.fromBitmap(input, 0);
        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<com.google.mlkit.vision.face.Face> faces) {
                                        // Task completed successfully
                                        // Here, you can handle the detected faces
                                        Log.d("FaceDetection", "length: " + faces.size());
                                        for (com.google.mlkit.vision.face.Face face : faces) {
                                            Rect bounds = face.getBoundingBox();
                                            Paint p1 = new Paint();
                                            p1.setColor(Color.GREEN);
                                            p1.setStyle(Paint.Style.STROKE);
                                            p1.setStrokeWidth(5);
                                            performFaceRecognition(bounds,input);
                                            canvas.drawRect(bounds,p1);

                                        }
                                        imageView.setImageBitmap(mutableBmp);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
//                                        Toast.makeText(RecognitionActivity.this, "No Faces Found", Toast.LENGTH_SHORT).show();
                                        Log.e("FaceDetection", "Failed to detect faces", e);
                                    }
                                });
    }

    public void performFaceRecognition(Rect bound,Bitmap input){
        if(bound.top<0) bound.top=0;
        if(bound.left<0) bound.left=0;
        if(bound.right>input.getWidth())bound.right=input.getWidth()-1;
        if(bound.bottom>input.getHeight())bound.bottom=input.getHeight()-1;
        Bitmap croppedFace = Bitmap.createBitmap(input,bound.left,bound.top,bound.width(),bound.height());
//        imageView.setImageBitmap(croppedFace);
        croppedFace = Bitmap.createScaledBitmap(croppedFace,160,160,false);
        FaceClassifier.Recognition recognition = faceClassifier.recognizeImage(croppedFace,false);
        if(recognition!=null){
        Log.d("TryFR", recognition.getTitle()+" "+ recognition.getDistance());
            if(recognition.getDistance()<1){
                Paint p1 = new Paint();
                p1.setColor(Color.RED);
                p1.setTextSize(200);
                canvas.drawText(recognition.getTitle(),bound.left,bound.top,p1);

            }
/*
            else{
                Toast.makeText(this, "Unregistered Faces", Toast.LENGTH_SHORT).show();
            }
*/
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
}
