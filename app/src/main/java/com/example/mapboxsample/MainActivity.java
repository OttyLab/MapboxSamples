package com.example.mapboxsample;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {
    private static final int INITIAL_ZOOM = 15;
    private static final int INTERVAL = 60000;

    //
    // To register map components, string ID are used
    //
    private static final String SOURCE_ID = "SOURCE_ID";
    private static final String LAYER_ID = "LAYER_ID";
    private static final String ICON_ID = "ICON_ID";

    //
    // Keys to store data permanently
    //
    private static final String SAVE_KEY = "SAVE_KEY";
    private static final String RUNNING_KEY = "SAVE_KEY";

    //
    // UI
    //
    private static final String BUTTON_START = "Start";
    private static final String BUTTON_STOP = "Stop";

    private PermissionsManager permissionsManager;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Button actionButton;
    private Timer timer;
    private List<Feature> locations;
    private Handler handler;

    //
    // This flag is used to restore the running status when onResume is called
    //
    private boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this,
                "pk.eyJ1Ijoib3R0eWxhYiIsImEiOiJjazl0a2R2dHUxaDJnM2VwbmR0NjduZHZ2In0.czH7iUVBp5QiWccbbX4lxg");
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        //
        // Parameter requires OnMapReadyCallback interface. This class implements the interface
        //
        mapView.getMapAsync(this);

        //
        // Timer updates UI components and working thread needs to use Handler to control the UI.
        //
        handler = new Handler(Looper.getMainLooper());

        locations = new ArrayList<>();

        actionButton = findViewById(R.id.actionButton);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    stopTimer();
                    running = false;
                    return;
                }

                startTimer();
                running = true;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();

        //
        // Timer is stopped but running flag is not changed because the app wants to resume the running
        // status when onResume is called.
        //
        stopTimer();

        //
        // Store the current data into storage
        //
        saveLocations();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        mapView.onSaveInstanceState(state);
        state.putBoolean(RUNNING_KEY, running);
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        running = state.getBoolean(RUNNING_KEY);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        }
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        MainActivity.this.mapboxMap = mapboxMap;
        mapboxMap.setCameraPosition(new CameraPosition.Builder().zoom(INITIAL_ZOOM).build());

        mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);

                Bitmap bitmap = BitmapFactory.decodeResource(
                        MainActivity.this.getResources(), R.drawable.mapbox_marker_icon_default);
                style.addImage(ICON_ID, bitmap);
                style.addLayer(
                        new SymbolLayer(LAYER_ID, SOURCE_ID)
                                .withProperties(PropertyFactory.iconImage(ICON_ID), iconAllowOverlap(true)));

                //
                // This method restore the map components. So it must be called after the style
                // gets ready
                //
                loadLocations();

                //
                // If the app stopped with running status, resume the timer.
                //
                if (running) {
                    startTimer();
                }
            }
        });

    }

    private void enableLocationComponent(@NonNull Style style) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            LocationComponentActivationOptions activationOptions =
                    LocationComponentActivationOptions.builder(this, style)
                            .build();
            LocationComponent locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(activationOptions);
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.setRenderMode(RenderMode.GPS);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    private Feature getCurrentLocation()
    {
        LocationComponent locationComponent = mapboxMap.getLocationComponent();
        Location location = locationComponent.getLastKnownLocation();

        return Feature.fromGeometry(
                Point.fromLngLat(location.getLongitude(), location.getLatitude()));
    }

    private void addLocation(Feature location)
    {
        locations.add(location);
    }

    private void clearLocations()
    {
        locations = new ArrayList<>();
    }

    private void setSource() {
        FeatureCollection collection = FeatureCollection.fromFeatures(locations);
        Style temp = mapboxMap.getStyle();
        GeoJsonSource source = (GeoJsonSource) mapboxMap.getStyle().getSource(SOURCE_ID);

        //
        // For the first time, source is null.
        //
        if (source == null) {
            source = new GeoJsonSource(SOURCE_ID, collection);
            mapboxMap.getStyle().addSource(source);
        } else {
            source.setGeoJson(collection);
        }
    }

    private void saveLocations() {
        //
        // Feature supports to serialize into JSON and JSON array is created here
        // TODO: Check if GeoJsonSource has option to serialize into JSON directly
        //
        List<String> rawLocations = new ArrayList<>();
        for (Feature location: locations) {
            rawLocations.add(location.toJson());
        }
        String json = new JSONArray(rawLocations).toString();

        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(SAVE_KEY, json);
        editor.commit();
    }

    private void loadLocations() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String json = preferences.getString(SAVE_KEY, "");

        JSONArray rawLocations = null;
        List<Feature> locations = new ArrayList<>();

        try {
            rawLocations = new JSONArray(json);
            for (int i = 0; i < rawLocations.length(); i++) {
                locations.add(Feature.fromJson(rawLocations.getString(i)));
            }
        } catch (JSONException e) {
           return;
        }

        for (Feature feature : locations) {
            addLocation(feature);
        }

        setSource();
    }

    private void clearSavedLocations() {
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.clear().commit();
    }

    private void startTimer()
    {
        clearSavedLocations();
        clearLocations();
        loadLocations();

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable(){
                    @Override
                    public void run() {
                        addLocation(getCurrentLocation());
                        setSource();
                    }
                });
            }
        }, 0, INTERVAL);

        actionButton.setText(BUTTON_STOP);
    }

    private void stopTimer() {
        if (timer == null) {
            return;
        }

        timer.cancel();
        timer = null;
        actionButton.setText(BUTTON_START);
    }
}
