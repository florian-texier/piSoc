package imerir.scavengerhunt;

import android.Manifest;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.media.ExifInterface;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static imerir.scavengerhunt.MainActivity.PREFS;
import static imerir.scavengerhunt.MainActivity.TAG;
import static imerir.scavengerhunt.MainActivity.contURl;
import static java.lang.String.format;

public class SendPicture extends AppCompatActivity {

    private static final int FILE_SELECT_CODE = 0;
    TextView mDistance;
    Button select;
    Button sendPicture;
    Button back;
    ImageView image;
    RequestQueue mRequestQueue;
    File imgFile;
    infoPeriph infoPeri;
    boolean pictureSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_picture);
        select = findViewById(R.id.selectpicture);
        sendPicture = findViewById(R.id.button2);
        mDistance = findViewById(R.id.dist);
        image = findViewById(R.id.imageView);
        back = findViewById(R.id.backButton);

        mRequestQueue = Volley.newRequestQueue(this);

        infoPeri = infoPeriph.getInstance();

        pictureSelected = false;

        sendPicture.setEnabled(false);

        sendPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast toast = Toast.makeText(SendPicture.this, "Envoie de la photo, merci de patienter ", Toast.LENGTH_LONG);
                toast.show();
                httpPostImage(contURl+"/picture");

            }
        });

        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                showFileChooser();

            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                switchPager();

            }
        });

        Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mDistance.setText(format("%.2f", infoPeri.getDistance()) + " m");
                                if (infoPeri.getDistance()<3.0){
                                    mDistance.setTextColor(Color.GREEN);
                                    if (pictureSelected){
                                        sendPicture.setEnabled(true);
                                    }
                                    else{
                                        sendPicture.setEnabled(false);
                                    }
                                }
                                else if (infoPeri.getDistance() == 50.0){
                                    mDistance.setText("");
                                }
                                else{
                                    mDistance.setTextColor(Color.RED);
                                    sendPicture.setEnabled(false);

                                }
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG,e.getMessage());
                }
            }
        };

        t.start();
    }

    private void showFileChooser() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");      //all files
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select a File to Upload"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(SendPicture.this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    Log.i(TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = null;
                    try {
                        path = getPath(this, uri);
                    } catch (URISyntaxException e) {
                        Log.e(TAG, e.getMessage());                    }
                    Log.d(TAG, "File Path: " + path);

                    imgFile = new File(path);

                    if(imgFile.exists()){

                        Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                        image.setImageBitmap(myBitmap);
                        pictureSelected = true;

                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void httpPostImage(String url) {

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String deviceID = prefs.getString("DeviceID", null);

        String latitute = "";
        String longitude = "";
        try {
            final ExifInterface exifInterface = new ExifInterface(imgFile.getAbsolutePath());
            latitute = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            longitude = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);

        } catch (IOException e) {
            Log.e("TAG", e.getMessage());
        }

        String image64 = jpegTo64();

        String[] splitString = imgFile.getName().split("_");

        JSONObject postData = new JSONObject();
        try {
            postData.put("id_Equipe", deviceID);
            postData.put("name",splitString[0]);
            postData.put("latitute", latitute);
            postData.put("longitude", longitude);
            postData.put("image", image64);
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
        }

        Response.Listener<JSONObject> onSuccess = new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    Toast toast = Toast.makeText(SendPicture.this, "Photo analysée ! Vérifiez l'afficheur !", Toast.LENGTH_LONG);
                    toast.show();
                } catch (Exception e) {
                    Toast toast = Toast.makeText(SendPicture.this, "Erreur de lecture de l'image", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        };

        Response.ErrorListener onError = new Response.ErrorListener() {
            public void onErrorResponse(VolleyError error) {
                Toast toast = Toast.makeText(SendPicture.this, "Image incorrect", Toast.LENGTH_LONG);
                toast.show();
                Log.i("Message Recu :","Erreur lors de la requête");
            }
        };



        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData, onSuccess, onError);
        request.setRetryPolicy(new DefaultRetryPolicy(10000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mRequestQueue.add(request);
    }
    public static String getPath(Context context, Uri uri) throws URISyntaxException {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor;

            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
            }
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }
    public String jpegTo64(){
        Bitmap myBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()),200,200,true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    void switchPager() {
        finish();
        Intent i = new Intent(SendPicture.this, ListeActivity.class);
        SendPicture.this.startActivity(i);
    }

}
