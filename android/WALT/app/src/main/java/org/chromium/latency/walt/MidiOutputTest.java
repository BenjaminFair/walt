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
import android.media.midi.MidiInputPort;
import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

@TargetApi(23)
class MidiOutputTest extends MidiTest {
    private Handler handler = new Handler();

    private static final byte[] noteMsg = {(byte) 0x90, (byte) 99, (byte) 0};

    // Output and Input here are with respect to the MIDI device, not the Android device.
    private MidiInputPort mInputPort;

    private long last_tWalt = 0;
    private long last_tSys = 0;

    private int mOutputSyncAfterRepetitions = 20; // TODO: implement periodic clock sync for output
    private int mRepetitions = 10;
    private int mRepetitionsDone;
    private ArrayList<Double> deltas = new ArrayList<>();

    private static final int noteDelay = 300;
    private static final int timeout = 1000;

    MidiOutputTest(Context context) {
        super(context);
    }

    MidiOutputTest(Context context, AutoRunFragment.ResultHandler resultHandler) {
        super(context, resultHandler);
    }

    void setRepetitions(int repetitions) {
        mRepetitions = repetitions;
    }

    @Override
    void run() {
        super.run();
        if (mMidiDevice == null) return;
        try {
            setupMidiOut();
        } catch (IOException e) {
            log("Error setting up test: " + e.getMessage());
            return;
        }
        handler.postDelayed(cancelMidiOutRunnable, noteDelay * mRepetitions + timeout);
    }

    private void setupMidiOut() throws IOException {
        mRepetitionsDone = 0;
        deltas.clear();

        mInputPort = mMidiDevice.openInputPort(0);

        clockManager.syncClock();
        clockManager.command(ClockManager.CMD_MIDI);
        clockManager.startListener();
        clockManager.setTriggerHandler(triggerHandler);

        scheduleNotes();
    }

    private ClockManager.TriggerHandler triggerHandler = new ClockManager.TriggerHandler() {
        @Override
        public void onReceive(ClockManager.TriggerMessage tmsg) {
            last_tWalt = tmsg.t + clockManager.baseTime;
            double dt = (last_tWalt - last_tSys) / 1000.;

            log(String.format(Locale.US, "Note detected: latency of %.3f ms", dt));

            last_tSys += noteDelay * 1000;
            mRepetitionsDone++;

            if (mRepetitionsDone < mRepetitions) {
                try {
                    clockManager.command(ClockManager.CMD_MIDI);
                } catch (IOException e) {
                    log("Failed to send command CMD_MIDI: " + e.getMessage());
                }
            } else {
                end();
            }
        }
    };

    private void scheduleNotes() {
        if(mInputPort == null) {
            log("mInputPort is not open");
            return;
        }
        long t = System.nanoTime() + ((long) noteDelay) * 1000000L;
        try {
            // TODO: only schedule some, then sync clock
            for (int i = 0; i < mRepetitions; i++) {
                mInputPort.send(noteMsg, 0, noteMsg.length, t + ((long) noteDelay) * 1000000L * i);
            }
        } catch(IOException e) {
            log("Unable to schedule note: " + e.getMessage());
            return;
        }
        last_tSys = t / 1000;
    }

    private Runnable cancelMidiOutRunnable = new Runnable() {
        @Override
        public void run() {
            log("Timed out waiting for notes to be detected by WALT");
            end();
        }
    };

    void end() {
        handler.removeCallbacks(cancelMidiOutRunnable);

        try {
            mInputPort.close();
        } catch(IOException e) {
            log("Error, failed to close input port: " + e.getMessage());
        }

        clockManager.stopListener();
        clockManager.clearTriggerHandler();
        clockManager.checkDrift();
    }

    @Override
    Iterable[] getData() {
        return new Iterable[]{deltas};
    }
}
