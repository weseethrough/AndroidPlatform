package com.raceyourself.raceyourself.home;

import org.joda.time.Duration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Duncan on 27/06/2014.
 */
@Slf4j
@Data
public class DurationChallengeBean extends ChallengeBean {
    private Duration duration;
    private double distanceMetres;
}