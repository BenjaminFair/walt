package org.chromium.latency.walt;

import android.content.Context;

abstract class Experiment {
    private SimpleLogger logger;
    ClockManager clockManager;

    private AutoRunFragment.ResultHandler resultHandler;

    Experiment(Context context) {
        logger = SimpleLogger.getInstance(context);
        clockManager = ClockManager.getInstance(context);
    }

    Experiment(Context context, AutoRunFragment.ResultHandler resultHandler) {
        this(context);
        this.resultHandler = resultHandler;
    }

    void end() {
        if (resultHandler != null) {
            resultHandler.onResult(getData());
        }
    }

    abstract void setRepetitions(int mRepetitions);
    abstract void run();
    abstract Iterable[] getData();

    void log(String msg) {
        logger.log(msg);
    }
}
