package com.example.hajken.fragments;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;
import com.example.hajken.BuildConfig;
import com.example.hajken.bluetooth.Bluetooth;
import com.example.hajken.bluetooth.ConnectionListener;
import com.example.hajken.bluetooth.VehicleListener;
import com.example.hajken.helpers.CustomDialogFragment;
import com.example.hajken.helpers.GPSTracker;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.maps.android.SphericalUtil;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.example.hajken.InterfaceMainActivity;
import com.example.hajken.bluetooth.BluetoothConnection;
import com.example.hajken.R;
import com.example.hajken.helpers.CoordinateConverter;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import es.dmoral.toasty.Toasty;
import static android.content.ContentValues.TAG;

public class GoogleMapsFragment extends Fragment  implements View.OnClickListener, OnMapReadyCallback,
        CustomDialogFragment.OnActionInterface, ConnectionListener, VehicleListener {

    private final int ZERO = 0;
    private final int SLOW = 1;
    private final int MED = 2;
    private final int FAST = 3;
    private final int MAX_SEEKBAR_LEVEL = 10;

    private MapView mMapView;
    private GeoApiContext mGeoApiContext = null;
    private Button sendToVehicleButton;
    private RadioGroup radioGroup;
    private RadioButton radioButton;
    private String instructions;
    private TextView amountOfLoops;
    private SeekBar seekBar;
    private Bluetooth mBluetooth;
    private Context mContext;
    private CustomDialogFragment mCustomDialog;
    private InterfaceMainActivity mInterfaceMainActivity;
    private CoordinateConverter mCoordinateConverter;
    private FragmentManager mFragmentManager;
    private boolean isRunning;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";

    //Markers for the map
    private Marker destinationMarker = null;
    private Marker carMarker = null;

    //Car color
    private static final float carMarkerColor = 210f;

    //Polyline object
    Polyline polyline = null;

    //ArrayList to hold coordinates of PolyLines
    List<LatLng> newDecodedPath = new ArrayList<>();

    //ArrayList to hold xy-coordinates of path
    ArrayList<PointF> pathToPointF;

    public GoogleMapsFragment() {
        // Required empty public constructor
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        this.mContext = context;


        //This might be moved to another method
        //Might be a singleton pattern?
        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(BuildConfig.apiKey)
                    .build();
        }

        mBluetooth = Bluetooth.getInstance(mContext);
        mCustomDialog = new CustomDialogFragment();
        mInterfaceMainActivity = (InterfaceMainActivity) getActivity();
        mCoordinateConverter = CoordinateConverter.getInstance(mContext);
        mFragmentManager = getFragmentManager();
        isRunning = false;
        BluetoothConnection.getInstance(mContext).registerVehicleListener(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_google_maps, container, false);

        //Creates the buttons
        sendToVehicleButton = view.findViewById(R.id.send_to_vehicle_button);
        amountOfLoops = view.findViewById(R.id.amount_of_repetitions);
        seekBar = view.findViewById(R.id.seekbar);
        sendToVehicleButton.setOnClickListener(this);

        sendToVehicleButton.setClickable(false);
        sendToVehicleButton.setActivated(false);

        //Speed changing
        radioGroup = view.findViewById(R.id.radio_group);

        //Set amount of repetitions beginning at zero
        amountOfLoops.setText(getString(R.string.amount_of_repetitions,Integer.toString(ZERO)));

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checkedRadioButton = group.findViewById(checkedId);
            boolean isChecked = checkedRadioButton.isChecked();

            if (isChecked) {
                checkButton(view);
            }
        });


        //Creating the MapView

        mMapView = new MapView(getContext());
        mMapView = view.findViewById(R.id.mapView);

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        mMapView.onCreate(mapViewBundle);
        mMapView.getMapAsync(this);

        seekBar.setMax(MAX_SEEKBAR_LEVEL);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                CoordinateConverter.getInstance(getContext()).setNrOfLoops(progress);
                amountOfLoops.setText(getString(R.string.amount_of_repetitions,Integer.toString(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        return view;
    }




    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);

        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }
        mMapView.onSaveInstanceState(mapViewBundle);
    }



    public void checkButton(View view) {
        int radioId = radioGroup.getCheckedRadioButtonId();
        radioButton = view.findViewById(radioId);

        switch (radioButton.getText().toString()) {
            case "Slow": {
                mCoordinateConverter.setSpeed(SLOW);
                break;
            }

            case "Medium": {
                mCoordinateConverter.setSpeed(MED);
                break;
            }

            case "Fast": {
                mCoordinateConverter.setSpeed(FAST);
                break;
            }
        }

    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {

            //This is the events that are associated with the buttons
            case R.id.send_to_vehicle_button: {


                    if(isRunning){
                        showStopDialog();
                    } else {
                        instructions = mCoordinateConverter.returnInstructions(getPathToPointFList());
                        showStartDialog();
                    }
                    break;
            }
        }
    }

    @Override
    public void controlVehicle(Boolean execute) {

        if (isRunning) {
            if (execute) {
                mBluetooth.stopCar(getString(R.string.stop_vehicle_instruction));
            }
        } else {
            if (execute) {
                if (instructions == null) {
                    Toasty.error(mContext,getString(R.string.vehicle_malfunction_text),Toast.LENGTH_LONG).show();
                } else {
                    mBluetooth.startCar(instructions);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onMapReady(GoogleMap map) {

        addCarOnMap(map);

        map.setOnMapClickListener(latLng -> {


                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.title(latLng.latitude + " : " + latLng.longitude);

                //Adding the destinationMarker to the Map
                if (destinationMarker == null) {
                    destinationMarker = map.addMarker(markerOptions);
                } else {
                    destinationMarker.remove();
                    destinationMarker = map.addMarker(markerOptions);
                }

            if (destinationMarker == null) {
                destinationMarker = map.addMarker(markerOptions);
            } else {
                destinationMarker.remove();
                destinationMarker = map.addMarker(markerOptions);
            }

            sendToVehicleButton.setActivated(true);
            sendToVehicleButton.setClickable(true);


                //calling method to calculate the directions
                calculateDirections(map, destinationMarker);

        });
    }


    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();

    }


    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();

    }

    public void addCarOnMap(GoogleMap map) {

        //Calling the GPSTracker and setting Latitide and Longitude of Car
        GPSTracker.getInstance(getContext()).setLatLgn();

        //Setting the CarMarker on the map according to its LatLgn position
        carMarker = map.addMarker(new MarkerOptions().position(new LatLng(
                GPSTracker.getInstance(getContext()).getLatitude(),
                GPSTracker.getInstance(getContext()).getLongitude()))
                .title("THE HAJKEN CAR").icon(BitmapDescriptorFactory.defaultMarker(carMarkerColor)));

        //Moving the camera to zoom value 19
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(carMarker.getPosition(), 19.0f);
        map.animateCamera(cu);
    }

    private void calculateDirections(GoogleMap map, Marker destinationMarker) {

        //Creating a request to use the Google Directions API
        DirectionsApiRequest apiRequest = DirectionsApi.newRequest(mGeoApiContext);

        // Giving the starting position of the car to the Directions API
        apiRequest.origin(new com.google.maps.model.LatLng(carMarker.getPosition().latitude, carMarker.getPosition().longitude));

        // Giving the destination position of the car to the Directions API
        apiRequest.destination(new com.google.maps.model.LatLng(destinationMarker.getPosition().latitude, destinationMarker.getPosition().latitude));

        //The route will be calculated for BICYCLE mode
        apiRequest.mode(TravelMode.BICYCLING);

        //Show only one route
        apiRequest.alternatives(false);

        //Creating a LatLng object to hold destination coordinates
        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
                destinationMarker.getPosition().latitude,
                destinationMarker.getPosition().longitude
        );

        apiRequest.destination(destination).setCallback(new com.google.maps.PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                DirectionsRoute[] routes = result.routes;

                //Calling method to add a Polyline to the map, given the DirectionResult
                addPolylinesToMap(result, map);
            }

            @Override
            public void onFailure(Throwable e) {

            }
        });
    }


    private void addPolylinesToMap(final DirectionsResult result, GoogleMap map) {

        new Handler(Looper.getMainLooper()).post(new Runnable() {

            @Override
            //Everything inside run will run on main thread
            public void run() {

                Log.d(TAG, "run: result routes: " + result.routes.length);

                for (DirectionsRoute route : result.routes) {

                    Log.d(TAG, "run: leg: " + route.legs[0].toString());

                    //Creating a list that is the Decoded Path
                    List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());

                    //Using external method to set the decoded path list
                    setDecodedPath(decodedPath);

                    //Logic to remove existing polyline from map if new one is created
                    if (polyline != null) {
                        if (polyline.isClickable()) {
                            polyline.remove();
                        }
                    }

                    //Setting a limitation so the maximum length of the route can be 100 meters
                    if(SphericalUtil.computeLength(getDecodedPath()) < 100){

                        //Polyline gets put onto the Map
                        polyline = map.addPolyline(new PolylineOptions().addAll(getDecodedPath()));
                        polyline.setColor(ContextCompat.getColor(getActivity(), R.color.background_color));
                        polyline.setClickable(true);

                        //This moves the camera to show the entire polyline on the screen
                        moveToBounds(polyline, map);

                        //Adding a delay to be sure the camera is set before convertLatLangToPoints() is called
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        convertLatLangToPoints(map);

                    }else{
                        Toast.makeText(getActivity(), "Please choose a destination within 100 meters to create a route for the car", Toast.LENGTH_LONG).show();
                    }

                }
            }
        });
    }

    public void setDecodedPath(List<com.google.maps.model.LatLng> decodedPath) {

        //Clears the list
        newDecodedPath.clear();

        // This loops through all the LatLng coordinates of ONE polyline.
        for (com.google.maps.model.LatLng latLng : decodedPath) {

            newDecodedPath.add(new LatLng(
                    latLng.lat,
                    latLng.lng
            ));
        }

    }


    public List<LatLng> getDecodedPath() {
        return newDecodedPath;
    }


    //Move camera of Maps fit the screen
    private void moveToBounds(Polyline p, GoogleMap map){

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for(int i = 0; i < p.getPoints().size();i++){
            builder.include(p.getPoints().get(i));
        }

        LatLngBounds bounds = builder.build();
        int padding = 50; // offset from edges of the map in pixels

        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        map.animateCamera(cu);
    }


    public void convertLatLangToPoints(GoogleMap map){

        //Getting the decoded path List
        List<LatLng> decodedPath = getDecodedPath();

        //Getting the distance of of the entire map
        LatLng farLeft = map.getProjection().getVisibleRegion().farLeft;
        LatLng farRight = map.getProjection().getVisibleRegion().farRight;

        //Converting the distance between the entire map into a scale
        double scale = SphericalUtil.computeDistanceBetween(farLeft, farRight) / 9;

        double xCoordinate;
        double yCoordinate;

        //Initializing the ArrayList pathToPointF
        pathToPointF = new ArrayList<>();

        for(int i = 0; i<decodedPath.size(); i++){

            //Creating XY-coordinates that works in a scale 1:1  -  1x = 1 cm in reality
            Point point = map.getProjection().toScreenLocation(decodedPath.get(i));
            xCoordinate = point.x * scale;
            //Inverting the yCoordinate with the size of the screen (900)
            yCoordinate = 900-point.y * scale;

            int x = (int) xCoordinate;
            int y = (int) yCoordinate;

            PointF convertedPoint = new PointF(x, y);

            pathToPointF.add(convertedPoint);

        }

    }

    public ArrayList<PointF> getPathToPointFList(){
        return pathToPointF;
    }


    public void showStartDialog(){
        mCustomDialog.setDialogHeading(getString(R.string.start_dialog_heading));
        mCustomDialog.setAction(getString(R.string.start_vehicle_text));
        mCustomDialog.setTargetFragment(GoogleMapsFragment.this,1);
        mCustomDialog.show(mFragmentManager,getString(R.string.dialog_tag));
    }

    public void showStopDialog(){
        mCustomDialog.setDialogHeading(getString(R.string.stop_dialog_heading));
        mCustomDialog.setAction(getString(R.string.stop_vehicle_text));
        mCustomDialog.setTargetFragment(GoogleMapsFragment.this,1);
        mCustomDialog.show(mFragmentManager,getString(R.string.dialog_tag));
    }

    @Override
    public void onConnect() {

    }

    @Override
    public void onNotConnected() {
        Toasty.error(mContext,getString(R.string.not_connected_text),Toast.LENGTH_LONG).show();
        sendToVehicleButton.setClickable(false);
        sendToVehicleButton.setActivated(false);
    }

    @Override
    public void onCarRunning() {
        isRunning = true;
        Toasty.info(mContext,"Starting", Toast.LENGTH_LONG).show();
        sendToVehicleButton.setText(getString(R.string.stop_vehicle_text));
        mInterfaceMainActivity.setOnBackPressedActive(false);

    }

    @Override
    public void onCarNotRunning() {
        isRunning = false;
        Toasty.info(mContext,"Completed route", Toast.LENGTH_LONG).show();
        sendToVehicleButton.setText(getString(R.string.start_vehicle_text));
        mInterfaceMainActivity.setOnBackPressedActive(true);
    }

    @Override
    public void onFoundObstacle() {
        Toasty.info(mContext,"Obstacle found", Toast.LENGTH_LONG).show();


    }
}
