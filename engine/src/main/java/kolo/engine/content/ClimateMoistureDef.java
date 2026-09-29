package kolo.engine.content;

import kolo.engine.error.Checks;

/**
 * Числа вологи провінції (GD §3.5), шкала {@code 0..}{@value ClimateDef#MAX_VALUE}: волога = {@code coast} −
 * (кроків до моря − 1) × {@code inlandDrying} + шум у межах {@code ±noiseAmplitude}, обрізано до шкали. Берегова
 * провінція — один крок до моря.
 *
 * @param coast волога берегової провінції, {@code 0..}{@value ClimateDef#MAX_VALUE}
 * @param inlandDrying на скільки волога меншає за кожен крок углиб суходолу, {@code 0..}{@value ClimateDef#MAX_VALUE}
 * @param noiseAmplitude найбільший внесок шуму в будь-який бік, {@code 0..}{@value ClimateDef#MAX_VALUE}
 */
public record ClimateMoistureDef(int coast, int inlandDrying, int noiseAmplitude) {

    public ClimateMoistureDef {
        Checks.inRange("climate.moisture.coast", coast, 0, ClimateDef.MAX_VALUE);
        Checks.inRange("climate.moisture.inland_drying", inlandDrying, 0, ClimateDef.MAX_VALUE);
        Checks.inRange("climate.moisture.noise_amplitude", noiseAmplitude, 0, ClimateDef.MAX_VALUE);
    }
}
