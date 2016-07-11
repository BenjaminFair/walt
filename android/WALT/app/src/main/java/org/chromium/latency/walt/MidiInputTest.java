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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

@TargetApi(23)
class MidiInputTest extends MidiTest {
    private Handler handler = new Handler();

    // Output and Input here are with respect to the MIDI device, not the Android device.
    private MidiOutputPort mOutputPort;

    private long last_tWalt = 0;
    private long last_tSys = 0;
    private long last_tJava = 0;

    private int mSyncAfterRepetitions = 100;
    private int mRepetitions = 100;
    private int mRepetitionsDone;
    private ArrayList<Double> deltasToSys = new ArrayList<>();
    private ArrayList<Double> deltasTotal = new ArrayList<>();

    private static final int noteDelay = 300;
    private static final int timeout = 1000;

    MidiInputTest(Context context) {
        super(context);
    }

    MidiInputTest(Context context, AutoRunFragment.ResultHandler resultHandler) {
        super(context, resultHandler);
    }

    void setRepetitions(int mRepetitions) {
        this.mRepetitions = mRepetitions;
    }

    @Override
    void run() {
        super.run();
        if (mMidiDevice == null) return;
        try {
            setupMidiIn();
        } catch (IOException e) {
            log("Error setting up test: " + e.getMessage());
            return;
        }
        handler.postDelayed(requestNoteRunnable, noteDelay);
    }

    private Runnable requestNoteRunnable = new Runnable() {
        @Override
        public void run() {
            log("Requesting note from WALT...");
            String s;
            try {
                s = clockManager.command(ClockManager.CMD_NOTE);
            } catch (IOException e) {
                log("Error sending NOTE command: " + e.getMessage());
                return;
            }
            last_tWalt = Integer.parseInt(s);
            handler.postDelayed(finishMidiInRunnable, timeout);
        }
    };

    private Runnable finishMidiInRunnable = new Runnable() {
        @Override
        public void run() {
            clockManager.checkDrift();

            log("deltas: " + deltasToSys.toString());
            log(String.format(Locale.US,
                    "Median MIDI subsystem latency %.1f ms\nMedian total latency %.1f ms",
                    Utils.median(deltasToSys), Utils.median(deltasTotal)
            ));
             
            end();
        }
    };

    private class WaltReceiver extends MidiReceiver {
        public void onSend(byte[] data, int offset,
                           int count, long timestamp) throws IOException {
            if(count > 0 && data[offset] == (byte) 0x90) { // NoteOn message on channel 1
                handler.removeCallbacks(finishMidiInRunnable);
                last_tJava = clockManager.micros();
                last_tSys = timestamp / 1000 - clockManager.baseTime;

                double d1 = (last_tSys - last_tWalt) / 1000.;
                double d2 = (last_tJava - last_tSys) / 1000.;
                double dt = (last_tJava - last_tWalt) / 1000.;
                log(String.format(Locale.US,
                        "Result: Time to MIDI subsystem = %.3f ms, Time to Java = %.3f ms, " +
                                "Total = %.3f ms",
                        d1, d2, dt));
                deltasToSys.add(d1);
                deltasTotal.add(dt);

                mRepetitionsDone++;
                if (mRepetitionsDone % mSyncAfterRepetitions == 0) {
                    try {
                        clockManager.syncClock();
                    } catch (IOException e) {
                        log("Error syncing clocks: " + e.getMessage());
                        handler.post(finishMidiInRunnable);
                        return;
                    }
                }
                if (mRepetitionsDone < mRepetitions) {
                    handler.post(requestNoteRunnable);
                } else {
                    handler.post(finishMidiInRunnable);
                }
            } else {
                log(String.format(Locale.US, "Expected 0x90, got 0x%x and count was %d",
                        data[offset], count));
            }
        }
    }

    private void setupMidiIn() throws IOException {
        mRepetitionsDone = 0;
        mOutputPort = mMidiDevice.openOutputPort(0);
        mOutputPort.connect(new WaltReceiver());
        clockManager.syncClock();
    }

    void end() {
        handler.removeCallbacks(requestNoteRunnable);
        handler.removeCallbacks(finishMidiInRunnable);
        try {
            mOutputPort.close();
        } catch (IOException e) {
            log("Error, failed to close output port: " + e.getMessage());
        }
    }

    @Override
    Iterable[] getData() {
        return new Iterable[]{deltasToSys, deltasTotal};
    }
}
