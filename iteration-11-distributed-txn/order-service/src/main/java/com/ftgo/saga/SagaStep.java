package com.ftgo.saga;

public class SagaStep {

    private final String name;
    private final Runnable forwardAction;
    private final Runnable compensatingAction;

    public SagaStep(String name, Runnable forwardAction, Runnable compensatingAction) {
        this.name = name;
        this.forwardAction = forwardAction;
        this.compensatingAction = compensatingAction;
    }

    public String getName() { return name; }
    public Runnable getForwardAction() { return forwardAction; }
    public Runnable getCompensatingAction() { return compensatingAction; }
}
