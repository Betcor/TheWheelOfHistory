package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Числа температури провінції (GD §3.5), шкала {@code 0..}{@value ClimateDef#MAX_VALUE}: температура = широта (від
 * {@code equator} на середній лінії карти до {@code pole} на верхньому й нижньому краях) + зсув клімату світу −
 * висота × {@code heightCooling} / 100 + шум у межах {@code ±noiseAmplitude}, обрізано до шкали.
 *
 * @param equator температура на екваторі, {@code pole..}{@value ClimateDef#MAX_VALUE}
 * @param pole температура на полюсі, {@code 0..equator}
 * @param heightCooling на скільки відсотків висоти холоднішає провінція, {@code 0..100}: гори — холодніші
 * @param noiseAmplitude найбільший внесок шуму в будь-який бік, {@code 0..}{@value ClimateDef#MAX_VALUE}
 */
public record ClimateTemperatureDef(int equator, int pole, int heightCooling, int noiseAmplitude) {

    public ClimateTemperatureDef {
        Checks.inRange("climate.temperature.pole", pole, 0, ClimateDef.MAX_VALUE);
        Checks.inRange("climate.temperature.equator", equator, pole, ClimateDef.MAX_VALUE);
        Checks.inRange("climate.temperature.height_cooling", heightCooling, 0, 100);
        Checks.inRange("climate.temperature.noise_amplitude", noiseAmplitude, 0, ClimateDef.MAX_VALUE);
    }
}
