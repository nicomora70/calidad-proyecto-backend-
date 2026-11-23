package co.ritech.simulation;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public class SimulationNotFoundException extends BusinessException {

    public SimulationNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "SIMULATION_NOT_FOUND", "Simulation " + id + " does not exist");
    }
}
