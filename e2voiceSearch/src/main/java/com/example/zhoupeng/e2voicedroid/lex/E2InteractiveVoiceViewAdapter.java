package com.example.zhoupeng.e2voicedroid.lex;

        import android.content.Context;
        import android.os.Environment;
        import android.os.Handler;
        import android.util.Log;
        import android.view.View;

        import com.amazonaws.ClientConfiguration;
        import com.amazonaws.auth.AWSCredentialsProvider;
        import com.amazonaws.mobileconnectors.lex.interactionkit.InteractionClient;
        import com.amazonaws.mobileconnectors.lex.interactionkit.Response;
        import com.amazonaws.mobileconnectors.lex.interactionkit.config.InteractionConfig;
        import com.amazonaws.mobileconnectors.lex.interactionkit.continuations.LexServiceContinuation;
        import com.amazonaws.mobileconnectors.lex.interactionkit.exceptions.InvalidParameterException;
        import com.amazonaws.mobileconnectors.lex.interactionkit.exceptions.MaxSpeechTimeOutException;
        import com.amazonaws.mobileconnectors.lex.interactionkit.exceptions.NoSpeechTimeOutException;
        import com.amazonaws.mobileconnectors.lex.interactionkit.listeners.AudioPlaybackListener;
        import com.amazonaws.mobileconnectors.lex.interactionkit.listeners.InteractionListener;
        import com.amazonaws.mobileconnectors.lex.interactionkit.listeners.MicrophoneListener;
        import com.amazonaws.regions.Regions;
        import com.amazonaws.services.lexrts.model.DialogState;
        import com.example.zhoupeng.e2voicedroid.R;

        import java.io.BufferedReader;
        import java.io.File;
        import java.io.FileOutputStream;
        import java.io.FileWriter;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.InputStreamReader;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.Map;

/**
 * This class helps to easily interface the {@link E2InteractiveVoiceViewAdapter} view's
 * with the Amazon Lex client. It manages the client states and delivers results
 * through separate callbacks. This implementation of the adapter offers only
 * voice for voice out operations only.
 */
public class E2InteractiveVoiceViewAdapter
        implements InteractionListener, AudioPlaybackListener, MicrophoneListener,
        View.OnClickListener {

    private final static String TAG = "Lex";
    private static final String INTERACTION_VOICE_VIEW_USER_AGENT = "VOICE_BUTTON";
    private final Context context;
    private final E2InteractiveVoiceView micButton;
    private E2InteractiveVoiceView.InteractiveVoiceListener voiceListener;
    protected int state;
    private Regions awsRegion;
    private AWSCredentialsProvider credentialsProvider;
    private InteractionConfig interactionConfig;
    private InteractionClient lexInteractionClient;
    private LexServiceContinuation continuation;
    private boolean shouldInitialize;
    private Map<String, String> sessionAttributes;
    private final ClientConfiguration clientConfiguration;

    private ArrayList<String> utteranceList;
    private int utterancesSubmitted = 0;

    private boolean hasInteractionError;
    private File outputFile;

    // Dialog states.
    public final static int STATE_NOT_READY = 0;
    public final static int STATE_READY = 1;
    public final static int STATE_LISTENING = 2;
    public final static int STATE_AUDIO_PLAYBACK = 3;
    public final static int STATE_AWAITING_RESPONSE = 4;

    private E2InteractiveVoiceViewAdapter(Context context,
                                          E2InteractiveVoiceView micButton) {
        this.context = context;
        this.micButton = micButton;
        this.micButton.setOnClickListener(this);
        this.shouldInitialize = true;
        this.voiceListener = null;
        this.state = STATE_READY;

        // for setting the user agent
        clientConfiguration = new ClientConfiguration();
        clientConfiguration.setUserAgent(INTERACTION_VOICE_VIEW_USER_AGENT);
    }

    public static E2InteractiveVoiceViewAdapter getInstance(Context context,
                                                            E2InteractiveVoiceView micButton) {
        return new E2InteractiveVoiceViewAdapter(context, micButton);
    }

    @Override
    public void onAudioPlaybackStarted() {
        state = STATE_AUDIO_PLAYBACK;
    }

    @Override
    public void onAudioPlayBackCompleted() {
        if (state != STATE_NOT_READY) {
            if (this.continuation != null) {
                state = STATE_LISTENING;
                continuation.continueWithCurrentMode();
            } else {
                // Cannot continue, must start new dialog.
                state = STATE_READY;
                micButton.animateNone();

                if(!hasInteractionError) {
                    if (voiceListener != null)
                        voiceListener.onFullFilled();
                    autoStartNewConversation();
                }
                hasInteractionError = false;
            }
        }
    }

    @Override
    public void onAudioPlaybackError(Exception e) {
        // Audio playback failed.
        Log.e(TAG, "E2InteractiveVoiceViewAdapter: Audio playback failed", e);
        state = STATE_READY;
    }

    @Override
    public void onReadyForFulfillment(Response response) {
        // The request is ready for fulfillment, the bot is ready for a new dialog.
        state = STATE_READY;
        continuation = null;

        if (voiceListener != null) {
            voiceListener.dialogReadyForFulfillment(response.getSlots(), response.getIntentName());
            Log.d("LexResponse", "Intent name: " + response.getIntentName());
        }
    }

    @Override
    public void promptUserToRespond(Response response,
                                    LexServiceContinuation continuation) {
        micButton.animateNone();
        if (response == null) {
            Log.e(TAG, "E2InteractiveVoiceViewAdapter: Received null response from Amazon Lex bot");
        }

        if (DialogState.Fulfilled.toString().equals(response.getDialogState())) {
            // The request has been fulfilled, the bot is ready for a new dialog.
            state = STATE_READY;
            this.continuation = null;

        } else {
            this.continuation = continuation;
        }

        if (voiceListener != null) {
            voiceListener.onResponse(response);
        }
    }

    @Override
    public void onInteractionError(Response response, Exception e) {
        Log.e(TAG, "E2InteractiveVoiceViewAdapter: Interaction error", e);
        micButton.animateNone();
        if (state != STATE_AUDIO_PLAYBACK) {
            state = STATE_READY;
        }
        continuation = null;

        if (voiceListener != null) {
            if (response != null) {
                hasInteractionError = true;
                voiceListener.onError(response.getTextResponse(), e);
            } else {
                voiceListener.onError("Error from Bot", e);
            }
        }
    }

    @Override
    public void readyForRecording() {
        // noop.
    }

    @Override
    public void startedRecording() {
        //noop.
    }

    @Override
    public void onRecordingEnd() {
        if (state == STATE_NOT_READY) {
            return;
        }

        if (state == STATE_LISTENING) {
            state = STATE_AWAITING_RESPONSE;
            micButton.animateWaitSpinner();
        } else {
            state = STATE_READY;
        }
    }

    @Override
    public void onSoundLevelChanged(double soundLevel) {
        if (state == STATE_LISTENING) {
            micButton.animateSoundLevel((float) soundLevel);
        }
    }

    @Override
    public void onMicrophoneError(Exception e) {
        micButton.animateNone();
        if (e instanceof NoSpeechTimeOutException) {
            Log.e(TAG, "E2InteractiveVoiceViewAdapter: Failed to detect speech", e);
            state = STATE_READY;

            autoStartNewConversation();

        } else if (e instanceof MaxSpeechTimeOutException) {
            Log.e(TAG, "E2InteractiveVoiceViewAdapter: Speech time out", e);
        }
        voiceListener.onMicrophoneError(e);
    }

    @Override
    public void onClick(View v) {
        // Return if not ready.
        switch (state) {
            case STATE_READY:
                if (shouldInitialize) {
                    init();
                }

                if (sessionAttributes == null) {
                    sessionAttributes = new HashMap<String, String>();
                }
//                startListening(sessionAttributes);
                runComparisonDataSet();
                break;
            case STATE_LISTENING:
            case STATE_AUDIO_PLAYBACK:
            case STATE_AWAITING_RESPONSE:
                lexInteractionClient.cancel();
                state = STATE_READY;
                break;
        }
    }

    public void startComparison(View v){
        init();
        runComparisonDataSet();
    }

    public void runComparisonDataSet()
    {
        Log.d("RunComparisonDataSet", "Entering method runComparisonDataSet()");

        //if utterancesSubmitted = utteranceList.size do nothing
        if(utterancesSubmitted == utteranceList.size()) {
            Log.i(TAG, "Reached end of utterance list.  No further utterances will be submitted.");
        }
        else{
            String utterance = utteranceList.get(utterancesSubmitted);
            utterancesSubmitted++;

            //Write to file

            startTextConversation(utterance, new HashMap<String, String>());
            Log.d(TAG, "Starting text conversation with utterance: " + utterance);
        }




    }

    public void autoStartNewConversation()
    {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(state == STATE_READY)
                {
                    if (shouldInitialize) {
                        init();
                    }

                    if (sessionAttributes == null) {
                        sessionAttributes = new HashMap<String, String>();
                    }

                    startListening(sessionAttributes);
                }
            }
        }, 200);
    }
    /**
     * Validates app details.
     * <p>
     *     <b>Override this method</b> to implement custom attribute verification.
     * </p>
     */
    protected void validateAppData() {
        if (interactionConfig == null) {
            throw new InvalidParameterException(
                    "Interaction config is not set");
        } else {
            if (interactionConfig.getBotName() == null || interactionConfig.getBotName().isEmpty()) {
                throw new InvalidParameterException(
                        "Bot name is not set");
            }

            if (interactionConfig.getBotAlias() == null || interactionConfig.getBotAlias().isEmpty()) {
                throw new InvalidParameterException(
                        "Bot alias is not set");
            }
        }

        if (awsRegion == null) {
            throw new InvalidParameterException(
                    "AWS Region is not set");
        }
    }

    /**
     * Creates the interaction client, uses default interaction config setting
     * {@link com.amazonaws.mobileconnectors.lex.interactionkit.config.InteractionConfig}
     * .
     */
    protected void createInteractionClient() {
        // Release system resources assigned to an earlier instance of the client.
        if (lexInteractionClient != null) {
            lexInteractionClient.cancel();
        }

        lexInteractionClient = new InteractionClient(
                context,
                credentialsProvider,
                awsRegion,
                interactionConfig,
                clientConfiguration);

        lexInteractionClient.setAudioPlaybackListener(this);
        lexInteractionClient.setInteractionListener(this);
        lexInteractionClient.setMicrophoneListener(this);


    }

    private void loadInputFile(){
        Log.d("RunComparisonDataSet", "Entering method runComparisonDataSet()");
        //read text from file
        InputStream is = context.getResources().openRawResource(R.raw.input);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                utteranceList.add(line);


            }
        }
        catch (IOException e) {
            Log.e("E2ViewAdapter", "Caught an IOException reading R.raw.input", e);
        }
    }

    private void loadOutputFile(){
        Log.d("RunComparisonDataSet", "Entering loadOutputFile method");
            File root = context.getFilesDir();
            File output = new File(root, "Output");
            if (!output.exists()) {
                output.mkdirs();
            }
            outputFile = new File(output, "LexComparisonOutput.txt");
        try {
           if(!outputFile.exists()) {
               Log.d("FileCreation", outputFile.toString());
               outputFile.createNewFile();

           }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void writeToOutputFile(String text){

        Log.d("RunComparisonDataSet", "Entered writeToOutputFile function");
//        try (FileWriter writer =new FileWriter(outputFile)) {
//            Log.d("RunComparisonDataSet", "Starting to write to output file");
//            writer.append(text);
//            writer.flush();
//            writer.close();
//            Log.d("RunComparisonDataSet", "Write to output file try block completed.");
//       }
        try (FileOutputStream fileOutputStream = context.openFileOutput(outputFile.getName(), Context.MODE_APPEND))
        {
            fileOutputStream.write(text.getBytes());
            fileOutputStream.close();
        }
       catch (IOException e) {
           e.printStackTrace();
       }

    }


    /**
     * Invoke Amazon Lex client to start a new audio in audio request.
     *
     * @param sessionParameters The session parameters to be used for this
     *            request.
     */
    private void startListening(Map<String, String> sessionParameters) {
        state = STATE_LISTENING;
        lexInteractionClient.audioInForAudioOut(sessionParameters);
        if(voiceListener!=null)
            voiceListener.onStartListening(state);
    }

    /**
     * Invoke Amazon Lex client to start a new text request.
     *
     * @param sessionParameters The session parameters to be used for this
     *            request.
     */
    public void startTextConversation(String text, Map<String, String> sessionParameters) {
//        state = STATE_LISTENING;
        Log.d("StartTextConvo", "Value of text: " + text);
        Log.d("StartTextConvo","Value of sessionParameters: " + sessionParameters.toString());
        if(null == lexInteractionClient)
        {
            createInteractionClient();
        }
        Log.d("StartTextConvo", "Value of lexInteractionClient" + lexInteractionClient);
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator()).append("Lex,input:").append(text).append(",");
        writeToOutputFile(sb.toString());
        lexInteractionClient.textInForTextOut(text, sessionParameters);

    }

    /**
     * Initializes this adapter. This will terminate any ongoing transactions with the current
     * instance of the client and creates a new client.
     */
    private void init() {
        state = STATE_READY;
        validateAppData();
        createInteractionClient();
        micButton.setOnClickListener(this);
        shouldInitialize = false;
        utteranceList = new ArrayList<String>();
        loadInputFile();
        loadOutputFile();
    }

    /**
     * Cancel current dialog.
     */
    public void cancel() {
        reset();
    }

    /**
     * Reset client.
     */
    protected void reset() {
        if (lexInteractionClient != null) {
            lexInteractionClient.cancel();
        }
        state = STATE_READY;
        sessionAttributes = null;
    }

    /**
     * Assign a listener for the voice interactions with the Amazon Lex bot.
     *
     * @param voiceListener
     */
    protected void setVoiceListener(E2InteractiveVoiceView.InteractiveVoiceListener voiceListener) {
        this.voiceListener = voiceListener;
    }

    /**
     * Set credentials provider.
     * @param credentialsProvider
     */
    public void setCredentialProvider(AWSCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        shouldInitialize = true;
    }

    /**
     * Set interaction config.
     * @param interactionConfig The interaction config.
     */
    public void setInteractionConfig(InteractionConfig interactionConfig) {
        this.interactionConfig = interactionConfig;
        shouldInitialize = true;
    }

    /**
     * Set session attributes, these will be picked for the next dialog transaction.
     * @param sessionAttributes
     */
    public void setSessionAttributes(Map<String, String> sessionAttributes) {
        this.sessionAttributes = sessionAttributes;
    }

    /**
     * Set the AWS region where the Amazon Lex bot has been setup.
     *
     * @param awsRegion
     */
    public void setAwsRegion(String awsRegion) {
        this.awsRegion = Regions.fromName(awsRegion) ;
    }

    /**
     * Get the AWS regions set for this adapter.
     * @return The AWS region, {@link String}.
     */
    public String getAwsRegion() {
        return awsRegion == null ? null : awsRegion.getName();
    }
}

