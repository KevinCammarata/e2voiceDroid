package com.example.zhoupeng.e2voicedroid;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.amazonaws.mobileconnectors.lex.interactionkit.ui.InteractiveVoiceView;

public class MainActivity extends Activity implements View.OnClickListener {



    private static final String TAG = "MainActivity";
//    private Button googleDemoButton;
//    private Button textDemoButton;
//    private Button textPollyDemoButton;
    private Button speechLexButton;

    private View mLayout;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLayout = findViewById(R.id.main_layout);

//        googleDemoButton = (Button) findViewById(R.id.goto_reg_google);
//        textDemoButton = (Button) findViewById(R.id.goto_reg_lex_mix);
//        textPollyDemoButton = (Button) findViewById(R.id.goto_reg_lex_mix_polly);
        speechLexButton = (Button) findViewById(R.id.goto_reg_lex_compare);

//        textDemoButton.setOnClickListener(this);
        speechLexButton.setOnClickListener(this);
//        textPollyDemoButton.setOnClickListener(this);
//        googleDemoButton.setOnClickListener(this);

        requestMicroPermission();

        Intent voiceIntent = new Intent(this, LexCompare.class);
    }

    @Override
    public void onClick(final View v) {
        switch ((v.getId())) {
//            case R.id.goto_reg_lex_mix:
//                Intent textIntent = new Intent(this, lex_mix.class);
//                startActivity(textIntent);
//                break;
//            case R.id.goto_reg_lex_mix_polly:
//                Intent textPollyIntent = new Intent(this, Lex_mix_polly.class);
//                startActivity(textPollyIntent);
//                break;
//            case R.id.goto_reg_google:
//                Intent googleIntent = new Intent(this, GoogleVoice.class);
//                startActivity(googleIntent);
//                break;
            case R.id.goto_reg_lex_compare:
                Intent voiceIntent = new Intent(this, LexCompare.class);
                startActivity(voiceIntent);
                break;
        }
    }


    private static final int REQUEST_RECORD_AUDIO = 0;
    private void requestMicroPermission() {
        Log.i(TAG, "Micro permission has NOT been granted. Requesting permission.");

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(mLayout, R.string.permission_mp_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.RECORD_AUDIO},
                                    REQUEST_RECORD_AUDIO);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
        }
    }
}
