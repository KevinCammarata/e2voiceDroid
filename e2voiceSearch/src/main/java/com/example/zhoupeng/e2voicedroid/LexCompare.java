package com.example.zhoupeng.e2voicedroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ListView;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.lex.interactionkit.Response;
import com.amazonaws.mobileconnectors.lex.interactionkit.config.InteractionConfig;
import com.amazonaws.mobileconnectors.lex.interactionkit.exceptions.MaxSpeechTimeOutException;
import com.amazonaws.mobileconnectors.lex.interactionkit.exceptions.NoSpeechTimeOutException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lexrts.AmazonLexRuntime;
import com.amazonaws.services.lexrts.AmazonLexRuntimeClient;
import com.amazonaws.services.lexrts.model.PostTextRequest;
import com.amazonaws.services.lexrts.model.PostTextResult;
import com.amazonaws.util.StringUtils;
import com.example.zhoupeng.e2voicedroid.lex.E2InteractiveVoiceView;
import com.example.zhoupeng.e2voicedroid.lex.E2InteractiveVoiceViewAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LexCompare extends Activity
        implements E2InteractiveVoiceView.InteractiveVoiceListener {

    private static final String TAG = "LexCompare";
    private Context appContext;
    private E2InteractiveVoiceView voiceView;
    private E2InteractiveVoiceViewAdapter interactiveVoiceViewAdapter;

    /**
     * Amazon Lex service client.
     */
    private AmazonLexRuntime amazonlex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lex_polly);

        init();
        StringUtils.isBlank("notempty");
    }

    @Override
    public void onBackPressed() {
        exit();
    }

    private void exit() {
        interactiveVoiceViewAdapter.cancel();
        Conversation.clear();
        finish();
    }

    private void init() {
        appContext = getApplicationContext();
        voiceView = (E2InteractiveVoiceView) findViewById(R.id.voiceInterface);
        voiceView.setInteractiveVoiceListener(this);
//        CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(
//                appContext.getResources().getString(R.string.identity_id_test),
//                Regions.fromName(appContext.getResources().getString(R.string.aws_region)));

        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                appContext,
                appContext.getResources().getString(R.string.identity_id_test),
                Regions.fromName("us-east-1")
        );

//        CognitoCachingCredentialsProvider myCredentialsProvider = new CognitoCachingCredentialsProvider(
//                appContext,
//                appContext.getResources().getString(R.string.identity_id_test),
//                Regions.fromName("us-east-1")
//        );

        interactiveVoiceViewAdapter = voiceView.getViewAdapter();
        interactiveVoiceViewAdapter.setCredentialProvider(credentialsProvider);
        InteractionConfig config = new InteractionConfig(appContext.getString(R.string.bot_name),
                appContext.getString(R.string.bot_alias));
        config.setNoSpeechTimeoutInterval(InteractionConfig.DEFAULT_NO_SPEECH_TIMEOUT_INTERVAL*3);
        config.setMaxSpeechTimeoutInterval(InteractionConfig.DEFAULT_MAX_SPEECH_TIMEOUT_INTERVAL*3);
        interactiveVoiceViewAdapter.setInteractionConfig(config);
        interactiveVoiceViewAdapter.setAwsRegion(appContext.getString(R.string.aws_region));

        interactiveVoiceViewAdapter.autoStartNewConversation();

//        comparisonInit(myCredentialsProvider);
        //runComparisonDataSet();
    }

    public void comparisonInit(CognitoCachingCredentialsProvider credentialsProvider){
        Log.d("ComparisonInit", "Entering the ComparisonInit() function");
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        String userAgent = "INTERACTION_CLIENT";
        clientConfiguration.setUserAgent(userAgent);
        amazonlex = new AmazonLexRuntimeClient(credentialsProvider, clientConfiguration);
        amazonlex.setRegion(Region.getRegion(Regions.fromName("us-east-1")));

    }



    @Override
    public void dialogReadyForFulfillment(final Map<String, String> slots, final String intent) {
        Log.d(TAG, String.format(
                Locale.US,
                "Dialog ready for fulfillment:\n\tIntent: %s\n\tSlots: %s",
                intent,
                slots.toString()));
    }

    @Override
    public void onResponse(Response response) {
        Log.d(TAG, "Bot responded with intent: " + response.getIntentName());
        Log.d(TAG, "Bot responded with slots: " + response.getSlots().toString());
        Log.d(TAG, "Bot responded with session attrs: " + response.getSessionAttributes().toString());
        addMessage(new TextMessage(response.getTextResponse(), "rx", getCurrentTimeStamp()));
    }

    private String getCurrentTimeStamp() {
        return DateFormat.getDateTimeInstance().format(new Date());
    }

    @Override
    public void onError(final String responseText, final Exception e) {
        addMessage(new TextMessage(responseText, "rx", getCurrentTimeStamp()));
        if(responseText.contains("Goodbye"))
        {
            goBackToMain();
        }
        Log.e(TAG, "Error: " + responseText, e);
    }

    @Override
    public void onMicrophoneError(Exception e) {
        if (e instanceof NoSpeechTimeOutException) {
            addStatusMsg("Sorry, i can not hear you.");
        } else if (e instanceof MaxSpeechTimeOutException) {
            addStatusMsg("Thank you, see you next time.");
            goBackToMain();
        }
        else
        {
            addStatusMsg("MicrophoneError:" + e.getMessage());
        }

    }

    @Override
    public void onStartListening(int state) {
        addStatusMsg(appContext.getResources().getString(R.string.lex_mic_start));
    }

    @Override
    public void onFullFilled() {
        addStatusMsg(appContext.getResources().getString(R.string.lex_fulfilled));
    }

    private void addMessage(final TextMessage message) {
        Conversation.add(message);
        final MessagesListAdapter listAdapter = new MessagesListAdapter(getApplicationContext());
        final ListView messagesListView = (ListView) findViewById(R.id.conversationListView);
        messagesListView.setDivider(null);
        messagesListView.setAdapter(listAdapter);
        messagesListView.setSelection(listAdapter.getCount() - 1);
    }

    private void addStatusMsg(String msg)
    {
        addMessage(new TextMessage(msg, "sys", getCurrentTimeStamp()));
    }

    private void goBackToMain()
    {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                onBackPressed();
            }
        }, 1000);
    }

}
