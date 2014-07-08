package com.raceyourself.raceyourself.matchmaking;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.raceyourself.platform.auth.AuthenticationActivity;
import com.raceyourself.platform.models.User;
import com.raceyourself.raceyourself.R;

import java.io.IOException;

public class ChooseFitnessActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_fitness);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.choose_fitness, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onFitnessBtn(View view) {
        Intent matchmakingIntent = new Intent(this, MatchmakingDistanceActivity.class);
        Bundle extras = new Bundle();
        String fitness = "";
        switch(view.getId()) {

            case R.id.outOfShape:
                Log.i("ChooseFitnessActivity", "Out of shape chosen");
                fitness = "Out of shape";
                break;
            case R.id.averageBtn:
                Log.i("ChooseFitnessActivity", "Average chosen");
                fitness = "Average";
                break;
            case R.id.athleticBtn:
                Log.i("ChooseFitnessActivity", "Athletic chosen");
                fitness = "Athletic";
                break;
            case R.id.eliteBtn:
                Log.i("ChooseFitnessActivity", "Elite chosen");
                fitness = "Elite";
                break;
            default:
                Log.i("ChooseFitnessActivity", "id not found");
                return;
        }
        extras.putString("fitness", fitness);
        final String finalFitness = fitness;
        Thread updateUserThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    AuthenticationActivity.editUser(new AuthenticationActivity.UserDiff().profile("running_fitness", finalFitness));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        updateUserThread.run();

        matchmakingIntent.putExtras(extras);
        startActivity(matchmakingIntent);
    }
}
