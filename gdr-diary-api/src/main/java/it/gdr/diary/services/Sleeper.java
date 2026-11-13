package it.gdr.diary.services;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
