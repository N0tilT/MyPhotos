package com.example.myphotos;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class AddPhotoActivity extends AppCompatActivity {

    private static final ObjectMapper mapper = new ObjectMapper();
    Button selectImage;
    Button addImage;
    Button backBtn;
    ImageView previewImage;
    private String filename;
    private String extension;
    private String timeStamp;
    private int userId;
    private String imageBytes = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_photo);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        backBtn = findViewById(R.id.back_button);
        addImage = findViewById(R.id.add_image_button);
        selectImage = findViewById(R.id.select_image_button);
        previewImage = findViewById(R.id.preview_image);

        Intent intent = getIntent();

        userId = intent.getIntExtra("user_id",-1);

        backBtn.setOnClickListener((View.OnClickListener) view -> {
            Intent MainIntent = new Intent(AddPhotoActivity.this, GalleryActivity.class);
            MainIntent.putExtra("user_id",userId);
            startActivity(MainIntent);
        });
        selectImage.setOnClickListener((View.OnClickListener) view -> imageChooser());
        addImage.setOnClickListener((View.OnClickListener) view -> AddImage(imageBytes,timeStamp,filename,extension,userId));
    }

    private void imageChooser() {

        Intent intent = new Intent();

        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        launchSomeActivity.launch(intent);
    }

    ActivityResultLauncher<Intent> launchSomeActivity
            = registerForActivityResult(
            new ActivityResultContracts
                    .StartActivityForResult(),
            result -> {
                if (result.getResultCode()
                        == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null
                            && data.getData() != null) {
                        Uri selectedImageUri = data.getData();
                        Bitmap selectedImageBitmap = null;
                        try {
                            selectedImageBitmap
                                    = MediaStore.Images.Media.getBitmap(
                                    this.getContentResolver(),
                                    selectedImageUri);
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }

                        String path = selectedImageUri.getPath();
                        String[] segments = path.split(File.separator);
                        filename = segments[segments.length - 1];

                        String uri = selectedImageUri.toString();
                        if(uri.contains(".")) {
                            extension = uri.substring(uri.lastIndexOf(".")+1);
                        }

                        Date date = new Date();
                        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                        timeStamp =  formatter.format(date);

                        imageBytes = Arrays.toString(BitmapUtils.convertBitmapToByteArray(selectedImageBitmap));

                        previewImage.setImageBitmap(
                                selectedImageBitmap);
                    }
                }
            });

    private void AddImage(String imageBytes, String filename, String extension, String timeStamp,int userId) {
        new Thread(() -> {
            InetSocketAddress sa = new InetSocketAddress(LoginActivity.host, LoginActivity.port);
            try {
                Socket socket = new Socket();
                socket.connect(sa, 5000);
                socket.setReceiveBufferSize(1024*10);
                socket.setSendBufferSize(1024*10);

                OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

                out.write("137Meow137");
                out.flush();

                InputStreamReader in = new InputStreamReader(socket.getInputStream());
                BufferedReader buf = new BufferedReader(in);
                String response_string = buf.readLine();
                if(!response_string.equalsIgnoreCase("Welcome to the server")){
                    socket.close();
                    throw new Exception();
                }


                out = new OutputStreamWriter(socket.getOutputStream());

                GalleryItem subjectToAdd = new GalleryItem(imageBytes,timeStamp,filename, extension, userId);
                String serialized = mapper.writeValueAsString(subjectToAdd);

                int squreParenthesesCounter = 0;

                StringBuilder tmp = new StringBuilder();
                tmp.append('{');
                System.out.println(serialized.length());
                for (int i = 1; i < serialized.length()-1; i++) {
                    char cur = serialized.charAt(i);
                    if(cur == '['){
                        squreParenthesesCounter++;
                        tmp.append(cur);
                    }
                    else if(cur == ':' && serialized.charAt(i+1) != '\"'){
                        tmp.append(cur);
                        tmp.append('\"');
                    }
                    else if(cur == ',' && serialized.charAt(i-1) != '\"' && squreParenthesesCounter < 1){
                        tmp.append('\"');
                        tmp.append(cur);
                    }
                    else{
                        tmp.append(cur);
                    }
                }
                if(serialized.charAt(serialized.length()-1)!='\"'){
                    tmp.append('\"');
                }
                tmp.append('}');
                String stringify = tmp.toString();

                out.write("/postImage|"+stringify);
                out.flush();
                in = new InputStreamReader(socket.getInputStream());
                buf = new BufferedReader(in);
                tmp = new StringBuilder();
                response_string = buf.readLine();
                tmp.append(response_string);
                socket.close();
                StringBuilder builder = new StringBuilder();
                int parenthesesCounter = 0;
                for (int i = 0; i < tmp.length(); i++) {
                    if(tmp.charAt(i) == '{'){
                        parenthesesCounter++;
                    }
                    if(tmp.charAt(i) == '}'){
                        parenthesesCounter--;
                    }
                    if(parenthesesCounter>1) {
                        if (tmp.charAt(i) == '\"')
                        {
                            builder.append('\\');
                        }
                    }
                    builder.append(tmp.charAt(i));
                }
                String finalResponse_string = builder.toString();
                runOnUiThread(() -> {
                    Response response;
                    try {
                        response = mapper.readValue(finalResponse_string,Response.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    if(response.getType().equals("SUCCESS")) {
                        Toast.makeText(this,"Предмет успешно добавлен в расписание!", Toast.LENGTH_SHORT);
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();

            }
        }).start();
    }
}