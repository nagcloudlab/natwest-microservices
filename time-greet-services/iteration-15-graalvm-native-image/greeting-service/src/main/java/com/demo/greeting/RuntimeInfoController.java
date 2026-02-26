package com.demo.greeting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RuntimeInfoController {

    private final StartupTimeListener startupTimeListener;

    public RuntimeInfoController(StartupTimeListener startupTimeListener) {
        this.startupTimeListener = startupTimeListener;
    }

    @GetMapping("/info/runtime")
    public Map<String, Object> runtimeInfo() {
        boolean isNative = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", isNative ? "Native" : "JVM");
        info.put("startupTimeMs", startupTimeListener.getStartupTimeMs());
        info.put("memoryUsedMB", usedMemory / (1024 * 1024));
        info.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        info.put("processors", runtime.availableProcessors());
        info.put("vmName", System.getProperty("java.vm.name", "unknown"));
        info.put("vmVersion", System.getProperty("java.vm.version", "unknown"));
        return info;
    }
}
