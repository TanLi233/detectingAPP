package com.example.group9.detectingapp;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {
    private FusedLocationProviderClient mFusedLocationClient;
    private MapFragment mapFragment;
    private LatLng pos = new LatLng(0,0);
    private GoogleMap map;
    private TextView latlng;
    private TextView fusedLatlng;
    protected Location mLastLocation;
    private Button insert;
    private ArrayList<LatLng> dangerZone = new ArrayList<>();
    private ArrayList<Coordinate> coordinates = new ArrayList<>();
    private Thread thread;
    private GeometryFactory GF;
    private Polygon p;
//    private Criteria criteria;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        GF = new GeometryFactory();
        latlng = (TextView) findViewById(R.id.latlng);
        fusedLatlng = (TextView) findViewById(R.id.fusedLatlng);
        insert = (Button) findViewById(R.id.button_insert);
        insert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSMS();
            }
        });

    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            if (what == 1){
                Toast.makeText(MainActivity.this, "insert suceess!", Toast.LENGTH_SHORT).show();
            }else if (what == 2){
                Toast.makeText(MainActivity.this, "insert failed!", Toast.LENGTH_SHORT).show();
            }else if (what == 0){
//                Toast.makeText(MainActivity.this, "insert start!", Toast.LENGTH_SHORT).show();
            }
        }
    };

    public void onMapReady(final GoogleMap map) {
        this.map = map;
        initLocation();
        map.moveCamera(CameraUpdateFactory.newLatLng(pos));
        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.strokeColor(Color.RED)
                .strokeWidth(3)
                .fillColor(Color.argb(100, 255, 0, 0));

        getDangerZone();
        try {
            Thread.sleep(2000);
        }catch (Exception e){
            e.printStackTrace();
        }

        Coordinate[] c = new Coordinate[coordinates.size()];
        coordinates.toArray(c);
        p = GF.createPolygon(c);

        for (LatLng latlng: dangerZone) {
            polygonOptions.add(latlng);
        };
        polygonOptions.add(dangerZone.get(0));
        map.addPolygon(polygonOptions);
        try {
            map.setMyLocationEnabled(true);
            map.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location location) {
                    pos = new LatLng(location.getLatitude(),location.getLongitude());

                    latlng.setText(pos.latitude+"/"+pos.longitude);
                }
            });
        }catch (SecurityException e){
            e.printStackTrace();
        }

    }
    @Override
    protected void onPause() {
        super.onPause();
    }

    @SuppressWarnings("MissingPermission")
    private void initLocation(){
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        final Handler h = new Handler();
        h.postDelayed(new Runnable()
        {
            private long time = 0;

            @Override
            public void run()
            {
                updateLocation();
                // do stuff then
                // can call h again after work!

                time += 5000;
                h.postDelayed(this, 5000);
            }
        }, 5000); // 5 seconds delay
    }

    @SuppressWarnings("MissingPermission")
    private void updateLocation(){
        mFusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
//                            Toast.makeText(MainActivity.this, "location updated!", Toast.LENGTH_SHORT).show();
                            mLastLocation = task.getResult();
                            fusedLatlng.setText(mLastLocation.getLatitude()+"/"+mLastLocation.getLongitude());
                            insertdata();
                            if (ifIndanger()){
                                sendSMS();
                            }
                        } else {
                            fusedLatlng.setText("????/????");
                        }
                    }
                });
    }

    private void insertdata(){
        final double lat = mLastLocation.getLatitude();
        final double lng = mLastLocation.getLongitude();
        final long time = (new Date()).getTime();

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
            try{
                mHandler.sendEmptyMessage(0);
                URL url = new URL("http://10.4.3.11/save_location.php?time="+time+"&lat="+lat+"&lng="+lng);
                //获取连接对象,此时未建立连接
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                //设置请求方式为Get请求
                conn.setRequestMethod("GET");

                //设置连接超时
                conn.setConnectTimeout(10000);
                int code = conn.getResponseCode();
                if (HttpURLConnection.HTTP_OK == code) {
                    mHandler.sendEmptyMessage(1);
                }else{
                    mHandler.sendEmptyMessage(2);
                }

            }catch (Exception e){
                e.printStackTrace();
            }
            }
        });
        thread.start();
    }


    @Override
    public void onDestroy() {
        thread.interrupt();
        super.onDestroy();
    }
    private void sendSMS(){
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage("12168046647", null, "Subject entring danger zone", null, null);
            Toast.makeText(getApplicationContext(), "Message Sent",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(),ex.getMessage().toString(),
                    Toast.LENGTH_SHORT).show();
            ex.printStackTrace();
        }
    }

    private boolean ifIndanger(){
        Point xy = GF.createPoint(new Coordinate(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
        return p.contains(xy);
    }

    private void getDangerZone(){
       Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    mHandler.sendEmptyMessage(0);
                    URL url = new URL("http://10.4.3.11/get_dangerzone.php");
                    //获取连接对象,此时未建立连接
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    //设置请求方式为Get请求
                    conn.setRequestMethod("GET");

                    //设置连接超时
                    conn.setConnectTimeout(10000);
                    int code = conn.getResponseCode();
                    if (HttpURLConnection.HTTP_OK == code) {
                        InputStream in = conn.getInputStream();
                        String polygon = readStream(in);
                        String[] l = polygon.split(",");
                        for(int i = 0; i < l.length; i=i+2){
                            dangerZone.add(new LatLng(Float.parseFloat(l[i]),Float.parseFloat(l[i+1])));
                            coordinates.add(new Coordinate(Float.parseFloat(l[i]),Float.parseFloat(l[i+1])));
                        }
                        coordinates.add(new Coordinate(Float.parseFloat(l[0]),Float.parseFloat(l[1])));
                        in.close();
                        mHandler.sendEmptyMessage(1);
                    }else{
                        mHandler.sendEmptyMessage(2);
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }
            }
       });
        thread2.start();
    }
    private String readStream(InputStream in) {
        BufferedReader reader = null;
        StringBuffer response = new StringBuffer();
        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return response.toString();
    }

}