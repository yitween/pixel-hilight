package com.custom.pixelhilight;

interface ILightBridge {
    void triggerEffect(int argbColor, int effectId, float brightness, float speed, int durationMs);
    void turnOff();
    void destroy();
}
