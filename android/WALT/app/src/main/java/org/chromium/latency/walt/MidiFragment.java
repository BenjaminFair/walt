/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.chromium.latency.walt;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class MidiFragment extends Fragment implements View.OnClickListener {

    private Activity activity;
    private SimpleLogger logger;
    private TextView mTextView;
    private Experiment currentExperiment;

    public MidiFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        activity = getActivity();
        logger = SimpleLogger.getInstance(getContext());

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_midi, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        mTextView = (TextView) activity.findViewById(R.id.txt_box_midi);

        // Register this fragment class as the listener for some button clicks
        activity.findViewById(R.id.button_start_midi_in).setOnClickListener(this);
        activity.findViewById(R.id.button_start_midi_out).setOnClickListener(this);

        // mLogTextView.setMovementMethod(new ScrollingMovementMethod());
        mTextView.setText(logger.getLogText());
        logger.registerReceiver(mLogReceiver);
    }

    @Override
    public void onPause() {
        logger.unregisterReceiver(mLogReceiver);
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        if (currentExperiment != null) {
            currentExperiment.end();
        }
        switch (v.getId()) {
            case R.id.button_start_midi_in:
                currentExperiment = new MidiInputTest(activity);
                break;
            case R.id.button_start_midi_out:
                currentExperiment = new MidiOutputTest(activity);
                break;
            default:
                logger.log("Unknown button pressed");
                return;
        }
        currentExperiment.run();
    }

    private BroadcastReceiver mLogReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra("message");
            mTextView.append(msg + "\n");
        }
    };
}
