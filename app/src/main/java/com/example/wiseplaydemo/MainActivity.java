/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.wiseplaydemo;

import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity_License";

    private static final String UTF_8 = "UTF-8";

    /**
     * Wiseplay DRM UUID
     */
    private static final UUID WISEPLAY_DRM_UUID = new UUID(0x3d5e6d359b9a41e8L, 0xb843dd3c6e72c42cL);

    /**
     * Wiseplay PSSH box
     */
    private static final String PSSH_BOX = "AAAAo3Bzc2gAAAAAPV5tNZuaQei4Qz88bnI/LAAAAIN7InZlcnNpb24iOiJWMS4wIiwiY29udGVudElEIjoiTVRBd01EQXhNREV5TXpRMU5qYzRPUT09Iiwia2lkcyI6WyJOelpsTnpSaU56YzBaREF4TkRSaU1XSXhPRE5tTlRnME1ERTRabVEzTVRrPSJdLCJlbnNjaGVtYSI6ImNlbmMifQ==";

    /**
     * button of get license
     */
    private Button downOnlineLicense;

    /**
     * button of get offline license
     */
    private Button downOfflineLicense;

    /**
     * button of restore offline license
     */
    private Button useOfflineLicense;

    /**
     * button of delete offline license
     */
    private Button deleteLicense;

    /**
     * url of get license
     */
    private String licenseUrl = "http://117.78.34.30:8000/wisePlayDrm/getLicense";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        downOnlineLicense = findViewById(R.id.down_online_license);
        downOfflineLicense = findViewById(R.id.down_offline_license);
        useOfflineLicense = findViewById(R.id.use_offline_license);
        deleteLicense = findViewById(R.id.delete_license);

        downOnlineLicense.setOnClickListener(this);
        downOfflineLicense.setOnClickListener(this);
        useOfflineLicense.setOnClickListener(this);
        deleteLicense.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.down_online_license:
                getOnlineLicense();
                // After obtaining the license, you can play the drm movie with license.
                break;

            case R.id.down_offline_license:
                getOfflineLicense();
                break;

            case R.id.use_offline_license:
                useOfflineLicense();
                // After using the offline license, you can play the drm movie with license.
                break;

            case R.id.delete_license:
                deleteLicense();
                break;
            default:
                break;
        }
    }

    private void deleteLicense() {
        if (!MediaDrm.isCryptoSchemeSupported(WISEPLAY_DRM_UUID)) {
            Toast.makeText(MainActivity.this, "The device does not support wiseplay drm.", Toast.LENGTH_SHORT).show();
            return;
        }
        HttpURLConnection connection = null;
        BufferedWriter writer = null;
        InputStream inputStream = null;
        try {
            MediaDrm mediaDrm = new MediaDrm(WISEPLAY_DRM_UUID);
            byte[] sessionID = mediaDrm.openSession();

            // Obtain the keySetId of the license from the SharedPreferences.
            byte[] keySetIdToDelete = new byte[0];
            MediaDrm.KeyRequest keyRequest =
                    mediaDrm.getKeyRequest(keySetIdToDelete, null, null, MediaDrm.KEY_TYPE_RELEASE, null);

            byte[] requestData = keyRequest.getData();
            String licenseServerUrl = keyRequest.getDefaultUrl();

            URL url = new URL(licenseServerUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("content-type", "application/json");
            connection.connect();

            String base64Payload = new String(Base64.encode(requestData, Base64.DEFAULT), UTF_8);
            String body = "{\"payload\":\"" + base64Payload + "\",\"psshBox\":\"\",\"keyType\":\"" + MediaDrm.KEY_TYPE_RELEASE + "\"}";
            writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(body);
            writer.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                byte[] response = toByteArray(inputStream);
                inputStream.close();
                connection.disconnect();

                String jsonStr = new String(response, "UTF-8");
                JSONObject jsonObject = new JSONObject(jsonStr);
                String license = jsonObject.getString("payload");
                byte[] base64decodeLicense = Base64.decode(license, Base64.DEFAULT);
                mediaDrm.provideKeyResponse(sessionID, base64decodeLicense);

                Toast.makeText(MainActivity.this, "delete license success.", Toast.LENGTH_SHORT).show();
            }
            // Delete the cached license relationship from the sharedPreferences.

        } catch (NotProvisionedException | UnsupportedSchemeException | ResourceBusyException | IOException | DeniedByServerException | JSONException e) {
            e.printStackTrace();
            Log.i(TAG, "delete license failed: " + e.getMessage());
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void useOfflineLicense() {
        if (!MediaDrm.isCryptoSchemeSupported(WISEPLAY_DRM_UUID)) {
            Toast.makeText(MainActivity.this, "The device does not support wiseplay drm.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Obtain the keySetId of the license from the SharedPreferences.
        byte[] keySetIdToRestore = new byte[0];
        try {
            MediaDrm mediaDrm = new MediaDrm(WISEPLAY_DRM_UUID);
            byte[] sessionID = mediaDrm.openSession();
            mediaDrm.restoreKeys(sessionID, keySetIdToRestore);

            // If resotreKeys succeed, you can play it with this license.

            Toast.makeText(MainActivity.this, "use offline license success.", Toast.LENGTH_SHORT).show();
        } catch (NotProvisionedException | UnsupportedSchemeException | ResourceBusyException e) {
            e.printStackTrace();
            Log.i(TAG, "use offline license failed: " + e.getMessage());
        }
    }

    private void getOfflineLicense() {
        if (!MediaDrm.isCryptoSchemeSupported(WISEPLAY_DRM_UUID)) {
            Toast.makeText(MainActivity.this, "The device does not support wiseplay drm.", Toast.LENGTH_SHORT).show();
            return;
        }
        HttpURLConnection connection = null;
        BufferedWriter writer = null;
        InputStream inputStream = null;
        try {
            // Obtain the value of pssh box of the movie whose UUID is wiseplay drm from the movie.
            byte[] initData = Base64.decode(PSSH_BOX, Base64.DEFAULT);
            String mimeType = "video/mp4";
            MediaDrm mediaDrm = new MediaDrm(WISEPLAY_DRM_UUID);
            byte[] sessionID = mediaDrm.openSession();
            MediaDrm.KeyRequest keyRequest =
                    mediaDrm.getKeyRequest(sessionID, initData, mimeType, MediaDrm.KEY_TYPE_OFFLINE, null);

            byte[] requestData = keyRequest.getData();
            String licenseServerUrl = licenseUrl;
            URL url = new URL(licenseServerUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("content-type", "application/json");
            connection.connect();

            String base64Payload = new String(Base64.encode(requestData, Base64.DEFAULT), UTF_8);
            String body = "{\"payload\":\"" + base64Payload + "\",\"psshBox\":\"" + PSSH_BOX + "\",\"keyType\":\"" + MediaDrm.KEY_TYPE_OFFLINE + "\"}";
            writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(body);
            writer.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                byte[] response = toByteArray(inputStream);
                inputStream.close();
                connection.disconnect();
                String jsonStr = new String(response, "UTF-8");
                JSONObject jsonObject = new JSONObject(jsonStr);
                String license = jsonObject.getString("playload");
                byte[] base64decodeLicense = Base64.decode(license, Base64.DEFAULT);
                byte[]  keySetId = mediaDrm.provideKeyResponse(sessionID, base64decodeLicense);
                // Save the keySetId of the offline license to the SharedPreferences.

                Toast.makeText(MainActivity.this, "get offline license success.", Toast.LENGTH_SHORT).show();
            }
        } catch (NotProvisionedException | UnsupportedSchemeException | ResourceBusyException | IOException | DeniedByServerException | JSONException e) {
            e.printStackTrace();
            Log.i(TAG, "get offline license failed: " + e.getMessage());
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void getOnlineLicense() {
        if (!MediaDrm.isCryptoSchemeSupported(WISEPLAY_DRM_UUID)) {
            Toast.makeText(MainActivity.this, "The device does not support wiseplay drm.", Toast.LENGTH_SHORT).show();
            return;
        }
        HttpURLConnection connection = null;
        BufferedWriter writer = null;
        InputStream inputStream = null;
        try {
            // Value of pssh box obtained from the movie.
            byte[] initData = Base64.decode(PSSH_BOX, Base64.DEFAULT);

            // Obtain the value of pssh box of the movie whose UUID is wiseplay drm from the movie.
            String mimeType = "video/mp4";
            MediaDrm mediaDrm = new MediaDrm(WISEPLAY_DRM_UUID);
            byte[] sessionID = mediaDrm.openSession();
            MediaDrm.KeyRequest keyRequest =
                    mediaDrm.getKeyRequest(sessionID, initData, mimeType, MediaDrm.KEY_TYPE_STREAMING, null);

            byte[] requestData = keyRequest.getData();
            String licenseServerUrl = licenseUrl;
            URL url = new URL(licenseServerUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("content-type", "application/json");
            connection.connect();

            String base64Payload = new String(Base64.encode(requestData, Base64.DEFAULT), UTF_8);
            String body = "{\"payload\":\"" + base64Payload + "\",\"psshBox\":\"" + PSSH_BOX + "\",\"keyType\":\"" + MediaDrm.KEY_TYPE_STREAMING + "\"}";
            writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(body);
            writer.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                byte[] response = toByteArray(inputStream);
                inputStream.close();
                connection.disconnect();
                String jsonStr = new String(response, "UTF-8");
                JSONObject jsonObject = new JSONObject(jsonStr);
                String license = jsonObject.getString("payload");
                byte[] base64decodeLicense = Base64.decode(license, Base64.DEFAULT);
                mediaDrm.provideKeyResponse(sessionID, base64decodeLicense);

                Toast.makeText(MainActivity.this, "get online license success.", Toast.LENGTH_SHORT).show();
            }
        } catch (NotProvisionedException | IOException | DeniedByServerException | ResourceBusyException | UnsupportedSchemeException | JSONException e) {
            e.printStackTrace();
            Log.i(TAG, "get online license failed: " + e.getMessage());
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024 * 4];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }
}