package com.demo.greeting;

import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupTimeListener {

    private long startupTimeMs = -1;

    @EventListener
    public void onApplicationStarted(ApplicationStartedEvent event) {
        this.startupTimeMs = event.getTimeTaken().toMillis();
    }

    public long getStartupTimeMs() {
        return startupTimeMs;
    }
}
