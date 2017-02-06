package com.example.zhoupeng.e2voicedroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.lex.interactionkit.InteractionClient;
import com.amazonaws.mobileconnectors.lex.interactionkit.Response;
import com.amazonaws.mobileconnectors.lex.interactionkit.continuations.LexServiceContinuation;
import com.amazonaws.mobileconnectors.lex.interactionkit.listeners.AudioPlaybackListener;
import com.amazonaws.mobileconnectors.lex.interactionkit.listeners.InteractionListener;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lexrts.AmazonLexRuntime;
import com.amazonaws.services.lexrts.AmazonLexRuntimeClient;
import com.amazonaws.services.lexrts.model.DialogState;
import com.amazonaws.services.lexrts.model.PostTextRequest;
import com.amazonaws.services.lexrts.model.PostTextResult;
import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.DescribeVoicesRequest;
import com.amazonaws.services.polly.model.DescribeVoicesResult;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest;
import com.amazonaws.services.polly.model.Voice;
import com.amazonaws.util.StringUtils;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.System.in;

public class Lex_mix_polly extends Activity implements TextToSpeech.OnInitListener{

    private static final String TAG = "TextActivity";
    private static final int REQUEST_CODE = 1234;

    ImageButton Start;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient googleClient;

    ArrayList<String> matches_text;

    private Context appContext;

    private InteractionClient lexInteractionClient;
    private boolean inConversation;

    private String userTextInput;


    private AmazonPollyPresigningClient client;
    MediaPlayer mediaPlayer;
    private List<Voice> voices;
    /**
     * Amazon Lex service client.
     */
    private AmazonLexRuntime amazonlex;

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lex_mix_polly);

        appContext = getApplicationContext();

        Start = (ImageButton) findViewById(R.id.start_reg_mix_polly);

        Start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected()) {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    startActivityForResult(intent, REQUEST_CODE);
                } else {
                    Toast.makeText(getApplicationContext(), "Plese Connect to Internet", Toast.LENGTH_LONG).show();
                }
            }

        });

        googleClient = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        initializeLexSDK();
    }

    private void initializeLexSDK() {
        Log.d(TAG, "Lex Client");
        // Cognito Identity Broker is the credentials provider.
//        CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(
//                appContext.getResources().getString(R.string.identity_id_test),
//                Regions.fromName("us-east-1"));

        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                appContext,
                appContext.getResources().getString(R.string.identity_id_test),
                Regions.fromName("us-east-1")
        );

        // Create Lex interaction client.
        lexInteractionClient = new InteractionClient(getApplicationContext(),
                credentialsProvider,
                Regions.US_EAST_1,
                appContext.getResources().getString(R.string.bot_name),
                appContext.getResources().getString(R.string.bot_alias));
        lexInteractionClient.setAudioPlaybackListener(audioPlaybackListener);
        lexInteractionClient.setInteractionListener(interactionListener);

        // Create a client that supports generation of presigned URLs.
        client = new AmazonPollyPresigningClient(credentialsProvider);

        setupNewMediaPlayer();

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        String userAgent = "INTERACTION_CLIENT";
        clientConfiguration.setUserAgent(userAgent);
        amazonlex = new AmazonLexRuntimeClient(credentialsProvider, clientConfiguration);
        amazonlex.setRegion(Region.getRegion(Regions.US_EAST_1));

        runComparisonDataSet();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupVoicesSpinner();
    }

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {

        } else {
            Log.e("TTS", "Initilization Failed!");
        }

    }

    @Override
    public void onDestroy() {
        // Don't forget to shutdown tts!

        super.onDestroy();
    }


    private void speakOut(String text) {
//        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

        speakByPolly(text);
    }


    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        if (net != null && net.isAvailable() && net.isConnected()) {
            return true;
        } else {
            return false;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {

//            match_text_dialog = new Dialog(MainActivity.this);
//            match_text_dialog.setContentView(R.layout.dialog_matches_frag);
//            match_text_dialog.setTitle("Select Matching Text");
//            textlist = (ListView) match_text_dialog.findViewById(R.id.list);
            matches_text = data
                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches_text != null && matches_text.size() > 0) {
//                if (matches_text.size() == 1) {
                processSpeechText(matches_text.get(0));
//                } else {
//                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
//                            android.R.layout.simple_list_item_1, matches_text);
//                    textlist.setAdapter(adapter);
//                    textlist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//                        @Override
//                        public void onItemClick(AdapterView<?> parent, View view,
//                                                int position, long id) {
//                            processSpeechText(matches_text.get(position));
//                            match_text_dialog.hide();
//                        }
//                    });
//                    match_text_dialog.show();
//                }
            } else {
                speakOut("Sorry, we did not get you.");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processSpeechText(String speechText) {
        userTextInput = speechText;
        textEntered();
//        Speech.setText(speechText);
//        speechText = NLPProcess(speechText);
//        doSearch(speechText);
    }

    final AudioPlaybackListener audioPlaybackListener = new AudioPlaybackListener() {
        @Override
        public void onAudioPlaybackStarted() {
            Log.d(TAG, " -- Audio playback started");
        }

        @Override
        public void onAudioPlayBackCompleted() {
            Log.d(TAG, " -- Audio playback ended");
        }

        @Override
        public void onAudioPlaybackError(Exception e) {
            Log.d(TAG, " -- Audio playback error", e);
        }
    };

    private void showToast(final String message) {
        Toast.makeText(this.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        Log.d(TAG, message);
    }

    final InteractionListener interactionListener = new InteractionListener() {
        @Override
        public void onReadyForFulfillment(final Response response) {
            Log.d(TAG, "Transaction completed successfully");
            addMessage(new TextMessage(response.getTextResponse(), "rx", getCurrentTimeStamp()));
            inConversation = false;
        }

        @Override
        public void promptUserToRespond(final Response response,
                                        final LexServiceContinuation continuation) {
            String responseText = response.getTextResponse();
            addMessage(new TextMessage(response.getTextResponse(), "rx", getCurrentTimeStamp()));
            readUserText(continuation);

            speakOut(responseText);
        }

        @Override
        public void onInteractionError(final Response response, final Exception e) {
            if (response != null) {
                if (DialogState.Failed.toString().equals(response.getDialogState())) {
                    addMessage(new TextMessage(response.getTextResponse(), "rx",
                            getCurrentTimeStamp()));
                    inConversation = false;
                } else {
                    addMessage(new TextMessage("Please retry", "rx", getCurrentTimeStamp()));
                }
            } else {
                showToast("Error: " + e.getMessage());
                Log.e(TAG, "Interaction error", e);
                inConversation = false;
            }
        }
    };

    private LexServiceContinuation convContinuation;
    private void readUserText(final LexServiceContinuation continuation) {
        convContinuation = continuation;
        inConversation = true;
    }

    private void runComparisonDataSet()
    {
        //read text from file
        InputStream is = getApplicationContext().getResources().openRawResource(R.raw.input);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("Lex,input:").append(line);
                Log.i("Lex request from file", sb.toString());

                PostTextRequest request = new PostTextRequest();
                request.setBotAlias("Prod");
                request.setBotName("CDRateCheckBot");
                request.setInputText(line);
                PostTextResult result = amazonlex.postText(request);
                Log.i("Lex result", result.toString());

//                if (!inConversation) {
//                    Log.d(TAG, " -- New conversation started");
//                    startNewConversation();
//                    addMessage(new TextMessage(line, "tx", getCurrentTimeStamp()));
//                    lexInteractionClient.textInForTextOut(line, null);
//                    inConversation = true;
//                } else {
//                    Log.d(TAG, " -- Responding with text: " + line);
//                    addMessage(new TextMessage(line, "tx", getCurrentTimeStamp()));
//                    convContinuation.continueWithTextInForTextOut(line);
//                }

            }
        }
        catch (IOException e) {
            Log.e("Error reading input", "Caught an IOException reading R.raw.input", e);
        }

        //for each input
            //Send message
            //Log response

    }

    private void textEntered() {
        // showToast("Text input not implemented");
        String text = userTextInput;
        if (!inConversation) {
            Log.d(TAG, " -- New conversation started");
            startNewConversation();
            addMessage(new TextMessage(text, "tx", getCurrentTimeStamp()));
            lexInteractionClient.textInForTextOut(text, null);
            inConversation = true;
        } else {
            Log.d(TAG, " -- Responding with text: " + text);
            addMessage(new TextMessage(text, "tx", getCurrentTimeStamp()));
            convContinuation.continueWithTextInForTextOut(text);
        }
        clearTextInput();
    }

    private void startNewConversation() {
        Log.d(TAG, "Starting new conversation");
        Conversation.clear();
        inConversation = false;
        clearTextInput();
    }

    private void addMessage(final TextMessage message) {
        Conversation.add(message);
    }

    private void clearTextInput() {
        userTextInput = ("");
    }

    private String getCurrentTimeStamp() {
        return DateFormat.getDateTimeInstance().format(new Date());
    }


    //polly speech

    private static final String KEY_SELECTED_VOICE_POSITION = "SelectedVoicePosition";
    private static final String KEY_VOICES = "Voices";
    private static final String KEY_SAMPLE_TEXT = "SampleText";

    private Spinner voicesSpinner;
    private int selectedPosition;

    private class SpinnerVoiceAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        private List<Voice> voices;

        SpinnerVoiceAdapter(Context ctx, List<Voice> voices) {
            this.inflater = LayoutInflater.from(ctx);
            this.voices = voices;
        }

        @Override
        public int getCount() {
            return voices.size();
        }

        @Override
        public Object getItem(int position) {
            return voices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.voice_spinner_row, parent, false);
            }
            Voice voice = voices.get(position);

            TextView nameTextView = (TextView) convertView.findViewById(R.id.voiceName);
            nameTextView.setText(voice.getName());

            TextView languageCodeTextView = (TextView) convertView.findViewById(R.id.voiceLanguageCode);
            languageCodeTextView.setText(voice.getLanguageName() +
                    " (" + voice.getLanguageCode() + ")");

            return convertView;
        }
    }

    void setupNewMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                setupNewMediaPlayer();
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                return false;
            }
        });
    }

    private void speakByPolly(String textToRead) {

        Voice selectedVoice = (Voice) voicesSpinner.getSelectedItem();

        if (textToRead.trim().isEmpty()) {
            textToRead = "No voice available.";
        }

        // Create speech synthesis request.
        SynthesizeSpeechPresignRequest synthesizeSpeechPresignRequest =
                new SynthesizeSpeechPresignRequest()
                        // Set text to synthesize.
                        .withText(textToRead)
                        // Set voice selected by the user.
                        .withVoiceId(selectedVoice.getId())
                        // Set format to MP3.
                        .withOutputFormat(OutputFormat.Mp3);

        // Get the presigned URL for synthesized speech audio stream.
        URL presignedSynthesizeSpeechUrl =
                client.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest);

        Log.i(TAG, "Playing speech from presigned URL: " + presignedSynthesizeSpeechUrl);

        // Create a media player to play the synthesized audio stream.
        if (mediaPlayer.isPlaying()) {
            setupNewMediaPlayer();
        }
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            // Set media player's data source to previously obtained URL.
            mediaPlayer.setDataSource(presignedSynthesizeSpeechUrl.toString());
        } catch (IOException e) {
            Log.e(TAG, "Unable to set data source for the media player! " + e.getMessage());
        }

        // Start the playback asynchronously (since the data source is a network stream).
        mediaPlayer.prepareAsync();
    }

    void setupVoicesSpinner() {
        voicesSpinner = (Spinner) findViewById(R.id.voicesSpinner);
//        findViewById(R.id.voicesProgressBar).setVisibility(View.VISIBLE);

        // Asynchronously get available Polly voices.
        new GetPollyVoices().execute();
    }

    private class GetPollyVoices extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (voices != null) {
                return null;
            }

            // Create describe voices request.
            DescribeVoicesRequest describeVoicesRequest = new DescribeVoicesRequest();

            DescribeVoicesResult describeVoicesResult;
            try {
                // Synchronously ask the Polly Service to describe available TTS voices.
                describeVoicesResult = client.describeVoices(describeVoicesRequest);
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to get available voices. " + e.getMessage());
                return null;
            }

            // Get list of voices from the result.
            List<Voice> fullVoices = describeVoicesResult.getVoices();
            voices = new ArrayList<>();
            for(Voice item : fullVoices)
            {
                if("en-US".equalsIgnoreCase(item.getLanguageCode()) )
                {
                    voices.add(item);
                }
            }

            // Log a message with a list of available TTS voices.
            Log.i(TAG, "Available Polly voices: " + voices);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (voices == null) {
                return;
            }

            voicesSpinner.setAdapter(new SpinnerVoiceAdapter(Lex_mix_polly.this, voices));

//            findViewById(R.id.voicesProgressBar).setVisibility(View.INVISIBLE);
            voicesSpinner.setVisibility(View.VISIBLE);

            voicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (view == null) {
                        return;
                    }

//                    setDefaultTextForSelectedVoice();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            // Restore previously selected voice (e.g. after screen orientation change).
            voicesSpinner.setSelection(selectedPosition);

//            playButton.setEnabled(true);
        }
    }
}
