package com.example.hajken;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.example.hajken.fragments.CollectionFragment;
import com.example.hajken.fragments.DrawFragment;
import com.example.hajken.fragments.GatewayFragment;
import com.example.hajken.fragments.GoogleMapsFragment;
import com.example.hajken.fragments.ScanFragment;
import com.example.hajken.fragments.StartFragment;
import java.util.List;

public class MainActivity extends AppCompatActivity implements InterfaceMainActivity {

    private static final String TAG = "MainActivity";
    private static MainActivity mMainActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMainActivity = this;
        init(); // when mainActivity starts, it will inflate StartFragment first
    }

    private void init(){
        StartFragment fragment = new StartFragment();
        doFragmentTransaction(fragment,getString(R.string.start_fragment),false);

    }

    //used to be a string message here as well but we are not using that
    private void doFragmentTransaction(Fragment fragment, String tag, boolean addToBackStack){
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        transaction.replace(R.id.main_container,fragment,tag);

        if (addToBackStack){
            transaction.addToBackStack(tag);
        }
        transaction.commit();
    }


    //this method is responsible to start fragmentTransactions
    @Override
    public void inflateFragment(String fragmentTag) {

        if (fragmentTag.equals(getString(R.string.scan_fragment))){

            ScanFragment fragment = new ScanFragment();
            doFragmentTransaction(fragment, fragmentTag, true); //addToBackStack(true) makes it possible to go back without closing the app

        } else if (fragmentTag.equals(getString(R.string.gateway_fragment))){

            GatewayFragment fragment = new GatewayFragment();
            doFragmentTransaction(fragment,fragmentTag,true); //addToBackStack(true) makes it possible to go back without closing the app

        } else if (fragmentTag.equals(getString(R.string.draw_fragment))){

            DrawFragment fragment = new DrawFragment();
            doFragmentTransaction(fragment,fragmentTag,true); //addToBackStack(true) makes it possible to go back without closing the app

        } else if (fragmentTag.equals(getString(R.string.collection_fragment))){

            CollectionFragment fragment = new CollectionFragment();
            doFragmentTransaction(fragment,fragmentTag,true); //addToBackStack(true) makes it possible to go back without closing the app

        } else if (fragmentTag.equals(getString(R.string.google_maps_fragment))){

            GoogleMapsFragment fragment = new GoogleMapsFragment();
            doFragmentTransaction(fragment,fragmentTag,true);
        }

    }


    //Override method to be able to adapt onBackPressed for fragments --- could this be done with just an if statement instead of loop?
    @Override
    public void onBackPressed(){
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments){
            if (fragment instanceof CollectionFragment && ((CollectionFragment) fragment).isVehicleOn()){
                Log.d(TAG, "onBackPressed: instance of CollectionFragment & backPressedDisabled = true");
                Toast.makeText(this, "Can't go back while vehicle is running", Toast.LENGTH_SHORT).show();
            } else {
                super.onBackPressed();
            }
        }
    }

    // hack to get activity passable in different fragments
    public static MainActivity getThis(){
        return mMainActivity;
    }
}