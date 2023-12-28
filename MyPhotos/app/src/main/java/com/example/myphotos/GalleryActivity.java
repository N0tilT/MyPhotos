package com.example.myphotos;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {

    ArrayList<GalleryItem> galleryItemArrayList;
    RecyclerView mRecyclerView;
    RecyclerView.LayoutManager mLayoutManager;
    RecyclerView.Adapter mAdapter;

    private static final ObjectMapper mapper = new ObjectMapper();
    ImageButton backBtn;
    ImageButton gotoAddBtn;
    private int userId;
    private String userName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        mRecyclerView = findViewById(R.id.recycler_gallery_view);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        backBtn = findViewById(R.id.back_button);
        gotoAddBtn = findViewById(R.id.go_to_add_button);


        galleryItemArrayList = new ArrayList<>();
        Intent intent = getIntent();

        userId = intent.getIntExtra("user_id",-1);
        userName = intent.getStringExtra("user_name");

        GetImages(userId);

        backBtn.setOnClickListener((View.OnClickListener) view -> {
            Intent MainIntent = new Intent(GalleryActivity.this, MainActivity.class);
            MainIntent.putExtra("user_id",userId);
            startActivity(MainIntent);
        });
        gotoAddBtn.setOnClickListener((View.OnClickListener) view -> {
            Intent addIntent = new Intent(GalleryActivity.this, AddPhotoActivity.class);
            addIntent.putExtra("user_id",userId);
            startActivity(addIntent);
        });
    }

    private void GetImages(int userId) {
        new Thread(() -> {
            InetSocketAddress sa = new InetSocketAddress(LoginActivity.host, LoginActivity.port);
            try {
                Socket socket = new Socket();
                socket.connect(sa, 5000);
                socket.setReceiveBufferSize(1024*10);

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

                out.write("/getImages@"+userId);
                out.flush();

                in = new InputStreamReader(socket.getInputStream());
                buf = new BufferedReader(in);
                StringBuilder tmp = new StringBuilder();
                response_string = buf.readLine();
                tmp.append(response_string);



                socket.close();

                StringBuilder builder = new StringBuilder();
                int parenthesesCounter = 0;
                for (int j = 0; j < tmp.length(); j++) {
                    if(tmp.charAt(j) == '{'){
                        parenthesesCounter++;
                    }
                    if(tmp.charAt(j) == '}'){
                        parenthesesCounter--;
                    }
                    if(parenthesesCounter>1) {
                        if (tmp.charAt(j) == '\"')
                        {
                            builder.append('\\');
                        }
                    }
                    builder.append(tmp.charAt(j));
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
                        try {
                            if(!response.getMessage().equals("Not found")){
                                if(response.getMessage().contains("[") || response.getMessage().contains("]")){
                                    this.galleryItemArrayList.addAll(mapper.readValue(response.getMessage(), new TypeReference<ArrayList<GalleryItem>>() {}));
                                }
                                else{
                                    this.galleryItemArrayList.add(mapper.readValue(response.getMessage(), new TypeReference<GalleryItem>() {}));
                                }

                            }
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    mRecyclerView.setHasFixedSize(true);
                    mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
                    mAdapter = new GalleryAdapter(this, galleryItemArrayList);
                    mRecyclerView.setLayoutManager(mLayoutManager);
                    mRecyclerView.setAdapter(mAdapter);
                });
                socket.close();

            } catch (Exception ex) {
                ex.printStackTrace();

            }
        }).start();
    }
}