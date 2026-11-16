package com.custom.pixelhilight;

interface ILightBridge {
    void triggerHiLight(int argbColor, int durationMs);
    void turnOff();
    void destroy();
}
