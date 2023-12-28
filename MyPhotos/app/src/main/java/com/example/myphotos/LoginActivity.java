package com.example.myphotos;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class LoginActivity extends AppCompatActivity {
    public static String host = "82.179.140.18";
    public static int port = 45138;
    public static String password;
    public static String login;

    private static final ObjectMapper mapper = new ObjectMapper();

    private EditText loginInput;
    private Button loginBtn;
    private Button backBtn;

    private EditText usernameInput, passwordInput;

    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loginBtn = findViewById(R.id.login_btn);
        loginInput = findViewById(R.id.login_input);
        passwordInput = findViewById(R.id.login_password_input);
        backBtn = findViewById(R.id.back_btn);

        loginBtn.setOnClickListener(view -> LoginAccount());
        backBtn.setOnClickListener(view -> {
            Intent MainIntent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(MainIntent);
        });

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private void LoginAccount() {
        login = loginInput.getText().toString();
        password = passwordInput.getText().toString();

        if(TextUtils.isEmpty(login)){
            Toast.makeText(this, "Введите логин", Toast.LENGTH_SHORT).show();
            return;
        }
        else if(TextUtils.isEmpty(password)){
            Toast.makeText(this, "Введите пароль", Toast.LENGTH_SHORT).show();
            return;
        }

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        byte[] hash = md.digest(password.getBytes());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            password = Base64.getEncoder().encodeToString(hash);
        }
        try {
            login();
        } catch (Exception e) {
            Intent backIntent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(backIntent);
        }

    }

    private void login() {
        new Thread(() -> {
            InetSocketAddress sa = new InetSocketAddress(host, port);
            try {
                Socket socket = new Socket();
                socket.connect(sa, 5000);
                socket.setReceiveBufferSize(4096);

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
                out.write("/login|"+login+"@"+password);
                out.flush();

                in = new InputStreamReader(socket.getInputStream());
                buf = new BufferedReader(in);
                StringBuilder tmp = new StringBuilder();
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
                        try {
                            this.user = mapper.readValue(response.getMessage(), User.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    else{
                        Toast.makeText(this, "Неверное имя пользователя или пароль.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent ScheduleIntent = new Intent(LoginActivity.this, GalleryActivity.class);
                    ScheduleIntent.putExtra("user_id", user.getUserId());
                    ScheduleIntent.putExtra("user_name", user.getUserLogin());
                    startActivity(ScheduleIntent);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }).start();
    }
}