package com.example.emotracker;



import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.emotracker.face_recognition.FaceClassifier;
import com.example.emotracker.face_recognition.TFLiteFaceRecognition;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;

import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.util.List;

public class VideoActivity extends AppCompatActivity {
    private PlayerView playerView;
    private ExoPlayer player;
    private ImageView imageOverlay;
    private Button btnSelectVideo;
    private ActivityResultLauncher<Intent> pickVideoResultLauncher;
    private ActivityResultLauncher<Intent> captureVideoResultLauncher;
    FaceDetectorOptions highAccuracyOpts =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build();
    FaceDetector detector;
    FaceClassifier faceClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        playerView = findViewById(R.id.player_view);
        imageOverlay = findViewById(R.id.imageOverlay);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);

        initializePlayer();
        setupActivityResultLaunchers();

        btnSelectVideo.setOnClickListener(v -> showVideoSourceDialog());
    }

    private void setupActivityResultLaunchers() {
        pickVideoResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri videoUri = result.getData().getData();
                        playVideo(videoUri);
                    }
                });

        captureVideoResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri videoUri = result.getData().getData();
                        playVideo(videoUri);
                    }
                });

        detector = FaceDetection.getClient(highAccuracyOpts);

        try {
            faceClassifier = TFLiteFaceRecognition.create(getAssets(),"facenet.tflite",160,false,getApplicationContext());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void showVideoSourceDialog() {
        CharSequence[] options = {"Choose from Gallery", "Record Video"};
        new AlertDialog.Builder(this)
                .setTitle("Select or Record Video")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                        pickVideoResultLauncher.launch(pickIntent);
                    } else {
                        Intent captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                        captureVideoResultLauncher.launch(captureIntent);
                    }
                })
                .show();
    }


    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.sample_video);
        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
        player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE); // Loop the video



        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!isPlaying) {
                    Bitmap frame = getBitmapFromPlayer();
                    if (frame != null) {
                        performFaceDetection(frame);
                    } else {
                        Log.e("VideoActivity", "Failed to capture frame or frame is null");
                    }
                }else{
                            imageOverlay.setVisibility(View.GONE);
                }
            }
        });

    }


    public void performFaceDetection(Bitmap input){
        Bitmap mutableBmp=input.copy(Bitmap.Config.ARGB_8888,true);
        canvas = new Canvas(mutableBmp);
        InputImage image = InputImage.fromBitmap(input, 0);
        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                faces -> {
                                    // Task completed successfully
                                    // Here, you can handle the detected faces
                                    Log.d("FaceDetection", "length: " + faces.size());
                                    for (Face face : faces) {
                                        Rect bounds = face.getBoundingBox();
//                                        Paint p1 = new Paint();
//                                        p1.setColor(Color.GREEN);
//                                        p1.setStyle(Paint.Style.STROKE);
//                                        p1.setStrokeWidth(5);
                                        performFaceRecognition(bounds,input);
//                                        canvas.drawRect(bounds,p1);

                                    }
                                    runOnUiThread(() -> {
                                        imageOverlay.setImageBitmap(mutableBmp);
                                        imageOverlay.setVisibility(View.VISIBLE);
                                    });
                                })
                        .addOnFailureListener(
                                e -> {
//                                        Toast.makeText(RecognitionActivity.this, "No Faces Found", Toast.LENGTH_SHORT).show();
                                    Log.e("FaceDetection", "Failed to detect faces", e);
                                });
    }

    Canvas canvas;
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
                p1.setColor(Color.GREEN);
                p1.setStyle(Paint.Style.STROKE);
                p1.setStrokeWidth(5);
                canvas.drawRect(bound,p1);
                Paint p2 = new Paint();
                p2.setColor(Color.WHITE);
                p2.setTextSize(70);
                canvas.drawText(recognition.getTitle(),bound.left,bound.top,p2);

            }
        }
    }
    private Bitmap getBitmapFromPlayer() {
        TextureView textureView = (TextureView) playerView.getVideoSurfaceView();
        if (textureView != null) {
            return textureView.getBitmap();
        }
        return null;
    }

    private void playVideo(Uri videoUri) {
        if (player != null) {
            player.setMediaItem(MediaItem.fromUri(videoUri));
            player.prepare();
            player.setPlayWhenReady(true);
            imageOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
